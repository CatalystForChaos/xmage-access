package mage.view;

import java.util.UUID;

/** The card as the client sees it. Only what the agent reads is here. */
public class CardView {
    private final UUID id = UUID.randomUUID();
    private final String name;
    private final String manaCost;
    public CardView(String name, String manaCost) { this.name = name; this.manaCost = manaCost; }
    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getManaCostStr() { return manaCost; }
}
