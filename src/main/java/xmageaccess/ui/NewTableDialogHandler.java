package xmageaccess.ui;

import xmageaccess.AccessibilityManager;
import xmageaccess.speech.SpeechOutput;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Accessibility handler for the XMage New Table Dialog.
 * Provides speech announcements for all fields and keyboard navigation.
 *
 * Keyboard shortcuts:
 *   Ctrl+Enter - Create game (click OK)
 *   Ctrl+Shift+I - Read current settings summary
 *   Ctrl+D - Open accessible deck picker for your deck
 *   Ctrl+Shift+D - Open accessible deck picker for opponent deck
 */
public class NewTableDialogHandler {

    private final Component dialog;
    private final Map<Component, String> componentLabels = new LinkedHashMap<>();
    private FocusListener focusListener;

    // Key components we need to interact with
    private JComboBox<?> cbGameType;
    private JComboBox<?> cbDeckType;
    private JTextField txtName;
    private JButton btnOK;
    private JButton btnCancel;
    private JCheckBox chkRollbackTurnsAllowed;
    private JCheckBox chkSpectatorsAllowed;
    private JCheckBox chkRated;
    private JSpinner spnNumWins;
    private JComboBox<?> cbTimeLimit;
    private JComboBox<?> cbSkillLevel;
    private Container pnlOtherPlayers;
    private Component player1Panel;

    public NewTableDialogHandler(Component dialog) {
        this.dialog = dialog;
    }

