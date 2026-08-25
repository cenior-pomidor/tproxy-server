package android.webkit;

public class CookieManager {

    private static final CookieManager INSTANCE = new CookieManager();

    public static CookieManager getInstance() {
        return INSTANCE;
    }

    public void setAcceptThirdPartyCookies(WebView view, boolean accept) {
    }
}
