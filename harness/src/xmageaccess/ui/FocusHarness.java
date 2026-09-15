package xmageaccess.ui;

import xmageaccess.util.UiUtils;

import javax.swing.JFrame;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.DefaultKeyboardFocusManager;
import java.awt.Frame;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;

/**
 * Drives the window-focus helpers in {@code xmageaccess.util.UiUtils}.
 *
 * What it pins down: that an accessible window opening behind XMage's own
 * frame still ends up with the keyboard. Every panel-level shortcut is gated
 * on {@code isActiveWindow}, and {@code requestFocusInWindow()} is documented
 * to do nothing unless its window "is already the focused Window" — so a
 * window that is shown and then asked to focus a list, in that order, gets
 * neither. This shows the component request is held back until the window
 * reports the focus, that the listener carrying it removes itself again
 * (leaked listeners being this project's most repeated bug), that a call
 * from off the EDT is moved onto it, and that the two gating predicates
 * answer for the right windows.
 *
 * The frames are placed far off-screen and made non-focusable, so the run
 * neither steals the keyboard from the terminal nor puts anything on screen;
 * that also pins the branch that matters, the one where the window is not
 * focused. The focus event is handed to the listener directly rather than
 * dispatched, to keep the real focus manager out of a test that is about
 * what the helper does, not what the window manager decides.
 */
public class FocusHarness {

    private static int failures = 0;

    private static void check(String what, boolean ok) {
        System.out.println((ok ? "  PASS  " : "  FAIL  ") + what);
        if (!ok) failures++;
    }

    /** Records focus requests instead of making them; they would fail anyway. */
    static class RecordingField extends JTextField {
        int requests = 0;
        @Override
        public boolean requestFocusInWindow() {
            requests++;
            return false;
        }
    }

    /** A window in package xmageaccess, which is how the agent knows its own. */
    static class AgentFrame extends JFrame {
        boolean pretendFocused = false;
        @Override public boolean isFocused() { return pretendFocused; }
    }

    private static AgentFrame offScreenFrame() {
        AgentFrame frame = new AgentFrame();
        frame.setFocusableWindowState(false);
        frame.setSize(10, 10);
        frame.setLocation(-4000, -4000);
        return frame;
    }

    private static void fireGainedFocus(Window window) {
        for (WindowFocusListener l : window.getWindowFocusListeners()) {
            l.windowGainedFocus(new WindowEvent(window, WindowEvent.WINDOW_GAINED_FOCUS));
        }
    }

    public static void main(String[] args) throws Exception {
        final AgentFrame shown = offScreenFrame();
        final RecordingField field = new RecordingField();
        shown.add(field);

        System.out.println("Off the EDT");
        SwingUtilities.invokeAndWait(() -> shown.setVisible(true));
        UiUtils.focusAgentWindow(shown, field);
        check("nothing touches Swing from the calling thread",
                shown.getWindowFocusListeners().length == 0 && field.requests == 0);
        SwingUtilities.invokeAndWait(() -> {});
        check("the work lands on the EDT instead",
                shown.getWindowFocusListeners().length == 1);

        System.out.println("Waiting for the focus");
        check("the component is not asked while the window is unfocused",
                field.requests == 0);
        SwingUtilities.invokeAndWait(() -> fireGainedFocus(shown));
        check("it is asked once the window reports the focus", field.requests == 1);
        check("and the listener takes itself off again",
                shown.getWindowFocusListeners().length == 0);

        System.out.println("Already focused");
        final RecordingField second = new RecordingField();
        SwingUtilities.invokeAndWait(() -> {
            shown.add(second);
            shown.pretendFocused = true;
            UiUtils.focusAgentWindow(shown, second);
        });
        check("a focused window is asked straight away", second.requests == 1);
        check("with no listener left behind",
                shown.getWindowFocusListeners().length == 0);

        System.out.println("Windows that cannot take it");
        final RecordingField unshown = new RecordingField();
        final AgentFrame hidden = offScreenFrame();
        SwingUtilities.invokeAndWait(() -> {
            UiUtils.focusAgentWindow(null, unshown);
            UiUtils.focusAgentWindow(hidden, unshown);
        });
        check("a null window is ignored, and an invisible one too",
                unshown.requests == 0 && hidden.getWindowFocusListeners().length == 0);

        System.out.println("Minimised");
        final AgentFrame iconified = offScreenFrame();
        SwingUtilities.invokeAndWait(() -> {
            iconified.setVisible(true);
            iconified.setExtendedState(Frame.ICONIFIED);
            UiUtils.focusAgentWindow(iconified, null);
        });
        check("a minimised window is restored, not just raised",
                (iconified.getExtendedState() & Frame.ICONIFIED) == 0);

        System.out.println("Which window is active");
        final Window[] active = new Window[1];
        KeyboardFocusManager stub = new DefaultKeyboardFocusManager() {
            @Override public Window getActiveWindow() { return active[0]; }
        };
        KeyboardFocusManager.setCurrentKeyboardFocusManager(stub);
        JFrame xmageish = new JFrame();

        check("no window active means no shortcuts answer",
                !UiUtils.isActiveWindow(shown) && !UiUtils.isAgentWindowActive());
        active[0] = shown;
        check("a handler's own window answers", UiUtils.isActiveWindow(shown));
        check("another window of ours does not answer for it",
                !UiUtils.isActiveWindow(iconified));
        check("but it counts as one of ours", UiUtils.isAgentWindowActive());
        active[0] = xmageish;
        check("XMage's own window is not one of ours",
                !UiUtils.isAgentWindowActive() && !UiUtils.isActiveWindow(shown));
        check("and a null window is never active", !UiUtils.isActiveWindow(null));

        KeyboardFocusManager.setCurrentKeyboardFocusManager(null);
        SwingUtilities.invokeAndWait(() -> {
            shown.dispose();
            hidden.dispose();
            iconified.dispose();
            xmageish.dispose();
        });

        System.out.println();
        System.out.println(failures == 0 ? "ALL CHECKS PASSED" : failures + " CHECK(S) FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }
}
