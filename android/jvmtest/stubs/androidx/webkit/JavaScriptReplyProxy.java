package androidx.webkit;

public abstract class JavaScriptReplyProxy {
    public abstract void postMessage(String message);
    public abstract void postMessage(byte[] arrayBuffer);
}
