package androidx.webkit;

public class WebMessageCompat {

    public static final int TYPE_STRING = 0;
    public static final int TYPE_ARRAY_BUFFER = 1;

    private final int type;
    private final String data;
    private final byte[] arrayBuffer;

    public WebMessageCompat(String data) {
        this.type = TYPE_STRING;
        this.data = data;
        this.arrayBuffer = null;
    }

    public WebMessageCompat(byte[] arrayBuffer) {
        this.type = TYPE_ARRAY_BUFFER;
        this.data = null;
        this.arrayBuffer = arrayBuffer;
    }

    public int getType() {
        return type;
    }

    public String getData() {
        return data;
    }

    public byte[] getArrayBuffer() {
        return arrayBuffer;
    }
}
