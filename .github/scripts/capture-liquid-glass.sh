#!/usr/bin/env bash
# Runs inside the reactivecircus/android-emulator-runner step.
# Kept in a file because the action executes `script` line by line with sh -c,
# which cannot parse a multi-line shell function.
set -euo pipefail

mkdir -p artifacts/screenshots
adb wait-for-device

# Keep the panel awake: a blanked emulator display produces an all-black
# screencap even though the activity is resumed.
adb shell svc power stayon true || true
adb shell settings put system screen_off_timeout 600000 || true
# Do NOT suppress error dialogs: with dialogs disabled Android kills an app on
# ANR instead of showing "Wait", which is exactly how the first cold capture
# died. The wait loop below taps "Wait" if SwiftShader's first blurred frame
# exceeds the input-dispatch timeout.
adb shell settings put global hide_error_dialogs 0 || true
adb shell input keyevent KEYCODE_WAKEUP || true
adb shell wm dismiss-keyguard || true
adb shell input keyevent 82 || true
sleep 3
adb shell dumpsys power > artifacts/power.txt || true
adb shell wm size > artifacts/display-size.txt || true

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -c || true

# Cold-start once. Subsequent launches reuse the process and its shader cache,
# which matters because the emulator renders with SwiftShader (software GPU).
adb shell am force-stop com.hmx.arena.debug || true
sleep 2

start_screen() {
  local screen="$1"
  local theme="$2"
  adb shell input keyevent KEYCODE_WAKEUP || true
  adb shell wm dismiss-keyguard || true
  adb shell am start -W \
    -n com.hmx.arena.debug/com.hmx.arena.ui.visual.VisualTestActivity \
    --es screen "$screen" --es theme "$theme" 2>&1 \
    | tee "artifacts/am-start-${screen}-${theme}.txt"
}

capture_screen() {
  local screen="$1"
  local theme="$2"
  local settle="${3:-12}"
  local file="$4"
  echo "=== capture $file (screen=$screen theme=$theme) ==="
  local attempt=1
  while [ "$attempt" -le 3 ]; do
    # Must be a real cold start: delivering the intent to an already running
    # top instance goes through onNewIntent (which the harness does not
    # handle), so the requested screen/theme extras would be ignored and every
    # capture would show the previous screen.
    adb shell am force-stop com.hmx.arena.debug || true
    sleep 1
    start_screen "$screen" "$theme"
    if wait_for_app_frame "$file"; then
      # Software rendering needs a long settle time for the first composed
      # frame. Do not send input while it draws: that is what triggered the
      # original ANR.
      sleep "$settle"
      if ! adb shell pidof com.hmx.arena.debug > /dev/null 2>&1; then
        echo "Process died during settle for $file" >&2
      else
        adb shell dumpsys window > "artifacts/window-$file.txt" || true
        if ! grep -q "mCurrentFocus.*com.hmx.arena.debug/com.hmx.arena.ui.visual.VisualTestActivity" "artifacts/window-$file.txt"; then
          echo "App window is not focused for $file" >&2
        else
          adb exec-out screencap -p > "artifacts/screenshots/$file"
          adb shell dumpsys activity top > "artifacts/activity-$file.txt" || true
          echo "=== captured $file ($(wc -c < "artifacts/screenshots/$file") bytes) ==="
          return 0
        fi
      fi
    fi
    echo "Attempt $attempt failed for $file" >&2
    adb logcat -d > "artifacts/logcat-failure-$file.txt" || true
    adb shell dumpsys activity top > "artifacts/activity-failure-$file.txt" || true
    adb shell am force-stop com.hmx.arena.debug || true
    sleep 3
    attempt=$((attempt + 1))
  done
  echo "=== giving up on $file ===" >&2
  return 1
}

# Wait until the app window is actually in the accessibility tree. If Android
# shows an ANR dialog, press "Wait" and keep polling. Returns non-zero when the
# process died or the deadline passed.
wait_for_app_frame() {
  local label="$1"
  local tries=0
  while [ "$tries" -lt 30 ]; do
    tries=$((tries + 1))
    if ! adb shell pidof com.hmx.arena.debug > /dev/null 2>&1; then
      echo "Process died while waiting for $label" >&2
      return 1
    fi
    rm -f artifacts/ui-last.xml
    adb shell uiautomator dump /sdcard/hmx-window.xml > /dev/null 2>&1 || true
    adb pull /sdcard/hmx-window.xml artifacts/ui-last.xml > /dev/null 2>&1 || true
    if grep -qi "isn.t responding\|Application Not Responding" artifacts/ui-last.xml 2>/dev/null; then
      echo "ANR dialog detected while waiting for $label; tapping Wait"
      local coords
      coords="$(node_bounds "Wait" || true)"
      if [ -n "$coords" ]; then
        adb shell input tap $coords || true
      fi
      sleep 4
      continue
    fi
    if grep -q 'package="com.hmx.arena.debug"' artifacts/ui-last.xml 2>/dev/null; then
      return 0
    fi
    sleep 2
  done
  echo "Timed out waiting for app window for $label" >&2
  return 1
}

