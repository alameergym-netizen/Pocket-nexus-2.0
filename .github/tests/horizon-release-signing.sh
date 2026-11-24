#!/usr/bin/env bash
set -euo pipefail
export PATH="$ANDROID_HOME/platform-tools:$PATH"
pkg=com.pocketnexus.console
mkdir -p release-test-evidence
exec > >(tee release-test-evidence/install.log) 2>&1
adb install release-test-apks/before.apk
adb shell dumpsys package "$pkg" | grep -F 'versionCode=910000'
# The incorrect certificate must be rejected by Android's actual Package Manager.
if adb install -r release-test-apks/wrong.apk > release-test-evidence/wrong-install.txt 2>&1; then
  echo 'FAIL: wrong signer accepted'
  exit 1
fi
cat release-test-evidence/wrong-install.txt
grep -F 'INSTALL_FAILED_UPDATE_INCOMPATIBLE' release-test-evidence/wrong-install.txt
adb shell dumpsys package "$pkg" | grep -F 'versionCode=910000'
adb install -r release-test-apks/after.apk
adb shell dumpsys package "$pkg" | grep -F 'versionCode=910001'
echo 'PASS: release package rejects wrong signer and accepts higher version with same test signer'
