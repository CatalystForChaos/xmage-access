package xmageaccess.ui;

import xmageaccess.AccessibilityManager;
import xmageaccess.speech.SpeechOutput;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Accessible game window with JList-based zone navigation.
 * Tab between zones, arrow keys within zones, Enter to act, D for detail.
 * Works alongside GamePanelHandler (keyboard shortcuts remain available).
 */
public class AccessibleGameWindow extends JFrame {

    private final Component gamePanel;

    // Zone panels in Tab order
    private final ZoneListPanel actionsZone;
    private final ZoneListPanel handZone;
    private final ZoneListPanel yourBattlefieldZone;
    private final ZoneListPanel opponentBattlefieldZone;
    private final ZoneListPanel stackZone;
    private final ZoneListPanel graveyardsZone;
    private final ZoneListPanel exileZone;
    private final ZoneListPanel commandZone;
    private final ZoneListPanel revealedZone;
    private final ZoneListPanel manaPoolZone;
    private final ZoneListPanel gameLogZone;

    private final List<ZoneListPanel> allZones = new ArrayList<>();

    // Cached reflection references (re-read each poll)
    private Object helperPanel;
    private Object lastGameData;
    private Object gameChatPanel;
    private List<?> pickTargetDialogs;
    private Object abilityPicker;

    private Timer pollTimer;

    public AccessibleGameWindow(Component gamePanel) {
        super("XMage Accessible Game");
        this.gamePanel = gamePanel;

        actionsZone = new ZoneListPanel("Actions");
        handZone = new ZoneListPanel("Hand");
        yourBattlefieldZone = new ZoneListPanel("Your Battlefield");
        opponentBattlefieldZone = new ZoneListPanel("Opponent Battlefield");
        stackZone = new ZoneListPanel("Stack");
        graveyardsZone = new ZoneListPanel("Graveyards");
        exileZone = new ZoneListPanel("Exile");
        commandZone = new ZoneListPanel("Command Zone");
        revealedZone = new ZoneListPanel("Revealed Cards");
        manaPoolZone = new ZoneListPanel("Mana Pool");
        gameLogZone = new ZoneListPanel("Game Log");

        allZones.add(actionsZone);
        allZones.add(handZone);
        allZones.add(yourBattlefieldZone);
        allZones.add(opponentBattlefieldZone);
        allZones.add(stackZone);
        allZones.add(graveyardsZone);
        allZones.add(exileZone);
        allZones.add(commandZone);
        allZones.add(revealedZone);
        allZones.add(manaPoolZone);
        allZones.add(gameLogZone);

        buildUI();
        discoverComponents();
        bindKeys();
        startPolling();
    }

