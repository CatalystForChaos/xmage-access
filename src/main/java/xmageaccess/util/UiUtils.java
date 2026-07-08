package xmageaccess.util;

import javax.swing.JFrame;
import java.awt.Window;

/**
 * Shared window/focus helpers.
 */
public final class UiUtils {

    private UiUtils() {}

    /**
     * Brings the XMage main window to the front, preferring the real
     * MageFrame over arbitrary windows (which could be one of our own
     * accessible windows). Returns true if a window was focused.
     */
    public static boolean focusXMageWindow(Window self) {
        try {
            Object mageFrame = Class.forName("mage.client.MageFrame")
                    .getMethod("getInstance").invoke(null);
            if (mageFrame instanceof Window && mageFrame != self && ((Window) mageFrame).isVisible()) {
                ((Window) mageFrame).toFront();
                ((Window) mageFrame).requestFocus();
                return true;
            }
        } catch (Exception ignored) {
            // MageFrame not available, fall through
        }
        for (Window w : Window.getWindows()) {
            if (w != self && w.isVisible() && w.getClass().getName().startsWith("mage.")) {
                w.toFront();
                w.requestFocus();
                return true;
            }
        }
        for (Window w : Window.getWindows()) {
            if (w != self && w.isVisible() && w instanceof JFrame
                    && !w.getClass().getName().startsWith("xmageaccess.")) {
                w.toFront();
                w.requestFocus();
                return true;
            }
        }
        return false;
    }
}
