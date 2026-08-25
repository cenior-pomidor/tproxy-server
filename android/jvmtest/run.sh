#!/usr/bin/env bash
# Runs the JVM checks for the Android WEB proxy client.
#
#   ./android/jvmtest/run.sh [telegram-checkout]
#
# FrameHarness  covers the frame codec, hostname, secret, capability and link vectors.
# BridgeHarness runs the real WebProxyTransport against Android stubs, a Java stand in for
#               the bridge page and a live tproxy-server relay whose backend echoes bytes.
#
# Requirements: JDK 17 or newer, Go, Python 3.

set -euo pipefail

script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
repo_root=$(cd "$script_dir/../.." && pwd)
checkout=${1:-$script_dir/../build/Telegram-Android}
sources=$checkout/TMessagesProj/src/main/java/org/telegram/messenger

if [ ! -f "$sources/WebProxyTransport.java" ]; then
    echo "no patched checkout at $checkout, run android/build.sh first" >&2
    exit 1
fi

work=$(mktemp -d)
relay_port=${RELAY_PORT:-18080}
admin_port=${ADMIN_PORT:-18081}
backend_port=${BACKEND_PORT:-12398}
host=proxy.example.com
secret=000102030405060708090a0b0c0d0e0f
pids=()

cleanup() {
    for pid in "${pids[@]:-}"; do
        [ -n "$pid" ] && kill "$pid" 2>/dev/null || true
    done
    rm -rf "$work"
}
trap cleanup EXIT

mkdir -p "$work/site" "$work/classes"
cat > "$work/config.json" <<JSON
{
  "public_hostname": "$host",
  "listen": "127.0.0.1:$relay_port",
  "admin_listen": "127.0.0.1:$admin_port",
  "public_dir": "$work/site",
  "profiles_file": "$work/profiles.json"
}
JSON
cat > "$work/profiles.json" <<JSON
{"profiles": [{"name": "default", "secret": "$secret", "backend": "127.0.0.1:$backend_port", "carrier_mode": "https"}]}
JSON
chmod 600 "$work/profiles.json"
echo "<!doctype html><title>site</title>" > "$work/site/index.html"

(cd "$repo_root" && go build -o "$work/tproxy-server" ./cmd/tproxy-server)

python3 "$script_dir/echo-backend.py" "$backend_port" > "$work/backend.log" 2>&1 &
pids+=($!)
"$work/tproxy-server" -config "$work/config.json" > "$work/relay.log" 2>&1 &
pids+=($!)

for _ in $(seq 1 50); do
    if curl -sS -o /dev/null --noproxy '*' -H "Host: $host" "http://127.0.0.1:$relay_port/" 2>/dev/null; then
        break
    fi
    sleep 0.2
done

javac -nowarn -d "$work/classes" \
    $(find "$script_dir/stubs" -name '*.java') \
    "$sources/WebProxyFrame.java" "$sources/WebProxyLink.java" "$sources/WebProxyTransport.java" \
    "$script_dir/FrameHarness.java" "$script_dir/BridgeHarness.java"

echo
echo "== frame, hostname, secret, capability and link vectors =="
java -cp "$work/classes" FrameHarness

echo
echo "== transport against a live relay =="
java -Dsun.net.http.allowRestrictedHeaders=true -cp "$work/classes" \
    BridgeHarness "http://127.0.0.1:$relay_port" "$host" "$secret"
