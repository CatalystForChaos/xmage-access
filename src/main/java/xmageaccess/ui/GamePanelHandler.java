package xmageaccess.ui;

import xmageaccess.AccessibilityManager;
import xmageaccess.speech.SpeechOutput;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Accessibility handler for the XMage Game Panel (active gameplay).
 * Polls the game state and announces changes via speech.
 *
 * Keyboard shortcuts:
 *   Ctrl+F1         - Read current prompt/question
 *   Ctrl+F2         - Read all player life totals and info
 *   Ctrl+F3         - Read your hand (full list)
 *   Ctrl+F4         - Read the battlefield (summary)
 *   Ctrl+F5         - Read the stack
 *   Ctrl+F6         - Read graveyards
 *   Ctrl+F7         - Read exile zones
 *   Ctrl+F8         - Read combat info
 *   Ctrl+F9         - Read mana pool
 *   Ctrl+F10        - Read command zone
 *   Ctrl+F11        - Read revealed/looked-at cards
 *   Ctrl+Left/Right - Navigate hand cards
 *   Ctrl+Enter      - Play/cast card at hand cursor
 *   Ctrl+D          - Read detailed card info at hand cursor
 *   Ctrl+Shift+Left/Right  - Navigate battlefield permanents
 *   Ctrl+Shift+Up/Down     - Switch between players' battlefields
 *   Ctrl+Shift+Enter       - Click permanent (attack/block/activate)
 *   Ctrl+Shift+D           - Read permanent detail
 *   Ctrl+L          - Read last 3 game log entries
 *   Ctrl+Shift+L    - Read last 10 game log entries
 *   Ctrl+T          - Read available targets/abilities
 *   Ctrl+Shift+1-9  - Select target or ability by number
 *   Ctrl+1          - Click left button (OK/Yes)
 *   Ctrl+2          - Click right button (Cancel/No)
 *   Ctrl+3          - Click special button
 *   Ctrl+Z          - Undo
 *   Ctrl+M          - Focus chat input (type and press Enter to send)
 *   Ctrl+Shift+M    - Read last 5 chat messages
 */
public class GamePanelHandler {

    private final Component gamePanel;
    private Timer pollTimer;

    // Cached references found via reflection
    private Object helperPanel;        // HelperPanel
    private Component feedbackPanel;   // FeedbackPanel
    private Object lastGameData;       // LastGameData inner class instance
    private Map<?, ?> playerPanels;    // Map<UUID, PlayAreaPanel>
    private List<?> pickTargetDialogs; // ArrayList<ShowCardsDialog>
    private Object abilityPicker;      // AbilityPicker component
    private Object gameChatPanel;      // ChatPanelBasic for game log
    private Object userChatPanel;      // ChatPanelBasic for player chat
    private ChatAccessHelper chatHelper;

    // State tracking for change detection
    private String lastFeedbackText = "";
    private String lastPhase = "";
    private int lastTurn = -1;
    private String lastActivePlayer = "";
    private final Map<String, Integer> lastLifeTotals = new HashMap<>();
    private int lastStackSize = -1;
    private final java.util.Set<Object> lastStackIds = new java.util.HashSet<>();
    private int lastHandSize = -1;
    private int lastTargetCount = 0;
    private boolean lastAbilityPickerVisible = false;
    private int lastCombatGroupCount = 0;
    private Object lastGameId = null; // UUID of the current game — changes between games in a match

    // Hand navigation cursor
    private int handCursorIndex = 0;
    private List<Object> handCards = new ArrayList<>();

    // Battlefield navigation cursor
    private int bfCursorIndex = 0;
    private int bfPlayerIndex = 0;
    private List<Object> bfPermanents = new ArrayList<>();
    private List<String> bfPlayerNames = new ArrayList<>();

    // Accessible window for event-driven refresh
    private AccessibleGameWindow accessibleWindow;

    public GamePanelHandler(Component gamePanel) {
        this.gamePanel = gamePanel;
    }

    public void setAccessibleWindow(AccessibleGameWindow window) {
        this.accessibleWindow = window;
    }

