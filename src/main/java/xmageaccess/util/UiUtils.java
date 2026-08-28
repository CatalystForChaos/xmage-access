package xmageaccess.util;

import javax.swing.JFrame;
import java.awt.KeyboardFocusManager;
import java.awt.Window;

/**
 * Shared window/focus helpers.
 */
public final class UiUtils {

    private UiUtils() {}

    /**
     * True while the given window is the active one, false if it is null.
     *
     * <p>The panel-level shortcuts hang off global KeyEventDispatchers, which
     * see every keystroke in the process. This is what keeps each set where
     * it belongs: inside XMage's own window they were never used and only
     * took bindings away from XMage, and asking merely whether *some* agent
     * window is active is not enough either — the lobby panel stays visible
     * behind a draft, so the lobby's keys would otherwise answer inside the
     * draft window. Each handler names its own window instead.
     *
     * <p>Dialog handlers are deliberately not gated this way: most XMage
     * dialogs are internal frames inside XMage's window, and their shortcuts
     * are the only way in.
     */
    public static boolean isActiveWindow(Window window) {
        if (window == null) return false;
        return KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow() == window;
    }

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
