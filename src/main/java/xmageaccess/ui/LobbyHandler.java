package xmageaccess.ui;

import xmageaccess.AccessibilityManager;
import xmageaccess.speech.SpeechOutput;

import javax.swing.*;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Accessibility handler for the XMage Lobby (TablesPanel).
 * Provides keyboard-driven navigation of game tables with speech output.
 *
 * Keyboard shortcuts (active when lobby is focused):
 *   Ctrl+G - List available games
 *   Ctrl+Up/Down - Navigate between games
 *   Ctrl+J - Join selected game
 *   Ctrl+W - Watch selected game
 *   Ctrl+N - Create new table
 *   Ctrl+R - Read current game details
 *   Ctrl+P - List online players
 *   Ctrl+I - Announce lobby info summary
 *   Ctrl+M - Focus chat input (type and Enter to send)
 *   Ctrl+Shift+M - Read last 5 chat messages
 */
public class LobbyHandler {

    private final Component lobbyPanel;
    private JTable activeTable;
    private JTable completedTable;
    private JTable playersTable;
    private int currentGameIndex = -1;
    private KeyListener keyListener;
    private ChatAccessHelper chatHelper;

    // Column indices for the active games table (TablesTableModel)
    private static final int COL_MATCH_TYPE = 0;  // Icon: Match/Tourney
    private static final int COL_DECK_TYPE = 1;
    private static final int COL_NAME = 2;
    private static final int COL_SEATS = 3;
    private static final int COL_OWNER = 4;
    private static final int COL_GAME_TYPE = 5;
    private static final int COL_INFO = 6;
    private static final int COL_STATUS = 7;
    private static final int COL_PASSWORD = 8;
    private static final int COL_SKILL = 10;
    private static final int COL_RATED = 11;
    private static final int COL_ACTION = 14;

    public LobbyHandler(Component lobbyPanel) {
        this.lobbyPanel = lobbyPanel;
    }

    public void attach() {
        attachSilent();
        announceWelcome();
    }

