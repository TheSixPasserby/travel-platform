#!/usr/bin/env bash
# Initialize Google command-line tools even when the hosted image omits sdkmanager from PATH.
set -euo pipefail
TRAVEL_ANDROID_SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$RUNNER_TEMP/android-sdk}}"
if [[ ! -d "$TRAVEL_ANDROID_SDK_ROOT" || ! -w "$TRAVEL_ANDROID_SDK_ROOT" ]]; then
  TRAVEL_ANDROID_SDK_ROOT="$RUNNER_TEMP/android-sdk"
fi
if [[ ! -x "$TRAVEL_ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" ]]; then
  TRAVEL_ANDROID_TOOLS="$RUNNER_TEMP/travel-android-tools"
  mkdir -p "$TRAVEL_ANDROID_TOOLS" "$TRAVEL_ANDROID_SDK_ROOT/cmdline-tools"
  curl --fail --location --retry 3 --proto '=https' \
    'https://dl.google.com/android/repository/commandlinetools-linux-15859902_latest.zip' \
    --output "$TRAVEL_ANDROID_TOOLS/tools.zip"
  printf '%s  %s\n' '4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583' "$TRAVEL_ANDROID_TOOLS/tools.zip" | sha256sum --check
  unzip -q "$TRAVEL_ANDROID_TOOLS/tools.zip" -d "$TRAVEL_ANDROID_TOOLS/extracted"
  mv "$TRAVEL_ANDROID_TOOLS/extracted/cmdline-tools" "$TRAVEL_ANDROID_SDK_ROOT/cmdline-tools/latest"
fi
printf 'ANDROID_HOME=%s\nANDROID_SDK_ROOT=%s\n' "$TRAVEL_ANDROID_SDK_ROOT" "$TRAVEL_ANDROID_SDK_ROOT" >> "$GITHUB_ENV"
printf '%s\n' "$TRAVEL_ANDROID_SDK_ROOT/cmdline-tools/latest/bin" "$TRAVEL_ANDROID_SDK_ROOT/platform-tools" "$TRAVEL_ANDROID_SDK_ROOT/emulator" >> "$GITHUB_PATH"
"$TRAVEL_ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" --version
yes | "$TRAVEL_ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$TRAVEL_ANDROID_SDK_ROOT" --licenses >/dev/null || test "${PIPESTATUS[1]}" = 0
