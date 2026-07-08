package xmageaccess.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static xmageaccess.util.ReflectionUtils.*;
import static xmageaccess.util.TextUtils.*;

/**
 * Builds a grouped, categorized ZoneItem list from a list of CardViews:
 * duplicates collapsed to "Nx Name", grouped into creatures, spells, lands.
 * Shared by the deck editor and sideboarding windows.
 */
final class GroupedCardList {

    private GroupedCardList() {}

    static List<ZoneItem> build(List<?> allCards, ZoneItem.ActionType actionType) {
        // Copy to avoid ConcurrentModificationException during import/load
        List<?> cardsCopy = new ArrayList<Object>(allCards);

        // Group by name, preserving order
        Map<String, List<Object>> grouped = new LinkedHashMap<>();
        for (Object cardView : cardsCopy) {
            String name = callString(cardView, "getName");
            if (name == null) name = "Unknown";
            List<Object> group = grouped.get(name);
            if (group == null) {
                group = new ArrayList<Object>();
                grouped.put(name, group);
            }
            group.add(cardView);
        }

        List<ZoneItem> creatures = new ArrayList<>();
        List<ZoneItem> spells = new ArrayList<>();
        List<ZoneItem> lands = new ArrayList<>();
        int creatureCount = 0, spellCount = 0, landCount = 0;

        for (Map.Entry<String, List<Object>> entry : grouped.entrySet()) {
            List<Object> cards = entry.getValue();
            Object firstCard = cards.get(0);
            int count = cards.size();

            String name = entry.getKey();
            String manaCost = callString(firstCard, "getManaCostStr");

            StringBuilder display = new StringBuilder();
            if (count > 1) display.append(count).append("x ");
            display.append(name);
            if (manaCost != null && !manaCost.isEmpty()) {
                display.append(", ").append(formatManaCost(manaCost));
            }

            // Store the first CardView for removal; detail is loaded lazily on D press
            ZoneItem item = new ZoneItem(display.toString(), null, firstCard, actionType);

            if (callBool(firstCard, "isCreature")) {
                creatures.add(item);
                creatureCount += count;
            } else if (callBool(firstCard, "isLand")) {
                lands.add(item);
                landCount += count;
            } else {
                spells.add(item);
                spellCount += count;
            }
        }

        List<ZoneItem> result = new ArrayList<>();
        if (!creatures.isEmpty()) {
            result.add(new ZoneItem("--- Creatures (" + creatureCount + ") ---",
                    creatureCount + " creatures", null, ZoneItem.ActionType.NONE));
            result.addAll(creatures);
        }
        if (!spells.isEmpty()) {
            result.add(new ZoneItem("--- Spells (" + spellCount + ") ---",
                    spellCount + " spells", null, ZoneItem.ActionType.NONE));
            result.addAll(spells);
        }
        if (!lands.isEmpty()) {
            result.add(new ZoneItem("--- Lands (" + landCount + ") ---",
                    landCount + " lands", null, ZoneItem.ActionType.NONE));
            result.addAll(lands);
        }
        return result;
    }
}