    private void buildUI() {
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        setSize(600, 800);
        setLocationRelativeTo(null);

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));

        for (ZoneListPanel zone : allZones) {
            zone.setPreferredSize(new Dimension(580, 100));
            zone.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));
            mainPanel.add(zone);
        }

        JScrollPane scrollPane = new JScrollPane(mainPanel);
        add(scrollPane, BorderLayout.CENTER);
    }

    private void discoverComponents() {
        helperPanel = findFieldDeep(gamePanel, "helper");
        lastGameData = findFieldDeep(gamePanel, "lastGameData");
        gameChatPanel = findFieldDeep(gamePanel, "gameChatPanel");
        pickTargetDialogs = findFieldTyped(gamePanel, "pickTarget", List.class);
        abilityPicker = findFieldDeep(gamePanel, "abilityPicker");

        System.out.println("[XMage Access] Accessible window - helper: " + (helperPanel != null)
                + ", gameData: " + (lastGameData != null)
                + ", gameLog: " + (gameChatPanel != null));
    }

    private void bindKeys() {
        // Enter and D on each zone's JList (WHEN_FOCUSED priority beats type-ahead)
        for (ZoneListPanel zone : allZones) {
            JList<ZoneItem> list = zone.getList();

            list.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "activateItem");
            list.getActionMap().put("activateItem", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    activateSelectedItem();
                }
            });

            list.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_D, 0), "readDetail");
            list.getActionMap().put("readDetail", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    readSelectedDetail();
                }
            });
        }

        // Escape on root pane (works from anywhere in the window)
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "returnFocus");
        getRootPane().getActionMap().put("returnFocus", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                returnFocusToXMage();
            }
        });
    }

    private void startPolling() {
        pollTimer = new Timer(5000, e -> {
            try {
                if (!gamePanel.isVisible()) {
                    stopPolling();
                    dispose();
                    speak("Game ended.");
                    return;
                }
                refreshReferences();
                refreshAllZones();
            } catch (Exception ex) {
                // Don't crash the timer
            }
        });
        pollTimer.setRepeats(true);
        pollTimer.start();
    }

    public void stopPolling() {
        if (pollTimer != null) {
            pollTimer.stop();
        }
    }

    @Override
    public void dispose() {
        stopPolling();
        super.dispose();
    }

    /**
     * Called by GamePanelHandler when a game state change is detected
     * (phase change, card played, feedback change, etc.) to immediately
     * refresh all zones without waiting for the next poll cycle.
     */
    public void refreshNow() {
        SwingUtilities.invokeLater(() -> {
            try {
                refreshReferences();
                refreshAllZones();
            } catch (Exception e) {
                // Don't crash
            }
        });
    }

    // ========== REFRESH REFERENCES ==========

    private void refreshReferences() {
        lastGameData = findFieldDeep(gamePanel, "lastGameData");
        helperPanel = findFieldDeep(gamePanel, "helper");
        gameChatPanel = findFieldDeep(gamePanel, "gameChatPanel");
        pickTargetDialogs = findFieldTyped(gamePanel, "pickTarget", List.class);
        abilityPicker = findFieldDeep(gamePanel, "abilityPicker");
    }

    // ========== REFRESH ZONES ==========

    private void refreshAllZones() {
        refreshActionsZone();
        refreshHandZone();
        refreshBattlefieldZones();
        refreshStackZone();
        refreshGraveyardsZone();
        refreshExileZone();
        refreshCommandZone();
        refreshRevealedZone();
        refreshManaPoolZone();
        refreshGameLogZone();
    }

    private void refreshActionsZone() {
        List<ZoneItem> items = new ArrayList<>();

        // Current prompt
        String promptText = getFeedbackText();
        if (promptText != null && !promptText.isEmpty() && !promptText.equals("<Empty>")) {
            items.add(new ZoneItem("Prompt: " + cleanHtml(promptText),
                    cleanHtml(promptText), null, ZoneItem.ActionType.NONE));
        }

        // Visible buttons
        addButtonItem(items, "btnLeft", "linkLeft", "OK");
        addButtonItem(items, "btnRight", "linkRight", "Cancel");
        addButtonItem(items, "btnSpecial", "linkSpecial", "Special");
        addButtonItem(items, "btnUndo", "linkUndo", "Undo");

        // Ability choices (when ability picker is visible)
        List<Object[]> abilities = getAbilityChoices();
        for (Object[] ability : abilities) {
            String text = (String) ability[1];
            items.add(new ZoneItem("Ability: " + text, text,
                    ability[0], ZoneItem.ActionType.SEND_ABILITY));
        }

        // Card targets (from ShowCardsDialog)
        List<Object> cardTargets = getVisibleTargets();
        for (Object target : cardTargets) {
            String name = callString(target, "getName");
            if (name != null) {
                items.add(new ZoneItem("Target: " + name,
                        formatCardDetailed(target), target, ZoneItem.ActionType.SEND_CARD_UUID));
            }
        }

        // Player targets (only when game needs feedback and no other targets)
        boolean needFeedback = false;
        if (helperPanel != null) {
            Boolean nf = findFieldTyped(helperPanel, "gameNeedFeedback", Boolean.class);
            needFeedback = nf != null && nf;
        }
        if (needFeedback && cardTargets.isEmpty() && abilities.isEmpty()) {
            List<Object[]> players = getSelectablePlayers();
            for (Object[] player : players) {
                items.add(new ZoneItem("Target player: " + player[0],
                        "Player: " + player[0], player[1], ZoneItem.ActionType.SEND_UUID_DIRECT));
            }
        }

        actionsZone.updateItems(items);
    }

    private void addButtonItem(List<ZoneItem> items, String btnField, String linkField, String fallbackLabel) {
        if (helperPanel == null) return;
        try {
            JButton btn = getButtonField(btnField);
            if (btn != null && btn.isVisible()) {
                String label = btn.getText();
                if (label == null || label.isEmpty()) label = fallbackLabel;
                items.add(new ZoneItem(label, "Button: " + label,
                        new Object[]{btn, linkField}, ZoneItem.ActionType.CLICK_BUTTON));
            }
        } catch (Exception e) {
            // Ignore
        }
    }

    private void refreshHandZone() {
        List<ZoneItem> items = new ArrayList<>();
        Object gameView = getGameView();
        if (gameView != null) {
            Object hand = callMethod(gameView, "getMyHand");
            if (hand instanceof Map) {
                int i = 1;
                for (Object cardView : ((Map<?, ?>) hand).values()) {
                    String name = callString(cardView, "getName");
                    String manaCost = callString(cardView, "getManaCostStr");
                    String display = (i++) + ": " + (name != null ? name : "Unknown");
                    if (manaCost != null && !manaCost.isEmpty()) {
                        display += ", " + formatManaCost(manaCost);
                    }
                    items.add(new ZoneItem(display, formatCardDetailed(cardView),
                            cardView, ZoneItem.ActionType.SEND_CARD_UUID));
                }
            }
        }
        handZone.updateItems(items);
    }

    private void refreshBattlefieldZones() {
        Object gameView = getGameView();
        if (gameView == null) {
            yourBattlefieldZone.updateItems(new ArrayList<ZoneItem>());
            opponentBattlefieldZone.updateItems(new ArrayList<ZoneItem>());
            return;
        }

        Object playersList = callMethod(gameView, "getPlayers");
        if (!(playersList instanceof List)) return;

        List<?> players = (List<?>) playersList;

        // Identify my player by ID
        Object myPlayer = getMyPlayer(gameView);
        Object myPlayerId = myPlayer != null ? callMethod(myPlayer, "getPlayerId") : null;

        List<ZoneItem> myItems = new ArrayList<>();
        List<ZoneItem> oppItems = new ArrayList<>();

        for (Object player : players) {
            Object playerId = callMethod(player, "getPlayerId");
            boolean isMe = (myPlayerId != null && myPlayerId.equals(playerId));
            List<ZoneItem> targetList = isMe ? myItems : oppItems;

            String playerName = callString(player, "getName");
            Object battlefield = callMethod(player, "getBattlefield");
            if (battlefield instanceof Map) {
                // Sort: creatures first, then non-land non-creatures, then lands
                List<Object> creatures = new ArrayList<>();
                List<Object> others = new ArrayList<>();
                List<Object> lands = new ArrayList<>();

                for (Object perm : ((Map<?, ?>) battlefield).values()) {
                    if (callBool(perm, "isCreature")) creatures.add(perm);
                    else if (callBool(perm, "isLand")) lands.add(perm);
                    else others.add(perm);
                }

                for (Object perm : creatures) addPermanentItem(targetList, perm, isMe ? null : playerName);
                for (Object perm : others) addPermanentItem(targetList, perm, isMe ? null : playerName);
                for (Object perm : lands) addPermanentItem(targetList, perm, isMe ? null : playerName);
            }
        }

        yourBattlefieldZone.updateItems(myItems);
        opponentBattlefieldZone.updateItems(oppItems);
    }

    private void addPermanentItem(List<ZoneItem> items, Object perm, String playerName) {
        String name = callString(perm, "getName");
        if (name == null) return;

        StringBuilder display = new StringBuilder();
        if (playerName != null) {
            display.append(playerName).append(": ");
        }
        display.append(name);

        if (callBool(perm, "isCreature")) {
            String power = callString(perm, "getPower");
            String toughness = callString(perm, "getToughness");
            if (power != null && toughness != null) {
                display.append(" ").append(power).append("/").append(toughness);
            }
        }
        if (callBool(perm, "isTapped")) display.append(", tapped");

        items.add(new ZoneItem(display.toString(), formatPermanentDetailed(perm),
                perm, ZoneItem.ActionType.SEND_CARD_UUID));
    }

    private void refreshStackZone() {
        List<ZoneItem> items = new ArrayList<>();
        Object gameView = getGameView();
        if (gameView != null) {
            Object stack = callMethod(gameView, "getStack");
            if (stack instanceof Map) {
                for (Object cardView : ((Map<?, ?>) stack).values()) {
                    String name = callString(cardView, "getName");
                    if (name != null) {
                        items.add(new ZoneItem(name, formatCardDetailed(cardView),
                                cardView, ZoneItem.ActionType.SEND_CARD_UUID));
                    }
                }
            }
        }
        stackZone.updateItems(items);
    }

    private void refreshGraveyardsZone() {
        List<ZoneItem> items = new ArrayList<>();
        Object gameView = getGameView();
        if (gameView != null) {
            Object playersList = callMethod(gameView, "getPlayers");
            if (playersList instanceof List) {
                for (Object player : (List<?>) playersList) {
                    String playerName = callString(player, "getName");
                    Object graveyard = callMethod(player, "getGraveyard");
                    if (graveyard instanceof Map) {
                        for (Object card : ((Map<?, ?>) graveyard).values()) {
                            String cardName = callString(card, "getName");
                            if (cardName != null) {
                                String display = (playerName != null ? playerName + ": " : "") + cardName;
                                items.add(new ZoneItem(display, formatCardDetailed(card),
                                        card, ZoneItem.ActionType.SEND_CARD_UUID));
                            }
                        }
                    }
                }
            }
        }
        graveyardsZone.updateItems(items);
    }

    private void refreshExileZone() {
        List<ZoneItem> items = new ArrayList<>();
        Object gameView = getGameView();
        if (gameView != null) {
            Object exileList = callMethod(gameView, "getExile");
            if (exileList instanceof List) {
                for (Object exile : (List<?>) exileList) {
                    String zoneName = callString(exile, "getName");
                    if (exile instanceof Map) {
                        for (Object card : ((Map<?, ?>) exile).values()) {
                            String cardName = callString(card, "getName");
                            if (cardName != null) {
                                String prefix = (zoneName != null && !zoneName.isEmpty()) ? zoneName + ": " : "";
                                items.add(new ZoneItem(prefix + cardName, formatCardDetailed(card),
                                        card, ZoneItem.ActionType.SEND_CARD_UUID));
                            }
                        }
                    }
                }
            }
        }
        exileZone.updateItems(items);
    }

    private void refreshCommandZone() {
        List<ZoneItem> items = new ArrayList<>();
        Object gameView = getGameView();
        if (gameView != null) {
            Object playersList = callMethod(gameView, "getPlayers");
            if (playersList instanceof List) {
                for (Object player : (List<?>) playersList) {
                    String playerName = callString(player, "getName");
                    Object commandList = callMethod(player, "getCommandObjectList");
                    if (commandList instanceof List) {
                        for (Object cmdObj : (List<?>) commandList) {
                            String name = callString(cmdObj, "getName");
                            if (name == null) continue;

                            // Build detail from rules
                            Object rules = callMethod(cmdObj, "getRules");
                            StringBuilder detail = new StringBuilder(name).append(". ");
                            if (rules instanceof List) {
                                for (Object rule : (List<?>) rules) {
                                    detail.append(cleanHtml(rule.toString())).append(". ");
                                }
                            }

                            // CommanderView extends CardView - can be cast/activated
                            // EmblemView, DungeonView, PlaneView are info-only
                            boolean isCommander = false;
                            try {
                                Class<?> cardViewClass = Class.forName("mage.view.CardView");
                                isCommander = cardViewClass.isInstance(cmdObj);
                            } catch (Exception e) {
                                // Ignore
                            }

                            String display = (playerName != null ? playerName + ": " : "") + name;
                            ZoneItem.ActionType action = isCommander
                                    ? ZoneItem.ActionType.SEND_CARD_UUID
                                    : ZoneItem.ActionType.NONE;
                            items.add(new ZoneItem(display, detail.toString(), cmdObj, action));
                        }
                    }
                }
            }
        }
        commandZone.updateItems(items);
    }

    private void refreshRevealedZone() {
        List<ZoneItem> items = new ArrayList<>();
        try {
            addRevealedCards(items, "revealed");
            addRevealedCards(items, "lookedAt");
        } catch (Exception e) {
            // Ignore
        }
        revealedZone.updateItems(items);
    }

    private void addRevealedCards(List<ZoneItem> items, String fieldName) {
        Map<?, ?> dialogMap = findFieldTyped(gamePanel, fieldName, Map.class);
        if (dialogMap == null) return;
        for (Map.Entry<?, ?> entry : dialogMap.entrySet()) {
            String contextName = entry.getKey() != null ? entry.getKey().toString() : "";
            Object dialog = entry.getValue();
            if (!(dialog instanceof Component) || !((Component) dialog).isVisible()) continue;
            try {
                // CardInfoWindowDialog has getMageCardsForUpdate() returning Map<UUID, MageCard>
                Method getCards = dialog.getClass().getMethod("getMageCardsForUpdate");
                Object cardsMap = getCards.invoke(dialog);
                if (cardsMap instanceof Map) {
                    for (Object mageCard : ((Map<?, ?>) cardsMap).values()) {
                        Object cardView = callMethod(mageCard, "getOriginal");
                        if (cardView == null) continue;
                        String cardName = callString(cardView, "getName");
                        if (cardName == null) continue;
                        String display = contextName.isEmpty() ? cardName : contextName + ": " + cardName;
                        items.add(new ZoneItem(display, formatCardDetailed(cardView),
                                cardView, ZoneItem.ActionType.SEND_CARD_UUID));
                    }
                }
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    private void refreshManaPoolZone() {
        List<ZoneItem> items = new ArrayList<>();
        Object gameView = getGameView();
        if (gameView != null) {
            Object myPlayer = getMyPlayer(gameView);
            if (myPlayer != null) {
                Object pool = callMethod(myPlayer, "getManaPool");
                if (pool != null) {
                    String[][] colors = {
                            {"WHITE", "white"}, {"BLUE", "blue"}, {"BLACK", "black"},
                            {"RED", "red"}, {"GREEN", "green"}, {"COLORLESS", "colorless"}
                    };
                    String[] getters = {"getWhite", "getBlue", "getBlack", "getRed", "getGreen", "getColorless"};
                    for (int i = 0; i < colors.length; i++) {
                        int amount = callInt(pool, getters[i]);
                        if (amount > 0) {
                            String display = amount + " " + colors[i][1] + " mana";
                            items.add(new ZoneItem(display, display,
                                    colors[i][0], ZoneItem.ActionType.SEND_MANA));
                        }
                    }
                }
            }
        }
        manaPoolZone.updateItems(items);
    }

    private void refreshGameLogZone() {
        if (gameChatPanel == null) return;
        try {
            Object textPane = findFieldDeep(gameChatPanel, "txtConversation");
            if (textPane == null) return;
            Object doc = callMethod(textPane, "getDocument");
            if (doc == null) return;
            int length = callInt(doc, "getLength");
            if (length == 0) return;

            Method getTextMethod = doc.getClass().getMethod("getText", int.class, int.class);
            String text = (String) getTextMethod.invoke(doc, 0, length);
            if (text == null || text.trim().isEmpty()) return;

            String[] allLines = text.split("\n");
            List<ZoneItem> items = new ArrayList<>();
            int start = Math.max(0, allLines.length - 20);
            for (int i = start; i < allLines.length; i++) {
                String trimmed = allLines[i].trim();
                if (!trimmed.isEmpty()) {
                    items.add(new ZoneItem(trimmed, trimmed, null, ZoneItem.ActionType.NONE));
                }
            }
            gameLogZone.updateItems(items);
        } catch (Exception e) {
            // Ignore
        }
    }

    // ========== ITEM ACTIVATION ==========

    private void activateSelectedItem() {
        ZoneListPanel focusedZone = getFocusedZone();
        if (focusedZone == null) return;

        ZoneItem item = focusedZone.getSelectedItem();
        if (item == null) {
            speak("Nothing selected.");
            return;
        }

        switch (item.getActionType()) {
            case CLICK_BUTTON:
                clickButtonItem(item);
                break;
            case SEND_CARD_UUID:
                String cardName = callString(item.getSourceObject(), "getName");
                speak("Selecting " + (cardName != null ? cardName : "card") + ".");
                sendUUID(item.getSourceObject());
                break;
            case SEND_UUID_DIRECT:
                speak("Selecting " + item.getDisplayName() + ".");
                sendUUIDDirect(item.getSourceObject());
                break;
            case SEND_ABILITY:
                speak("Selecting " + item.getDisplayName() + ".");
                Object abilityId = item.getSourceObject();
                if (abilityId != null) {
                    sendUUIDDirect(abilityId);
                } else {
                    // Cancel option (null id)
                    sendBooleanFalse();
                }
                // Hide the ability picker
                if (abilityPicker instanceof Component) {
                    ((Component) abilityPicker).setVisible(false);
                }
                break;
            case SEND_MANA:
                String colorName = (String) item.getSourceObject();
                speak("Spending " + colorName.toLowerCase() + " mana.");
                sendManaType(colorName);
                break;
            case NONE:
                speak("No action for this item.");
                break;
        }
    }

    private void clickButtonItem(ZoneItem item) {
        Object[] btnRefs = (Object[]) item.getSourceObject();
        JButton btn = (JButton) btnRefs[0];
        String linkField = (String) btnRefs[1];

        speak(item.getDisplayName());

        if (btn.isVisible()) {
            btn.doClick();
            return;
        }

        // Fall back to link button when game needs feedback
        Boolean needFeedback = findFieldTyped(helperPanel, "gameNeedFeedback", Boolean.class);
        if (needFeedback != null && needFeedback) {
            JButton linkBtn = getButtonField(linkField);
            if (linkBtn != null) {
                linkBtn.doClick();
            }
        }
    }

    private void readSelectedDetail() {
        ZoneListPanel focusedZone = getFocusedZone();
        if (focusedZone == null) return;

        ZoneItem item = focusedZone.getSelectedItem();
        if (item == null) {
            speak("Nothing selected.");
            return;
        }

        String detail = item.getDetailText();
        if (detail != null && !detail.isEmpty()) {
            speak(detail);
        } else {
            speak(item.getDisplayName());
        }
    }

    private ZoneListPanel getFocusedZone() {
        Component focused = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if (focused == null) return null;
        for (ZoneListPanel zone : allZones) {
            if (zone.getList() == focused || SwingUtilities.isDescendingFrom(focused, zone)) {
                return zone;
            }
        }
        return null;
    }

    private void returnFocusToXMage() {
        for (Window w : Window.getWindows()) {
            if (w != this && w.isVisible() && w instanceof JFrame) {
                w.toFront();
                w.requestFocus();
                speak("Returned to XMage.");
                return;
            }
        }
    }

    // ========== GAME STATE ACCESS ==========

    private Object getGameView() {
        if (lastGameData == null) return null;
        return findFieldDeep(lastGameData, "game");
    }

    private Object getMyPlayer(Object gameView) {
        Object myPlayer = callMethod(gameView, "getMyPlayer");
        if (myPlayer != null) return myPlayer;

        Object playersList = callMethod(gameView, "getPlayers");
        if (playersList instanceof List) {
            for (Object player : (List<?>) playersList) {
                if (callBool(player, "getControlled")) return player;
            }
            List<?> list = (List<?>) playersList;
            if (!list.isEmpty()) return list.get(0);
        }
        return null;
    }

    private String getFeedbackText() {
        if (helperPanel == null) return null;
        try {
            Object textArea = findFieldDeep(helperPanel, "dialogTextArea");
            if (textArea == null) return null;
            Object currentText = findFieldDeep(textArea, "currentText");
            if (currentText != null) {
                return currentText.toString();
            }
            Method getText = textArea.getClass().getMethod("getText");
            Object result = getText.invoke(textArea);
            return result != null ? result.toString() : null;
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }

    // ========== TARGETS AND ABILITIES ==========

    private List<Object> getVisibleTargets() {
        List<Object> targets = new ArrayList<>();
        if (pickTargetDialogs == null) return targets;
        try {
            for (Object dialog : pickTargetDialogs) {
                if (!(dialog instanceof Component) || !((Component) dialog).isVisible()) continue;
                Object cardArea = findFieldDeep(dialog, "cardArea");
                if (cardArea == null) continue;
                Object innerPanel = findFieldDeep(cardArea, "cardArea");
                if (innerPanel instanceof Container) {
                    for (Component comp : ((Container) innerPanel).getComponents()) {
                        Object cardView = callMethod(comp, "getOriginal");
                        if (cardView != null) targets.add(cardView);
                    }
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return targets;
    }

    private List<Object[]> getSelectablePlayers() {
        List<Object[]> players = new ArrayList<>();
        Object gameView = getGameView();
        if (gameView == null) return players;
        try {
            Object playersList = callMethod(gameView, "getPlayers");
            if (playersList instanceof List) {
                for (Object player : (List<?>) playersList) {
                    String name = callString(player, "getName");
                    Object playerId = callMethod(player, "getPlayerId");
                    if (name != null && playerId != null) {
                        players.add(new Object[]{name, playerId});
                    }
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return players;
    }

    private List<Object[]> getAbilityChoices() {
        List<Object[]> choices = new ArrayList<>();
        if (abilityPicker == null) return choices;
        if (!(abilityPicker instanceof Component) || !((Component) abilityPicker).isVisible()) return choices;

        try {
            Object choicesList = findFieldDeep(abilityPicker, "choices");
            if (!(choicesList instanceof List)) return choices;

            for (Object action : (List<?>) choicesList) {
                Object id = findFieldDeep(action, "id");
                String text = cleanHtml(action.toString());
                choices.add(new Object[]{id, text});
            }
        } catch (Exception e) {
            // Ignore
        }
        return choices;
    }

    // ========== UUID SENDING ==========

    private void sendUUID(Object cardView) {
        try {
            Object gameId = findFieldDeep(gamePanel, "gameId");
            if (gameId == null) return;
            Class<?> callbackClass = Class.forName("mage.client.util.DefaultActionCallback");
            Object callbackInstance = callbackClass.getField("instance").get(null);
            Method mouseClicked = callbackClass.getMethod("mouseClicked", UUID.class,
                    Class.forName("mage.view.CardView"));
            mouseClicked.invoke(callbackInstance, gameId, cardView);
        } catch (Exception e) {
            System.err.println("[XMage Access] Window sendUUID error: " + e.getMessage());
        }
    }

    private void sendUUIDDirect(Object id) {
        try {
            Object gameId = findFieldDeep(gamePanel, "gameId");
            if (gameId == null) return;
            Class<?> sessionClass = Class.forName("mage.client.SessionHandler");
            Method sendMethod = sessionClass.getMethod("sendPlayerUUID", UUID.class, UUID.class);
            sendMethod.invoke(null, gameId, id);
        } catch (Exception e) {
            System.err.println("[XMage Access] Window sendUUIDDirect error: " + e.getMessage());
        }
    }

    private void sendBooleanFalse() {
        try {
            Object gameId = findFieldDeep(gamePanel, "gameId");
            if (gameId == null) return;
            Class<?> sessionClass = Class.forName("mage.client.SessionHandler");
            Method sendMethod = sessionClass.getMethod("sendPlayerBoolean", UUID.class, boolean.class);
            sendMethod.invoke(null, gameId, false);
        } catch (Exception e) {
            // Ignore
        }
    }

    private void sendManaType(String colorName) {
        try {
            Object gameId = findFieldDeep(gamePanel, "gameId");
            if (gameId == null) return;

            Object gameView = getGameView();
            if (gameView == null) return;
            Object myPlayer = getMyPlayer(gameView);
            if (myPlayer == null) return;
            Object playerId = callMethod(myPlayer, "getPlayerId");
            if (playerId == null) return;

            Class<?> manaTypeClass = Class.forName("mage.constants.ManaType");
            Method valueOf = manaTypeClass.getMethod("valueOf", String.class);
            Object manaTypeValue = valueOf.invoke(null, colorName);

            Class<?> sessionClass = Class.forName("mage.client.SessionHandler");
            Method sendMethod = sessionClass.getMethod("sendPlayerManaType",
                    UUID.class, UUID.class, manaTypeClass);
            sendMethod.invoke(null, gameId, playerId, manaTypeValue);
        } catch (Exception e) {
            System.err.println("[XMage Access] Window sendManaType error: " + e.getMessage());
        }
    }

    // ========== BUTTON ACCESS ==========

    private JButton getButtonField(String name) {
        if (helperPanel == null) return null;
        try {
            Field field = helperPanel.getClass().getDeclaredField(name);
            field.setAccessible(true);
            Object val = field.get(helperPanel);
            if (val instanceof JButton) return (JButton) val;
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }

    // ========== FORMATTING HELPERS ==========

    private String formatCardDetailed(Object cardView) {
        StringBuilder sb = new StringBuilder();
        String name = callString(cardView, "getName");
        String manaCost = callString(cardView, "getManaCostStr");
        String types = callString(cardView, "getTypeText");
        String power = callString(cardView, "getPower");
        String toughness = callString(cardView, "getToughness");
        boolean isCreature = callBool(cardView, "isCreature");

        sb.append(name != null ? name : "Unknown").append(". ");
        if (manaCost != null && !manaCost.isEmpty()) {
            sb.append("Mana cost: ").append(formatManaCost(manaCost)).append(". ");
        }
        if (types != null && !types.isEmpty()) {
            sb.append(types).append(". ");
        }
        if (isCreature && power != null && toughness != null) {
            sb.append(power).append("/").append(toughness).append(". ");
        }

        Object rules = callMethod(cardView, "getRules");
        if (rules instanceof List) {
            List<?> rulesList = (List<?>) rules;
            if (!rulesList.isEmpty()) {
                sb.append("Rules: ");
                for (Object rule : rulesList) {
                    sb.append(cleanHtml(rule.toString())).append(". ");
                }
            }
        }
        return sb.toString();
    }

    private String formatPermanentDetailed(Object perm) {
        StringBuilder sb = new StringBuilder();
        String name = callString(perm, "getName");
        String types = callString(perm, "getTypeText");
        boolean isCreature = callBool(perm, "isCreature");
        boolean isTapped = callBool(perm, "isTapped");

        sb.append(name != null ? name : "Unknown").append(". ");
        if (types != null && !types.isEmpty()) sb.append(types).append(". ");

        if (isCreature) {
            String power = callString(perm, "getPower");
            String toughness = callString(perm, "getToughness");
            if (power != null && toughness != null) sb.append(power).append("/").append(toughness).append(". ");
        }

        if (isTapped) sb.append("Tapped. ");
        if (callBool(perm, "hasSummoningSickness")) sb.append("Summoning sickness. ");

        Object counters = callMethod(perm, "getCounters");
        if (counters instanceof List && !((List<?>) counters).isEmpty()) {
            sb.append("Counters: ");
            for (Object counter : (List<?>) counters) {
                String cName = callString(counter, "getName");
                int cCount = callInt(counter, "getCount");
                if (cName != null) sb.append(cCount).append(" ").append(cName).append(", ");
            }
        }

        Object rules = callMethod(perm, "getRules");
        if (rules instanceof List && !((List<?>) rules).isEmpty()) {
            sb.append("Rules: ");
            for (Object rule : (List<?>) rules) {
                sb.append(cleanHtml(rule.toString())).append(". ");
            }
        }
        return sb.toString();
    }

    private String formatManaCost(String manaCost) {
        if (manaCost == null) return "";
        return manaCost
                .replace("{W}", "white ")
                .replace("{U}", "blue ")
                .replace("{B}", "black ")
                .replace("{R}", "red ")
                .replace("{G}", "green ")
                .replace("{C}", "colorless ")
                .replace("{X}", "X ")
                .replaceAll("\\{(\\d+)\\}", "$1 ")
                .trim();
    }

    private String cleanHtml(String text) {
        if (text == null) return "";
        return text.replaceAll("<[^>]*>", " ")
                .replaceAll("&nbsp;", " ")
                .replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("\\s+", " ")
                .trim();
    }

    // ========== REFLECTION HELPERS ==========

    @SuppressWarnings("unchecked")
    private <T> T findFieldTyped(Object target, String name, Class<T> type) {
        Object val = findFieldDeep(target, name);
        if (type.isInstance(val)) return (T) val;
        return null;
    }

    private Object findFieldDeep(Object target, String name) {
        if (target == null) return null;
        try {
            Class<?> clazz = target.getClass();
            while (clazz != null) {
                try {
                    Field field = clazz.getDeclaredField(name);
                    field.setAccessible(true);
                    return field.get(target);
                } catch (NoSuchFieldException e) {
                    clazz = clazz.getSuperclass();
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }

    private static Object callMethod(Object obj, String methodName) {
        if (obj == null) return null;
        try {
            Method method = obj.getClass().getMethod(methodName);
            return method.invoke(obj);
        } catch (Exception e) {
            return null;
        }
    }

    private static String callString(Object obj, String methodName) {
        Object result = callMethod(obj, methodName);
        return result != null ? result.toString() : null;
    }

    private static int callInt(Object obj, String methodName) {
        Object result = callMethod(obj, methodName);
        if (result instanceof Number) return ((Number) result).intValue();
        return 0;
    }

    private static boolean callBool(Object obj, String methodName) {
        Object result = callMethod(obj, methodName);
        if (result instanceof Boolean) return (Boolean) result;
        return false;
    }

    // ========== SPEECH ==========

    private void speak(String text) {
        SpeechOutput speech = AccessibilityManager.getInstance().getSpeech();
        if (speech != null) {
            speech.speak(text);
        }
    }
}
