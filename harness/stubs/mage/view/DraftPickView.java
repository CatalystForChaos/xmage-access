package mage.view;

/** The answer to a pick. */
public class DraftPickView {
    private final SimpleCardsView picks;
    public DraftPickView(SimpleCardsView picks) { this.picks = picks; }
    public SimpleCardsView getPicks() { return picks; }
}
