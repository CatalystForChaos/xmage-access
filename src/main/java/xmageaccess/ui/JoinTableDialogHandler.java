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
 *   Ctrl+P      - Focus the password field
 *   Ctrl+R      - Read the current selection
 *   Ctrl+Enter  - Join the table
 */
public class JoinTableDialogHandler {

    private final Component dialog;
    private Component newPlayerPanel;
    private JTextField txtPassword;
    private JButton btnOK;
    private KeyEventDispatcher keyDispatcher;

    public JoinTableDialogHandler(Component dialog) {
        this.dialog = dialog;
    }

    public void attach() {
        try {
            newPlayerPanel = findFieldTyped(dialog, "newPlayerPanel", Component.class);
            txtPassword = findFieldTyped(dialog, "txtPassword", JTextField.class);
            btnOK = findFieldTyped(dialog, "btnOK", JButton.class);
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
        sb.append("Ctrl+D to choose a deck, Ctrl+P for the password, "
                + "Ctrl+Enter to join.");
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

            switch (e.getKeyCode()) {
                case KeyEvent.VK_D:
                    chooseDeck();
                    return true;
                case KeyEvent.VK_P:
                    focusPassword();
                    return true;
                case KeyEvent.VK_R:
                    announce();
                    return true;
                case KeyEvent.VK_ENTER:
                    join();
                    return true;
                default:
                    return false;
            }
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

    private void focusPassword() {
        if (txtPassword == null) {
            speak("This table has no password field.");
            return;
        }
        txtPassword.requestFocusInWindow();
        String current = txtPassword.getText();
        speak("Password field." + (current != null && !current.isEmpty()
                ? " Current: " + current + "." : ""));
    }

    private void join() {
        JTextField txtDeck = deckField();
        if (txtDeck == null || txtDeck.getText() == null || txtDeck.getText().trim().isEmpty()) {
            speak("Choose a deck first with Ctrl+D.");
            return;
        }
        if (btnOK == null || !btnOK.isEnabled()) {
            speak("Cannot join.");
            return;
        }
        speak("Joining table.");
        btnOK.doClick();
    }

    private void speak(String text) {
        SpeechOutput speech = AccessibilityManager.getInstance().getSpeech();
        if (speech != null) speech.speak(text);
    }
}
