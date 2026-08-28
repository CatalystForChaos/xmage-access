package xmageaccess.ui;

import mage.client.SessionHandler;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JPanel;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Drives GameActions against stand-in GamePanel/PlayAreaPanel objects. */
public class GameActionsHarness {

    /** Same field and method names GamePanel exposes. */
    public static class FakeGamePanel extends JPanel {
        private UUID gameId = UUID.randomUUID();
        private UUID playerId = UUID.randomUUID();
        private final Map<UUID, Object> players = new LinkedHashMap<>();
        private boolean holdingPriority = false;
        String lastMenuStates = "(none)";

        public void setMenuStates(boolean a, boolean b, boolean c, boolean hold) {
            lastMenuStates = a + "," + b + "," + c + ",hold=" + hold;
        }
        public void holdPriority(boolean hold) {
            holdingPriority = hold;
            SessionHandler.CALLS.add("holdPriority " + hold);
        }
    }

    /** Same checkbox field names PlayAreaPanel exposes. */
    public static class FakePlayArea extends JPanel {
        private final JCheckBoxMenuItem manaPoolMenuItem1 = new JCheckBoxMenuItem("", true);
        private final JCheckBoxMenuItem manaPoolMenuItem2 = new JCheckBoxMenuItem("", true);
        private final JCheckBoxMenuItem useFirstManaAbilityItem = new JCheckBoxMenuItem("", false);
        private final JCheckBoxMenuItem allowViewHandCardsMenuItem = new JCheckBoxMenuItem("", false);
    }

    /** Stands in for PlayerView. */
    public static class FakePlayerView {
        private final UUID id;
        private final String name;
        FakePlayerView(UUID id, String name) { this.id = id; this.name = name; }
        public UUID getPlayerId() { return id; }
        public String getName() { return name; }
    }

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
        FakeGamePanel panel = new FakeGamePanel();
        FakePlayArea ownArea = new FakePlayArea();
        panel.players.put(panel.playerId, ownArea);
        UUID opponentId = UUID.randomUUID();
        panel.players.put(opponentId, new FakePlayArea());

        GameActions actions = new GameActions(panel);

        System.out.println("Identity");
        check("gameId read from the panel", panel.gameId.equals(actions.gameId()));
        check("own playerId read from the panel", panel.playerId.equals(actions.ownPlayerId()));
        check("not watching while a playerId is set", !actions.isWatching());

        System.out.println("Skip actions");
        check("send() reports success", actions.send("PASS_PRIORITY_UNTIL_NEXT_TURN"));
        check("PASS_PRIORITY_UNTIL_NEXT_TURN reached SessionHandler",
                lastCall().startsWith("sendPlayerAction PASS_PRIORITY_UNTIL_NEXT_TURN game=" + panel.gameId));
        check("unknown action fails instead of throwing", !actions.send("NO_SUCH_ACTION"));

        System.out.println("Payloads");
        actions.send("ROLLBACK_TURNS", 2);
        check("rollback carries the turn count", lastCall().endsWith("data=2"));
        actions.send("VIEW_SIDEBOARD", opponentId);
        check("view sideboard carries the player id", lastCall().endsWith("data=" + opponentId));

        System.out.println("Toggles");
        check("mana auto payment starts on", actions.isManaAutoPayment());
        check("toggle returns the new state", !actions.toggleManaAutoPayment());
        check("MANA_AUTO_PAYMENT_OFF sent", lastCall().contains("MANA_AUTO_PAYMENT_OFF"));
        check("menu states pushed back to XMage", panel.lastMenuStates.startsWith("false,true,false"));
        check("use first mana ability starts off", !actions.isUseFirstManaAbility());
        check("toggling it sends ON", actions.toggleUseFirstManaAbility()
                && lastCall().contains("USE_FIRST_MANA_ABILITY_ON"));

        check("mana autopayment preference persisted",
                "false".equals(mage.client.dialog.PreferencesDialog.SAVED.get("gameManaAutopayment")));
        check("first mana ability preference persisted",
                "true".equals(mage.client.dialog.PreferencesDialog.SAVED.get("useFirstManaAbility")));

        System.out.println("Hold priority");
        check("starts off", !actions.isHoldingPriority());
        check("toggle turns it on", actions.toggleHoldPriority());
        check("GamePanel.holdPriority was called", lastCall().equals("holdPriority true"));
        check("hold state visible in menu sync", panel.lastMenuStates.endsWith("hold=true"));
        check("reading it back reflects the panel field", actions.isHoldingPriority());

        System.out.println("Leaving the game");
        actions.concedeGame();
        check("concede sends PlayerAction.CONCEDE", lastCall().contains("sendPlayerAction CONCEDE"));
        actions.concedeMatch();
        check("concede match calls quitMatch", lastCall().equals("quitMatch game=" + panel.gameId));
        actions.stopWatching();
        check("stop watching calls stopWatching", lastCall().equals("stopWatching game=" + panel.gameId));

        System.out.println("Player lookup");
        java.util.List<Object> views = new java.util.ArrayList<>();
        views.add(new FakePlayerView(panel.playerId, "Alice"));
        views.add(new FakePlayerView(opponentId, "Bob"));
        Map<String, UUID> byName = actions.playerIdsByName(views);
        check("names map to ids", panel.playerId.equals(byName.get("Alice"))
                && opponentId.equals(byName.get("Bob")));
        check("empty input is handled", actions.playerIdsByName(null).isEmpty());

        System.out.println("Missing game");
        FakeGamePanel noGame = new FakeGamePanel();
        noGame.gameId = null;
        check("no gameId means no action is sent", !new GameActions(noGame).send("CONCEDE"));

        System.out.println();
        System.out.println(failures == 0 ? "ALL CHECKS PASSED" : failures + " CHECK(S) FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }
}
