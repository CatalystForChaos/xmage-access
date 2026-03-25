package xmageaccess.ui;

import xmageaccess.AccessibilityManager;
import xmageaccess.speech.SpeechOutput;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.UUID;

/**
 * Accessibility handler for XMage's ShowCardsDialog.
 * This dialog appears when cards are revealed, scried, looked at,
 * or when the player needs to pick a target from a card list.
 *
 * Keyboard shortcuts:
 *   Ctrl+Up/Down     - Navigate through cards
 *   Ctrl+Enter       - Select/target the current card (when targeting)
 *   Ctrl+R           - Re-read all cards
 *   Ctrl+D           - Read detailed info for current card
 *   Escape           - Close dialog (if not required)
 */
public class ShowCardsDialogHandler {

    private final Component dialog;
    private int cursor = 0;
    private KeyEventDispatcher keyDispatcher;

    public ShowCardsDialogHandler(Component dialog) {
        this.dialog = dialog;
    }

    public void attach() {
        try {
            addKeyboardShortcuts();
            announceCards();
        } catch (Exception e) {
            System.err.println("[XMage Access] Error attaching ShowCardsDialog: " + e.getMessage());
        }
    }

    public void detach() {
        if (keyDispatcher != null) {
            KeyboardFocusManager.getCurrentKeyboardFocusManager()
                    .removeKeyEventDispatcher(keyDispatcher);
            keyDispatcher = null;
        }
    }

    private void announceCards() {
        String title = getTitle();
        java.util.List<CardInfo> cards = getCards();

        StringBuilder sb = new StringBuilder();
        if (title != null && !title.isEmpty()) {
            sb.append(title).append(". ");
        }

        if (cards.isEmpty()) {
            sb.append("No cards.");
        } else {
            sb.append(cards.size()).append(cards.size() == 1 ? " card" : " cards").append(". ");
            for (int i = 0; i < cards.size(); i++) {
                sb.append(cards.get(i).brief);
                if (i < cards.size() - 1) sb.append(", ");
            }
            sb.append(". ");
            if (cards.size() > 1) {
                sb.append("Ctrl+Up, Down to navigate. ");
            }
            if (isTargeting()) {
                sb.append("Ctrl+Enter to select.");
            }
        }

        speak(sb.toString());
    }

    private void addKeyboardShortcuts() {
        keyDispatcher = e -> {
                    if (e.getID() != KeyEvent.KEY_PRESSED) return false;
                    if (!isDialogVisible()) return false;
                    if (!e.isControlDown()) return false;
                    if (e.isShiftDown()) return false;

                    switch (e.getKeyCode()) {
                        case KeyEvent.VK_UP:
                            navigateCards(-1);
                            return true;
                        case KeyEvent.VK_DOWN:
                            navigateCards(1);
                            return true;
                        case KeyEvent.VK_ENTER:
                            selectCard();
                            return true;
                        case KeyEvent.VK_R:
                            announceCards();
                            return true;
                        case KeyEvent.VK_D:
                            readCardDetail();
                            return true;
                    }
                    return false;
                };
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addKeyEventDispatcher(keyDispatcher);
    }

    private void navigateCards(int direction) {
        java.util.List<CardInfo> cards = getCards();
        if (cards.isEmpty()) {
            speak("No cards.");
            return;
        }

        cursor += direction;
        if (cursor < 0) cursor = cards.size() - 1;
        if (cursor >= cards.size()) cursor = 0;

        CardInfo card = cards.get(cursor);
        String choosable = card.choosable ? " (targetable)" : "";
        String selected = card.selected ? " (selected)" : "";
        speak((cursor + 1) + " of " + cards.size() + ": " + card.brief + choosable + selected);
    }

