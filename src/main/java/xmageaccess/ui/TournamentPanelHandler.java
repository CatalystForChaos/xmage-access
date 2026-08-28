package xmageaccess.ui;

import xmageaccess.AccessibilityManager;
import xmageaccess.speech.SpeechOutput;

import static xmageaccess.util.ReflectionUtils.*;

import javax.swing.*;
import java.awt.*;

/**
 * Accessibility handler for the XMage Tournament Panel.
 *
 * <p>Announcements only. The tournament used to carry its own keyboard
 * shortcuts (standings, match results, watch a match), but they worked solely
 * inside XMage's own window, which is where the agent no longer takes keys —
 * see {@code UiUtils.isAgentWindowActive}. Unlike the game, the lobby and the
 * deck editor, the tournament has no accessible window to move them into, so
 * what remains is the state polling and the tournament chat, which
 * {@link ChatAccessHelper} reads out on its own as messages arrive.
 */
public class TournamentPanelHandler {

    private final Component panel;
    private JTable tablePlayers;
    private ChatAccessHelper chatHelper;
    private String lastState = "";
    private Timer pollTimer;

    public TournamentPanelHandler(Component panel) {
        this.panel = panel;
    }

    public void attach() {
        try {
            discoverComponents();
            startMonitoring();
            announceTournament();
        } catch (Exception e) {
            xmageaccess.util.Log.warn("Tournament", "attach failed", e);
        }
    }

    public void detach() {
        if (pollTimer != null) {
            pollTimer.stop();
            pollTimer = null;
        }
        if (chatHelper != null) {
            chatHelper.detach();
            chatHelper = null;
        }
    }

    private void discoverComponents() {
        tablePlayers = findFieldTyped(panel, "tablePlayers", JTable.class);

        // Attach chat — it announces incoming messages by itself
        Object chatPanel = findFieldDeep(panel, "chatPanel1");
        if (chatPanel != null) {
            chatHelper = new ChatAccessHelper(chatPanel);
            chatHelper.attach();
        }

        System.out.println("[XMage Access] Tournament - players: " + (tablePlayers != null)
                + ", chat: " + (chatHelper != null));
    }

    private void announceTournament() {
        StringBuilder sb = new StringBuilder("Tournament. ");
        sb.append(readStatus());

        if (tablePlayers != null) {
            sb.append(tablePlayers.getRowCount()).append(" players. ");
        }

        sb.append("Announcements only — the tournament has no keyboard control yet.");
        speak(sb.toString());
    }

    private void startMonitoring() {
        // Poll for state changes
        pollTimer = new Timer(3000, e -> {
            if (!isPanelVisible()) return;
            try {
                String state = readTextField("txtTournamentState");
                if (state != null && !state.equals(lastState) && !lastState.isEmpty()) {
                    speak("Tournament state: " + state);
                }
                if (state != null) lastState = state;
            } catch (Exception ex) {
                xmageaccess.util.Log.debug("Tournament", "poll error: " + ex);
            }
        });
        pollTimer.setRepeats(true);
        pollTimer.start();
    }

    // ========== STATUS ==========

    private String readStatus() {
        StringBuilder sb = new StringBuilder();

        String name = readTextField("txtName");
        if (name != null && !name.isEmpty()) sb.append(name).append(". ");

        String type = readTextField("txtType");
        if (type != null && !type.isEmpty()) sb.append(type).append(". ");

        String state = readTextField("txtTournamentState");
        if (state != null && !state.isEmpty()) sb.append("State: ").append(state).append(". ");

        return sb.toString();
    }

    private String readTextField(String fieldName) {
        try {
            JTextField field = findFieldTyped(panel, fieldName, JTextField.class);
            if (field != null) return field.getText();
        } catch (Exception ignored) {}
        return null;
    }

    private boolean isPanelVisible() {
        if (panel == null || !panel.isVisible()) return false;
        Component c = panel;
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
