package android.webkit;

import android.content.Context;

import java.util.function.Consumer;

public class WebView {

    /** The harness plays the bridge document instead of loading it. */
    public static volatile Consumer<String> loadHook;

    private final WebSettings settings = new WebSettings();

    public WebView(Context context) {
    }

    public WebSettings getSettings() {
        return settings;
    }

    public void setWebViewClient(WebViewClient client) {}
    public void setWebChromeClient(WebChromeClient client) {}
    public void setDownloadListener(DownloadListener listener) {}
    public void setNetworkAvailable(boolean value) {}
    public void stopLoading() {}
    public void clearHistory() {}
    public void clearCache(boolean value) {}
    public void clearFormData() {}
    public void destroy() {}

    public void loadUrl(String url) {
        Consumer<String> hook = loadHook;
        if (hook != null) {
            hook.accept(url);
        }
    }
}
