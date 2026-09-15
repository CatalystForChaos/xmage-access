package xmageaccess.util;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Frame;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

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
     * True while the active window is one the agent created. Used to decide
     * whether taking the keyboard would interrupt something the user is in
     * the middle of, rather than merely lifting it out of XMage's own frame.
     */
    public static boolean isAgentWindowActive() {
        Window active = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
        return active != null && active.getClass().getName().startsWith("xmageaccess.");
    }

    /**
     * Brings one of the agent's own windows forward, makes it the active
     * window, and puts the keyboard on {@code initial} (null to leave the
     * choice to the window's focus traversal policy).
     *
     * <p>Both halves are needed. A window that opens behind XMage's frame —
     * or while a modal dialog such as the connect dialog still holds the
     * focus — arrives with a dead keyboard, because the panel-level
     * shortcuts only answer while their own window is active (see
     * {@link #isActiveWindow}). And {@code requestFocusInWindow()} alone
     * cannot fix that: it is documented to focus the component only "if this
     * Component's top-level ancestor is already the focused Window", and
     * returns false everywhere else, leaving the caret where it was.
     *
     * <p>Activation is a round trip through the window manager, so the
     * component request waits for the window to report the focus rather than
     * firing blind behind {@code toFront()}.
     */
    public static void focusAgentWindow(final Window window, final Component initial) {
        if (window == null) return;
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override public void run() { focusAgentWindow(window, initial); }
            });
            return;
        }
        if (!window.isVisible()) return;

        if (window instanceof Frame) {
            Frame frame = (Frame) window;
            if ((frame.getExtendedState() & Frame.ICONIFIED) != 0) {
                frame.setExtendedState(frame.getExtendedState() & ~Frame.ICONIFIED);
            }
        }

        Window active = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
        Log.event("Focus", "asking for " + window.getClass().getSimpleName()
                + "; active now: " + (active == null ? "none" : active.getClass().getName())
                + "; already focused: " + window.isFocused());

        if (initial != null) {
            if (window.isFocused()) {
                initial.requestFocusInWindow();
            } else {
                // Installed before the activation request, so the event
                // cannot arrive between the two. Focus events are delivered
                // on this thread, so there is no race to lose.
                window.addWindowFocusListener(new WindowAdapter() {
                    @Override
                    public void windowGainedFocus(WindowEvent e) {
                        window.removeWindowFocusListener(this);
                        Log.event("Focus", window.getClass().getSimpleName()
                                + " got the focus; handing it to " + describe(initial));
                        initial.requestFocusInWindow();
                    }
                });
            }
        }

        window.toFront();
        window.requestFocus();
    }

    /**
     * A component as a log line should name it. The zone lists are anonymous
     * JList subclasses with an empty simple name, which is why the log of
     * 2 September read "handing it to " and nothing after it. The type falls
     * back to the nearest named superclass, and the accessible name — a
     * zone's own name — is added when there is one.
     */
    public static String describe(Component c) {
        if (c == null) return "nothing";
        Class<?> type = c.getClass();
        while (type.getSimpleName().isEmpty() && type.getSuperclass() != null) {
            type = type.getSuperclass();
        }
        String name = null;
        try {
            if (c.getAccessibleContext() != null) {
                name = c.getAccessibleContext().getAccessibleName();
            }
        } catch (RuntimeException ignored) {
            // Not worth losing the log line over.
        }
        return name != null && !name.isEmpty()
                ? type.getSimpleName() + " \"" + name + "\""
                : type.getSimpleName();
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
