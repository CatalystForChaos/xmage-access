package xmageaccess.ui;

import xmageaccess.AccessibilityManager;
import xmageaccess.speech.SpeechOutput;
import xmageaccess.util.Log;

import javax.accessibility.AccessibleContext;
import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.LinkedHashMap;
import java.util.Map;

import static xmageaccess.util.ReflectionUtils.findFieldTyped;

/**
 * Generic handler for XMage's plain form dialogs: announce every field as it
 * receives focus, speak status-label changes as they happen, and offer a
 * summary plus a submit shortcut.
 *
 * <p>Used for dialogs whose whole interaction is "fill in fields, press a
 * button" — registration and password reset — where a dedicated handler would
 * be the same 150 lines with different strings.
 *
 * Keyboard shortcuts:
 *   Ctrl+R            - Read all fields and their current values
 *   Ctrl+Enter        - Press the dialog's submit button
 *   Ctrl+Shift+Enter  - Press the secondary button, where the dialog has one
 */
public class FormDialogHandler {

    private final Component dialog;
    private final String intro;
    private final Map<String, String> fieldLabels;
    private final String submitField;
    private final String submitLabel;
    private String secondaryField;
    private String secondaryLabel;

    private final Map<Component, String> componentLabels = new LinkedHashMap<>();
    private JLabel statusLabel;
    private FocusListener focusListener;
    private PropertyChangeListener statusListener;
    private KeyEventDispatcher keyDispatcher;

    /**
     * @param fieldLabels XMage field name to spoken label, in tab order
     * @param submitField field name of the button Ctrl+Enter should press
     */
    FormDialogHandler(Component dialog, String intro,
                      Map<String, String> fieldLabels,
                      String submitField, String submitLabel) {
        this.dialog = dialog;
        this.intro = intro;
        this.fieldLabels = fieldLabels;
        this.submitField = submitField;
        this.submitLabel = submitLabel;
    }

    /** Adds a second button on Ctrl+Shift+Enter, for two-step dialogs. */
    FormDialogHandler withSecondaryAction(String field, String label) {
        this.secondaryField = field;
        this.secondaryLabel = label;
        return this;
    }

    public void attach() {
        try {
            discoverComponents();
            addFocusAnnouncements();
            monitorStatusLabel();
            addKeyboardShortcuts();
            speak(intro + " Tab through the fields. Ctrl+R reads them all, "
                    + "Ctrl+Enter is " + submitLabel + "."
                    + (secondaryField != null
                        ? " Ctrl+Shift+Enter is " + secondaryLabel + "." : ""));
        } catch (Exception e) {
            Log.warn("Form", "attach failed for " + dialog.getClass().getSimpleName(), e);
        }
    }

    public void detach() {
        if (focusListener != null) {
            for (Component comp : componentLabels.keySet()) {
                comp.removeFocusListener(focusListener);
            }
            focusListener = null;
        }
        if (statusListener != null && statusLabel != null) {
            statusLabel.removePropertyChangeListener("text", statusListener);
            statusListener = null;
        }
        if (keyDispatcher != null) {
            KeyboardFocusManager.getCurrentKeyboardFocusManager()
                    .removeKeyEventDispatcher(keyDispatcher);
            keyDispatcher = null;
        }
        componentLabels.clear();
    }

    private void discoverComponents() {
        for (Map.Entry<String, String> entry : fieldLabels.entrySet()) {
            Component comp = findFieldTyped(dialog, entry.getKey(), Component.class);
            if (comp == null) {
                Log.debug("Form", "field not found: " + entry.getKey());
                continue;
            }
            componentLabels.put(comp, entry.getValue());
            AccessibleContext ctx = comp.getAccessibleContext();
            if (ctx != null) ctx.setAccessibleName(entry.getValue());
        }
        statusLabel = findFieldTyped(dialog, "lblStatus", JLabel.class);
        if (statusLabel != null) {
            statusLabel.getAccessibleContext().setAccessibleName("Status");
        }
    }

    private void addFocusAnnouncements() {
        focusListener = new FocusListener() {
            @Override
            public void focusGained(FocusEvent e) {
                String label = componentLabels.get(e.getComponent());
                if (label != null) speak(describe(e.getComponent(), label));
            }

            @Override
            public void focusLost(FocusEvent e) {
                // nothing to announce
            }
        };
        for (Component comp : componentLabels.keySet()) {
            comp.addFocusListener(focusListener);
        }
    }

    /** Label plus whatever the component currently holds. */
    private static String describe(Component comp, String label) {
        StringBuilder sb = new StringBuilder(label);
        if (comp instanceof JPasswordField) {
            char[] pw = ((JPasswordField) comp).getPassword();
            if (pw != null && pw.length > 0) sb.append(": password entered");
        } else if (comp instanceof JTextField) {
            String text = ((JTextField) comp).getText();
            if (text != null && !text.isEmpty()) sb.append(": ").append(text);
        } else if (comp instanceof JCheckBox) {
            sb.append(": ").append(((JCheckBox) comp).isSelected() ? "checked" : "not checked");
        }
        if (comp instanceof JButton && !comp.isEnabled()) sb.append(" (disabled)");
        return sb.toString();
    }

    private void monitorStatusLabel() {
        if (statusLabel == null) return;
        statusListener = new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                Object value = evt.getNewValue();
                if (value instanceof String && !((String) value).isEmpty()) {
                    speak((String) value);
                }
            }
        };
        statusLabel.addPropertyChangeListener("text", statusListener);
    }

    private void addKeyboardShortcuts() {
        keyDispatcher = e -> {
            if (e.getID() != KeyEvent.KEY_PRESSED) return false;
            if (!dialog.isVisible()) return false;
            if (!e.isControlDown()) return false;

            if (e.isShiftDown()) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER && secondaryField != null) {
                    press(secondaryField, secondaryLabel);
                    return true;
                }
                return false;
            }

            switch (e.getKeyCode()) {
                case KeyEvent.VK_R:
                    readAllFields();
                    return true;
                case KeyEvent.VK_ENTER:
                    press(submitField, submitLabel);
                    return true;
                default:
                    return false;
            }
        };
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addKeyEventDispatcher(keyDispatcher);
    }

    private void readAllFields() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Component, String> entry : componentLabels.entrySet()) {
            if (entry.getKey() instanceof JButton) continue;
            sb.append(describe(entry.getKey(), entry.getValue())).append(". ");
        }
        if (statusLabel != null && statusLabel.getText() != null
                && !statusLabel.getText().isEmpty()) {
            sb.append("Status: ").append(statusLabel.getText()).append(".");
        }
        speak(sb.length() == 0 ? "No fields found." : sb.toString());
    }

    private void press(String fieldName, String label) {
        JButton button = findFieldTyped(dialog, fieldName, JButton.class);
        if (button == null || !button.isEnabled()) {
            speak("Cannot " + label + " yet.");
            return;
        }
        speak(label + ".");
        button.doClick();
    }

    private void speak(String text) {
        SpeechOutput speech = AccessibilityManager.getInstance().getSpeech();
        if (speech != null) speech.speak(text);
    }
}
