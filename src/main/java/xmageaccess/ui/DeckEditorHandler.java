package xmageaccess.ui;

import xmageaccess.AccessibilityManager;
import xmageaccess.speech.SpeechOutput;

import static xmageaccess.util.ReflectionUtils.*;

import java.awt.Component;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.KeyEvent;
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
    private KeyEventDispatcher keyDispatcher;

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
            xmageaccess.util.Log.warn("DeckEditor", "attach failed", e);
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
        keyDispatcher = e -> {
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
                };
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addKeyEventDispatcher(keyDispatcher);
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

    private void speak(String text) {
        SpeechOutput speech = AccessibilityManager.getInstance().getSpeech();
        if (speech != null) {
            speech.speak(text);
        }
    }
}
