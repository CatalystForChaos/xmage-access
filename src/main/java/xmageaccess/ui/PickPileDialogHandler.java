package xmageaccess.ui;

import xmageaccess.AccessibilityManager;
import xmageaccess.speech.SpeechOutput;

import static xmageaccess.util.ReflectionUtils.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;

/**
 * Accessibility handler for the XMage PickPileDialog.
 * Used for cards like Fact or Fiction where you choose between two piles.
 *
 * Keyboard shortcuts:
 *   Ctrl+1         - Choose pile 1
 *   Ctrl+2         - Choose pile 2
 *   Ctrl+R         - Re-read both piles
 */
public class PickPileDialogHandler {

    private final Component dialog;
    private JButton btnChoosePile1;
    private JButton btnChoosePile2;
    private Component pile1; // CardArea
    private Component pile2; // CardArea
    private KeyEventDispatcher keyDispatcher;

    public PickPileDialogHandler(Component dialog) {
        this.dialog = dialog;
    }

    public void attach() {
        try {
            discoverComponents();
            addKeyboardShortcuts();
            announcePiles();
        } catch (Exception e) {
            System.err.println("[XMage Access] Error attaching to PickPileDialog: " + e.getMessage());
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
        btnChoosePile1 = findFieldTyped(dialog,"btnChoosePile1", JButton.class);
        btnChoosePile2 = findFieldTyped(dialog,"btnChoosePile2", JButton.class);
        pile1 = findFieldTyped(dialog,"pile1", Component.class);
        pile2 = findFieldTyped(dialog,"pile2", Component.class);
    }

    private void announcePiles() {
        StringBuilder sb = new StringBuilder("Choose a pile. ");

        String pile1Cards = readPileCards(pile1);
        String pile2Cards = readPileCards(pile2);

        sb.append("Pile 1: ");
        sb.append(pile1Cards.isEmpty() ? "empty" : pile1Cards);
        sb.append(". ");

        sb.append("Pile 2: ");
        sb.append(pile2Cards.isEmpty() ? "empty" : pile2Cards);
        sb.append(". ");

        sb.append("Ctrl+1 for pile 1, Ctrl+2 for pile 2.");
        speak(sb.toString());
    }

    private String readPileCards(Component cardArea) {
        if (cardArea == null) return "";

        try {
            // CardArea has a JLayeredPane field named "cardArea" containing MageCard components
            Object innerPanel = findFieldDeep(cardArea, "cardArea");

            if (!(innerPanel instanceof Container)) return "";

            Component[] components = ((Container) innerPanel).getComponents();
            StringBuilder sb = new StringBuilder();
            int count = 0;

            for (Component comp : components) {
                String name = getCardName(comp);
                if (name != null && !name.isEmpty()) {
                    if (count > 0) sb.append(", ");
                    sb.append(name);
                    count++;
                }
            }

            if (count > 0) {
                return count + (count == 1 ? " card: " : " cards: ") + sb.toString();
            }
        } catch (Exception e) {
            System.err.println("[XMage Access] Error reading pile: " + e.getMessage());
        }
        return "";
    }

    private String getCardName(Component mageCard) {
        // MageCard.getOriginal() returns CardView, CardView.getName() returns name
        Object cardView = callMethod(mageCard, "getOriginal");
        return cardView != null ? callString(cardView, "getName") : null;
    }

    private void addKeyboardShortcuts() {
        keyDispatcher = e -> {
                    if (e.getID() != KeyEvent.KEY_PRESSED) return false;
                    if (!isDialogVisible()) return false;

                    if (e.isControlDown() && !e.isShiftDown()) {
                        switch (e.getKeyCode()) {
                            case KeyEvent.VK_1:
                                choosePile(btnChoosePile1, "Pile 1");
                                return true;
                            case KeyEvent.VK_2:
                                choosePile(btnChoosePile2, "Pile 2");
                                return true;
                            case KeyEvent.VK_R:
                                announcePiles();
                                return true;
                        }
                    }

                    return false;
                };
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addKeyEventDispatcher(keyDispatcher);
    }

    private void choosePile(JButton btn, String name) {
        if (btn != null && btn.isEnabled()) {
            speak("Choosing " + name + ".");
            btn.doClick();
        } else {
            speak(name + " not available.");
        }
    }

    private boolean isDialogVisible() {
        if (!dialog.isVisible()) return false;
        Component c = dialog;
        while (c != null) {
            if (!c.isVisible()) return false;
            c = c.getParent();
        }
        return true;
    }

    private void speak(String text) {
        SpeechOutput speech = AccessibilityManager.getInstance().getSpeech();
        if (speech != null) speech.speak(text);
    }
}
