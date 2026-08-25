package androidx.webkit;

import android.net.Uri;
import android.webkit.WebView;

import java.util.Set;

public final class WebViewCompat {

    public interface WebMessageListener {
        void onPostMessage(WebView view, WebMessageCompat message, Uri sourceOrigin, boolean isMainFrame, JavaScriptReplyProxy replyProxy);
    }

    public static class ScriptHandler {
    }

    /** The harness reads back the listener the transport installed. */
    public static volatile WebView installedView;
    public static volatile WebMessageListener installedListener;
    public static volatile String installedName;
    public static volatile Set<String> installedRules;
    public static volatile String installedScript;

    public static void addWebMessageListener(WebView view, String jsObjectName, Set<String> allowedOriginRules, WebMessageListener listener) {
        installedView = view;
        installedName = jsObjectName;
        installedRules = allowedOriginRules;
        installedListener = listener;
    }

    public static void removeWebMessageListener(WebView view, String jsObjectName) {
        installedListener = null;
    }

    public static ScriptHandler addDocumentStartJavaScript(WebView view, String script, Set<String> allowedOriginRules) {
        installedScript = script;
        return new ScriptHandler();
    }
}
