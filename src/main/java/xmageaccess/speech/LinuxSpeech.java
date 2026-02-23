package xmageaccess.speech;

import java.io.IOException;

/**
 * Linux speech output using speech-dispatcher (spd-say command).
 */
public class LinuxSpeech implements SpeechEngine {

    private Process currentProcess;

    @Override
    public void speak(String text, boolean interrupt) {
        if (interrupt) {
            silence();
        }

        try {
            ProcessBuilder pb = new ProcessBuilder("spd-say", text);
            pb.redirectErrorStream(true);
            currentProcess = pb.start();
        } catch (IOException e) {
            System.err.println("[XMage Access] Speech error: " + e.getMessage());
        }
    }

    @Override
    public void silence() {
        if (currentProcess != null && currentProcess.isAlive()) {
            currentProcess.destroyForcibly();
            currentProcess = null;
        }
        // Also tell speech-dispatcher to stop
        try {
            new ProcessBuilder("spd-say", "--cancel").start();
        } catch (IOException e) {
            // Ignore - spd-say might not be available
        }
    }
}
