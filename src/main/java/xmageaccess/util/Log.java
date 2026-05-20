package xmageaccess.util;

/**
 * Lightweight logging helper that writes to System.err with a fixed
 * "[XMage Access][CATEGORY]" prefix. Replaces ad-hoc empty catch blocks
 * and bare e.printStackTrace() calls scattered through the codebase.
 *
 * <p>Verbose ("debug") output is gated by the system property
 * {@code xmageaccess.log=debug}.
 */
public final class Log {

    private static final boolean DEBUG = "debug".equalsIgnoreCase(System.getProperty("xmageaccess.log", ""));

    private Log() {}

    /** Log a warning about a recoverable failure. Always emitted. */
    public static void warn(String category, String message) {
        System.err.println("[XMage Access][" + category + "] " + message);
    }

    /** Log a warning with the throwable's message attached. */
    public static void warn(String category, String message, Throwable t) {
        String tail = t != null ? " (" + t.getClass().getSimpleName() + ": " + t.getMessage() + ")" : "";
        System.err.println("[XMage Access][" + category + "] " + message + tail);
        if (DEBUG && t != null) t.printStackTrace(System.err);
    }

    /** Log a verbose-only message. Suppressed unless -Dxmageaccess.log=debug. */
    public static void debug(String category, String message) {
        if (DEBUG) System.err.println("[XMage Access][" + category + "][debug] " + message);
    }
}
