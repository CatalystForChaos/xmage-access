package xmageaccess.speech;

import com.sun.jna.WString;

/**
 * Speech output via Tolk, which communicates directly with screen readers
 * like NVDA and JAWS on Windows. Also supports braille displays.
 *
 * Falls back to SAPI (Windows built-in speech) if no screen reader is running.
 */
public class TolkSpeech implements SpeechEngine {

    private final TolkLibrary tolk;
    private boolean loaded;

    public TolkSpeech(TolkLibrary tolk) {
        this.tolk = tolk;
        try {
            tolk.Tolk_Load();
            tolk.Tolk_TrySAPI(true);
            loaded = true;

            WString screenReader = tolk.Tolk_DetectScreenReader();
            if (screenReader != null) {
                System.out.println("[XMage Access] Tolk: Detected screen reader: " + screenReader);
            } else {
                System.out.println("[XMage Access] Tolk: No screen reader detected, using SAPI fallback.");
            }
        } catch (Exception e) {
            System.err.println("[XMage Access] Tolk: Failed to initialize: " + e.getMessage());
            loaded = false;
        }
    }

    @Override
    public void speak(String text, boolean interrupt) {
        if (!loaded) return;
        try {
            // Use Tolk_Output to send to both speech and braille
            tolk.Tolk_Output(new WString(text), interrupt);
        } catch (Exception e) {
            System.err.println("[XMage Access] Tolk speech error: " + e.getMessage());
        }
    }

    @Override
    public void silence() {
        if (!loaded) return;
        try {
            tolk.Tolk_Silence();
        } catch (Exception e) {
            System.err.println("[XMage Access] Tolk silence error: " + e.getMessage());
        }
    }

    public boolean isLoaded() {
        return loaded;
    }

    public void shutdown() {
        if (loaded) {
            try {
                tolk.Tolk_Unload();
            } catch (Exception e) {
                // Ignore during shutdown
            }
            loaded = false;
        }
    }
}