    private void selectCard() {
        java.util.List<CardInfo> cards = getCards();
        if (cards.isEmpty()) {
            speak("No cards to select.");
            return;
        }
        if (cursor >= cards.size()) cursor = 0;

        CardInfo card = cards.get(cursor);

        // Use DefaultActionCallback or SessionHandler.sendPlayerUUID
        try {
            UUID gameId = getGameId();
            if (gameId != null) {
                Class<?> sessionHandler = Class.forName("mage.client.SessionHandler");
                Method sendUUID = sessionHandler.getMethod("sendPlayerUUID", UUID.class, UUID.class);

                // If it's an ability, send ability ID; otherwise send card ID
                if (card.isAbility && card.abilityId != null) {
                    sendUUID.invoke(null, gameId, card.abilityId);
                } else {
                    sendUUID.invoke(null, gameId, card.id);
                }
                speak("Selected " + card.name + ".");
            } else {
                speak("Cannot select: no game ID.");
            }
        } catch (Exception e) {
            speak("Could not select card.");
            System.err.println("[XMage Access] Error selecting card: " + e.getMessage());
        }
    }

    private void readCardDetail() {
        java.util.List<CardInfo> cards = getCards();
        if (cards.isEmpty()) {
            speak("No cards.");
            return;
        }
        if (cursor >= cards.size()) cursor = 0;

        CardInfo card = cards.get(cursor);
        speak(card.detailed);
    }

    // ========== Card extraction ==========

    private java.util.List<CardInfo> getCards() {
        java.util.List<CardInfo> result = new ArrayList<>();
        try {
            Object cardAreaPanel = getFieldValue(dialog.getClass(), dialog, "cardArea");
            if (cardAreaPanel == null) return result;

            // CardArea has a JLayeredPane field also called cardArea
            Object layeredPane = getFieldValue(cardAreaPanel.getClass(), cardAreaPanel, "cardArea");
            if (!(layeredPane instanceof JLayeredPane)) return result;

            JLayeredPane pane = (JLayeredPane) layeredPane;
            for (Component comp : pane.getComponents()) {
                String compClass = comp.getClass().getName();
                if (compClass.contains("MageCard") || compClass.contains("CardPanel")) {
                    CardInfo info = extractCardInfo(comp);
                    if (info != null) result.add(info);
                }
            }
        } catch (Exception e) {
            System.err.println("[XMage Access] Error reading cards: " + e.getMessage());
        }
        return result;
    }

    private CardInfo extractCardInfo(Component mageCard) {
        try {
            // MageCard.getOriginal() returns CardView
            Method getOriginal = mageCard.getClass().getMethod("getOriginal");
            Object cardView = getOriginal.invoke(mageCard);
            if (cardView == null) return null;

            CardInfo info = new CardInfo();

            // Basic info
            info.name = callStringMethod(cardView, "getName");
            info.id = callUUIDMethod(cardView, "getId");

            String manaCost = callStringMethod(cardView, "getManaCostStr");
            String types = callStringMethod(cardView, "getTypeText");
            String power = callStringMethod(cardView, "getPower");
            String toughness = callStringMethod(cardView, "getToughness");
            int loyalty = callIntMethod(cardView, "getLoyalty");

            // Check if ability
            try {
                Method isAbilityMethod = cardView.getClass().getMethod("isAbility");
                info.isAbility = (Boolean) isAbilityMethod.invoke(cardView);
                if (info.isAbility) {
                    Method getAbility = cardView.getClass().getMethod("getAbility");
                    Object ability = getAbility.invoke(cardView);
                    if (ability != null) {
                        info.abilityId = callUUIDMethod(ability, "getId");
                    }
                }
            } catch (Exception ignored) {}

            // Choosable/selected state
            try {
                Method isChoosable = cardView.getClass().getMethod("isChoosable");
                info.choosable = (Boolean) isChoosable.invoke(cardView);
            } catch (Exception ignored) {}
            try {
                Method isSelected = cardView.getClass().getMethod("isSelected");
                info.selected = (Boolean) isSelected.invoke(cardView);
            } catch (Exception ignored) {}

            // Rules text
            java.util.List<String> rules = null;
            try {
                Method getRules = cardView.getClass().getMethod("getRules");
                rules = (java.util.List<String>) getRules.invoke(cardView);
            } catch (Exception ignored) {}

            // Build brief description
            StringBuilder brief = new StringBuilder(info.name != null ? info.name : "Unknown");
            if (manaCost != null && !manaCost.isEmpty()) {
                brief.append(" ").append(manaCost);
            }

            // Build detailed description
            StringBuilder detailed = new StringBuilder(info.name != null ? info.name : "Unknown");
            detailed.append(". ");
            if (manaCost != null && !manaCost.isEmpty()) {
                detailed.append("Cost: ").append(manaCost).append(". ");
            }
            if (types != null && !types.isEmpty()) {
                detailed.append(types).append(". ");
            }

            // Check creature P/T
            boolean isCreature = false;
            try {
                Method m = cardView.getClass().getMethod("isCreature");
                isCreature = (Boolean) m.invoke(cardView);
            } catch (Exception ignored) {}

            if (isCreature && power != null && toughness != null) {
                brief.append(" ").append(power).append("/").append(toughness);
                detailed.append(power).append("/").append(toughness).append(". ");
            }

            // Loyalty for planeswalkers
            if (loyalty > 0) {
                brief.append(" Loyalty: ").append(loyalty);
                detailed.append("Loyalty: ").append(loyalty).append(". ");
            }

            // Rules for detailed view
            if (rules != null && !rules.isEmpty()) {
                detailed.append("Rules: ");
                for (String rule : rules) {
                    // Strip HTML tags
                    String clean = rule.replaceAll("<[^>]*>", "").trim();
                    if (!clean.isEmpty()) {
                        detailed.append(clean).append(". ");
                    }
                }
            }

            info.brief = brief.toString();
            info.detailed = detailed.toString();
            return info;
        } catch (Exception e) {
            return null;
        }
    }

