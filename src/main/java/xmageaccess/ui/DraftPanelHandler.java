package xmageaccess.ui;

import xmageaccess.AccessibilityManager;
import xmageaccess.speech.SpeechOutput;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Accessibility handler for the XMage Draft Panel.
 * Provides keyboard-driven draft navigation with speech output.
 *
 * Keyboard shortcuts:
 *   Ctrl+Up/Down     - Navigate booster cards
 *   Ctrl+Enter       - Pick the current card
 *   Ctrl+D           - Read detailed card info
 *   Ctrl+R           - Re-read draft status (pack, pick, time, card count)
 *   Ctrl+F1          - Read all booster cards
 *   Ctrl+F2          - Read picked cards summary
 *   Ctrl+T           - Read time remaining
 */
public class DraftPanelHandler {

    private final Component draftPanel;
    private Component draftBooster;      // DraftGrid
    private int cursorIndex = 0;
    private List<Object[]> boosterCards = new ArrayList<>(); // [MageCard, CardView]
    private Timer announceTimer;
    private int lastBoosterSize = -1;
    private KeyEventDispatcher keyDispatcher;

    public DraftPanelHandler(Component draftPanel) {
        this.draftPanel = draftPanel;
    }

    public void attach() {
        try {
            discoverComponents();
            addKeyboardShortcuts();
            startMonitoring();
            announceDraftStart();
        } catch (Exception e) {
            System.err.println("[XMage Access] Error attaching to DraftPanel: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void detach() {
        if (announceTimer != null) {
            announceTimer.stop();
        }
        if (keyDispatcher != null) {
            KeyboardFocusManager.getCurrentKeyboardFocusManager()
                    .removeKeyEventDispatcher(keyDispatcher);
            keyDispatcher = null;
        }
    }

    private void discoverComponents() {
        draftBooster = findFieldTyped(draftPanel, "draftBooster", Component.class);
        System.out.println("[XMage Access] Draft components - booster: " + (draftBooster != null));
    }

    private void announceDraftStart() {
        StringBuilder sb = new StringBuilder("Draft started. ");
        String status = readDraftStatus();
        if (!status.isEmpty()) sb.append(status).append(" ");

        refreshBoosterCards();
        if (!boosterCards.isEmpty()) {
            sb.append(boosterCards.size()).append(" cards to pick from. ");
        }

        sb.append("Ctrl+Up, Down to navigate cards. Ctrl+Enter to pick. ");
        sb.append("Ctrl+D for card detail. Ctrl+T for time.");
        speak(sb.toString());
    }

    private void startMonitoring() {
        // Poll for new boosters appearing
        announceTimer = new Timer(1000, e -> {
            if (!isPanelVisible()) return;
            try {
                refreshBoosterCards();
                int currentSize = boosterCards.size();
                if (currentSize != lastBoosterSize && currentSize > 0 && lastBoosterSize >= 0) {
                    // New booster arrived
                    cursorIndex = 0;
                    String status = readDraftStatus();
                    speak("New pack. " + status + " " + currentSize + " cards. "
                            + "First: " + getCardName(0) + ".");
                }
                lastBoosterSize = currentSize;
            } catch (Exception ex) {
                // Ignore
            }
        });
        announceTimer.setRepeats(true);
        announceTimer.start();
    }

    private void addKeyboardShortcuts() {
        keyDispatcher = e -> {
                    if (e.getID() != KeyEvent.KEY_PRESSED) return false;
                    if (!isPanelVisible()) return false;
                    if (!e.isControlDown()) return false;

                    if (!e.isShiftDown()) {
                        switch (e.getKeyCode()) {
                            case KeyEvent.VK_UP:
                                navigateBooster(-1);
                                return true;
                            case KeyEvent.VK_DOWN:
                                navigateBooster(1);
                                return true;
                            case KeyEvent.VK_ENTER:
                                pickCard();
                                return true;
                            case KeyEvent.VK_D:
                                readCardDetail();
                                return true;
                            case KeyEvent.VK_R:
                                readStatus();
                                return true;
                            case KeyEvent.VK_F1:
                                readAllBoosterCards();
                                return true;
                            case KeyEvent.VK_F2:
                                readPickedCards();
                                return true;
                            case KeyEvent.VK_T:
                                readTimeRemaining();
                                return true;
                        }
                    }
                    return false;
                };
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addKeyEventDispatcher(keyDispatcher);
    }

    // ========== BOOSTER NAVIGATION ==========

    private void navigateBooster(int direction) {
        refreshBoosterCards();
        if (boosterCards.isEmpty()) {
            speak("No cards in booster. Waiting for next pack.");
            return;
        }

        cursorIndex += direction;
        if (cursorIndex < 0) cursorIndex = boosterCards.size() - 1;
        if (cursorIndex >= boosterCards.size()) cursorIndex = 0;

        Object cardView = boosterCards.get(cursorIndex)[1];
        String name = callString(cardView, "getName");
        String manaCost = callString(cardView, "getManaCostStr");
        boolean isCreature = callBool(cardView, "isCreature");

        StringBuilder sb = new StringBuilder();
        sb.append(cursorIndex + 1).append(" of ").append(boosterCards.size()).append(": ");
        sb.append(name != null ? name : "Unknown");

        if (manaCost != null && !manaCost.isEmpty()) {
            sb.append(", ").append(formatManaCost(manaCost));
        }

        if (isCreature) {
            String power = callString(cardView, "getPower");
            String toughness = callString(cardView, "getToughness");
            if (power != null && toughness != null) {
                sb.append(", ").append(power).append("/").append(toughness);
            }
        }

        speak(sb.toString());
    }

    private void readCardDetail() {
        refreshBoosterCards();
        if (boosterCards.isEmpty() || cursorIndex >= boosterCards.size()) {
            speak("No card selected.");
            return;
        }

        Object cardView = boosterCards.get(cursorIndex)[1];
        speak(formatCardDetailed(cardView));
    }

    private void readAllBoosterCards() {
        refreshBoosterCards();
        if (boosterCards.isEmpty()) {
            speak("No cards in booster.");
            return;
        }

        StringBuilder sb = new StringBuilder(boosterCards.size() + " cards in booster. ");
        for (int i = 0; i < boosterCards.size(); i++) {
            Object cv = boosterCards.get(i)[1];
            String name = callString(cv, "getName");
            String manaCost = callString(cv, "getManaCostStr");
            sb.append(i + 1).append(": ").append(name != null ? name : "Unknown");
            if (manaCost != null && !manaCost.isEmpty()) {
                sb.append(", ").append(formatManaCost(manaCost));
            }
            sb.append(". ");
        }
        speak(sb.toString());
    }

    // ========== PICKING ==========

    @SuppressWarnings("unchecked")
    private void pickCard() {
        refreshBoosterCards();
        if (boosterCards.isEmpty()) {
            speak("No cards to pick.");
            return;
        }
        if (cursorIndex >= boosterCards.size()) cursorIndex = 0;

        // Check protection timer
        Object protectionTimer = findFieldDeep(draftPanel, "protectionTimer");
        if (protectionTimer instanceof Timer && ((Timer) protectionTimer).isRunning()) {
            speak("Please wait before picking.");
            return;
        }

        Object cardView = boosterCards.get(cursorIndex)[1];
        String name = callString(cardView, "getName");
        Object cardId = callMethod(cardView, "getId");
        UUID draftId = findFieldTyped(draftPanel, "draftId", UUID.class);

        if (cardId == null || draftId == null) {
            speak("Cannot pick card.");
            return;
        }

        speak("Picking: " + (name != null ? name : "Unknown"));

        try {
            // Call SessionHandler.sendCardPick(draftId, cardId, cardsHidden)
            Set<?> cardsHidden = findFieldTyped(draftPanel, "cardsHidden", Set.class);
            if (cardsHidden == null) cardsHidden = new java.util.HashSet<>();

            Class<?> sessionHandler = Class.forName("mage.client.SessionHandler");
            Method sendPick = sessionHandler.getMethod("sendCardPick", UUID.class, UUID.class, Set.class);
            Object result = sendPick.invoke(null, draftId, cardId, cardsHidden);

            if (result != null) {
                // Update the picked cards display
                Object picks = callMethod(result, "getPicks");
                if (picks != null) {
                    Method loadPicked = draftPanel.getClass().getDeclaredMethod("loadCardsToPickedCardsArea", picks.getClass());
                    loadPicked.setAccessible(true);
                    loadPicked.invoke(draftPanel, picks);
                }

                // Clear the booster
                if (draftBooster != null) {
                    // Load empty booster
                    try {
                        Object emptyView = findFieldDeep(draftPanel, "EMPTY_VIEW");
                        Object bigCard = findFieldDeep(draftPanel, "bigCard");
                        Method loadBooster = draftBooster.getClass().getMethod("loadBooster",
                                Class.forName("mage.view.CardsView"),
                                Class.forName("mage.client.cards.BigCard"));
                        loadBooster.invoke(draftBooster, emptyView, bigCard);
                    } catch (Exception ignored) {}
                }

                speak("Picked " + (name != null ? name : "card") + ". Waiting for other players.");
            } else {
                speak("Pick failed.");
            }
        } catch (Exception e) {
            System.err.println("[XMage Access] Draft pick error: " + e.getMessage());
            speak("Error picking card.");
        }
    }

    // ========== STATUS READING ==========

    private void readStatus() {
        StringBuilder sb = new StringBuilder("Draft status. ");
        sb.append(readDraftStatus());

        refreshBoosterCards();
        if (!boosterCards.isEmpty()) {
            sb.append(boosterCards.size()).append(" cards in booster. ");
            if (cursorIndex < boosterCards.size()) {
                sb.append("Current: ").append(getCardName(cursorIndex)).append(". ");
            }
        } else {
            sb.append("Waiting for next pack. ");
        }

        speak(sb.toString());
    }

    private String readDraftStatus() {
        StringBuilder sb = new StringBuilder();

        // Pack info
        String packLabel = readLabel("labelCardNumber");
        if (packLabel != null && !packLabel.isEmpty()) {
            sb.append(packLabel).append(". ");
        }

        // Which pack is active
        for (int i = 1; i <= 3; i++) {
            JCheckBox check = findFieldTyped(draftPanel, "checkPack" + i, JCheckBox.class);
            if (check != null && check.isSelected()) {
                JTextField packField = findFieldTyped(draftPanel, "editPack" + i, JTextField.class);
                if (packField != null) {
                    String packName = packField.getText();
                    if (packName != null && !packName.isEmpty()) {
                        sb.append("Pack ").append(i).append(": ").append(packName).append(". ");
                    }
                }
                break;
            }
        }

        // Time remaining
        String time = readTimeField();
        if (time != null) {
            sb.append("Time: ").append(time).append(". ");
        }

        return sb.toString();
    }

    private void readTimeRemaining() {
        String time = readTimeField();
        if (time != null) {
            speak("Time remaining: " + time);
        } else {
            speak("Timer not available.");
        }
    }

    private String readTimeField() {
        JTextField timeField = findFieldTyped(draftPanel, "editTimeRemaining", JTextField.class);
        if (timeField != null) {
            String text = timeField.getText();
            if (text != null && !text.isEmpty()) return text;
        }
        return null;
    }

    private void readPickedCards() {
        Object pickedCards = findFieldDeep(draftPanel, "pickedCards");
        if (pickedCards == null) {
            speak("No picked cards data.");
            return;
        }

        // SimpleCardsView is a Map<UUID, SimpleCardView>
        if (pickedCards instanceof java.util.Map) {
            java.util.Map<?, ?> cards = (java.util.Map<?, ?>) pickedCards;
            if (cards.isEmpty()) {
                speak("No cards picked yet.");
                return;
            }

            StringBuilder sb = new StringBuilder(cards.size() + " cards picked. ");
            int idx = 0;
            for (Object card : cards.values()) {
                String name = callString(card, "getName");
                if (name != null) {
                    sb.append(name).append(", ");
                    idx++;
                }
                if (idx >= 20) {
                    sb.append("and ").append(cards.size() - idx).append(" more. ");
                    break;
                }
            }
            speak(sb.toString());
        } else {
            speak("Cannot read picked cards.");
        }
    }

    // ========== BOOSTER CARDS ==========

    private void refreshBoosterCards() {
        boosterCards.clear();
        if (draftBooster == null) return;

        try {
            Component[] components = ((Container) draftBooster).getComponents();
            for (Component comp : components) {
                try {
                    Method getOriginal = comp.getClass().getMethod("getOriginal");
                    Object cardView = getOriginal.invoke(comp);
                    if (cardView != null) {
                        boosterCards.add(new Object[]{comp, cardView});
                    }
                } catch (NoSuchMethodException ignored) {
                    // Not a MageCard
                }
            }
        } catch (Exception e) {
            System.err.println("[XMage Access] Error refreshing booster: " + e.getMessage());
        }
    }

    private String getCardName(int index) {
        if (index < 0 || index >= boosterCards.size()) return "Unknown";
        return callString(boosterCards.get(index)[1], "getName");
    }

    // ========== CARD FORMATTING ==========

    private String formatCardDetailed(Object cardView) {
        StringBuilder sb = new StringBuilder();
        String name = callString(cardView, "getName");
        String manaCost = callString(cardView, "getManaCostStr");
        String types = callString(cardView, "getTypeText");
        boolean isCreature = callBool(cardView, "isCreature");
        String power = callString(cardView, "getPower");
        String toughness = callString(cardView, "getToughness");

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
        if (rules instanceof List && !((List<?>) rules).isEmpty()) {
            sb.append("Rules: ");
            for (Object rule : (List<?>) rules) {
                String ruleText = rule.toString();
                ruleText = ruleText.replaceAll("<[^>]*>", "").trim();
                if (!ruleText.isEmpty()) sb.append(ruleText).append(". ");
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

    // ========== VISIBILITY ==========

    private boolean isPanelVisible() {
        if (draftPanel == null || !draftPanel.isVisible()) return false;
        Component c = draftPanel;
        while (c != null) {
            if (!c.isVisible()) return false;
            c = c.getParent();
        }
        return true;
    }

    // ========== REFLECTION HELPERS ==========

    private String readLabel(String fieldName) {
        try {
            JLabel label = findFieldTyped(draftPanel, fieldName, JLabel.class);
            if (label != null) return label.getText();
        } catch (Exception ignored) {}
        return null;
    }

    private String callString(Object obj, String methodName) {
        try {
            Method m = obj.getClass().getMethod(methodName);
            Object result = m.invoke(obj);
            return result instanceof String ? (String) result : null;
        } catch (Exception e) {
            return null;
        }
    }

    private boolean callBool(Object obj, String methodName) {
        try {
            Method m = obj.getClass().getMethod(methodName);
            Object result = m.invoke(obj);
            return result instanceof Boolean && (Boolean) result;
        } catch (Exception e) {
            return false;
        }
    }

    private Object callMethod(Object obj, String methodName) {
        try {
            Method m = obj.getClass().getMethod(methodName);
            return m.invoke(obj);
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T findFieldTyped(Object obj, String name, Class<T> type) {
        try {
            Class<?> clazz = obj.getClass();
            while (clazz != null) {
                try {
                    Field field = clazz.getDeclaredField(name);
                    field.setAccessible(true);
                    Object val = field.get(obj);
                    if (type.isInstance(val)) return (T) val;
                    return null;
                } catch (NoSuchFieldException e) {
                    clazz = clazz.getSuperclass();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private Object findFieldDeep(Object obj, String name) {
        try {
            Class<?> clazz = obj.getClass();
            while (clazz != null) {
                try {
                    Field field = clazz.getDeclaredField(name);
                    field.setAccessible(true);
                    return field.get(obj);
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
