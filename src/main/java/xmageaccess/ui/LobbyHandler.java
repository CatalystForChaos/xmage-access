package xmageaccess.ui;

import xmageaccess.AccessibilityManager;
import xmageaccess.speech.SpeechOutput;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Field;

/**
 * Finds XMage's lobby (TablesPanel), announces it, and builds the
 * accessible lobby window over the tables it holds.
 *
 * <p>It used to carry thirteen shortcuts of its own — Ctrl+G to list games,
 * Ctrl+J to join, Ctrl+N for a new table, and so on. Every one of them
 * repeated something {@link AccessibleLobbyWindow} already offers as a row
 * you reach with Tab and the arrow keys and act on with Enter, so they were
 * a second way to do what the window does, and the keys are gone. What is
 * left here is the discovery of XMage's tables and the chat helper the
 * window reads from.
 */
public class LobbyHandler {

    private final Component lobbyPanel;
    private JTable activeTable;
    private JTable playersTable;
    private ChatAccessHelper chatHelper;
    private AccessibleLobbyWindow accessibleWindow;


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
        } catch (Exception e) {
            xmageaccess.util.Log.warn("Lobby", "attach failed", e);
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

        speak("Lobby. Tab between actions, open games, players and chat. "
                + "Up and Down move through a list, Enter acts on the "
                + "selected row, D reads a game in full, Escape returns to "
                + "XMage.");

        // The shortcuts above only answer while the accessible window is the
        // active one, so hand it the keyboard as the announcement goes out.
        if (accessibleWindow != null) {
            accessibleWindow.takeFocus();
        }
    }

    /**
     * Creates the accessible lobby window using the tables already discovered.
     * Called by UIWatcher after attachSilent(). The reference is kept because
     * the shortcuts below only answer while that window is the active one.
     */
    public AccessibleLobbyWindow createAccessibleWindow() {
        accessibleWindow = new AccessibleLobbyWindow(lobbyPanel, activeTable, playersTable, chatHelper);
        return accessibleWindow;
    }

    public void detach() {
        if (chatHelper != null) {
            chatHelper.detach();
        }
    }

    private void discoverComponents() throws Exception {
        // TablesPanel contains the game tables directly
        activeTable = findFieldValue(lobbyPanel, "tableTables", JTable.class);

        // Players table and chat are nested inside chatPanelMain (PlayersChatPanel)
        Object chatPanel = findFieldValue(lobbyPanel, "chatPanelMain", Object.class);
        if (chatPanel != null) {
            playersTable = findFieldValue(chatPanel, "jTablePlayers", JTable.class);

            // Get the user chat panel (ChatPanelSeparated -> ChatPanelBasic)
            if (chatHelper == null) {
                Object userChat = xmageaccess.util.ReflectionUtils.callMethod(chatPanel, "getUserChatPanel");
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

    private void speak(String text) {
        SpeechOutput speech = AccessibilityManager.getInstance().getSpeech();
        if (speech != null) {
            speech.speak(text);
        }
    }
}
