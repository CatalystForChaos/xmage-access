package xmageaccess.ui;

import xmageaccess.AccessibilityManager;
import xmageaccess.speech.SpeechOutput;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;

/**
 * Accessibility handler for the XMage PreferencesDialog.
 *
 * Keyboard shortcuts:
 *   Ctrl+Tab         - Next tab (with announcement)
 *   Ctrl+Shift+Tab   - Previous tab (with announcement)
 *   Ctrl+R           - Read all settings on current tab
 *   Ctrl+S           - Save and close
 *   Escape           - Close without saving (standard dialog behavior)
 */
public class PreferencesDialogHandler {

    private final Component dialog;

    // Tab pane and action buttons
    private JTabbedPane tabsPanel;
    private JButton saveButton;
    private JButton exitButton;

    // Main tab checkboxes
    private JCheckBox cbGameLogAutoSave;
    private JCheckBox cbDraftLogAutoSave;
    private JCheckBox cbLimitedDeckAutoSave;
    private JCheckBox cbGameJsonLogAutoSave;
    private JCheckBox showCardName;
    private JCheckBox nonLandPermanentsInOnePile;
    private JCheckBox showPlayerNamesPermanently;
    private JCheckBox displayLifeOnAvatar;
    private JCheckBox cbAllowRequestToShowHandCards;
    private JCheckBox cbConfirmEmptyManaPool;
    private JCheckBox cbAskMoveToGraveOrder;

    // Phases tab - your turn
    private JCheckBox checkBoxUpkeepYou;
    private JCheckBox checkBoxDrawYou;
    private JCheckBox checkBoxMainYou;
    private JCheckBox checkBoxBeforeCYou;
    private JCheckBox checkBoxEndOfCYou;
    private JCheckBox checkBoxMain2You;
    private JCheckBox checkBoxEndTurnYou;
    // Phases tab - opponents' turn
    private JCheckBox checkBoxUpkeepOthers;
    private JCheckBox checkBoxDrawOthers;
    private JCheckBox checkBoxMainOthers;
    private JCheckBox checkBoxBeforeCOthers;
    private JCheckBox checkBoxEndOfCOthers;
    private JCheckBox checkBoxMain2Others;
    private JCheckBox checkBoxEndTurnOthers;
    // Phases tab - skip/priority settings
    private JCheckBox cbStopAttack;
    private JCheckBox cbStopBlockWithAny;
    private JCheckBox cbStopBlockWithZero;
    private JCheckBox cbStopOnAllMain;
    private JCheckBox cbStopOnAllEnd;
    private JCheckBox cbStopOnNewStackObjects;
    private JCheckBox cbPassPriorityCast;
    private JCheckBox cbPassPriorityActivation;
    private JCheckBox cbAutoOrderTrigger;

    // Sounds tab
    private JCheckBox cbEnableGameSounds;
    private JCheckBox cbEnableDraftSounds;
    private JCheckBox cbEnableSkipButtonsSounds;
    private JCheckBox cbEnableOtherSounds;
    private JCheckBox cbEnableBattlefieldBGM;

    // Network tab
    private JTextField txtProxyServer;
    private JTextField txtProxyPort;
    private JCheckBox rememberPswd;
    private KeyEventDispatcher keyDispatcher;

    public PreferencesDialogHandler(Component dialog) {
        this.dialog = dialog;
    }

