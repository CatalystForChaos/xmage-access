package xmageaccess.ui;

import xmageaccess.AccessibilityManager;
import xmageaccess.speech.SpeechOutput;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Accessible lobby window with four navigable zones and a chat input bar.
 *
 * Zones (Tab between them):
 *   1. Actions      - New Game, New Tournament, Deck Editor, Download Images, Preferences
 *   2. Open Games   - every game in the lobby; Enter = join/watch, D = full detail
 *   3. Players      - all players currently online
 *   4. Chat         - last 10 messages
 *   Chat bar        - text field at the bottom; type and press Enter to send
 *
 * Keys:
 *   Tab / Shift+Tab  move between zones and chat bar
 *   Up / Down        move within a zone
 *   Enter            activate selected action or game
 *   D                read full game detail (Games zone)
 *   Escape           return focus to the main XMage window
 *
 * Auto-refreshes every 5 seconds.
 */
public class AccessibleLobbyWindow extends JFrame {

    private final Component lobbyPanel;

    private final ZoneListPanel actionsZone;
    private final ZoneListPanel gamesZone;
    private final ZoneListPanel playersZone;
    private final ZoneListPanel chatZone;

    // Chat input at the bottom
    private final JTextField chatInput;
    private final JButton chatSendButton;

    // Reflected tables and helpers
    private JTable activeTable;
    private JTable playersTable;
    private ChatAccessHelper chatHelper;

    // Maps list index -> table row for the games zone
    private final List<Integer> gameRowMap = new ArrayList<>();

    private Timer pollTimer;

    // Column indices (match LobbyHandler)
    private static final int COL_DECK_TYPE = 1;
    private static final int COL_NAME      = 2;
    private static final int COL_SEATS     = 3;
    private static final int COL_OWNER     = 4;
    private static final int COL_GAME_TYPE = 5;
    private static final int COL_INFO      = 6;
    private static final int COL_STATUS    = 7;
    private static final int COL_PASSWORD  = 8;
    private static final int COL_SKILL     = 10;
    private static final int COL_RATED     = 11;

    private static AccessibleLobbyWindow instance;

    public AccessibleLobbyWindow(Component lobbyPanel, JTable activeTable,
                                  JTable playersTable, ChatAccessHelper chatHelper) {
        super("XMage Accessible Lobby");
        this.lobbyPanel   = lobbyPanel;
        this.activeTable  = activeTable;
        this.playersTable = playersTable;
        this.chatHelper   = chatHelper;
        instance = this;

        actionsZone = new ZoneListPanel("Actions");
        gamesZone   = new ZoneListPanel("Open Games");
        playersZone = new ZoneListPanel("Players Online");
        chatZone    = new ZoneListPanel("Chat");

        chatInput      = new JTextField();
        chatSendButton = new JButton("Send");

        buildUI();
        bindKeys();
        populateActions();
        refreshGames();
        refreshPlayers();
        refreshChat();
        startPolling();
    }

    // ========== UI ==========

