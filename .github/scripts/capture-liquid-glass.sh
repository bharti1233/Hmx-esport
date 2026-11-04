#!/usr/bin/env bash
# Runs inside the reactivecircus/android-emulator-runner step.
# Kept in a file because the action executes `script` line by line with sh -c,
# which cannot parse a multi-line shell function.
set -euo pipefail

mkdir -p artifacts/screenshots
adb wait-for-device
adb shell input keyevent 82 || true
adb shell wm dismiss-keyguard || true
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -c || true

capture() {
  local state="$1"
  local scroll="$2"
  local file="$3"
  echo "=== capture $file (state=$state scroll=$scroll) ==="
  adb shell am force-stop com.hmx.arena.debug || true
  adb shell am start -W \
    -n com.hmx.arena.debug/com.hmx.arena.ui.visual.VisualTestActivity \
    --es state "$state" --ei scroll "$scroll" 2>&1 \
    | tee "artifacts/am-start-$file.txt"
  sleep 6
  if ! adb shell pidof com.hmx.arena.debug > /dev/null; then
    echo "Process died for $file" >&2
    adb logcat -d > "artifacts/logcat-failure-$file.txt" || true
    adb shell dumpsys activity top > "artifacts/activity-failure-$file.txt" || true
    return 1
  fi
  adb exec-out screencap -p > "artifacts/screenshots/$file"
  adb shell dumpsys activity top > "artifacts/activity-$file.txt" || true
}

capture main 0 01-home.png
capture main 4 02-scrolled.png
capture sheet 0 03-bottom-sheet.png
capture dialog 0 04-dialog.png

adb logcat -d -t 800 > artifacts/logcat.txt || true
