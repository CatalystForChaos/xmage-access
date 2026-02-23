package xmageaccess.ui;

import xmageaccess.AccessibilityManager;
import xmageaccess.speech.SpeechOutput;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.lang.reflect.Method;

/**
 * Accessibility handler for the XMage Download Images dialog.
 * Provides keyboard shortcuts to navigate sources, sets, start/stop downloads,
 * and read progress.
 *
 * Shortcuts (active when dialog is visible):
 *   Ctrl+S     - Read current image source, cycle to next
 *   Ctrl+M     - Read current set selection, cycle to next
 *   Ctrl+Enter - Start download
 *   Ctrl+R     - Read download progress
 *   Escape     - Cancel / close dialog
 */
public class DownloadImagesDialogHandler {

    private final Component dialog;
    private JComboBox<?> sourcesCombo;
    private JComboBox<?> setsCombo;
    private JButton startButton;
    private JButton stopButton;
    private JButton cancelButton;
    private JProgressBar progressBar;

    private KeyEventDispatcher keyDispatcher;
    private Timer pollTimer;
    private int lastAnnouncedPercent = -1;

    public DownloadImagesDialogHandler(Component dialog) {
        this.dialog = dialog;
    }

    public void attach() {
        discoverComponents();
        addKeyboardShortcuts();
        startProgressPolling();
        announceDialog();
    }

    public void detach() {
        if (keyDispatcher != null) {
            KeyboardFocusManager.getCurrentKeyboardFocusManager()
                    .removeKeyEventDispatcher(keyDispatcher);
            keyDispatcher = null;
        }
        if (pollTimer != null) {
            pollTimer.stop();
            pollTimer = null;
        }
    }

    private void discoverComponents() {
        try {
            sourcesCombo = callGetter(dialog, "getSourcesCombo", JComboBox.class);
            setsCombo = callGetter(dialog, "getSetsCombo", JComboBox.class);
            startButton = callGetter(dialog, "getStartButton", JButton.class);
            stopButton = callGetter(dialog, "getStopButton", JButton.class);
            cancelButton = callGetter(dialog, "getCancelButton", JButton.class);
            progressBar = callGetter(dialog, "getProgressBar", JProgressBar.class);

            System.out.println("[XMage Access] Download dialog - sources: " + (sourcesCombo != null)
                    + ", sets: " + (setsCombo != null)
                    + ", start: " + (startButton != null)
                    + ", stop: " + (stopButton != null)
                    + ", cancel: " + (cancelButton != null)
                    + ", progress: " + (progressBar != null));
        } catch (Exception e) {
            System.err.println("[XMage Access] Error discovering download dialog: " + e.getMessage());
        }
    }

    private void announceDialog() {
        StringBuilder sb = new StringBuilder("Download images dialog. ");

        if (sourcesCombo != null) {
            Object selected = sourcesCombo.getSelectedItem();
            if (selected != null) {
                sb.append("Source: ").append(selected).append(". ");
            }
        }

        sb.append("Ctrl+S to cycle source, ");
        sb.append("Ctrl+M to cycle set, ");
        sb.append("Ctrl+Enter to start, ");
        sb.append("Ctrl+R for progress, ");
        sb.append("Escape to close.");

        speak(sb.toString());
    }