    public void attach() {
        try {
            discoverComponents();
            addKeyboardShortcuts();
            startPolling();

            speak("Game started. "
                    + "Ctrl+Left, Right navigate hand. Ctrl+Enter to play. "
                    + "Ctrl+F1 for prompt, Ctrl+1 to confirm, Ctrl+2 to cancel.");

        } catch (Exception e) {
            System.err.println("[XMage Access] Error attaching to game panel: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void detach() {
        if (pollTimer != null) {
            pollTimer.stop();
        }
    }

    private void discoverComponents() {
        helperPanel = findFieldDeep(gamePanel, "helper");
        feedbackPanel = findFieldTyped(gamePanel, "feedbackPanel", Component.class);
        lastGameData = findFieldDeep(gamePanel, "lastGameData");
        playerPanels = findFieldTyped(gamePanel, "players", Map.class);
        pickTargetDialogs = findFieldTyped(gamePanel, "pickTarget", List.class);
        abilityPicker = findFieldDeep(gamePanel, "abilityPicker");
        gameChatPanel = findFieldDeep(gamePanel, "gameChatPanel");
        userChatPanel = findFieldDeep(gamePanel, "userChatPanel");

        // Attach chat helper for player-to-player chat
        if (userChatPanel != null) {
            chatHelper = new ChatAccessHelper(userChatPanel);
            chatHelper.attach();
        }

        System.out.println("[XMage Access] Game components - helper: " + (helperPanel != null)
                + ", feedback: " + (feedbackPanel != null)
                + ", gameData: " + (lastGameData != null)
                + ", players: " + (playerPanels != null ? playerPanels.size() : 0)
                + ", abilityPicker: " + (abilityPicker != null)
                + ", gameLog: " + (gameChatPanel != null)
                + ", userChat: " + (userChatPanel != null));
    }

    private void addKeyboardShortcuts() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addKeyEventDispatcher(e -> {
                    if (e.getID() != KeyEvent.KEY_PRESSED) return false;
                    if (!isGameVisible()) return false;

                    // --- Ctrl+Shift shortcuts ---
                    if (e.isControlDown() && e.isShiftDown()) {
                        switch (e.getKeyCode()) {
                            // Target/ability selection
                            case KeyEvent.VK_1: selectTargetOrAbility(0); return true;
                            case KeyEvent.VK_2: selectTargetOrAbility(1); return true;
                            case KeyEvent.VK_3: selectTargetOrAbility(2); return true;
                            case KeyEvent.VK_4: selectTargetOrAbility(3); return true;
                            case KeyEvent.VK_5: selectTargetOrAbility(4); return true;
                            case KeyEvent.VK_6: selectTargetOrAbility(5); return true;
                            case KeyEvent.VK_7: selectTargetOrAbility(6); return true;
                            case KeyEvent.VK_8: selectTargetOrAbility(7); return true;
                            case KeyEvent.VK_9: selectTargetOrAbility(8); return true;
                            // Battlefield navigation
                            case KeyEvent.VK_LEFT: navigateBattlefield(-1); return true;
                            case KeyEvent.VK_RIGHT: navigateBattlefield(1); return true;
                            case KeyEvent.VK_UP: switchBattlefieldPlayer(-1); return true;
                            case KeyEvent.VK_DOWN: switchBattlefieldPlayer(1); return true;
                            case KeyEvent.VK_ENTER: clickBattlefieldPermanent(); return true;
                            case KeyEvent.VK_D: readBattlefieldDetail(); return true;
                            // Game log (extended)
                            case KeyEvent.VK_L: readGameLog(10); return true;
                            // Chat (read recent)
                            case KeyEvent.VK_M: readChat(); return true;
                        }
                    }

                    // --- Ctrl (no shift) shortcuts ---
                    if (e.isControlDown() && !e.isShiftDown()) {
                        switch (e.getKeyCode()) {
                            // Zone reading
                            case KeyEvent.VK_F1: readCurrentPrompt(); return true;
                            case KeyEvent.VK_F2: readPlayerInfo(); return true;
                            case KeyEvent.VK_F3: readHand(); return true;
                            case KeyEvent.VK_F4: readBattlefield(); return true;
                            case KeyEvent.VK_F5: readStack(); return true;
                            case KeyEvent.VK_F6: readGraveyard(); return true;
                            case KeyEvent.VK_F7: readExile(); return true;
                            case KeyEvent.VK_F8: readCombat(); return true;
                            case KeyEvent.VK_F9: readManaPool(); return true;
                            case KeyEvent.VK_F10: readCommandZone(); return true;
                            case KeyEvent.VK_F11: readRevealed(); return true;
                            // Hand navigation
                            case KeyEvent.VK_LEFT: navigateHand(-1); return true;
                            case KeyEvent.VK_RIGHT: navigateHand(1); return true;
                            case KeyEvent.VK_ENTER: playHandCard(); return true;
                            case KeyEvent.VK_D: readHandCardDetail(); return true;
                            // Game log
                            case KeyEvent.VK_L: readGameLog(3); return true;
                            // Targets
                            case KeyEvent.VK_T: readTargets(); return true;
                            // Buttons
                            case KeyEvent.VK_1: clickButton("btnLeft", "linkLeft"); return true;
                            case KeyEvent.VK_2: clickButton("btnRight", "linkRight"); return true;
                            case KeyEvent.VK_3: clickButton("btnSpecial", "linkSpecial"); return true;
                            case KeyEvent.VK_Z: clickButton("btnUndo", "linkUndo"); return true;
                            // Chat (focus input)
                            case KeyEvent.VK_M: focusChatInput(); return true;
                        }
                    }
                    return false;
                });
    }

    private void startPolling() {
        pollTimer = new Timer(5000, e -> {
            if (!isGameVisible()) return;
            try {
                checkFeedbackChanges();
                checkGameStateChanges();
                checkTargetDialogs();
                checkAbilityPicker();
                checkHandSizeChange();
                checkCombatChanges();
            } catch (Exception ex) {
                // Don't spam errors
            }
        });
        pollTimer.setRepeats(true);
        pollTimer.start();
    }

    private boolean isGameVisible() {
        return gamePanel != null && gamePanel.isVisible();
    }

    private void triggerWindowRefresh() {
        if (accessibleWindow != null) {
            accessibleWindow.refreshNow();
        }
    }

    // ========== CHANGE DETECTION ==========

    private void checkFeedbackChanges() {
        String text = getFeedbackText();
        if (text != null && !text.equals(lastFeedbackText)) {
            lastFeedbackText = text;
            if (!text.isEmpty() && !text.equals("<Empty>")) {
                StringBuilder sb = new StringBuilder(cleanHtml(text));
                String buttons = getVisibleButtons();
                if (!buttons.isEmpty()) {
                    sb.append(". ").append(buttons);
                }
                speak(sb.toString());
            }
            triggerWindowRefresh();
        }
    }

    private void checkGameStateChanges() {
        if (lastGameData == null) return;
        try {
            Object gameView = getGameView();
            if (gameView == null) return;

            boolean changed = false;

            int turn = callInt(gameView, "getTurn");
            String activePlayer = callString(gameView, "getActivePlayerName");
            String phase = callString(gameView, "getPhase");

            // Detect new game within the same GamePanel by watching the gameId field.
            // XMage calls showGame() with a fresh UUID for each game in a match,
            // so a changed gameId is the definitive signal that a new game has started.
            Object currentGameId = findFieldDeep(gamePanel, "gameId");
            if (currentGameId != null && !currentGameId.equals(lastGameId)) {
                boolean isSwitch = lastGameId != null; // false on the very first game
                lastLifeTotals.clear();
                lastTurn = -1;
                lastPhase = "";
                lastActivePlayer = null;
                lastStackSize = -1;
                lastStackIds.clear();
                lastHandSize = -1;
                lastTargetCount = 0;
                lastAbilityPickerVisible = false;
                lastCombatGroupCount = 0;
                lastFeedbackText = "";
                lastGameId = currentGameId;
                if (isSwitch) {
                    speak("New game.");
                }
                triggerWindowRefresh();
                return;
            }
            lastGameId = currentGameId;

            // Announce turn changes (new turn = new active player's turn)
            if (turn != lastTurn && turn > 0) {
                if (lastTurn > 0) {
                    String who = activePlayer != null ? activePlayer + "'s turn" : "New turn";
                    speak("Turn " + turn + ". " + who + ".");
                }
                lastTurn = turn;
                changed = true;
            }

            // Announce important phase/step transitions
            // Phase values: "Beginning", "Precombat Main", "Combat", "Postcombat Main", "End"
            // Step values: "Declare Attackers", "Declare Blockers", "Begin Combat", etc.
            if (phase != null && !phase.equals(lastPhase)) {
                if (!lastPhase.isEmpty()) {
                    switch (phase) {
                        case "Combat":
                            speak("Combat phase.");
                            break;
                        case "Postcombat Main":
                            speak("Second main phase.");
                            break;
                        // Beginning, Precombat Main, End covered by turn announcement or feedback
                    }
                }
                lastPhase = phase;
                changed = true;
            }


            if (activePlayer != null && !activePlayer.equals(lastActivePlayer)) {
                lastActivePlayer = activePlayer;
                changed = true;
            }

            // Life total changes
            Object playersList = callMethod(gameView, "getPlayers");
            if (playersList instanceof List) {
                for (Object player : (List<?>) playersList) {
                    String name = callString(player, "getName");
                    int life = callInt(player, "getLife");
                    if (name != null) {
                        Integer prev = lastLifeTotals.get(name);
                        if (prev != null && prev != life) {
                            int change = life - prev;
                            String changeText = change > 0 ? "gained " + change : "lost " + Math.abs(change);
                            speak(name + " " + changeText + " life. Now at " + life + ".");
                            changed = true;
                        }
                        lastLifeTotals.put(name, life);
                    }
                }
            }

            // Stack changes - announce new entries
            Object stack = callMethod(gameView, "getStack");
            if (stack instanceof Map) {
                Map<?, ?> stackMap = (Map<?, ?>) stack;
                int newSize = stackMap.size();
                if (newSize != lastStackSize) {
                    changed = true;
                    // Announce newly added stack entries
                    if (newSize > lastStackSize && lastStackSize >= 0) {
                        for (Map.Entry<?, ?> entry : stackMap.entrySet()) {
                            Object id = entry.getKey();
                            if (!lastStackIds.contains(id)) {
                                Object cardView = entry.getValue();
                                String spellName = callString(cardView, "getName");
                                if (spellName != null) {
                                    speak("Stack: " + spellName + ".");
                                }
                            }
                        }
                    } else if (newSize == 0 && lastStackSize > 0) {
                        speak("Stack empty.");
                    }
                }
                lastStackSize = newSize;
                lastStackIds.clear();
                lastStackIds.addAll(stackMap.keySet());
            }

            if (changed) {
                triggerWindowRefresh();
            }
        } catch (Exception e) {
            // Ignore
        }
    }

    private void checkHandSizeChange() {
        Object gameView = getGameView();
        if (gameView == null) return;
        Object hand = callMethod(gameView, "getMyHand");
        if (hand instanceof Map) {
            int size = ((Map<?, ?>) hand).size();
            if (size != lastHandSize && lastHandSize >= 0) {
                if (size > lastHandSize) {
                    speak("Drew a card. " + size + " in hand.");
                } else if (size < lastHandSize) {
                    speak(size + " cards in hand.");
                }
                // Reset cursor if out of bounds
                if (handCursorIndex >= size) {
                    handCursorIndex = Math.max(0, size - 1);
                }
                triggerWindowRefresh();
            }
            lastHandSize = size;
        }
    }

    private void checkCombatChanges() {
        Object gameView = getGameView();
        if (gameView == null) return;
        Object combatList = callMethod(gameView, "getCombat");
        if (combatList instanceof List) {
            int count = ((List<?>) combatList).size();
            if (count != lastCombatGroupCount && count > 0) {
                readCombatInternal((List<?>) combatList);
            }
            lastCombatGroupCount = count;
        }
    }

    private void checkAbilityPicker() {
        if (abilityPicker == null) return;
        boolean visible = abilityPicker instanceof Component && ((Component) abilityPicker).isVisible();
        if (visible && !lastAbilityPickerVisible) {
            announceAbilityChoices();
        }
        lastAbilityPickerVisible = visible;
    }

    private void checkTargetDialogs() {
        List<Object> cardTargets = getVisibleTargets();
        int count = cardTargets.size();
        if (count != lastTargetCount && count > 0) {
            StringBuilder sb = new StringBuilder("Choose target. ");
            for (int i = 0; i < cardTargets.size(); i++) {
                String name = callString(cardTargets.get(i), "getName");
                if (name != null) {
                    sb.append("Ctrl+Shift+").append(i + 1).append(": ").append(name).append(". ");
                }
            }
            speak(sb.toString());
        }
        lastTargetCount = count;
    }

    // ========== HAND NAVIGATION ==========

    private void refreshHandCache() {
        handCards.clear();
        Object gameView = getGameView();
        if (gameView == null) return;
        Object hand = callMethod(gameView, "getMyHand");
        if (hand instanceof Map) {
            handCards.addAll(((Map<?, ?>) hand).values());
        }
    }

    private void navigateHand(int direction) {
        refreshHandCache();
        if (handCards.isEmpty()) {
            speak("Hand is empty.");
            return;
        }
        handCursorIndex += direction;
        if (handCursorIndex < 0) handCursorIndex = handCards.size() - 1;
        if (handCursorIndex >= handCards.size()) handCursorIndex = 0;

        Object card = handCards.get(handCursorIndex);
        speak(formatCardBrief(card, handCursorIndex + 1, handCards.size()));
    }

    private void playHandCard() {
        refreshHandCache();
        if (handCards.isEmpty()) {
            speak("Hand is empty.");
            return;
        }
        if (handCursorIndex >= handCards.size()) {
            handCursorIndex = 0;
        }
        Object card = handCards.get(handCursorIndex);
        String name = callString(card, "getName");
        speak("Playing " + (name != null ? name : "card") + ".");
        sendUUID(card);
    }

    private void readHandCardDetail() {
        refreshHandCache();
        if (handCards.isEmpty()) {
            speak("Hand is empty.");
            return;
        }
        if (handCursorIndex >= handCards.size()) {
            handCursorIndex = 0;
        }
        speak(formatCardDetailed(handCards.get(handCursorIndex)));
    }

    // ========== BATTLEFIELD NAVIGATION ==========

    private void refreshBattlefieldCache() {
        bfPermanents.clear();
        bfPlayerNames.clear();
        Object gameView = getGameView();
        if (gameView == null) return;

        Object playersList = callMethod(gameView, "getPlayers");
        if (!(playersList instanceof List)) return;

        List<?> players = (List<?>) playersList;
        for (Object player : players) {
            String name = callString(player, "getName");
            bfPlayerNames.add(name != null ? name : "Unknown");
        }

        if (bfPlayerIndex >= players.size()) bfPlayerIndex = 0;
        if (players.isEmpty()) return;

        Object player = players.get(bfPlayerIndex);
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
            bfPermanents.addAll(creatures);
            bfPermanents.addAll(others);
            bfPermanents.addAll(lands);
        }
    }

