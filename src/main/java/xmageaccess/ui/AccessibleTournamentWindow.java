package xmageaccess.ui;

import xmageaccess.AccessibilityManager;
import xmageaccess.speech.SpeechOutput;
import xmageaccess.util.Log;

import static xmageaccess.util.ReflectionUtils.*;

import javax.swing.*;
import javax.swing.table.TableModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FocusTraversalPolicy;
import java.awt.KeyboardFocusManager;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Accessible tournament window: standings, match results and the tournament
 * chat, as lists you Tab between, plus a box to type into the chat.
 *
 * <p>Watching a match repeats what XMage's own action button does. Its
 * matches model carries three columns past the five it displays — table id,
 * match id, game id — and the button hands the table id to
 * {@code SessionHandler.watchTournamentTable(UUID)} when the state starts
 * with "Dueling" and the action cell reads "Watch". This window reads the
 * same model rows, so a match is watchable here exactly when it is watchable
 * there.
 *
 * <p>Chat goes through {@link ChatAccessHelper}, which also announces
 * incoming messages by itself. The input box is this window's own: handing
 * focus to XMage's chat field from another window does not work.
 *
 * Shortcuts:
 *   Tab/Shift+Tab - switch between Standings, Matches and Chat
 *   Up/Down       - move through a list
 *   Enter         - watch the selected match (in Matches)
 *   Ctrl+R        - read name, type and state
 *   Ctrl+M        - jump to the chat box
 *   Ctrl+F1       - read all shortcuts
 *   Escape        - back to XMage's own window
 */
public class AccessibleTournamentWindow extends JFrame {

    private static final String SESSION_HANDLER = "mage.client.SessionHandler";

    /** Columns the matches model holds past the ones it shows. */
    private static final int MATCH_COL_STATE = 2;
    private static final int MATCH_COL_ACTION = 4;
    private static final int MATCH_COL_TABLE_ID = 5;

    private static final int CHAT_LINES = 20;

    private final Component panel;

    private final ZoneListPanel standingsZone;
    private final ZoneListPanel matchesZone;
    private final ZoneListPanel chatZone;
    private final List<ZoneListPanel> allZones = new ArrayList<>();

    private final JTextField chatInput;
    private final JButton chatSendButton;

    private JTable tablePlayers;
    private TableModel matchesModel;
    private ChatAccessHelper chatHelper;

    private Timer pollTimer;
    private String lastState = "";

    public AccessibleTournamentWindow(Component panel) {
        super("XMage Accessible Tournament");
        this.panel = panel;

        standingsZone = new ZoneListPanel("Standings");
        matchesZone = new ZoneListPanel("Matches");
        chatZone = new ZoneListPanel("Chat");
        allZones.add(standingsZone);
        allZones.add(matchesZone);
        allZones.add(chatZone);

        chatInput = new JTextField();
        chatInput.getAccessibleContext().setAccessibleName("Tournament chat message");
        chatSendButton = new JButton("Send");

        buildUI();
        discoverComponents();
        bindKeys();
        startPolling();
    }

