package org.telegram.messenger;

import android.content.Context;

public class ApplicationLoader {
    public static volatile Context applicationContext = new Context();
    public static volatile boolean mainInterfacePaused = false;
}
