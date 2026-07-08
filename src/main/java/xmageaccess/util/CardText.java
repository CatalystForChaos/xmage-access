package xmageaccess.util;

import java.util.List;

import static xmageaccess.util.ReflectionUtils.*;
import static xmageaccess.util.TextUtils.*;

/**
 * Shared speech formatting for XMage CardView objects (accessed via reflection).
 */
public final class CardText {

    private CardText() {}

    /**
     * Full spoken description of a card: name, mana cost, types,
     * power/toughness for creatures, and rules text.
     */
    public static String formatCardDetailed(Object cardView) {
        StringBuilder sb = new StringBuilder();
        String name = callString(cardView, "getName");
        String manaCost = callString(cardView, "getManaCostStr");
        String types = callString(cardView, "getTypeText");
        String power = callString(cardView, "getPower");
        String toughness = callString(cardView, "getToughness");
        boolean isCreature = callBool(cardView, "isCreature");

        sb.append(name != null ? name : "Unknown").append(". ");
        if (manaCost != null && !manaCost.isEmpty()) {
            sb.append("Mana cost: ").append(formatManaCost(manaCost)).append(". ");
        }
        if (types != null && !types.isEmpty()) {
            sb.append(types).append(". ");
        }
        if (isCreature && power != null && toughness != null) {
            sb.append(power).append("/").append(toughness).append(". ");
        }

        Object rules = callMethod(cardView, "getRules");
        if (rules instanceof List) {
            List<?> rulesList = (List<?>) rules;
            if (!rulesList.isEmpty()) {
                sb.append("Rules: ");
                for (Object rule : rulesList) {
                    String clean = cleanHtml(rule.toString());
                    if (!clean.isEmpty()) {
                        sb.append(clean).append(". ");
                    }
                }
            }
        }
        return sb.toString();
    }
}
