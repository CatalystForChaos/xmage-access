package xmageaccess.ui;

import xmageaccess.AccessibilityManager;
import xmageaccess.speech.SpeechOutput;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Accessibility handler for the XMage NewTournamentDialog.
 * Used for setting up drafts, sealed, and other limited formats.
 *
 * Keyboard shortcuts:
 *   Ctrl+R          - Read all current settings
 *   Ctrl+T          - Cycle tournament type forward
 *   Ctrl+Shift+T    - Cycle tournament type backward
 *   Ctrl+Up/Down    - Adjust number of players
 *   Ctrl+1/2/3      - Focus pack 1/2/3 combo (then Up/Down to change set)
 *   Ctrl+P          - Read current pack selections
 *   Ctrl+Enter      - Create tournament
 *   Escape          - Cancel (standard dialog behavior)
 */
public class NewTournamentDialogHandler {

    private final Component dialog;

    private JTextField txtName;
    private JTextField txtPassword;
    private JComboBox<?> cbTournamentType;
    private JComboBox<?> cbSkillLevel;
    private JComboBox<?> cbTimeLimit;
    private JSpinner spnNumPlayers;
    private JSpinner spnNumRounds;
    private JSpinner spnConstructTime;
    private JCheckBox cbAllowSpectators;
    private JCheckBox chkRated;
    private JButton btnOk;
    private JButton btnCancel;
    // packPanels is a List<JPanel>; each panel holds one JComboBox for set selection
    private List<?> packPanels;
    private KeyEventDispatcher keyDispatcher;

    public NewTournamentDialogHandler(Component dialog) {
        this.dialog = dialog;
    }

