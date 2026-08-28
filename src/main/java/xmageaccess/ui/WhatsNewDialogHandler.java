package xmageaccess.ui;

import xmageaccess.AccessibilityManager;
import xmageaccess.speech.SpeechOutput;
import xmageaccess.util.Log;

import javax.swing.JButton;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static xmageaccess.util.ReflectionUtils.callMethodWithArg;
import static xmageaccess.util.ReflectionUtils.callStaticVoid;
import static xmageaccess.util.ReflectionUtils.findFieldDeep;
import static xmageaccess.util.ReflectionUtils.findFieldTyped;
import static xmageaccess.util.ReflectionUtils.getField;

/**
 * Accessibility handler for XMage's WhatsNewDialog — the news page that opens
 * on its own at startup whenever the news version changed (MageFrame calls
 * {@code showWhatsNewDialog(false)} after the connect dialog), and on demand
 * from the tables panel, the connect dialog and the about dialog.
 *
 * <p>The dialog is modal and its content is a JavaFX WebView, so a screen
 * reader finds nothing in it whatsoever — the client simply appears to stop
 * responding. This handler says what opened, reads the page out of the
 * WebView's own DOM line by line, and offers the system browser for the real
 * thing, links included.
 *
 * <p>Reading the page means asking the WebEngine for
 * {@code document.body.innerText}, which is only legal on the JavaFX
 * application thread. The text is therefore fetched through
 * {@code Platform.runLater} and cached; nothing here blocks the EDT. XMage
 * only shows the dialog once its {@code isPageReady} flag is set, so the DOM
 * is loaded by the time we ask.
 *
 * Keyboard shortcuts:
 *   Ctrl+Down/Up  - Next / previous line of the news (text only, no position)
 *   Ctrl+R        - Read the current line again, with its position
 *   Ctrl+Shift+R  - Read the whole page
 *   Ctrl+B        - Open the news page in the system browser
 *   Ctrl+C        - Copy the news text to the clipboard
 *   Ctrl+Enter    - Close (Escape, XMage's own binding, does the same)
 */
public class WhatsNewDialogHandler {

    private static final String APP_UTIL = "mage.client.util.AppUtil";
    private static final String PLATFORM = "javafx.application.Platform";

    /** Rendered text if the engine offers it, raw text nodes otherwise. */
    private static final String READ_BODY_SCRIPT =
            "(function(){var b=document.body;"
                    + "if(!b)return '';return b.innerText||b.textContent||'';})()";

    private final Component dialog;
    private JButton buttonCancel;
    private KeyEventDispatcher keyDispatcher;

    /** Both written on the JavaFX thread, read on the EDT. */
    private volatile String rawText;
    private volatile List<String> lines;

    private int cursor = -1;

    public WhatsNewDialogHandler(Component dialog) {
        this.dialog = dialog;
    }

    public void attach() {
        try {
            buttonCancel = findFieldTyped(dialog, "buttonCancel", JButton.class);
            loadNewsText();
            addKeyboardShortcuts();
            speak("What's new. XMage's news page opened. "
                    + "Ctrl+Down reads it line by line, Ctrl+B opens it in your browser, "
                    + "Ctrl+C copies it, Escape closes.");
        } catch (Exception e) {
            Log.warn("WhatsNew", "attach failed", e);
        }
    }

    public void detach() {
        if (keyDispatcher != null) {
            KeyboardFocusManager.getCurrentKeyboardFocusManager()
                    .removeKeyEventDispatcher(keyDispatcher);
            keyDispatcher = null;
        }
    }

    // ---- reading the page ------------------------------------------------

    /**
     * Asks the WebEngine for the page text on the JavaFX thread. Fire and
     * forget: whatever comes back lands in {@link #rawText} and
     * {@link #lines}, and the read shortcuts use it once it is there.
     */
    private void loadNewsText() {
        final Object engine = findFieldDeep(dialog, "engine");
        if (engine == null) {
            Log.warn("WhatsNew", "no web engine on the dialog, page text unavailable");
            return;
        }
        boolean queued = callStaticVoid(PLATFORM, "runLater",
                new Class[]{Runnable.class}, new Runnable() {
                    @Override
                    public void run() {
                        Object text = callMethodWithArg(engine, "executeScript",
                                String.class, READ_BODY_SCRIPT);
                        if (text == null) return;
                        List<String> split = splitLines(text.toString());
                        if (split.isEmpty()) return;
                        rawText = text.toString();
                        lines = split;
                    }
                });
        if (!queued) {
            Log.warn("WhatsNew", "JavaFX unavailable, cannot read the page text");
        }
    }

