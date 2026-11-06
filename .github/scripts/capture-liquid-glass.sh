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
  timeout 25 adb shell input keyevent KEYCODE_WAKEUP || true
  timeout 25 adb shell wm dismiss-keyguard || true
  timeout 45 adb shell am start -W \
    -n com.hmx.arena.debug/com.hmx.arena.ui.visual.VisualTestActivity \
    --es screen "$screen" --es theme "$theme" 2>&1 \
    | tee "artifacts/am-start-${screen}-${theme}.txt" || true
}

capture_screen() {
  local screen="$1"
  local theme="$2"
  local settle="${3:-12}"
  local file="$4"
  echo "=== capture $file (screen=$screen theme=$theme) ==="
  local attempt=1
  while [ "$attempt" -le 2 ]; do
    # A system ANR dialog (typically the Pixel Launcher on a slow SwiftShader
    # boot) sits above every window and would be captured in the screenshot,
    # even though HMX itself is healthy. Clear it before launching.
    clear_anr_dialog || true
    # Must be a real cold start: delivering the intent to an already running
    # top instance goes through onNewIntent (which the harness does not
    # handle), so the requested screen/theme extras would be ignored and every
    # capture would show the previous screen.
    timeout 20 adb shell am force-stop com.hmx.arena.debug || true
    sleep 1
    start_screen "$screen" "$theme"
    if wait_for_app_frame "$file"; then
      # Software rendering needs a long settle time for the first composed
      # frame. Do not send input while it draws: that is what triggered the
      # original ANR.
      sleep "$settle"
      if ! timeout 10 adb shell pidof com.hmx.arena.debug > /dev/null 2>&1; then
        echo "Process died during settle for $file" >&2
      else
        timeout 25 adb shell dumpsys window > "artifacts/window-$file.txt" || true
        # Only a *focused* ANR dialog blocks the capture. `dumpsys window`
        # also keeps a "last ANR" section from earlier in the run, so a plain
        # grep would false-positive forever.
        if grep -Eqi "mCurrentFocus=.*(Application Not Responding|isn.t responding)" "artifacts/window-$file.txt"; then
          echo "ANR dialog still focused for $file" >&2
          clear_anr_dialog || true
        else
          timeout 30 adb exec-out screencap -p > "artifacts/screenshots/$file" || true
          timeout 25 adb shell dumpsys activity top > "artifacts/activity-$file.txt" || true
          echo "=== captured $file ($(wc -c < "artifacts/screenshots/$file" 2>/dev/null || echo 0) bytes) ==="
          return 0
        fi
      fi
    fi
    echo "Attempt $attempt failed for $file" >&2
    timeout 25 adb logcat -d > "artifacts/logcat-failure-$file.txt" || true
    timeout 25 adb shell dumpsys activity top > "artifacts/activity-failure-$file.txt" || true
    timeout 20 adb shell am force-stop com.hmx.arena.debug || true
    sleep 2
    attempt=$((attempt + 1))
  done
  echo "=== giving up on $file ===" >&2
  return 1
}

# Close a system ANR dialog that belongs to another process (the launcher on a
# slow emulator boot). For HMX's own dialog, tap "Wait" so the app can finish
# its first frame instead of being killed. Expects artifacts/ui-last.xml to be
# the current dump. Safe to call when no dialog is present.
dismiss_anr_dialog() {
  if ! grep -qi "isn.t responding\|Application Not Responding" artifacts/ui-last.xml 2>/dev/null; then
    return 0
  fi
  local button="Close app"
  if grep -qi "HMX Arena isn.t responding" artifacts/ui-last.xml 2>/dev/null; then
    button="Wait"
  fi
  echo "ANR dialog detected ($(grep -o 'text="[^"]*isn.t responding[^"]*"' artifacts/ui-last.xml | head -1)); tapping $button" >&2
  local coords
  coords="$(node_bounds "$button" || true)"
  if [ -n "$coords" ]; then
    timeout 10 adb shell input tap $coords || true
  fi
  sleep 3
}

clear_anr_dialog() {
  rm -f artifacts/ui-last.xml
  timeout 20 adb shell uiautomator dump /sdcard/hmx-window.xml > /dev/null 2>&1 || true
  timeout 15 adb pull /sdcard/hmx-window.xml artifacts/ui-last.xml > /dev/null 2>&1 || true
  dismiss_anr_dialog || true
}

