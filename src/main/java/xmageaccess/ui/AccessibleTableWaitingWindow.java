package xmageaccess.ui;

import xmageaccess.AccessibilityManager;
import xmageaccess.speech.SpeechOutput;
import xmageaccess.util.Log;
import xmageaccess.util.UiUtils;

import static xmageaccess.util.ReflectionUtils.*;

import javax.swing.*;
import javax.swing.table.TableModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FocusTraversalPolicy;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Accessible waiting room: the window for XMage's TableWaitingDialog, where a
 * table waits for its players before a match or a tournament starts. XMage
 * shows the seats as a table with Start and Cancel under it, and gives a
 * screen reader little to go on. Here the table's standing, the seats, what
 * can be done and the chat are lists you Tab between, like the lobby window.
 *
 * <p>Everything is read off the dialog itself. The seats come from its table
 * model, {@code TableWaitModel}, by model column, so a column moved in XMage's
 * view is still found. Game type and deck type come from the dialog's title,
 * which XMage sets to "Waiting for players - deck type / game type" once the
 * first table update has arrived. Whether the table is ready is XMage's own
 * Start button being enabled, which {@code update} does exactly for
 * READY_TO_START; whether the user owns the table is the same button being
 * visible, which {@code showDialog} decides with
 * {@code SessionHandler.isTableOwner}. Start and Leave press XMage's Start
 * and Cancel, so they do exactly what a click does.
 *
 * <p>The window says who joins and who leaves, and tells the table owner when
 * the table becomes ready: the moments XMage marks with a sound and a tray
 * message. XMage's Move Up and Move Down, which swap seats, are not offered.
 *
 * Keys:
 *   Tab/Shift+Tab - move between Table, Seats, Actions, Chat and the chat box
 *   Up/Down       - move through a list
 *   Enter         - act on a row in Actions; elsewhere read the row in full
 *   D             - read the row in full
 *   Escape        - back to XMage's own window
 */
public class AccessibleTableWaitingWindow extends JFrame {

    /** How XMage's title begins once the table has been read; the types follow. */
    static final String TITLE_PREFIX = "Waiting for players - ";
    static final String TITLE_SEPARATOR = " / ";

    /** TableWaitModel's columns: Seat, Loc, Player Name, Rating, Player Type, History. */
    private static final int COL_SEAT = 0;
    private static final int COL_NAME = 2;
    private static final int COL_RATING = 3;
    private static final int COL_TYPE = 4;
    private static final int COL_HISTORY = 5;

    private static final String START = "start";
    private static final String LEAVE = "leave";

    private static final int CHAT_LINES = 20;

    private final Component dialog;

    private final ZoneListPanel tableZone;
    private final ZoneListPanel seatsZone;
    private final ZoneListPanel actionsZone;
    private final ZoneListPanel chatZone;
    private final List<ZoneListPanel> allZones = new ArrayList<>();

    private final JTextField chatInput;
    private final JButton chatSendButton;

    private JButton btnStart;
    private JButton btnCancel;
    private JTable seatsTable;
    private boolean tournament;
    private ChatAccessHelper chatHelper;

    private Timer pollTimer;
    private List<String> lastPlayers;
    private boolean lastReady;
    private boolean leaving;

    public AccessibleTableWaitingWindow(Component dialog) {
        super("XMage Accessible Waiting Room");
        this.dialog = dialog;

        tableZone = new ZoneListPanel("Table");
        seatsZone = new ZoneListPanel("Seats");
        actionsZone = new ZoneListPanel("Actions");
        chatZone = new ZoneListPanel("Chat");
        allZones.add(tableZone);
        allZones.add(seatsZone);
        allZones.add(actionsZone);
        allZones.add(chatZone);

        chatInput = new JTextField();
        chatInput.getAccessibleContext().setAccessibleName("Table chat message");
        chatSendButton = new JButton("Send");

        buildUI();
        discoverComponents();
        bindKeys();
        lastPlayers = readPlayers();
        lastReady = isReady();
        refreshZones();
        startPolling();
    }

