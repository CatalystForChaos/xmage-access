package xmageaccess.ui;

import mage.client.SessionHandler;
import mage.client.draft.DraftPanel;
import mage.view.CardView;

import java.util.List;

/**
 * Drives AccessibleDraftWindow against a stand-in draft panel.
 *
 * What it pins down: that the booster is read off the card grid by asking
 * each card panel for its CardView, that the picks come from the client's own
 * CardsList rather than the nameless SimpleCardViews the server returns with
 * a pick, that XMage's own {@code isAllowedToPick} really blocks a pick, that
 * an allowed pick reaches {@code SessionHandler.sendCardPick} with the draft
 * id, the card id and the hidden-card set, and that the panel's private
 * picked-cards redraw is invoked afterwards — but not when the pick came back
 * refused.
 */
public class DraftWindowHarness {

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
        DraftPanel panel = new DraftPanel();
        CardView bolt = new CardView("Lightning Bolt", "{R}");
        CardView giant = new CardView("Frost Giant", "{4}{U}");
        CardView alreadyPicked = new CardView("Counterspell", "{U}{U}");
        panel.booster().addCard(bolt);
        panel.booster().addCard(giant);
        panel.picks().cards().put(alreadyPicked.getId(), alreadyPicked);

        AccessibleDraftWindow window = new AccessibleDraftWindow(panel);
        window.stopPolling();

        System.out.println("Reading the booster");
        List<ZoneItem> booster = window.readBoosterCards();
        check("both cards are read off the grid", booster.size() == 2);
        check("the label leads with the card name",
                booster.get(0).getDisplayName().startsWith("Lightning Bolt"));
        check("the mana cost is spoken with it",
                booster.get(0).getDisplayName().length() > "Lightning Bolt".length());
        check("the card view rides along for the detail reader",
                booster.get(0).getSourceObject() == bolt);
        check("booster cards are pickable",
                booster.get(0).getActionType() == ZoneItem.ActionType.PICK_CARD);

        System.out.println("Reading the picks");
        List<ZoneItem> picks = window.readPickedCards();
        check("the pool comes from the client's own card list", picks.size() == 1);
        check("and it has a name, unlike the server's pick view",
                picks.get(0).getDisplayName().startsWith("Counterspell"));
        check("an already picked card cannot be picked again",
                picks.get(0).getActionType() == ZoneItem.ActionType.NONE);

        System.out.println("Picking");
        SessionHandler.CALLS.clear();
        panel.allowedToPick = false;
        window.pickCard(booster.get(0));
        check("XMage's own isAllowedToPick blocks the pick",
                SessionHandler.CALLS.isEmpty());

        panel.allowedToPick = true;
        window.pickCard(booster.get(0));
        check("an allowed pick reaches sendCardPick with the draft id",
                lastCall().startsWith("sendCardPick draft=" + panel.draftId()));
        check("and with the card under the cursor",
                lastCall().contains("card=" + bolt.getId()));
        check("the hidden-card set travels with it", lastCall().endsWith("hidden=0"));
        check("XMage's picked-cards area is redrawn", panel.lastPickedAreaLoad != null);

        System.out.println("A pick the server refuses");
        panel.lastPickedAreaLoad = null;
        SessionHandler.pickAccepted = false;
        window.pickCard(booster.get(1));
        check("the call is still made", lastCall().contains("card=" + giant.getId()));
        check("but nothing is redrawn on a refusal", panel.lastPickedAreaLoad == null);

        window.dispose();

        System.out.println();
        System.out.println(failures == 0 ? "ALL CHECKS PASSED" : failures + " CHECK(S) FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }
}