# Wait until the app window is actually in the accessibility tree. If Android
# shows an ANR dialog, dismiss it and keep polling. Returns non-zero when the
# process died or the deadline passed. Every adb call is bounded: uiautomator
# can otherwise block for minutes while SwiftShader renders the first frame.
wait_for_app_frame() {
  local label="$1"
  local tries=0
  while [ "$tries" -lt 5 ]; do
    tries=$((tries + 1))
    if ! timeout 10 adb shell pidof com.hmx.arena.debug > /dev/null 2>&1; then
      echo "Process died while waiting for $label" >&2
      return 1
    fi
    rm -f artifacts/ui-last.xml
    timeout 20 adb shell uiautomator dump /sdcard/hmx-window.xml > /dev/null 2>&1 || true
    timeout 15 adb pull /sdcard/hmx-window.xml artifacts/ui-last.xml > /dev/null 2>&1 || true
    if grep -qi "isn.t responding\|Application Not Responding" artifacts/ui-last.xml 2>/dev/null; then
      dismiss_anr_dialog || true
      continue
    fi
    if grep -q 'package="com.hmx.arena.debug"' artifacts/ui-last.xml 2>/dev/null; then
      return 0
    fi
    sleep 1
  done
  echo "Timed out waiting for app window for $label" >&2
  return 1
}

