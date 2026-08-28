package xmageaccess.ui;

import mage.client.SessionHandler;
import mage.client.tournament.TournamentPanel;

import java.util.List;
import java.util.UUID;

/**
 * Drives AccessibleTournamentWindow against a stand-in tournament panel.
 *
 * What it pins down: that the standings are read out of the players table
 * with their column names, that the matches are read from the model rather
 * than the table so the three hidden id columns come along, that a match is
 * offered for watching exactly when XMage's own action cell says "Watch",
 * and that watching hands that row's table id to
 * {@code SessionHandler.watchTournamentTable} — the same call XMage's own
 * action button makes.
 */
public class TournamentWindowHarness {

    private static int failures = 0;

    private static void check(String what, boolean ok) {
        System.out.println((ok ? "  PASS  " : "  FAIL  ") + what);
        if (!ok) failures++;
    }

    private static String lastCall() {
        return SessionHandler.CALLS.isEmpty() ? "(none)"
                : SessionHandler.CALLS.get(SessionHandler.CALLS.size() - 1);
    }

    public static void main(String[] args) {
        String liveTable = UUID.randomUUID().toString();
        String doneTable = UUID.randomUUID().toString();

        TournamentPanel panel = new TournamentPanel();
        panel.matches().addRow("1", "Alice vs Bob", "Dueling", "", liveTable,
                UUID.randomUUID().toString(), UUID.randomUUID().toString());
        panel.matches().addRow("1", "Carol vs Dave", "Finished", "2-1", doneTable,
                UUID.randomUUID().toString(), UUID.randomUUID().toString());

        AccessibleTournamentWindow window = new AccessibleTournamentWindow(panel);
        window.stopPolling();

        System.out.println("Standings");
        List<ZoneItem> standings = window.readStandings();
        check("every player is a row", standings.size() == 2);
        check("the row carries its column names",
                standings.get(0).getDisplayName().contains("Player: Alice"));
        check("and the score with it",
                standings.get(0).getDisplayName().contains("Score: 2-0"));

        System.out.println("Matches");
        List<ZoneItem> matches = window.readMatches();
        check("both matches are listed", matches.size() == 2);
        check("the players are named",
                matches.get(0).getDisplayName().contains("Alice vs Bob"));
        check("the state is spoken",
                matches.get(0).getDisplayName().contains("Dueling"));
        check("a duelling match is offered for watching",
                matches.get(0).getActionType() == ZoneItem.ActionType.WATCH_MATCH);
        check("and says so out loud",
                matches.get(0).getDisplayName().contains("can be watched"));
        check("a finished match is not",
                matches.get(1).getActionType() == ZoneItem.ActionType.NONE);
        check("the hidden table id column rides along",
                liveTable.equals(matches.get(0).getSourceObject()));

        System.out.println("Watching");
        SessionHandler.CALLS.clear();
        window.watchMatch(matches.get(0));
        check("watching hands the table id to XMage's own call",
                lastCall().equals("watchTournamentTable table=" + liveTable));

        window.dispose();

        System.out.println();
        System.out.println(failures == 0 ? "ALL CHECKS PASSED" : failures + " CHECK(S) FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }
}
