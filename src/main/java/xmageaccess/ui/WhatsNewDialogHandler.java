package xmageaccess.ui;

import xmageaccess.AccessibilityManager;
import xmageaccess.speech.SpeechOutput;
import xmageaccess.util.Log;

import javax.swing.JButton;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;

import static xmageaccess.util.ReflectionUtils.callMethod;
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
 * reader finds nothing in it whatsoever. This handler takes the page out of
 * the WebView's own DOM as blocks — headings, paragraphs and list items,
 * each with its first link — and puts them in an {@link AccessibleNewsWindow}.
 * It binds no keys of its own any more; the window is the way in.
 *
 * <p>Reading the DOM is only legal on the JavaFX application thread, so the
 * blocks are fetched through {@code Platform.runLater} and handed to the EDT.
 * XMage only shows the dialog once its {@code isPageReady} flag is set, so
 * the page has loaded by the time we ask. The window opens when the blocks
 * arrive, or after {@link #OPEN_TIMEOUT_MS} without them, and then says the
 * text could not be read and offers the system browser.
 */
public class WhatsNewDialogHandler {

    private static final String APP_UTIL = "mage.client.util.AppUtil";
    private static final String PLATFORM = "javafx.application.Platform";

    /** How long the window waits for the page text before opening without it. */
    static final int OPEN_TIMEOUT_MS = 3000;

    /**
     * Runs in the page and returns one line per block: "h" or "t", a tab, the
     * block's text, a tab, the first http link in it. It walks headings,
     * paragraphs and list items in document order. A heading takes all of
     * its text — XMage's page puts a post's title in a div inside the h2. A
     * paragraph or list item takes its own text only, leaving out nested
     * lists, paragraphs and headings, which are blocks of their own; its link
     * likewise. With no such blocks at all it falls back to the rendered
     * text, a block per line.
     */
    static final String READ_BLOCKS_SCRIPT = "(function(){"
            + "var b=document.body;if(!b)return '';"
            + "function clean(s){return (s||'').replace(/\\s+/g,' ').replace(/^ | $/g,'');}"
            + "function own(el){var s='';"
            + "for(var n=el.firstChild;n;n=n.nextSibling){"
            + "if(n.nodeType===3){s+=n.nodeValue;}"
            + "else if(n.nodeType===1&&!/^(UL|OL|P|H[1-6]|SCRIPT|STYLE)$/.test(n.tagName)){"
            + "s+=(/^(DIV|BR)$/.test(n.tagName)?' '+own(n)+' ':own(n));}}"
            + "return s;}"
            + "function link(el){var as=el.getElementsByTagName('a');"
            + "for(var i=0;i<as.length;i++){var h=as[i].getAttribute('href');"
            + "if(!h||h.indexOf('http')!==0)continue;"
            + "var p=as[i].parentNode;"
            + "while(p&&p!==el&&!/^(UL|OL|P|H[1-6])$/.test(p.tagName)){p=p.parentNode;}"
            + "if(p===el)return h;}"
            + "return '';}"
            + "var out=[],els=b.querySelectorAll('h1,h2,h3,h4,h5,h6,p,li');"
            + "for(var i=0;i<els.length;i++){var el=els[i],hd=/^H[1-6]$/.test(el.tagName);"
            + "var t=clean(hd?el.textContent:own(el));"
            + "if(t)out.push((hd?'h':'t')+'\\t'+t+'\\t'+link(el));}"
            + "if(!out.length){var ls=(b.innerText||b.textContent||'').split('\\n');"
            + "for(var j=0;j<ls.length;j++){var l=clean(ls[j]);if(l)out.push('t\\t'+l+'\\t');}}"
            + "return out.join('\\n');})()";

    private final Component dialog;
    private final boolean showWindow;
    private JButton buttonCancel;
    private AccessibleNewsWindow window;
    private Timer openTimer;
    private boolean opened;
    private boolean closed;

    /** The page as plain text, a line per block, for the clipboard. */
    private String pageText;

    public WhatsNewDialogHandler(Component dialog) {
        this(dialog, true);
    }

    /** With {@code showWindow} false the window is built and filled but never shown: WhatsNewHarness. */
    WhatsNewDialogHandler(Component dialog, boolean showWindow) {
        this.dialog = dialog;
        this.showWindow = showWindow;
    }

    public void attach() {
        try {
            buttonCancel = findFieldTyped(dialog, "buttonCancel", JButton.class);
            window = new AccessibleNewsWindow(this);
            openTimer = new Timer(OPEN_TIMEOUT_MS, e -> openWindow());
            openTimer.setRepeats(false);
            openTimer.start();
            loadPage();
        } catch (Exception e) {
            Log.warn("WhatsNew", "attach failed", e);
        }
    }

    public void detach() {
        closed = true;
        if (openTimer != null) {
            openTimer.stop();
            openTimer = null;
        }
        if (window != null) {
            window.dispose();
        }
    }

    /** The accessible window, once attached; UIWatcher watches it close. */
    AccessibleNewsWindow getWindow() {
        return window;
    }

    // ---- reading the page ------------------------------------------------

    private void loadPage() {
        final Object engine = findFieldDeep(dialog, "engine");
        if (engine == null) {
            Log.warn("WhatsNew", "no web engine on the dialog, page text unavailable");
            pageArrived(null);
            return;
        }
        boolean queued = callStaticVoid(PLATFORM, "runLater",
                new Class[]{Runnable.class}, new Runnable() {
                    @Override
                    public void run() {
                        Object result = callMethodWithArg(engine, "executeScript",
                                String.class, READ_BLOCKS_SCRIPT);
                        final String encoded = result != null ? result.toString() : null;
                        SwingUtilities.invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                pageArrived(encoded);
                            }
                        });
                    }
                });
        if (!queued) {
            Log.warn("WhatsNew", "JavaFX unavailable, cannot read the page text");
            pageArrived(null);
        }
    }

    private void pageArrived(String encoded) {
        if (closed || window == null) return;
        List<AccessibleNewsWindow.Block> blocks = parseBlocks(encoded);
        Log.event("WhatsNew", "page read: " + blocks.size() + " blocks");

        StringBuilder text = new StringBuilder();
        for (AccessibleNewsWindow.Block block : blocks) {
            if (text.length() > 0) text.append('\n');
            text.append(block.text);
        }
        pageText = text.toString();

        boolean late = opened;
        window.setBlocks(blocks);
        openWindow();
        if (late && !blocks.isEmpty()) {
            speak("The news text is ready: " + blocks.size() + " lines in What's new.");
        }
    }

    /** The script's lines back into blocks; malformed and empty lines are skipped. */
    static List<AccessibleNewsWindow.Block> parseBlocks(String encoded) {
        List<AccessibleNewsWindow.Block> blocks = new ArrayList<>();
        if (encoded == null) return blocks;
        for (String line : encoded.split("\n")) {
            String[] parts = line.split("\t", -1);
            if (parts.length < 2) continue;
            String text = parts[1].trim();
            if (text.isEmpty()) continue;
            String link = parts.length > 2 ? parts[2].trim() : "";
            blocks.add(new AccessibleNewsWindow.Block("h".equals(parts[0]), text,
                    link.startsWith("http") ? link : null));
        }
        return blocks;
    }

    private void openWindow() {
        if (opened || closed || window == null) return;
        opened = true;
        if (openTimer != null) openTimer.stop();
        if (showWindow) window.open();
    }

    // ---- what the rows do ------------------------------------------------

    void openInBrowser() {
        String url = getField(null, dialog.getClass(), "WHATS_NEW_PAGE", String.class);
        if (url == null) {
            speak("The news address is not available.");
            return;
        }
        openUrl(url, "Opening the news page in your browser.");
    }

    /** XMage's own page opens only http links, in the system browser; so does this. */
    void openLink(String url) {
        if (url == null || !url.startsWith("http")) {
            speak("No link on this line.");
            return;
        }
        openUrl(url, "Opening the link in your browser.");
    }

    private void openUrl(String url, String confirmation) {
        if (callStaticVoid(APP_UTIL, "openUrlInSystemBrowser", new Class[]{String.class}, url)) {
            speak(confirmation);
        } else {
            speak("Could not open the browser.");
        }
    }

    void copyToClipboard() {
        if (pageText == null || pageText.isEmpty()) {
            speak("There is no news text to copy. The browser has the page.");
            return;
        }
        if (callStaticVoid(APP_UTIL, "setClipboardData", new Class[]{String.class}, pageText)) {
            speak("News copied to the clipboard.");
        } else {
            speak("Could not copy the news.");
        }
    }

    /**
     * Closes the news through XMage's own Close button, then this window.
     * Where the keyboard goes next is UIWatcher's hand-over, as for any
     * window of the agent's that closes.
     */
    void close() {
        if (closed) return;
        if (buttonCancel != null && buttonCancel.isEnabled()) {
            buttonCancel.doClick();
        } else {
            callMethod(dialog, "hideDialog");
        }
        detach();
    }

    private void speak(String text) {
        SpeechOutput speech = AccessibilityManager.getInstance().getSpeech();
        if (speech != null) speech.speak(text);
    }
}