    private void navigateBattlefield(int direction) {
        refreshBattlefieldCache();
        if (bfPermanents.isEmpty()) {
            String who = bfPlayerIndex < bfPlayerNames.size() ? bfPlayerNames.get(bfPlayerIndex) : "Unknown";
            speak(who + "'s battlefield is empty.");
            return;
        }
        bfCursorIndex += direction;
        if (bfCursorIndex < 0) bfCursorIndex = bfPermanents.size() - 1;
        if (bfCursorIndex >= bfPermanents.size()) bfCursorIndex = 0;

        Object perm = bfPermanents.get(bfCursorIndex);
        speak(formatPermanentBrief(perm, bfCursorIndex + 1, bfPermanents.size()));
    }

    private void switchBattlefieldPlayer(int direction) {
        refreshBattlefieldCache();
        if (bfPlayerNames.isEmpty()) {
            speak("No players.");
            return;
        }
        bfPlayerIndex += direction;
        if (bfPlayerIndex < 0) bfPlayerIndex = bfPlayerNames.size() - 1;
        if (bfPlayerIndex >= bfPlayerNames.size()) bfPlayerIndex = 0;
        bfCursorIndex = 0;
        refreshBattlefieldCache();
        String name = bfPlayerNames.get(bfPlayerIndex);
        speak(name + "'s battlefield. " + bfPermanents.size() + " permanents.");
    }

