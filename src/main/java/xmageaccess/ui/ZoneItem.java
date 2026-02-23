package xmageaccess.ui;

/**
 * Data holder for a single item in a game zone list.
 * toString() returns displayName so screen readers read it from JList automatically.
 */
public class ZoneItem {

    public enum ActionType {
        CLICK_BUTTON,
        SEND_CARD_UUID,
        SEND_UUID_DIRECT,
        SEND_ABILITY,
        SEND_MANA,
        ADD_TO_DECK,
        REMOVE_FROM_DECK,
        REMOVE_FROM_SIDEBOARD,
        MOVE_TO_SIDEBOARD,
        MOVE_TO_DECK,
        NONE
    }

    private final String displayName;
    private final String detailText;
    private final Object sourceObject;
    private final ActionType actionType;

    public ZoneItem(String displayName, String detailText, Object sourceObject, ActionType actionType) {
        this.displayName = displayName;
        this.detailText = detailText;
        this.sourceObject = sourceObject;
        this.actionType = actionType;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDetailText() {
        return detailText;
    }

    public Object getSourceObject() {
        return sourceObject;
    }

    public ActionType getActionType() {
        return actionType;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
