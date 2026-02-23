package xmageaccess;

import xmageaccess.speech.SpeechOutput;
import xmageaccess.ui.UIWatcher;

import javax.swing.*;
import java.lang.instrument.Instrumentation;

/**
 * Central coordinator for all accessibility features.
 */
public class AccessibilityManager {

    private static final AccessibilityManager INSTANCE = new AccessibilityManager();

    private SpeechOutput speech;
    private UIWatcher uiWatcher;
    private Instrumentation instrumentation;
    private boolean initialized;

    private AccessibilityManager() {
    }

    public static AccessibilityManager getInstance() {
        return INSTANCE;
    }

    public void initialize(Instrumentation inst) {
        if (initialized) {
            return;
        }
        this.instrumentation = inst;

        // Initialize speech output
        speech = new SpeechOutput();
        speech.initialize();

        // Start UI watcher on the Swing EDT (needs to wait for AWT to be ready)
        SwingUtilities.invokeLater(() -> {
            uiWatcher = new UIWatcher();
            uiWatcher.start();
        });

        // Announce that the mod is loaded
        speech.speak("XMage Access loaded.");

        initialized = true;
    }

    public SpeechOutput getSpeech() {
        return speech;
    }

    public UIWatcher getUIWatcher() {
        return uiWatcher;
    }

    public Instrumentation getInstrumentation() {
        return instrumentation;
    }
}
