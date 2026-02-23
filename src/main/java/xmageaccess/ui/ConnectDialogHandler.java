package xmageaccess.ui;

import xmageaccess.AccessibilityManager;
import xmageaccess.speech.SpeechOutput;

import javax.accessibility.AccessibleContext;
import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Accessibility handler for the XMage Connect Dialog.
 * Injects accessible names, adds focus-based speech announcements,
 * and monitors status label changes.
 */
public class ConnectDialogHandler {

    private final Component dialog;
    private final Map<Component, String> componentLabels = new LinkedHashMap<>();
    private FocusListener focusListener;
    private PropertyChangeListener statusListener;
    private JLabel statusLabel;

    public ConnectDialogHandler(Component dialog) {
        this.dialog = dialog;
    }

    public void attach() {
        try {
            discoverComponents();
            setAccessibleNames();
            addFocusAnnouncements();
            monitorStatusLabel();

            speak("Connect to server dialog. "
                    + "Tab through fields: server, port, username, password. "
                    + "Press Enter in any field to connect.");
        } catch (Exception e) {
            System.err.println("[XMage Access] Error attaching to connect dialog: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void detach() {
        if (focusListener != null) {
            for (Component comp : componentLabels.keySet()) {
                comp.removeFocusListener(focusListener);
            }
        }
        if (statusListener != null && statusLabel != null) {
            statusLabel.removePropertyChangeListener("text", statusListener);
        }
    }

    /**
     * Use reflection to find the dialog's components by field name.
     */
    private void discoverComponents() throws Exception {
        Class<?> clazz = dialog.getClass();

        // Map field names to accessible labels
        Map<String, String> fieldLabels = new LinkedHashMap<>();
        fieldLabels.put("txtServer", "Server name");
        fieldLabels.put("txtPort", "Port number");
        fieldLabels.put("txtUserName", "Username");
        fieldLabels.put("txtPassword", "Password. Leave empty for servers without registration");
        fieldLabels.put("cbFlag", "Country flag");
        fieldLabels.put("chkAutoConnect", "Auto connect checkbox");
        fieldLabels.put("btnConnect", "Connect to server");
        fieldLabels.put("btnCancel", "Cancel");
        fieldLabels.put("btnRegister", "Register new user");
        fieldLabels.put("btnForgotPassword", "Forgot password");
        fieldLabels.put("jProxySettingsButton", "Proxy settings");
        fieldLabels.put("btnWhatsNew", "Show what's new");
        fieldLabels.put("btnFlagSearch", "Search country flag");
        fieldLabels.put("btnCheckStatus", "Check server online status");
        fieldLabels.put("btnFindMain", "Connect to xmage.de, main Europe server, registration required");
        fieldLabels.put("btnFindEU", "Connect to EU server, no registration needed");
        fieldLabels.put("btnFindUs", "Connect to US server, no registration needed");
        fieldLabels.put("btnFindBeta", "Connect to Beta server, no registration needed");
        fieldLabels.put("btnFindLocal", "Connect to local server with AI");
        fieldLabels.put("btnFindOther", "Choose from server list");
        fieldLabels.put("lblStatus", null); // Status label, tracked separately

        for (Map.Entry<String, String> entry : fieldLabels.entrySet()) {
            try {
                Field field = clazz.getDeclaredField(entry.getKey());
                field.setAccessible(true);
                Component comp = (Component) field.get(dialog);
                if (comp != null && entry.getValue() != null) {
                    componentLabels.put(comp, entry.getValue());
                }
                if ("lblStatus".equals(entry.getKey()) && comp instanceof JLabel) {
                    statusLabel = (JLabel) comp;
                }
            } catch (NoSuchFieldException e) {
                System.err.println("[XMage Access] Field not found: " + entry.getKey());
            }
        }
    }

    /**
     * Set accessible names on all components so screen readers can read them.
     */
    private void setAccessibleNames() {
        for (Map.Entry<Component, String> entry : componentLabels.entrySet()) {
            Component comp = entry.getKey();
            String label = entry.getValue();
            if (comp instanceof JComponent) {
                AccessibleContext ctx = comp.getAccessibleContext();
                if (ctx != null) {
                    ctx.setAccessibleName(label);
                }
            }
        }

        // Fix the flag label association (it wrongly points to txtUserName)
        if (statusLabel != null) {
            statusLabel.getAccessibleContext().setAccessibleName("Connection status");
        }
    }

    /**
     * Add focus listeners that announce component labels via speech
     * when the user tabs to each field.
     */
    private void addFocusAnnouncements() {
        focusListener = new FocusListener() {
            @Override
            public void focusGained(FocusEvent e) {
                Component comp = e.getComponent();
                String label = componentLabels.get(comp);
                if (label == null) return;

                StringBuilder announcement = new StringBuilder(label);

                // For text fields, also read the current value
                if (comp instanceof JTextField) {
                    String text = ((JTextField) comp).getText();
                    if (text != null && !text.isEmpty()) {
                        announcement.append(": ").append(text);
                    }
                }

                // For password fields, indicate if it has content
                if (comp instanceof JPasswordField) {
                    char[] pw = ((JPasswordField) comp).getPassword();
                    if (pw != null && pw.length > 0) {
                        announcement.append(": password entered");
                    }
                }

                // For checkboxes, read the checked state
                if (comp instanceof JCheckBox) {
                    boolean selected = ((JCheckBox) comp).isSelected();
                    announcement.append(": ").append(selected ? "checked" : "not checked");
                }

                // For buttons, indicate if disabled
                if (comp instanceof JButton && !comp.isEnabled()) {
                    announcement.append(" (disabled)");
                }

                speak(announcement.toString());
            }

            @Override
            public void focusLost(FocusEvent e) {
                // No action needed
            }
        };

        for (Component comp : componentLabels.keySet()) {
            comp.addFocusListener(focusListener);
        }
    }

    /**
     * Monitor the status label for text changes and announce them.
     * This catches "Connecting...", "Connected", error messages, etc.
     */
    private void monitorStatusLabel() {
        if (statusLabel == null) return;

        statusListener = new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if ("text".equals(evt.getPropertyName())) {
                    String newText = (String) evt.getNewValue();
                    if (newText != null && !newText.isEmpty()) {
                        speak(newText);
                    }
                }
            }
        };
        statusLabel.addPropertyChangeListener("text", statusListener);
    }

    private void speak(String text) {
        SpeechOutput speech = AccessibilityManager.getInstance().getSpeech();
        if (speech != null) {
            speech.speak(text);
        }
    }
}
