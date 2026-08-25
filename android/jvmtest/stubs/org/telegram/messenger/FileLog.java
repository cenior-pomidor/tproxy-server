package org.telegram.messenger;

public class FileLog {
    public static void e(Throwable e) {
        System.out.println("[log] " + e);
    }

    public static void e(String message) {
        System.out.println("[log] " + message);
    }

    public static void d(String message) {
        System.out.println("[log] " + message);
    }
}
