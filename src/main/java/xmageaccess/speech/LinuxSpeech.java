package xmageaccess.speech;

import java.io.IOException;

/**
 * Linux speech output using speech-dispatcher (spd-say command).
 */
public class LinuxSpeech implements SpeechEngine {

    private Process currentProcess;

    @Override
    public synchronized void speak(String text, boolean interrupt) {
        if (interrupt) {
            silence();
        }

        try {
            ProcessBuilder pb = new ProcessBuilder("spd-say", text);
            currentProcess = pb.start();
            closeStreams(currentProcess);
        } catch (IOException e) {
            System.err.println("[XMage Access] Speech error: " + e.getMessage());
        }
    }

    @Override
    public synchronized void silence() {
        if (currentProcess != null && currentProcess.isAlive()) {
            currentProcess.destroyForcibly();
        }
        currentProcess = null;
        // Also tell speech-dispatcher to stop
        try {
            Process cancel = new ProcessBuilder("spd-say", "--cancel").start();
            closeStreams(cancel);
            cancel.waitFor(500, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            // Ignore - spd-say might not be available
        }
    }

    private static void closeStreams(Process p) {
        try { p.getInputStream().close(); } catch (Exception ignored) {}
        try { p.getOutputStream().close(); } catch (Exception ignored) {}
        try { p.getErrorStream().close(); } catch (Exception ignored) {}
    }
}
