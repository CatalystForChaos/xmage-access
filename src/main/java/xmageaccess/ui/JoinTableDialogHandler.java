package xmageaccess.ui;

import xmageaccess.AccessibilityManager;
import xmageaccess.speech.SpeechOutput;
import xmageaccess.util.Log;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.File;

import static xmageaccess.util.ReflectionUtils.findFieldTyped;

/**
 * Accessibility handler for XMage's JoinTableDialog — the dialog that opens
 * every time you join someone else's table (TablesPanel always calls
 * showDialog for a constructed table, since a deck has to be chosen).
 *
 * <p>Its deck button opens a JFileChooser, which is unusable with a screen
 * reader, so Ctrl+D substitutes the same {@link AccessibleDeckPicker} the new
 * table dialog uses.
 *
 * Keyboard shortcuts:
 *   Ctrl+D      - Choose a deck (accessible picker)
 *
 * <p>That is the only key. The password field and the OK button are in the
 * dialog's own Tab order and announce themselves on focus, so a shortcut
 * for either would only be a second way to do the same thing.
 */
public class JoinTableDialogHandler {

    private final Component dialog;
    private Component newPlayerPanel;
    private KeyEventDispatcher keyDispatcher;

    public JoinTableDialogHandler(Component dialog) {
        this.dialog = dialog;
    }

    public void attach() {
        try {
            newPlayerPanel = findFieldTyped(dialog, "newPlayerPanel", Component.class);
            addKeyboardShortcuts();
            announce();
        } catch (Exception e) {
            Log.warn("JoinTable", "attach failed", e);
        }
    }

    public void detach() {
        if (keyDispatcher != null) {
            KeyboardFocusManager.getCurrentKeyboardFocusManager()
                    .removeKeyEventDispatcher(keyDispatcher);
            keyDispatcher = null;
        }
    }

    private void announce() {
        StringBuilder sb = new StringBuilder("Join table. ");
        sb.append(deckSummary()).append(" ");
        sb.append("Ctrl+D chooses a deck; Tab reaches the password and "
                + "the OK button.");
        speak(sb.toString());
    }

    private String deckSummary() {
        JTextField txtDeck = deckField();
        String deck = txtDeck != null ? txtDeck.getText() : null;
        if (deck == null || deck.trim().isEmpty()) return "No deck chosen yet.";
        return "Deck: " + new File(deck).getName() + ".";
    }

    private JTextField deckField() {
        return newPlayerPanel != null
                ? findFieldTyped(newPlayerPanel, "txtPlayerDeck", JTextField.class) : null;
    }

    private void addKeyboardShortcuts() {
        keyDispatcher = e -> {
            if (e.getID() != KeyEvent.KEY_PRESSED) return false;
            if (!dialog.isVisible()) return false;
            if (!e.isControlDown() || e.isShiftDown()) return false;

            // Ctrl+D only. The password field, the summary and the OK
            // button are all reachable with Tab, and each announces itself
            // on focus; the deck button is the one thing that is not, since
            // XMage answers it with a JFileChooser.
            if (e.getKeyCode() == KeyEvent.VK_D) {
                chooseDeck();
                return true;
            }
            return false;
        };
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addKeyEventDispatcher(keyDispatcher);
    }

    private void chooseDeck() {
        JTextField txtDeck = deckField();
        if (txtDeck == null) {
            speak("Deck field not found.");
            return;
        }
        File startDir = AccessibleDeckPicker.findDeckDirectory();
        if (startDir == null) {
            speak("Deck folder not found.");
            return;
        }
        new AccessibleDeckPicker(txtDeck, startDir).show();
    }

    private void speak(String text) {
        SpeechOutput speech = AccessibilityManager.getInstance().getSpeech();
        if (speech != null) speech.speak(text);
    }
}
