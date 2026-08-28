package mage.client.table;

import javax.swing.table.AbstractTableModel;

/**
 * Same column layout as XMage's: five shown, three more carrying the ids.
 * Column 4 reads "Watch" only while a match is duelling and watching is
 * allowed, which is exactly when the agent offers it.
 */
public class TournamentMatchesTableModel extends AbstractTableModel {

    public static final int ACTION_COLUMN = 4;

    private final String[] columnNames = {"Round Number", "Players", "State", "Result", "Action"};
    private final java.util.List<String[]> rows = new java.util.ArrayList<>();

    /** round, players, state, result, tableId, matchId, gameId */
    public void addRow(String round, String players, String state, String result,
                       String tableId, String matchId, String gameId) {
        rows.add(new String[]{round, players, state, result, tableId, matchId, gameId});
        fireTableDataChanged();
    }

    @Override
    public int getRowCount() { return rows.size(); }

    @Override
    public int getColumnCount() { return columnNames.length; }

    @Override
    public String getColumnName(int column) { return columnNames[column]; }

    @Override
    public Object getValueAt(int row, int column) {
        String[] data = rows.get(row);
        switch (column) {
            case 0: return data[0];
            case 1: return data[1];
            case 2: return data[2];
            case 3: return data[3];
            case 4: return data[2].startsWith("Dueling") ? "Watch" : "";
            case 5: return data[4];
            case 6: return data[5];
            case 7: return data[6];
        }
        return "";
    }
}
