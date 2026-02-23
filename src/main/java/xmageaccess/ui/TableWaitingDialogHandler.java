package xmageaccess.ui;

import xmageaccess.AccessibilityManager;
import xmageaccess.speech.SpeechOutput;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.reflect.Field;

/**
 * Accessibility handler for the XMage Table Waiting Dialog.
 * This is the screen shown after creating a table, waiting for
 * players to join before the game starts.
 *
 * Keyboard shortcuts:
 *   Ctrl+Enter - Start the game
 *   Ctrl+Shift+I - Read who is seated
 */
public class TableWaitingDialogHandler {

    private final Component dialog;
    private JButton btnStart;
    private JButton btnCancel;
    private JTable jTableSeats;

    public TableWaitingDialogHandler(Component dialog) {
        this.dialog = dialog;
    }

    public void attach() {
        try {
            discoverComponents();
            addKeyboardShortcuts();
            monitorStartButton();

            // Announce the waiting screen
            StringBuilder sb = new StringBuilder("Waiting for game to start. ");

            // Read who is seated
            if (jTableSeats != null) {
                int rows = jTableSeats.getRowCount();
                for (int i = 0; i < rows; i++) {
                    String seatInfo = readSeatRow(i);
                    if (seatInfo != null) {
                        sb.append(seatInfo).append(" ");
                    }
                }
            }

            if (btnStart != null && btnStart.isEnabled()) {
                sb.append("Press Ctrl+Enter to start.");
            } else if (btnStart != null && btnStart.isVisible()) {
                sb.append("Waiting for all players. Start button not yet available.");
            }

            speak(sb.toString());

        } catch (Exception e) {
            System.err.println("[XMage Access] Error attaching to table waiting: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void detach() {
        // Cleanup if needed
    }

    private void discoverComponents() throws Exception {
        Class<?> clazz = dialog.getClass();
        btnStart = getField(clazz, "btnStart", JButton.class);
        btnCancel = getField(clazz, "btnCancel", JButton.class);
        jTableSeats = getField(clazz, "jTableSeats", JTable.class);
    }

    private void addKeyboardShortcuts() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addKeyEventDispatcher(e -> {
                    if (e.getID() != KeyEvent.KEY_PRESSED) return false;
                    if (!dialog.isVisible()) return false;

                    // Ctrl+Enter = Start game
                    if (e.isControlDown() && !e.isShiftDown()
                            && e.getKeyCode() == KeyEvent.VK_ENTER) {
                        if (btnStart != null && btnStart.isEnabled() && btnStart.isVisible()) {
                            speak("Starting game.");
                            btnStart.doClick();
                        } else {
                            speak("Start button not available yet.");
                        }
                        return true;
                    }

                    // Ctrl+Shift+I = Read seat info
                    if (e.isControlDown() && e.isShiftDown()
                            && e.getKeyCode() == KeyEvent.VK_I) {
                        readSeats();
                        return true;
                    }

                    return false;
                });
    }

    /**
     * Monitor the Start button becoming enabled (all players seated).
     */
    private void monitorStartButton() {
        if (btnStart == null) return;

        // Poll for the button becoming enabled
        Timer timer = new Timer(1000, e -> {
            if (!dialog.isVisible()) {
                ((Timer) e.getSource()).stop();
                return;
            }
            if (btnStart.isEnabled() && btnStart.isVisible()) {
                ((Timer) e.getSource()).stop();
                speak("All players ready. Press Ctrl+Enter to start the game.");
            }
        });
        timer.setRepeats(true);
        timer.start();
    }

    private void readSeats() {
        if (jTableSeats == null) {
            speak("Seats table not found.");
            return;
        }

        int rows = jTableSeats.getRowCount();
        if (rows == 0) {
            speak("No players seated.");
            return;
        }

        StringBuilder sb = new StringBuilder(rows + " seats. ");
        for (int i = 0; i < rows; i++) {
            String seatInfo = readSeatRow(i);
            if (seatInfo != null) {
                sb.append(seatInfo).append(" ");
            }
        }
        speak(sb.toString());
    }

    private String readSeatRow(int row) {
        if (jTableSeats == null) return null;
        try {
            StringBuilder sb = new StringBuilder();
            int cols = jTableSeats.getColumnCount();
            for (int col = 0; col < cols; col++) {
                Object val = jTableSeats.getValueAt(row, col);
                if (val != null) {
                    String text = val.toString().trim();
                    if (!text.isEmpty()) {
                        if (sb.length() > 0) sb.append(", ");
                        sb.append(text);
                    }
                }
            }
            return sb.length() > 0 ? "Seat " + (row + 1) + ": " + sb.toString() + "." : null;
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T getField(Class<?> clazz, String name, Class<T> type) {
        try {
            Field field = clazz.getDeclaredField(name);
            field.setAccessible(true);
            Object val = field.get(dialog);
            if (type.isInstance(val)) {
                return (T) val;
            }
        } catch (Exception e) {
            // Field doesn't exist
        }
        return null;
    }

    private void speak(String text) {
        SpeechOutput speech = AccessibilityManager.getInstance().getSpeech();
        if (speech != null) {
            speech.speak(text);
        }
    }
}