    public void attach() {
        try {
            discoverComponents();
            addKeyboardShortcuts();
            announceDialog();
        } catch (Exception e) {
            System.err.println("[XMage Access] Error attaching to PreferencesDialog: " + e.getMessage());
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
        tabsPanel = getField(dialog, "tabsPanel", JTabbedPane.class);
        saveButton = getField(dialog, "saveButton", JButton.class);
        exitButton = getField(dialog, "exitButton", JButton.class);
        // Main tab
        cbGameLogAutoSave = getField(dialog, "cbGameLogAutoSave", JCheckBox.class);
        cbDraftLogAutoSave = getField(dialog, "cbDraftLogAutoSave", JCheckBox.class);
        cbLimitedDeckAutoSave = getField(dialog, "cbLimitedDeckAutoSave", JCheckBox.class);
        cbGameJsonLogAutoSave = getField(dialog, "cbGameJsonLogAutoSave", JCheckBox.class);
        showCardName = getField(dialog, "showCardName", JCheckBox.class);
        nonLandPermanentsInOnePile = getField(dialog, "nonLandPermanentsInOnePile", JCheckBox.class);
        showPlayerNamesPermanently = getField(dialog, "showPlayerNamesPermanently", JCheckBox.class);
        displayLifeOnAvatar = getField(dialog, "displayLifeOnAvatar", JCheckBox.class);
        cbAllowRequestToShowHandCards = getField(dialog, "cbAllowRequestToShowHandCards", JCheckBox.class);
        cbConfirmEmptyManaPool = getField(dialog, "cbConfirmEmptyManaPool", JCheckBox.class);
        cbAskMoveToGraveOrder = getField(dialog, "cbAskMoveToGraveOrder", JCheckBox.class);
        // Phases tab - your turn
        checkBoxUpkeepYou = getField(dialog, "checkBoxUpkeepYou", JCheckBox.class);
        checkBoxDrawYou = getField(dialog, "checkBoxDrawYou", JCheckBox.class);
        checkBoxMainYou = getField(dialog, "checkBoxMainYou", JCheckBox.class);
        checkBoxBeforeCYou = getField(dialog, "checkBoxBeforeCYou", JCheckBox.class);
        checkBoxEndOfCYou = getField(dialog, "checkBoxEndOfCYou", JCheckBox.class);
        checkBoxMain2You = getField(dialog, "checkBoxMain2You", JCheckBox.class);
        checkBoxEndTurnYou = getField(dialog, "checkBoxEndTurnYou", JCheckBox.class);
        // Phases tab - opponents
        checkBoxUpkeepOthers = getField(dialog, "checkBoxUpkeepOthers", JCheckBox.class);
        checkBoxDrawOthers = getField(dialog, "checkBoxDrawOthers", JCheckBox.class);
        checkBoxMainOthers = getField(dialog, "checkBoxMainOthers", JCheckBox.class);
        checkBoxBeforeCOthers = getField(dialog, "checkBoxBeforeCOthers", JCheckBox.class);
        checkBoxEndOfCOthers = getField(dialog, "checkBoxEndOfCOthers", JCheckBox.class);
        checkBoxMain2Others = getField(dialog, "checkBoxMain2Others", JCheckBox.class);
        checkBoxEndTurnOthers = getField(dialog, "checkBoxEndTurnOthers", JCheckBox.class);
        // Phases tab - skip/priority
        cbStopAttack = getField(dialog, "cbStopAttack", JCheckBox.class);
        cbStopBlockWithAny = getField(dialog, "cbStopBlockWithAny", JCheckBox.class);
        cbStopBlockWithZero = getField(dialog, "cbStopBlockWithZero", JCheckBox.class);
        cbStopOnAllMain = getField(dialog, "cbStopOnAllMain", JCheckBox.class);
        cbStopOnAllEnd = getField(dialog, "cbStopOnAllEnd", JCheckBox.class);
        cbStopOnNewStackObjects = getField(dialog, "cbStopOnNewStackObjects", JCheckBox.class);
        cbPassPriorityCast = getField(dialog, "cbPassPriorityCast", JCheckBox.class);
        cbPassPriorityActivation = getField(dialog, "cbPassPriorityActivation", JCheckBox.class);
        cbAutoOrderTrigger = getField(dialog, "cbAutoOrderTrigger", JCheckBox.class);
        // Sounds tab
        cbEnableGameSounds = getField(dialog, "cbEnableGameSounds", JCheckBox.class);
        cbEnableDraftSounds = getField(dialog, "cbEnableDraftSounds", JCheckBox.class);
        cbEnableSkipButtonsSounds = getField(dialog, "cbEnableSkipButtonsSounds", JCheckBox.class);
        cbEnableOtherSounds = getField(dialog, "cbEnableOtherSounds", JCheckBox.class);
        cbEnableBattlefieldBGM = getField(dialog, "cbEnableBattlefieldBGM", JCheckBox.class);
        // Network tab
        txtProxyServer = getField(dialog, "txtProxyServer", JTextField.class);
        txtProxyPort = getField(dialog, "txtProxyPort", JTextField.class);
        rememberPswd = getField(dialog, "rememberPswd", JCheckBox.class);
    }

    private void announceDialog() {
        String tabName = getCurrentTabName();
        speak("Preferences. " + tabName + " tab. "
                + "Ctrl+Tab next tab, Ctrl+Shift+Tab previous tab. "
                + "Ctrl+R read settings. Ctrl+S save. Escape close.");
    }

    private void addKeyboardShortcuts() {
        keyDispatcher = e -> {
                    if (e.getID() != KeyEvent.KEY_PRESSED) return false;
                    if (!isDialogActive()) return false;

                    if (e.isControlDown() && !e.isAltDown()) {
                        if (!e.isShiftDown()) {
                            switch (e.getKeyCode()) {
                                case KeyEvent.VK_TAB:
                                    nextTab();
                                    return true;
                                case KeyEvent.VK_R:
                                    readCurrentTab();
                                    return true;
                                case KeyEvent.VK_S:
                                    savePreferences();
                                    return true;
                            }
                        } else {
                            if (e.getKeyCode() == KeyEvent.VK_TAB) {
                                prevTab();
                                return true;
                            }
                        }
                    }
                    return false;
                };
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addKeyEventDispatcher(keyDispatcher);
    }

    private void nextTab() {
        if (tabsPanel == null) return;
        int count = tabsPanel.getTabCount();
        int next = (tabsPanel.getSelectedIndex() + 1) % count;
        tabsPanel.setSelectedIndex(next);
        speak(tabsPanel.getTitleAt(next) + " tab.");
    }

    private void prevTab() {
        if (tabsPanel == null) return;
        int count = tabsPanel.getTabCount();
        int prev = (tabsPanel.getSelectedIndex() - 1 + count) % count;
        tabsPanel.setSelectedIndex(prev);
        speak(tabsPanel.getTitleAt(prev) + " tab.");
    }

    private void savePreferences() {
        if (saveButton != null) {
            speak("Saving preferences.");
            saveButton.doClick();
        }
    }

    private String getCurrentTabName() {
        if (tabsPanel == null) return "Settings";
        int idx = tabsPanel.getSelectedIndex();
        if (idx < 0) return "Settings";
        return tabsPanel.getTitleAt(idx);
    }

    private void readCurrentTab() {
        if (tabsPanel == null) {
            speak("Preferences not available.");
            return;
        }
        int idx = tabsPanel.getSelectedIndex();
        String name = tabsPanel.getTitleAt(idx);
        speak(name + " tab. " + getTabSettings(idx));
    }

    private String getTabSettings(int idx) {
        switch (idx) {
            case 0: return readMainTab();
            case 5: return readPhasesTab();
            case 7: return readSoundsTab();
            case 8: return readNetworkTab();
            default: return "Use Tab and Space to navigate these settings.";
        }
    }

    private String readMainTab() {
        StringBuilder sb = new StringBuilder();
        readCb(sb, cbGameLogAutoSave, "Save game logs");
        readCb(sb, cbDraftLogAutoSave, "Save draft logs");
        readCb(sb, cbLimitedDeckAutoSave, "Save limited decks");
        readCb(sb, cbGameJsonLogAutoSave, "Save JSON logs");
        readCb(sb, showCardName, "Show card name");
        readCb(sb, nonLandPermanentsInOnePile, "Non-lands in one pile");
        readCb(sb, showPlayerNamesPermanently, "Show player names permanently");
        readCb(sb, displayLifeOnAvatar, "Life on avatar");
        readCb(sb, cbAllowRequestToShowHandCards, "Allow hand card requests");
        readCb(sb, cbConfirmEmptyManaPool, "Confirm empty mana pool");
        readCb(sb, cbAskMoveToGraveOrder, "Ask graveyard order");
        return sb.toString();
    }

    private String readPhasesTab() {
        StringBuilder sb = new StringBuilder();
        sb.append("Your turn stops: ");
        sb.append("Upkeep ").append(onOff(checkBoxUpkeepYou)).append(", ");
        sb.append("Draw ").append(onOff(checkBoxDrawYou)).append(", ");
        sb.append("Main 1 ").append(onOff(checkBoxMainYou)).append(", ");
        sb.append("Before Combat ").append(onOff(checkBoxBeforeCYou)).append(", ");
        sb.append("End of Combat ").append(onOff(checkBoxEndOfCYou)).append(", ");
        sb.append("Main 2 ").append(onOff(checkBoxMain2You)).append(", ");
        sb.append("End of Turn ").append(onOff(checkBoxEndTurnYou)).append(". ");
        sb.append("Opponent stops: ");
        sb.append("Upkeep ").append(onOff(checkBoxUpkeepOthers)).append(", ");
        sb.append("Draw ").append(onOff(checkBoxDrawOthers)).append(", ");
        sb.append("Main 1 ").append(onOff(checkBoxMainOthers)).append(", ");
        sb.append("Before Combat ").append(onOff(checkBoxBeforeCOthers)).append(", ");
        sb.append("End of Combat ").append(onOff(checkBoxEndOfCOthers)).append(", ");
        sb.append("Main 2 ").append(onOff(checkBoxMain2Others)).append(", ");
        sb.append("End of Turn ").append(onOff(checkBoxEndTurnOthers)).append(". ");
        readCb(sb, cbStopAttack, "Stop on attackers");
        readCb(sb, cbStopBlockWithAny, "Stop on blockers if any");
        readCb(sb, cbStopBlockWithZero, "Stop on zero blockers");
        readCb(sb, cbStopOnAllMain, "Stop all main steps");
        readCb(sb, cbStopOnAllEnd, "Stop all end steps");
        readCb(sb, cbStopOnNewStackObjects, "Stop on new stack objects");
        readCb(sb, cbPassPriorityCast, "Pass priority after cast");
        readCb(sb, cbPassPriorityActivation, "Pass priority after activation");
        readCb(sb, cbAutoOrderTrigger, "Auto-order same triggers");
        return sb.toString();
    }

    private String readSoundsTab() {
        StringBuilder sb = new StringBuilder();
        readCb(sb, cbEnableGameSounds, "Game sounds");
        readCb(sb, cbEnableDraftSounds, "Draft sounds");
        readCb(sb, cbEnableSkipButtonsSounds, "Skip button sounds");
        readCb(sb, cbEnableOtherSounds, "Other sounds");
        readCb(sb, cbEnableBattlefieldBGM, "Background music");
        return sb.toString();
    }

    private String readNetworkTab() {
        StringBuilder sb = new StringBuilder();
        String server = txtProxyServer != null ? txtProxyServer.getText().trim() : "";
        if (!server.isEmpty()) {
            sb.append("Proxy server: ").append(server).append(". ");
            String port = txtProxyPort != null ? txtProxyPort.getText().trim() : "";
            if (!port.isEmpty()) sb.append("Port: ").append(port).append(". ");
        } else {
            sb.append("No proxy configured. ");
        }
        readCb(sb, rememberPswd, "Remember password");
        return sb.toString();
    }

    private void readCb(StringBuilder sb, JCheckBox cb, String label) {
        if (cb == null) return;
        sb.append(label).append(": ").append(onOff(cb)).append(". ");
    }

    private String onOff(JCheckBox cb) {
        if (cb == null) return "unknown";
        return cb.isSelected() ? "on" : "off";
    }

    private boolean isDialogActive() {
        if (!dialog.isVisible()) return false;
        Window focused = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
        if (focused == null) return false;
        if (dialog instanceof Window) {
            return dialog == focused;
        }
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
