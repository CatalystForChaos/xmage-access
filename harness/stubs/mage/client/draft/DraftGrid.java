package mage.client.draft;

import javax.swing.JPanel;

/** The booster grid. Its children are card panels answering getOriginal(). */
public class DraftGrid extends JPanel {

    /** Stands in for a MageCard: the agent reads the CardView off it. */
    public static class Card extends JPanel {
        private final Object original;
        public Card(Object original) { this.original = original; }
        public Object getOriginal() { return original; }
    }

    public void addCard(Object cardView) {
        add(new Card(cardView));
    }
}
