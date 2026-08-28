package mage.client.tournament;

import mage.client.table.TournamentMatchesTableModel;

import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;

/** Same field names XMage's tournament panel exposes. */
public class TournamentPanel extends JPanel {

    private final JTable tablePlayers = new JTable(new DefaultTableModel(
            new Object[][]{{"1", "Alice", "2-0"}, {"2", "Bob", "1-1"}},
            new Object[]{"Place", "Player", "Score"}));
    private final TournamentMatchesTableModel matchesModel = new TournamentMatchesTableModel();
    private final Object chatPanel1 = null;   // no chat panel in the harness
    private final JTextField txtName = new JTextField("Friday Draft");
    private final JTextField txtType = new JTextField("Booster Draft");
    private final JTextField txtTournamentState = new JTextField("Dueling");

    public TournamentMatchesTableModel matches() { return matchesModel; }
    public JTable players() { return tablePlayers; }
}