# Print the bounds of a node whose text or content description contains the
# first argument. Used to tap the real "Dark" segment and "Switch theme".
node_bounds() {
  local needle="$1"
  timeout 20 adb shell uiautomator dump /sdcard/hmx-window.xml > /dev/null 2>&1 || true
  timeout 15 adb pull /sdcard/hmx-window.xml artifacts/ui-last.xml > /dev/null 2>&1 || true
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
  # A leftover system ANR dialog would hide the target node from uiautomator.
  clear_anr_dialog || true
  local coords
  coords="$(node_bounds "$needle")"
  if [ -z "$coords" ]; then
    echo "Could not find UI node: $needle" >&2
    return 1
  fi
  echo "Tap '$needle' at $coords"
  timeout 10 adb shell input tap $coords
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
# `start_screen` alone delivers the intent to the running top-most instance,
# and the harness does not handle onNewIntent, so the requested screen would be
# ignored. Force-stop first so this is a real launch of the Arena screen.
timeout 20 adb shell am force-stop com.hmx.arena.debug || true
sleep 2
start_screen arena light
wait_for_app_frame 17-arena-light-search.png || true
sleep 12
tap_node "Search tournaments" || true
sleep 1
timeout 15 adb shell input text "weekend" || true
sleep 1
timeout 15 adb shell input keyevent 111 || true
sleep 2
clear_anr_dialog || true
timeout 30 adb exec-out screencap -p > artifacts/screenshots/17-arena-light-search.png || true
tap_node "Switch theme" || true
sleep 12
clear_anr_dialog || true
timeout 30 adb exec-out screencap -p > artifacts/screenshots/18-arena-switched-dark.png || true
timeout 25 adb shell dumpsys activity top > artifacts/activity-after-dark.txt || true
timeout 20 adb shell uiautomator dump /sdcard/hmx-window.xml > /dev/null 2>&1 || true
timeout 15 adb pull /sdcard/hmx-window.xml artifacts/ui-after-dark.xml > /dev/null 2>&1 || true
if grep -q "currently dark" artifacts/ui-after-dark.xml 2>/dev/null; then
  echo "Theme switch check: dark appearance active on the same Arena screen"
else
  echo "Theme switch check: could not confirm dark appearance in the UI dump" >&2
fi
tap_node "Switch theme" || true
sleep 12
clear_anr_dialog || true
timeout 30 adb exec-out screencap -p > artifacts/screenshots/19-arena-switched-light.png || true
timeout 25 adb shell dumpsys activity top > artifacts/activity-after-light.txt || true
timeout 20 adb shell uiautomator dump /sdcard/hmx-window.xml > /dev/null 2>&1 || true
timeout 15 adb pull /sdcard/hmx-window.xml artifacts/ui-after-light.xml > /dev/null 2>&1 || true
if grep -q "currently light" artifacts/ui-after-light.xml 2>/dev/null; then
  echo "Theme switch check: light appearance active on the same Arena screen"
else
  echo "Theme switch check: could not confirm light appearance in the UI dump" >&2
fi
if grep -qi "weekend" artifacts/ui-after-light.xml 2>/dev/null; then
  echo "Theme switch check: typed search text survived the switch"
fi

# --- Persistence across process death --------------------------------------
echo "=== theme persistence ==="
# Capture 19 is Light. Switch to Dark, let DataStore write, then kill the
# process and start without a `theme` extra. The screenshot must be taken after
# that restart: capturing the previous process would prove nothing.
tap_node "Switch theme" || true
sleep 6
timeout 20 adb shell am force-stop com.hmx.arena.debug || true
sleep 2
timeout 45 adb shell am start -W \
  -n com.hmx.arena.debug/com.hmx.arena.ui.visual.VisualTestActivity \
  --es screen settings 2>&1 | tee artifacts/am-start-persistence.txt || true
wait_for_app_frame 20-persistence-dark.png || true
sleep 12
clear_anr_dialog || true
timeout 30 adb exec-out screencap -p > artifacts/screenshots/20-persistence-dark.png || true
timeout 25 adb shell dumpsys activity top > artifacts/activity-persistence.txt || true
timeout 20 adb shell uiautomator dump /sdcard/hmx-window.xml > /dev/null 2>&1 || true
timeout 15 adb pull /sdcard/hmx-window.xml artifacts/ui-persistence.xml > /dev/null 2>&1 || true
if grep -q "Dark glass" artifacts/ui-persistence.xml 2>/dev/null; then
  echo "Persistence check: dark appearance restored after process death"
else
  echo "Persistence check FAILED: dark appearance not restored after process death" >&2
  exit 1
fi

timeout 25 adb logcat -d -t 1600 > artifacts/logcat.txt || true

captured="$(find artifacts/screenshots -name '*.png' | wc -l | tr -d ' ')"
echo "=== captured $captured screenshots ==="
if [ "$captured" -lt 20 ]; then
  echo "Expected 20 screenshots, only found $captured" >&2
  exit 1
fi

# ---------------------------------------------------------------------------
# Performance evidence. Numbers are written verbatim so the report never has
# to rely on estimates.
# ---------------------------------------------------------------------------
mkdir -p artifacts/performance
echo "=== performance measurements ==="

# Cold and warm start of the production activity (no session: it shows the
# real welcome/auth surface, which is the first screen a new player sees).
for i in 1 2 3; do
  timeout 20 adb shell am force-stop com.hmx.arena.debug || true
  sleep 2
  timeout 45 adb shell am start -W \
    -n com.hmx.arena.debug/com.hmx.arena.MainActivity 2>&1 \
    | tee "artifacts/performance/cold-start-$i.txt" || true
  sleep 2
done
timeout 15 adb shell input keyevent KEYCODE_HOME || true
sleep 2
for i in 1 2 3; do
  timeout 45 adb shell am start -W \
    -n com.hmx.arena.debug/com.hmx.arena.MainActivity 2>&1 \
    | tee "artifacts/performance/warm-start-$i.txt" || true
  sleep 1
  timeout 15 adb shell input keyevent KEYCODE_HOME || true
  sleep 1
done

# Scroll jank on the densest glass screen (Home: hero + cards + list).
timeout 20 adb shell am force-stop com.hmx.arena.debug || true
sleep 1
start_screen home dark
sleep 14
timeout 25 adb shell dumpsys gfxinfo com.hmx.arena.debug reset > /dev/null 2>&1 || true
for _ in 1 2 3 4 5 6 7 8; do
  timeout 10 adb shell input swipe 540 1750 540 450 250 || true
  sleep 1
done
timeout 30 adb shell dumpsys gfxinfo com.hmx.arena.debug \
  > artifacts/performance/gfxinfo-home-dark.txt || true

{
  echo "cold start (ms):"
  grep -h "TotalTime" artifacts/performance/cold-start-*.txt || true
  echo "warm start (ms):"
  grep -h "TotalTime" artifacts/performance/warm-start-*.txt || true
  echo "gfxinfo summary:"
  grep -E "Total frames rendered|Janky frames|50th percentile|90th percentile|95th percentile|99th percentile" \
    artifacts/performance/gfxinfo-home-dark.txt || true
} | tee artifacts/performance/performance-summary.txt

echo "=== performance measurement complete ==="
