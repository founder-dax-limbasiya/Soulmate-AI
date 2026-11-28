#!/usr/bin/env bash
set -euo pipefail
adb install -r luna/app/build/outputs/apk/debug/app-debug.apk
adb install -r luna/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell run-as studio.luna.companion mkdir -p files
adb shell -T run-as studio.luna.companion sh -c "'cat > files/companion.gguf'" < /tmp/model.gguf
adb shell cmd connectivity airplane-mode enable
adb shell am instrument -w studio.luna.companion.test/androidx.test.runner.AndroidJUnitRunner | tee /tmp/luna-test-results
grep -F 'OK (2 tests)' /tmp/luna-test-results
