# Telegram for Android with WEB proxy support

This directory turns the official Telegram for Android source into a client that can
use a `tproxy-server` relay as a WEB proxy. The design, the WebView hardening rules
and the known limits are in `../ANDROID.md`; the wire contract is in `../PROTOCOL.md`.

## What the patch adds

New client sources, all under `TMessagesProj/src/main/java/org/telegram/messenger/`:

| File | Responsibility |
|---|---|
| `WebProxyTransport.java` | loopback MTProxy listener, logical stream mux, per-stream 4 MiB windows, the private origin-scoped WebView carrier, and the reconnect lifecycle |
| `WebProxyFrame.java` | shared frame codec: encoding, batch parsing, relay-to-client shape validation |
| `WebProxyLink.java` | canonical hostname and secret validation, bridge capability derivation, bridge URL, `t.me/webproxy` links |

Changes to existing sources:

| File | Change |
|---|---|
| `SharedConfig` | `ProxyInfo` gains an explicit SOCKS5 / MTProto / WEB type, proxy-list schema v3, WEB link generation, and the type lookup used by the connection layer |
| `ConnectionsManager` | an enabled WEB entry starts the carrier and points native tgnet at `127.0.0.1:<sidecar>`, keeping the user's MTProxy secret; it never falls back to a direct connection |
| `ProxySettingsActivity` | a third `WEB Proxy` type: hostname and secret only, port fixed to 443, canonical validation, `t.me/webproxy` sharing and clipboard import |
| `ProxyListActivity` | shows the WEB address without a port and `Not tested` instead of a ping, and never probes a WEB entry |
| `ProxyRotationController` | never rotates into a WEB entry |
| `AndroidUtilities` | recognizes `t.me/webproxy` and `tg://webproxy` links and shows the shared confirmation sheet |
| `build.gradle` | adds `androidx.webkit:webkit:1.14.0` for the exact-origin binary message boundary |
| `strings.xml` | `UseProxyWeb`, `UseProxyWebInfo`, `ProxyNotTested` |

## Build

```bash
JAVA_HOME=/path/to/jdk-17 ANDROID_SDK_ROOT=/path/to/android-sdk ./android/build.sh
```

The script clones the pinned upstream release, applies `telegram-web-proxy.patch`
and runs `:TMessagesProj_App:assembleAfatDebug`. Pass an existing checkout as the
first argument to reuse it. The result is
`TMessagesProj_App/build/outputs/apk/afat/debug/app.apk` with `armeabi-v7a`,
`arm64-v8a`, `x86` and `x86_64` native libraries, signed with upstream's dummy debug
keystore: it is a local test build, not something to publish.

Requirements: JDK 17, Android platform 35 and 36, build-tools 36.0.0,
NDK 27.2.12479018, CMake 3.22.1, and roughly 25 GB of free disk space. The first
build compiles the native tree for four ABIs and takes a while; later builds reuse it.

## Checks

```bash
./android/jvmtest/run.sh [telegram-checkout]
```

No device is needed. `FrameHarness` covers the frame codec plus the hostname, secret,
capability and link vectors from `PROTOCOL.md`. `BridgeHarness` compiles the real
`WebProxyTransport` against small Android and AndroidX stubs, plays the bridge page
with a plain Java implementation of the `https` carrier, and drives a live
`tproxy-server` whose backend echoes bytes: it checks session setup, small and 9 MiB
round trips, concurrent streams, backend-initiated close and shutdown.

## Using it

1. Deploy `tproxy-server` with a profile whose `secret` is the client-facing MTProxy
   secret, and serve it over HTTPS on the profile hostname.
2. In the app: Settings, Data and Storage, Proxy Settings, Add Proxy, `WEB Proxy`.
   Enter the relay hostname and the same secret. There is no port and no username.
3. Enable the row. The proxy stays in the connecting state until the hidden carrier
   has a relay session, then reports connected like any other proxy.

Share links use the dedicated type, for example:

```text
https://t.me/webproxy?server=proxy.example.com&secret=000102030405060708090a0b0c0d0e0f
```

`tg://webproxy?server=...&secret=...` is accepted as well. `t.me` does not register
`/webproxy` today, so the HTTPS form is only guaranteed when this build handles the
link itself; use the `tg://` form for cross-app testing.
