package xmageaccess.ui;

import xmageaccess.AccessibilityManager;
import xmageaccess.speech.SpeechOutput;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;

/**
 * Accessibility handler for the XMage Tournament Panel.
 * Provides keyboard-driven tournament navigation with speech output.
 *
 * Keyboard shortcuts:
 *   Ctrl+R           - Read tournament status (name, type, state, time)
 *   Ctrl+P           - Read player standings
 *   Ctrl+G           - Read match results
 *   Ctrl+Up/Down     - Navigate player standings
 *   Ctrl+Shift+Up/Down - Navigate match results
 *   Ctrl+M           - Focus chat input
 *   Ctrl+Shift+M     - Read recent chat
 *   Ctrl+W           - Watch selected match
 *   Escape           - Close tournament window
 */
public class TournamentPanelHandler {

    private final Component panel;
    private JTable tablePlayers;
    private JTable tableMatches;
    private JButton btnQuitTournament;
    private JButton btnCloseWindow;
    private ChatAccessHelper chatHelper;
    private int playerCursor = 0;
    private int matchCursor = 0;
    private String lastState = "";

    public TournamentPanelHandler(Component panel) {
        this.panel = panel;
    }

    public void attach() {
        try {
            discoverComponents();
            addKeyboardShortcuts();
            startMonitoring();
            announceTournament();
        } catch (Exception e) {
            System.err.println("[XMage Access] Error attaching to TournamentPanel: " + e.getMessage());
        }
    }

    public void detach() {}

    private void discoverComponents() {
        Class<?> clazz = panel.getClass();
        tablePlayers = getField(clazz, "tablePlayers", JTable.class);
        tableMatches = getField(clazz, "tableMatches", JTable.class);
        btnQuitTournament = getField(clazz, "btnQuitTournament", JButton.class);
        btnCloseWindow = getField(clazz, "btnCloseWindow", JButton.class);

        // Attach chat
        Object chatPanel = getField(clazz, "chatPanel1", Object.class);
        if (chatPanel != null) {
            chatHelper = new ChatAccessHelper(chatPanel);
            chatHelper.attach();
        }

        System.out.println("[XMage Access] Tournament - players: " + (tablePlayers != null)
                + ", matches: " + (tableMatches != null)
                + ", chat: " + (chatHelper != null));
    }

    private void announceTournament() {
        StringBuilder sb = new StringBuilder("Tournament. ");
        sb.append(readStatus());

        if (tablePlayers != null) {
            sb.append(tablePlayers.getRowCount()).append(" players. ");
        }

        sb.append("Ctrl+R for status. Ctrl+P for standings. Ctrl+G for matches.");
        speak(sb.toString());
    }

    private void startMonitoring() {
        // Poll for state changes
        Timer timer = new Timer(3000, e -> {
            if (!isPanelVisible()) return;
            try {
                String state = readTextField("txtTournamentState");
                if (state != null && !state.equals(lastState) && !lastState.isEmpty()) {
                    speak("Tournament state: " + state);
                }
                if (state != null) lastState = state;
            } catch (Exception ignored) {}
        });
        timer.setRepeats(true);
        timer.start();
    }

