package mage.client;

import mage.constants.PlayerAction;
import mage.view.DraftPickView;
import mage.view.SimpleCardsView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Records what the agent sends, standing in for the real client session. */
public class SessionHandler {
    public static final List<String> CALLS = new ArrayList<>();

    /** Set to false to make a pick come back refused. */
    public static boolean pickAccepted = true;

    public static void sendPlayerAction(PlayerAction action, UUID gameId, Object data) {
        CALLS.add("sendPlayerAction " + action + " game=" + gameId + " data=" + data);
    }
    public static void quitMatch(UUID gameId) { CALLS.add("quitMatch game=" + gameId); }
    public static void stopWatching(UUID gameId) { CALLS.add("stopWatching game=" + gameId); }

    public static DraftPickView sendCardPick(UUID draftId, UUID cardId, Set<UUID> cardsHidden) {
        CALLS.add("sendCardPick draft=" + draftId + " card=" + cardId
                + " hidden=" + cardsHidden.size());
        return pickAccepted ? new DraftPickView(new SimpleCardsView()) : null;
    }

    public static void watchTournamentTable(UUID tableId) {
        CALLS.add("watchTournamentTable table=" + tableId);
    }
}
