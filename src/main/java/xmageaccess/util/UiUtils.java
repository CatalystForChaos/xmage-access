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
     * True while one of the agent's own windows is the active one. Every
     * window the agent puts on screen — the accessible game, lobby, deck
     * editor and sideboarding windows, the list and deck pickers — lives in
     * the {@code xmageaccess} package, so the package name is the whole test.
     *
     * <p>The panel-level shortcuts hang off global KeyEventDispatchers, which
     * see every keystroke in the process. This is what keeps them out of
     * XMage's own window: there they were never used, and swallowing keys
     * there only takes bindings away from XMage itself. Dialog handlers are
     * deliberately not gated this way — most XMage dialogs are internal
     * frames inside that same window, and their shortcuts are the only way
     * in.
     */
    public static boolean isAgentWindowActive() {
        Window active = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
        return active != null && active.getClass().getName().startsWith("xmageaccess.");
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