    private void buildUI() {
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        setSize(640, 720);
        setLocationRelativeTo(null);

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        for (ZoneListPanel zone : allZones) {
            zone.setPreferredSize(new Dimension(620, 220));
            zone.setMaximumSize(new Dimension(Integer.MAX_VALUE, 320));
            mainPanel.add(zone);
        }

        JPanel chatBar = new JPanel(new BorderLayout());
        chatBar.setBorder(BorderFactory.createTitledBorder("Send Tournament Chat Message"));
        chatBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));
        chatInput.setToolTipText("Type a message and press Enter or click Send");
        chatBar.add(chatInput, BorderLayout.CENTER);
        chatBar.add(chatSendButton, BorderLayout.EAST);
        chatSendButton.addActionListener(e -> sendChatMessage());
        chatInput.addActionListener(e -> sendChatMessage());

        JPanel outer = new JPanel(new BorderLayout());
        outer.add(new JScrollPane(mainPanel), BorderLayout.CENTER);
        outer.add(chatBar, BorderLayout.SOUTH);
        add(outer, BorderLayout.CENTER);

        setFocusCycleRoot(true);
        final List<Component> focusOrder = new ArrayList<>();
        focusOrder.add(standingsZone.getList());
        focusOrder.add(matchesZone.getList());
        focusOrder.add(chatZone.getList());
        focusOrder.add(chatInput);
        setFocusTraversalPolicy(new FocusTraversalPolicy() {
            @Override
            public Component getComponentAfter(Container container, Component component) {
                int idx = focusOrder.indexOf(component);
                if (idx < 0) return focusOrder.get(0);
                return focusOrder.get((idx + 1) % focusOrder.size());
            }

            @Override
            public Component getComponentBefore(Container container, Component component) {
                int idx = focusOrder.indexOf(component);
                if (idx < 0) return focusOrder.get(focusOrder.size() - 1);
                return focusOrder.get((idx - 1 + focusOrder.size()) % focusOrder.size());
            }

            @Override
            public Component getFirstComponent(Container container) {
                return focusOrder.get(0);
            }

            @Override
            public Component getLastComponent(Container container) {
                return focusOrder.get(focusOrder.size() - 1);
            }

            @Override
            public Component getDefaultComponent(Container container) {
                return focusOrder.get(0);
            }
        });
    }

    private void discoverComponents() {
        tablePlayers = findFieldTyped(panel, "tablePlayers", JTable.class);
        matchesModel = findFieldTyped(panel, "matchesModel", TableModel.class);

        if (chatHelper == null) {
            Object chatPanel = findFieldDeep(panel, "chatPanel1");
            if (chatPanel != null) {
                chatHelper = new ChatAccessHelper(chatPanel);
                chatHelper.attach();
            }
        }

        System.out.println("[XMage Access] Tournament window - players: " + (tablePlayers != null)
                + ", matches: " + (matchesModel != null)
                + ", chat: " + (chatHelper != null));
    }

    private void bindKeys() {
        for (ZoneListPanel zone : allZones) {
            JList<ZoneItem> list = zone.getList();
            list.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "activateItem");
            list.getActionMap().put("activateItem", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    activateSelectedItem();
                }
            });
        }

        InputMap windowInput = getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap windowAction = getRootPane().getActionMap();

        windowInput.put(KeyStroke.getKeyStroke(KeyEvent.VK_R, KeyEvent.CTRL_DOWN_MASK), "readStatus");
        windowAction.put("readStatus", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                speak(tournamentStatus() + standingsZone.getList().getModel().getSize() + " players.");
            }
        });

        windowInput.put(KeyStroke.getKeyStroke(KeyEvent.VK_M, KeyEvent.CTRL_DOWN_MASK), "focusChat");
        windowAction.put("focusChat", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                SwingUtilities.invokeLater(() -> chatInput.requestFocusInWindow());
                speak("Tournament chat input. Type your message and press Enter to send.");
            }
        });

        windowInput.put(KeyStroke.getKeyStroke(KeyEvent.VK_F1, KeyEvent.CTRL_DOWN_MASK), "readHelp");
        windowAction.put("readHelp", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                readHelp();
            }
        });

        windowInput.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "returnFocus");
        windowAction.put("returnFocus", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (xmageaccess.util.UiUtils.focusXMageWindow(AccessibleTournamentWindow.this)) {
                    speak("Returned to XMage.");
                }
            }
        });
    }

    // ========== LIFECYCLE ==========

    private void startPolling() {
        pollTimer = new Timer(3000, e -> {
            try {
                if (!panel.isVisible()) {
                    stopPolling();
                    dispose();
                    speak("Tournament closed.");
                    return;
                }
                discoverComponents();
                refreshZones();

                String state = readTextField("txtTournamentState");
                if (state != null && !state.equals(lastState) && !lastState.isEmpty()) {
                    speak("Tournament state: " + state);
                }
                if (state != null) lastState = state;
            } catch (Exception ex) {
                Log.debug("Tournament", "poll error: " + ex);
            }
        });
        pollTimer.setRepeats(true);
        pollTimer.start();
    }

    public void stopPolling() {
        if (pollTimer != null) {
            pollTimer.stop();
            pollTimer = null;
        }
    }

    @Override
    public void dispose() {
        stopPolling();
        if (chatHelper != null) {
            chatHelper.detach();
            chatHelper = null;
        }
        super.dispose();
    }

    /** Opening announcement, and focus into the standings. */
    public void announceWelcome() {
        refreshZones();
        speak("Tournament. " + tournamentStatus()
                + standingsZone.getList().getModel().getSize() + " players. "
                + "Tab between standings, matches and chat, Enter to watch a "
                + "match. Ctrl+F1 for all shortcuts.");
        SwingUtilities.invokeLater(() -> standingsZone.getList().requestFocusInWindow());
    }

    // ========== REFRESH ==========

    private void refreshZones() {
        standingsZone.updateItems(readStandings());
        matchesZone.updateItems(readMatches());
        chatZone.updateItems(readChat());
    }

    List<ZoneItem> readStandings() {
        List<ZoneItem> items = new ArrayList<>();
        if (tablePlayers == null) return items;
        for (int row = 0; row < tablePlayers.getRowCount(); row++) {
            StringBuilder sb = new StringBuilder();
            for (int col = 0; col < tablePlayers.getColumnCount(); col++) {
                Object value = tablePlayers.getValueAt(row, col);
                // The country column holds an icon and reads as nothing
                if (value == null || value instanceof Icon) continue;
                String text = value.toString().trim();
                if (text.isEmpty()) continue;
                String colName = tablePlayers.getColumnName(col);
                if (colName != null && !colName.isEmpty()) sb.append(colName).append(": ");
                sb.append(text).append(", ");
            }
            items.add(new ZoneItem(sb.length() > 0 ? sb.toString() : "Empty row",
                    null, null, ZoneItem.ActionType.NONE));
        }
        return items;
    }

    /**
     * Reads the model rather than the table so the hidden id columns come
     * along; a row is only watchable when its action cell says so.
     */
    List<ZoneItem> readMatches() {
        List<ZoneItem> items = new ArrayList<>();
        if (matchesModel == null) return items;
        for (int row = 0; row < matchesModel.getRowCount(); row++) {
            String players = cell(row, 1);
            String state = cell(row, MATCH_COL_STATE);
            String result = cell(row, 3);
            boolean watchable = "Watch".equals(cell(row, MATCH_COL_ACTION));

            StringBuilder label = new StringBuilder("Round ").append(cell(row, 0));
            if (!players.isEmpty()) label.append(": ").append(players);
            if (!state.isEmpty()) label.append(", ").append(state);
            if (!result.isEmpty()) label.append(", ").append(result);
            if (watchable) label.append(", can be watched");

            items.add(new ZoneItem(label.toString(), null, cell(row, MATCH_COL_TABLE_ID),
                    watchable ? ZoneItem.ActionType.WATCH_MATCH : ZoneItem.ActionType.NONE));
        }
        return items;
    }

    private String cell(int row, int col) {
        try {
            Object value = matchesModel.getValueAt(row, col);
            return value != null ? value.toString().trim() : "";
        } catch (Exception e) {
            return "";
        }
    }

    private List<ZoneItem> readChat() {
        List<ZoneItem> items = new ArrayList<>();
        if (chatHelper == null) return items;
        for (String line : chatHelper.getRecentLines(CHAT_LINES)) {
            items.add(new ZoneItem(line, null, null, ZoneItem.ActionType.NONE));
        }
        return items;
    }

    // ========== ACTIONS ==========

    private void activateSelectedItem() {
        ZoneListPanel zone = getFocusedZone();
        if (zone == null) return;
        ZoneItem item = zone.getSelectedItem();
        if (item == null) {
            speak("Nothing selected.");
            return;
        }
        if (item.getActionType() == ZoneItem.ActionType.WATCH_MATCH) {
            watchMatch(item);
        } else if (zone == matchesZone) {
            speak("This match cannot be watched.");
        }
    }

    void watchMatch(ZoneItem item) {
        Object tableId = item.getSourceObject();
        if (tableId == null) {
            speak("This match has no table.");
            return;
        }
        try {
            UUID id = UUID.fromString(tableId.toString());
            if (callStaticVoid(SESSION_HANDLER, "watchTournamentTable",
                    new Class[]{UUID.class}, id)) {
                speak("Watching the match.");
            } else {
                speak("Could not watch the match.");
            }
        } catch (IllegalArgumentException e) {
            speak("Could not watch the match.");
        }
    }

    private void sendChatMessage() {
        String text = chatInput.getText();
        if (text == null || text.trim().isEmpty()) {
            speak("Nothing to send.");
            return;
        }
        if (chatHelper == null) {
            speak("Chat not available.");
            return;
        }
        chatHelper.sendMessage(text.trim());
        chatInput.setText("");
    }

    // ========== STATUS ==========

    private String tournamentStatus() {
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
        JTextField field = findFieldTyped(panel, fieldName, JTextField.class);
        return field != null ? field.getText() : null;
    }

    private void readHelp() {
        speak("Tournament shortcuts. Tab and Shift+Tab move between standings, "
                + "matches, chat and the chat box. Up and Down move through a "
                + "list. Enter watches the selected match, when it can be "
                + "watched. Ctrl+R reads name, type and state. Ctrl+M jumps to "
                + "the chat box. Escape returns to XMage.");
    }

    // ========== HELPERS ==========

    private ZoneListPanel getFocusedZone() {
        Component focused = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if (focused == null) return null;
        for (ZoneListPanel zone : allZones) {
            if (zone.getList() == focused || SwingUtilities.isDescendingFrom(focused, zone)) {
                return zone;
            }
        }
        return null;
    }

    private void speak(String text) {
        SpeechOutput speech = AccessibilityManager.getInstance().getSpeech();
        if (speech != null) speech.speak(text);
    }
}