    private void clickBattlefieldPermanent() {
        refreshBattlefieldCache();
        if (bfPermanents.isEmpty()) {
            speak("No permanents to click.");
            return;
        }
        if (bfCursorIndex >= bfPermanents.size()) bfCursorIndex = 0;
        Object perm = bfPermanents.get(bfCursorIndex);
        String name = callString(perm, "getName");
        speak("Clicked " + (name != null ? name : "permanent") + ".");
        sendUUID(perm);
    }

    private void readBattlefieldDetail() {
        refreshBattlefieldCache();
        if (bfPermanents.isEmpty()) {
            speak("No permanents.");
            return;
        }
        if (bfCursorIndex >= bfPermanents.size()) bfCursorIndex = 0;
        speak(formatPermanentDetailed(bfPermanents.get(bfCursorIndex)));
    }

    // ========== GAME LOG ==========

    private void readGameLog(int count) {
        if (gameChatPanel == null) {
            speak("Game log not available.");
            return;
        }
        try {
            Object textPane = findFieldDeep(gameChatPanel, "txtConversation");
            if (textPane == null) {
                speak("Game log not available.");
                return;
            }
            // Get the document text (plain text, not HTML)
            Object doc = callMethod(textPane, "getDocument");
            if (doc == null) {
                speak("Game log empty.");
                return;
            }
            int length = callInt(doc, "getLength");
            if (length == 0) {
                speak("Game log empty.");
                return;
            }

            // Get text from document
            Method getTextMethod = doc.getClass().getMethod("getText", int.class, int.class);
            String text = (String) getTextMethod.invoke(doc, 0, length);
            if (text == null || text.trim().isEmpty()) {
                speak("Game log empty.");
                return;
            }

            // Split into lines, filter empty, take last N
            String[] allLines = text.split("\n");
            List<String> lines = new ArrayList<>();
            for (String line : allLines) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    lines.add(trimmed);
                }
            }

            if (lines.isEmpty()) {
                speak("Game log empty.");
                return;
            }