    private void buildUI() {
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        setSize(640, 720);
        setLocationRelativeTo(null);

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        for (ZoneListPanel zone : allZones) {
            zone.setPreferredSize(new Dimension(620, 160));
            zone.setMaximumSize(new Dimension(Integer.MAX_VALUE, 260));
            mainPanel.add(zone);
        }

        JPanel chatBar = new JPanel(new BorderLayout());
        chatBar.setBorder(BorderFactory.createTitledBorder("Send Table Chat Message"));
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
        for (ZoneListPanel zone : allZones) {
            focusOrder.add(zone.getList());
        }
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
        btnStart = findFieldTyped(dialog, "btnStart", JButton.class);
        btnCancel = findFieldTyped(dialog, "btnCancel", JButton.class);
        seatsTable = findFieldTyped(dialog, "jTableSeats", JTable.class);
        tournament = Boolean.TRUE.equals(findFieldTyped(dialog, "isTournament", Boolean.class));

        Object chatPanel = findFieldDeep(dialog, "chatPanel");
        if (chatPanel != null) {
            chatHelper = new ChatAccessHelper(chatPanel);
            chatHelper.attach();
        }

        Log.event("Waiting", "attached; start button: " + (btnStart != null)
                + ", cancel button: " + (btnCancel != null)
                + ", seats: " + (seatsTable != null)
                + ", chat: " + (chatHelper != null)
                + ", tournament: " + tournament
                + ", owner: " + isOwner());
    }