    public void attach() {
        try {
            discoverComponents();
            addKeyboardShortcuts();
            announceDialog();
        } catch (Exception e) {
            System.err.println("[XMage Access] Error attaching to NewTournamentDialog: " + e.getMessage());
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
        txtName = getField(dialog, "txtName", JTextField.class);
        txtPassword = getField(dialog, "txtPassword", JTextField.class);
        cbTournamentType = getField(dialog, "cbTournamentType", JComboBox.class);
        cbSkillLevel = getField(dialog, "cbSkillLevel", JComboBox.class);
        cbTimeLimit = getField(dialog, "cbTimeLimit", JComboBox.class);
        spnNumPlayers = getField(dialog, "spnNumPlayers", JSpinner.class);
        spnNumRounds = getField(dialog, "spnNumRounds", JSpinner.class);
        spnConstructTime = getField(dialog, "spnConstructTime", JSpinner.class);
        cbAllowSpectators = getField(dialog, "cbAllowSpectators", JCheckBox.class);
        chkRated = getField(dialog, "chkRated", JCheckBox.class);
        btnOk = getField(dialog, "btnOk", JButton.class);
        btnCancel = getField(dialog, "btnCancel", JButton.class);
        packPanels = getField(dialog, "packPanels", List.class);
    }

    private void announceDialog() {
        StringBuilder sb = new StringBuilder("New tournament dialog. ");
        if (cbTournamentType != null && cbTournamentType.getSelectedItem() != null) {
            sb.append("Type: ").append(cbTournamentType.getSelectedItem()).append(". ");
        }
        if (spnNumPlayers != null) {
            sb.append("Players: ").append(spnNumPlayers.getValue()).append(". ");
        }
        sb.append("Ctrl+T to change type. Ctrl+Up/Down players. Ctrl+1/2/3 to choose packs. Ctrl+R read all. Ctrl+Enter create.");
        speak(sb.toString());
    }

    private void addKeyboardShortcuts() {
        keyDispatcher = e -> {
                    if (e.getID() != KeyEvent.KEY_PRESSED) return false;
                    if (!isDialogActive()) return false;

                    if (e.isControlDown() && !e.isAltDown()) {
                        if (!e.isShiftDown()) {
                            switch (e.getKeyCode()) {
                                case KeyEvent.VK_R:
                                    readAllSettings();
                                    return true;
                                case KeyEvent.VK_T:
                                    cycleTournamentType(1);
                                    return true;
                                case KeyEvent.VK_UP:
                                    adjustPlayers(1);
                                    return true;
                                case KeyEvent.VK_DOWN:
                                    adjustPlayers(-1);
                                    return true;
                                case KeyEvent.VK_1:
                                    focusPack(0);
                                    return true;
                                case KeyEvent.VK_2:
                                    focusPack(1);
                                    return true;
                                case KeyEvent.VK_3:
                                    focusPack(2);
                                    return true;
                                case KeyEvent.VK_P:
                                    readPacks();
                                    return true;
                                case KeyEvent.VK_ENTER:
                                    createTournament();
                                    return true;
                            }
                        } else {
                            if (e.getKeyCode() == KeyEvent.VK_T) {
                                cycleTournamentType(-1);
                                return true;
                            }
                        }
                    }
                    return false;
                };
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addKeyEventDispatcher(keyDispatcher);
    }

    private void readAllSettings() {
        StringBuilder sb = new StringBuilder();
        if (txtName != null) sb.append("Name: ").append(txtName.getText().trim()).append(". ");
        if (cbTournamentType != null && cbTournamentType.getSelectedItem() != null) {
            sb.append("Type: ").append(cbTournamentType.getSelectedItem()).append(". ");
        }
        if (spnNumPlayers != null && spnNumPlayers.isVisible()) {
            sb.append("Players: ").append(spnNumPlayers.getValue()).append(". ");
        }
        if (spnNumRounds != null && spnNumRounds.isVisible()) {
            sb.append("Rounds: ").append(spnNumRounds.getValue()).append(". ");
        }
        if (spnConstructTime != null && spnConstructTime.isVisible()) {
            sb.append("Construction time: ").append(spnConstructTime.getValue()).append(" minutes. ");
        }
        if (cbTimeLimit != null && cbTimeLimit.getSelectedItem() != null) {
            sb.append("Time limit: ").append(cbTimeLimit.getSelectedItem()).append(". ");
        }
        if (cbAllowSpectators != null) {
            sb.append("Spectators: ").append(cbAllowSpectators.isSelected() ? "allowed" : "not allowed").append(". ");
        }
        if (chkRated != null) {
            sb.append("Rated: ").append(chkRated.isSelected() ? "yes" : "no").append(". ");
        }
        if (txtPassword != null && !txtPassword.getText().trim().isEmpty()) {
            sb.append("Password set. ");
        }
        sb.append(readPacksText());
        speak(sb.toString());
    }

    private void cycleTournamentType(int direction) {
        if (cbTournamentType == null) return;
        int count = cbTournamentType.getItemCount();
        if (count == 0) return;
        int next = (cbTournamentType.getSelectedIndex() + direction + count) % count;
        cbTournamentType.setSelectedIndex(next);
        speak("Type: " + cbTournamentType.getSelectedItem());
    }

    private void adjustPlayers(int delta) {
        if (spnNumPlayers == null || !spnNumPlayers.isEnabled()) {
            speak("Player count is fixed for this format.");
            return;
        }
        SpinnerNumberModel model = (SpinnerNumberModel) spnNumPlayers.getModel();
        int current = ((Number) spnNumPlayers.getValue()).intValue();
        int next = current + delta;
        int min = ((Number) model.getMinimum()).intValue();
        int max = ((Number) model.getMaximum()).intValue();
        if (next < min) next = min;
        if (next > max) next = max;
        spnNumPlayers.setValue(next);
        speak(next + " players.");
    }

    private void focusPack(int index) {
        if (packPanels == null || packPanels.isEmpty()) {
            speak("No pack selection for this format.");
            return;
        }
        if (index >= packPanels.size()) {
            speak("Only " + packPanels.size() + " packs for this format.");
            return;
        }
        Object panel = packPanels.get(index);
        if (!(panel instanceof Container)) return;
        JComboBox<?> combo = findCombo((Container) panel);
        if (combo == null) {
            speak("Pack " + (index + 1) + " not available.");
            return;
        }
        combo.requestFocusInWindow();
        String set = combo.getSelectedItem() != null ? combo.getSelectedItem().toString() : "none";
        speak("Pack " + (index + 1) + ": " + set + ". Use Up/Down to change set.");
    }

    private void readPacks() {
        speak(readPacksText());
    }

    private String readPacksText() {
        if (packPanels == null || packPanels.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("Packs: ");
        for (int i = 0; i < packPanels.size(); i++) {
            Object panel = packPanels.get(i);
            if (!(panel instanceof Container)) continue;
            JComboBox<?> combo = findCombo((Container) panel);
            String set = (combo != null && combo.getSelectedItem() != null)
                    ? combo.getSelectedItem().toString() : "none";
            sb.append("Pack ").append(i + 1).append(": ").append(set).append(". ");
        }
        return sb.toString();
    }

    private void createTournament() {
        if (btnOk != null) {
            speak("Creating tournament.");
            btnOk.doClick();
        }
    }

    private JComboBox<?> findCombo(Container container) {
        for (Component c : container.getComponents()) {
            if (c instanceof JComboBox) return (JComboBox<?>) c;
        }
        return null;
    }

    private boolean isDialogActive() {
        if (!dialog.isVisible()) return false;
        Window focused = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
        if (focused == null) return false;
        if (dialog instanceof Window) return dialog == focused;
        return SwingUtilities.getWindowAncestor(dialog) == focused;
    }

    @SuppressWarnings("unchecked")
    private <T> T getField(Object obj, String name, Class<T> type) {
        try {
            Class<?> clazz = obj.getClass();
            while (clazz != null) {
                try {
                    Field f = clazz.getDeclaredField(name);
                    f.setAccessible(true);
                    Object val = f.get(obj);
                    if (type.isInstance(val)) return (T) val;
                    return null;
                } catch (NoSuchFieldException e) {
                    clazz = clazz.getSuperclass();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void speak(String text) {
        SpeechOutput speech = AccessibilityManager.getInstance().getSpeech();
        if (speech != null) speech.speak(text);
    }
}
