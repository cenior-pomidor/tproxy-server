package androidx.webkit;

public final class WebViewFeature {
    public static final String WEB_MESSAGE_LISTENER = "WEB_MESSAGE_LISTENER";
    public static final String WEB_MESSAGE_ARRAY_BUFFER = "WEB_MESSAGE_ARRAY_BUFFER";
    public static final String DOCUMENT_START_SCRIPT = "DOCUMENT_START_SCRIPT";
    public static final String SAFE_BROWSING_ENABLE = "SAFE_BROWSING_ENABLE";

    public static volatile boolean supported = true;

    public static boolean isFeatureSupported(String feature) {
        return supported;
    }
}
