package xmageaccess.ui;

import mage.client.dialog.TableWaitingDialog;

import javax.swing.JDesktopPane;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Drives AccessibleTableWaitingWindow against a stand-in waiting dialog.
 *
 * What it pins down: that the seats are read from the table model by model
 * column, a taken seat by player and type, with rating and history left for
 * D, and an open one as empty; that game type and deck type come out of the
 * title XMage sets, split at the last separator, and are left out before it
 * is set; that Start is offered only to the table owner and presses XMage's
 * own button only while it is enabled; that Leave presses Cancel and notices
 * when XMage keeps the dialog open; what is said as players come and go; and
 * when the window lets UIWatcher hand the keyboard on after it closes.
 */
public class TableWaitingWindowHarness {

    private static int failures = 0;

    private static void check(String what, boolean ok) {
        System.out.println((ok ? "  PASS  " : "  FAIL  ") + what);
        if (!ok) failures++;
    }

    /** XMage's dialog sits on the desktop; closing it takes it off. */
    private static TableWaitingDialog onDesktop(TableWaitingDialog dialog) {
        new JDesktopPane().add(dialog);
        return dialog;
    }

    private static boolean hasRow(List<ZoneItem> items, String text) {
        for (ZoneItem item : items) {
            if (item.getDisplayName().equals(text)) return true;
        }
        return false;
    }

    private static boolean anyRowStartsWith(List<ZoneItem> items, String prefix) {
        for (ZoneItem item : items) {
            if (item.getDisplayName().startsWith(prefix)) return true;
        }
        return false;
    }

