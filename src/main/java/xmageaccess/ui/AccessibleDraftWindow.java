package xmageaccess.ui;

import xmageaccess.AccessibilityManager;
import xmageaccess.speech.SpeechOutput;
import xmageaccess.util.Log;

import static xmageaccess.util.CardText.formatCardDetailed;
import static xmageaccess.util.ReflectionUtils.*;
import static xmageaccess.util.TextUtils.*;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FocusTraversalPolicy;
import java.awt.KeyboardFocusManager;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Accessible draft window: the booster you are picking from and the pool you
 * have built so far, as two lists you Tab between.
 *
 * <p>The draft used to be driven by shortcuts on XMage's own draft panel,
 * where the cards are card images in a grid and nothing is reachable by
 * keyboard. Those shortcuts went away with everything else the agent used to
 * bind inside XMage's window; this window is where they come back, bound to
 * its own root pane so they answer here and nowhere else.
 *
 * <p>Picking goes through {@code SessionHandler.sendCardPick(UUID, UUID,
 * Set)} — the same call XMage's own click handler makes — and then asks the
 * draft panel to redraw its picked-cards area, so XMage's display stays in
 * step with ours. Whether a pick is allowed at all is XMage's own
 * {@code isAllowedToPick()}, which covers its click-protection timer.
 *
 * Shortcuts:
 *   Tab/Shift+Tab - switch between Booster and Your Picks
 *   Up/Down       - move through the cards in a list
 *   Enter         - pick the selected card (in the booster)
 *   D             - read the full card text
 *   Ctrl+R        - read pack, pick and time
 *   Ctrl+T        - read the time remaining
 *   Ctrl+F1       - read all shortcuts
 *   Escape        - back to XMage's own window
 */
public class AccessibleDraftWindow extends JFrame {

    private static final String SESSION_HANDLER = "mage.client.SessionHandler";

    private final Component draftPanel;

    private final ZoneListPanel boosterZone;
    private final ZoneListPanel picksZone;
    private final List<ZoneListPanel> allZones = new ArrayList<>();

    // Cached reflection references (re-read on every poll)
    private Component draftBooster;   // DraftGrid
    private Object draftPicks;        // CardsList

    private Timer pollTimer;
    private int lastBoosterSize = -1;
    private int lastPicksSize = -1;

    public AccessibleDraftWindow(Component draftPanel) {
        super("XMage Accessible Draft");
        this.draftPanel = draftPanel;

        boosterZone = new ZoneListPanel("Booster");
        picksZone = new ZoneListPanel("Your Picks");
        allZones.add(boosterZone);
        allZones.add(picksZone);

        buildUI();
        discoverComponents();
        bindKeys();
        startPolling();
    }

