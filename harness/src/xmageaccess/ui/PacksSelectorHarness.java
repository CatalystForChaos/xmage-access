package xmageaccess.ui;

import mage.client.dialog.RandomPacksSelectorDialog;

import java.util.List;

/**
 * Drives RandomPacksSelectorDialogHandler against a stand-in pack selector.
 *
 * What it pins down: that the checkboxes are reachable at all — they live in a
 * heavyweight {@code java.awt.Panel} the handler looks up as a Container —
 * that the spoken cursor moves and clamps at both ends, that toggling writes
 * through to the state {@code getSelectedPacks} reads while notifying no
 * ActionListener (the deck editor's lesson: an ActionEvent raised inside a key
 * event carries the Ctrl modifier), that select-all and select-none go through
 * XMage's own buttons, and that an empty pool is refused here rather than in a
 * JOptionPane nobody would hear.
 */
public class PacksSelectorHarness {

    private static int failures = 0;

    private static void check(String what, boolean ok) {
        System.out.println((ok ? "  PASS  " : "  FAIL  ") + what);
        if (!ok) failures++;
    }

    public static void main(String[] args) {
        RandomPacksSelectorDialog dialog = new RandomPacksSelectorDialog();
        dialog.addPack("MH3", "Modern Horizons 3");
        dialog.addPack("LCI", "The Lost Caverns of Ixalan");
        dialog.addPack("10E", "Tenth Edition");

        RandomPacksSelectorDialogHandler handler = new RandomPacksSelectorDialogHandler(dialog);
        handler.attach();

        System.out.println("Finding the checkboxes");
        check("the AWT panel of checkboxes is reached through a Container field",
                handler.packs().size() == 3);
        check("XMage's own default is every set in",
                dialog.getSelectedPacks().size() == 3);

        System.out.println("Emptying the pool");
        handler.selectAll(false);
        check("Ctrl+N clears every set through XMage's button",
                dialog.getSelectedPacks().isEmpty());

        handler.apply();
        check("an empty pool is refused before the button is ever clicked",
                dialog.applied == 0 && dialog.applyRejected == 0);

        System.out.println("Building a pool with the cursor");
        handler.move(1);
        handler.toggleCurrent();
        check("Ctrl+Space adds the set under the cursor",
                dialog.getSelectedPacks().equals(java.util.Collections.singletonList("MH3")));

        handler.move(1);
        handler.toggleCurrent();
        List<String> two = dialog.getSelectedPacks();
        check("the cursor moved on and the next set went in too",
                two.size() == 2 && two.contains("LCI"));

        handler.toggleCurrent();
        check("toggling the same set again takes it back out",
                dialog.getSelectedPacks().equals(java.util.Collections.singletonList("MH3")));

        System.out.println("No modifier can leak out of a toggle");
        final int[] listenerHits = {0};
        handler.packs().get(0).addActionListener(e -> listenerHits[0]++);
        handler.move(-1);
        handler.move(-1);
        handler.toggleCurrent();
        check("the cursor clamped at the first set instead of running off",
                dialog.getSelectedPacks().isEmpty());
        check("toggling notifies no ActionListener at all", listenerHits[0] == 0);

        System.out.println("Filling it again and applying");
        handler.selectAll(true);
        check("Ctrl+A selects every set through XMage's button",
                dialog.getSelectedPacks().size() == 3);

        handler.move(1);
        handler.move(1);
        handler.move(1);
        handler.toggleCurrent();
        check("the cursor clamped at the last set",
                !dialog.getSelectedPacks().contains("10E")
                        && dialog.getSelectedPacks().size() == 2);

        handler.apply();
        check("a pool with sets in it reaches XMage's apply",
                dialog.applied == 1 && dialog.applyRejected == 0);
        handler.detach();

        System.out.println("A selector with no sets at all");
        RandomPacksSelectorDialog empty = new RandomPacksSelectorDialog();
        RandomPacksSelectorDialogHandler emptyHandler =
                new RandomPacksSelectorDialogHandler(empty);
        emptyHandler.attach();
        check("no checkboxes means no packs", emptyHandler.packs().isEmpty());
        emptyHandler.move(1);
        emptyHandler.toggleCurrent();
        emptyHandler.apply();
        check("nothing is applied and nothing throws",
                empty.applied == 0 && empty.applyRejected == 0);
        emptyHandler.detach();

        System.out.println();
        System.out.println(failures == 0 ? "ALL CHECKS PASSED" : failures + " CHECK(S) FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }
}
