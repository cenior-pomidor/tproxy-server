package android.webkit;

import android.net.Uri;

public class WebChromeClient {

    public static abstract class FileChooserParams {
    }

    public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, android.os.Message resultMsg) {
        return false;
    }

    public void onPermissionRequest(PermissionRequest request) {
    }

    public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
    }

    public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
        return false;
    }

    public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
        return false;
    }

    public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
        return false;
    }

    public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, JsPromptResult result) {
        return false;
    }

    public boolean onConsoleMessage(ConsoleMessage message) {
        return false;
    }
}
