package xmageaccess.ui;

import mage.client.MageFrame;
import mage.client.MagePane;

import javax.swing.JDesktopPane;
import javax.swing.JFrame;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Window;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Drives the keyboard hand-over in UIWatcher: which accessible window gets
 * the keyboard once one of the agent's windows has closed.
 *
 * What it pins down: that XMage's pane in front is read from MageFrame's
 * private static activeFrame, the field XMage's own setActive writes, and
 * that the window chosen is the one whose XMage panel sits inside that pane.
 * So a finished game hands the keyboard to the lobby while XMage shows the
 * lobby, and to the tournament while it shows that. A window that is no
 * longer showing is not brought back, and without a pane nothing is chosen.
 *
 * And what happens when the pane in front has no window showing. After a
 * game XMage shows the topmost pane that is left, which on 15 September was a
 * deck editor whose window had been closed long before; the log said
 * "handing the keyboard to nobody". The choice now moves on to the panes
 * behind it, front to back as they sit on MageFrame.getDesktop(), which is
 * the order MageFrame.getTopMost ranks them by.
 *
 * The timing half, waiting for the window manager to settle the close and
 * then asking whether an agent window already has the keyboard, needs a
 * real desktop and is left to the test round; isAgentWindowActive itself is
 * pinned in FocusHarness. The frames here are off-screen and non-focusable,
 * as there, so the run takes nothing from the terminal.
 */
public class HandoverHarness {

    private static int failures = 0;

    private static void check(String what, boolean ok) {
        System.out.println((ok ? "  PASS  " : "  FAIL  ") + what);
        if (!ok) failures++;
    }

    private static JFrame offScreenFrame(boolean shown) {
        JFrame frame = new JFrame();
        frame.setFocusableWindowState(false);
        frame.setSize(10, 10);
        frame.setLocation(-4000, -4000);
        if (shown) frame.setVisible(true);
        return frame;
    }

    private static MagePane paneHolding(Component panel) {
        MagePane pane = new MagePane();
        JPanel content = new JPanel();
        content.add(panel);
        pane.getContentPane().add(content);
        return pane;
    }

    public static void main(String[] args) throws Exception {
        final JPanel tablesPanel = new JPanel();
        final JPanel tournamentPanel = new JPanel();
        final JPanel gamePanel = new JPanel();
        final MagePane tablesPane = paneHolding(tablesPanel);
        final MagePane tournamentPane = paneHolding(tournamentPanel);
        final MagePane gamePane = paneHolding(gamePanel);

        final Map<Component, Window> windows = new LinkedHashMap<>();
        final JFrame[] frames = new JFrame[3];
        SwingUtilities.invokeAndWait(() -> {
            frames[0] = offScreenFrame(true);    // the lobby's window
            frames[1] = offScreenFrame(true);    // the tournament's window
            frames[2] = offScreenFrame(false);   // a game window no longer showing
            windows.put(tablesPanel, frames[0]);
            windows.put(tournamentPanel, frames[1]);
            windows.put(gamePanel, frames[2]);
        });

        System.out.println("Reading XMage's pane in front");
        MageFrame.setActive(null);
        check("no pane in front reads as none", UIWatcher.activePane() == null);
        MageFrame.setActive(tablesPane);
        check("the pane is read from MageFrame.activeFrame",
                UIWatcher.activePane() == tablesPane);

        System.out.println("Choosing the window");
        check("the lobby's window while XMage shows the lobby",
                UIWatcher.windowForPane(UIWatcher.activePane(), windows) == frames[0]);
        MageFrame.setActive(tournamentPane);
        check("the tournament's window while XMage shows the tournament",
                UIWatcher.windowForPane(UIWatcher.activePane(), windows) == frames[1]);
        MageFrame.setActive(gamePane);
        check("a window that is no longer showing is not brought back",
                UIWatcher.windowForPane(UIWatcher.activePane(), windows) == null);
        check("nothing is chosen without a pane",
                UIWatcher.windowForPane(null, windows) == null);
        check("nor for a pane no accessible window belongs to",
                UIWatcher.windowForPane(new MagePane(), windows) == null);

        System.out.println("Looking past a pane whose window is closed");
        final JPanel deckEditorPanel = new JPanel();
        final MagePane deckEditorPane = paneHolding(deckEditorPanel);
        final JFrame[] closedByUser = new JFrame[1];
        SwingUtilities.invokeAndWait(() -> {
            closedByUser[0] = offScreenFrame(false);   // the deck editor's window, closed long ago
            windows.put(deckEditorPanel, closedByUser[0]);
        });
        // The evening of 15 September: the lobby, then the deck editor, then
        // a game, each put in front as XMage opens it.
        JDesktopPane desktop = MageFrame.getDesktop();
        for (MagePane pane : new MagePane[]{tablesPane, deckEditorPane, gamePane}) {
            desktop.add(pane, JLayeredPane.DEFAULT_LAYER);
            pane.setVisible(true);
            MageFrame.setActive(pane);
        }
        // The game ends: MagePane.removeFrame hides the game's pane, has
        // MageFrame.deactivate put the topmost pane left in front, and takes
        // the game's pane off the desktop.
        gamePane.setVisible(false);
        MageFrame.setActive(deckEditorPane);
        desktop.remove(gamePane);

        check("XMage's panes are read front to back",
                UIWatcher.panesFrontToBack().equals(Arrays.<Component>asList(deckEditorPane, tablesPane)));
        check("the pane in front leads nowhere by itself, as in the log",
                UIWatcher.windowForPane(UIWatcher.activePane(), windows) == null);
        check("so the keyboard goes to the lobby behind it",
                UIWatcher.windowForPanes(UIWatcher.panesFrontToBack(), windows) == frames[0]);

        desktop.add(tournamentPane, JLayeredPane.DEFAULT_LAYER);
        tournamentPane.setVisible(true);
        MageFrame.setActive(tournamentPane);
        MageFrame.setActive(deckEditorPane);
        check("a pane nearer the front comes before the lobby",
                UIWatcher.windowForPanes(UIWatcher.panesFrontToBack(), windows) == frames[1]);
        tournamentPane.setVisible(false);
        check("a hidden pane is passed over",
                UIWatcher.windowForPanes(UIWatcher.panesFrontToBack(), windows) == frames[0]);
        MageFrame.setActive(null);
        check("nothing is chosen while XMage shows nothing",
                UIWatcher.panesFrontToBack().isEmpty());

        SwingUtilities.invokeAndWait(() -> {
            for (JFrame frame : frames) frame.dispose();
            closedByUser[0].dispose();
        });

        System.out.println();
        System.out.println(failures == 0 ? "ALL CHECKS PASSED" : failures + " CHECK(S) FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }
}
