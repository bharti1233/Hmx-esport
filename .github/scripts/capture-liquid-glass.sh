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
adb shell settings put global hide_error_dialogs 1 || true
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
  local settle="${3:-15}"
  local file="$4"
  echo "=== capture $file (screen=$screen theme=$theme) ==="
  start_screen "$screen" "$theme"
  # Software rendering needs a long settle time for the first frame. Later
  # launches are warm but still slower than a hardware device.
  sleep "$settle"
  adb shell input keyevent KEYCODE_WAKEUP || true
  adb shell wm dismiss-keyguard || true
  sleep 2
  if ! adb shell pidof com.hmx.arena.debug > /dev/null; then
    echo "Process died for $file" >&2
    adb logcat -d > "artifacts/logcat-failure-$file.txt" || true
    adb shell dumpsys activity top > "artifacts/activity-failure-$file.txt" || true
    return 1
  fi
  adb exec-out screencap -p > "artifacts/screenshots/$file"
  adb shell dumpsys activity top > "artifacts/activity-$file.txt" || true
  adb shell dumpsys window > "artifacts/window-$file.txt" || true
  echo "=== captured $file ($(wc -c < "artifacts/screenshots/$file") bytes) ==="
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
capture_screen signin       light 22 01-signin-light.png
capture_screen signin       dark  15 02-signin-dark.png
capture_screen home         light 15 03-home-light.png
capture_screen home         dark  15 04-home-dark.png
capture_screen arena        light 15 05-arena-light.png
capture_screen arena        dark  15 06-arena-dark.png
capture_screen tournament   light 15 07-tournament-light.png
capture_screen tournament   dark  15 08-tournament-dark.png
capture_screen matches      light 15 09-matches-light.png
capture_screen matches      dark  15 10-matches-dark.png
capture_screen leaderboard  light 15 11-leaderboard-light.png
capture_screen leaderboard  dark  15 12-leaderboard-dark.png
capture_screen profile      light 15 13-profile-light.png
capture_screen profile      dark  15 14-profile-dark.png
capture_screen settings     light 15 15-settings-light.png
capture_screen settings     dark  15 16-settings-dark.png

# --- Immediate switching, preserving screen + typed form input -------------
echo "=== theme switch state preservation ==="
start_screen arena light
sleep 15
tap_node "Search tournaments" || true
sleep 1
adb shell input text "weekend" || true
sleep 1
adb shell input keyevent 111 || true
sleep 2
adb exec-out screencap -p > artifacts/screenshots/17-arena-light-search.png
tap_node "Switch theme" || true
sleep 4
adb exec-out screencap -p > artifacts/screenshots/18-arena-switched-dark.png
adb shell dumpsys activity top > artifacts/activity-after-dark.txt || true
tap_node "Switch theme" || true
sleep 4
adb exec-out screencap -p > artifacts/screenshots/19-arena-switched-light.png
adb shell dumpsys activity top > artifacts/activity-after-light.txt || true
adb shell uiautomator dump /sdcard/hmx-window.xml > /dev/null 2>&1 || true
adb pull /sdcard/hmx-window.xml artifacts/ui-after-light.xml > /dev/null 2>&1 || true

# --- Persistence across process death --------------------------------------
echo "=== theme persistence ==="
start_screen settings dark
sleep 15
adb shell am force-stop com.hmx.arena.debug
sleep 2
adb shell am start -W \
  -n com.hmx.arena.debug/com.hmx.arena.ui.visual.VisualTestActivity \
  --es screen settings 2>&1 | tee artifacts/am-start-persistence.txt
sleep 15
adb exec-out screencap -p > artifacts/screenshots/20-persistence-dark.png
adb shell uiautomator dump /sdcard/hmx-window.xml > /dev/null 2>&1 || true
adb pull /sdcard/hmx-window.xml artifacts/ui-persistence.xml > /dev/null 2>&1 || true

adb logcat -d -t 1600 > artifacts/logcat.txt || true