    public void attach() {
        try {
            discoverComponents();
            addFocusAnnouncements();
            addKeyboardShortcuts();

            // Build welcome message
            StringBuilder welcome = new StringBuilder("New game dialog. ");

            // Read current game type
            if (cbGameType != null && cbGameType.getSelectedItem() != null) {
                welcome.append("Game type: ").append(cbGameType.getSelectedItem()).append(". ");
            }
            if (cbDeckType != null && cbDeckType.getSelectedItem() != null) {
                welcome.append("Deck type: ").append(cbDeckType.getSelectedItem()).append(". ");
            }

            welcome.append("Tab through options. Ctrl+Enter to create. Ctrl+Shift+I for summary.");
            speak(welcome.toString());

        } catch (Exception e) {
            System.err.println("[XMage Access] Error attaching to new table dialog: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void detach() {
        if (focusListener != null) {
            for (Component comp : componentLabels.keySet()) {
                comp.removeFocusListener(focusListener);
            }
        }
    }

    private void discoverComponents() throws Exception {
        Class<?> clazz = dialog.getClass();

        // Main controls
        cbGameType = getField(clazz, "cbGameType", JComboBox.class);
        cbDeckType = getField(clazz, "cbDeckType", JComboBox.class);
        txtName = getField(clazz, "txtName", JTextField.class);
        btnOK = getField(clazz, "btnOK", JButton.class);
        btnCancel = getField(clazz, "btnCancel", JButton.class);
        chkRollbackTurnsAllowed = getField(clazz, "chkRollbackTurnsAllowed", JCheckBox.class);
        chkSpectatorsAllowed = getField(clazz, "chkSpectatorsAllowed", JCheckBox.class);
        chkRated = getField(clazz, "chkRated", JCheckBox.class);
        spnNumWins = getField(clazz, "spnNumWins", JSpinner.class);
        cbTimeLimit = getField(clazz, "cbTimeLimit", JComboBox.class);
        cbSkillLevel = getField(clazz, "cbSkillLevel", JComboBox.class);
        pnlOtherPlayers = getField(clazz, "pnlOtherPlayers", Container.class);
        player1Panel = getField(clazz, "player1Panel", Component.class);

        // Map components to labels
        addLabel(txtName, "Game name");
        addLabel(cbGameType, "Game type");
        addLabel(cbDeckType, "Deck type");
        addLabel(cbTimeLimit, "Time limit");
        addLabel(cbSkillLevel, "Skill level");
        addLabel(spnNumWins, "Number of wins needed");
        addLabel(chkRollbackTurnsAllowed, "Rollbacks allowed");
        addLabel(chkSpectatorsAllowed, "Spectators allowed");
        addLabel(chkRated, "Rated game");
        addLabel(btnOK, "Create game");
        addLabel(btnCancel, "Cancel");

        // Additional fields that may exist
        addFieldLabel(clazz, "txtPassword", "Password");
        addFieldLabel(clazz, "cbBufferTime", "Buffer time");
        addFieldLabel(clazz, "spnNumPlayers", "Number of players");
        addFieldLabel(clazz, "spnQuitRatio", "Allowed quit percentage");
        addFieldLabel(clazz, "spnMinimumRating", "Minimum rating");
        addFieldLabel(clazz, "spnEdhPowerLevel", "Commander power level");
        addFieldLabel(clazz, "cbRange", "Range of influence");
        addFieldLabel(clazz, "cbAttackOption", "Attack option");
        addFieldLabel(clazz, "btnCustomOptions", "Custom options");
        addFieldLabel(clazz, "btnSettingsLoad", "Load settings");
        addFieldLabel(clazz, "btnSettingsSave", "Save settings");

        // Discover player panel components
        discoverPlayerPanels();
    }

    /**
     * Find and label components inside the player configuration panels.
     */
    private void discoverPlayerPanels() {
        // Player 1 panel (your settings)
        if (player1Panel != null) {
            discoverNewPlayerPanel(player1Panel, "Your");
        }

        // Other player panels (AI/human opponents)
        if (pnlOtherPlayers != null) {
            int playerNum = 2;
            for (Component child : pnlOtherPlayers.getComponents()) {
                String prefix = "Player " + playerNum;
                // TablePlayerPanel contains a combo for player type and a NewPlayerPanel
                discoverTablePlayerPanel(child, prefix);
                playerNum++;
            }
        }
    }

    private void discoverTablePlayerPanel(Component panel, String prefix) {
        if (panel == null) return;
        try {
            Class<?> clazz = panel.getClass();

            // Player type combo (Human/Computer Mad/Computer Monte Carlo, etc.)
            JComboBox<?> cbPlayerType = getFieldFromObject(panel, "cbPlayerType", JComboBox.class);
            if (cbPlayerType != null) {
                addLabel(cbPlayerType, prefix + " type");
            }

            // The NewPlayerPanel within this TablePlayerPanel
            Component newPlayerPanel = getFieldFromObject(panel, "newPlayerPanel", Component.class);
            if (newPlayerPanel == null) {
                newPlayerPanel = getFieldFromObject(panel, "playerPanel", Component.class);
            }
            if (newPlayerPanel != null) {
                discoverNewPlayerPanel(newPlayerPanel, prefix);
            }
        } catch (Exception e) {
            // Non-fatal, panel structure may differ
        }
    }

    private void discoverNewPlayerPanel(Component panel, String prefix) {
        if (panel == null) return;
        try {
            // Deck file text field (field name is txtPlayerDeck in NewPlayerPanel)
            JTextField txtDeck = getFieldFromObject(panel, "txtPlayerDeck", JTextField.class);
            if (txtDeck != null) {
                addLabel(txtDeck, prefix + " deck file");
            }

            // Browse button for deck (field name is btnPlayerDeck in NewPlayerPanel)
            JButton btnDeckBrowse = getFieldFromObject(panel, "btnPlayerDeck", JButton.class);
            if (btnDeckBrowse != null) {
                addLabel(btnDeckBrowse, prefix + " browse deck");
            }

            // Generate deck button
            JButton btnGenerate = getFieldFromObject(panel, "btnGenerate", JButton.class);
            if (btnGenerate != null) {
                addLabel(btnGenerate, prefix + " generate random deck");
            }

            // Skill level spinner
            JSpinner spnLevel = getFieldFromObject(panel, "spnLevel", JSpinner.class);
            if (spnLevel != null) {
                addLabel(spnLevel, prefix + " AI skill level");
            }
        } catch (Exception e) {
            // Non-fatal
        }
    }

    private void addFocusAnnouncements() {
        focusListener = new FocusListener() {
            @Override
            public void focusGained(FocusEvent e) {
                Component comp = e.getComponent();
                String label = componentLabels.get(comp);
                if (label == null && comp.getParent() != null) {
                    // Try parent - spinner editors have the focus, not the spinner itself
                    label = componentLabels.get(comp.getParent());
                    if (label == null && comp.getParent().getParent() != null) {
                        // Try grandparent - JFormattedTextField -> DefaultEditor -> JSpinner
                        label = componentLabels.get(comp.getParent().getParent());
                    }
                }
                if (label == null) return;

                StringBuilder announcement = new StringBuilder(label);

                // Find the actual widget to read value from (may differ from focused comp for spinners)
                Component widget = componentLabels.containsKey(comp) ? comp : null;
                if (widget == null && comp.getParent() != null) {
                    widget = componentLabels.containsKey(comp.getParent()) ? comp.getParent() : null;
                    if (widget == null && comp.getParent().getParent() != null) {
                        widget = componentLabels.containsKey(comp.getParent().getParent()) ? comp.getParent().getParent() : null;
                    }
                }
                if (widget == null) widget = comp;

                if (widget instanceof JComboBox) {
                    Object selected = ((JComboBox<?>) widget).getSelectedItem();
                    if (selected != null) {
                        announcement.append(": ").append(selected);
                    }
                } else if (widget instanceof JSpinner) {
                    Object val = ((JSpinner) widget).getValue();
                    if (val != null) {
                        announcement.append(": ").append(val);
                    }
                } else if (widget instanceof JTextField) {
                    String text = ((JTextField) widget).getText();
                    if (text != null && !text.isEmpty()) {
                        announcement.append(": ").append(text);
                    }
                } else if (widget instanceof JCheckBox) {
                    boolean checked = ((JCheckBox) widget).isSelected();
                    announcement.append(": ").append(checked ? "checked" : "not checked");
                } else if (widget instanceof JButton && !widget.isEnabled()) {
                    announcement.append(" (disabled)");
                }

                // Set accessible name too
                if (comp instanceof JComponent) {
                    comp.getAccessibleContext().setAccessibleName(label);
                }

                speak(announcement.toString());
            }

            @Override
            public void focusLost(FocusEvent e) {}
        };

        for (Component comp : componentLabels.keySet()) {
            comp.addFocusListener(focusListener);

            // For spinners, also add to the editor component
            if (comp instanceof JSpinner) {
                JComponent editor = ((JSpinner) comp).getEditor();
                if (editor != null) {
                    for (Component child : editor.getComponents()) {
                        child.addFocusListener(focusListener);
                    }
                }
            }

            // Set accessible name
            if (comp instanceof JComponent) {
                String label = componentLabels.get(comp);
                if (label != null) {
                    comp.getAccessibleContext().setAccessibleName(label);
                }
            }
        }
    }

    private void addKeyboardShortcuts() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addKeyEventDispatcher(e -> {
                    if (e.getID() != java.awt.event.KeyEvent.KEY_PRESSED) return false;
                    if (!isDialogVisible()) return false;
                    // Don't intercept keys when sideboarding window is open
                    if (SideboardingHandler.isAnyWindowVisible()) return false;

                    // Ctrl+Enter = Create game
                    if (e.isControlDown() && !e.isShiftDown()
                            && e.getKeyCode() == java.awt.event.KeyEvent.VK_ENTER) {
                        if (btnOK != null && btnOK.isEnabled()) {
                            speak("Creating game.");
                            btnOK.doClick();
                        }
                        return true;
                    }

                    // Ctrl+Shift+I = Read settings summary
                    if (e.isControlDown() && e.isShiftDown()
                            && e.getKeyCode() == java.awt.event.KeyEvent.VK_I) {
                        readSettingsSummary();
                        return true;
                    }

                    // Ctrl+D = Open accessible deck picker for your deck
                    if (e.isControlDown() && !e.isShiftDown()
                            && e.getKeyCode() == java.awt.event.KeyEvent.VK_D) {
                        openDeckPicker(player1Panel, "your");
                        return true;
                    }

                    // Ctrl+Shift+D = Open accessible deck picker for opponent deck
                    if (e.isControlDown() && e.isShiftDown()
                            && e.getKeyCode() == java.awt.event.KeyEvent.VK_D) {
                        openOpponentDeckPicker();
                        return true;
                    }

                    return false;
                });
    }

    /**
     * Open the accessible deck picker for a given player panel.
     */
    private void openDeckPicker(Component playerPanel, String playerName) {
        if (playerPanel == null) {
            speak("Player panel not found.");
            return;
        }

        // Find the deck text field in this panel (field name is txtPlayerDeck in NewPlayerPanel)
        JTextField txtDeck = getFieldFromObject(playerPanel, "txtPlayerDeck", JTextField.class);
        if (txtDeck == null) {
            speak("Deck field not found for " + playerName + ".");
            return;
        }

        // Find the sample-decks directory
        File sampleDecks = findSampleDecksDir();
        if (sampleDecks == null) {
            speak("Sample decks folder not found.");
            return;
        }

        AccessibleDeckPicker picker = new AccessibleDeckPicker(txtDeck, sampleDecks);
        picker.show();
    }

    /**
     * Open the accessible deck picker for the first opponent.
     */
    private void openOpponentDeckPicker() {
        if (pnlOtherPlayers == null) {
            speak("No opponent panel found.");
            return;
        }

        // Find the first opponent's NewPlayerPanel
        for (Component child : pnlOtherPlayers.getComponents()) {
            Component newPlayerPanel = getFieldFromObject(child, "newPlayerPanel", Component.class);
            if (newPlayerPanel == null) {
                newPlayerPanel = getFieldFromObject(child, "playerPanel", Component.class);
            }
            if (newPlayerPanel != null) {
                openDeckPicker(newPlayerPanel, "opponent");
                return;
            }
        }
        speak("Opponent panel not found. Make sure you've set the opponent to AI first.");
    }

    /**
     * Find the sample-decks directory relative to the XMage client install.
     */
    private File findSampleDecksDir() {
        // Try common locations
        String userDir = System.getProperty("user.dir");
        File[] candidates = {
                new File(userDir, "sample-decks"),
                new File(userDir, "../sample-decks"),
                new File(userDir, "mage-client/sample-decks"),
        };
        for (File dir : candidates) {
            if (dir.exists() && dir.isDirectory()) {
                return dir;
            }
        }
        return null;
    }

    private boolean isDialogVisible() {
        return dialog != null && dialog.isVisible();
    }

    /**
     * Read out a summary of all current game settings.
     */
    private void readSettingsSummary() {
        StringBuilder sb = new StringBuilder("Game settings. ");

        if (txtName != null) {
            sb.append("Name: ").append(txtName.getText()).append(". ");
        }
        if (cbGameType != null && cbGameType.getSelectedItem() != null) {
            sb.append("Game type: ").append(cbGameType.getSelectedItem()).append(". ");
        }
        if (cbDeckType != null && cbDeckType.getSelectedItem() != null) {
            sb.append("Deck type: ").append(cbDeckType.getSelectedItem()).append(". ");
        }
        if (cbTimeLimit != null && cbTimeLimit.getSelectedItem() != null) {
            sb.append("Time: ").append(cbTimeLimit.getSelectedItem()).append(". ");
        }
        if (spnNumWins != null) {
            sb.append("Wins needed: ").append(spnNumWins.getValue()).append(". ");
        }
        if (chkRollbackTurnsAllowed != null) {
            sb.append("Rollbacks: ").append(chkRollbackTurnsAllowed.isSelected() ? "yes" : "no").append(". ");
        }

        // Read player info
        if (player1Panel != null) {
            String deck = readDeckFromPanel(player1Panel);
            if (deck != null) {
                sb.append("Your deck: ").append(deck).append(". ");
            }
        }

        // Read opponent info
        if (pnlOtherPlayers != null) {
            int playerNum = 2;
            for (Component child : pnlOtherPlayers.getComponents()) {
                String playerInfo = readPlayerInfo(child, playerNum);
                if (playerInfo != null) {
                    sb.append(playerInfo);
                }
                playerNum++;
            }
        }

        sb.append("Ctrl+Enter to create.");
        speak(sb.toString());
    }

    private String readPlayerInfo(Component tablePlayerPanel, int playerNum) {
        try {
            JComboBox<?> cbPlayerType = getFieldFromObject(tablePlayerPanel, "cbPlayerType", JComboBox.class);
            if (cbPlayerType != null && cbPlayerType.getSelectedItem() != null) {
                String type = cbPlayerType.getSelectedItem().toString();
                StringBuilder sb = new StringBuilder();
                sb.append("Player ").append(playerNum).append(": ").append(type).append(". ");

                // Try to read deck
                Component newPlayerPanel = getFieldFromObject(tablePlayerPanel, "newPlayerPanel", Component.class);
                if (newPlayerPanel == null) {
                    newPlayerPanel = getFieldFromObject(tablePlayerPanel, "playerPanel", Component.class);
                }
                if (newPlayerPanel != null) {
                    String deck = readDeckFromPanel(newPlayerPanel);
                    if (deck != null) {
                        sb.append("Deck: ").append(deck).append(". ");
                    }
                }
                return sb.toString();
            }
        } catch (Exception e) {
            // Non-fatal
        }
        return null;
    }

    private String readDeckFromPanel(Component panel) {
        try {
            JTextField txtDeck = getFieldFromObject(panel, "txtPlayerDeck", JTextField.class);
            if (txtDeck != null) {
                String text = txtDeck.getText();
                if (text != null && !text.isEmpty()) {
                    // Extract just the filename
                    int lastSep = Math.max(text.lastIndexOf('/'), text.lastIndexOf('\\'));
                    if (lastSep >= 0) {
                        return text.substring(lastSep + 1);
                    }
                    return text;
                }
            }
        } catch (Exception e) {
            // Non-fatal
        }
        return null;
    }

    // --- Reflection helpers ---

    private void addLabel(Component comp, String label) {
        if (comp != null) {
            componentLabels.put(comp, label);
        }
    }

    private void addFieldLabel(Class<?> clazz, String fieldName, String label) {
        try {
            Component comp = getField(clazz, fieldName, Component.class);
            if (comp != null) {
                componentLabels.put(comp, label);
            }
        } catch (Exception e) {
            // Field doesn't exist, skip
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T getField(Class<?> clazz, String name, Class<T> type) {
        try {
            Field field = clazz.getDeclaredField(name);
            field.setAccessible(true);
            Object val = field.get(dialog);
            if (type.isInstance(val)) {
                return (T) val;
            }
        } catch (Exception e) {
            // Field doesn't exist
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private <T> T getFieldFromObject(Object target, String name, Class<T> type) {
        if (target == null) return null;
        try {
            Class<?> clazz = target.getClass();
            while (clazz != null) {
                try {
                    Field field = clazz.getDeclaredField(name);
                    field.setAccessible(true);
                    Object val = field.get(target);
                    if (type.isInstance(val)) {
                        return (T) val;
                    }
                    return null;
                } catch (NoSuchFieldException e) {
                    clazz = clazz.getSuperclass();
                }
            }
        } catch (Exception e) {
            // Field doesn't exist
        }
        return null;
    }

    private void speak(String text) {
        SpeechOutput speech = AccessibilityManager.getInstance().getSpeech();
        if (speech != null) {
            speech.speak(text);
        }
    }
}
