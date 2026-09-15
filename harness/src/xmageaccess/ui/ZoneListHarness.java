package xmageaccess.ui;

import javax.accessibility.AccessibleContext;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Drives {@link ZoneListPanel}'s refresh, which every accessible window
 * leans on and every polling timer calls.
 *
 * What it pins down: that a refresh tells the screen reader as little as it
 * can. What a screen reader hears depends on how the list model changes.
 * Clearing and refilling moves the selection off its row and back, and
 * JList's accessible context reports each move as a new active descendant.
 * So this counts the list selection events and the accessible selection and
 * active-descendant changes a refresh fires, and shows there are none when
 * rows are unchanged, replaced in place, added at the end, or removed below
 * the cursor. Identity of the items in the model shows which rows were
 * replaced: an unchanged row is still the very same object.
 *
 * It also pins what counts as a change — a row that reads the same but
 * points at a different object is replaced, or Enter would act on the
 * previous game; a button row rebuilt around an equal array is not — and
 * what the agent reads out: a row standing for something that changed under
 * the cursor while the list has the keyboard, never an information row such
 * as the draft clock.
 */
public class ZoneListHarness {

    private static int failures = 0;

    private static void check(String what, boolean ok) {
        System.out.println((ok ? "  PASS  " : "  FAIL  ") + what);
        if (!ok) failures++;
    }

    /** A zone that can pretend to have the keyboard and records what it would say. */
    static class ListeningZone extends ZoneListPanel {
        boolean keyboard = false;
        final List<String> said = new ArrayList<>();

        ListeningZone(String name) {
            super(name);
        }

        @Override
        boolean hasKeyboard() {
            return keyboard;
        }

        @Override
        void announceChange(String text) {
            said.add(text);
        }
    }

    private static ZoneItem item(String name, String detail, Object source) {
        return new ZoneItem(name, detail, source, ZoneItem.ActionType.NONE);
    }

    private static ZoneItem copy(ZoneItem row) {
        return new ZoneItem(row.getDisplayName(), row.getDetailText(),
                row.getSourceObject(), row.getActionType());
    }

    private static List<ZoneItem> rows(ZoneItem... items) {
        return new ArrayList<>(Arrays.asList(items));
    }

    private static ZoneItem shown(ZoneListPanel zone, int index) {
        return zone.getList().getModel().getElementAt(index);
    }

    private static int size(ZoneListPanel zone) {
        return zone.getList().getModel().getSize();
    }

    private static int selected(ZoneListPanel zone) {
        return zone.getList().getSelectedIndex();
    }

    private static void refresh(final ZoneListPanel zone, final List<ZoneItem> items) throws Exception {
        SwingUtilities.invokeAndWait(() -> zone.updateItems(items));
    }

