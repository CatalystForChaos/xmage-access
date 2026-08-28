package xmageaccess.ui;

import xmageaccess.AccessibilityManager;
import xmageaccess.speech.SpeechOutput;
import xmageaccess.util.Log;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

import static xmageaccess.util.ReflectionUtils.findFieldTyped;

/**
 * Accessibility handler for XMage's AddLandDialog — six spinners, a set combo
 * and three buttons, none of which announce themselves. The deck editor's
 * Ctrl+A opens this dialog, and limited play cannot be finished without it.
 *
 * Keyboard shortcuts:
 *   Ctrl+Left/Right - Move between Forest, Island, Mountain, Plains, Swamp, deck size
 *   Ctrl+Up/Down    - Raise or lower the current count
 *   Ctrl+S          - Suggest lands (fills the counts from the deck's colors)
 *   Ctrl+E          - Choose the set the lands come from
 *   Ctrl+R          - Read all counts
 *   Ctrl+Enter      - Add the lands
 */
public class AddLandDialogHandler {

    /** Spinner field name paired with its spoken name, in cursor order. */
    private static final String[][] FIELDS = {
            {"spnForest", "Forest"},
            {"spnIsland", "Island"},
            {"spnMountain", "Mountain"},
            {"spnPlains", "Plains"},
            {"spnSwamp", "Swamp"},
            {"spnDeckSize", "Deck size"},
    };

    private final Component dialog;
    private final List<JSpinner> spinners = new ArrayList<>();
    private final List<String> spinnerNames = new ArrayList<>();
    private JComboBox<?> cbLandSet;
    private JButton btnAutoAdd;
    private JButton btnOK;
    private int cursor = 0;
    private KeyEventDispatcher keyDispatcher;

    public AddLandDialogHandler(Component dialog) {
        this.dialog = dialog;
    }

    public void attach() {
        try {
            for (String[] field : FIELDS) {
                JSpinner spinner = findFieldTyped(dialog, field[0], JSpinner.class);
                if (spinner != null) {
                    spinners.add(spinner);
                    spinnerNames.add(field[1]);
                }
            }
            cbLandSet = findFieldTyped(dialog, "cbLandSet", JComboBox.class);
            btnAutoAdd = findFieldTyped(dialog, "btnAutoAdd", JButton.class);
            btnOK = findFieldTyped(dialog, "btnOK", JButton.class);

            addKeyboardShortcuts();
            speak("Add lands. " + countsSummary()
                    + " Ctrl+Left and Right to pick a land, Ctrl+Up and Down to change it, "
                    + "Ctrl+S to suggest lands, Ctrl+E for the set, Ctrl+Enter to add.");
        } catch (Exception e) {
            Log.warn("AddLand", "attach failed", e);
        }
    }

    public void detach() {
        if (keyDispatcher != null) {
            KeyboardFocusManager.getCurrentKeyboardFocusManager()
                    .removeKeyEventDispatcher(keyDispatcher);
            keyDispatcher = null;
        }
        spinners.clear();
        spinnerNames.clear();
    }

    private void addKeyboardShortcuts() {
        keyDispatcher = e -> {
            if (e.getID() != KeyEvent.KEY_PRESSED) return false;
            if (!dialog.isVisible()) return false;
            if (!e.isControlDown() || e.isShiftDown()) return false;

            switch (e.getKeyCode()) {
                case KeyEvent.VK_LEFT:
                    moveCursor(-1);
                    return true;
                case KeyEvent.VK_RIGHT:
                    moveCursor(1);
                    return true;
                case KeyEvent.VK_UP:
                    adjust(1);
                    return true;
                case KeyEvent.VK_DOWN:
                    adjust(-1);
                    return true;
                case KeyEvent.VK_S:
                    suggestLands();
                    return true;
                case KeyEvent.VK_E:
                    chooseSet();
                    return true;
                case KeyEvent.VK_R:
                    speak(countsSummary() + " " + setSummary());
                    return true;
                case KeyEvent.VK_ENTER:
                    addLands();
                    return true;
                default:
                    return false;
            }
        };
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addKeyEventDispatcher(keyDispatcher);
    }

    private void moveCursor(int direction) {
        if (spinners.isEmpty()) {
            speak("No land fields found.");
            return;
        }
        cursor = (cursor + direction + spinners.size()) % spinners.size();
        speak(currentDescription());
    }

    private void adjust(int direction) {
        if (spinners.isEmpty()) {
            speak("No land fields found.");
            return;
        }
        JSpinner spinner = spinners.get(cursor);
        SpinnerModel model = spinner.getModel();
        if (!(model instanceof SpinnerNumberModel)) return;

        SpinnerNumberModel numbers = (SpinnerNumberModel) model;
        int next = ((Number) numbers.getValue()).intValue() + direction;
        Comparable<?> min = numbers.getMinimum();
        Comparable<?> max = numbers.getMaximum();
        if (min instanceof Number && next < ((Number) min).intValue()) {
            speak("Minimum " + min + ".");
            return;
        }
        if (max instanceof Number && next > ((Number) max).intValue()) {
            speak("Maximum " + max + ".");
            return;
        }
        numbers.setValue(next);
        speak(currentDescription());
    }

    private String currentDescription() {
        if (spinners.isEmpty()) return "No land fields found.";
        return spinnerNames.get(cursor) + ": " + spinners.get(cursor).getValue() + ".";
    }

    private String countsSummary() {
        if (spinners.isEmpty()) return "No land fields found.";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < spinners.size(); i++) {
            sb.append(spinnerNames.get(i)).append(' ')
              .append(spinners.get(i).getValue()).append(". ");
        }
        return sb.toString().trim();
    }

    private String setSummary() {
        if (cbLandSet == null || cbLandSet.getSelectedItem() == null) return "";
        return "From set: " + cbLandSet.getSelectedItem() + ".";
    }

    private void suggestLands() {
        if (btnAutoAdd == null || !btnAutoAdd.isEnabled()) {
            speak("Suggest lands is not available.");
            return;
        }
        btnAutoAdd.doClick();
        speak("Suggested. " + countsSummary());
    }

    private void chooseSet() {
        if (cbLandSet == null || cbLandSet.getItemCount() == 0) {
            speak("No land sets available.");
            return;
        }
        final List<String> names = new ArrayList<>();
        for (int i = 0; i < cbLandSet.getItemCount(); i++) {
            Object item = cbLandSet.getItemAt(i);
            names.add(item != null ? item.toString() : "");
        }
        Window owner = SwingUtilities.getWindowAncestor(dialog);
        new AccessibleListPicker(owner, "Land Set", "sets", names, name -> {
            // Resolve by text at pick time — the combo can be rebuilt underneath us.
            for (int i = 0; i < cbLandSet.getItemCount(); i++) {
                Object item = cbLandSet.getItemAt(i);
                if (item != null && name.equals(item.toString())) {
                    cbLandSet.setSelectedIndex(i);
                    speak("Lands from " + name + ".");
                    return;
                }
            }
            speak("Set not found.");
        }).showPicker();
    }

    private void addLands() {
        if (btnOK == null || !btnOK.isEnabled()) {
            speak("Cannot add lands.");
            return;
        }
        speak("Adding lands.");
        btnOK.doClick();
    }

    private void speak(String text) {
        SpeechOutput speech = AccessibilityManager.getInstance().getSpeech();
        if (speech != null) speech.speak(text);
    }
}
