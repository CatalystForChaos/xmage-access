package xmageaccess.ui;

import xmageaccess.AccessibilityManager;
import xmageaccess.speech.SpeechOutput;
import xmageaccess.util.Log;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;

import static xmageaccess.util.ReflectionUtils.findFieldTyped;

/**
 * Accessibility handler for XMage's ErrorDialog, which reports both client
 * errors and server-side game errors. Without it the dialog appears silently
 * and the game just seems to stop responding.
 *
 * <p>Error text is often a stack trace, so only the first lines are spoken;
 * Ctrl+Shift+R reads the whole thing for anyone filing a bug report.
 *
 * Keyboard shortcuts:
 *   Ctrl+R        - Read the first lines of the error again
 *   Ctrl+Shift+R  - Read the full error text
 *   Ctrl+C        - Copy the error to the clipboard
 *   Ctrl+Enter    - Close the dialog
 */
public class ErrorDialogHandler {

    private static final int SPOKEN_CHARS = 300;

    private final Component dialog;
    private JTextArea textError;
    private JButton btnOK;
    private JButton btnCopyToClipboard;
    private KeyEventDispatcher keyDispatcher;

    public ErrorDialogHandler(Component dialog) {
        this.dialog = dialog;
    }

    public void attach() {
        try {
            textError = findFieldTyped(dialog, "textError", JTextArea.class);
            btnOK = findFieldTyped(dialog, "btnOK", JButton.class);
            btnCopyToClipboard = findFieldTyped(dialog, "btnCopyToClipboard", JButton.class);

            addKeyboardShortcuts();
            speak("XMage error. " + shortError()
                    + " Ctrl+Shift+R reads the full text, Ctrl+C copies it, "
                    + "Ctrl+Enter closes.");
        } catch (Exception e) {
            Log.warn("Error", "attach failed", e);
        }
    }

    public void detach() {
        if (keyDispatcher != null) {
            KeyboardFocusManager.getCurrentKeyboardFocusManager()
                    .removeKeyEventDispatcher(keyDispatcher);
            keyDispatcher = null;
        }
    }

    private String fullError() {
        String text = textError != null ? textError.getText() : null;
        if (text == null || text.trim().isEmpty()) return "No details given.";
        return text.trim();
    }

    /** The opening of the error — enough to say what went wrong. */
    private String shortError() {
        String text = fullError();
        if (text.length() <= SPOKEN_CHARS) return text;
        int cut = text.lastIndexOf(' ', SPOKEN_CHARS);
        return text.substring(0, cut > 0 ? cut : SPOKEN_CHARS) + ", and more.";
    }

    private void addKeyboardShortcuts() {
        keyDispatcher = e -> {
            if (e.getID() != KeyEvent.KEY_PRESSED) return false;
            if (!dialog.isVisible()) return false;
            if (!e.isControlDown()) return false;

            if (e.isShiftDown()) {
                if (e.getKeyCode() == KeyEvent.VK_R) {
                    speak(fullError());
                    return true;
                }
                return false;
            }

            switch (e.getKeyCode()) {
                case KeyEvent.VK_R:
                    speak(shortError());
                    return true;
                case KeyEvent.VK_C:
                    copyError();
                    return true;
                case KeyEvent.VK_ENTER:
                    close();
                    return true;
                default:
                    return false;
            }
        };
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addKeyEventDispatcher(keyDispatcher);
    }

    private void copyError() {
        if (btnCopyToClipboard == null || !btnCopyToClipboard.isEnabled()) {
            speak("Copying is not available.");
            return;
        }
        btnCopyToClipboard.doClick();
        speak("Error copied to clipboard.");
    }

    private void close() {
        if (btnOK == null || !btnOK.isEnabled()) {
            speak("Cannot close.");
            return;
        }
        speak("Closing.");
        btnOK.doClick();
    }

    private void speak(String text) {
        SpeechOutput speech = AccessibilityManager.getInstance().getSpeech();
        if (speech != null) speech.speak(text);
    }
}
