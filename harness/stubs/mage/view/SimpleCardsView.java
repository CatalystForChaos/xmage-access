package mage.view;

import java.util.LinkedHashMap;
import java.util.UUID;

/**
 * What the server sends back with a pick. Deliberately holds no names —
 * the real SimpleCardView has no getName, which is why the picks list is
 * read from the client's own CardsList instead.
 */
public class SimpleCardsView extends LinkedHashMap<UUID, Object> {
}