    private void addKeyboardShortcuts() {
        keyDispatcher = new KeyEventDispatcher() {
            @Override
            public boolean dispatchKeyEvent(KeyEvent e) {
                if (e.getID() != KeyEvent.KEY_PRESSED) return false;
                if (!isDialogVisible()) return false;

                // Escape without modifiers
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE && !e.isControlDown()) {
                    closeDialog();
                    return true;
                }

                if (!e.isControlDown()) return false;

                switch (e.getKeyCode()) {
                    case KeyEvent.VK_S:
                        cycleSource();
                        return true;
                    case KeyEvent.VK_M:
                        cycleSet();
                        return true;
                    case KeyEvent.VK_ENTER:
                        startDownload();
                        return true;
                    case KeyEvent.VK_R:
                        readProgress();
                        return true;
                }
                return false;
            }
        };

        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addKeyEventDispatcher(keyDispatcher);
    }

    private void startProgressPolling() {
        lastAnnouncedPercent = -1;
        pollTimer = new Timer(3000, e -> {
            if (!isDialogVisible()) {
                detach();
                return;
            }
            checkProgressChange();
        });
        pollTimer.setRepeats(true);
        pollTimer.start();
    }

    private void checkProgressChange() {
        if (progressBar == null) return;

        int percent = progressBar.getValue();
        int max = progressBar.getMaximum();
        if (max <= 0) return;

        int pct = (int) ((percent * 100L) / max);

        // Announce at every 25% milestone
        int currentMilestone = (pct / 25) * 25;
        int lastMilestone = (lastAnnouncedPercent / 25) * 25;

        if (pct > 0 && currentMilestone > lastMilestone) {
            speak("Download " + pct + " percent.");
            lastAnnouncedPercent = pct;
        }

        // Announce completion
        if (pct >= 100 && lastAnnouncedPercent < 100) {
            speak("Download complete.");
            lastAnnouncedPercent = 100;
        }
    }

    private boolean isDialogVisible() {
        if (dialog == null) return false;
        if (!dialog.isVisible()) return false;
        Container parent = dialog.getParent();
        while (parent != null) {
            if (!parent.isVisible()) return false;
            parent = parent.getParent();
        }
        return true;
    }

    private void cycleSource() {
        if (sourcesCombo == null || sourcesCombo.getItemCount() == 0) {
            speak("No image sources available.");
            return;
        }
        int current = sourcesCombo.getSelectedIndex();
        int next = (current + 1) % sourcesCombo.getItemCount();
        sourcesCombo.setSelectedIndex(next);
        Object selected = sourcesCombo.getSelectedItem();
        speak("Source: " + (selected != null ? selected : "unknown") + ". "
                + (next + 1) + " of " + sourcesCombo.getItemCount() + ".");
    }

    private void cycleSet() {
        if (setsCombo == null || setsCombo.getItemCount() == 0) {
            speak("No sets available.");
            return;
        }
        int current = setsCombo.getSelectedIndex();
        int next = (current + 1) % setsCombo.getItemCount();
        setsCombo.setSelectedIndex(next);
        Object selected = setsCombo.getSelectedItem();
        speak("Set: " + (selected != null ? selected : "unknown") + ". "
                + (next + 1) + " of " + setsCombo.getItemCount() + ".");
    }

    private void startDownload() {
        if (startButton != null && startButton.isEnabled()) {
            speak("Starting download.");
            lastAnnouncedPercent = -1;
            startButton.doClick();
        } else if (stopButton != null && stopButton.isEnabled()) {
            speak("Download already in progress. Ctrl+R to check progress.");
        } else {
            speak("Cannot start download.");
        }
    }

    private void readProgress() {
        if (progressBar == null) {
            speak("No progress information available.");
            return;
        }

        int value = progressBar.getValue();
        int max = progressBar.getMaximum();
        String text = progressBar.getString();

        if (max <= 0) {
            speak("Download not started.");
            return;
        }

        int pct = (int) ((value * 100L) / max);
        StringBuilder sb = new StringBuilder();
        sb.append("Progress: ").append(pct).append(" percent.");
        if (text != null && !text.isEmpty()) {
            sb.append(" ").append(text);
        }
        speak(sb.toString());
    }

    private void closeDialog() {
        // Try stop button first if download is running
        if (stopButton != null && stopButton.isEnabled()) {
            speak("Stopping download.");
            stopButton.doClick();
            return;
        }
        if (cancelButton != null) {
            speak("Closing download dialog.");
            cancelButton.doClick();
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T callGetter(Object target, String methodName, Class<T> type) {
        try {
            Method method = target.getClass().getMethod(methodName);
            Object result = method.invoke(target);
            if (type.isInstance(result)) return (T) result;
        } catch (Exception e) {
            // Ignore
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
