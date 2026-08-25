package android.net;

import java.net.URI;

public final class Uri {

    private final String value;
    private URI parsed;

    private Uri(String value) {
        this.value = value;
        try {
            parsed = new URI(value);
        } catch (Exception ignore) {
            parsed = null;
        }
    }

    public static Uri parse(String value) {
        return new Uri(value);
    }

    public String getScheme() {
        return parsed == null ? null : parsed.getScheme();
    }

    public String getHost() {
        return parsed == null ? null : parsed.getHost();
    }

    public int getPort() {
        return parsed == null ? -1 : parsed.getPort();
    }

    public String getUserInfo() {
        return parsed == null ? null : parsed.getUserInfo();
    }

    public String getPath() {
        return parsed == null ? null : parsed.getPath();
    }

    public String getQueryParameter(String name) {
        return null;
    }

    @Override
    public String toString() {
        return value;
    }
}
