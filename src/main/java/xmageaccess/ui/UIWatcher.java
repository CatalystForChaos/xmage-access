package xmageaccess.ui;

import xmageaccess.AccessibilityManager;

import xmageaccess.speech.SpeechOutput;

import javax.swing.*;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Watches for XMage windows and panels appearing, then attaches
 * the appropriate accessibility handler to each one.
 */
public class UIWatcher implements AWTEventListener, PropertyChangeListener {

    private final Map<Component, Object> attachedHandlers = new ConcurrentHashMap<>();
    private final Map<Component, AccessibleGameWindow> gameWindows = new ConcurrentHashMap<>();
    private final Map<Component, AccessibleDeckEditorWindow> deckEditorWindows = new ConcurrentHashMap<>();
    private final Map<Component, SideboardingHandler> sideboardingWindows = new ConcurrentHashMap<>();
    private ConnectDialogHandler connectHandler;
    private LobbyHandler lobbyHandler;
    private AccessibleLobbyWindow lobbyWindow;
    private boolean connectDialogWasVisible = false;
    private boolean lobbyAnnounced = false;

    public void start() {
        // Listen for window events (open, close, activate) and key events (global shortcuts)
        Toolkit.getDefaultToolkit().addAWTEventListener(this,
                AWTEvent.WINDOW_EVENT_MASK | AWTEvent.FOCUS_EVENT_MASK | AWTEvent.KEY_EVENT_MASK);

        // Listen for focus changes to detect panel switches
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addPropertyChangeListener("activeWindow", this);
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addPropertyChangeListener("focusOwner", this);

        System.out.println("[XMage Access] UI watcher started.");

        // Schedule a periodic scan for new components, since some XMage
        // UI is created without standard window events
        Timer scanTimer = new Timer(1000, e -> scanForKnownUI());
        scanTimer.setRepeats(true);
        scanTimer.start();
    }

    @Override
    public void eventDispatched(AWTEvent event) {
        if (event instanceof WindowEvent) {
            WindowEvent we = (WindowEvent) event;
            if (we.getID() == WindowEvent.WINDOW_OPENED
                    || we.getID() == WindowEvent.WINDOW_ACTIVATED) {
                scanForKnownUI();
            }
        } else if (event instanceof KeyEvent) {
            KeyEvent ke = (KeyEvent) event;
            if (ke.getID() == KeyEvent.KEY_PRESSED) {
                handleGlobalKey(ke);
            }
        }
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        scanForKnownUI();
    }

    /**
     * Handles global keyboard shortcuts that work from anywhere in the client.
     */
    private void handleGlobalKey(KeyEvent ke) {
        if (ke.isControlDown() && ke.getKeyCode() == KeyEvent.VK_Q) {
            ke.consume();
            quitClient();
        }
    }

    /**
     * Quits the XMage client immediately, bypassing the inaccessible confirmation dialog.
     * Tries to use MageFrame's shutdown method for a clean exit, falls back to System.exit.
     */
    private void quitClient() {
        SpeechOutput speech = AccessibilityManager.getInstance().getSpeech();
        if (speech != null) speech.speak("Quitting.");
        try {
            // Find MageFrame instance and call its private shutdown method
            Class<?> mageFrameClass = Class.forName("mage.client.MageFrame");
            Method getInstance = mageFrameClass.getMethod("getInstance");
            Object mageFrame = getInstance.invoke(null);

            if (mageFrame != null) {
                // Try to disconnect cleanly first
                try {
                    Class<?> sessionHandler = Class.forName("mage.client.SessionHandler");
                    Method isConnected = sessionHandler.getMethod("isConnected");
                    if ((Boolean) isConnected.invoke(null)) {
                        Method disconnect = sessionHandler.getMethod("disconnect", boolean.class);
                        disconnect.invoke(null, false);
                    }
                } catch (Exception e) {
                    // Not critical, continue with exit
                }

                // Call the private doClientShutdownAndExit method
                Method shutdown = mageFrameClass.getDeclaredMethod("doClientShutdownAndExit");
                shutdown.setAccessible(true);
                shutdown.invoke(mageFrame);
                return;
            }
        } catch (Exception e) {
            System.err.println("[XMage Access] Clean shutdown failed: " + e.getMessage());
        }
        // Fallback: force exit
        System.exit(0);
    }

