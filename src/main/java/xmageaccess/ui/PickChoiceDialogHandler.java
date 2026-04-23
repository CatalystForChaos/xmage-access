package xmageaccess.ui;

import xmageaccess.AccessibilityManager;
import xmageaccess.speech.SpeechOutput;

import static xmageaccess.util.ReflectionUtils.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;

/**
 * Accessibility handler for the XMage PickChoiceDialog.
 * Used for modal choices like "choose one" card modes, card selection, etc.
 *
 * Keyboard shortcuts:
 *   Ctrl+Up/Down   - Navigate choices
 *   Ctrl+Enter     - Confirm selection
 *   Ctrl+R         - Re-read current choice and message
 *   Escape         - Cancel (if allowed)
 */
public class PickChoiceDialogHandler {

    private final Component dialog;
    private JList<?> listChoices;
    private JButton btOK;
    private JButton btCancel;
    private JTextField editSearch;
    private KeyEventDispatcher keyDispatcher;

    public PickChoiceDialogHandler(Component dialog) {
        this.dialog = dialog;
    }

    public void attach() {
        try {
            discoverComponents();
            addKeyboardShortcuts();
            announceDialog();
        } catch (Exception e) {
            System.err.println("[XMage Access] Error attaching to PickChoiceDialog: " + e.getMessage());
        }
    }

    public void detach() {
        if (keyDispatcher != null) {
            KeyboardFocusManager.getCurrentKeyboardFocusManager()
                    .removeKeyEventDispatcher(keyDispatcher);
            keyDispatcher = null;
        }
    }

    private void discoverComponents() {
        listChoices = findFieldTyped(dialog, "listChoices", JList.class);
        btOK = findFieldTyped(dialog, "btOK", JButton.class);
        btCancel = findFieldTyped(dialog, "btCancel", JButton.class);
        editSearch = findFieldTyped(dialog, "editSearch", JTextField.class);
    }

    private void announceDialog() {
        StringBuilder sb = new StringBuilder("Choice. ");

        // Read message text
        String message = readEditorPane("textMessage");
        if (message != null && !message.isEmpty()) {
            sb.append(message).append(". ");
        }

        String subMessage = readEditorPane("textSubMessage");
        if (subMessage != null && !subMessage.isEmpty()) {
            sb.append(subMessage).append(". ");
        }

        // Read choice count
        if (listChoices != null) {
            int count = listChoices.getModel().getSize();
            sb.append(count).append(" options. ");

            // Read first selected or first item
            int sel = listChoices.getSelectedIndex();
            if (sel >= 0) {
                sb.append("Selected: ").append(getChoiceText(sel)).append(". ");
            } else if (count > 0) {
                listChoices.setSelectedIndex(0);
                sb.append("First: ").append(getChoiceText(0)).append(". ");
            }
        }

        if (editSearch != null && editSearch.isVisible()) {
            sb.append("Type to search. ");
        }
        sb.append("Ctrl+Up, Down to navigate. Ctrl+Enter to choose.");

        speak(sb.toString());
    }

    private void addKeyboardShortcuts() {
        keyDispatcher = e -> {
                    if (e.getID() != KeyEvent.KEY_PRESSED) return false;
                    if (!isDialogVisible()) return false;

                    if (e.getKeyCode() == KeyEvent.VK_ESCAPE && !e.isControlDown()) {
                        if (btCancel != null && btCancel.isEnabled() && btCancel.isVisible()) {
                            btCancel.doClick();
                            return true;
                        }
                        return false;
                    }

                    if (e.isControlDown() && !e.isShiftDown()) {
                        switch (e.getKeyCode()) {
                            case KeyEvent.VK_UP:
                                navigateChoice(-1);
                                return true;
                            case KeyEvent.VK_DOWN:
                                navigateChoice(1);
                                return true;
                            case KeyEvent.VK_ENTER:
                                confirmChoice();
                                return true;
                            case KeyEvent.VK_R:
                                announceDialog();
                                return true;
                        }
                    }

                    return false;
                };
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addKeyEventDispatcher(keyDispatcher);
    }

    private void navigateChoice(int direction) {
        if (listChoices == null) return;

        int size = listChoices.getModel().getSize();
        if (size == 0) {
            speak("No choices available.");
            return;
        }

        int current = listChoices.getSelectedIndex();
        int next = current + direction;

        if (next < 0) next = 0;
        if (next >= size) next = size - 1;

        listChoices.setSelectedIndex(next);
        listChoices.ensureIndexIsVisible(next);

        String text = getChoiceText(next);
        speak((next + 1) + " of " + size + ". " + text);
    }

    private void confirmChoice() {
        if (btOK != null && btOK.isEnabled()) {
            if (listChoices != null && listChoices.getSelectedIndex() >= 0) {
                String text = getChoiceText(listChoices.getSelectedIndex());
                speak("Choosing: " + text);
            }
            btOK.doClick();
        } else {
            speak("No selection to confirm.");
        }
    }

    private String getChoiceText(int index) {
        if (listChoices == null || index < 0 || index >= listChoices.getModel().getSize()) {
            return "Unknown";
        }
        Object item = listChoices.getModel().getElementAt(index);
        if (item == null) return "Unknown";

        // Try to get the plain-text value from KeyValueItem via reflection
        String value = findFieldTyped(item, "value", String.class);
        if (value != null && !value.isEmpty()) return value;

        // Fallback: strip HTML from toString
        String text = item.toString();
        text = text.replaceAll("<[^>]*>", "").trim();
        return text.isEmpty() ? "Unknown" : text;
    }

    private String readEditorPane(String fieldName) {
        JEditorPane pane = findFieldTyped(dialog, fieldName, JEditorPane.class);
        if (pane == null) return null;
        String text = pane.getText();
        if (text == null) return null;
        return text.replaceAll("<[^>]*>", "").replaceAll("\\s+", " ").trim();
    }

    private boolean isDialogVisible() {
        if (!dialog.isVisible()) return false;
        // Walk parent hierarchy to check actual visibility
        Component c = dialog;
        while (c != null) {
            if (!c.isVisible()) return false;
            c = c.getParent();
        }
        return true;
    }

    private void speak(String text) {
        SpeechOutput speech = AccessibilityManager.getInstance().getSpeech();
        if (speech != null) {
            speech.speak(text);
        }
    }
}
