package mage.client.draft;

import mage.client.cards.CardsList;
import mage.view.SimpleCardsView;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Same field names XMage's draft panel exposes, plus a record of the redraw. */
public class DraftPanel extends JPanel {

    public boolean allowedToPick = true;
    public SimpleCardsView lastPickedAreaLoad;

    private UUID draftId = UUID.randomUUID();
    private final Set<UUID> cardsHidden = new HashSet<>();
    private final DraftGrid draftBooster = new DraftGrid();
    private final CardsList draftPicks = new CardsList();
    private final JLabel labelCardNumber = new JLabel("Card 3 of 15");
    private final JCheckBox checkPack1 = new JCheckBox("", true);
    private final JCheckBox checkPack2 = new JCheckBox("", false);
    private final JCheckBox checkPack3 = new JCheckBox("", false);
    private final JTextField editPack1 = new JTextField("Modern Horizons 3");
    private final JTextField editPack2 = new JTextField("");
    private final JTextField editPack3 = new JTextField("");
    private final JTextField editTimeRemaining = new JTextField("0:42");

    public UUID draftId() { return draftId; }
    public DraftGrid booster() { return draftBooster; }
    public CardsList picks() { return draftPicks; }

    public boolean isAllowedToPick() { return allowedToPick; }

    private void loadCardsToPickedCardsArea(SimpleCardsView picks) {
        lastPickedAreaLoad = picks;
    }
}
