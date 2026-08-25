/*
 * Deterministic end to end check for the Android WEB proxy carrier.
 *
 * It runs the real WebProxyTransport on the JVM against Android stubs, plays the role of the
 * bridge page with a plain Java implementation of the https carrier, and drives a live
 * tproxy-server relay whose backend is a TCP echo server.
 *
 * Usage: java BridgeHarness <relay base url> <public hostname> <secret hex>
 */

import android.net.Uri;
import android.webkit.WebView;

import androidx.webkit.JavaScriptReplyProxy;
import androidx.webkit.WebMessageCompat;
import androidx.webkit.WebViewCompat;

import org.telegram.messenger.WebProxyFrame;
import org.telegram.messenger.WebProxyTransport;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BridgeHarness {

    private static String relayBase;
    private static String publicHost;
    private static int failures;
    private static volatile FakeBridge current;

    public static void main(String[] args) throws Exception {
        relayBase = args[0];
        publicHost = args[1];
        String secret = args[2];

        WebView.loadHook = url -> {
            if (url.startsWith("https://") && url.contains("?bridge=")) {
                FakeBridge bridge = new FakeBridge();
                current = bridge;
                new Thread(() -> bridge.load(url), "fake-bridge").start();
            }
        };

        WebProxyTransport transport = WebProxyTransport.getInstance();
        int port = transport.start(publicHost, secret);
        check(port > 0, "loopback listener bound on port " + port);

        long deadline = System.currentTimeMillis() + 30000;
        while (transport.getState() != WebProxyTransport.STATE_CONNECTED && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        check(transport.getState() == WebProxyTransport.STATE_CONNECTED, "carrier session established");
        if (transport.getState() != WebProxyTransport.STATE_CONNECTED) {
            System.exit(1);
        }

        echoOnce(port, 1024, "small round trip");
        echoOnce(port, 200 * 1024, "200 KiB round trip");
        echoOnce(port, 9 * 1024 * 1024, "9 MiB round trip, exercises both stream windows");
        concurrent(port, 4, 512 * 1024);
        backendClose(port);
        echoOnce(port, 4096, "stream after a backend close");

        // A carrier failure closes every logical stream and the transport rebuilds the WebView.
        Socket doomed = new Socket("127.0.0.1", port);
        doomed.setSoTimeout(15000);
        doomed.getOutputStream().write("still here".getBytes(StandardCharsets.US_ASCII));
        doomed.getOutputStream().flush();
        new DataInputStream(doomed.getInputStream()).readFully(new byte[10]);
        current.fail();
        deadline = System.currentTimeMillis() + 10000;
        while (transport.getState() == WebProxyTransport.STATE_CONNECTED && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        check(transport.getState() != WebProxyTransport.STATE_CONNECTED, "reported carrier failure drops the session");
        check(doomed.getInputStream().read() == -1, "carrier failure closes the live streams");
        doomed.close();
        deadline = System.currentTimeMillis() + 30000;
        while (transport.getState() != WebProxyTransport.STATE_CONNECTED && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        check(transport.getState() == WebProxyTransport.STATE_CONNECTED, "carrier reconnects on its own");
        echoOnce(port, 8192, "round trip after the reconnect");

        check(transport.getState() == WebProxyTransport.STATE_CONNECTED, "carrier still connected at the end");
        transport.stop();
        Thread.sleep(300);
        check(!transport.isRunning(), "transport stopped");

        // A system WebView without the exact origin binary message boundary must fail closed:
        // the listener still exists, so tgnet never falls back to a direct connection, but no
        // logical stream is ever carried.
        androidx.webkit.WebViewFeature.supported = false;
        int closedPort = transport.start(publicHost, secret);
        check(closedPort > 0, "unsupported webview still binds the listener");
        check(transport.getState() == WebProxyTransport.STATE_UNSUPPORTED, "unsupported webview is reported");
        try (Socket socket = new Socket("127.0.0.1", closedPort)) {
            socket.setSoTimeout(5000);
            socket.getOutputStream().write(new byte[]{1, 2, 3, 4});
            socket.getOutputStream().flush();
            check(socket.getInputStream().read() == -1, "unsupported webview carries no traffic");
        }
        transport.stop();

        System.out.println(failures == 0 ? "ALL PASSED" : (failures + " FAILURES"));
        System.exit(failures == 0 ? 0 : 1);
    }

    private static void check(boolean condition, String name) {
        System.out.println((condition ? "ok   " : "FAIL ") + name);
        if (!condition) {
            failures++;
        }
    }

    private static void echoOnce(int port, int size, String name) throws IOException {
        byte[] payload = new byte[size];
        new Random(size).nextBytes(payload);
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setTcpNoDelay(true);
            byte[] echoed = exchange(socket, payload);
            check(java.util.Arrays.equals(payload, echoed), name);
        }
    }

    private static byte[] exchange(Socket socket, byte[] payload) throws IOException {
        OutputStream output = socket.getOutputStream();
        InputStream input = socket.getInputStream();
        byte[] result = new byte[payload.length];
        Thread writer = new Thread(() -> {
            try {
                output.write(payload);
                output.flush();
            } catch (IOException ignore) {
            }
        }, "harness-writer");
        writer.start();
        new DataInputStream(input).readFully(result);
        try {
            writer.join(5000);
        } catch (InterruptedException ignore) {
        }
        return result;
    }

    private static void concurrent(int port, int count, int size) throws Exception {
        Thread[] threads = new Thread[count];
        AtomicInteger good = new AtomicInteger();
        for (int a = 0; a < count; a++) {
            final int index = a;
            threads[a] = new Thread(() -> {
                byte[] payload = new byte[size];
                new Random(index).nextBytes(payload);
                try (Socket socket = new Socket("127.0.0.1", port)) {
                    socket.setTcpNoDelay(true);
                    if (java.util.Arrays.equals(payload, exchange(socket, payload))) {
                        good.incrementAndGet();
                    }
                } catch (IOException e) {
                    System.out.println("stream " + index + " failed: " + e);
                }
            }, "harness-stream-" + a);
            threads[a].start();
        }
        for (Thread thread : threads) {
            thread.join();
        }
        check(good.get() == count, count + " concurrent streams, " + (size / 1024) + " KiB each");
    }

    private static void backendClose(int port) throws IOException {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(15000);
            socket.getOutputStream().write("BYE!".getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            InputStream input = socket.getInputStream();
            int total = 0;
            byte[] buffer = new byte[16];
            while (true) {
                int read = input.read(buffer);
                if (read < 0) {
                    break;
                }
                total += read;
            }
            check(total == 4, "backend close propagates as a local EOF");
        }
    }

    /** Plain Java stand in for the bridge page, https carrier mode. */
    private static final class FakeBridge {

        private final LinkedBlockingQueue<byte[]> uplink = new LinkedBlockingQueue<>();
        private WebViewCompat.WebMessageListener listener;
        private WebView view;
        private String bootstrap;
        private String sessionToken;
        private volatile String downCursor = "0";
        private boolean createStarted;
        private JavaScriptReplyProxy proxy;

        private void load(String url) {
            listener = WebViewCompat.installedListener;
            view = WebViewCompat.installedView;
            try {
                String capability = url.substring(url.indexOf("?bridge=") + "?bridge=".length(), url.indexOf('#'));
                String nonce = url.substring(url.indexOf("#android=") + "#android=".length());
                HttpURLConnection page = open("GET", "/?bridge=" + capability, null);
                String body = new String(readAll(page.getInputStream()), StandardCharsets.UTF_8);
                Matcher matcher = Pattern.compile("bootstrap=\"([A-Za-z0-9_-]+)\"").matcher(body);
                if (!matcher.find()) {
                    throw new IOException("no bootstrap token in the bridge page");
                }
                bootstrap = matcher.group(1);
                proxy = new JavaScriptReplyProxy() {
                    @Override
                    public void postMessage(String message) {
                    }

                    @Override
                    public void postMessage(byte[] arrayBuffer) {
                        onAppMessage(arrayBuffer);
                    }
                };
                deliver(new WebMessageCompat("{\"t\":\"tproxy-android-init\",\"v\":1,\"nonce\":\"" + nonce + "\"}"));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private void onAppMessage(byte[] batch) {
            synchronized (this) {
                if (!createStarted) {
                    createStarted = true;
                    new Thread(() -> createSession(batch), "fake-bridge-create").start();
                    return;
                }
            }
            uplink.add(batch);
        }

        private void createSession(byte[] hello) {
            try {
                HttpURLConnection connection = open("POST", "/api/v1/session", bootstrap);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/octet-stream");
                connection.setFixedLengthStreamingMode(hello.length);
                connection.getOutputStream().write(hello);
                if (connection.getResponseCode() != 200) {
                    throw new IOException("session creation rejected: " + connection.getResponseCode());
                }
                sessionToken = connection.getHeaderField("X-Session-Token");
                downCursor = connection.getHeaderField("X-Down-Cursor");
                byte[] welcome = readAll(connection.getInputStream());
                deliver(new WebMessageCompat(welcome));
                deliver(new WebMessageCompat("{\"t\":\"status\",\"state\":\"connected\"}"));
                new Thread(this::uplinkLoop, "fake-bridge-up").start();
                new Thread(this::downlinkLoop, "fake-bridge-down").start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private void uplinkLoop() {
            int sequence = 1;
            try {
                while (current == this) {
                    byte[] batch = uplink.take();
                    HttpURLConnection connection = open("POST", "/api/v1/up", sessionToken);
                    connection.setDoOutput(true);
                    connection.setRequestProperty("Content-Type", "application/octet-stream");
                    connection.setRequestProperty("X-Up-Seq", String.valueOf(sequence));
                    connection.setFixedLengthStreamingMode(batch.length);
                    connection.getOutputStream().write(batch);
                    int code = connection.getResponseCode();
                    if (code != 204 || !String.valueOf(sequence).equals(connection.getHeaderField("X-Up-Ack"))) {
                        throw new IOException("uplink rejected: " + code);
                    }
                    connection.getInputStream().close();
                    sequence++;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private void downlinkLoop() {
            try {
                while (current == this) {
                    HttpURLConnection connection = open("POST", "/api/v1/down", sessionToken);
                    connection.setRequestProperty("X-Down-Cursor", downCursor);
                    int code = connection.getResponseCode();
                    if (code == 204) {
                        connection.getInputStream().close();
                        continue;
                    }
                    if (code != 200) {
                        throw new IOException("downlink rejected: " + code);
                    }
                    byte[] body = readAll(connection.getInputStream());
                    downCursor = connection.getHeaderField("X-Down-Cursor");
                    // The real bridge splits an aggregated body into single frames before the
                    // WebView boundary, so do the same here.
                    for (byte[] frame : split(body)) {
                        deliver(new WebMessageCompat(frame));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        /** Reports the carrier failure the real bridge sends when its session dies. */
        private void fail() {
            deliver(new WebMessageCompat("{\"t\":\"status\",\"state\":\"failed\"}"));
        }

        private void deliver(WebMessageCompat message) {
            if (listener == null || view == null || (current != this && current != null)) {
                return;
            }
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    listener.onPostMessage(view, message, Uri.parse("https://" + publicHost), true, proxy));
        }

        private HttpURLConnection open(String method, String path, String token) throws IOException {
            HttpURLConnection connection = (HttpURLConnection) new URL(relayBase + path).openConnection();
            connection.setRequestMethod(method);
            connection.setRequestProperty("Host", publicHost);
            connection.setRequestProperty("Origin", "https://" + publicHost);
            if (token != null) {
                connection.setRequestProperty("Authorization", "Bearer " + token);
            }
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(90000);
            connection.setInstanceFollowRedirects(false);
            return connection;
        }
    }

    private static java.util.List<byte[]> split(byte[] batch) {
        java.util.List<byte[]> result = new java.util.ArrayList<>();
        int offset = 0;
        while (offset < batch.length) {
            int length = ((batch[offset + 4] & 0xff) << 24) | ((batch[offset + 5] & 0xff) << 16)
                    | ((batch[offset + 6] & 0xff) << 8) | (batch[offset + 7] & 0xff);
            byte[] frame = new byte[WebProxyFrame.HEADER_SIZE + length];
            System.arraycopy(batch, offset, frame, 0, frame.length);
            result.add(frame);
            offset += frame.length;
        }
        return result;
    }

    private static byte[] readAll(InputStream input) throws IOException {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[16384];
        int read;
        while ((read = input.read(buffer)) > 0) {
            output.write(buffer, 0, read);
        }
        input.close();
        return output.toByteArray();
    }
}
