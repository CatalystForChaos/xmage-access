package xmageaccess.util;

import javax.swing.SwingUtilities;
import java.util.concurrent.ForkJoinPool;

/**
 * Tiny EDT-aware execution helpers. Keeps Swing-touching work on
 * the EDT and reflection-heavy / I/O work off it.
 */
public final class Async {

    private Async() {}

    /** Run on the Swing event dispatch thread, immediately if already on EDT. */
    public static void onEdt(Runnable r) {
        if (SwingUtilities.isEventDispatchThread()) {
            r.run();
        } else {
            SwingUtilities.invokeLater(r);
        }
    }

    /** Run off the EDT on the common ForkJoinPool. */
    public static void offEdt(Runnable r) {
        ForkJoinPool.commonPool().execute(r);
    }
}
