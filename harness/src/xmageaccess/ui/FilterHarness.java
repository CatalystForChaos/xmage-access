package xmageaccess.ui;

import javax.swing.*;
import java.awt.EventQueue;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Reproduces the deck editor filter crash and checks the fix.
 *
 * The stand-in buttons carry the same listener logic as XMage's CardSelector:
 * a Ctrl or Alt modifier on the incoming ActionEvent means "select all the
 * other filters instead", and every notification ends in filterCards() — the
 * call that walks the card database.
 */
public class FilterHarness {

    static int filterCardsCalls = 0;
    static final List<JToggleButton> group = new ArrayList<>();
    static int lastSeenModifiers = -1;

    /** Mirrors CardSelector.filterCardsColor / filterCardsType / filterCardsRarity. */
    static ActionListener xmageFilterListener = new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
            lastSeenModifiers = e.getModifiers();
            boolean ctrlOrAlt = (e.getModifiers() & ActionEvent.ALT_MASK) == ActionEvent.ALT_MASK
                    || (e.getModifiers() & ActionEvent.CTRL_MASK) == ActionEvent.CTRL_MASK;
            if (ctrlOrAlt) {
                boolean invert = (e.getModifiers() & ActionEvent.ALT_MASK) == ActionEvent.ALT_MASK;
                for (JToggleButton other : group) {
                    boolean same = other.getActionCommand().equals(e.getActionCommand());
                    other.setSelected(invert == same);
                }
            }
            filterCardsCalls++;
        }
    };

    static JToggleButton makeButton(String command) {
        JToggleButton button = new JToggleButton();
        button.setActionCommand(command);
        button.setSelected(true); // XMage's default for all 18 filters
        button.addActionListener(xmageFilterListener);
        group.add(button);
        return button;
    }

    /** A component that runs the given action while an InputEvent is dispatched. */
    static class EventDrivenPanel extends JPanel {
        Runnable action;
        EventDrivenPanel() { enableEvents(java.awt.AWTEvent.MOUSE_EVENT_MASK); }
        @Override
        protected void processMouseEvent(java.awt.event.MouseEvent e) {
            if (action != null) action.run();
        }
    }

    /**
     * Runs the action while the event queue is dispatching an InputEvent that
     * carries Ctrl and Shift — the situation every one of our shortcuts creates,
     * and the one DefaultButtonModel.setPressed copies its modifiers from.
     *
     * A mouse event is used rather than a key event only because key events
     * posted to an unfocused component are swallowed by the focus manager;
     * setPressed treats both the same, as InputEvent.
     */
    static void underCtrlInputEvent(Runnable action) throws Exception {
        EventDrivenPanel panel = new EventDrivenPanel();
        panel.action = action;
        java.awt.event.MouseEvent event = new java.awt.event.MouseEvent(panel,
                java.awt.event.MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
                KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK,
                1, 1, 1, false);
        Toolkit.getDefaultToolkit().getSystemEventQueue().postEvent(event);
        EventQueue.invokeAndWait(() -> { });
    }

    /** Guards against the harness silently not running the action at all. */
    static void checkHarnessRan() {
        check("(harness) the action ran inside the event dispatch", lastSeenModifiers != -1);
    }

    static int failures = 0;

    static void check(String what, boolean ok) {
        System.out.println((ok ? "  PASS  " : "  FAIL  ") + what);
        if (!ok) failures++;
    }

    static void reset() {
        filterCardsCalls = 0;
        lastSeenModifiers = -1;
        for (JToggleButton b : group) b.setSelected(true);
    }

    public static void main(String[] args) throws Exception {
        JToggleButton white = makeButton("White");
        JToggleButton blue = makeButton("Blue");
        JToggleButton black = makeButton("Black");
        JToggleButton red = makeButton("Red");
        JToggleButton green = makeButton("Green");

        System.out.println("The defect: doClick() inside a Ctrl shortcut");
        reset();
        underCtrlInputEvent(() -> white.doClick());
        checkHarnessRan();
        check("XMage sees the Ctrl modifier it never got from a click",
                (lastSeenModifiers & ActionEvent.CTRL_MASK) == ActionEvent.CTRL_MASK);
        check("the filter we meant to toggle ends up OFF", !white.isSelected());
        check("and every other colour is switched ON instead",
                blue.isSelected() && black.isSelected() && red.isSelected() && green.isSelected());

        System.out.println("The fix: setSelected plus an unmodified notification");
        reset();
        underCtrlInputEvent(() -> {
            white.setSelected(!white.isSelected());
            AccessibleDeckEditorWindow.fireUnmodified(white);
        });
        check("XMage sees no modifiers", lastSeenModifiers == 0);
        check("only the chosen filter changed", !white.isSelected());
        check("the other colours are untouched",
                blue.isSelected() && black.isSelected() && red.isSelected() && green.isSelected());
        check("the card database was walked exactly once", filterCardsCalls == 1);

        System.out.println("Toggling back on");
        reset();
        white.setSelected(false);
        filterCardsCalls = 0;
        underCtrlInputEvent(() -> {
            white.setSelected(!white.isSelected());
            AccessibleDeckEditorWindow.fireUnmodified(white);
        });
        check("the filter comes back on", white.isSelected());
        check("still one pass over the database", filterCardsCalls == 1);

        System.out.println("Resetting every filter at once");
        reset();
        white.setSelected(false);
        blue.setSelected(false);
        red.setSelected(false);
        filterCardsCalls = 0;
        underCtrlInputEvent(() -> {
            for (JToggleButton b : group) b.setSelected(true);   // silent
            AccessibleDeckEditorWindow.fireUnmodified(group.get(0)); // one refresh
        });
        check("setSelected alone notifies XMage of nothing, so the batch is silent",
                filterCardsCalls == 1);
        check("all filters are back on",
                white.isSelected() && blue.isSelected() && black.isSelected()
                        && red.isSelected() && green.isSelected());

        System.out.println("What the old reset loop did with the same five buttons");
        reset();
        white.setSelected(false);
        blue.setSelected(false);
        red.setSelected(false);
        filterCardsCalls = 0;
        underCtrlInputEvent(() -> {
            for (JToggleButton b : group) {
                if (b.isSelected()) b.doClick();
            }
        });
        System.out.println("        old loop ran filterCards() " + filterCardsCalls
                + " times over 5 buttons (18 in the real editor)");
        check("the old loop ran more than one database pass", filterCardsCalls > 1);

        System.out.println();
        System.out.println(failures == 0 ? "ALL CHECKS PASSED" : failures + " CHECK(S) FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }
}
