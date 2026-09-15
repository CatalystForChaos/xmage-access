package mage.client;

/**
 * Stands in for XMage's main frame, for the one thing the agent reads off it
 * by name: the private static activeFrame, which the real setActive writes.
 * No getInstance, so code that looks for the frame instance finds none, as
 * it did before this stub existed.
 */
public class MageFrame {

    private static MagePane activeFrame;

    /** The real one also moves, resizes and activates the pane. */
    public static void setActive(MagePane frame) {
        activeFrame = frame;
    }
}
