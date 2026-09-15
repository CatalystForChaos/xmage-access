package xmageaccess.ui;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Drives {@link ZoneListPanel}'s refresh, which every accessible window
 * leans on and every polling timer calls.
 *
 * What it pins down: that a refresh which would change nothing does not
 * touch the list at all. The zones are rebuilt on a timer — once a second
 * in the draft, every five in the lobby and the game — and clearing and
 * refilling a JList fires accessibility events at the screen reader whether
 * or not anything is different, which is what made it talk over itself
 * while you were reading. Identity of the items already in the model is the
 * test: if they are still the very same objects, nothing was rebuilt.
 *
 * It also pins what does count as a change. A row that reads the same but
 * points at a different object has to be replaced, or Enter would act on
 * the previous game.
 */
public class ZoneListHarness {

    private static int failures = 0;

    private static void check(String what, boolean ok) {
        System.out.println((ok ? "  PASS  " : "  FAIL  ") + what);
        if (!ok) failures++;
    }

    private static ZoneItem item(String name, String detail, Object source) {
        return new ZoneItem(name, detail, source, ZoneItem.ActionType.NONE);
    }

    private static List<ZoneItem> rows(ZoneItem... items) {
        return new ArrayList<>(Arrays.asList(items));
    }

    /** True while the model still holds exactly the objects it was given. */
    private static boolean modelHolds(ZoneListPanel zone, List<ZoneItem> expected) {
        if (zone.getList().getModel().getSize() != expected.size()) return false;
        for (int i = 0; i < expected.size(); i++) {
            if (zone.getList().getModel().getElementAt(i) != expected.get(i)) return false;
        }
        return true;
    }

    public static void main(String[] args) throws Exception {
        final ZoneListPanel zone = new ZoneListPanel("Open Games");
        final Object tableA = new Object();
        final Object tableB = new Object();

        final List<ZoneItem> first = rows(
                item("Alice's table", "Alice, waiting", tableA),
                item("Bob's table", "Bob, duelling", tableB));

        SwingUtilities.invokeAndWait(() -> zone.updateItems(first));
        check("the first refresh fills the list", modelHolds(zone, first));

        System.out.println("A refresh that changes nothing");
        SwingUtilities.invokeAndWait(() -> zone.getList().setSelectedIndex(1));
        SwingUtilities.invokeAndWait(() -> zone.updateItems(rows(
                item("Alice's table", "Alice, waiting", tableA),
                item("Bob's table", "Bob, duelling", tableB))));
        check("leaves the very same rows in place", modelHolds(zone, first));
        check("and the selection where it was", zone.getList().getSelectedIndex() == 1);

        System.out.println("A refresh that changes something");
        final List<ZoneItem> renamed = rows(
                item("Alice's table", "Alice, waiting", tableA),
                item("Bob's table (full)", "Bob, duelling", tableB));
        SwingUtilities.invokeAndWait(() -> zone.updateItems(renamed));
        check("different text is taken", modelHolds(zone, renamed));

        final List<ZoneItem> moved = rows(
                item("Alice's table", "Alice, waiting", tableA),
                item("Bob's table (full)", "Bob, duelling", new Object()));
        SwingUtilities.invokeAndWait(() -> zone.updateItems(moved));
        check("so is a row that reads the same but points elsewhere",
                modelHolds(zone, moved));

        final List<ZoneItem> detailed = rows(
                item("Alice's table", "Alice, waiting, rated", tableA),
                moved.get(1));
        SwingUtilities.invokeAndWait(() -> zone.updateItems(detailed));
        check("so is a changed detail text, which D reads out",
                modelHolds(zone, detailed));

        final List<ZoneItem> shorter = rows(detailed.get(0));
        SwingUtilities.invokeAndWait(() -> zone.updateItems(shorter));
        check("and a list that lost a row", modelHolds(zone, shorter));

        System.out.println("Edge cases");
        SwingUtilities.invokeAndWait(() -> zone.updateItems(new ArrayList<>()));
        check("an empty refresh empties the list",
                zone.getList().getModel().getSize() == 0);
        SwingUtilities.invokeAndWait(() -> zone.updateItems(new ArrayList<>()));
        check("emptying an empty list changes nothing",
                zone.getList().getModel().getSize() == 0);

        System.out.println();
        System.out.println(failures == 0 ? "ALL CHECKS PASSED" : failures + " CHECK(S) FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }
}