    private void buildUI() {
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        setSize(600, 700);
        setLocationRelativeTo(null);

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        for (ZoneListPanel zone : allZones) {
            zone.setPreferredSize(new Dimension(580, 300));
            zone.setMaximumSize(new Dimension(Integer.MAX_VALUE, 400));
            mainPanel.add(zone);
        }
        add(new JScrollPane(mainPanel), BorderLayout.CENTER);

        setFocusCycleRoot(true);
        final List<Component> focusOrder = new ArrayList<>();
        focusOrder.add(boosterZone.getList());
        focusOrder.add(picksZone.getList());
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
        draftBooster = findFieldTyped(draftPanel, "draftBooster", Component.class);
        draftPicks = findFieldDeep(draftPanel, "draftPicks");
        System.out.println("[XMage Access] Draft window - booster: " + (draftBooster != null)
                + ", picks: " + (draftPicks != null));
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

            list.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_D, 0), "readDetail");
            list.getActionMap().put("readDetail", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    readSelectedDetail();
                }
            });
        }

        InputMap windowInput = getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap windowAction = getRootPane().getActionMap();

        windowInput.put(KeyStroke.getKeyStroke(KeyEvent.VK_R, KeyEvent.CTRL_DOWN_MASK), "readStatus");
        windowAction.put("readStatus", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                speak(draftStatus() + boosterZone.getList().getModel().getSize() + " cards in the booster.");
            }
        });

        windowInput.put(KeyStroke.getKeyStroke(KeyEvent.VK_T, KeyEvent.CTRL_DOWN_MASK), "readTime");
        windowAction.put("readTime", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String time = readTimeField();
                speak(time != null ? "Time remaining: " + time : "No timer.");
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
                if (xmageaccess.util.UiUtils.focusXMageWindow(AccessibleDraftWindow.this)) {
                    speak("Returned to XMage.");
                }
            }
        });
    }

    // ========== LIFECYCLE ==========

    private void startPolling() {
        pollTimer = new Timer(1000, e -> {
            try {
                if (!draftPanel.isVisible()) {
                    stopPolling();
                    dispose();
                    speak("Draft closed.");
                    return;
                }
                discoverComponents();
                refreshZones();
            } catch (Exception ex) {
                Log.debug("Draft", "poll error: " + ex);
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
        super.dispose();
    }

    /** Opening announcement, and focus into the booster. */
    public void announceWelcome() {
        refreshZones();
        int count = boosterZone.getList().getModel().getSize();
        speak("Draft. " + draftStatus() + count + " cards to pick from. "
                + "Tab between booster and your picks, Enter to pick, "
                + "D for card text. Ctrl+F1 for all shortcuts.");
        SwingUtilities.invokeLater(() -> boosterZone.getList().requestFocusInWindow());
    }

    // ========== REFRESH ==========

    private void refreshZones() {
        List<ZoneItem> booster = readBoosterCards();
        if (booster.size() != lastBoosterSize) {
            boolean newPack = lastBoosterSize >= 0 && booster.size() > lastBoosterSize;
            lastBoosterSize = booster.size();
            boosterZone.updateItems(booster);
            if (newPack) {
                speak("New pack. " + draftStatus() + booster.size() + " cards. "
                        + "First: " + booster.get(0).getDisplayName() + ".");
            }
        }

        List<ZoneItem> picks = readPickedCards();
        if (picks.size() != lastPicksSize) {
            lastPicksSize = picks.size();
            picksZone.updateItems(picks);
        }
    }

    /**
     * The booster is a grid of card panels; every MageCard answers
     * {@code getOriginal()} with the CardView behind it.
     */
    List<ZoneItem> readBoosterCards() {
        List<ZoneItem> items = new ArrayList<>();
        if (!(draftBooster instanceof Container)) return items;
        for (Component comp : ((Container) draftBooster).getComponents()) {
            Object cardView = callMethod(comp, "getOriginal");
            if (cardView != null) {
                items.add(cardItem(cardView, ZoneItem.ActionType.PICK_CARD));
            }
        }
        return items;
    }

    /**
     * The picked cards live in the panel's CardsList, whose {@code cards}
     * field is a CardsView — a map of full CardViews, unlike the
     * SimpleCardViews the server sends back with a pick, which carry no name.
     */
    List<ZoneItem> readPickedCards() {
        List<ZoneItem> items = new ArrayList<>();
        Object cards = findFieldDeep(draftPicks, "cards");
        if (!(cards instanceof Map)) return items;
        for (Object cardView : ((Map<?, ?>) cards).values()) {
            if (cardView != null) {
                items.add(cardItem(cardView, ZoneItem.ActionType.NONE));
            }
        }
        return items;
    }

    private static ZoneItem cardItem(Object cardView, ZoneItem.ActionType action) {
        String name = callString(cardView, "getName");
        String manaCost = callString(cardView, "getManaCostStr");
        StringBuilder label = new StringBuilder(name != null ? name : "Unknown card");
        if (manaCost != null && !manaCost.isEmpty()) {
            label.append(", ").append(formatManaCost(manaCost));
        }
        return new ZoneItem(label.toString(), null, cardView, action);
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
        if (item.getActionType() == ZoneItem.ActionType.PICK_CARD) {
            pickCard(item);
        } else {
            speak("Already picked.");
        }
    }

    @SuppressWarnings("unchecked")
    void pickCard(ZoneItem item) {
        Object allowed = callMethod(draftPanel, "isAllowedToPick");
        if (Boolean.FALSE.equals(allowed)) {
            speak("Not your pick yet.");
            return;
        }

        Object cardView = item.getSourceObject();
        Object cardId = callMethod(cardView, "getId");
        UUID draftId = findFieldTyped(draftPanel, "draftId", UUID.class);
        if (!(cardId instanceof UUID) || draftId == null) {
            speak("Cannot pick this card.");
            return;
        }

        Set<UUID> cardsHidden = findFieldTyped(draftPanel, "cardsHidden", Set.class);
        if (cardsHidden == null) cardsHidden = new java.util.HashSet<>();

        Object result;
        try {
            Class<?> sessionHandler = Class.forName(SESSION_HANDLER);
            Method sendPick = sessionHandler.getMethod("sendCardPick",
                    UUID.class, UUID.class, Set.class);
            result = sendPick.invoke(null, draftId, cardId, cardsHidden);
        } catch (Exception e) {
            Log.warn("Draft", "pick failed", e);
            speak("Could not send the pick.");
            return;
        }

        if (result == null) {
            speak("The pick was refused.");
            return;
        }

        speak("Picked " + item.getDisplayName() + ". Waiting for the other players.");
        redrawPickedArea(result);
        lastBoosterSize = -1;   // force the booster list to rebuild next poll
        refreshZones();
    }

    /**
     * Hands the new pick list to the draft panel's own redraw so XMage's
     * display keeps up. Private in XMage, and pure display: a failure here
     * says nothing about the pick, which the server has already taken.
     */
    private void redrawPickedArea(Object pickView) {
        try {
            Object picks = callMethod(pickView, "getPicks");
            Class<?> simpleCardsView = findClass("mage.view.SimpleCardsView");
            if (picks == null || simpleCardsView == null) return;
            Method loadPicked = draftPanel.getClass()
                    .getDeclaredMethod("loadCardsToPickedCardsArea", simpleCardsView);
            loadPicked.setAccessible(true);
            loadPicked.invoke(draftPanel, picks);
        } catch (Exception e) {
            Log.debug("Draft", "picked-area redraw failed: " + e);
        }
    }

    private void readSelectedDetail() {
        ZoneListPanel zone = getFocusedZone();
        if (zone == null) return;
        ZoneItem item = zone.getSelectedItem();
        if (item == null) {
            speak("Nothing selected.");
            return;
        }
        if (item.getSourceObject() != null) {
            speak(formatCardDetailed(item.getSourceObject()));
        } else {
            speak(item.getDisplayName());
        }
    }

    // ========== STATUS ==========

    /** "Pack 2, Guilds of Ravnica. Card 3 of 15. Time: 0:42. " */
    private String draftStatus() {
        StringBuilder sb = new StringBuilder();

        JLabel cardNumber = findFieldTyped(draftPanel, "labelCardNumber", JLabel.class);
        if (cardNumber != null && cardNumber.getText() != null && !cardNumber.getText().isEmpty()) {
            sb.append(cardNumber.getText()).append(". ");
        }

        for (int i = 1; i <= 3; i++) {
            JCheckBox check = findFieldTyped(draftPanel, "checkPack" + i, JCheckBox.class);
            if (check != null && check.isSelected()) {
                JTextField packField = findFieldTyped(draftPanel, "editPack" + i, JTextField.class);
                String packName = packField != null ? packField.getText() : null;
                if (packName != null && !packName.isEmpty()) {
                    sb.append("Pack ").append(i).append(": ").append(packName).append(". ");
                }
                break;
            }
        }

        String time = readTimeField();
        if (time != null) sb.append("Time: ").append(time).append(". ");

        return sb.toString();
    }

    private String readTimeField() {
        JTextField timeField = findFieldTyped(draftPanel, "editTimeRemaining", JTextField.class);
        if (timeField == null) return null;
        String text = timeField.getText();
        return text != null && !text.isEmpty() ? text : null;
    }

    private void readHelp() {
        speak("Draft shortcuts. Tab and Shift+Tab switch between the booster "
                + "and your picks. Up and Down move through the cards. "
                + "Enter picks the selected card. D reads the full card text. "
                + "Ctrl+R reads pack, pick and time. Ctrl+T reads the time "
                + "remaining. Escape returns to XMage.");
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
