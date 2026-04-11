package xmageaccess.ui;

import xmageaccess.AccessibilityManager;
import xmageaccess.speech.SpeechOutput;

import static xmageaccess.util.ReflectionUtils.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;

/**
 * Accessibility handler for the XMage PickNumberDialog.
 * Used for selecting numbers (X costs, damage amounts, etc).
 *
 * Keyboard shortcuts:
 *   Ctrl+Up/Down   - Increase/decrease number
 *   Ctrl+Enter     - Confirm number
 *   Ctrl+R         - Re-read current value and range
 *   Escape         - Cancel (if allowed)
 */
public class PickNumberDialogHandler {

    private final Component dialog;
    private JSpinner editAmount;
    private JButton buttonOk;
    private JButton buttonCancel;
    private KeyEventDispatcher keyDispatcher;

    public PickNumberDialogHandler(Component dialog) {
        this.dialog = dialog;
    }

    public void attach() {
        try {
            discoverComponents();
            addKeyboardShortcuts();
            announceDialog();
        } catch (Exception e) {
            System.err.println("[XMage Access] Error attaching to PickNumberDialog: " + e.getMessage());
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
        editAmount = findFieldTyped(dialog,"editAmount", JSpinner.class);
        buttonOk = findFieldTyped(dialog,"buttonOk", JButton.class);
        buttonCancel = findFieldTyped(dialog,"buttonCancel", JButton.class);
    }

    private void announceDialog() {
        StringBuilder sb = new StringBuilder("Number selection. ");

        // Read message
        String message = readEditorPane("textMessage");
        if (message != null && !message.isEmpty()) {
            sb.append(message).append(". ");
        }

        // Read limits label
        String limits = readLabel("labelLimits");
        if (limits != null && !limits.isEmpty()) {
            sb.append("Range: ").append(limits).append(". ");
        }

        // Read current value
        if (editAmount != null) {
            sb.append("Current value: ").append(editAmount.getValue()).append(". ");
        }

        sb.append("Ctrl+Up, Down to adjust. Ctrl+Enter to confirm.");
        speak(sb.toString());
    }

    private void addKeyboardShortcuts() {
        keyDispatcher = e -> {
                    if (e.getID() != KeyEvent.KEY_PRESSED) return false;
                    if (!isDialogVisible()) return false;

                    if (e.isControlDown() && !e.isShiftDown()) {
                        switch (e.getKeyCode()) {
                            case KeyEvent.VK_UP:
                                adjustNumber(1);
                                return true;
                            case KeyEvent.VK_DOWN:
                                adjustNumber(-1);
                                return true;
                            case KeyEvent.VK_ENTER:
                                confirmNumber();
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

    private void adjustNumber(int direction) {
        if (editAmount == null) return;

        SpinnerModel model = editAmount.getModel();
        if (model instanceof SpinnerNumberModel) {
            SpinnerNumberModel numModel = (SpinnerNumberModel) model;
            int current = ((Number) numModel.getValue()).intValue();
            int next = current + direction;

            Comparable<?> min = numModel.getMinimum();
            Comparable<?> max = numModel.getMaximum();

            if (min instanceof Number && next < ((Number) min).intValue()) {
                speak("Minimum: " + min);
                return;
            }
            if (max instanceof Number && next > ((Number) max).intValue()) {
                speak("Maximum: " + max);
                return;
            }

            numModel.setValue(next);
            speak(String.valueOf(next));
        }
    }

    private void confirmNumber() {
        if (buttonOk != null && buttonOk.isEnabled()) {
            String val = editAmount != null ? editAmount.getValue().toString() : "unknown";
            speak("Choosing: " + val);
            buttonOk.doClick();
        } else {
            speak("Cannot confirm.");
        }
    }

    private String readEditorPane(String fieldName) {
        JEditorPane pane = findFieldTyped(dialog, fieldName, JEditorPane.class);
        if (pane == null) return null;
        String text = pane.getText();
        if (text == null) return null;
        return text.replaceAll("<[^>]*>", "").replaceAll("\\s+", " ").trim();
    }

    private String readLabel(String fieldName) {
        JLabel lbl = findFieldTyped(dialog, fieldName, JLabel.class);
        return lbl != null ? lbl.getText() : null;
    }

    private boolean isDialogVisible() {
        if (!dialog.isVisible()) return false;
        Component c = dialog;
        while (c != null) {
            if (!c.isVisible()) return false;
            c = c.getParent();
        }
        return true;
    }

    private void speak(String text) {
        SpeechOutput speech = AccessibilityManager.getInstance().getSpeech();
        if (speech != null) speech.speak(text);
    }
}