    /** Paragraph-ish units: the engine's own line breaks, blanks dropped. */
    private static List<String> splitLines(String text) {
        List<String> out = new ArrayList<>();
        for (String line : text.split("\\R")) {
            String trimmed = line.replaceAll("\\s+", " ").trim();
            if (!trimmed.isEmpty()) out.add(trimmed);
        }
        return out;
    }

    List<String> newsLines() {
        List<String> text = lines;
        return text != null ? text : Collections.<String>emptyList();
    }

    private void move(int direction) {
        List<String> text = newsLines();
        if (text.isEmpty()) {
            notReady();
            return;
        }
        int next = cursor + direction;
        if (next < 0) {
            speak("Start of the news.");
            return;
        }
        if (next >= text.size()) {
            speak("End of the news.");
            return;
        }
        cursor = next;
        speak(text.get(cursor));
    }

    private void readCurrentLine() {
        List<String> text = newsLines();
        if (text.isEmpty()) {
            notReady();
            return;
        }
        if (cursor < 0) {
            speak(text.size() + " lines. Ctrl+Down starts reading.");
            return;
        }
        speak("Line " + (cursor + 1) + " of " + text.size() + ". " + text.get(cursor));
    }

    private void readWholePage() {
        List<String> text = newsLines();
        if (text.isEmpty()) {
            notReady();
            return;
        }
        speak(joinLines(text));
    }

    private static String joinLines(List<String> text) {
        StringBuilder sb = new StringBuilder();
        for (String line : text) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(line);
        }
        return sb.toString();
    }

    /**
     * The fetch runs on the JavaFX thread, so a read can arrive first. Ask
     * again — a second attempt costs nothing and covers a page that finished
     * loading late.
     */
    private void notReady() {
        loadNewsText();
        speak("The news text is not ready yet. Try again in a moment, "
                + "or press Ctrl+B to read it in your browser.");
    }

    // ---- actions ---------------------------------------------------------

    void openInBrowser() {
        String url = getField(null, dialog.getClass(), "WHATS_NEW_PAGE", String.class);
        if (url == null) {
            speak("The news address is not available.");
            return;
        }
        if (callStaticVoid(APP_UTIL, "openUrlInSystemBrowser", new Class[]{String.class}, url)) {
            speak("Opening the news page in your browser.");
        } else {
            speak("Could not open the browser.");
        }
    }

    void copyToClipboard() {
        String text = rawText;
        if (text == null) {
            notReady();
            return;
        }
        if (callStaticVoid(APP_UTIL, "setClipboardData", new Class[]{String.class}, text)) {
            speak("News copied to the clipboard.");
        } else {
            speak("Could not copy the news.");
        }
    }

    void close() {
        if (buttonCancel == null || !buttonCancel.isEnabled()) {
            speak("Cannot close. Press Escape.");
            return;
        }
        speak("Closing.");
        buttonCancel.doClick();
    }

    // ---- keyboard --------------------------------------------------------

    private void addKeyboardShortcuts() {
        keyDispatcher = e -> {
            if (e.getID() != KeyEvent.KEY_PRESSED) return false;
            if (!isDialogActive()) return false;
            if (!e.isControlDown() || e.isAltDown()) return false;

            if (e.isShiftDown()) {
                if (e.getKeyCode() == KeyEvent.VK_R) {
                    readWholePage();
                    return true;
                }
                return false;
            }

            switch (e.getKeyCode()) {
                case KeyEvent.VK_DOWN:
                    move(1);
                    return true;
                case KeyEvent.VK_UP:
                    move(-1);
                    return true;
                case KeyEvent.VK_R:
                    readCurrentLine();
                    return true;
                case KeyEvent.VK_B:
                    openInBrowser();
                    return true;
                case KeyEvent.VK_C:
                    copyToClipboard();
                    return true;
                case KeyEvent.VK_ENTER:
                    close();
                    return true;
                default:
                    return false;
            }
        };
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addKeyEventDispatcher(keyDispatcher);
    }

    /**
     * The dialog is a JInternalFrame on XMage's desktop, so "active" means
     * the main window has focus — not one of the agent's own windows.
     */
    private boolean isDialogActive() {
        if (!dialog.isVisible()) return false;
        Window active = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
        if (active == null) return false;
        if (dialog instanceof Window) return dialog == active;
        return SwingUtilities.getWindowAncestor(dialog) == active;
    }

    private void speak(String text) {
        SpeechOutput speech = AccessibilityManager.getInstance().getSpeech();
        if (speech != null) speech.speak(text);
    }
}
