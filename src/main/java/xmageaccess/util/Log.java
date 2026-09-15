package xmageaccess.util;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Lightweight logging helper. Writes to System.err with a fixed
 * "[XMage Access][CATEGORY]" prefix, and, in parallel, to a file.
 *
 * <p>The file is the point. Launched through XMage's own launcher the
 * client has no console, so everything the agent printed was lost — which
 * is why the first round of testing could say a window did not take the
 * focus but not why. The file lands next to the client's own
 * {@code mageclient.log}, or in the user's home directory if that is not
 * writable, and is truncated at every start so it always describes the run
 * that is being asked about.
 *
 * <p>Verbose ("debug") output is gated by the system property
 * {@code xmageaccess.log=debug}; warnings are always written.
 */
public final class Log {

    private static final boolean DEBUG = "debug".equalsIgnoreCase(System.getProperty("xmageaccess.log", ""));
    private static final SimpleDateFormat STAMP = new SimpleDateFormat("HH:mm:ss.SSS");

    private static PrintWriter file;
    private static boolean fileTried = false;

    private Log() {}

    /** Log a warning about a recoverable failure. Always emitted. */
    public static void warn(String category, String message) {
        emit("[XMage Access][" + category + "] " + message);
    }

    /** Log a warning with the throwable's message attached. */
    public static void warn(String category, String message, Throwable t) {
        String tail = t != null ? " (" + t.getClass().getSimpleName() + ": " + t.getMessage() + ")" : "";
        emit("[XMage Access][" + category + "] " + message + tail);
        if (DEBUG && t != null) t.printStackTrace(System.err);
    }

    /** Log a verbose-only message. Suppressed unless -Dxmageaccess.log=debug. */
    public static void debug(String category, String message) {
        if (DEBUG) emit("[XMage Access][" + category + "][debug] " + message);
    }

    /**
     * Log something worth having in the file whether or not debug is on:
     * a window opening, a focus request, a handler attaching. These are
     * what a bug report is reconstructed from.
     */
    public static void event(String category, String message) {
        emit("[XMage Access][" + category + "] " + message);
    }

    private static void emit(String line) {
        System.err.println(line);
        PrintWriter out = fileWriter();
        if (out != null) {
            synchronized (Log.class) {
                out.println(STAMP.format(new Date()) + " " + line);
                out.flush();
            }
        }
    }

    /** Opens the log file once; a failure is remembered and not retried. */
    private static synchronized PrintWriter fileWriter() {
        if (fileTried) return file;
        fileTried = true;
        for (File dir : candidateDirs()) {
            try {
                if (dir == null || !dir.isDirectory()) continue;
                File target = new File(dir, "xmage-access.log");
                PrintWriter out = new PrintWriter(new FileWriter(target, false));
                out.println(STAMP.format(new Date()) + " [XMage Access] log opened at " + target);
                out.flush();
                file = out;
                System.err.println("[XMage Access] Logging to " + target);
                return file;
            } catch (Exception ignored) {
                // Try the next candidate.
            }
        }
        return null;
    }

    /**
     * Where the file may go, best first: beside the client's working
     * directory (which is mage-client when XMage is launched normally),
     * then the user's home.
     */
    private static File[] candidateDirs() {
        File working = null;
        File home = null;
        try {
            working = new File(System.getProperty("user.dir", "."));
        } catch (Exception ignored) {
        }
        try {
            home = new File(System.getProperty("user.home", "."));
        } catch (Exception ignored) {
        }
        return new File[]{working, home};
    }
}
