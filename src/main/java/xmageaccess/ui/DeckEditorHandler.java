package xmageaccess.ui;

import xmageaccess.AccessibilityManager;
import xmageaccess.speech.SpeechOutput;

import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Accessibility handler for the XMage Deck Editor Panel.
 * Provides keyboard shortcuts for deck summaries.
 *
 * Keyboard shortcuts:
 *   Ctrl+F1 - Read deck summary (total cards, creatures, lands, spells)
 *   Ctrl+F2 - Read sideboard summary
 *   Ctrl+F3 - Read search results count
 */
public class DeckEditorHandler {

    private final Component deckEditorPanel;

    // Cached reflection references
    private Object cardSelector;
    private Object deckArea;
    private Object mainModel;
    private Object deckList;
    private Object sideboardList;

    public DeckEditorHandler(Component deckEditorPanel) {
        this.deckEditorPanel = deckEditorPanel;
    }

    public void attach() {
        try {
            discoverComponents();
            addKeyboardShortcuts();
            speak("Deck editor opened. "
                    + "Ctrl+N new deck, Ctrl+O load, Ctrl+S save. "
                    + "Ctrl+F1 for all shortcuts.");
        } catch (Exception e) {
            System.err.println("[XMage Access] Error attaching to deck editor: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void detach() {
        // Keyboard dispatcher is left registered but isDeckEditorVisible() check prevents it from firing
    }

    private void discoverComponents() {
        cardSelector = findFieldDeep(deckEditorPanel, "cardSelector");
        deckArea = findFieldDeep(deckEditorPanel, "deckArea");

        if (cardSelector != null) {
            mainModel = findFieldDeep(cardSelector, "mainModel");
        }

        if (deckArea != null) {
            deckList = findFieldDeep(deckArea, "deckList");
            sideboardList = findFieldDeep(deckArea, "sideboardList");
        }

        System.out.println("[XMage Access] Deck editor - cardSelector: " + (cardSelector != null)
                + ", deckArea: " + (deckArea != null)
                + ", mainModel: " + (mainModel != null)
                + ", deckList: " + (deckList != null)
                + ", sideboardList: " + (sideboardList != null));
    }

    private void addKeyboardShortcuts() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addKeyEventDispatcher(e -> {
                    if (e.getID() != KeyEvent.KEY_PRESSED) return false;
                    if (!isDeckEditorVisible()) return false;
                    // If the accessible deck editor or sideboarding window is open, it handles all shortcuts
                    if (AccessibleDeckEditorWindow.isAnyWindowVisible()) return false;
                    if (SideboardingHandler.isAnyWindowVisible()) return false;

                    if (e.isControlDown() && !e.isShiftDown()) {
                        switch (e.getKeyCode()) {
                            case KeyEvent.VK_F1: readDeckSummary(); return true;
                            case KeyEvent.VK_F2: readSideboardSummary(); return true;
                            case KeyEvent.VK_F3: readSearchResultsCount(); return true;
                        }
                    }
                    return false;
                });
    }

    private boolean isDeckEditorVisible() {
        return deckEditorPanel != null && deckEditorPanel.isVisible();
    }

    // ========== SUMMARY READERS ==========

    private void readDeckSummary() {
        refreshReferences();
        if (deckList == null) {
            speak("Deck not available.");
            return;
        }

        List<?> allCards = findFieldTyped(deckList, "allCards", List.class);
        if (allCards == null || allCards.isEmpty()) {
            speak("Deck is empty.");
            return;
        }

        int total = allCards.size();
        int creatures = 0, spells = 0, lands = 0;
        for (Object card : allCards) {
            if (callBool(card, "isCreature")) creatures++;
            else if (callBool(card, "isLand")) lands++;
            else spells++;
        }

        speak("Deck: " + total + " cards. "
                + creatures + " creatures, "
                + spells + " spells, "
                + lands + " lands.");
    }

    private void readSideboardSummary() {
        refreshReferences();
        if (sideboardList == null) {
            speak("Sideboard not available.");
            return;
        }

        List<?> allCards = findFieldTyped(sideboardList, "allCards", List.class);
        if (allCards == null || allCards.isEmpty()) {
            speak("Sideboard is empty.");
            return;
        }

        speak("Sideboard: " + allCards.size() + " cards.");
    }

    private void readSearchResultsCount() {
        refreshReferences();
        if (mainModel == null) {
            speak("Search not available.");
            return;
        }

        List<?> view = findFieldTyped(mainModel, "view", List.class);
        int count = view != null ? view.size() : 0;
        speak("Search results: " + count + " cards.");
    }

    private void refreshReferences() {
        if (deckArea != null) {
            deckList = findFieldDeep(deckArea, "deckList");
            sideboardList = findFieldDeep(deckArea, "sideboardList");
        }
        if (cardSelector != null) {
            mainModel = findFieldDeep(cardSelector, "mainModel");
        }
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