    private void addKeyboardShortcuts() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addKeyEventDispatcher(e -> {
                    if (e.getID() != KeyEvent.KEY_PRESSED) return false;
                    if (!isPanelVisible()) return false;
                    if (!e.isControlDown()) return false;

                    // Ctrl+Shift shortcuts
                    if (e.isShiftDown()) {
                        switch (e.getKeyCode()) {
                            case KeyEvent.VK_UP:
                                navigateMatches(-1);
                                return true;
                            case KeyEvent.VK_DOWN:
                                navigateMatches(1);
                                return true;
                            case KeyEvent.VK_M:
                                if (chatHelper != null) chatHelper.readRecentChat(5);
                                else speak("Chat not available.");
                                return true;
                        }
                        return false;
                    }

                    // Ctrl (no shift) shortcuts
                    switch (e.getKeyCode()) {
                        case KeyEvent.VK_R:
                            readFullStatus();
                            return true;
                        case KeyEvent.VK_P:
                            readStandings();
                            return true;
                        case KeyEvent.VK_G:
                            readMatches();
                            return true;
                        case KeyEvent.VK_UP:
                            navigatePlayers(-1);
                            return true;
                        case KeyEvent.VK_DOWN:
                            navigatePlayers(1);
                            return true;
                        case KeyEvent.VK_W:
                            watchMatch();
                            return true;
                        case KeyEvent.VK_M:
                            if (chatHelper != null) chatHelper.focusInput();
                            else speak("Chat not available.");
                            return true;
                        case KeyEvent.VK_ESCAPE:
                            if (btnCloseWindow != null) btnCloseWindow.doClick();
                            return true;
                    }
                    return false;
                });
    }

    // ========== STATUS ==========

    private void readFullStatus() {
        StringBuilder sb = new StringBuilder("Tournament status. ");
        sb.append(readStatus());

        if (tablePlayers != null) {
            sb.append(tablePlayers.getRowCount()).append(" players. ");
        }
        if (tableMatches != null && tableMatches.getRowCount() > 0) {
            sb.append(tableMatches.getRowCount()).append(" matches. ");
        }
        if (btnQuitTournament != null && btnQuitTournament.isVisible()) {
            sb.append("You are in this tournament. ");
        }

        speak(sb.toString());
    }

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

    // ========== PLAYER STANDINGS ==========

    private void readStandings() {
        if (tablePlayers == null || tablePlayers.getRowCount() == 0) {
            speak("No player standings.");
            return;
        }

        int rows = tablePlayers.getRowCount();
        StringBuilder sb = new StringBuilder(rows + " players. ");
        for (int i = 0; i < Math.min(rows, 10); i++) {
            sb.append(readPlayerRow(i)).append(". ");
        }
        if (rows > 10) sb.append("And ").append(rows - 10).append(" more.");
        speak(sb.toString());
    }

    private void navigatePlayers(int direction) {
        if (tablePlayers == null || tablePlayers.getRowCount() == 0) {
            speak("No players.");
            return;
        }

        playerCursor += direction;
        int rows = tablePlayers.getRowCount();
        if (playerCursor < 0) playerCursor = rows - 1;
        if (playerCursor >= rows) playerCursor = 0;

        speak((playerCursor + 1) + " of " + rows + ": " + readPlayerRow(playerCursor));
    }

    private String readPlayerRow(int row) {
        if (tablePlayers == null) return "Unknown";
        StringBuilder sb = new StringBuilder();
        int cols = tablePlayers.getColumnCount();
        for (int col = 0; col < cols; col++) {
            Object val = tablePlayers.getValueAt(row, col);
            if (val == null) continue;
            // Skip icon columns
            if (val instanceof Icon || val instanceof ImageIcon) continue;
            String text = val.toString().trim();
            if (!text.isEmpty()) {
                String colName = tablePlayers.getColumnName(col);
                if (colName != null && !colName.isEmpty()) {
                    sb.append(colName).append(": ");
                }
                sb.append(text).append(", ");
            }
        }
        return sb.length() > 0 ? sb.toString() : "Empty row";
    }

    // ========== MATCH RESULTS ==========

    private void readMatches() {
        if (tableMatches == null || tableMatches.getRowCount() == 0) {
            speak("No matches.");
            return;
        }

        int rows = tableMatches.getRowCount();
        StringBuilder sb = new StringBuilder(rows + " matches. ");
        for (int i = 0; i < Math.min(rows, 8); i++) {
            sb.append(readMatchRow(i)).append(". ");
        }
        if (rows > 8) sb.append("And ").append(rows - 8).append(" more.");
        speak(sb.toString());
    }

    private void navigateMatches(int direction) {
        if (tableMatches == null || tableMatches.getRowCount() == 0) {
            speak("No matches.");
            return;
        }

        matchCursor += direction;
        int rows = tableMatches.getRowCount();
        if (matchCursor < 0) matchCursor = rows - 1;
        if (matchCursor >= rows) matchCursor = 0;

        speak("Match " + (matchCursor + 1) + " of " + rows + ": " + readMatchRow(matchCursor));
    }

    private String readMatchRow(int row) {
        if (tableMatches == null) return "Unknown";
        StringBuilder sb = new StringBuilder();
        int cols = tableMatches.getColumnCount();
        for (int col = 0; col < cols; col++) {
            Object val = tableMatches.getValueAt(row, col);
            if (val == null) continue;
            if (val instanceof Icon || val instanceof ImageIcon) continue;
            String text = val.toString().trim();
            if (!text.isEmpty()) {
                if (sb.length() > 0) sb.append(" vs ");
                sb.append(text);
            }
        }
        return sb.length() > 0 ? sb.toString() : "Empty match";
    }

    private void watchMatch() {
        if (tableMatches == null || tableMatches.getRowCount() == 0) {
            speak("No matches to watch.");
            return;
        }
        if (matchCursor >= tableMatches.getRowCount()) matchCursor = 0;

        // Try to click the action button in the last column
        try {
            int actionCol = tableMatches.getColumnCount() - 1;
            Object val = tableMatches.getValueAt(matchCursor, actionCol);
            if (val != null && val.toString().toLowerCase().contains("watch")) {
                // Simulate click on the action cell
                tableMatches.setRowSelectionInterval(matchCursor, matchCursor);
                tableMatches.editCellAt(matchCursor, actionCol);
                speak("Watching match.");
            } else {
                speak("This match cannot be watched. State: " + readMatchRow(matchCursor));
            }
        } catch (Exception e) {
            speak("Could not watch match.");
        }
    }

    // ========== HELPERS ==========

    private String readTextField(String fieldName) {
        try {
            JTextField field = getField(panel.getClass(), fieldName, JTextField.class);
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

    private Field findField(Class<?> clazz, String name) {
        while (clazz != null) {
            try {
                return clazz.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private <T> T getField(Class<?> clazz, String name, Class<T> type) {
        try {
            Field field = findField(clazz, name);
            if (field == null) return null;
            field.setAccessible(true);
            Object val = field.get(panel);
            if (type.isInstance(val)) return (T) val;
        } catch (Exception ignored) {}
        return null;
    }

    private void speak(String text) {
        SpeechOutput speech = AccessibilityManager.getInstance().getSpeech();
        if (speech != null) speech.speak(text);
    }
}
