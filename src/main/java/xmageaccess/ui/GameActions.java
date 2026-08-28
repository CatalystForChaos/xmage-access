package xmageaccess.ui;

import xmageaccess.util.Log;

import javax.swing.JCheckBoxMenuItem;
import java.awt.Component;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static xmageaccess.util.ReflectionUtils.*;

/**
 * Player actions that XMage otherwise only reaches through the mouse: the
 * right-click popup menu on a play area (skip actions, mana options, rollback,
 * concede, view deck/sideboard, hand-card permissions), and the skip hotkeys
 * F3-F11, which GamePanel binds with {@code WHEN_IN_FOCUSED_WINDOW} and which
 * therefore never fire while the accessible window has focus.
 *
 * <p>Everything routes through {@code SessionHandler.sendPlayerAction(
 * PlayerAction, UUID, Object)} — public and static — plus the public GamePanel
 * methods {@code holdPriority} and {@code setMenuStates}, so XMage's own menu
 * checkboxes stay in sync with what we send.
 *
 * <p>This is the same path PlayAreaPanel's popup menu takes: it sends the
 * action and nothing else. GamePanel's own button handlers additionally
 * highlight a skip button and play a sound, which is local UI feedback we have
 * no use for. One consequence is that "skip everything until next turn" works
 * here even though XMage's F6 does not — {@code btnEndTurnSkipStackActionPerformed}
 * has its body commented out and only logs an error, while the menu entry for
 * the same action still sends {@code PASS_PRIORITY_UNTIL_NEXT_TURN_SKIP_STACK}.
 */
class GameActions {

    private static final String SESSION_HANDLER = "mage.client.SessionHandler";
    private static final String PLAYER_ACTION = "mage.constants.PlayerAction";
    private static final String PREFERENCES = "mage.client.dialog.PreferencesDialog";

    // Preference keys XMage's own menu writes alongside the mana actions, so
    // the choice survives into the next session (PreferencesDialog.KEY_*).
    private static final String PREF_MANA_AUTOPAYMENT = "gameManaAutopayment";
    private static final String PREF_MANA_AUTOPAYMENT_ONLY_ONE = "gameManaAutopaymentOnlyOne";
    private static final String PREF_USE_FIRST_MANA_ABILITY = "useFirstManaAbility";

    private final Component gamePanel;

    GameActions(Component gamePanel) {
        this.gamePanel = gamePanel;
    }

    // ---- identity -------------------------------------------------------

    /** The running game's id, or null if the panel has no game attached. */
    UUID gameId() {
        Object id = findFieldDeep(gamePanel, "gameId");
        return id instanceof UUID ? (UUID) id : null;
    }

    /** Our own player id — null while watching, since spectators have none. */
    UUID ownPlayerId() {
        Object id = findFieldDeep(gamePanel, "playerId");
        return id instanceof UUID ? (UUID) id : null;
    }

    boolean isWatching() {
        return ownPlayerId() == null;
    }

    // ---- raw action plumbing --------------------------------------------

    /** Send a PlayerAction with no payload. */
    boolean send(String action) {
        return send(action, null);
    }

    /** Send a PlayerAction carrying a payload (a turn count, a player id, ...). */
    boolean send(String action, Object data) {
        UUID gameId = gameId();
        if (gameId == null) return false;

        Object playerAction = enumConstant(PLAYER_ACTION, action);
        Class<?> actionType = findClass(PLAYER_ACTION);
        if (playerAction == null || actionType == null) {
            Log.warn("GameActions", "PlayerAction." + action + " not found in this XMage build");
            return false;
        }

        boolean sent = callStaticVoid(SESSION_HANDLER, "sendPlayerAction",
                new Class<?>[]{actionType, UUID.class, Object.class},
                playerAction, gameId, data);
        if (!sent) Log.warn("GameActions", "sendPlayerAction(" + action + ") failed");
        return sent;
    }

    /** Call a single-UUID static SessionHandler method such as quitMatch. */
    private boolean sendGameCall(String methodName) {
        UUID gameId = gameId();
        if (gameId == null) return false;
        boolean sent = callStaticVoid(SESSION_HANDLER, methodName,
                new Class<?>[]{UUID.class}, gameId);
        if (!sent) Log.warn("GameActions", "SessionHandler." + methodName + " failed");
        return sent;
    }

    // ---- leaving the game -----------------------------------------------

    /**
     * Concede the current game. XMage's own menu routes CLIENT_CONCEDE_GAME
     * through MageFrame, which just sends PlayerAction.CONCEDE — we send that
     * directly and skip the inaccessible confirmation popup.
     */
    boolean concedeGame() {
        return send("CONCEDE");
    }

    /** Concede the whole match. MageFrame maps this to SessionHandler.quitMatch. */
    boolean concedeMatch() {
        return sendGameCall("quitMatch");
    }

    boolean stopWatching() {
        return sendGameCall("stopWatching");
    }

    // ---- toggles ---------------------------------------------------------

    /**
     * Reads the state of one of the play area's checkbox menu items. XMage
     * keeps these in sync across all players' panels, so any panel will do;
     * we prefer our own.
     */
    private Boolean menuItemState(String fieldName) {
        for (Object playAreaPanel : playAreaPanels()) {
            JCheckBoxMenuItem item = findFieldTyped(playAreaPanel, fieldName, JCheckBoxMenuItem.class);
            if (item != null) return item.getState();
        }
        return null;
    }

