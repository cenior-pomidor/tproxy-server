#!/usr/bin/env bash
# Builds Telegram for Android with WEB proxy support.
#
# It clones the pinned upstream release, applies telegram-web-proxy.patch and runs the
# ordinary Gradle build. Pass an existing checkout as the first argument to reuse it.
#
#   ./android/build.sh [checkout-directory]
#
# Requirements: JDK 17, Android SDK platform 35 and 36, build-tools 36.0.0,
# NDK 27.2.12479018, CMake 3.22.1, git and about 25 GB of free disk space.

set -euo pipefail

UPSTREAM_URL=${UPSTREAM_URL:-https://github.com/DrKLO/Telegram.git}
UPSTREAM_COMMIT=${UPSTREAM_COMMIT:-62b56a07ca7e30e39f7fd00a6728d6bbd716ca1c}
GRADLE_TASK=${GRADLE_TASK:-:TMessagesProj_App:assembleAfatDebug}

script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
patch_file=$script_dir/telegram-web-proxy.patch
checkout=${1:-$script_dir/build/Telegram-Android}

if [ -z "${ANDROID_SDK_ROOT:-}" ] && [ -z "${ANDROID_HOME:-}" ]; then
    echo "set ANDROID_SDK_ROOT (or ANDROID_HOME) to the Android SDK" >&2
    exit 1
fi
sdk_root=${ANDROID_SDK_ROOT:-$ANDROID_HOME}

if [ ! -d "$checkout/.git" ]; then
    mkdir -p "$(dirname "$checkout")"
    git clone --depth=1 "$UPSTREAM_URL" "$checkout"
    git -C "$checkout" fetch --depth=1 origin "$UPSTREAM_COMMIT"
    git -C "$checkout" checkout --detach "$UPSTREAM_COMMIT"
    git -C "$checkout" submodule update --init --recursive --depth=1
fi

# The patch is idempotent: skip it when the carrier is already present.
if [ ! -f "$checkout/TMessagesProj/src/main/java/org/telegram/messenger/WebProxyTransport.java" ]; then
    git -C "$checkout" apply --whitespace=nowarn "$patch_file"
fi

printf 'sdk.dir=%s\n' "$sdk_root" > "$checkout/local.properties"

# Telegram refuses to sign a real account in with the placeholder api_id the upstream
# repository ships. Get your own from https://my.telegram.org, API development tools.
build_vars=$checkout/TMessagesProj/src/main/java/org/telegram/messenger/BuildVars.java
if [ -n "${TELEGRAM_APP_ID:-}" ] && [ -n "${TELEGRAM_APP_HASH:-}" ]; then
    sed -i -E "s/public static int APP_ID = .*/public static int APP_ID = ${TELEGRAM_APP_ID};/" "$build_vars"
    sed -i -E "s/public static String APP_HASH = .*/public static String APP_HASH = \"${TELEGRAM_APP_HASH}\";/" "$build_vars"
    echo "building with the supplied Telegram api_id"
elif grep -q 'APP_ID = 4;' "$build_vars"; then
    echo "warning: building with the upstream placeholder api_id, sign in will fail." >&2
    echo "         set TELEGRAM_APP_ID and TELEGRAM_APP_HASH to your own credentials." >&2
fi

(cd "$checkout" && ANDROID_SDK_ROOT="$sdk_root" ANDROID_HOME="$sdk_root" ./gradlew --no-daemon "$GRADLE_TASK")

echo
echo "APK: $checkout/TMessagesProj_App/build/outputs/apk/afat/debug/app.apk"