    /**
     * Attach keyboard navigation without announcing. Used when the lobby
     * is detected early (behind the connect dialog).
     */
    public void attachSilent() {
        try {
            discoverComponents();
            addKeyboardNavigation();
        } catch (Exception e) {
            System.err.println("[XMage Access] Error attaching to lobby: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Announce the lobby welcome message with shortcuts.
     * Called separately so it can be timed after the connect dialog closes.
     */
    public void announceWelcome() {
        // Re-discover components in case table data has changed since initial attach
        try {
            discoverComponents();
        } catch (Exception e) {
            // Non-fatal, we already have references from attachSilent
        }

        speak("Lobby. "
                + "Ctrl+G to list games, "
                + "Ctrl+Up and Down to navigate, "
                + "Ctrl+J to join, "
                + "Ctrl+N to create a new game, "
                + "Ctrl+E for deck editor, "
                + "Ctrl+D to download images, "
                + "Ctrl+I for info.");
    }

    /**
     * Creates the accessible lobby window using the tables already discovered.
     * Called by UIWatcher after attachSilent().
     */
    public AccessibleLobbyWindow createAccessibleWindow() {
        return new AccessibleLobbyWindow(lobbyPanel, activeTable, playersTable, chatHelper);
    }

    public void detach() {
        if (keyListener != null && lobbyPanel != null) {
            lobbyPanel.removeKeyListener(keyListener);
            // Also remove from tables
            if (activeTable != null) activeTable.removeKeyListener(keyListener);
        }
    }

    private void discoverComponents() throws Exception {
        // TablesPanel contains the game tables directly
        activeTable = findFieldValue(lobbyPanel, "tableTables", JTable.class);
        completedTable = findFieldValue(lobbyPanel, "tableCompleted", JTable.class);

        // Players table and chat are nested inside chatPanelMain (PlayersChatPanel)
        Object chatPanel = findFieldValue(lobbyPanel, "chatPanelMain", Object.class);
        if (chatPanel != null) {
            playersTable = findFieldValue(chatPanel, "jTablePlayers", JTable.class);

            // Get the user chat panel (ChatPanelSeparated -> ChatPanelBasic)
            if (chatHelper == null) {
                Object userChat = callMethodOn(chatPanel, "getUserChatPanel");
                if (userChat != null) {
                    chatHelper = new ChatAccessHelper(userChat);
                    chatHelper.attach();
                    System.out.println("[XMage Access] Lobby chat helper attached.");
                }
            }
        }

        if (activeTable != null) {
            System.out.println("[XMage Access] Found active games table with "
                    + activeTable.getRowCount() + " games.");
        }
        if (playersTable != null) {
            System.out.println("[XMage Access] Found players table.");
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T findFieldValue(Object target, String fieldName, Class<T> type) {
        try {
            Class<?> clazz = target.getClass();
            while (clazz != null) {
                try {
                    Field field = clazz.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    Object obj = field.get(target);
                    if (type.isInstance(obj)) {
                        return (T) obj;
                    }
                } catch (NoSuchFieldException e) {
                    // Try parent class
                    clazz = clazz.getSuperclass();
                }
            }
        } catch (Exception e) {
            System.err.println("[XMage Access] Could not find field: " + fieldName);
        }
        return null;
    }

    private void addKeyboardNavigation() {
        keyListener = new KeyListener() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (!e.isControlDown() || e.isShiftDown()) return;

                switch (e.getKeyCode()) {
                    case KeyEvent.VK_G:
                        listGames();
                        e.consume();
                        break;
                    case KeyEvent.VK_UP:
                        navigateGame(-1);
                        e.consume();
                        break;
                    case KeyEvent.VK_DOWN:
                        navigateGame(1);
                        e.consume();
                        break;
                    case KeyEvent.VK_J:
                        joinSelectedGame();
                        e.consume();
                        break;
                    case KeyEvent.VK_W:
                        watchSelectedGame();
                        e.consume();
                        break;
                    case KeyEvent.VK_N:
                        createNewTable();
                        e.consume();
                        break;
                    case KeyEvent.VK_R:
                        readCurrentGame();
                        e.consume();
                        break;
                    case KeyEvent.VK_P:
                        listPlayers();
                        e.consume();
                        break;
                    case KeyEvent.VK_I:
                        announceLobbyInfo();
                        e.consume();
                        break;
                }
            }

            @Override
            public void keyTyped(KeyEvent e) {}

            @Override
            public void keyReleased(KeyEvent e) {}
        };

        // Add the listener globally using a toolkit listener
        // so it works regardless of which component has focus
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addKeyEventDispatcher(e -> {
                    if (e.getID() != KeyEvent.KEY_PRESSED) return false;
                    if (!e.isControlDown()) return false;
                    if (!isLobbyVisible()) return false;
                    // Don't intercept keys when deck editor or sideboarding window is open
                    if (AccessibleDeckEditorWindow.isAnyWindowVisible()) return false;
                    if (SideboardingHandler.isAnyWindowVisible()) return false;

                    // Ctrl+Shift shortcuts
                    if (e.isShiftDown()) {
                        switch (e.getKeyCode()) {
                            case KeyEvent.VK_M:
                                readChat();
                                return true;
                        }
                        return false;
                    }

                    // Ctrl (no shift) shortcuts
                    switch (e.getKeyCode()) {
                        case KeyEvent.VK_G:
                            listGames();
                            return true;
                        case KeyEvent.VK_UP:
                            navigateGame(-1);
                            return true;
                        case KeyEvent.VK_DOWN:
                            navigateGame(1);
                            return true;
                        case KeyEvent.VK_J:
                            joinSelectedGame();
                            return true;
                        case KeyEvent.VK_W:
                            watchSelectedGame();
                            return true;
                        case KeyEvent.VK_N:
                            createNewTable();
                            return true;
                        case KeyEvent.VK_R:
                            readCurrentGame();
                            return true;
                        case KeyEvent.VK_P:
                            listPlayers();
                            return true;
                        case KeyEvent.VK_I:
                            announceLobbyInfo();
                            return true;
                        case KeyEvent.VK_E:
                            openDeckEditor();
                            return true;
                        case KeyEvent.VK_D:
                            openDownloadImages();
                            return true;
                        case KeyEvent.VK_M:
                            focusChatInput();
                            return true;
                    }
                    return false;
                });
    }

    private boolean isLobbyVisible() {
        if (lobbyPanel == null) return false;

        // Check if the lobby panel is visible. XMage uses JDesktopPane/JLayeredPane
        // so isShowing() may not work reliably. Fall back to isVisible() and check
        // that no modal dialog is on top.
        if (!lobbyPanel.isVisible()) return false;

        // Check if the lobby's parent hierarchy is displayable
        Container parent = lobbyPanel.getParent();
        while (parent != null) {
            if (!parent.isVisible()) return false;
            parent = parent.getParent();
        }
        return true;
    }

    /**
     * List all available games with a summary.
     */
    private void listGames() {
        if (activeTable == null) {
            speak("No games table found.");
            return;
        }

        int rowCount = activeTable.getRowCount();
        if (rowCount == 0) {
            speak("No games available.");
            return;
        }

        // Count waiting vs active games
        int waiting = 0;
        int active = 0;
        for (int i = 0; i < rowCount; i++) {
            String status = getTableValue(activeTable, i, COL_STATUS);
            if (status != null && status.toLowerCase().contains("waiting")) {
                waiting++;
            } else {
                active++;
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append(rowCount).append(" games. ");
        if (waiting > 0) sb.append(waiting).append(" waiting for players. ");
        if (active > 0) sb.append(active).append(" in progress. ");
        sb.append("Use Ctrl+Down to browse.");

        speak(sb.toString());

        // Auto-select first game if none selected
        if (currentGameIndex < 0 && rowCount > 0) {
            currentGameIndex = 0;
        }
    }

    /**
     * Navigate up or down in the games list.
     */
    private void navigateGame(int direction) {
        if (activeTable == null) {
            speak("No games table found.");
            return;
        }

        int rowCount = activeTable.getRowCount();
        if (rowCount == 0) {
            speak("No games available.");
            return;
        }

        currentGameIndex += direction;
        if (currentGameIndex < 0) {
            currentGameIndex = 0;
            speak("At the first game.");
            return;
        }
        if (currentGameIndex >= rowCount) {
            currentGameIndex = rowCount - 1;
            speak("At the last game.");
            return;
        }

        // Select the row in the actual table
        activeTable.setRowSelectionInterval(currentGameIndex, currentGameIndex);
        activeTable.scrollRectToVisible(activeTable.getCellRect(currentGameIndex, 0, true));

        // Announce the game
        readGameAtRow(currentGameIndex);
    }

    /**
     * Read full details of the currently selected game.
     */
    private void readCurrentGame() {
        if (activeTable == null || currentGameIndex < 0
                || currentGameIndex >= activeTable.getRowCount()) {
            speak("No game selected. Press Ctrl+G to list games.");
            return;
        }
        readGameAtRow(currentGameIndex);
    }

    /**
     * Build and speak a description of a game table row.
     */
    private void readGameAtRow(int row) {
        StringBuilder sb = new StringBuilder();

        // Game number for orientation
        sb.append("Game ").append(row + 1).append(" of ").append(activeTable.getRowCount()).append(". ");

        // Match type (icon column - try to get string representation)
        String matchType = getMatchType(row);
        if (matchType != null) {
            sb.append(matchType).append(". ");
        }

        // Name
        String name = getTableValue(activeTable, row, COL_NAME);
        if (name != null && !name.isEmpty()) {
            sb.append(name).append(". ");
        }

        // Deck type
        String deckType = getTableValue(activeTable, row, COL_DECK_TYPE);
        if (deckType != null && !deckType.isEmpty()) {
            sb.append(deckType).append(". ");
        }

        // Game type
        String gameType = getTableValue(activeTable, row, COL_GAME_TYPE);
        if (gameType != null && !gameType.isEmpty()) {
            sb.append(gameType).append(". ");
        }

        // Seats
        String seats = getTableValue(activeTable, row, COL_SEATS);
        if (seats != null && !seats.isEmpty()) {
            sb.append("Seats: ").append(seats).append(". ");
        }

        // Owner/Players
        String owner = getTableValue(activeTable, row, COL_OWNER);
        if (owner != null && !owner.isEmpty()) {
            sb.append("By: ").append(owner).append(". ");
        }

        // Status
        String status = getTableValue(activeTable, row, COL_STATUS);
        if (status != null && !status.isEmpty()) {
            sb.append("Status: ").append(status).append(". ");
        }

        // Password
        String password = getTableValue(activeTable, row, COL_PASSWORD);
        if (password != null && !password.isEmpty()) {
            sb.append("Password required. ");
        }

        // Skill level (convert star icons to text)
        String skill = getSkillLevel(row);
        if (skill != null) {
            sb.append(skill).append(". ");
        }

        // Rated
        String rated = getTableValue(activeTable, row, COL_RATED);
        if (rated != null && !rated.isEmpty()) {
            sb.append("Rated. ");
        }

        // Info
        String info = getTableValue(activeTable, row, COL_INFO);
        if (info != null && !info.isEmpty()) {
            sb.append("Info: ").append(info).append(". ");
        }

        speak(sb.toString());
    }

    /**
     * Try to get the match type as text from the icon column.
     */
    private String getMatchType(int row) {
        try {
            Object value = activeTable.getValueAt(row, COL_MATCH_TYPE);
            if (value != null) {
                String str = value.toString();
                if (str.toLowerCase().contains("tourney") || str.toLowerCase().contains("tournament")) {
                    return "Tournament";
                }
                return "Match";
            }
        } catch (Exception e) {
            // Ignore
        }
        return "Match";
    }

    /**
     * Convert the skill column (star icons) to a readable text.
     */
    private String getSkillLevel(int row) {
        try {
            Object value = activeTable.getValueAt(row, COL_SKILL);
            if (value != null) {
                String str = value.toString().trim();
                int stars = str.length();
                switch (stars) {
                    case 1: return "Skill: Beginner";
                    case 2: return "Skill: Casual";
                    case 3: return "Skill: Serious";
                    default: return "Skill: " + str;
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }

    /**
     * Safely get a string value from a table cell.
     */
    private String getTableValue(JTable table, int row, int col) {
        try {
            if (row < 0 || row >= table.getRowCount()) return null;
            if (col < 0 || col >= table.getColumnCount()) return null;
            Object value = table.getValueAt(row, col);
            return value != null ? value.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Join the currently selected game.
     */
    private void joinSelectedGame() {
        if (activeTable == null || currentGameIndex < 0
                || currentGameIndex >= activeTable.getRowCount()) {
            speak("No game selected. Press Ctrl+G to list games first.");
            return;
        }

        String status = getTableValue(activeTable, currentGameIndex, COL_STATUS);
        if (status != null && status.toLowerCase().contains("waiting")) {
            speak("Joining game.");
            clickActionButton(currentGameIndex);
        } else {
            speak("This game is not waiting for players. Try Ctrl+W to watch instead.");
        }
    }

    /**
     * Watch the currently selected game.
     */
    private void watchSelectedGame() {
        if (activeTable == null || currentGameIndex < 0
                || currentGameIndex >= activeTable.getRowCount()) {
            speak("No game selected. Press Ctrl+G to list games first.");
            return;
        }

        speak("Watching game.");
        // The action button text changes based on game state,
        // "Watch" appears for games in progress
        clickActionButton(currentGameIndex);
    }

    /**
     * Click the action button in the selected row.
     * Uses reflection to trigger the button column's action.
     */
    private void clickActionButton(int row) {
        try {
            // Select the row first
            activeTable.setRowSelectionInterval(row, row);

            // Try to find and invoke the action button column's action
            // The TablesButtonColumn handles clicks on the action column
            Class<?> panelClass = lobbyPanel.getClass();

            // Try calling the tables panel's action directly
            // First approach: simulate editing the action cell
            int actionCol = activeTable.getColumnCount() - 1; // Action is last column
            activeTable.editCellAt(row, actionCol);

            // Alternative: try to find the button column and invoke its action
            for (Field field : panelClass.getDeclaredFields()) {
                if (field.getType().getSimpleName().equals("TablesButtonColumn")) {
                    field.setAccessible(true);
                    Object buttonColumn = field.get(lobbyPanel);
                    // Try to invoke the action
                    Method actionMethod = buttonColumn.getClass().getDeclaredMethod("doClick", int.class);
                    actionMethod.setAccessible(true);
                    actionMethod.invoke(buttonColumn, row);
                    return;
                }
            }
        } catch (Exception e) {
            System.err.println("[XMage Access] Error clicking action button: " + e.getMessage());
            speak("Could not perform action. Try double-clicking the table row.");
        }
    }

    /**
     * Create a new table/game.
     */
    private void createNewTable() {
        try {
            // Find and click the New Table button
            Class<?> panelClass = lobbyPanel.getClass();
            Field btnField = panelClass.getDeclaredField("btnNewTable");
            btnField.setAccessible(true);
            JButton btn = (JButton) btnField.get(lobbyPanel);
            if (btn != null && btn.isEnabled()) {
                speak("Opening new table dialog.");
                btn.doClick();
            } else {
                speak("New table button is not available. You may need to connect first.");
            }
        } catch (Exception e) {
            System.err.println("[XMage Access] Error creating new table: " + e.getMessage());
            speak("Could not open new table dialog.");
        }
    }

    /**
     * List online players.
     */
    private void listPlayers() {
        if (playersTable == null) {
            speak("Players list not found.");
            return;
        }

        int count = playersTable.getRowCount();
        if (count == 0) {
            speak("No players online.");
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(count).append(" players online. ");

        // Read first few player names (column 1 is the name)
        int limit = Math.min(count, 10);
        for (int i = 0; i < limit; i++) {
            String name = getTableValue(playersTable, i, 1);
            if (name != null) {
                sb.append(name);
                if (i < limit - 1) sb.append(", ");
            }
        }
        if (count > limit) {
            sb.append(", and ").append(count - limit).append(" more.");
        }

        speak(sb.toString());
    }

    /**
     * Announce a summary of the lobby state.
     */
    private void announceLobbyInfo() {
        StringBuilder sb = new StringBuilder("Lobby summary. ");

        if (activeTable != null) {
            int games = activeTable.getRowCount();
            sb.append(games).append(" games listed. ");
        }

        if (playersTable != null) {
            int players = playersTable.getRowCount();
            sb.append(players).append(" players online. ");
        }

        sb.append("Shortcuts: Ctrl+G games, Ctrl+N new game, Ctrl+P players.");
        speak(sb.toString());
    }

    /**
     * Open the deck editor via MageFrame's toolbar button.
     */
    private void openDeckEditor() {
        try {
            Object mageFrame = Class.forName("mage.client.MageFrame")
                    .getMethod("getInstance").invoke(null);
            Field btnField = mageFrame.getClass().getDeclaredField("btnDeckEditor");
            btnField.setAccessible(true);
            JButton btn = (JButton) btnField.get(mageFrame);
            if (btn != null && btn.isEnabled()) {
                speak("Opening deck editor.");
                btn.doClick();
            } else {
                speak("Deck editor is not available.");
            }
        } catch (Exception e) {
            System.err.println("[XMage Access] Error opening deck editor: " + e.getMessage());
            speak("Could not open deck editor.");
        }
    }

    /**
     * Open the download images dialog via MageFrame.
     */
    private void openDownloadImages() {
        try {
            Object mageFrame = Class.forName("mage.client.MageFrame")
                    .getMethod("getInstance").invoke(null);
            Method downloadMethod = mageFrame.getClass().getMethod("downloadImages");
            speak("Opening download images.");
            downloadMethod.invoke(mageFrame);
        } catch (Exception e) {
            System.err.println("[XMage Access] Error opening download images: " + e.getMessage());
            speak("Could not open download images dialog.");
        }
    }

    private void readChat() {
        if (chatHelper != null) {
            chatHelper.readRecentChat(5);
        } else {
            speak("Chat not available.");
        }
    }

    private void focusChatInput() {
        if (chatHelper != null) {
            chatHelper.focusInput();
        } else {
            speak("Chat not available.");
        }
    }

    private Object callMethodOn(Object obj, String methodName) {
        try {
            java.lang.reflect.Method m = obj.getClass().getMethod(methodName);
            return m.invoke(obj);
        } catch (Exception e) {
            return null;
        }
    }

    private void speak(String text) {
        SpeechOutput speech = AccessibilityManager.getInstance().getSpeech();
        if (speech != null) {
            speech.speak(text);
        }
    }
}