    /**
     * Scans all visible windows and internal frames for known XMage UI
     * components and attaches handlers where needed.
     */
    private void scanForKnownUI() {
        try {
            for (Window window : Window.getWindows()) {
                if (!window.isVisible()) continue;
                scanComponent(window);
            }

            // Check if the connect dialog has closed and lobby should be announced
            checkConnectDialogClosed();
        } catch (Exception e) {
            // Avoid crashing the AWT event thread
            System.err.println("[XMage Access] UI scan error: " + e.getMessage());
        }
    }

    private void scanComponent(Component comp) {
        if (comp == null) return;

        String className = comp.getClass().getName();

        // Detect ConnectDialog
        if (className.equals("mage.client.dialog.ConnectDialog")) {
            if (comp.isVisible()) {
                connectDialogWasVisible = true;
                if (!attachedHandlers.containsKey(comp)) {
                    attachConnectDialog(comp);
                }
            }
        }

        // Detect TablesPanel (lobby) - attach silently, announce later
        if (className.equals("mage.client.table.TablesPanel")) {
            if (!attachedHandlers.containsKey(comp)) {
                attachLobby(comp);
            }
        }

        // Detect NewTableDialog
        if (className.equals("mage.client.dialog.NewTableDialog")) {
            if (!attachedHandlers.containsKey(comp) && comp.isVisible()) {
                attachNewTableDialog(comp);
            }
        }

        // Detect DeckGeneratorDialog
        if (className.equals("mage.client.deck.generator.DeckGeneratorDialog")) {
            if (!attachedHandlers.containsKey(comp) && comp.isVisible()) {
                attachDeckGeneratorDialog(comp);
            }
        }

        // Detect DownloadImagesDialog
        if (className.equals("mage.client.dialog.DownloadImagesDialog")) {
            if (!attachedHandlers.containsKey(comp) && comp.isVisible()) {
                attachDownloadImagesDialog(comp);
            }
        }

        // Detect TableWaitingDialog
        if (className.equals("mage.client.dialog.TableWaitingDialog")) {
            if (!attachedHandlers.containsKey(comp) && comp.isVisible()) {
                attachTableWaitingDialog(comp);
            }
        }

        // Detect DeckEditorPanel
        if (className.equals("mage.client.deckeditor.DeckEditorPanel")) {
            if (!attachedHandlers.containsKey(comp) && comp.isVisible()) {
                attachDeckEditorPanel(comp);
            }
        }

        // Detect DraftPanel
        if (className.equals("mage.client.draft.DraftPanel")) {
            if (!attachedHandlers.containsKey(comp) && comp.isVisible()) {
                attachDraftPanel(comp);
            }
        }

        // Detect GamePanel (active gameplay)
        if (className.equals("mage.client.game.GamePanel")) {
            if (!attachedHandlers.containsKey(comp) && comp.isVisible()) {
                attachGamePanel(comp);
            } else if (comp.isVisible() && attachedHandlers.containsKey(comp)) {
                // The panel may be reused for a new game in a match.
                // If the accessible window was disposed (end of previous game),
                // reset state and open a fresh window for the new game.
                AccessibleGameWindow existingWindow = gameWindows.get(comp);
                if (existingWindow == null || !existingWindow.isDisplayable()) {
                    Object handler = attachedHandlers.get(comp);
                    if (handler instanceof GamePanelHandler) {
                        ((GamePanelHandler) handler).resetState();
                    }
                    AccessibleGameWindow newWindow = new AccessibleGameWindow(comp);
                    newWindow.setVisible(true);
                    gameWindows.put(comp, newWindow);
                    if (handler instanceof GamePanelHandler) {
                        ((GamePanelHandler) handler).setAccessibleWindow(newWindow);
                    }
                    System.out.println("[XMage Access] New game in match detected — reopened accessible game window.");
                }
            }
        }

        // Detect PickChoiceDialog (modal choices)
        if (className.equals("mage.client.dialog.PickChoiceDialog")) {
            if (!attachedHandlers.containsKey(comp) && comp.isVisible()) {
                attachPickChoiceDialog(comp);
            }
        }

        // Detect PickNumberDialog (number selection)
        if (className.equals("mage.client.dialog.PickNumberDialog")) {
            if (!attachedHandlers.containsKey(comp) && comp.isVisible()) {
                attachPickNumberDialog(comp);
            }
        }

        // Detect PickCheckBoxDialog (multi-select)
        if (className.equals("mage.client.dialog.PickCheckBoxDialog")) {
            if (!attachedHandlers.containsKey(comp) && comp.isVisible()) {
                attachPickCheckBoxDialog(comp);
            }
        }

        // Detect PickPileDialog (pile choice)
        if (className.equals("mage.client.dialog.PickPileDialog")) {
            if (!attachedHandlers.containsKey(comp) && comp.isVisible()) {
                attachPickPileDialog(comp);
            }
        }

        // Detect PickMultiNumberDialog (distribute values)
        if (className.equals("mage.client.dialog.PickMultiNumberDialog")) {
            if (!attachedHandlers.containsKey(comp) && comp.isVisible()) {
                attachPickMultiNumberDialog(comp);
            }
        }

        // Detect UserRequestDialog (system prompts)
        if (className.equals("mage.client.dialog.UserRequestDialog")) {
            if (!attachedHandlers.containsKey(comp) && comp.isVisible()) {
                attachUserRequestDialog(comp);
            }
        }

        // Detect GameEndDialog (game results)
        if (className.equals("mage.client.dialog.GameEndDialog")) {
            if (!attachedHandlers.containsKey(comp) && comp.isVisible()) {
                attachGameEndDialog(comp);
            }
        }

        // Detect ShowCardsDialog (revealed cards, scry, targeting from card list)
        if (className.equals("mage.client.dialog.ShowCardsDialog")) {
            if (!attachedHandlers.containsKey(comp) && comp.isVisible()) {
                attachShowCardsDialog(comp);
            }
        }

        // Detect TournamentPanel
        if (className.equals("mage.client.tournament.TournamentPanel")) {
            if (!attachedHandlers.containsKey(comp) && comp.isVisible()) {
                attachTournamentPanel(comp);
            }
        }

        // Detect NewTournamentDialog
        if (className.equals("mage.client.dialog.NewTournamentDialog")) {
            if (!attachedHandlers.containsKey(comp) && comp.isVisible()) {
                attachNewTournamentDialog(comp);
            }
        }

        // Detect PreferencesDialog
        if (className.equals("mage.client.dialog.PreferencesDialog")) {
            if (!attachedHandlers.containsKey(comp) && comp.isVisible()) {
                attachPreferencesDialog(comp);
            }
        }

        // Recurse into containers
        if (comp instanceof Container) {
            for (Component child : ((Container) comp).getComponents()) {
                scanComponent(child);
            }
        }
    }

