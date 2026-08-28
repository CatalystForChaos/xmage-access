package mage.client;
import mage.constants.PlayerAction;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Records what the agent sends, standing in for the real client session. */
public class SessionHandler {
    public static final List<String> CALLS = new ArrayList<>();
    public static void sendPlayerAction(PlayerAction action, UUID gameId, Object data) {
        CALLS.add("sendPlayerAction " + action + " game=" + gameId + " data=" + data);
    }
    public static void quitMatch(UUID gameId) { CALLS.add("quitMatch game=" + gameId); }
    public static void stopWatching(UUID gameId) { CALLS.add("stopWatching game=" + gameId); }
}
