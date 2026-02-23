package xmageaccess.speech;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.WString;

/**
 * JNA binding for Tolk.dll - a screen reader abstraction library.
 * Tolk auto-detects the running screen reader (NVDA, JAWS, etc.)
 * and provides a unified speech/braille output interface.
 *
 * Requires Tolk.dll (and screen reader client DLLs) in the game
 * directory or on the system PATH.
 *
 * Download: https://github.com/dkager/tolk/releases
 */
public interface TolkLibrary extends Library {

    /**
     * Load and initialize Tolk. Must be called before any other function.
     */
    void Tolk_Load();

    /**
     * Unload Tolk and release resources.
     */
    void Tolk_Unload();

    /**
     * Enable or disable SAPI fallback when no screen reader is detected.
     */
    void Tolk_TrySAPI(boolean trySAPI);

    /**
     * Prefer SAPI over the detected screen reader.
     */
    void Tolk_PreferSAPI(boolean preferSAPI);

    /**
     * Output text to both speech and braille.
     * @param text the text to output
     * @param interrupt if true, stop current speech first
     * @return true if successful
     */
    boolean Tolk_Output(WString text, boolean interrupt);

    /**
     * Output text to speech only.
     * @param text the text to speak
     * @param interrupt if true, stop current speech first
     * @return true if successful
     */
    boolean Tolk_Speak(WString text, boolean interrupt);

    /**
     * Output text to braille only.
     * @param text the text to display on braille
     * @return true if successful
     */
    boolean Tolk_Braille(WString text);

    /**
     * Check if the current screen reader supports speech.
     */
    boolean Tolk_HasSpeech();

    /**
     * Check if the current screen reader supports braille.
     */
    boolean Tolk_HasBraille();

    /**
     * Check if the screen reader is currently speaking.
     */
    boolean Tolk_IsSpeaking();

    /**
     * Stop current speech output.
     */
    boolean Tolk_Silence();

    /**
     * Detect the currently running screen reader.
     * @return the name of the screen reader, or null if none detected
     */
    WString Tolk_DetectScreenReader();

    /**
     * Try to load the Tolk native library.
     * Returns null if Tolk.dll is not available.
     */
    static TolkLibrary load() {
        try {
            return Native.load("Tolk", TolkLibrary.class);
        } catch (UnsatisfiedLinkError e) {
            return null;
        }
    }
}
