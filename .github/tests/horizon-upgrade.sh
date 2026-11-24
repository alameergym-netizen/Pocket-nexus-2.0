#!/usr/bin/env bash
set -euo pipefail
export PATH="$ANDROID_HOME/platform-tools:$PATH"
pkg=com.pocketnexus.console.debug
mkdir -p upgrade-results
exec > >(tee upgrade-results/upgrade.log) 2>&1
trap 'adb logcat -b crash -d > upgrade-results/crash.log || true' EXIT
adb install upgrade-apks/before.apk
adb shell pm path "$pkg" | grep -F 'package:'
adb shell dumpsys package "$pkg" | grep -F 'versionCode=900000'
# Synthetic files in the real app-private preference and data locations; no user data.
adb shell "run-as $pkg mkdir -p shared_prefs files"
for file in "${pkg}_preferences" pocket_game_profiles pocket_cloud_clients; do
  printf '<?xml version="1.0" encoding="utf-8" standalone="yes"?><map><string name="horizon_upgrade_fixture">%s</string></map>\n' "$file" | adb shell -T "run-as $pkg sh -c 'cat > shared_prefs/$file.xml'"
done
printf 'horizon-upgrade-fixture\n' | adb shell -T "run-as $pkg sh -c 'cat > files/horizon-upgrade-fixture.txt'"
adb shell am force-stop "$pkg"
adb install -r upgrade-apks/after.apk
adb shell dumpsys package "$pkg" | grep -F 'versionCode=900001'
for file in "${pkg}_preferences" pocket_game_profiles pocket_cloud_clients; do
  adb shell "run-as $pkg cat shared_prefs/$file.xml" | grep -F "<string name=\"horizon_upgrade_fixture\">$file</string>"
done
adb shell "run-as $pkg cat files/horizon-upgrade-fixture.txt" | grep -Fx 'horizon-upgrade-fixture'
echo 'PASS: same-package same-signer upgrade retained synthetic preferences and private file'
