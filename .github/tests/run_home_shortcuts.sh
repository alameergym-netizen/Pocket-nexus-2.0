#!/usr/bin/env bash
set -euo pipefail
export PATH="$ANDROID_HOME/platform-tools:$PATH"
mkdir -p home-ui-results
trap 'timeout 10 adb logcat -b crash -d > home-ui-results/crash.log || true' EXIT
adb shell svc power stayon true
adb shell settings put system screen_off_timeout 1800000
python3 -u .github/tests/home_shortcuts.py home-ui-apks/dev.apk app/build/outputs/apk/nonRoot_game/debug/*.apk 2>&1 | tee home-ui-results/result.log