    /**
     * Once the connect dialog disappears, announce the lobby.
     * This ensures the lobby announcement doesn't get interrupted
     * by the connect dialog announcement.
     */
    private void checkConnectDialogClosed() {
        if (!connectDialogWasVisible || lobbyAnnounced) return;

        // Check if connect dialog is still visible
        boolean connectStillVisible = false;
        for (Map.Entry<Component, Object> entry : attachedHandlers.entrySet()) {
            if (entry.getValue() instanceof ConnectDialogHandler) {
                if (entry.getKey().isVisible()) {
                    connectStillVisible = true;
                    break;
                }
            }
        }

        if (!connectStillVisible && lobbyHandler != null) {
            lobbyAnnounced = true;
            System.out.println("[XMage Access] Connect dialog closed, announcing lobby.");
            lobbyHandler.announceWelcome();
        }
    }

    private void attachConnectDialog(Component dialog) {
        System.out.println("[XMage Access] Connect dialog detected.");
        connectHandler = new ConnectDialogHandler(dialog);
        connectHandler.attach();
        attachedHandlers.put(dialog, connectHandler);
    }

    private void attachTableWaitingDialog(Component dialog) {
        System.out.println("[XMage Access] Table waiting dialog detected.");
        TableWaitingDialogHandler handler = new TableWaitingDialogHandler(dialog);
        handler.attach();
        attachedHandlers.put(dialog, handler);
    }