    private void buildUI() {
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        setSize(680, 700);
        setLocationRelativeTo(null);

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));

        actionsZone.setPreferredSize(new Dimension(660, 120));
        actionsZone.setMaximumSize(new Dimension(Integer.MAX_VALUE, 140));

        gamesZone.setPreferredSize(new Dimension(660, 240));
        gamesZone.setMaximumSize(new Dimension(Integer.MAX_VALUE, 280));

        playersZone.setPreferredSize(new Dimension(660, 120));
        playersZone.setMaximumSize(new Dimension(Integer.MAX_VALUE, 160));

        chatZone.setPreferredSize(new Dimension(660, 120));
        chatZone.setMaximumSize(new Dimension(Integer.MAX_VALUE, 160));

        mainPanel.add(actionsZone);
        mainPanel.add(gamesZone);
        mainPanel.add(playersZone);
        mainPanel.add(chatZone);

        // Chat input bar at the bottom
        JPanel chatBar = new JPanel(new BorderLayout(4, 0));
        chatBar.setBorder(BorderFactory.createTitledBorder("Send Chat Message"));
        chatBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));
        chatInput.setToolTipText("Type a message and press Enter or click Send");
        chatBar.add(chatInput, BorderLayout.CENTER);
        chatBar.add(chatSendButton, BorderLayout.EAST);

        // Wire send button and Enter key on chat field
        chatSendButton.addActionListener(e -> sendChatMessage());
        chatInput.addActionListener(e -> sendChatMessage());

        JPanel outer = new JPanel(new BorderLayout());
        outer.add(new JScrollPane(mainPanel), BorderLayout.CENTER);
        outer.add(chatBar, BorderLayout.SOUTH);
        add(outer);
    }

    private void bindKeys() {
        // Actions zone: Enter activates
        JList<ZoneItem> actionList = actionsZone.getList();
        actionList.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "doAction");
        actionList.getActionMap().put("doAction", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { activateSelectedAction(); }
        });

        // Games zone: Enter = join/watch, D = detail
        JList<ZoneItem> gameList = gamesZone.getList();
        gameList.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "joinGame");
        gameList.getActionMap().put("joinGame", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { joinOrWatchSelected(); }
        });
        gameList.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_D, 0), "gameDetail");
        gameList.getActionMap().put("gameDetail", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { readGameDetail(); }
        });

        // Escape from anywhere returns focus to XMage
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "returnFocus");
        getRootPane().getActionMap().put("returnFocus", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { returnFocusToXMage(); }
        });
    }

    // ========== ACTIONS ZONE ==========

    private void populateActions() {
        List<ZoneItem> items = new ArrayList<>();
        items.add(new ZoneItem("New Game",           "Create a new match table",       "newGame",       ZoneItem.ActionType.NONE));
        items.add(new ZoneItem("New Tournament",     "Create a new tournament table",  "newTournament", ZoneItem.ActionType.NONE));
        items.add(new ZoneItem("Open Deck Editor",   "Open the deck editor",           "deckEditor",    ZoneItem.ActionType.NONE));
        items.add(new ZoneItem("Download Images",    "Download card images",           "downloadImages",ZoneItem.ActionType.NONE));
        items.add(new ZoneItem("Preferences",        "Open client preferences/settings","preferences",  ZoneItem.ActionType.NONE));
        items.add(new ZoneItem("Connect / Disconnect","Connect to or disconnect from server","connect", ZoneItem.ActionType.NONE));
        actionsZone.updateItems(items);
    }

    private void activateSelectedAction() {
        ZoneItem item = actionsZone.getSelectedItem();
        if (item == null) { speak("No action selected."); return; }

        String key = item.getSourceObject() instanceof String ? (String) item.getSourceObject() : "";
        switch (key) {
            case "newGame":        clickLobbyButton("btnNewTable");      break;
            case "newTournament":  clickLobbyButton("btnNewTournament"); break;
            case "deckEditor":     clickMageFrameButton("btnDeckEditor");break;
            case "downloadImages": callMageFrameMethod("downloadImages");break;
            case "preferences":    clickMageFrameButton("btnPreferences");break;
            case "connect":        clickMageFrameButton("btnConnect");   break;
            default: speak("Unknown action.");
        }
    }

    private void clickLobbyButton(String fieldName) {
        try {
            Class<?> clazz = lobbyPanel.getClass();
            while (clazz != null) {
                try {
                    Field f = clazz.getDeclaredField(fieldName);
                    f.setAccessible(true);
                    Object btn = f.get(lobbyPanel);
                    if (btn instanceof JButton) {
                        JButton button = (JButton) btn;
                        if (button.isEnabled()) {
                            speak(button.getToolTipText() != null ? button.getToolTipText() : fieldName);
                            button.doClick();
                            return;
                        } else {
                            speak("Not available right now.");
                            return;
                        }
                    }
                } catch (NoSuchFieldException e) {
                    clazz = clazz.getSuperclass();
                }
            }
            speak("Button not found.");
        } catch (Exception e) {
            speak("Could not perform action.");
            System.err.println("[XMage Access] Error clicking " + fieldName + ": " + e.getMessage());
        }
    }

    private void clickMageFrameButton(String fieldName) {
        try {
            Class<?> mageFrameClass = Class.forName("mage.client.MageFrame");
            Object mageFrame = mageFrameClass.getMethod("getInstance").invoke(null);
            Field f = mageFrameClass.getDeclaredField(fieldName);
            f.setAccessible(true);
            Object btn = f.get(mageFrame);
            if (btn instanceof JButton) {
                JButton button = (JButton) btn;
                String label = button.getText();
                if (label == null || label.isEmpty()) label = button.getToolTipText();
                speak(label != null ? label : fieldName);
                button.doClick();
            } else {
                speak("Button not found.");
            }
        } catch (Exception e) {
            speak("Could not perform action.");
            System.err.println("[XMage Access] Error clicking MageFrame." + fieldName + ": " + e.getMessage());
        }
    }

    private void callMageFrameMethod(String methodName) {
        try {
            Class<?> mageFrameClass = Class.forName("mage.client.MageFrame");
            Object mageFrame = mageFrameClass.getMethod("getInstance").invoke(null);
            Method m = mageFrameClass.getMethod(methodName);
            speak(methodName);
            m.invoke(mageFrame);
        } catch (Exception e) {
            speak("Could not perform action.");
            System.err.println("[XMage Access] Error calling MageFrame." + methodName + ": " + e.getMessage());
        }
    }

    // ========== GAMES ZONE ==========

    private void joinOrWatchSelected() {
        int listIndex = gamesZone.getList().getSelectedIndex();
        if (listIndex < 0 || listIndex >= gameRowMap.size()) {
            speak("No game selected.");
            return;
        }
        int tableRow = gameRowMap.get(listIndex);
        if (activeTable == null || tableRow >= activeTable.getRowCount()) {
            speak("Game list has changed. Press Ctrl+G to refresh.");
            return;
        }
        String status = getCell(activeTable, tableRow, COL_STATUS);
        boolean waiting = status != null && status.toLowerCase().contains("waiting");

        try {
            activeTable.setRowSelectionInterval(tableRow, tableRow);
        } catch (IllegalArgumentException e) {
            speak("Game list has changed. Press Ctrl+G to refresh.");
            return;
        }
        speak(waiting ? "Joining game." : "Watching game.");
        clickActionButton(tableRow);
    }

    private void readGameDetail() {
        int listIndex = gamesZone.getList().getSelectedIndex();
        if (listIndex < 0 || listIndex >= gameRowMap.size()) {
            speak("No game selected.");
            return;
        }
        int tableRow = gameRowMap.get(listIndex);
        if (activeTable == null || tableRow >= activeTable.getRowCount()) {
            speak("Game list has changed. Press Ctrl+G to refresh.");
            return;
        }
        speak(buildGameDetail(tableRow));
    }

    private void clickActionButton(int viewRow) {
        try {
            // Find the TablesButtonColumn whose internal table == activeTable.
            // TablesPanel has two: actionButton1 (active games) and actionButton2 (completed).
            // We must match by the table field to get the right action.
            javax.swing.Action openAction = null;
            Class<?> clazz = lobbyPanel.getClass();
            while (clazz != null) {
                for (Field field : clazz.getDeclaredFields()) {
                    field.setAccessible(true);
                    Object val = field.get(lobbyPanel);
                    if (val == null) continue;
                    if (!val.getClass().getSimpleName().equals("TablesButtonColumn")) continue;
                    // Check that this button column belongs to our activeTable
                    try {
                        Field tableField = val.getClass().getDeclaredField("table");
                        tableField.setAccessible(true);
                        Object btnTable = tableField.get(val);
                        if (btnTable != activeTable) continue;
                    } catch (NoSuchFieldException ignored) {}
                    Field actionField = val.getClass().getDeclaredField("action");
                    actionField.setAccessible(true);
                    openAction = (javax.swing.Action) actionField.get(val);
                    System.out.println("[XMage Access] Found openTableAction via field: " + field.getName());
                    break;
                }
                if (openAction != null) break;
                clazz = clazz.getSuperclass();
            }
            if (openAction == null) {
                System.err.println("[XMage Access] openTableAction not found on lobbyPanel.");
                speak("Action not available.");
                return;
            }
            // Convert view row to model row (handles sorting)
            int modelRow = activeTable.convertRowIndexToModel(viewRow);
            System.out.println("[XMage Access] Joining: viewRow=" + viewRow + " modelRow=" + modelRow);
            // Get the search ID (tableUUID;gameUUID) via TablesUtil
            Class<?> util = Class.forName("mage.client.table.TablesUtil");
            Method getSearchId = util.getMethod("getSearchIdFromTable", JTable.class, int.class);
            String searchId = (String) getSearchId.invoke(null, activeTable, modelRow);
            System.out.println("[XMage Access] searchId=" + searchId);
            if (searchId == null) {
                speak("Could not get game information.");
                return;
            }
            // Fire the action exactly as the double-click listener does
            openAction.actionPerformed(new ActionEvent(activeTable, ActionEvent.ACTION_PERFORMED, searchId));
        } catch (Exception e) {
            System.err.println("[XMage Access] Error clicking game action: " + e.getMessage());
            e.printStackTrace();
            speak("Could not perform action.");
        }
    }

    // ========== CHAT ==========

    private void sendChatMessage() {
        String text = chatInput.getText().trim();
        if (text.isEmpty()) {
            speak("Message is empty.");
            return;
        }
        if (chatHelper != null) {
            chatHelper.sendMessage(text);
            chatInput.setText("");
        } else {
            speak("Chat not available.");
        }
    }

    // ========== REFRESH ==========

    private void startPolling() {
        pollTimer = new Timer(5000, e -> {
            try {
                if (!lobbyPanel.isVisible()) {
                    stopPolling();
                    setVisible(false);
                    return;
                }
                refreshGames();
                refreshPlayers();
                refreshChat();
            } catch (Exception ex) {
                // Don't crash the timer
            }
        });
        pollTimer.setRepeats(true);
        pollTimer.start();
    }

    public void stopPolling() {
        if (pollTimer != null) pollTimer.stop();
    }

    public void updateReferences(JTable activeTable, JTable playersTable, ChatAccessHelper chatHelper) {
        this.activeTable  = activeTable;
        this.playersTable = playersTable;
        this.chatHelper   = chatHelper;
    }

    private void refreshGames() {
        gameRowMap.clear();
        List<ZoneItem> items = new ArrayList<>();

        if (activeTable == null || activeTable.getRowCount() == 0) {
            items.add(new ZoneItem("No games available", null, null, ZoneItem.ActionType.NONE));
            gamesZone.updateItems(items);
            return;
        }

        for (int row = 0; row < activeTable.getRowCount(); row++) {
            String brief  = buildGameBrief(row);
            String detail = buildGameDetail(row);
            items.add(new ZoneItem(brief, detail, null, ZoneItem.ActionType.NONE));
            gameRowMap.add(row);
        }
        gamesZone.updateItems(items);
    }

    private void refreshPlayers() {
        List<ZoneItem> items = new ArrayList<>();

        if (playersTable == null || playersTable.getRowCount() == 0) {
            items.add(new ZoneItem("No players online", null, null, ZoneItem.ActionType.NONE));
            playersZone.updateItems(items);
            return;
        }

        for (int row = 0; row < playersTable.getRowCount(); row++) {
            String name = getCell(playersTable, row, 1);
            if (name != null && !name.isEmpty()) {
                items.add(new ZoneItem(name, null, null, ZoneItem.ActionType.NONE));
            }
        }
        if (items.isEmpty()) {
            items.add(new ZoneItem("No players online", null, null, ZoneItem.ActionType.NONE));
        }
        playersZone.updateItems(items);
    }

    private void refreshChat() {
        List<ZoneItem> items = new ArrayList<>();

        if (chatHelper == null) {
            items.add(new ZoneItem("Chat not available", null, null, ZoneItem.ActionType.NONE));
            chatZone.updateItems(items);
            return;
        }

        List<String> lines = chatHelper.getRecentLines(10);
        if (lines.isEmpty()) {
            items.add(new ZoneItem("No messages yet", null, null, ZoneItem.ActionType.NONE));
        } else {
            for (String line : lines) {
                items.add(new ZoneItem(line, null, null, ZoneItem.ActionType.NONE));
            }
        }
        chatZone.updateItems(items);
    }

    // ========== GAME TEXT BUILDERS ==========

    private String buildGameBrief(int row) {
        StringBuilder sb = new StringBuilder();
        String name = getCell(activeTable, row, COL_NAME);
        sb.append(name != null && !name.isEmpty() ? name : "Unnamed");

        String gameType = getCell(activeTable, row, COL_GAME_TYPE);
        if (gameType != null && !gameType.isEmpty()) sb.append(", ").append(gameType);

        String status = getCell(activeTable, row, COL_STATUS);
        if (status != null && !status.isEmpty()) sb.append(", ").append(status);

        String seats = getCell(activeTable, row, COL_SEATS);
        if (seats != null && !seats.isEmpty()) sb.append(", seats: ").append(seats);

        return sb.toString();
    }

    private String buildGameDetail(int row) {
        StringBuilder sb = new StringBuilder();
        sb.append("Game ").append(row + 1).append(" of ").append(activeTable.getRowCount()).append(". ");

        String name = getCell(activeTable, row, COL_NAME);
        if (name != null && !name.isEmpty()) sb.append(name).append(". ");

        String deckType = getCell(activeTable, row, COL_DECK_TYPE);
        if (deckType != null && !deckType.isEmpty()) sb.append(deckType).append(". ");

        String gameType = getCell(activeTable, row, COL_GAME_TYPE);
        if (gameType != null && !gameType.isEmpty()) sb.append(gameType).append(". ");

        String seats = getCell(activeTable, row, COL_SEATS);
        if (seats != null && !seats.isEmpty()) sb.append("Seats: ").append(seats).append(". ");

        String owner = getCell(activeTable, row, COL_OWNER);
        if (owner != null && !owner.isEmpty()) sb.append("By: ").append(owner).append(". ");

        String status = getCell(activeTable, row, COL_STATUS);
        if (status != null && !status.isEmpty()) sb.append("Status: ").append(status).append(". ");

        String password = getCell(activeTable, row, COL_PASSWORD);
        if (password != null && !password.isEmpty()) sb.append("Password required. ");

        String skill = getSkillLevel(row);
        if (skill != null) sb.append(skill).append(". ");

        String info = getCell(activeTable, row, COL_INFO);
        if (info != null && !info.isEmpty()) sb.append("Info: ").append(info).append(". ");

        String rated = getCell(activeTable, row, COL_RATED);
        if (rated != null && !rated.isEmpty()) sb.append("Rated. ");

        return sb.toString();
    }

    private String getSkillLevel(int row) {
        try {
            Object value = activeTable.getValueAt(row, COL_SKILL);
            if (value == null) return null;
            String str = value.toString().trim();
            if (str.isEmpty()) return null;
            switch (str.length()) {
                case 1:  return "Skill: Beginner";
                case 2:  return "Skill: Casual";
                case 3:  return "Skill: Serious";
                default: return "Skill: " + str;
            }
        } catch (Exception e) {
            return null;
        }
    }

    // ========== MISC ==========

    private void returnFocusToXMage() {
        for (Window w : Window.getWindows()) {
            if (w.isVisible() && !(w instanceof AccessibleLobbyWindow)) {
                w.toFront();
                w.requestFocus();
                break;
            }
        }
    }

    private String getCell(JTable table, int row, int col) {
        try {
            if (table == null) return null;
            if (row < 0 || row >= table.getRowCount()) return null;
            if (col < 0 || col >= table.getColumnCount()) return null;
            Object val = table.getValueAt(row, col);
            return val != null ? val.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void speak(String text) {
        SpeechOutput speech = AccessibilityManager.getInstance().getSpeech();
        if (speech != null) speech.speak(text);
    }

    public static boolean isAnyWindowVisible() {
        return instance != null && instance.isVisible();
    }
}
