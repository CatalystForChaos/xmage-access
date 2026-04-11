package xmageaccess.ui;

import xmageaccess.AccessibilityManager;
import xmageaccess.speech.SpeechOutput;

import static xmageaccess.util.ReflectionUtils.*;
import static xmageaccess.util.TextUtils.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;

/**
 * Accessibility handler for the XMage GameEndDialog.
 * Announces game result, statistics, and allows dismissal.
 *
 * Keyboard shortcuts:
 *   Ctrl+Enter     - Close the dialog
 *   Ctrl+R         - Re-read result and stats
 */
public class GameEndDialogHandler {

    private final Component dialog;
    private JButton btnOk;
    private KeyEventDispatcher keyDispatcher;

    public GameEndDialogHandler(Component dialog) {
        this.dialog = dialog;
    }

    public void attach() {
        try {
            discoverComponents();
            addKeyboardShortcuts();
            announceResult();
        } catch (Exception e) {
            System.err.println("[XMage Access] Error attaching to GameEndDialog: " + e.getMessage());
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
        btnOk = findFieldTyped(dialog, "btnOk", JButton.class);
    }

    private void announceResult() {
        StringBuilder sb = new StringBuilder("Game over. ");

        // Result tab labels
        String gameInfo = readLabel("lblGameInfo");
        if (gameInfo != null && !gameInfo.isEmpty()) {
            sb.append(cleanHtml(gameInfo)).append(". ");
        }

        String matchInfo = readLabel("lblMatchInfo");
        if (matchInfo != null && !matchInfo.isEmpty()) {
            sb.append(cleanHtml(matchInfo)).append(". ");
        }

        String additionalInfo = readLabel("lblAdditionalInfo");
        if (additionalInfo != null && !additionalInfo.isEmpty()) {
            sb.append(cleanHtml(additionalInfo)).append(". ");
        }

        // Statistics tab labels
        String life = readLabel("txtLife");
        if (life != null && !life.isEmpty()) {
            sb.append("Life: ").append(life).append(". ");
        }

        String matchScore = readLabel("txtMatchScore");
        if (matchScore != null && !matchScore.isEmpty()) {
            sb.append("Score: ").append(matchScore).append(". ");
        }

        String durationGame = readLabel("txtDurationGame");
        if (durationGame != null && !durationGame.isEmpty()) {
            sb.append("Game duration: ").append(durationGame).append(". ");
        }

        String durationMatch = readLabel("txtDurationMatch");
        if (durationMatch != null && !durationMatch.isEmpty()) {
            sb.append("Match duration: ").append(durationMatch).append(". ");
        }

        sb.append("Ctrl+Enter to close.");
        speak(sb.toString());
    }

    private void addKeyboardShortcuts() {
        keyDispatcher = e -> {
                    if (e.getID() != KeyEvent.KEY_PRESSED) return false;
                    if (!isDialogVisible()) return false;

                    if (e.isControlDown() && !e.isShiftDown()) {
                        switch (e.getKeyCode()) {
                            case KeyEvent.VK_ENTER:
                                if (btnOk != null) btnOk.doClick();
                                return true;
                            case KeyEvent.VK_R:
                                announceResult();
                                return true;
                        }
                    }

                    return false;
                };
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addKeyEventDispatcher(keyDispatcher);
    }

    private String readLabel(String fieldName) {
        JLabel lbl = findFieldTyped(dialog, fieldName, JLabel.class);
        return lbl != null ? lbl.getText() : null;
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
