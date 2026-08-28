package mage.client.dialog;

import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;

/**
 * Stands in for XMage's pack selector: the same field names and types — note
 * pnlPacks really is a heavyweight java.awt.Panel — one JCheckBox per set with
 * the set code as its label and the set name only in the tooltip, and the same
 * three button actions. doApply refuses an empty pool the way the real one
 * does, minus the JOptionPane.
 */
public class RandomPacksSelectorDialog extends JDialog {

    public int applied;
    public int applyRejected;

    private JButton btnAll = new JButton("Select all");
    private JButton btnApply = new JButton("Apply");
    private JButton btnNone = new JButton("Select none");
    private java.awt.Panel pnlPacks = new java.awt.Panel();

    public RandomPacksSelectorDialog() {
        setTitle("Random Booster Draft Packs Selector");
        btnAll.addActionListener(e -> setAllCheckBoxes(true));
        btnNone.addActionListener(e -> setAllCheckBoxes(false));
        btnApply.addActionListener(e -> doApply());
    }

    /** Mirrors createCheckboxes: label is the code, tooltip the name, all on. */
    public void addPack(String code, String name) {
        JCheckBox pack = new JCheckBox();
        pack.setSelected(true);
        pack.setText(code);
        pack.setToolTipText(name);
        pnlPacks.add(pack);
    }

    public List<String> getSelectedPacks() {
        List<String> selected = new ArrayList<>();
        for (Component pack : pnlPacks.getComponents()) {
            JCheckBox thePack = (JCheckBox) pack;
            if (thePack.isSelected()) selected.add(thePack.getText());
        }
        return selected;
    }

    public void doApply() {
        if (getSelectedPacks().size() < 1) {
            applyRejected++;
        } else {
            applied++;
        }
    }

    private void setAllCheckBoxes(boolean value) {
        for (Component pack : pnlPacks.getComponents()) {
            ((JCheckBox) pack).setSelected(value);
        }
    }
}