    private List<Object> playAreaPanels() {
        List<Object> panels = new ArrayList<>();
        Map<?, ?> players = findFieldTyped(gamePanel, "players", Map.class);
        if (players == null) return panels;

        UUID own = ownPlayerId();
        Object ownPanel = own != null ? players.get(own) : null;
        if (ownPanel != null) panels.add(ownPanel);
        for (Object panel : players.values()) {
            if (panel != null && panel != ownPanel) panels.add(panel);
        }
        return panels;
    }

    boolean isHoldingPriority() {
        Object held = findFieldDeep(gamePanel, "holdingPriority");
        return held instanceof Boolean && (Boolean) held;
    }

    boolean isManaAutoPayment() {
        Boolean state = menuItemState("manaPoolMenuItem1");
        return state == null || state;
    }

    boolean isManaAutoPaymentRestricted() {
        Boolean state = menuItemState("manaPoolMenuItem2");
        return state == null || state;
    }

    boolean isUseFirstManaAbility() {
        Boolean state = menuItemState("useFirstManaAbilityItem");
        return state != null && state;
    }

    boolean isAllowingHandRequests() {
        Boolean state = menuItemState("allowViewHandCardsMenuItem");
        return state != null && state;
    }

    /**
     * Pushes the four menu states back into XMage so its own checkboxes match
     * what we just sent. Mirrors what PlayAreaPanel's listeners do.
     */
    private void syncMenuStates(boolean manaAuto, boolean manaRestricted,
                                boolean useFirstMana, boolean holdPriority) {
        callMethodWithArgs(gamePanel, "setMenuStates",
                new Class<?>[]{boolean.class, boolean.class, boolean.class, boolean.class},
                manaAuto, manaRestricted, useFirstMana, holdPriority);
    }

    /** Toggle "hold priority". Returns the new state. */
    boolean toggleHoldPriority() {
        boolean next = !isHoldingPriority();
        syncMenuStates(isManaAutoPayment(), isManaAutoPaymentRestricted(),
                isUseFirstManaAbility(), next);
        // holdPriority() sends HOLD_PRIORITY / UNHOLD_PRIORITY itself.
        callMethodWithArg(gamePanel, "holdPriority", boolean.class, next);
        return next;
    }

    /** Toggle automatic mana payment. Returns the new state. */
    boolean toggleManaAutoPayment() {
        boolean next = !isManaAutoPayment();
        savePreference(PREF_MANA_AUTOPAYMENT, next);
        syncMenuStates(next, isManaAutoPaymentRestricted(),
                isUseFirstManaAbility(), isHoldingPriority());
        send(next ? "MANA_AUTO_PAYMENT_ON" : "MANA_AUTO_PAYMENT_OFF");
        return next;
    }

    /** Toggle "no automatic usage for mana already in the pool". */
    boolean toggleManaAutoPaymentRestricted() {
        boolean next = !isManaAutoPaymentRestricted();
        savePreference(PREF_MANA_AUTOPAYMENT_ONLY_ONE, next);
        syncMenuStates(isManaAutoPayment(), next,
                isUseFirstManaAbility(), isHoldingPriority());
        send(next ? "MANA_AUTO_PAYMENT_RESTRICTED_ON" : "MANA_AUTO_PAYMENT_RESTRICTED_OFF");
        return next;
    }

    /** Toggle "use first mana ability when tapping lands". */
    boolean toggleUseFirstManaAbility() {
        boolean next = !isUseFirstManaAbility();
        savePreference(PREF_USE_FIRST_MANA_ABILITY, next);
        syncMenuStates(isManaAutoPayment(), isManaAutoPaymentRestricted(),
                next, isHoldingPriority());
        send(next ? "USE_FIRST_MANA_ABILITY_ON" : "USE_FIRST_MANA_ABILITY_OFF");
        return next;
    }

    /**
     * Persists a mana option the way XMage's own menu does, so the choice
     * still holds in the next session rather than only the current game.
     */
    private void savePreference(String key, boolean value) {
        callStaticVoid(PREFERENCES, "saveValue",
                new Class<?>[]{String.class, String.class}, key, value ? "true" : "false");
    }

    /** Toggle whether other users may ask to see our hand. */
    boolean toggleHandRequestsAllowed() {
        boolean next = !isAllowingHandRequests();
        JCheckBoxMenuItem item = null;
        for (Object panel : playAreaPanels()) {
            item = findFieldTyped(panel, "allowViewHandCardsMenuItem", JCheckBoxMenuItem.class);
            if (item != null) break;
        }
        if (item != null) item.setState(next);
        send(next ? "PERMISSION_REQUESTS_ALLOWED_ON" : "PERMISSION_REQUESTS_ALLOWED_OFF");
        return next;
    }

    // ---- other players ---------------------------------------------------

    /**
     * Player names mapped to their ids, taken from the game view so the name
     * and the id always come from the same record.
     *
     * @param playerViews the PlayerView objects of the current game view
     */
    Map<String, UUID> playerIdsByName(List<?> playerViews) {
        Map<String, UUID> result = new LinkedHashMap<>();
        if (playerViews == null) return result;
        for (Object player : playerViews) {
            Object id = callMethod(player, "getPlayerId");
            String name = callString(player, "getName");
            if (id instanceof UUID && name != null) result.put(name, (UUID) id);
        }
        return result;
    }
}
