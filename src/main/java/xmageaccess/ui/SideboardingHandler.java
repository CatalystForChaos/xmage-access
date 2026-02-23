package xmageaccess.ui;

import xmageaccess.AccessibilityManager;
import xmageaccess.speech.SpeechOutput;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FocusTraversalPolicy;
import java.awt.KeyboardFocusManager;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Accessible sideboarding window for between-game deck modifications.
 * Simplified version of AccessibleDeckEditorWindow with only main deck
 * and sideboard zones - no search bar, no filters, no file operations.
 *
 * Shortcuts:
 *   Tab/Shift+Tab - cycle between Main Deck and Sideboard
 *   Up/Down       - navigate cards within a zone
 *   Enter         - move selected card to the other zone
 *   D             - read card detail
 *   Ctrl+R        - read deck/sideboard summary
 *   Ctrl+Enter    - submit deck (proceed to next game)
 *   Ctrl+F1       - read all shortcuts
 */
public class SideboardingHandler extends JFrame {

    private static final java.util.Set<SideboardingHandler> _activeWindows =
            java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<SideboardingHandler, Boolean>());

    /** Returns true if any SideboardingHandler window is currently showing. */
    public static boolean isAnyWindowVisible() {
        return !_activeWindows.isEmpty();
    }

    private final Component deckEditorPanel;

    private final ZoneListPanel mainDeckZone;
    private final ZoneListPanel sideboardZone;
    private final java.util.List<ZoneListPanel> allZones = new ArrayList<>();

    // Cached reflection references
    private Object deckArea;
    private Object deck;
    private Object deckList;
    private Object sideboardList;
    private JButton xmageBtnSubmit;

    private Timer pollTimer;

    private int _lastDeckCount = -1;
    private int _lastSideboardCount = -1;

    public SideboardingHandler(Component deckEditorPanel) {
        super("XMage Sideboarding");
        this.deckEditorPanel = deckEditorPanel;

        mainDeckZone = new ZoneListPanel("Main Deck");
        sideboardZone = new ZoneListPanel("Sideboard");

        allZones.add(mainDeckZone);
        allZones.add(sideboardZone);

        buildUI();
        discoverComponents();
        bindKeys();
        startPolling();
    }

    private void buildUI() {
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        setSize(600, 700);
        setLocationRelativeTo(null);

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));

        for (ZoneListPanel zone : allZones) {
            zone.setPreferredSize(new Dimension(580, 300));
            zone.setMaximumSize(new Dimension(Integer.MAX_VALUE, 400));
            mainPanel.add(zone);
        }

        JScrollPane scrollPane = new JScrollPane(mainPanel);
        add(scrollPane, BorderLayout.CENTER);

        // Focus traversal: main deck -> sideboard -> main deck
        setFocusCycleRoot(true);
        final java.util.List<Component> focusOrder = new ArrayList<>();
        focusOrder.add(mainDeckZone.getList());
        focusOrder.add(sideboardZone.getList());

        setFocusTraversalPolicy(new FocusTraversalPolicy() {
            @Override
            public Component getComponentAfter(Container container, Component component) {
                int idx = focusOrder.indexOf(component);
                if (idx < 0) return focusOrder.get(0);
                return focusOrder.get((idx + 1) % focusOrder.size());
            }

            @Override
            public Component getComponentBefore(Container container, Component component) {
                int idx = focusOrder.indexOf(component);
                if (idx < 0) return focusOrder.get(focusOrder.size() - 1);
                return focusOrder.get((idx - 1 + focusOrder.size()) % focusOrder.size());
            }

            @Override
            public Component getFirstComponent(Container container) {
                return focusOrder.get(0);
            }

            @Override
            public Component getLastComponent(Container container) {
                return focusOrder.get(focusOrder.size() - 1);
            }

            @Override
            public Component getDefaultComponent(Container container) {
                return focusOrder.get(0);
            }
        });
    }

    private void discoverComponents() {
        deckArea = findFieldDeep(deckEditorPanel, "deckArea");
        deck = findFieldDeep(deckEditorPanel, "deck");
        xmageBtnSubmit = findFieldTyped(deckEditorPanel, "btnSubmit", JButton.class);

        if (deckArea != null) {
            deckList = findFieldDeep(deckArea, "deckList");
            sideboardList = findFieldDeep(deckArea, "sideboardList");
        }

        System.out.println("[XMage Access] Sideboarding - deckArea: " + (deckArea != null)
                + ", deck: " + (deck != null)
                + ", deckList: " + (deckList != null)
                + ", sideboardList: " + (sideboardList != null)
                + ", btnSubmit: " + (xmageBtnSubmit != null));
    }

    private void bindKeys() {
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

        InputMap windowInput = getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap windowAction = getRootPane().getActionMap();

        // Ctrl+Enter = submit deck
        windowInput.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.CTRL_DOWN_MASK), "submitDeck");
        windowAction.put("submitDeck", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                submitDeck();
            }
        });

        // Ctrl+R = read summary
        windowInput.put(KeyStroke.getKeyStroke(KeyEvent.VK_R, KeyEvent.CTRL_DOWN_MASK), "readSummary");
        windowAction.put("readSummary", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                readSummary();
            }
        });

        // Ctrl+F1 = help
        windowInput.put(KeyStroke.getKeyStroke(KeyEvent.VK_F1, KeyEvent.CTRL_DOWN_MASK), "readHelp");
        windowAction.put("readHelp", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                readHelp();
            }
        });

        // Escape = return focus to XMage
        windowInput.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "returnFocus");
        windowAction.put("returnFocus", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                returnFocusToXMage();
            }
        });
    }

    private void startPolling() {
        pollTimer = new Timer(2000, e -> {
            try {
                if (!deckEditorPanel.isVisible()) {
                    stopPolling();
                    dispose();
                    speak("Sideboarding closed.");
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
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (visible) {
            _activeWindows.add(this);
        } else {
            _activeWindows.remove(this);
        }
    }

    @Override
    public void dispose() {
        _activeWindows.remove(this);
        super.dispose();
    }

    /** Announces the welcome message and initial deck counts. */
    public void announceWelcome() {
        refreshReferences();
        _lastDeckCount = -1;
        _lastSideboardCount = -1;
        refreshAllZones();

        int deckCount = getDeckCount();
        int sbCount = getSideboardCount();

        speak("Sideboarding. " + deckCount + " cards in main deck, "
                + sbCount + " in sideboard. "
                + "Tab between zones, Enter to move cards, Ctrl+Enter to submit. "
                + "Ctrl+F1 for all shortcuts.");

        // Focus the main deck zone
        SwingUtilities.invokeLater(() -> {
            mainDeckZone.getList().requestFocusInWindow();
        });
    }

    // ========== REFRESH ==========

    private void refreshReferences() {
        deck = findFieldDeep(deckEditorPanel, "deck");
        if (deckArea != null) {
            deckList = findFieldDeep(deckArea, "deckList");
            sideboardList = findFieldDeep(deckArea, "sideboardList");
        }
        xmageBtnSubmit = findFieldTyped(deckEditorPanel, "btnSubmit", JButton.class);
    }

    private void refreshAllZones() {
        try {
            refreshMainDeckZone();
        } catch (Exception e) {
            // Avoid ConcurrentModificationException
        }
        try {
            refreshSideboardZone();
        } catch (Exception e) {
            // Avoid ConcurrentModificationException
        }
    }

    private void refreshMainDeckZone() {
        int count = 0;
        if (deckList != null) {
            java.util.List<?> allCards = findFieldTyped(deckList, "allCards", java.util.List.class);
            count = allCards != null ? allCards.size() : 0;

            if (count == _lastDeckCount) return;
            _lastDeckCount = count;

            if (allCards != null) {
                mainDeckZone.updateItems(buildGroupedCardList(allCards, ZoneItem.ActionType.MOVE_TO_SIDEBOARD));
                return;
            }
        }
        if (_lastDeckCount != 0) {
            _lastDeckCount = 0;
            mainDeckZone.updateItems(new ArrayList<ZoneItem>());
        }
    }

    private void refreshSideboardZone() {
        int count = 0;
        if (sideboardList != null) {
            java.util.List<?> allCards = findFieldTyped(sideboardList, "allCards", java.util.List.class);
            count = allCards != null ? allCards.size() : 0;

            if (count == _lastSideboardCount) return;
            _lastSideboardCount = count;

            if (allCards != null) {
                sideboardZone.updateItems(buildGroupedCardList(allCards, ZoneItem.ActionType.MOVE_TO_DECK));
                return;
            }
        }
        if (_lastSideboardCount != 0) {
            _lastSideboardCount = 0;
            sideboardZone.updateItems(new ArrayList<ZoneItem>());
        }
    }

    private java.util.List<ZoneItem> buildGroupedCardList(java.util.List<?> allCards, ZoneItem.ActionType actionType) {
        java.util.List<?> cardsCopy = new ArrayList<Object>(allCards);

        Map<String, java.util.List<Object>> grouped = new LinkedHashMap<>();
        for (Object cardView : cardsCopy) {
            String name = callString(cardView, "getName");
            if (name == null) name = "Unknown";
            if (!grouped.containsKey(name)) {
                grouped.put(name, new ArrayList<Object>());
            }
            grouped.get(name).add(cardView);
        }

        java.util.List<ZoneItem> creatures = new ArrayList<>();
        java.util.List<ZoneItem> spells = new ArrayList<>();
        java.util.List<ZoneItem> lands = new ArrayList<>();
        int creatureCount = 0, spellCount = 0, landCount = 0;

        for (Map.Entry<String, java.util.List<Object>> entry : grouped.entrySet()) {
            java.util.List<Object> cards = entry.getValue();
            Object firstCard = cards.get(0);
            int count = cards.size();

            String name = entry.getKey();
            String manaCost = callString(firstCard, "getManaCostStr");

            StringBuilder display = new StringBuilder();
            if (count > 1) display.append(count).append("x ");
            display.append(name);
            if (manaCost != null && !manaCost.isEmpty()) {
                display.append(", ").append(formatManaCost(manaCost));
            }

            ZoneItem item = new ZoneItem(display.toString(), null, firstCard, actionType);

            if (callBool(firstCard, "isCreature")) {
                creatures.add(item);
                creatureCount += count;
            } else if (callBool(firstCard, "isLand")) {
                lands.add(item);
                landCount += count;
            } else {
                spells.add(item);
                spellCount += count;
            }
        }

        java.util.List<ZoneItem> result = new ArrayList<>();
        if (!creatures.isEmpty()) {
            result.add(new ZoneItem("--- Creatures (" + creatureCount + ") ---",
                    creatureCount + " creatures", null, ZoneItem.ActionType.NONE));
            result.addAll(creatures);
        }
        if (!spells.isEmpty()) {
            result.add(new ZoneItem("--- Spells (" + spellCount + ") ---",
                    spellCount + " spells", null, ZoneItem.ActionType.NONE));
            result.addAll(spells);
        }
        if (!lands.isEmpty()) {
            result.add(new ZoneItem("--- Lands (" + landCount + ") ---",
                    landCount + " lands", null, ZoneItem.ActionType.NONE));
            result.addAll(lands);
        }

        return result;
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
            case MOVE_TO_SIDEBOARD:
                moveCardToSideboard(item);
                break;
            case MOVE_TO_DECK:
                moveCardToDeck(item);
                break;
            case NONE:
                speak("No action for this item.");
                break;
            default:
                break;
        }
    }

    private void moveCardToSideboard(ZoneItem item) {
        Object cardView = item.getSourceObject();
        if (cardView == null || deck == null) {
            speak("Cannot move card.");
            return;
        }

        try {
            Object cardId = callMethod(cardView, "getId");
            if (cardId == null) {
                speak("Cannot identify card.");
                return;
            }

            Method findCard = deck.getClass().getMethod("findCard", UUID.class);
            Object card = findCard.invoke(deck, cardId);

            if (card == null) {
                card = findCardByName(callMethod(deck, "getCards"), callString(cardView, "getName"));
            }

            if (card == null) {
                speak("Card not found in deck.");
                return;
            }

            Method getCards = deck.getClass().getMethod("getCards");
            Set<?> cards = (Set<?>) getCards.invoke(deck);
            cards.remove(card);

            Method getSideboard = deck.getClass().getMethod("getSideboard");
            Set<?> sideboard = (Set<?>) getSideboard.invoke(deck);
            ((Set) sideboard).add(card);

            String name = callString(cardView, "getName");
            speak("Moved " + (name != null ? name : "card") + " to sideboard.");

            callRefreshDeck();
            scheduleRefresh();
        } catch (Exception e) {
            speak("Could not move card.");
            System.err.println("[XMage Access] Error moving card to sideboard: " + e.getMessage());
        }
    }

    private void moveCardToDeck(ZoneItem item) {
        Object cardView = item.getSourceObject();
        if (cardView == null || deck == null) {
            speak("Cannot move card.");
            return;
        }

        try {
            Object cardId = callMethod(cardView, "getId");
            if (cardId == null) {
                speak("Cannot identify card.");
                return;
            }

            Method findSideboardCard = deck.getClass().getMethod("findSideboardCard", UUID.class);
            Object card = findSideboardCard.invoke(deck, cardId);

            if (card == null) {
                card = findCardByName(callMethod(deck, "getSideboard"), callString(cardView, "getName"));
            }

            if (card == null) {
                speak("Card not found in sideboard.");
                return;
            }

            Method getSideboard = deck.getClass().getMethod("getSideboard");
            Set<?> sideboard = (Set<?>) getSideboard.invoke(deck);
            sideboard.remove(card);

            Method getCards = deck.getClass().getMethod("getCards");
            Set<?> cards = (Set<?>) getCards.invoke(deck);
            ((Set) cards).add(card);

            String name = callString(cardView, "getName");
            speak("Moved " + (name != null ? name : "card") + " to deck.");

            callRefreshDeck();
            scheduleRefresh();
        } catch (Exception e) {
            speak("Could not move card.");
            System.err.println("[XMage Access] Error moving card to deck: " + e.getMessage());
        }
    }

    private Object findCardByName(Object cardSet, String targetName) {
        if (cardSet == null || targetName == null) return null;
        if (cardSet instanceof Set) {
            for (Object card : (Set<?>) cardSet) {
                String name = callString(card, "getName");
                if (targetName.equals(name)) return card;
            }
        }
        return null;
    }

    private void callRefreshDeck() {
        try {
            Method refreshDeck = deckEditorPanel.getClass().getDeclaredMethod("refreshDeck");
            refreshDeck.setAccessible(true);
            refreshDeck.invoke(deckEditorPanel);
        } catch (Exception e) {
            System.err.println("[XMage Access] Error refreshing deck: " + e.getMessage());
        }
    }

    private void scheduleRefresh() {
        Timer refreshTimer = new Timer(500, e -> {
            refreshReferences();
            _lastDeckCount = -1;
            _lastSideboardCount = -1;
            refreshAllZones();
        });
        refreshTimer.setRepeats(false);
        refreshTimer.start();
    }

    // ========== SUBMIT ==========

    private void submitDeck() {
        if (xmageBtnSubmit == null || !xmageBtnSubmit.isVisible() || !xmageBtnSubmit.isEnabled()) {
            speak("Submit is not available.");
            return;
        }
        speak("Submitting deck.");
        SwingUtilities.invokeLater(() -> xmageBtnSubmit.doClick());
    }

    // ========== SUMMARY AND HELP ==========

    private void readSummary() {
        refreshReferences();
        int deckCount = getDeckCount();
        int sbCount = getSideboardCount();
        speak("Main deck: " + deckCount + " cards. Sideboard: " + sbCount + " cards.");
    }

    private void readHelp() {
        speak("Sideboarding shortcuts. "
                + "Tab or Shift+Tab to cycle between main deck and sideboard. "
                + "Up and down arrows to navigate cards. "
                + "Enter to move selected card to the other zone. "
                + "D to read card detail. "
                + "Ctrl+R to read deck and sideboard summary. "
                + "Ctrl+Enter to submit deck and proceed to next game. "
                + "Ctrl+F1 to hear these shortcuts.");
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
            return;
        }

        if (item.getSourceObject() != null
                && (item.getActionType() == ZoneItem.ActionType.MOVE_TO_SIDEBOARD
                    || item.getActionType() == ZoneItem.ActionType.MOVE_TO_DECK)) {
            speak(formatCardDetailed(item.getSourceObject()));
            return;
        }

        speak(item.getDisplayName());
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
        for (java.awt.Window w : java.awt.Window.getWindows()) {
            if (w != this && w.isVisible() && w instanceof JFrame) {
                w.toFront();
                w.requestFocus();
                speak("Returned to XMage.");
                return;
            }
        }
    }

    // ========== HELPERS ==========

    private int getDeckCount() {
        if (deckList == null) return 0;
        java.util.List<?> allCards = findFieldTyped(deckList, "allCards", java.util.List.class);
        return allCards != null ? allCards.size() : 0;
    }

    private int getSideboardCount() {
        if (sideboardList == null) return 0;
        java.util.List<?> allCards = findFieldTyped(sideboardList, "allCards", java.util.List.class);
        return allCards != null ? allCards.size() : 0;
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

        Object rules = callMethod(cardView, "getRules");
        if (rules instanceof java.util.List) {
            java.util.List<?> rulesList = (java.util.List<?>) rules;
            if (!rulesList.isEmpty()) {
                sb.append("Rules: ");
                for (Object rule : rulesList) {
                    sb.append(cleanHtml(rule.toString())).append(". ");
                }
            }
        }
        return sb.toString();
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
