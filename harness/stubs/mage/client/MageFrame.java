package mage.client;

import javax.swing.JDesktopPane;

/**
 * Stands in for XMage's main frame, for the two things the agent reads off it
 * by name: the private static activeFrame, which the real setActive writes,
 * and the desktop the panes sit on, from the public static getDesktop. No
 * getInstance, so code that looks for the frame instance finds none, as it
 * did before this stub existed.
 */
public class MageFrame {

    private static MagePane activeFrame;
    private static final JDesktopPane desktopPane = new JDesktopPane();

    public static JDesktopPane getDesktop() {
        return desktopPane;
    }

    /**
     * Like the real one, moves the pane in front when it sits on the desktop.
     * The real one also resizes and activates it.
     */
    public static void setActive(MagePane frame) {
        activeFrame = frame;
        if (frame != null && frame.getParent() == desktopPane) {
            desktopPane.moveToFront(frame);
        }
    }
}
