package xmageaccess.speech;

/**
 * Platform-detecting speech output. Routes text to the appropriate
 * screen reader or TTS engine based on the operating system.
 */
public class SpeechOutput {

    private SpeechEngine engine;

    public void initialize() {
        String os = System.getProperty("os.name", "").toLowerCase();

        if (os.contains("mac")) {
            engine = new MacOSSpeech();
            System.out.println("[XMage Access] Speech: macOS detected, using 'say' command.");
        } else if (os.contains("win")) {
            // WindowsSpeech tries Tolk first (NVDA/JAWS), falls back to SAPI
            engine = new WindowsSpeech();
        } else {
            engine = new LinuxSpeech();
            System.out.println("[XMage Access] Speech: Linux detected, using speech-dispatcher.");
        }
    }

    /**
     * Speak text. Interrupts any current speech.
     */
    public void speak(String text) {
        if (engine != null && text != null && !text.isEmpty()) {
            engine.speak(text, true);
        }
    }

    /**
     * Speak text without interrupting current speech (queued).
     */
    public void speakQueued(String text) {
        if (engine != null && text != null && !text.isEmpty()) {
            engine.speak(text, false);
        }
    }

    /**
     * Stop all current speech.
     */
    public void silence() {
        if (engine != null) {
            engine.silence();
        }
    }
}