    private void attachDeckGeneratorDialog(Component dialog) {
        System.out.println("[XMage Access] Deck generator dialog detected.");
        DeckGeneratorDialogHandler handler = new DeckGeneratorDialogHandler(dialog);
        handler.attach();
        attachedHandlers.put(dialog, handler);
    }

    private void attachDownloadImagesDialog(Component dialog) {
        System.out.println("[XMage Access] Download images dialog detected.");
        DownloadImagesDialogHandler handler = new DownloadImagesDialogHandler(dialog);
        handler.attach();
        attachedHandlers.put(dialog, handler);
    }

    private void attachNewTableDialog(Component dialog) {
        System.out.println("[XMage Access] New table dialog detected.");
        NewTableDialogHandler handler = new NewTableDialogHandler(dialog);
        handler.attach();
        attachedHandlers.put(dialog, handler);
    }

    private void attachDeckEditorPanel(Component panel) {
        // Check if this is a sideboarding mode panel
        String modeName = getDeckEditorMode(panel);
        boolean isSideboarding = "SIDEBOARDING".equals(modeName)
                || "LIMITED_SIDEBOARD_BUILDING".equals(modeName);

        if (isSideboarding) {
            System.out.println("[XMage Access] Sideboarding panel detected (mode: " + modeName + ").");
            SideboardingHandler sbHandler = new SideboardingHandler(panel);
            sbHandler.setVisible(true);
            sbHandler.announceWelcome();
            sideboardingWindows.put(panel, sbHandler);
            attachedHandlers.put(panel, sbHandler);
            System.out.println("[XMage Access] Sideboarding window opened.");
        } else {
            System.out.println("[XMage Access] Deck editor panel detected (mode: " + modeName + ").");
            DeckEditorHandler handler = new DeckEditorHandler(panel);
            handler.attach();
            attachedHandlers.put(panel, handler);

            // Open accessible deck editor window alongside the handler
            AccessibleDeckEditorWindow window = new AccessibleDeckEditorWindow(panel);
            window.setVisible(true);
            deckEditorWindows.put(panel, window);
            System.out.println("[XMage Access] Accessible deck editor window opened.");
        }
    }

    private String getDeckEditorMode(Component panel) {
        try {
            Class<?> clazz = panel.getClass();
            while (clazz != null) {
                try {
                    java.lang.reflect.Field field = clazz.getDeclaredField("mode");
                    field.setAccessible(true);
                    Object mode = field.get(panel);
                    return mode != null ? mode.toString() : null;
                } catch (NoSuchFieldException e) {
                    clazz = clazz.getSuperclass();
                }
            }
        } catch (Exception e) {
            System.err.println("[XMage Access] Error reading deck editor mode: " + e.getMessage());
        }
        return null;
    }

