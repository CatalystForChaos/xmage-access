package xmageaccess.speech;

import java.io.File;
import java.io.IOException;

/**
 * Linux speech output using speech-dispatcher (spd-say command).
 */
public class LinuxSpeech implements SpeechEngine {

    private static final File DEV_NULL = new File("/dev/null");

    private Process currentProcess;

    @Override
    public synchronized void speak(String text, boolean interrupt) {
        if (interrupt) {
            silence();
        }

        try {
            ProcessBuilder pb = new ProcessBuilder("spd-say", text)
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
        try {
            Process cancel = new ProcessBuilder("spd-say", "--cancel")
                    .redirectInput(ProcessBuilder.Redirect.from(DEV_NULL))
                    .redirectOutput(ProcessBuilder.Redirect.to(DEV_NULL))
                    .redirectError(ProcessBuilder.Redirect.to(DEV_NULL))
                    .start();
            if (!cancel.waitFor(500, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                cancel.destroyForcibly();
            }
        } catch (Exception e) {
            // spd-say --cancel may not be available; nothing to recover.
        }
    }
}
