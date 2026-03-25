package xmageaccess.ui;

import xmageaccess.AccessibilityManager;
import xmageaccess.speech.SpeechOutput;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;

/**
 * Accessibility handler for the XMage UserRequestDialog.
 * Used for system prompts like concede, reconnect, quit confirmation, etc.
 *
 * Keyboard shortcuts:
 *   Ctrl+1         - Press button 1
 *   Ctrl+2         - Press button 2
 *   Ctrl+3         - Press button 3
 *   Ctrl+R         - Re-read the message and buttons
 */
public class UserRequestDialogHandler {

    private final Component dialog;
    private JLabel lblText;
    private JButton btn1;
    private JButton btn2;
    private JButton btn3;
    private KeyEventDispatcher keyDispatcher;

    public UserRequestDialogHandler(Component dialog) {
        this.dialog = dialog;
    }

    public void attach() {
        try {
            discoverComponents();
            addKeyboardShortcuts();
            announceDialog();
        } catch (Exception e) {
            System.err.println("[XMage Access] Error attaching to UserRequestDialog: " + e.getMessage());
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
        Class<?> clazz = dialog.getClass();
        lblText = getField(clazz, "lblText", JLabel.class);
        btn1 = getField(clazz, "btn1", JButton.class);
        btn2 = getField(clazz, "btn2", JButton.class);
        btn3 = getField(clazz, "btn3", JButton.class);
    }

    private void announceDialog() {
        StringBuilder sb = new StringBuilder("System message. ");

        if (lblText != null) {
            String text = lblText.getText();
            if (text != null) {
                text = text.replaceAll("<[^>]*>", "").trim();
                text = text.replaceAll("\\s+", " ");
                if (!text.isEmpty()) {
                    sb.append(text).append(". ");
                }
            }
        }

        // Announce available buttons
        appendButton(sb, btn1, "Ctrl+1");
        appendButton(sb, btn2, "Ctrl+2");
        appendButton(sb, btn3, "Ctrl+3");

        speak(sb.toString());
    }

    private void appendButton(StringBuilder sb, JButton btn, String shortcut) {
        if (btn != null && btn.isVisible()) {
            String label = btn.getText();
            if (label != null && !label.isEmpty()) {
                sb.append(shortcut).append(": ").append(label).append(". ");
            }
        }
    }

    private void addKeyboardShortcuts() {
        keyDispatcher = e -> {
                    if (e.getID() != KeyEvent.KEY_PRESSED) return false;
                    if (!isDialogVisible()) return false;

                    if (e.isControlDown() && !e.isShiftDown()) {
                        switch (e.getKeyCode()) {
                            case KeyEvent.VK_1:
                                clickButton(btn1);
                                return true;
                            case KeyEvent.VK_2:
                                clickButton(btn2);
                                return true;
                            case KeyEvent.VK_3:
                                clickButton(btn3);
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

    private void clickButton(JButton btn) {
        if (btn != null && btn.isVisible() && btn.isEnabled()) {
            String label = btn.getText();
            speak(label != null ? label : "OK");
            btn.doClick();
        } else {
            speak("Button not available.");
        }
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