    // ========== Helpers ==========

    private String getTitle() {
        try {
            // MageDialog extends JInternalFrame which has getTitle()
            Method getTitle = dialog.getClass().getMethod("getTitle");
            return (String) getTitle.invoke(dialog);
        } catch (Exception e) {
            return null;
        }
    }

    private UUID getGameId() {
        // Try to get gameId from the dialog
        try {
            Object val = getFieldValue(dialog.getClass(), dialog, "gameId");
            if (val instanceof UUID) return (UUID) val;
        } catch (Exception ignored) {}

        // Fallback: try to find it from GamePanel
        try {
            // Walk up to find GamePanel parent
            // Or just search active games via SessionHandler
        } catch (Exception ignored) {}

        return null;
    }

    private boolean isTargeting() {
        try {
            Method isModal = dialog.getClass().getMethod("isModal");
            return (Boolean) isModal.invoke(dialog);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isDialogVisible() {
        if (dialog == null || !dialog.isVisible()) return false;
        Component c = dialog;
        while (c != null) {
            if (!c.isVisible()) return false;
            c = c.getParent();
        }
        return true;
    }

    private Object getFieldValue(Class<?> clazz, Object obj, String name) {
        while (clazz != null) {
            try {
                Field f = clazz.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(obj);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    private String callStringMethod(Object obj, String methodName) {
        try {
            Method m = obj.getClass().getMethod(methodName);
            Object result = m.invoke(obj);
            return result != null ? result.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private UUID callUUIDMethod(Object obj, String methodName) {
        try {
            Method m = obj.getClass().getMethod(methodName);
            Object result = m.invoke(obj);
            return result instanceof UUID ? (UUID) result : null;
        } catch (Exception e) {
            return null;
        }
    }

    private int callIntMethod(Object obj, String methodName) {
        try {
            Method m = obj.getClass().getMethod(methodName);
            Object result = m.invoke(obj);
            if (result instanceof Integer) return (Integer) result;
            if (result instanceof Number) return ((Number) result).intValue();
            if (result instanceof String) return Integer.parseInt((String) result);
        } catch (Exception ignored) {}
        return 0;
    }

    private void speak(String text) {
        SpeechOutput speech = AccessibilityManager.getInstance().getSpeech();
        if (speech != null) speech.speak(text);
    }

    // ========== Card info struct ==========

    private static class CardInfo {
        String name;
        UUID id;
        UUID abilityId;
        boolean isAbility;
        boolean choosable;
        boolean selected;
        String brief;
        String detailed;
    }
}
