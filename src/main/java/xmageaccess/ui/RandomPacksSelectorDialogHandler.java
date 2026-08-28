package xmageaccess.ui;

import xmageaccess.AccessibilityManager;
import xmageaccess.speech.SpeechOutput;
import xmageaccess.util.Log;

import javax.swing.JButton;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.JCheckBox;

import static xmageaccess.util.ReflectionUtils.callString;
import static xmageaccess.util.ReflectionUtils.findFieldTyped;

/**
 * Accessibility handler for XMage's RandomPacksSelectorDialog — the modal
 * dialog behind "Select sets to be included in the pool" in the new tournament
 * dialog, used by random, rich man and chaos reshuffled drafts.
 *
 * <p>It draws one JCheckBox per boostered set into a {@code java.awt.Panel}
 * laid out as a grid, with the set code as the label and the set name only in
 * the tooltip. That is several hundred checkboxes with two-to-four-letter
 * names, in a heavyweight AWT container — unreadable and barely reachable by
 * keyboard. This handler puts a spoken cursor over the same checkboxes:
 * the set name first, then its code spelled out, then whether it is in.
 *
 * <p>Ctrl+F is the way to actually build a pool: browsing several hundred sets
 * one at a time is not a plan, so it opens the same {@link AccessibleListPicker}
 * used elsewhere, filtered by typing, and lands the cursor on the chosen set.
 *
 * <p>Toggling writes {@code setSelected} straight onto the checkbox rather
 * than calling {@code doClick()}: nothing listens to these boxes (XMage only
 * reads {@code isSelected()} in {@code getSelectedPacks}), so a click would
 * add nothing but the risk the deck editor filters already demonstrated —
 * {@code doClick()} inside a key event delivers the Ctrl modifier along with
 * the ActionEvent. The three buttons are clicked, because their listeners are
 * where XMage's own select-all / select-none / apply logic lives, and none of
 * them look at modifiers.
 *
 * Keyboard shortcuts:
 *   Ctrl+Down/Up  - Next / previous set
 *   Ctrl+F        - Find a set by name or code, and go to it
 *   Ctrl+Space    - Add or remove the current set
 *   Ctrl+A        - Select all sets
 *   Ctrl+N        - Select none
 *   Ctrl+R        - Read the count and the current set
 *   Ctrl+Shift+R  - List the selected sets
 *   Ctrl+Enter    - Apply and close
 */
public class RandomPacksSelectorDialogHandler {

    /** Beyond this many, the spoken list of chosen sets is cut short. */
    private static final int LIST_LIMIT = 25;

    private final Component dialog;
    private Container pnlPacks;
    private JButton btnAll;
    private JButton btnNone;
    private JButton btnApply;
    private KeyEventDispatcher keyDispatcher;

    private int cursor = -1;

    public RandomPacksSelectorDialogHandler(Component dialog) {
        this.dialog = dialog;
    }

    public void attach() {
        try {
            pnlPacks = findFieldTyped(dialog, "pnlPacks", Container.class);
            btnAll = findFieldTyped(dialog, "btnAll", JButton.class);
            btnNone = findFieldTyped(dialog, "btnNone", JButton.class);
            btnApply = findFieldTyped(dialog, "btnApply", JButton.class);
            addKeyboardShortcuts();
            announce();
        } catch (Exception e) {
            Log.warn("RandomPacks", "attach failed", e);
        }
    }

    public void detach() {
        if (keyDispatcher != null) {
            KeyboardFocusManager.getCurrentKeyboardFocusManager()
                    .removeKeyEventDispatcher(keyDispatcher);
            keyDispatcher = null;
        }
    }

    // ---- the checkboxes --------------------------------------------------

    /** The set checkboxes, in the order XMage created them (by release date). */
    List<JCheckBox> packs() {
        List<JCheckBox> boxes = new ArrayList<>();
        if (pnlPacks == null) return boxes;
        for (Component child : pnlPacks.getComponents()) {
            if (child instanceof JCheckBox) boxes.add((JCheckBox) child);
        }
        return boxes;
    }