    private void attachGamePanel(Component panel) {
        System.out.println("[XMage Access] Game panel detected.");
        GamePanelHandler handler = new GamePanelHandler(panel);
        handler.attach();
        attachedHandlers.put(panel, handler);

        // Open accessible game window alongside the handler
        AccessibleGameWindow window = new AccessibleGameWindow(panel);
        window.setVisible(true);
        gameWindows.put(panel, window);

        // Connect handler to window for event-driven refreshes
        handler.setAccessibleWindow(window);

        // Closing the accessible window wipes all cached game state in the handler.
        // This gives the user an explicit way to force a clean slate mid-game.
        window.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                handler.resetState();
                System.out.println("[XMage Access] Accessible game window closed — game state reset.");
            }
        });

        System.out.println("[XMage Access] Accessible game window opened.");
    }

    private void attachLobby(Component panel) {
        System.out.println("[XMage Access] Lobby detected.");
        lobbyHandler = new LobbyHandler(panel);
        // Attach but don't announce yet - wait for connect dialog to close
        lobbyHandler.attachSilent();
        attachedHandlers.put(panel, lobbyHandler);

        // Open the accessible lobby window
        lobbyWindow = lobbyHandler.createAccessibleWindow();
        if (lobbyWindow != null) {
            lobbyWindow.setVisible(true);
            System.out.println("[XMage Access] Accessible lobby window opened.");
        }
    }

    private void attachDraftPanel(Component panel) {
        System.out.println("[XMage Access] Draft panel detected.");
        DraftPanelHandler handler = new DraftPanelHandler(panel);
        handler.attach();
        attachedHandlers.put(panel, handler);
    }

    private void attachPickChoiceDialog(Component dialog) {
        System.out.println("[XMage Access] PickChoiceDialog detected.");
        PickChoiceDialogHandler handler = new PickChoiceDialogHandler(dialog);
        handler.attach();
        attachedHandlers.put(dialog, handler);
    }

    private void attachPickNumberDialog(Component dialog) {
        System.out.println("[XMage Access] PickNumberDialog detected.");
        PickNumberDialogHandler handler = new PickNumberDialogHandler(dialog);
        handler.attach();
        attachedHandlers.put(dialog, handler);
    }

    private void attachPickCheckBoxDialog(Component dialog) {
        System.out.println("[XMage Access] PickCheckBoxDialog detected.");
        PickCheckBoxDialogHandler handler = new PickCheckBoxDialogHandler(dialog);
        handler.attach();
        attachedHandlers.put(dialog, handler);
    }

    private void attachPickPileDialog(Component dialog) {
        System.out.println("[XMage Access] PickPileDialog detected.");
        PickPileDialogHandler handler = new PickPileDialogHandler(dialog);
        handler.attach();
        attachedHandlers.put(dialog, handler);
    }

    private void attachPickMultiNumberDialog(Component dialog) {
        System.out.println("[XMage Access] PickMultiNumberDialog detected.");
        PickMultiNumberDialogHandler handler = new PickMultiNumberDialogHandler(dialog);
        handler.attach();
        attachedHandlers.put(dialog, handler);
    }

    private void attachUserRequestDialog(Component dialog) {
        System.out.println("[XMage Access] UserRequestDialog detected.");
        UserRequestDialogHandler handler = new UserRequestDialogHandler(dialog);
        handler.attach();
        attachedHandlers.put(dialog, handler);
    }

    private void attachGameEndDialog(Component dialog) {
        System.out.println("[XMage Access] GameEndDialog detected.");
        GameEndDialogHandler handler = new GameEndDialogHandler(dialog);
        handler.attach();
        attachedHandlers.put(dialog, handler);
    }

    private void attachShowCardsDialog(Component dialog) {
        System.out.println("[XMage Access] ShowCardsDialog detected.");
        ShowCardsDialogHandler handler = new ShowCardsDialogHandler(dialog);
        handler.attach();
        attachedHandlers.put(dialog, handler);
    }

    private void attachTournamentPanel(Component panel) {
        System.out.println("[XMage Access] Tournament panel detected.");
        TournamentPanelHandler handler = new TournamentPanelHandler(panel);
        handler.attach();
        attachedHandlers.put(panel, handler);
    }

    private void attachNewTournamentDialog(Component dialog) {
        System.out.println("[XMage Access] New tournament dialog detected.");
        NewTournamentDialogHandler handler = new NewTournamentDialogHandler(dialog);
        handler.attach();
        attachedHandlers.put(dialog, handler);
    }

    private void attachPreferencesDialog(Component dialog) {
        System.out.println("[XMage Access] Preferences dialog detected.");
        PreferencesDialogHandler handler = new PreferencesDialogHandler(dialog);
        handler.attach();
        attachedHandlers.put(dialog, handler);
    }

    /**
     * Called when a previously attached component is no longer visible.
     */
    public void detach(Component comp) {
        Object handler = attachedHandlers.remove(comp);
        if (handler instanceof ConnectDialogHandler) {
            ((ConnectDialogHandler) handler).detach();
        } else if (handler instanceof LobbyHandler) {
            ((LobbyHandler) handler).detach();
            if (lobbyWindow != null) {
                lobbyWindow.stopPolling();
                lobbyWindow.dispose();
                lobbyWindow = null;
            }
        } else if (handler instanceof NewTableDialogHandler) {
            ((NewTableDialogHandler) handler).detach();
        } else if (handler instanceof DeckGeneratorDialogHandler) {
            ((DeckGeneratorDialogHandler) handler).detach();
        } else if (handler instanceof DownloadImagesDialogHandler) {
            ((DownloadImagesDialogHandler) handler).detach();
        } else if (handler instanceof TableWaitingDialogHandler) {
            ((TableWaitingDialogHandler) handler).detach();
        } else if (handler instanceof SideboardingHandler) {
            SideboardingHandler sbWindow = sideboardingWindows.remove(comp);
            if (sbWindow != null) {
                sbWindow.stopPolling();
                sbWindow.dispose();
            }
        } else if (handler instanceof DeckEditorHandler) {
            ((DeckEditorHandler) handler).detach();
            AccessibleDeckEditorWindow deckWindow = deckEditorWindows.remove(comp);
            if (deckWindow != null) {
                deckWindow.stopPolling();
                deckWindow.dispose();
            }
        } else if (handler instanceof GamePanelHandler) {
            ((GamePanelHandler) handler).detach();
            AccessibleGameWindow window = gameWindows.remove(comp);
            if (window != null) {
                window.stopPolling();
                window.dispose();
            }
        } else if (handler instanceof DraftPanelHandler) {
            ((DraftPanelHandler) handler).detach();
        } else if (handler instanceof PickChoiceDialogHandler) {
            ((PickChoiceDialogHandler) handler).detach();
        } else if (handler instanceof PickNumberDialogHandler) {
            ((PickNumberDialogHandler) handler).detach();
        } else if (handler instanceof PickCheckBoxDialogHandler) {
            ((PickCheckBoxDialogHandler) handler).detach();
        } else if (handler instanceof PickPileDialogHandler) {
            ((PickPileDialogHandler) handler).detach();
        } else if (handler instanceof PickMultiNumberDialogHandler) {
            ((PickMultiNumberDialogHandler) handler).detach();
        } else if (handler instanceof UserRequestDialogHandler) {
            ((UserRequestDialogHandler) handler).detach();
        } else if (handler instanceof GameEndDialogHandler) {
            ((GameEndDialogHandler) handler).detach();
        } else if (handler instanceof ShowCardsDialogHandler) {
            ((ShowCardsDialogHandler) handler).detach();
        } else if (handler instanceof TournamentPanelHandler) {
            ((TournamentPanelHandler) handler).detach();
        } else if (handler instanceof NewTournamentDialogHandler) {
            ((NewTournamentDialogHandler) handler).detach();
        } else if (handler instanceof PreferencesDialogHandler) {
            ((PreferencesDialogHandler) handler).detach();
        }
    }
}
