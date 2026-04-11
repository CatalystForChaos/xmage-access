package xmageaccess.ui;

import xmageaccess.AccessibilityManager;
import xmageaccess.speech.SpeechOutput;

import static xmageaccess.util.ReflectionUtils.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;

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
        tabsPanel = findFieldTyped(dialog,"tabsPanel", JTabbedPane.class);
        saveButton = findFieldTyped(dialog,"saveButton", JButton.class);
        exitButton = findFieldTyped(dialog,"exitButton", JButton.class);
        // Main tab
        cbGameLogAutoSave = findFieldTyped(dialog,"cbGameLogAutoSave", JCheckBox.class);
        cbDraftLogAutoSave = findFieldTyped(dialog,"cbDraftLogAutoSave", JCheckBox.class);
        cbLimitedDeckAutoSave = findFieldTyped(dialog,"cbLimitedDeckAutoSave", JCheckBox.class);
        cbGameJsonLogAutoSave = findFieldTyped(dialog,"cbGameJsonLogAutoSave", JCheckBox.class);
        showCardName = findFieldTyped(dialog,"showCardName", JCheckBox.class);
        nonLandPermanentsInOnePile = findFieldTyped(dialog,"nonLandPermanentsInOnePile", JCheckBox.class);
        showPlayerNamesPermanently = findFieldTyped(dialog,"showPlayerNamesPermanently", JCheckBox.class);
        displayLifeOnAvatar = findFieldTyped(dialog,"displayLifeOnAvatar", JCheckBox.class);
        cbAllowRequestToShowHandCards = findFieldTyped(dialog,"cbAllowRequestToShowHandCards", JCheckBox.class);
        cbConfirmEmptyManaPool = findFieldTyped(dialog,"cbConfirmEmptyManaPool", JCheckBox.class);
        cbAskMoveToGraveOrder = findFieldTyped(dialog,"cbAskMoveToGraveOrder", JCheckBox.class);
        // Phases tab - your turn
        checkBoxUpkeepYou = findFieldTyped(dialog,"checkBoxUpkeepYou", JCheckBox.class);
        checkBoxDrawYou = findFieldTyped(dialog,"checkBoxDrawYou", JCheckBox.class);
        checkBoxMainYou = findFieldTyped(dialog,"checkBoxMainYou", JCheckBox.class);
        checkBoxBeforeCYou = findFieldTyped(dialog,"checkBoxBeforeCYou", JCheckBox.class);
        checkBoxEndOfCYou = findFieldTyped(dialog,"checkBoxEndOfCYou", JCheckBox.class);
        checkBoxMain2You = findFieldTyped(dialog,"checkBoxMain2You", JCheckBox.class);
        checkBoxEndTurnYou = findFieldTyped(dialog,"checkBoxEndTurnYou", JCheckBox.class);
        // Phases tab - opponents
        checkBoxUpkeepOthers = findFieldTyped(dialog,"checkBoxUpkeepOthers", JCheckBox.class);
        checkBoxDrawOthers = findFieldTyped(dialog,"checkBoxDrawOthers", JCheckBox.class);
        checkBoxMainOthers = findFieldTyped(dialog,"checkBoxMainOthers", JCheckBox.class);
        checkBoxBeforeCOthers = findFieldTyped(dialog,"checkBoxBeforeCOthers", JCheckBox.class);
        checkBoxEndOfCOthers = findFieldTyped(dialog,"checkBoxEndOfCOthers", JCheckBox.class);
        checkBoxMain2Others = findFieldTyped(dialog,"checkBoxMain2Others", JCheckBox.class);
        checkBoxEndTurnOthers = findFieldTyped(dialog,"checkBoxEndTurnOthers", JCheckBox.class);
        // Phases tab - skip/priority
        cbStopAttack = findFieldTyped(dialog,"cbStopAttack", JCheckBox.class);
        cbStopBlockWithAny = findFieldTyped(dialog,"cbStopBlockWithAny", JCheckBox.class);
        cbStopBlockWithZero = findFieldTyped(dialog,"cbStopBlockWithZero", JCheckBox.class);
        cbStopOnAllMain = findFieldTyped(dialog,"cbStopOnAllMain", JCheckBox.class);
        cbStopOnAllEnd = findFieldTyped(dialog,"cbStopOnAllEnd", JCheckBox.class);
        cbStopOnNewStackObjects = findFieldTyped(dialog,"cbStopOnNewStackObjects", JCheckBox.class);
        cbPassPriorityCast = findFieldTyped(dialog,"cbPassPriorityCast", JCheckBox.class);
        cbPassPriorityActivation = findFieldTyped(dialog,"cbPassPriorityActivation", JCheckBox.class);
        cbAutoOrderTrigger = findFieldTyped(dialog,"cbAutoOrderTrigger", JCheckBox.class);
        // Sounds tab
        cbEnableGameSounds = findFieldTyped(dialog,"cbEnableGameSounds", JCheckBox.class);
        cbEnableDraftSounds = findFieldTyped(dialog,"cbEnableDraftSounds", JCheckBox.class);
        cbEnableSkipButtonsSounds = findFieldTyped(dialog,"cbEnableSkipButtonsSounds", JCheckBox.class);
        cbEnableOtherSounds = findFieldTyped(dialog,"cbEnableOtherSounds", JCheckBox.class);
        cbEnableBattlefieldBGM = findFieldTyped(dialog,"cbEnableBattlefieldBGM", JCheckBox.class);
        // Network tab
        txtProxyServer = findFieldTyped(dialog,"txtProxyServer", JTextField.class);
        txtProxyPort = findFieldTyped(dialog,"txtProxyPort", JTextField.class);
        rememberPswd = findFieldTyped(dialog,"rememberPswd", JCheckBox.class);
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

    private void speak(String text) {
        SpeechOutput speech = AccessibilityManager.getInstance().getSpeech();
        if (speech != null) speech.speak(text);
    }
}
