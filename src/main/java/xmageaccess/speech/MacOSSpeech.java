package xmageaccess.speech;

import java.io.File;
import java.io.IOException;

/**
 * macOS speech output using the built-in 'say' command.
 * Also works with VoiceOver when it is running.
 */
public class MacOSSpeech implements SpeechEngine {

    private static final File DEV_NULL = new File("/dev/null");

    private Process currentProcess;

    @Override
    public synchronized void speak(String text, boolean interrupt) {
        if (interrupt) {
            silence();
        }

        try {
            ProcessBuilder pb = new ProcessBuilder("say", text)
                    .redirectInput(ProcessBuilder.Redirect.from(DEV_NULL))
                    .redirectOutput(ProcessBuilder.Redirect.to(DEV_NULL))
                    .redirectError(ProcessBuilder.Redirect.to(DEV_NULL));
            currentProcess = pb.start();
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
}
