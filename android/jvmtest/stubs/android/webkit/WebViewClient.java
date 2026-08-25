package android.webkit;

public class WebViewClient {
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        return false;
    }

    public boolean shouldOverrideUrlLoading(WebView view, String url) {
        return false;
    }

    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        return null;
    }

    public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
        return null;
    }

    public void onReceivedSslError(WebView view, SslErrorHandler handler, android.net.http.SslError error) {
    }

    public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
    }

    public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
        return false;
    }
}