    public static void main(String[] args) {
        System.out.println("The title");
        String[] types = AccessibleTableWaitingWindow.splitTitle(
                "Waiting for players - Constructed - Standard / Two Player Duel");
        check("deck type and game type come out of XMage's title",
                types != null && types[0].equals("Constructed - Standard")
                        && types[1].equals("Two Player Duel"));
        types = AccessibleTableWaitingWindow.splitTitle(
                "Waiting for players - Limited A / B / Two Player Duel 3 Rounds");
        check("a separator inside the deck type stays with the deck type",
                types != null && types[0].equals("Limited A / B")
                        && types[1].equals("Two Player Duel 3 Rounds"));
        check("nothing before XMage has read the table",
                AccessibleTableWaitingWindow.splitTitle("Waiting for players") == null);

        System.out.println("A match table you own, one seat open");
        TableWaitingDialog dialog = onDesktop(new TableWaitingDialog(true, false));
        dialog.takenSeat("Sightless Mage", "Human", 1500, "Matches: 3");
        dialog.openSeat();
        AccessibleTableWaitingWindow window = new AccessibleTableWaitingWindow(dialog);
        window.stopPolling();

        List<ZoneItem> seats = window.readSeats();
        check("every seat is a row", seats.size() == 2);
        check("a taken seat names its player and type",
                seats.get(0).getDisplayName().equals("Seat 1: Sightless Mage, Human"));
        check("rating and history wait for D",
                seats.get(0).getDetailText().contains("Rating 1500")
                        && seats.get(0).getDetailText().contains("History: Matches: 3"));
        check("an open seat says so", seats.get(1).getDisplayName().equals("Seat 2: empty"));

        List<ZoneItem> table = window.readTableItems();
        check("the count of taken seats is a row", hasRow(table, "1 of 2 seats taken"));
        check("the table is waiting", hasRow(table, "Waiting for players"));
        check("and it is yours", hasRow(table, "You own this table"));
        check("no types before XMage has set the title", !anyRowStartsWith(table, "Game type"));
        dialog.tableRead("Constructed - Standard", "Two Player Duel");
        table = window.readTableItems();
        check("the types once it has",
                hasRow(table, "Game type: Two Player Duel")
                        && hasRow(table, "Deck type: Constructed - Standard"));

        List<ZoneItem> actions = window.readActions();
        check("the owner is offered Start, not yet",
                actions.get(0).getDisplayName().equals("Start the game, not yet: waiting for players"));
        check("and Leave", hasRow(actions, "Leave the table"));
        check("Start on a table that is not ready presses nothing",
                !window.start() && dialog.startClicks == 0);

        String opening = window.openingSentence();
        check("the opening sentence gives the seats and the state",
                opening.contains("1 of 2 seats taken") && opening.contains("Waiting for players"));
        check("and names no key", !opening.contains("Ctrl"));
        check("a table that closes while waiting hands the keyboard on",
                window.handsKeyboardOnWhenClosed());

        System.out.println("Players coming and going");
        List<String> alone = Arrays.asList("Sightless Mage", "");
        List<String> full = Arrays.asList("Sightless Mage", "Bob");
        check("a player who joins is named, and the owner hears the table is ready",
                "Bob joined. Ready to start.".equals(
                        AccessibleTableWaitingWindow.changeAnnouncement(alone, full, false, true, true)));
        check("a player who leaves is named",
                "Bob left.".equals(
                        AccessibleTableWaitingWindow.changeAnnouncement(full, alone, true, false, true)));
        check("swapping seats is no news",
                AccessibleTableWaitingWindow.changeAnnouncement(
                        full, Arrays.asList("Bob", "Sightless Mage"), true, true, true) == null);
        check("a player who does not own the table hears no ready",
                "Bob joined.".equals(
                        AccessibleTableWaitingWindow.changeAnnouncement(alone, full, false, true, false)));
        check("the seats XMage fills on its first update are no news",
                AccessibleTableWaitingWindow.changeAnnouncement(
                        Collections.<String>emptyList(), full, false, true, true) == null);

        System.out.println("Starting");
        dialog.setSeat(1, "Computer", "Computer - mad");
        dialog.ready(true);
        window.poll();
        check("the second seat is taken now",
                window.readSeats().get(1).getDisplayName().equals("Seat 2: Computer, Computer - mad"));
        check("a computer player's rating of 0 is left out",
                !window.readSeats().get(1).getDetailText().contains("Rating"));
        check("Start reads as ready once the table is",
                window.readActions().get(0).getDisplayName().equals("Start the game"));
        check("Start presses XMage's own button", window.start() && dialog.startClicks == 1);
        check("which closes the dialog", !window.isDialogOpen());
        window.poll();
        check("a table that was ready leaves the keyboard to the game",
                !window.handsKeyboardOnWhenClosed());

        System.out.println("A tournament table you joined");
        TableWaitingDialog joined = onDesktop(new TableWaitingDialog(false, true));
        joined.takenSeat("Alice", "Human", 1500, "");
        joined.takenSeat("Sightless Mage", "Human", 1500, "");
        AccessibleTableWaitingWindow guest = new AccessibleTableWaitingWindow(joined);
        guest.stopPolling();
        check("a guest is not offered Start",
                guest.readActions().size() == 1 && hasRow(guest.readActions(), "Leave the table"));
        check("the table knows it is not yours", hasRow(guest.readTableItems(), "Another player owns this table"));
        joined.ready(true);
        guest.poll();
        check("a guest hears who starts it",
                hasRow(guest.readTableItems(), "Ready, waiting for the table owner to start"));
        check("and the sentence says it is a tournament",
                guest.openingSentence().startsWith("Waiting room for a tournament"));

        System.out.println("Leaving");
        joined.leaveAllowed = false;
        check("when XMage keeps the dialog open, leaving says it failed",
                !guest.leave() && joined.cancelClicks == 1 && guest.isDialogOpen());
        check("and the keyboard is left to the tournament that is starting",
                !guest.handsKeyboardOnWhenClosed());
        joined.leaveAllowed = true;
        check("Leave presses XMage's Cancel",
                guest.leave() && joined.cancelClicks == 2 && !guest.isDialogOpen());
        check("after leaving the keyboard is handed on, ready table or not",
                guest.handsKeyboardOnWhenClosed());

        System.out.println("A tournament table you own");
        TableWaitingDialog own = onDesktop(new TableWaitingDialog(true, true));
        own.takenSeat("Sightless Mage", "Human", 1500, "");
        own.takenSeat("Computer", "Computer - draftbot", 0, "");
        own.ready(true);
        AccessibleTableWaitingWindow host = new AccessibleTableWaitingWindow(own);
        host.stopPolling();
        check("its owner starts the tournament",
                host.readActions().get(0).getDisplayName().equals("Start the tournament"));
        check("and is told it is ready", hasRow(host.readTableItems(), "Ready to start"));

        window.dispose();
        guest.dispose();
        host.dispose();

        System.out.println();
        System.out.println(failures == 0 ? "ALL CHECKS PASSED" : failures + " CHECK(S) FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }
}
