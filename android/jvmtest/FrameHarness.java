import org.telegram.messenger.WebProxyFrame;
import org.telegram.messenger.WebProxyLink;

import java.util.List;

public class FrameHarness {

    private static int failures;

    private static void check(boolean condition, String name) {
        System.out.println((condition ? "ok   " : "FAIL ") + name);
        if (!condition) {
            failures++;
        }
    }

    public static void main(String[] args) throws Exception {
        // Capability vectors from PROTOCOL.md.
        check("MHLEY5PmW1GWqJkSrlmJpvJUiLhBH_QKy6yKg8a0JPk".equals(
                WebProxyLink.capability("proxy.example.com", WebProxyLink.decodeSecret("000102030405060708090a0b0c0d0e0f"))),
                "capability, plain 16 byte secret");
        check("IpJrt3e7sKtzPyoXy6w-Zj6GGEvsvclN66JzQEfPYLA".equals(
                WebProxyLink.capability("proxy.example.com", WebProxyLink.decodeSecret("dd000102030405060708090a0b0c0d0e0f"))),
                "capability, dd padded secret");
        check(WebProxyLink.decodeSecret("AAECAwQFBgcICQoLDA0ODw").length == 16, "base64url secret");
        check(WebProxyLink.decodeSecret("000102030405060708090a0b0c0d0e") == null, "short secret rejected");
        check(WebProxyLink.decodeSecret("ee000102030405060708090a0b0c0d0e0f") == null, "17 byte secret needs the dd prefix");

        check(WebProxyLink.isValidHostname("proxy.example.com"), "canonical hostname");
        check(!WebProxyLink.isValidHostname("Proxy.Example.com"), "uppercase rejected");
        check(!WebProxyLink.isValidHostname("127.0.0.1"), "ip literal rejected");
        check(!WebProxyLink.isValidHostname("localhost"), "single label rejected");
        check(!WebProxyLink.isValidHostname("proxy.example.com."), "trailing dot rejected");
        check(!WebProxyLink.isValidHostname("proxy.example.com:443"), "port rejected");
        check(!WebProxyLink.isValidHostname("-bad.example.com"), "leading dash rejected");
        check(WebProxyLink.isValidHostname("xn--80ak6aa92e.com"), "a-label accepted");

        WebProxyLink link = WebProxyLink.parse("https://t.me/webproxy?server=proxy.example.com&secret=000102030405060708090a0b0c0d0e0f");
        check(link != null && "proxy.example.com".equals(link.hostname), "t.me link");
        link = WebProxyLink.parse("tg://webproxy?host=PROXY.example.com&secret=000102030405060708090a0b0c0d0e0f");
        check(link != null && "proxy.example.com".equals(link.hostname), "tg link with the host alias");
        check(WebProxyLink.parse("https://t.me/proxy?server=proxy.example.com&port=443&secret=000102030405060708090a0b0c0d0e0f") == null,
                "mtproto link is not a web link");
        check(WebProxyLink.parse("tg://webproxy?server=proxy.example.com&secret=zz") == null, "invalid secret rejected");
        check("https://t.me/webproxy?server=proxy.example.com&secret=000102030405060708090a0b0c0d0e0f".equals(
                WebProxyLink.buildLink("proxy.example.com", "000102030405060708090a0b0c0d0e0f")), "link round trip");
        check("https://proxy.example.com/?bridge=CAP#android=NONCE".equals(
                WebProxyLink.bridgeUrl("proxy.example.com", "CAP", "NONCE")), "bridge url");

        // Frames.
        byte[] hello = WebProxyFrame.hello();
        check(hello.length == 9 && (hello[0] & 0xff) == 0x10 && hello[8] == 1, "HELLO encoding");
        byte[] open = WebProxyFrame.open(0xABCDEF);
        check(open.length == 8 && (open[1] & 0xff) == 0xAB && (open[2] & 0xff) == 0xCD && (open[3] & 0xff) == 0xEF, "OPEN stream id");
        byte[] window = WebProxyFrame.window(7, 4 * 1024 * 1024);
        check(window.length == 12 && window[7] == 4 && (window[8] & 0xff) == 0 && (window[9] & 0xff) == 0x40, "WINDOW payload");

        byte[] welcome = new byte[]{0x11, 0, 0, 0, 0, 0, 0, 0};
        List<WebProxyFrame> frames = WebProxyFrame.parseAll(welcome);
        check(frames.size() == 1 && frames.get(0).type == WebProxyFrame.TYPE_WELCOME, "WELCOME parse");

        byte[] batch = concat(welcome, WebProxyFrame.data(5, new byte[]{9, 9, 9}, 0, 3), WebProxyFrame.window(5, 3));
        frames = WebProxyFrame.parseAll(batch);
        check(frames.size() == 3 && frames.get(1).payload.length == 3 && frames.get(2).windowAmount() == 3, "batch parse");

        check(rejects(new byte[]{0x02, 0, 0, 5, 0, 0, 0, 0}), "empty DATA rejected");
        check(rejects(new byte[]{0x01, 0, 0, 5, 0, 0, 0, 0}), "client only OPEN rejected");
        check(rejects(new byte[]{0x02, 0, 0, 5, 0, 0, 0, 4, 1, 2, 3}), "truncated frame rejected");
        check(rejects(new byte[]{0x04, 0, 0, 5, 0, 0, 0, 4, 0, 0, 0, 0}), "zero WINDOW rejected");
        check(rejects(new byte[]{0x11, 0, 0, 1, 0, 0, 0, 0}), "WELCOME on a nonzero stream rejected");
        check(rejects(new byte[0]), "empty batch rejected");

        System.out.println(failures == 0 ? "ALL PASSED" : (failures + " FAILURES"));
        if (failures != 0) {
            System.exit(1);
        }
    }

    private static boolean rejects(byte[] input) {
        try {
            WebProxyFrame.parseAll(input);
            return false;
        } catch (WebProxyFrame.InvalidFrameException e) {
            return true;
        }
    }

    private static byte[] concat(byte[]... parts) {
        int total = 0;
        for (byte[] part : parts) {
            total += part.length;
        }
        byte[] result = new byte[total];
        int offset = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, result, offset, part.length);
            offset += part.length;
        }
        return result;
    }
}
