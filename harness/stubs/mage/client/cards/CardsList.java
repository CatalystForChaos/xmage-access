package mage.client.cards;

import mage.view.CardsView;
import javax.swing.JPanel;

/** The picked-cards list. Its `cards` field is what the agent reads. */
public class CardsList extends JPanel {
    private CardsView cards = new CardsView();
    public CardsView cards() { return cards; }
}