    private void bindKeys() {
        for (final ZoneListPanel zone : allZones) {
            JList<ZoneItem> list = zone.getList();
            list.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "activateItem");
            list.getActionMap().put("activateItem", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    activate(zone.getSelectedItem());
                }
            });
            list.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_D, 0), "readDetail");
            list.getActionMap().put("readDetail", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    readDetail(zone.getSelectedItem());
                }
            });
        }

        InputMap windowInput = getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap windowAction = getRootPane().getActionMap();
        windowInput.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "returnFocus");
        windowAction.put("returnFocus", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (UiUtils.focusXMageWindow(AccessibleTableWaitingWindow.this)) {
                    speak("Returned to XMage.");
                }
            }
        });
    }

    // ========== LIFECYCLE ==========

    private void startPolling() {
        pollTimer = new Timer(1000, e -> {
            try {
                poll();
            } catch (Exception ex) {
                Log.debug("Waiting", "poll error: " + ex);
            }
        });
        pollTimer.setRepeats(true);
        pollTimer.start();
    }

    /**
     * One refresh, once a second like XMage's own seat updates. When XMage
     * has closed the dialog, the window goes too, without a word: a table
     * that starts is announced by the match or tournament window, and leaving
     * has been confirmed already. Package-private for
     * TableWaitingWindowHarness.
     */
    void poll() {
        if (!isDialogOpen()) {
            dispose();
            return;
        }
        List<String> players = readPlayers();
        boolean ready = isReady();
        String news = changeAnnouncement(lastPlayers, players, lastReady, ready, isOwner());
        lastPlayers = players;
        lastReady = ready;
        refreshZones();
        if (news != null) {
            speak(news);
        }
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

    /**
     * XMage closes the dialog through {@code MageDialog.removeDialog}, which
     * closes the internal frame, hiding it, and takes it off the desktop.
     * Package-private for the harness.
     */
    boolean isDialogOpen() {
        return dialog.isVisible() && dialog.getParent() != null;
    }

    /**
     * Whether UIWatcher should hand the keyboard on once this window has
     * closed: always after the user left, never when the table was ready to
     * start. A ready table closes because its match or tournament is
     * starting, and that window takes the keyboard itself a few seconds
     * later; handing it to the lobby in between would only make the lobby
     * speak.
     */
    boolean handsKeyboardOnWhenClosed() {
        return leaving || !lastReady;
    }

    /**
     * Takes the keyboard into Actions, where Start is. The opening sentence
     * replaces the list's name and count and is spoken from the focus event
     * itself, so the two cannot talk over each other.
     */
    public void announceWelcome() {
        actionsZone.announceOnNextFocus(openingSentence());
        UiUtils.focusAgentWindow(this, actionsZone.getList());
    }

    String openingSentence() {
        StringBuilder sb = new StringBuilder(tournament ? "Waiting room for a tournament" : "Waiting room");
        String[] types = splitTitle(title());
        if (types != null) {
            sb.append(", ").append(types[1]);
        }
        sb.append(". ").append(seatsSummary(readPlayers())).append(". ")
                .append(stateText()).append(". ")
                .append("Tab reaches the table, the seats and the chat.");
        return sb.toString();
    }

    // ========== REFRESH ==========

    private void refreshZones() {
        tableZone.updateItems(readTableItems());
        seatsZone.updateItems(readSeats());
        actionsZone.updateItems(readActions());
        chatZone.updateItems(readChat());
    }

    /**
     * The table's standing as rows: what kind of table, its types once XMage
     * has read them, how many seats are taken, whether it can start, and
     * whose it is.
     */
    List<ZoneItem> readTableItems() {
        List<ZoneItem> items = new ArrayList<>();
        items.add(info(tournament ? "Tournament table" : "Match table"));
        String[] types = splitTitle(title());
        if (types != null) {
            items.add(info("Game type: " + types[1]));
            items.add(info("Deck type: " + types[0]));
        }
        items.add(info(seatsSummary(readPlayers())));
        items.add(info(stateText()));
        items.add(info(isOwner() ? "You own this table" : "Another player owns this table"));
        return items;
    }

    /**
     * One row per seat, read from the model by model column. A seat nobody
     * has taken has its number and empty strings, which is how TableWaitModel
     * reports it; rating and history are left to D, and a rating of 0 — what
     * a computer player has — is left out.
     */
    List<ZoneItem> readSeats() {
        List<ZoneItem> items = new ArrayList<>();
        TableModel model = seatsModel();
        if (model == null) return items;
        for (int row = 0; row < model.getRowCount(); row++) {
            String seat = cell(model, row, COL_SEAT);
            if (seat.isEmpty()) seat = String.valueOf(row + 1);
            String name = cell(model, row, COL_NAME);
            if (name.isEmpty()) {
                items.add(info("Seat " + seat + ": empty"));
                continue;
            }
            String type = cell(model, row, COL_TYPE);
            String label = "Seat " + seat + ": " + name + (type.isEmpty() ? "" : ", " + type);
            StringBuilder detail = new StringBuilder(label).append('.');
            String rating = cell(model, row, COL_RATING);
            if (!rating.isEmpty() && !"0".equals(rating)) {
                detail.append(" Rating ").append(rating).append('.');
            }
            String history = cell(model, row, COL_HISTORY);
            if (!history.isEmpty()) {
                detail.append(" History: ").append(history).append('.');
            }
            items.add(new ZoneItem(label, detail.toString(), null, ZoneItem.ActionType.NONE));
        }
        return items;
    }

    /** Start for the table owner, Leave for everyone. */
    List<ZoneItem> readActions() {
        List<ZoneItem> items = new ArrayList<>();
        if (isOwner()) {
            String start = tournament ? "Start the tournament" : "Start the game";
            items.add(new ZoneItem(isReady() ? start : start + ", not yet: waiting for players",
                    null, START, ZoneItem.ActionType.CLICK_BUTTON));
        }
        if (btnCancel != null) {
            items.add(new ZoneItem("Leave the table", null, LEAVE, ZoneItem.ActionType.CLICK_BUTTON));
        }
        return items;
    }

    private List<ZoneItem> readChat() {
        List<ZoneItem> items = new ArrayList<>();
        if (chatHelper == null) return items;
        for (String line : chatHelper.getRecentLines(CHAT_LINES)) {
            items.add(info(line));
        }
        return items;
    }

    /** One entry per seat: the player's name, or "" for a seat nobody has taken. */
    List<String> readPlayers() {
        List<String> players = new ArrayList<>();
        TableModel model = seatsModel();
        if (model == null) return players;
        for (int row = 0; row < model.getRowCount(); row++) {
            players.add(cell(model, row, COL_NAME));
        }
        return players;
    }

    // ========== STATE ==========

    /** XMage shows its Start button only to the table owner. */
    boolean isOwner() {
        return btnStart != null && btnStart.isVisible();
    }

    /** XMage enables Start in READY_TO_START, for everyone, shown or not. */
    boolean isReady() {
        return btnStart != null && btnStart.isEnabled();
    }

    String stateText() {
        if (!isReady()) return "Waiting for players";
        return isOwner() ? "Ready to start" : "Ready, waiting for the table owner to start";
    }

    static String seatsSummary(List<String> players) {
        if (players.isEmpty()) return "Seats not read yet";
        return taken(players).size() + " of " + players.size() + " seats taken";
    }

    /**
     * Deck type and game type out of the dialog's title, or null before XMage
     * has set them. Split at the last separator: the game type is one of the
     * server's game type names, perhaps with rounds appended, none of which
     * holds one, while a tournament's deck type carries booster information
     * that is not known not to.
     */
    static String[] splitTitle(String title) {
        if (title == null || !title.startsWith(TITLE_PREFIX)) return null;
        String rest = title.substring(TITLE_PREFIX.length());
        int cut = rest.lastIndexOf(TITLE_SEPARATOR);
        if (cut < 0) return null;
        return new String[]{
                rest.substring(0, cut).trim(),
                rest.substring(cut + TITLE_SEPARATOR.length()).trim()};
    }

    /**
     * What a refresh has to say: who joined and who left, by name, and, for
     * the table owner, that the table has just become ready. Null when there
     * is nothing. Names are compared as a bag, so players swapping seats is no
     * news; and while the model was still empty — before XMage's first table
     * update — the seats it fills in are not news either.
     */
    static String changeAnnouncement(List<String> before, List<String> after,
                                     boolean wasReady, boolean ready, boolean owner) {
        if (before == null || after == null || before.isEmpty()) return null;
        List<String> joined = taken(after);
        for (String name : taken(before)) {
            joined.remove(name);
        }
        List<String> left = taken(before);
        for (String name : taken(after)) {
            left.remove(name);
        }
        StringBuilder sb = new StringBuilder();
        for (String name : joined) {
            sb.append(name).append(" joined. ");
        }
        for (String name : left) {
            sb.append(name).append(" left. ");
        }
        if (owner && ready && !wasReady) {
            sb.append("Ready to start.");
        }
        String text = sb.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private static List<String> taken(List<String> players) {
        List<String> names = new ArrayList<>();
        for (String name : players) {
            if (name != null && !name.isEmpty()) names.add(name);
        }
        return names;
    }

    // ========== ACTIONS ==========

    void activate(ZoneItem item) {
        if (item == null) {
            speak("Nothing selected.");
            return;
        }
        if (START.equals(item.getSourceObject())) {
            start();
        } else if (LEAVE.equals(item.getSourceObject())) {
            leave();
        } else {
            readDetail(item);
        }
    }

    private void readDetail(ZoneItem item) {
        if (item == null) {
            speak("Nothing selected.");
            return;
        }
        String detail = item.getDetailText();
        speak(detail != null && !detail.isEmpty() ? detail : item.getDisplayName());
    }

    /**
     * Presses XMage's Start, which asks the server to start the match or the
     * tournament and closes the dialog when it does. Package-private for the
     * harness.
     */
    boolean start() {
        if (!isOwner()) {
            speak("Only the table owner can start.");
            return false;
        }
        if (!isReady()) {
            speak("Not all seats are taken yet.");
            return false;
        }
        Log.event("Waiting", "start pressed; tournament: " + tournament);
        speak(tournament ? "Starting the tournament." : "Starting the game.");
        btnStart.doClick();
        return true;
    }

    /**
     * Presses XMage's Cancel, which leaves the table and closes the dialog,
     * unless the server refuses because the table has already started; then
     * XMage keeps the dialog open and does nothing else. Package-private for
     * the harness.
     */
    boolean leave() {
        if (btnCancel == null) {
            speak("Leaving is not available.");
            return false;
        }
        Log.event("Waiting", "leave pressed");
        btnCancel.doClick();
        if (isDialogOpen()) {
            speak("Could not leave, the table has already started.");
            return false;
        }
        leaving = true;
        speak("Left the table.");
        return true;
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

    // ========== HELPERS ==========

    private String title() {
        return callString(dialog, "getTitle");
    }

    private TableModel seatsModel() {
        return seatsTable != null ? seatsTable.getModel() : null;
    }

    private static String cell(TableModel model, int row, int col) {
        try {
            if (col >= model.getColumnCount()) return "";
            Object value = model.getValueAt(row, col);
            return value != null ? value.toString().trim() : "";
        } catch (Exception e) {
            return "";
        }
    }

    private static ZoneItem info(String text) {
        return new ZoneItem(text, text, null, ZoneItem.ActionType.NONE);
    }

    private void speak(String text) {
        SpeechOutput speech = AccessibilityManager.getInstance().getSpeech();
        if (speech != null) speech.speak(text);
    }
}
