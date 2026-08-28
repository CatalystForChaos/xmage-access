package xmageaccess.ui;

import xmageaccess.AccessibilityManager;
import xmageaccess.speech.SpeechOutput;

import static xmageaccess.util.ReflectionUtils.*;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Accessibility handler for the XMage Draft Panel.
 *
 * <p>Announcements only. The draft used to carry its own keyboard shortcuts
 * (navigate the booster, pick a card, read the picks), but they worked solely
 * inside XMage's own window, which is where the agent no longer takes keys —
 * see {@code UiUtils.isAgentWindowActive}. Unlike the game, the lobby and the
 * deck editor, the draft has no accessible window to move them into, so for
 * now this handler says what is happening and nothing more. Giving the draft
 * a window of its own is the way to bring picking back.
 */
public class DraftPanelHandler {

    private final Component draftPanel;
    private Component draftBooster;      // DraftGrid
    private List<Object[]> boosterCards = new ArrayList<>(); // [MageCard, CardView]
    private Timer announceTimer;
    private int lastBoosterSize = -1;

    public DraftPanelHandler(Component draftPanel) {
        this.draftPanel = draftPanel;
    }

    public void attach() {
        try {
            discoverComponents();
            startMonitoring();
            announceDraftStart();
        } catch (Exception e) {
            xmageaccess.util.Log.warn("Draft", "attach failed", e);
        }
    }

    public void detach() {
        if (announceTimer != null) {
            announceTimer.stop();
            announceTimer = null;
        }
    }

    private void discoverComponents() {
        draftBooster = findFieldTyped(draftPanel, "draftBooster", Component.class);
        System.out.println("[XMage Access] Draft components - booster: " + (draftBooster != null));
    }

    private void announceDraftStart() {
        StringBuilder sb = new StringBuilder("Draft started. ");
        String status = readDraftStatus();
        if (!status.isEmpty()) sb.append(status).append(" ");

        refreshBoosterCards();
        if (!boosterCards.isEmpty()) {
            sb.append(boosterCards.size()).append(" cards to pick from. ");
        }

        sb.append("Announcements only — the draft has no keyboard control yet.");
        speak(sb.toString());
    }

    private void startMonitoring() {
        // Poll for new boosters appearing
        announceTimer = new Timer(1000, e -> {
            if (!isPanelVisible()) return;
            try {
                refreshBoosterCards();
                int currentSize = boosterCards.size();
                if (currentSize != lastBoosterSize && currentSize > 0 && lastBoosterSize >= 0) {
                    // New booster arrived
                    String status = readDraftStatus();
                    speak("New pack. " + status + " " + currentSize + " cards. "
                            + "First: " + getCardName(0) + ".");
                }
                lastBoosterSize = currentSize;
            } catch (Exception ex) {
                // Ignore
            }
        });
        announceTimer.setRepeats(true);
        announceTimer.start();
    }

    // ========== STATUS READING ==========

    private String readDraftStatus() {
        StringBuilder sb = new StringBuilder();

        // Pack info
        String packLabel = readLabel("labelCardNumber");
        if (packLabel != null && !packLabel.isEmpty()) {
            sb.append(packLabel).append(". ");
        }

        // Which pack is active
        for (int i = 1; i <= 3; i++) {
            JCheckBox check = findFieldTyped(draftPanel, "checkPack" + i, JCheckBox.class);
            if (check != null && check.isSelected()) {
                JTextField packField = findFieldTyped(draftPanel, "editPack" + i, JTextField.class);
                if (packField != null) {
                    String packName = packField.getText();
                    if (packName != null && !packName.isEmpty()) {
                        sb.append("Pack ").append(i).append(": ").append(packName).append(". ");
                    }
                }
                break;
            }
        }

        // Time remaining
        String time = readTimeField();
        if (time != null) {
            sb.append("Time: ").append(time).append(". ");
        }

        return sb.toString();
    }

    private String readTimeField() {
        JTextField timeField = findFieldTyped(draftPanel, "editTimeRemaining", JTextField.class);
        if (timeField != null) {
            String text = timeField.getText();
            if (text != null && !text.isEmpty()) return text;
        }
        return null;
    }

    // ========== BOOSTER CARDS ==========

    private void refreshBoosterCards() {
        boosterCards.clear();
        if (draftBooster == null) return;

        try {
            Component[] components = ((Container) draftBooster).getComponents();
            for (Component comp : components) {
                try {
                    Method getOriginal = comp.getClass().getMethod("getOriginal");
                    Object cardView = getOriginal.invoke(comp);
                    if (cardView != null) {
                        boosterCards.add(new Object[]{comp, cardView});
                    }
                } catch (NoSuchMethodException ignored) {
                    // Not a MageCard
                }
            }
        } catch (Exception e) {
            xmageaccess.util.Log.warn("Draft", "error refreshing booster", e);
        }
    }

    private String getCardName(int index) {
        if (index < 0 || index >= boosterCards.size()) return "Unknown";
        return callString(boosterCards.get(index)[1], "getName");
    }

    // ========== VISIBILITY ==========

    private boolean isPanelVisible() {
        if (draftPanel == null || !draftPanel.isVisible()) return false;
        Component c = draftPanel;
        while (c != null) {
            if (!c.isVisible()) return false;
            c = c.getParent();
        }
        return true;
    }

    private String readLabel(String fieldName) {
        try {
            JLabel label = findFieldTyped(draftPanel, fieldName, JLabel.class);
            if (label != null) return label.getText();
        } catch (Exception ignored) {}
        return null;
    }

    private void speak(String text) {
        SpeechOutput speech = AccessibilityManager.getInstance().getSpeech();
        if (speech != null) speech.speak(text);
    }
}
