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
     */
    public void updateItems(List<ZoneItem> items) {
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
