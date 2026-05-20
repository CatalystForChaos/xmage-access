package xmageaccess.speech;

import java.io.File;
import java.io.IOException;

/**
 * Windows speech output. Tries Tolk first for direct NVDA/JAWS
 * communication, falls back to PowerShell SAPI if Tolk is not available.
 */
public class WindowsSpeech implements SpeechEngine {

    private static final File DEV_NULL = new File("NUL");

    private TolkSpeech tolkSpeech;
    private Process currentProcess;
    private boolean usingSapi;

    public WindowsSpeech() {
        // Try to load Tolk for direct screen reader communication
        TolkLibrary tolkLib = TolkLibrary.load();
        if (tolkLib != null) {
            tolkSpeech = new TolkSpeech(tolkLib);
            if (tolkSpeech.isLoaded()) {
                System.out.println("[XMage Access] Speech: Using Tolk for screen reader output.");
                return;
            }
        }

        // Tolk not available, fall back to SAPI
        usingSapi = true;
        System.out.println("[XMage Access] Speech: Tolk not found, falling back to Windows SAPI.");
    }

    @Override
    public synchronized void speak(String text, boolean interrupt) {
        if (tolkSpeech != null && tolkSpeech.isLoaded()) {
            tolkSpeech.speak(text, interrupt);
        } else {
            speakSapi(text, interrupt);
        }
    }

    @Override
    public synchronized void silence() {
        if (tolkSpeech != null && tolkSpeech.isLoaded()) {
            tolkSpeech.silence();
        }
        if (currentProcess != null && currentProcess.isAlive()) {
            currentProcess.destroyForcibly();
        }
        currentProcess = null;
    }

    private void speakSapi(String text, boolean interrupt) {
        if (interrupt) {
            if (currentProcess != null && currentProcess.isAlive()) {
                currentProcess.destroyForcibly();
            }
            currentProcess = null;
        }

        try {
            String escaped = text.replace("'", "''");
            String command = "Add-Type -AssemblyName System.Speech; "
                    + "$synth = New-Object System.Speech.Synthesis.SpeechSynthesizer; "
                    + "$synth.Speak('" + escaped + "')";

            ProcessBuilder pb = new ProcessBuilder("powershell", "-Command", command)
                    .redirectInput(ProcessBuilder.Redirect.from(DEV_NULL))
                    .redirectOutput(ProcessBuilder.Redirect.to(DEV_NULL))
                    .redirectError(ProcessBuilder.Redirect.to(DEV_NULL));
            currentProcess = pb.start();
        } catch (IOException e) {
            System.err.println("[XMage Access] SAPI speech error: " + e.getMessage());
        }
    }

    public void shutdown() {
        if (tolkSpeech != null) {
            tolkSpeech.shutdown();
        }
    }
}