    private static int countSelected(List<JCheckBox> boxes) {
        int count = 0;
        for (JCheckBox box : boxes) {
            if (box.isSelected()) count++;
        }
        return count;
    }

    /** Spoken form: the set name, then its code spelled out letter by letter. */
    private static String describe(JCheckBox box) {
        String code = box.getText() != null ? box.getText().trim() : "";
        String name = box.getToolTipText() != null ? box.getToolTipText().trim() : "";
        if (name.isEmpty()) return spellOut(code);
        if (code.isEmpty()) return name;
        return name + ", " + spellOut(code);
    }

    /** Written form for the picker: both parts, so typing either one filters. */
    private static String pickerLabel(JCheckBox box) {
        String code = box.getText() != null ? box.getText().trim() : "";
        String name = box.getToolTipText() != null ? box.getToolTipText().trim() : "";
        if (name.isEmpty()) return code;
        if (code.isEmpty()) return name;
        return code + " - " + name;
    }

    /** "MH3" spoken as a word is noise; as "M H 3" it is a set code. */
    private static String spellOut(String code) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < code.length(); i++) {
            if (i > 0) sb.append(' ');
            sb.append(code.charAt(i));
        }
        return sb.toString();
    }

    private static String sets(int count) {
        return count + (count == 1 ? " set" : " sets");
    }

    // ---- announcements ---------------------------------------------------

    private void announce() {
        List<JCheckBox> boxes = packs();
        String title = callString(dialog, "getTitle");
        StringBuilder sb = new StringBuilder();
        sb.append(title != null && !title.trim().isEmpty() ? title.trim() : "Pack selector");
        sb.append(". ").append(countSelected(boxes)).append(" of ")
                .append(sets(boxes.size())).append(" selected. ");
        sb.append("Ctrl+F to find a set, Ctrl+Up and Down to browse, "
                + "Ctrl+Space to add or remove, Ctrl+A all, Ctrl+N none, "
                + "Ctrl+Enter to apply.");
        speak(sb.toString());
    }

    /** The set under the cursor, or a nudge towards choosing one. */
    private String currentDescription() {
        List<JCheckBox> boxes = packs();
        if (cursor < 0 || cursor >= boxes.size()) {
            return "No set chosen yet. Ctrl+F finds one, Ctrl+Down starts at the top.";
        }
        JCheckBox box = boxes.get(cursor);
        return describe(box) + ". "
                + (box.isSelected() ? "In the pool. " : "Not in the pool. ")
                + (cursor + 1) + " of " + boxes.size() + ".";
    }

    private void speakCurrent() {
        speak(currentDescription());
    }

    private void readSummary() {
        List<JCheckBox> boxes = packs();
        if (boxes.isEmpty()) {
            speak("No sets available.");
            return;
        }
        // One call on purpose: a second speak() cancels the first before it
        // is ever heard, so the count and the current set travel together.
        speak(countSelected(boxes) + " of " + sets(boxes.size()) + " selected. "
                + currentDescription());
    }

    private void readSelected() {
        List<JCheckBox> boxes = packs();
        List<String> chosen = new ArrayList<>();
        for (JCheckBox box : boxes) {
            if (box.isSelected()) chosen.add(describe(box));
        }
        if (chosen.isEmpty()) {
            speak("No sets selected.");
            return;
        }
        if (chosen.size() == boxes.size()) {
            speak("All " + sets(boxes.size()) + " are selected.");
            return;
        }
        StringBuilder sb = new StringBuilder(sets(chosen.size()) + " selected. ");
        int spoken = Math.min(chosen.size(), LIST_LIMIT);
        for (int i = 0; i < spoken; i++) {
            sb.append(chosen.get(i)).append(". ");
        }
        if (chosen.size() > spoken) {
            sb.append("And ").append(chosen.size() - spoken).append(" more.");
        }
        speak(sb.toString());
    }

    // ---- actions ---------------------------------------------------------

    void move(int direction) {
        List<JCheckBox> boxes = packs();
        if (boxes.isEmpty()) {
            speak("No sets available.");
            return;
        }
        int next = cursor < 0 ? (direction > 0 ? 0 : boxes.size() - 1) : cursor + direction;
        if (next < 0) {
            speak("First set.");
            return;
        }
        if (next >= boxes.size()) {
            speak("Last set.");
            return;
        }
        cursor = next;
        speakCurrent();
    }

    void toggleCurrent() {
        List<JCheckBox> boxes = packs();
        if (cursor < 0 || cursor >= boxes.size()) {
            speak("No set chosen yet. Ctrl+F finds one.");
            return;
        }
        JCheckBox box = boxes.get(cursor);
        box.setSelected(!box.isSelected());
        speak((box.isSelected() ? "Added: " : "Removed: ") + describe(box)
                + ". " + countSelected(boxes) + " of " + boxes.size() + ".");
    }

    private void findPack() {
        List<JCheckBox> boxes = packs();
        if (boxes.isEmpty()) {
            speak("No sets available.");
            return;
        }
        final List<String> items = new ArrayList<>();
        for (JCheckBox box : boxes) {
            items.add(pickerLabel(box));
        }
        Window owner = dialog instanceof Window
                ? (Window) dialog : SwingUtilities.getWindowAncestor(dialog);
        new AccessibleListPicker(owner, "Find a set", "sets", items,
                new Consumer<String>() {
                    @Override
                    public void accept(String chosen) {
                        int index = items.indexOf(chosen);
                        if (index < 0) {
                            speak("Set not found.");
                            return;
                        }
                        cursor = index;
                        speakCurrent();
                    }
                }).showPicker();
    }

    void selectAll(boolean all) {
        JButton button = all ? btnAll : btnNone;
        if (button == null || !button.isEnabled()) {
            speak("Cannot change the selection.");
            return;
        }
        button.doClick();
        List<JCheckBox> boxes = packs();
        speak(all ? "All " + sets(boxes.size()) + " selected." : "No sets selected.");
    }

    void apply() {
        List<JCheckBox> boxes = packs();
        int selected = countSelected(boxes);
        if (selected == 0) {
            // XMage's own answer to this is a JOptionPane nobody would hear.
            speak("At least one set must be selected.");
            return;
        }
        if (btnApply == null || !btnApply.isEnabled()) {
            speak("Cannot apply.");
            return;
        }
        speak("Applying " + sets(selected) + ".");
        btnApply.doClick();
    }

    // ---- keyboard --------------------------------------------------------

    private void addKeyboardShortcuts() {
        keyDispatcher = e -> {
            if (e.getID() != KeyEvent.KEY_PRESSED) return false;
            if (!isDialogActive()) return false;
            if (!e.isControlDown() || e.isAltDown()) return false;

            if (e.isShiftDown()) {
                if (e.getKeyCode() == KeyEvent.VK_R) {
                    readSelected();
                    return true;
                }
                return false;
            }

            switch (e.getKeyCode()) {
                case KeyEvent.VK_DOWN:
                    move(1);
                    return true;
                case KeyEvent.VK_UP:
                    move(-1);
                    return true;
                case KeyEvent.VK_SPACE:
                    toggleCurrent();
                    return true;
                case KeyEvent.VK_F:
                    findPack();
                    return true;
                case KeyEvent.VK_A:
                    selectAll(true);
                    return true;
                case KeyEvent.VK_N:
                    selectAll(false);
                    return true;
                case KeyEvent.VK_R:
                    readSummary();
                    return true;
                case KeyEvent.VK_ENTER:
                    apply();
                    return true;
                default:
                    return false;
            }
        };
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addKeyEventDispatcher(keyDispatcher);
    }

    /**
     * The dialog is modal and owns the keyboard while it is up, but the
     * picker opened by Ctrl+F is a window of its own — this keeps the two
     * from fighting over the same keys.
     */
    private boolean isDialogActive() {
        if (!dialog.isVisible()) return false;
        Window active = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
        if (active == null) return false;
        if (dialog instanceof Window) return dialog == active;
        return SwingUtilities.getWindowAncestor(dialog) == active;
    }

    private void speak(String text) {
        SpeechOutput speech = AccessibilityManager.getInstance().getSpeech();
        if (speech != null) speech.speak(text);
    }
}
