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

capture() {
  local state="$1"
  local scroll="$2"
  local file="$3"
  echo "=== capture $file (state=$state scroll=$scroll) ==="
  adb shell input keyevent KEYCODE_WAKEUP || true
  adb shell wm dismiss-keyguard || true
  adb shell am start -W \
    -n com.hmx.arena.debug/com.hmx.arena.ui.visual.VisualTestActivity \
    --es state "$state" --ei scroll "$scroll" 2>&1 \
    | tee "artifacts/am-start-$file.txt"
  # Software rendering needs a long settle time for the first frame. Later
  # launches are warm but still slower than a hardware device.
  sleep 20
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

capture main 0 01-home.png
capture main 4 02-scrolled.png
capture sheet 0 03-bottom-sheet.png
capture dialog 0 04-dialog.png

adb logcat -d -t 1200 > artifacts/logcat.txt || true