            int start = Math.max(0, lines.size() - count);
            StringBuilder sb = new StringBuilder("Game log. ");
            for (int i = start; i < lines.size(); i++) {
                sb.append(lines.get(i)).append(". ");
            }
            speak(sb.toString());
        } catch (Exception e) {
            speak("Could not read game log.");
        }
    }

    // ========== CHAT ==========

    private void readChat() {
        if (chatHelper != null) {
            chatHelper.readRecentChat(5);
        } else {
            speak("Chat not available.");
        }
    }

    private void focusChatInput() {
        if (chatHelper != null) {
            chatHelper.focusInput();
        } else {
            speak("Chat not available.");
        }
    }

    // ========== ZONE READING ==========

    private void readGraveyard() {
        Object gameView = getGameView();
        if (gameView == null) {
            speak("Game data not available.");
            return;
        }
        try {
            Object playersList = callMethod(gameView, "getPlayers");
            if (!(playersList instanceof List)) {
                speak("No player data.");
                return;
            }

            StringBuilder sb = new StringBuilder();
            for (Object player : (List<?>) playersList) {
                String name = callString(player, "getName");
                Object graveyard = callMethod(player, "getGraveyard");
                if (graveyard instanceof Map) {
                    Map<?, ?> cards = (Map<?, ?>) graveyard;
                    sb.append(name).append("'s graveyard: ");
                    if (cards.isEmpty()) {
                        sb.append("empty. ");
                    } else {
                        sb.append(cards.size()).append(" cards. ");
                        for (Object card : cards.values()) {
                            String cardName = callString(card, "getName");
                            if (cardName != null) sb.append(cardName).append(", ");
                        }
                    }
                }
            }
            speak(sb.length() > 0 ? sb.toString() : "No graveyard data.");
        } catch (Exception e) {
            speak("Could not read graveyard.");
        }
    }

    private void readExile() {
        Object gameView = getGameView();
        if (gameView == null) {
            speak("Game data not available.");
            return;
        }
        try {
            Object exileList = callMethod(gameView, "getExile");
            if (!(exileList instanceof List)) {
                speak("No exile data.");
                return;
            }
            List<?> exiles = (List<?>) exileList;
            if (exiles.isEmpty()) {
                speak("No cards in exile.");
                return;
            }

            StringBuilder sb = new StringBuilder();
            for (Object exile : exiles) {
                String zoneName = callString(exile, "getName");
                sb.append("Exile");
                if (zoneName != null && !zoneName.isEmpty()) {
                    sb.append(" ").append(zoneName);
                }
                sb.append(": ");
                // ExileView extends CardsView (Map)
                if (exile instanceof Map) {
                    Map<?, ?> cards = (Map<?, ?>) exile;
                    if (cards.isEmpty()) {
                        sb.append("empty. ");
                    } else {
                        sb.append(cards.size()).append(" cards. ");
                        for (Object card : cards.values()) {
                            String name = callString(card, "getName");
                            if (name != null) sb.append(name).append(", ");
                        }
                    }
                }
            }
            speak(sb.toString());
        } catch (Exception e) {
            speak("Could not read exile.");
        }
    }

    private void readCombat() {
        Object gameView = getGameView();
        if (gameView == null) {
            speak("Game data not available.");
            return;
        }
        try {
            Object combatList = callMethod(gameView, "getCombat");
            if (!(combatList instanceof List) || ((List<?>) combatList).isEmpty()) {
                speak("No combat.");
                return;
            }
            readCombatInternal((List<?>) combatList);
        } catch (Exception e) {
            speak("Could not read combat.");
        }
    }

    private void readCombatInternal(List<?> combatGroups) {
        StringBuilder sb = new StringBuilder("Combat. ");
        for (Object group : combatGroups) {
            String defender = callString(group, "getDefenderName");
            if (defender != null) sb.append("Attacking ").append(defender).append(": ");

            Object attackers = callMethod(group, "getAttackers");
            if (attackers instanceof Map) {
                for (Object card : ((Map<?, ?>) attackers).values()) {
                    String name = callString(card, "getName");
                    String power = callString(card, "getPower");
                    String toughness = callString(card, "getToughness");
                    if (name != null) {
                        sb.append(name);
                        if (power != null && toughness != null) sb.append(" ").append(power).append("/").append(toughness);
                        sb.append(", ");
                    }
                }
            }

            boolean blocked = callBool(group, "isBlocked");
            if (blocked) {
                sb.append("blocked by ");
                Object blockers = callMethod(group, "getBlockers");
                if (blockers instanceof Map) {
                    for (Object card : ((Map<?, ?>) blockers).values()) {
                        String name = callString(card, "getName");
                        if (name != null) sb.append(name).append(", ");
                    }
                }
            } else {
                sb.append("unblocked. ");
            }
        }
        speak(sb.toString());
    }

    private void readManaPool() {
        Object gameView = getGameView();
        if (gameView == null) {
            speak("Game data not available.");
            return;
        }
        try {
            // Find your player
            Object myPlayer = getMyPlayer(gameView);
            if (myPlayer == null) {
                speak("Player data not available.");
                return;
            }

            Object pool = callMethod(myPlayer, "getManaPool");
            if (pool == null) {
                speak("Mana pool not available.");
                return;
            }

            int white = callInt(pool, "getWhite");
            int blue = callInt(pool, "getBlue");
            int black = callInt(pool, "getBlack");
            int red = callInt(pool, "getRed");
            int green = callInt(pool, "getGreen");
            int colorless = callInt(pool, "getColorless");

            int total = white + blue + black + red + green + colorless;
            if (total == 0) {
                speak("Mana pool is empty.");
                return;
            }

            StringBuilder sb = new StringBuilder("Mana: ");
            if (white > 0) sb.append(white).append(" white, ");
            if (blue > 0) sb.append(blue).append(" blue, ");
            if (black > 0) sb.append(black).append(" black, ");
            if (red > 0) sb.append(red).append(" red, ");
            if (green > 0) sb.append(green).append(" green, ");
            if (colorless > 0) sb.append(colorless).append(" colorless, ");
            speak(sb.toString());
        } catch (Exception e) {
            speak("Could not read mana pool.");
        }
    }

    private void readCommandZone() {
        Object gameView = getGameView();
        if (gameView == null) {
            speak("Game data not available.");
            return;
        }
        try {
            Object playersList = callMethod(gameView, "getPlayers");
            if (!(playersList instanceof List)) {
                speak("No player data.");
                return;
            }

            StringBuilder sb = new StringBuilder();
            for (Object player : (List<?>) playersList) {
                String playerName = callString(player, "getName");
                Object commandList = callMethod(player, "getCommandObjectList");
                if (!(commandList instanceof List)) continue;
                List<?> cmds = (List<?>) commandList;
                if (cmds.isEmpty()) continue;

                for (Object cmdObj : cmds) {
                    String name = callString(cmdObj, "getName");
                    if (name == null) continue;
                    sb.append(playerName != null ? playerName : "Unknown");
                    sb.append(": ").append(name);

                    // Add mana cost if available (CommanderView extends CardView)
                    String manaCost = callString(cmdObj, "getManaCostStr");
                    if (manaCost != null && !manaCost.isEmpty()) {
                        sb.append(", ").append(formatManaCost(manaCost));
                    }

                    // Add power/toughness for creatures
                    boolean isCreature = callBool(cmdObj, "isCreature");
                    if (isCreature) {
                        String power = callString(cmdObj, "getPower");
                        String toughness = callString(cmdObj, "getToughness");
                        if (power != null && toughness != null) {
                            sb.append(", ").append(power).append("/").append(toughness);
                        }
                    }

                    sb.append(". ");
                }
            }
            speak(sb.length() > 0 ? "Command zone. " + sb.toString() : "Command zone is empty.");
        } catch (Exception e) {
            speak("Could not read command zone.");
        }
    }

    private void readRevealed() {
        try {
            StringBuilder sb = new StringBuilder();
            appendRevealedCards(sb, "revealed");
            appendRevealedCards(sb, "lookedAt");

            if (sb.length() > 0) {
                speak("Revealed cards. " + sb.toString());
            } else {
                speak("No revealed cards.");
            }
        } catch (Exception e) {
            speak("Could not read revealed cards.");
        }
    }

    private void appendRevealedCards(StringBuilder sb, String fieldName) {
        Map<?, ?> dialogMap = findFieldTyped(gamePanel, fieldName, Map.class);
        if (dialogMap == null) return;
        for (Map.Entry<?, ?> entry : dialogMap.entrySet()) {
            String contextName = entry.getKey() != null ? entry.getKey().toString() : "";
            Object dialog = entry.getValue();
            if (!(dialog instanceof Component) || !((Component) dialog).isVisible()) continue;
            try {
                Method getCards = dialog.getClass().getMethod("getMageCardsForUpdate");
                Object cardsMap = getCards.invoke(dialog);
                if (cardsMap instanceof Map) {
                    for (Object mageCard : ((Map<?, ?>) cardsMap).values()) {
                        Object cardView = callMethod(mageCard, "getOriginal");
                        if (cardView == null) continue;
                        String cardName = callString(cardView, "getName");
                        if (cardName == null) continue;
                        if (!contextName.isEmpty()) {
                            sb.append(contextName).append(": ");
                        }
                        sb.append(cardName).append(". ");
                    }
                }
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    // ========== EXISTING ZONE READERS ==========

    private void readCurrentPrompt() {
        StringBuilder sb = new StringBuilder();

        // Include turn/phase info
        Object gameView = getGameView();
        if (gameView != null) {
            int turn = callInt(gameView, "getTurn");
            String phase = callString(gameView, "getPhase");
            String step = callString(gameView, "getStep");
            String activePlayer = callString(gameView, "getActivePlayerName");
            if (turn > 0) sb.append("Turn ").append(turn).append(". ");
            if (activePlayer != null) sb.append(activePlayer).append("'s turn. ");
            if (phase != null) sb.append(formatPhase(phase));
            if (step != null) sb.append(", ").append(formatPhase(step));
            sb.append(". ");
        }

        String text = getFeedbackText();
        if (text != null && !text.isEmpty() && !text.equals("<Empty>")) {
            sb.append(cleanHtml(text));
        }

        String buttons = getVisibleButtons();
        if (!buttons.isEmpty()) {
            sb.append(". ").append(buttons);
        }

        if (sb.length() == 0) {
            speak("No current prompt.");
        } else {
            speak(sb.toString());
        }
    }

    private void readPlayerInfo() {
        Object gameView = getGameView();
        if (gameView == null) {
            speak("Game data not available.");
            return;
        }
        try {
            StringBuilder sb = new StringBuilder();

            int turn = callInt(gameView, "getTurn");
            String phase = callString(gameView, "getPhase");
            String step = callString(gameView, "getStep");
            String activePlayer = callString(gameView, "getActivePlayerName");
            if (turn > 0) sb.append("Turn ").append(turn).append(". ");
            if (activePlayer != null) sb.append(activePlayer).append("'s turn. ");
            if (phase != null) sb.append(formatPhase(phase)).append(". ");
            if (step != null) sb.append(formatPhase(step)).append(". ");

            Object playersList = callMethod(gameView, "getPlayers");
            if (playersList instanceof List) {
                for (Object player : (List<?>) playersList) {
                    String name = callString(player, "getName");
                    int life = callInt(player, "getLife");
                    int handCount = callInt(player, "getHandCount");
                    int libraryCount = callInt(player, "getLibraryCount");
                    Object graveyard = callMethod(player, "getGraveyard");
                    int graveyardCount = graveyard instanceof Map ? ((Map<?, ?>) graveyard).size() : 0;

                    sb.append(name).append(": ").append(life).append(" life");

                    Object counters = callMethod(player, "getCounters");
                    if (counters instanceof List) {
                        for (Object counter : (List<?>) counters) {
                            String cName = callString(counter, "getName");
                            int cCount = callInt(counter, "getCount");
                            if ("poison".equalsIgnoreCase(cName) && cCount > 0) {
                                sb.append(", ").append(cCount).append(" poison");
                            }
                        }
                    }

                    sb.append(", ").append(handCount).append(" in hand");
                    sb.append(", ").append(libraryCount).append(" in library");
                    if (graveyardCount > 0) sb.append(", ").append(graveyardCount).append(" in graveyard");
                    sb.append(". ");
                }
            }
            speak(sb.toString());
        } catch (Exception e) {
            speak("Could not read player info.");
        }
    }

    private void readHand() {
        Object gameView = getGameView();
        if (gameView == null) { speak("Game data not available."); return; }
        Object hand = callMethod(gameView, "getMyHand");
        if (!(hand instanceof Map)) { speak("No hand data."); return; }

        Map<?, ?> handMap = (Map<?, ?>) hand;
        if (handMap.isEmpty()) { speak("Your hand is empty."); return; }

        StringBuilder sb = new StringBuilder(handMap.size() + " cards in hand. ");
        int i = 1;
        for (Object cardView : handMap.values()) {
            String name = callString(cardView, "getName");
            String manaCost = callString(cardView, "getManaCostStr");
            if (name != null) {
                sb.append(i).append(": ").append(name);
                if (manaCost != null && !manaCost.isEmpty()) {
                    sb.append(", ").append(formatManaCost(manaCost));
                }
                sb.append(". ");
                i++;
            }
        }
        speak(sb.toString());
    }

    private void readBattlefield() {
        Object gameView = getGameView();
        if (gameView == null) { speak("Game data not available."); return; }

        Object playersList = callMethod(gameView, "getPlayers");
        if (!(playersList instanceof List)) { speak("No player data."); return; }

        StringBuilder sb = new StringBuilder("Battlefield. ");
        for (Object player : (List<?>) playersList) {
            String name = callString(player, "getName");
            Object battlefield = callMethod(player, "getBattlefield");
            if (battlefield instanceof Map) {
                Map<?, ?> permanents = (Map<?, ?>) battlefield;
                sb.append(name).append(": ");
                if (permanents.isEmpty()) { sb.append("empty. "); continue; }
                sb.append(permanents.size()).append(" permanents. ");

                List<String> creatures = new ArrayList<>();
                List<String> lands = new ArrayList<>();
                List<String> others = new ArrayList<>();

                // Build UUID->name map for attachment lookups
                Map<Object, String> permNames = new HashMap<>();
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) battlefield).entrySet()) {
                    String pName = callString(entry.getValue(), "getName");
                    if (pName != null) permNames.put(entry.getKey(), pName);
                }

                for (Object perm : permanents.values()) {
                    String permName = callString(perm, "getName");
                    if (permName == null) continue;
                    // Skip auras/equipment that are attached to something
                    if (callBool(perm, "isAttachedTo")) continue;

                    boolean isCreature = callBool(perm, "isCreature");
                    boolean isLand = callBool(perm, "isLand");
                    boolean isTapped = callBool(perm, "isTapped");
                    String desc = permName;
                    if (isCreature) {
                        String power = callString(perm, "getPower");
                        String toughness = callString(perm, "getToughness");
                        if (power != null && toughness != null) desc += " " + power + "/" + toughness;
                    }
                    if (isTapped) desc += " tapped";

                    // List attachments inline
                    Object attachIds = callMethod(perm, "getAttachments");
                    if (attachIds instanceof List && !((List<?>) attachIds).isEmpty()) {
                        StringBuilder attachSb = new StringBuilder(" (");
                        int ac = 0;
                        for (Object aid : (List<?>) attachIds) {
                            String aName = permNames.get(aid);
                            if (aName != null) {
                                if (ac > 0) attachSb.append(", ");
                                attachSb.append(aName);
                                ac++;
                            }
                        }
                        if (ac > 0) {
                            attachSb.append(")");
                            desc += attachSb.toString();
                        }
                    }

                    if (isCreature) creatures.add(desc);
                    else if (isLand) lands.add(desc);
                    else others.add(desc);
                }

                if (!creatures.isEmpty()) {
                    sb.append("Creatures: ");
                    for (String c : creatures) sb.append(c).append(", ");
                }
                if (!lands.isEmpty()) sb.append("Lands: ").append(lands.size()).append(". ");
                if (!others.isEmpty()) {
                    sb.append("Other: ");
                    for (String o : others) sb.append(o).append(", ");
                }
            }
        }
        speak(sb.toString());
    }

    private void readStack() {
        Object gameView = getGameView();
        if (gameView == null) { speak("Game data not available."); return; }
        Object stack = callMethod(gameView, "getStack");
        if (!(stack instanceof Map)) { speak("No stack data."); return; }
        Map<?, ?> stackMap = (Map<?, ?>) stack;
        if (stackMap.isEmpty()) { speak("Stack is empty."); return; }
        StringBuilder sb = new StringBuilder(stackMap.size() + " on the stack. ");
        int idx = 1;
        for (Object cardView : stackMap.values()) {
            String name = callString(cardView, "getName");
            if (name == null) continue;
            sb.append(idx++).append(": ").append(name);
            String manaCost = callString(cardView, "getManaCostStr");
            if (manaCost != null && !manaCost.isEmpty()) {
                sb.append(", ").append(formatManaCost(manaCost));
            }
            sb.append(". ");
        }
        speak(sb.toString());
    }

    // ========== TARGET AND ABILITY SELECTION ==========

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

    /**
     * Gets ability choices from the ability picker if visible.
     * Returns list of [UUID id, String text] pairs.
     */
    private List<Object[]> getAbilityChoices() {
        List<Object[]> choices = new ArrayList<>();
        if (abilityPicker == null) return choices;
        if (!(abilityPicker instanceof Component) || !((Component) abilityPicker).isVisible()) return choices;

        try {
            Object choicesList = findFieldDeep(abilityPicker, "choices");
            if (!(choicesList instanceof List)) return choices;

            for (Object action : (List<?>) choicesList) {
                Object id = findFieldDeep(action, "id");
                String text = action.toString();
                // Strip HTML from choice text
                text = cleanHtml(text);
                choices.add(new Object[]{id, text});
            }
        } catch (Exception e) {
            // Ignore
        }
        return choices;
    }

    private void announceAbilityChoices() {
        List<Object[]> choices = getAbilityChoices();
        if (choices.isEmpty()) return;
        StringBuilder sb = new StringBuilder("Choose ability. ");
        for (int i = 0; i < choices.size(); i++) {
            sb.append("Ctrl+Shift+").append(i + 1).append(": ").append(choices.get(i)[1]).append(". ");
        }
        speak(sb.toString());
    }

    private void readTargets() {
        // Priority 1: ability picker
        List<Object[]> abilities = getAbilityChoices();
        if (!abilities.isEmpty()) {
            StringBuilder sb = new StringBuilder(abilities.size() + " abilities. ");
            for (int i = 0; i < abilities.size(); i++) {
                sb.append("Ctrl+Shift+").append(i + 1).append(": ").append(abilities.get(i)[1]).append(". ");
            }
            speak(sb.toString());
            return;
        }

        // Priority 2: ShowCardsDialog targets
        List<Object> cardTargets = getVisibleTargets();
        if (!cardTargets.isEmpty()) {
            StringBuilder sb = new StringBuilder(cardTargets.size() + " targets. ");
            for (int i = 0; i < cardTargets.size(); i++) {
                String name = callString(cardTargets.get(i), "getName");
                if (name != null) sb.append("Ctrl+Shift+").append(i + 1).append(": ").append(name).append(". ");
            }
            speak(sb.toString());
            return;
        }

        // Priority 3: player targets
        Boolean needFeedback = findFieldTyped(helperPanel, "gameNeedFeedback", Boolean.class);
        if (needFeedback != null && needFeedback) {
            List<Object[]> players = getSelectablePlayers();
            if (!players.isEmpty()) {
                StringBuilder sb = new StringBuilder(players.size() + " players. ");
                for (int i = 0; i < players.size(); i++) {
                    sb.append("Ctrl+Shift+").append(i + 1).append(": ").append(players.get(i)[0]).append(". ");
                }
                speak(sb.toString());
                return;
            }
        }

        speak("No targets to choose from.");
    }

    /**
     * Unified selection: ability picker > card targets > player targets.
     */
    private void selectTargetOrAbility(int index) {
        // Priority 1: ability picker
        List<Object[]> abilities = getAbilityChoices();
        if (!abilities.isEmpty()) {
            if (index >= abilities.size()) {
                speak("Only " + abilities.size() + " abilities.");
                return;
            }
            Object id = abilities.get(index)[0];
            String text = (String) abilities.get(index)[1];
            speak("Selected " + text);
            if (id != null) {
                sendUUIDDirect(id);
            } else {
                // Cancel option (null id)
                try {
                    Object gameId = findFieldDeep(gamePanel, "gameId");
                    Class<?> sessionClass = Class.forName("mage.client.SessionHandler");
                    Method sendMethod = sessionClass.getMethod("sendPlayerBoolean", UUID.class, boolean.class);
                    sendMethod.invoke(null, gameId, false);
                } catch (Exception e) {
                    // Ignore
                }
            }
            // Hide the picker
            if (abilityPicker instanceof Component) {
                ((Component) abilityPicker).setVisible(false);
            }
            return;
        }

        // Priority 2: card targets
        List<Object> cardTargets = getVisibleTargets();
        if (!cardTargets.isEmpty()) {
            if (index >= cardTargets.size()) {
                speak("Only " + cardTargets.size() + " targets.");
                return;
            }
            Object cardView = cardTargets.get(index);
            String name = callString(cardView, "getName");
            speak("Selected " + (name != null ? name : "target " + (index + 1)) + ".");
            sendUUID(cardView);
            return;
        }

        // Priority 3: player targets
        List<Object[]> players = getSelectablePlayers();
        if (!players.isEmpty()) {
            if (index >= players.size()) {
                speak("Only " + players.size() + " players.");
                return;
            }
            speak("Selected " + players.get(index)[0] + ".");
            sendUUIDDirect(players.get(index)[1]);
            return;
        }

        speak("No targets available.");
    }

    // ========== BUTTON INTERACTION ==========

    private void clickButton(String visibleName, String linkName) {
        if (helperPanel == null) return;
        try {
            JButton visibleBtn = getButtonField(visibleName);
            if (visibleBtn != null && visibleBtn.isVisible()) {
                speak(visibleBtn.getText());
                visibleBtn.doClick();
                return;
            }

            Boolean needFeedback = findFieldTyped(helperPanel, "gameNeedFeedback", Boolean.class);
            if (needFeedback != null && needFeedback) {
                JButton linkBtn = getButtonField(linkName);
                if (linkBtn != null) {
                    String text = visibleBtn != null ? visibleBtn.getText() : linkName.replace("link", "");
                    speak(text);
                    linkBtn.doClick();
                    return;
                }
            }

            speak(visibleName.replace("btn", "") + " not available.");
        } catch (Exception e) {
            // Ignore
        }
    }

    private JButton getButtonField(String name) {
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
            System.err.println("[XMage Access] Error sending UUID: " + e.getMessage());
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
            System.err.println("[XMage Access] Error sending UUID: " + e.getMessage());
        }
    }

    // ========== FORMATTING HELPERS ==========

    private String formatCardBrief(Object cardView, int position, int total) {
        String name = callString(cardView, "getName");
        String manaCost = callString(cardView, "getManaCostStr");
        String types = callString(cardView, "getTypeText");

        StringBuilder sb = new StringBuilder();
        sb.append("Card ").append(position).append(" of ").append(total).append(": ");
        sb.append(name != null ? name : "Unknown");
        if (manaCost != null && !manaCost.isEmpty()) {
            sb.append(", ").append(formatManaCost(manaCost));
        }
        if (types != null && !types.isEmpty()) {
            sb.append(". ").append(types);
        }
        return sb.toString();
    }

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

        // Rules text
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

    private String formatPermanentBrief(Object perm, int position, int total) {
        String name = callString(perm, "getName");
        boolean isCreature = callBool(perm, "isCreature");
        boolean isTapped = callBool(perm, "isTapped");

        StringBuilder sb = new StringBuilder();
        sb.append("Permanent ").append(position).append(" of ").append(total).append(": ");
        sb.append(name != null ? name : "Unknown");
        if (isCreature) {
            String power = callString(perm, "getPower");
            String toughness = callString(perm, "getToughness");
            if (power != null && toughness != null) {
                sb.append(" ").append(power).append("/").append(toughness);
            }
        }
        if (isTapped) sb.append(", tapped");
        if (callBool(perm, "hasSummoningSickness")) sb.append(", summoning sickness");

        // Attachments (auras, equipment)
        String attachments = getAttachmentNames(perm);
        if (attachments != null) sb.append(", ").append(attachments);

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

        // Attachments
        String attachments = getAttachmentNames(perm);
        if (attachments != null) sb.append("Attached: ").append(attachments).append(". ");

        // Attached to
        if (callBool(perm, "isAttachedTo")) {
            String parentName = getAttachedToName(perm);
            if (parentName != null) sb.append("Attached to: ").append(parentName).append(". ");
        }

        // Counters
        Object counters = callMethod(perm, "getCounters");
        if (counters instanceof List && !((List<?>) counters).isEmpty()) {
            sb.append("Counters: ");
            for (Object counter : (List<?>) counters) {
                String cName = callString(counter, "getName");
                int cCount = callInt(counter, "getCount");
                if (cName != null) sb.append(cCount).append(" ").append(cName).append(", ");
            }
        }

        // Rules text
        Object rules = callMethod(perm, "getRules");
        if (rules instanceof List && !((List<?>) rules).isEmpty()) {
            sb.append("Rules: ");
            for (Object rule : (List<?>) rules) {
                sb.append(cleanHtml(rule.toString())).append(". ");
            }
        }
        return sb.toString();
    }

    /**
     * Gets names of all permanents attached to this permanent (auras, equipment).
     */
    private String getAttachmentNames(Object perm) {
        try {
            Object attachmentIds = callMethod(perm, "getAttachments");
            if (!(attachmentIds instanceof List)) return null;
            List<?> ids = (List<?>) attachmentIds;
            if (ids.isEmpty()) return null;

            // Build a UUID->name map from all players' battlefields
            Map<Object, String> uuidToName = getBattlefieldNameMap();
            if (uuidToName.isEmpty()) return null;

            StringBuilder sb = new StringBuilder();
            int count = 0;
            for (Object id : ids) {
                String attachName = uuidToName.get(id);
                if (attachName != null) {
                    if (count > 0) sb.append(", ");
                    sb.append(attachName);
                    count++;
                }
            }
            return count > 0 ? sb.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Gets the name of the permanent this one is attached to.
     */
    private String getAttachedToName(Object perm) {
        try {
            Object attachedToId = callMethod(perm, "getAttachedTo");
            if (attachedToId == null) return null;
            Map<Object, String> uuidToName = getBattlefieldNameMap();
            return uuidToName.get(attachedToId);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Builds a map of UUID -> card name for all permanents on all battlefields.
     */
    private Map<Object, String> getBattlefieldNameMap() {
        Map<Object, String> map = new HashMap<>();
        Object gameView = getGameView();
        if (gameView == null) return map;
        Object playersList = callMethod(gameView, "getPlayers");
        if (!(playersList instanceof List)) return map;
        for (Object player : (List<?>) playersList) {
            Object battlefield = callMethod(player, "getBattlefield");
            if (battlefield instanceof Map) {
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) battlefield).entrySet()) {
                    String name = callString(entry.getValue(), "getName");
                    if (name != null) {
                        map.put(entry.getKey(), name);
                    }
                }
            }
        }
        return map;
    }

    private Object getGameView() {
        if (lastGameData == null) return null;
        return findFieldDeep(lastGameData, "game");
    }

    private Object getMyPlayer(Object gameView) {
        // Try getMyPlayer first
        Object myPlayer = callMethod(gameView, "getMyPlayer");
        if (myPlayer != null) return myPlayer;

        // Fall back to first player with getControlled() == true
        Object playersList = callMethod(gameView, "getPlayers");
        if (playersList instanceof List) {
            for (Object player : (List<?>) playersList) {
                if (callBool(player, "getControlled")) return player;
            }
            // Fall back to first player
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
            // MageTextArea stores the original (non-HTML) text in currentText
            Object currentText = findFieldDeep(textArea, "currentText");
            if (currentText != null) {
                return currentText.toString();
            }
            // Fallback: try getText() on the editor pane
            Method getText = textArea.getClass().getMethod("getText");
            Object result = getText.invoke(textArea);
            return result != null ? result.toString() : null;
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }

    private String getVisibleButtons() {
        if (helperPanel == null) return "";
        StringBuilder sb = new StringBuilder();
        String[] buttons = {"btnLeft", "btnRight", "btnSpecial", "btnUndo"};
        String[] shortcuts = {"Ctrl+1", "Ctrl+2", "Ctrl+3", "Ctrl+Z"};
        for (int i = 0; i < buttons.length; i++) {
            try {
                Field field = helperPanel.getClass().getDeclaredField(buttons[i]);
                field.setAccessible(true);
                JButton btn = (JButton) field.get(helperPanel);
                if (btn != null && btn.isVisible()) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(shortcuts[i]).append(" ").append(btn.getText());
                }
            } catch (Exception e) {
                // Ignore
            }
        }
        return sb.toString();
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

    private String formatPhase(String phase) {
        if (phase == null) return "";
        return phase.replace("_", " ").toLowerCase();
    }

    private String formatManaCost(String manaCost) {
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

    private void speak(String text) {
        SpeechOutput speech = AccessibilityManager.getInstance().getSpeech();
        if (speech != null) {
            speech.speak(text);
        }
    }
}
