package xmageaccess.ui;

import xmageaccess.AccessibilityManager;
import xmageaccess.speech.SpeechOutput;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.util.List;

/**
 * Reusable panel for one game zone. Contains a JLabel header and a JList
 * with accessible name set for screen reader zone announcement.
 */
public class ZoneListPanel extends JPanel {

    private final JList<ZoneItem> list;
    private final DefaultListModel<ZoneItem> model;
    private final String zoneName;
    private boolean isRefreshing = false;

    public ZoneListPanel(String zoneName) {
        this.zoneName = zoneName;
        setLayout(new BorderLayout());

        JLabel headerLabel = new JLabel(zoneName);
        model = new DefaultListModel<>();
        // Override processKeyEvent to suppress KEY_TYPED before BasicListUI's
        // KeyHandler sees it. BasicListUI registers its type-ahead handler during
        // installUI(), so addKeyListener() alone fires too late to win the race.
        // Dropping KEY_TYPED entirely is safe: our InputMap shortcuts use KEY_PRESSED.
        list = new JList<ZoneItem>(model) {
            @Override
            protected void processKeyEvent(KeyEvent e) {
                if (e.getID() == KeyEvent.KEY_TYPED) {
                    return;
                }
                super.processKeyEvent(e);
            }
        };
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Set accessible name for screen reader
        list.getAccessibleContext().setAccessibleName(zoneName);

        // Speak zone name + item count when Tab arrives
        list.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                if (!isRefreshing) {
                    int count = model.getSize();
                    speak(zoneName + ". " + count + (count == 1 ? " item." : " items."));
                    if (count > 0 && list.getSelectedIndex() < 0) {
                        list.setSelectedIndex(0);
                    }
                }
            }
        });

        // Speak item name when arrow keys move selection
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !isRefreshing) {
                ZoneItem item = list.getSelectedValue();
                if (item != null) {
                    speak(item.getDisplayName());
                }
            }
        });

        add(headerLabel, BorderLayout.NORTH);
        add(new JScrollPane(list), BorderLayout.CENTER);
    }

    /**
     * Refreshes the list content, preserving the selection position.
     *
     * <p>A refresh that would change nothing is dropped before it touches
     * the model. The zones are rebuilt by polling timers — once a second in
     * the draft, every five in the lobby and the game — and clearing and
     * refilling a JList fires accessibility events at the screen reader
     * whether or not anything is different. Rebuilding only on a real
     * change is what keeps it quiet while you read.
     */
    public void updateItems(List<ZoneItem> items) {
        if (sameAsShown(items)) return;

        isRefreshing = true;
        try {
            int prevIndex = list.getSelectedIndex();
            model.clear();
            for (ZoneItem item : items) {
                model.addElement(item);
            }
            if (prevIndex >= 0 && prevIndex < model.getSize()) {
                list.setSelectedIndex(prevIndex);
            } else if (model.getSize() > 0) {
                list.setSelectedIndex(0);
            }
        } finally {
            isRefreshing = false;
        }
    }

    /**
     * True when the list already shows exactly these items — same order,
     * same spoken text, same detail, same object behind each row. The
     * source object is part of the comparison because it is what an action
     * is sent to: a row that reads the same but points somewhere else has
     * to be replaced, or Enter would act on the previous game.
     */
    private boolean sameAsShown(List<ZoneItem> items) {
        if (items == null || items.size() != model.getSize()) return false;
        for (int i = 0; i < items.size(); i++) {
            ZoneItem now = model.get(i);
            ZoneItem next = items.get(i);
            if (next == null || now == null) return false;
            if (!java.util.Objects.equals(now.getDisplayName(), next.getDisplayName())) return false;
            if (!java.util.Objects.equals(now.getDetailText(), next.getDetailText())) return false;
            if (now.getSourceObject() != next.getSourceObject()
                    && !java.util.Objects.equals(now.getSourceObject(), next.getSourceObject())) return false;
            if (now.getActionType() != next.getActionType()) return false;
        }
        return true;
    }

    public JList<ZoneItem> getList() {
        return list;
    }

    public ZoneItem getSelectedItem() {
        return list.getSelectedValue();
    }

    public String getZoneName() {
        return zoneName;
    }

    private void speak(String text) {
        SpeechOutput speech = AccessibilityManager.getInstance().getSpeech();
        if (speech != null) {
            speech.speak(text);
        }
    }
}
