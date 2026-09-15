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
    private String nextFocusAnnouncement;

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
                    String once = nextFocusAnnouncement;
                    nextFocusAnnouncement = null;
                    speak(once != null ? once
                            : zoneName + ". " + count + (count == 1 ? " item." : " items."));
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
     * Refreshes the list content, keeping the user where they are and
     * telling the screen reader as little as possible.
     *
     * <p>Every zone is refreshed by a polling timer, some once a second, and
     * what a screen reader hears depends on how the model changes rather
     * than on whether anything looks different. Clearing and refilling the
     * list takes the selection off its row and puts it back, and JList's
     * accessible context reports each of those moves as a new active
     * descendant, which a screen reader answers by reading the row again.
     * So nothing is cleared. An unchanged row stays as it is; a changed row
     * is replaced in place, which fires a contents change and no selection
     * event; rows come and go only at the end. The selection moves only when
     * its own row went away, and then to the nearest row left.
     *
     * <p>A row that changed under the cursor while this list has the
     * keyboard is read out by the agent — but only a row that stands for
     * something: a card, a button, a match. Rows without a source object are
     * information, such as the draft clock, the chat or the game log; they
     * change silently and read current when you arrive on them.
     */
    public void updateItems(List<ZoneItem> items) {
        if (items == null) items = java.util.Collections.emptyList();
        ZoneItem changedUnderCursor = null;
        isRefreshing = true;
        try {
            int selected = list.getSelectedIndex();
            int common = Math.min(model.getSize(), items.size());
            for (int i = 0; i < common; i++) {
                ZoneItem now = model.get(i);
                ZoneItem next = items.get(i);
                if (sameRow(now, next)) continue;
                model.set(i, next);
                if (i == selected && next != null && next.getSourceObject() != null
                        && (now == null || !java.util.Objects.equals(
                                now.getDisplayName(), next.getDisplayName()))) {
                    changedUnderCursor = next;
                }
            }
            if (model.getSize() > items.size()) {
                model.removeRange(items.size(), model.getSize() - 1);
            }
            for (int i = common; i < items.size(); i++) {
                model.addElement(items.get(i));
            }
            if (list.getSelectedIndex() < 0 && model.getSize() > 0) {
                list.setSelectedIndex(selected >= 0 ? Math.min(selected, model.getSize() - 1) : 0);
            }
        } finally {
            isRefreshing = false;
        }
        if (changedUnderCursor != null && hasKeyboard()) {
            announceChange(changedUnderCursor.getDisplayName());
        }
    }

    /**
     * Whether a row can stay as it is: the same spoken text, detail and
     * action, and the same object behind it. The object is what an action is
     * sent to, so a row that reads the same but points somewhere else is
     * replaced, or Enter would act on the previous game. Arrays compare by
     * content: the game window builds a fresh one for every button row on
     * every refresh, which made its Actions zone count as changed each time.
     */
    private static boolean sameRow(ZoneItem now, ZoneItem next) {
        if (now == next) return true;
        if (now == null || next == null) return false;
        if (!java.util.Objects.equals(now.getDisplayName(), next.getDisplayName())) return false;
        if (!java.util.Objects.equals(now.getDetailText(), next.getDetailText())) return false;
        if (now.getActionType() != next.getActionType()) return false;
        Object a = now.getSourceObject();
        Object b = next.getSourceObject();
        if (a instanceof Object[] && b instanceof Object[]) {
            return java.util.Arrays.equals((Object[]) a, (Object[]) b);
        }
        return java.util.Objects.equals(a, b);
    }

    /** Whether this list has the keyboard. Package-private so ZoneListHarness can stand in. */
    boolean hasKeyboard() {
        return list.isFocusOwner();
    }

    /**
     * Reads out a row that changed under the cursor — queued, so it does not
     * cut off the confirmation of whatever changed it. Package-private so
     * ZoneListHarness can listen.
     */
    void announceChange(String text) {
        SpeechOutput speech = AccessibilityManager.getInstance().getSpeech();
        if (speech != null) {
            speech.speakQueued(text);
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

    /**
     * Says {@code text} in place of the zone's name and count the next time
     * this list takes the focus, once: a window's opening sentence, or the
     * row a jump landed on. It is spoken from the focus event itself, so it
     * cannot race the name-and-count announcement it replaces.
     */
    public void announceOnNextFocus(String text) {
        nextFocusAnnouncement = text;
    }

    /** What {@link #announceOnNextFocus} left waiting, if anything. Package-private for WhatsNewHarness. */
    String pendingFocusAnnouncement() {
        return nextFocusAnnouncement;
    }

    /** Selects a row and scrolls to it without the agent reading it out. */
    public void selectQuietly(int index) {
        isRefreshing = true;
        try {
            list.setSelectedIndex(index);
            list.ensureIndexIsVisible(index);
        } finally {
            isRefreshing = false;
        }
    }

    private void speak(String text) {
        SpeechOutput speech = AccessibilityManager.getInstance().getSpeech();
        if (speech != null) {
            speech.speak(text);
        }
    }
}
