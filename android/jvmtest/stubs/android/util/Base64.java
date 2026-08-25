package android.util;

public final class Base64 {
    public static final int DEFAULT = 0;
    public static final int NO_PADDING = 1;
    public static final int NO_WRAP = 2;
    public static final int URL_SAFE = 8;

    public static String encodeToString(byte[] input, int flags) {
        java.util.Base64.Encoder encoder = (flags & URL_SAFE) != 0 ? java.util.Base64.getUrlEncoder() : java.util.Base64.getEncoder();
        if ((flags & NO_PADDING) != 0) {
            encoder = encoder.withoutPadding();
        }
        return encoder.encodeToString(input);
    }

    public static byte[] decode(String input, int flags) {
        input = input.trim();
        java.util.Base64.Decoder decoder = (flags & URL_SAFE) != 0 ? java.util.Base64.getUrlDecoder() : java.util.Base64.getDecoder();
        int pad = input.length() % 4;
        if (pad == 2) {
            input = input + "==";
        } else if (pad == 3) {
            input = input + "=";
        } else if (pad == 1) {
            throw new IllegalArgumentException("bad base64");
        }
        return decoder.decode(input);
    }
}
