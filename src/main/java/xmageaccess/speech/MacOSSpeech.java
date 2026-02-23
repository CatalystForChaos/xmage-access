package xmageaccess.speech;

import java.io.IOException;

/**
 * macOS speech output using the built-in 'say' command.
 * Also works with VoiceOver when it is running.
 */
public class MacOSSpeech implements SpeechEngine {

    private Process currentProcess;

    @Override
    public void speak(String text, boolean interrupt) {
        if (interrupt) {
            silence();
        }

        try {
            // The 'say' command is available on all macOS systems
            ProcessBuilder pb = new ProcessBuilder("say", text);
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
    }
}
