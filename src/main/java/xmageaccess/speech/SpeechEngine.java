package xmageaccess.speech;

/**
 * Interface for platform-specific speech output.
 */
public interface SpeechEngine {

    /**
     * Speak text.
     * @param text the text to speak
     * @param interrupt if true, stop current speech before speaking
     */
    void speak(String text, boolean interrupt);

    /**
     * Stop all current speech.
     */
    void silence();
}
