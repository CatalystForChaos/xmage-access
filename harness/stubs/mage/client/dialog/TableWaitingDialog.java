package mage.client.dialog;

import javax.swing.JButton;
import javax.swing.JInternalFrame;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

/**
 * Stands in for XMage's table waiting dialog: the same private field names,
 * a seats table with TableWaitModel's six columns, and the title XMage sets
 * once the first table update has arrived. On a JInternalFrame because the
 * real one extends MageDialog, which extends JInternalFrame.
 *
 * Start and Cancel close it the way removeDialog does, hidden and taken off
 * its parent; Cancel only while the server would still let the player leave.
 */
public class TableWaitingDialog extends JInternalFrame {

    public int startClicks;
    public int cancelClicks;
    /** False once the table has started: XMage's leaveTable then refuses. */
    public boolean leaveAllowed = true;

    private boolean isTournament;
    private final JButton btnStart = new JButton("Start");
    private final JButton btnCancel = new JButton("Cancel");
    private final DefaultTableModel seats = new DefaultTableModel(
            new Object[]{"Seat", "Loc", "Player Name", "Rating", "Player Type", "History"}, 0);
    private final JTable jTableSeats = new JTable(seats);

    public TableWaitingDialog(boolean owner, boolean tournament) {
        super("Waiting for players");
        this.isTournament = tournament;
        btnStart.setVisible(owner);
        btnStart.setEnabled(false);
        btnStart.addActionListener(e -> {
            startClicks++;
            close();
        });
        btnCancel.addActionListener(e -> {
            cancelClicks++;
            if (leaveAllowed) close();
        });
        setVisible(true);
    }

    /** A taken seat, as TableWaitModel.getValueAt reports one. */
    public void takenSeat(String name, String playerType, int rating, String history) {
        seats.addRow(new Object[]{Integer.toString(seats.getRowCount() + 1), "de", name, rating, playerType, history});
    }

    /** An open seat: its number, and empty strings after it. */
    public void openSeat() {
        seats.addRow(new Object[]{Integer.toString(seats.getRowCount() + 1), "", "", "", "", ""});
    }

    /** Seat {@code index}, from 0, taken by {@code name}; an empty name opens it again. */
    public void setSeat(int index, String name, String playerType) {
        boolean taken = !name.isEmpty();
        seats.setValueAt(taken ? "de" : "", index, 1);
        seats.setValueAt(name, index, 2);
        seats.setValueAt(taken ? (Object) 0 : "", index, 3);
        seats.setValueAt(taken ? playerType : "", index, 4);
        seats.setValueAt("", index, 5);
    }

    /** What update() does to Start for READY_TO_START and for WAITING. */
    public void ready(boolean ready) {
        btnStart.setEnabled(ready);
    }

    /** The title update() sets on the first table update. */
    public void tableRead(String deckType, String gameType) {
        setTitle("Waiting for players - " + deckType + " / " + gameType);
    }

    private void close() {
        setVisible(false);
        if (getParent() != null) getParent().remove(this);
    }
}
