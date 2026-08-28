package xmageaccess.ui;

import xmageaccess.AccessibilityManager;
import xmageaccess.speech.SpeechOutput;

/**
 * Announces that the deck editor has opened, and names the keys that do
 * something once focus is in the accessible deck editor window — which
 * {@link UIWatcher} opens at the same moment.
 *
 * <p>It used to carry three shortcuts of its own (Ctrl+F1 to F3, deck and
 * sideboard summaries) that only fired while the accessible window was
 * closed. That window opens with the panel, so they were already all but
 * unreachable, and the agent no longer takes keys inside XMage's own window
 * at all — see {@code UiUtils.isAgentWindowActive}. The summaries they read
 * live in {@link AccessibleDeckEditorWindow}, which reads them from the same
 * fields.
 */
public class DeckEditorHandler {

    public DeckEditorHandler() {
    }

    public void attach() {
        speak("Deck editor opened. "
                + "Ctrl+N new deck, Ctrl+O load, Ctrl+S save. "
                + "Ctrl+F1 for all shortcuts.");
    }

    public void detach() {
        // Nothing to unhook: no listeners, no timers, no shortcuts.
    }

    private void speak(String text) {
        SpeechOutput speech = AccessibilityManager.getInstance().getSpeech();
        if (speech != null) {
            speech.speak(text);
        }
    }
}
