package xmageaccess.speech;

import java.io.IOException;

/**
 * macOS speech output using the built-in 'say' command.
 * Also works with VoiceOver when it is running.
 */
public class MacOSSpeech implements SpeechEngine {

    private Process currentProcess;

    @Override
    public synchronized void speak(String text, boolean interrupt) {
        if (interrupt) {
            silence();
        } else if (currentProcess != null && currentProcess.isAlive()) {
            try {
                currentProcess.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception e) {
                currentProcess.destroyForcibly();
            }
        }

        try {
            // The 'say' command is available on all macOS systems
            ProcessBuilder pb = new ProcessBuilder("say", text);
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
    }

    private static void closeStreams(Process p) {
        try { p.getInputStream().close(); } catch (Exception ignored) {}
        try { p.getOutputStream().close(); } catch (Exception ignored) {}
        try { p.getErrorStream().close(); } catch (Exception ignored) {}
    }
}
