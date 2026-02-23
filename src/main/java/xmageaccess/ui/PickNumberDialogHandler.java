package xmageaccess.ui;

import xmageaccess.AccessibilityManager;
import xmageaccess.speech.SpeechOutput;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;

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

    public void detach() {}

    private void discoverComponents() {
        Class<?> clazz = dialog.getClass();
        editAmount = getField(clazz, "editAmount", JSpinner.class);
        buttonOk = getField(clazz, "buttonOk", JButton.class);
        buttonCancel = getField(clazz, "buttonCancel", JButton.class);
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
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addKeyEventDispatcher(e -> {
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
                });
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
        try {
            Field field = findField(dialog.getClass(), fieldName);
            if (field == null) return null;
            field.setAccessible(true);
            Object pane = field.get(dialog);
            if (pane instanceof JEditorPane) {
                String text = ((JEditorPane) pane).getText();
                text = text.replaceAll("<[^>]*>", "").trim();
                text = text.replaceAll("\\s+", " ");
                return text;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String readLabel(String fieldName) {
        try {
            Field field = findField(dialog.getClass(), fieldName);
            if (field == null) return null;
            field.setAccessible(true);
            Object label = field.get(dialog);
            if (label instanceof JLabel) {
                return ((JLabel) label).getText();
            }
        } catch (Exception ignored) {}
        return null;
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

    private Field findField(Class<?> clazz, String name) {
        while (clazz != null) {
            try {
                return clazz.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private <T> T getField(Class<?> clazz, String name, Class<T> type) {
        try {
            Field field = findField(clazz, name);
            if (field == null) return null;
            field.setAccessible(true);
            Object val = field.get(dialog);
            if (type.isInstance(val)) return (T) val;
        } catch (Exception ignored) {}
        return null;
    }

    private void speak(String text) {
        SpeechOutput speech = AccessibilityManager.getInstance().getSpeech();
        if (speech != null) speech.speak(text);
    }
}