    public static void main(String[] args) throws Exception {
        final ListeningZone zone = new ListeningZone("Open Games");
        final int[] events = {0};
        zone.getList().addListSelectionListener(e -> events[0]++);
        zone.getList().getAccessibleContext().addPropertyChangeListener(e -> {
            String property = e.getPropertyName();
            if (AccessibleContext.ACCESSIBLE_ACTIVE_DESCENDANT_PROPERTY.equals(property)
                    || AccessibleContext.ACCESSIBLE_SELECTION_PROPERTY.equals(property)) {
                events[0]++;
            }
        });

        final Object tableA = new Object();
        final Object tableB = new Object();
        final Object tableC = new Object();
        final List<ZoneItem> first = rows(
                item("Alice's table", "Alice, waiting", tableA),
                item("Bob's table", "Bob, duelling", tableB),
                item("Carol's table", "Carol, waiting", tableC));

        refresh(zone, first);
        check("the first refresh fills the list", size(zone) == 3
                && shown(zone, 0) == first.get(0) && shown(zone, 2) == first.get(2));
        check("and puts the selection on the first row", selected(zone) == 0);

        System.out.println("A refresh that changes nothing");
        SwingUtilities.invokeAndWait(() -> zone.getList().setSelectedIndex(1));
        events[0] = 0;
        refresh(zone, rows(copy(first.get(0)), copy(first.get(1)), copy(first.get(2))));
        check("leaves the very same rows in place", shown(zone, 0) == first.get(0)
                && shown(zone, 1) == first.get(1) && shown(zone, 2) == first.get(2));
        check("and tells the screen reader nothing", events[0] == 0);

        System.out.println("A row that changed");
        final ZoneItem bobFull = item("Bob's table (full)", "Bob, duelling", tableB);
        refresh(zone, rows(copy(first.get(0)), bobFull, copy(first.get(2))));
        check("is replaced in place, and only that row", shown(zone, 0) == first.get(0)
                && shown(zone, 1) == bobFull && shown(zone, 2) == first.get(2));
        check("with no selection event, though it is the selected row",
                events[0] == 0 && selected(zone) == 1);

        final ZoneItem bobElsewhere = item("Bob's table (full)", "Bob, duelling", new Object());
        refresh(zone, rows(copy(first.get(0)), bobElsewhere, copy(first.get(2))));
        check("a row that reads the same but points elsewhere is replaced",
                shown(zone, 1) == bobElsewhere);

        final ZoneItem aliceRated = item("Alice's table", "Alice, waiting, rated", tableA);
        refresh(zone, rows(aliceRated, copy(bobElsewhere), copy(first.get(2))));
        check("so is a changed detail text, which D reads out", shown(zone, 0) == aliceRated);

        System.out.println("Rows added and removed at the end");
        events[0] = 0;
        final ZoneItem dave = item("Dave's table", "Dave, waiting", new Object());
        refresh(zone, rows(copy(aliceRated), copy(bobElsewhere), copy(first.get(2)), dave));
        check("a row added at the end is appended", size(zone) == 4 && shown(zone, 3) == dave);
        check("and leaves the selection alone, with no event",
                selected(zone) == 1 && events[0] == 0);
        refresh(zone, rows(copy(aliceRated), copy(bobElsewhere)));
        check("rows removed below the selection leave it alone, with no event",
                size(zone) == 2 && selected(zone) == 1 && events[0] == 0);
        refresh(zone, rows(copy(aliceRated)));
        check("when the selected row itself goes, the selection moves to the nearest row left",
                size(zone) == 1 && selected(zone) == 0);

        System.out.println("What the agent reads out");
        zone.keyboard = true;
        zone.said.clear();
        refresh(zone, rows(item("Alice's table, started", "Alice, duelling", tableA)));
        check("a row that stands for something is read when it changes under the cursor",
                zone.said.equals(Arrays.asList("Alice's table, started")));
        zone.said.clear();
        refresh(zone, rows(item("Alice's table, started", "Alice, duelling, rated", tableA)));
        check("but not when only its detail changed, which the row does not say",
                zone.said.isEmpty());
        refresh(zone, rows(item("Time remaining: 0:42", "Time remaining: 0:42", null)));
        zone.said.clear();
        refresh(zone, rows(item("Time remaining: 0:41", "Time remaining: 0:41", null)));
        check("an information row, like the draft clock, changes silently",
                zone.said.isEmpty());
        zone.keyboard = false;
        refresh(zone, rows(item("Alice's table, finished", "Alice", tableA)));
        check("and nothing is read while the list does not have the keyboard",
                zone.said.isEmpty());

        System.out.println("Button rows");
        final ZoneListPanel actions = new ZoneListPanel("Actions");
        final JButton ok = new JButton("OK");
        final ZoneItem okRow = new ZoneItem("OK", "Button: OK",
                new Object[]{ok, "linkLeft"}, ZoneItem.ActionType.CLICK_BUTTON);
        refresh(actions, rows(okRow));
        refresh(actions, rows(new ZoneItem("OK", "Button: OK",
                new Object[]{ok, "linkLeft"}, ZoneItem.ActionType.CLICK_BUTTON)));
        check("a button row rebuilt around an equal array is left alone",
                shown(actions, 0) == okRow);

        System.out.println("Edge cases");
        refresh(zone, new ArrayList<ZoneItem>());
        check("an empty refresh empties the list", size(zone) == 0);
        refresh(zone, new ArrayList<ZoneItem>());
        check("emptying an empty list changes nothing", size(zone) == 0);
        boolean nullTaken;
        try {
            refresh(zone, null);
            nullTaken = size(zone) == 0;
        } catch (Exception e) {
            nullTaken = false;
        }
        check("a null refresh counts as empty rather than failing", nullTaken);

        System.out.println();
        System.out.println(failures == 0 ? "ALL CHECKS PASSED" : failures + " CHECK(S) FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }
}