# Print the bounds of a node whose text or content description contains the
# first argument. Used to tap the real "Dark" segment and "Switch theme".
node_bounds() {
  local needle="$1"
  adb shell uiautomator dump /sdcard/hmx-window.xml > /dev/null 2>&1 || true
  adb pull /sdcard/hmx-window.xml artifacts/ui-last.xml > /dev/null 2>&1 || true
  python3 - "$needle" <<'PY'
import re, sys, xml.etree.ElementTree as ET
needle = sys.argv[1].lower()
try:
    tree = ET.parse('artifacts/ui-last.xml')
except Exception:
    sys.exit(0)
for node in tree.iter('node'):
    text = (node.get('text') or '').lower()
    desc = (node.get('content-desc') or '').lower()
    if needle in text or needle in desc:
        bounds = node.get('bounds') or ''
        m = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', bounds)
        if m:
            x1, y1, x2, y2 = map(int, m.groups())
            print((x1 + x2) // 2, (y1 + y2) // 2)
            break
PY
}

tap_node() {
  local needle="$1"
  local coords
  coords="$(node_bounds "$needle")"
  if [ -z "$coords" ]; then
    echo "Could not find UI node: $needle" >&2
    return 1
  fi
  echo "Tap '$needle' at $coords"
  adb shell input tap $coords
}

# --- Light + Dark matrix: 8 screens x 2 themes = 16 captures ----------------
# Keep going after an individual failure so the uploaded artifact still shows
# every screen; the final count check below fails the job if any are missing.
capture_screen signin       light 20 01-signin-light.png || true
capture_screen signin       dark  10 02-signin-dark.png || true
capture_screen home         light 10 03-home-light.png || true
capture_screen home         dark  10 04-home-dark.png || true
capture_screen arena        light 10 05-arena-light.png || true
capture_screen arena        dark  10 06-arena-dark.png || true
capture_screen tournament   light 10 07-tournament-light.png || true
capture_screen tournament   dark  10 08-tournament-dark.png || true
capture_screen matches      light 10 09-matches-light.png || true
capture_screen matches      dark  10 10-matches-dark.png || true
capture_screen leaderboard  light 10 11-leaderboard-light.png || true
capture_screen leaderboard  dark  10 12-leaderboard-dark.png || true
capture_screen profile      light 10 13-profile-light.png || true
capture_screen profile      dark  10 14-profile-dark.png || true
capture_screen settings     light 10 15-settings-light.png || true
capture_screen settings     dark  10 16-settings-dark.png || true

# --- Immediate switching, preserving screen + typed form input -------------
echo "=== theme switch state preservation ==="
start_screen arena light
wait_for_app_frame 17-arena-light-search.png || true
sleep 12
tap_node "Search tournaments" || true
sleep 1
adb shell input text "weekend" || true
sleep 1
adb shell input keyevent 111 || true
sleep 2
adb exec-out screencap -p > artifacts/screenshots/17-arena-light-search.png
tap_node "Switch theme" || true
sleep 12
adb exec-out screencap -p > artifacts/screenshots/18-arena-switched-dark.png
adb shell dumpsys activity top > artifacts/activity-after-dark.txt || true
tap_node "Switch theme" || true
sleep 12
adb exec-out screencap -p > artifacts/screenshots/19-arena-switched-light.png
adb shell dumpsys activity top > artifacts/activity-after-light.txt || true
adb shell uiautomator dump /sdcard/hmx-window.xml > /dev/null 2>&1 || true
adb pull /sdcard/hmx-window.xml artifacts/ui-after-light.xml > /dev/null 2>&1 || true

# --- Persistence across process death --------------------------------------
echo "=== theme persistence ==="
start_screen settings dark
wait_for_app_frame 20-persistence-dark.png || true
sleep 12
adb shell am force-stop com.hmx.arena.debug
sleep 2
adb shell am start -W \
  -n com.hmx.arena.debug/com.hmx.arena.ui.visual.VisualTestActivity \
  --es screen settings 2>&1 | tee artifacts/am-start-persistence.txt
wait_for_app_frame 20-persistence-dark.png || true
sleep 12
adb exec-out screencap -p > artifacts/screenshots/20-persistence-dark.png
adb shell uiautomator dump /sdcard/hmx-window.xml > /dev/null 2>&1 || true
adb pull /sdcard/hmx-window.xml artifacts/ui-persistence.xml > /dev/null 2>&1 || true

adb logcat -d -t 1600 > artifacts/logcat.txt || true

captured="$(find artifacts/screenshots -name '*.png' | wc -l | tr -d ' ')"
echo "=== captured $captured screenshots ==="
if [ "$captured" -lt 20 ]; then
  echo "Expected 20 screenshots, only found $captured" >&2
  exit 1
fi
