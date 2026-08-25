package android.os;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Single threaded stand in for the main looper. */
public class Handler {

    private static final ScheduledExecutorService EXECUTOR =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "fake-main");
                thread.setDaemon(true);
                return thread;
            });

    public Handler() {
    }

    public Handler(Looper looper) {
    }

    public boolean post(Runnable runnable) {
        EXECUTOR.execute(wrap(runnable));
        return true;
    }

    public boolean postDelayed(Runnable runnable, long delay) {
        EXECUTOR.schedule(wrap(runnable), delay, TimeUnit.MILLISECONDS);
        return true;
    }

    public void removeCallbacks(Runnable runnable) {
    }

    private static Runnable wrap(Runnable runnable) {
        return () -> {
            try {
                runnable.run();
            } catch (Throwable e) {
                e.printStackTrace();
            }
        };
    }
}
