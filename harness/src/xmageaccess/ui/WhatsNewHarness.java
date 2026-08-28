package xmageaccess.ui;

import javafx.application.Platform;
import mage.client.dialog.WhatsNewDialog;
import mage.client.util.AppUtil;

/**
 * Drives WhatsNewDialogHandler against a stand-in news dialog.
 *
 * What it pins down: the page text really is fetched through
 * {@code Platform.runLater} (the only legal way to touch a WebEngine) and
 * through {@code executeScript}, that the reading cursor sees paragraphs
 * rather than raw markup, that Ctrl+B hands XMage's own WHATS_NEW_PAGE
 * constant to AppUtil, that Ctrl+C copies the page with its line breaks
 * intact, that Ctrl+Enter reaches the dialog's Close button, and that a page
 * with no text is reported as missing instead of copied as an empty string.
 */
public class WhatsNewHarness {

    private static int failures = 0;

    private static void check(String what, boolean ok) {
        System.out.println((ok ? "  PASS  " : "  FAIL  ") + what);
        if (!ok) failures++;
    }

    public static void main(String[] args) {
        String page = "XMage 1.4.61\n\n   Two new sets   \n\nBug fixes and card fixes.\n";

        System.out.println("Reading the page out of the WebView");
        int runLaterBefore = Platform.runLaterCalls;
        WhatsNewDialog dialog = new WhatsNewDialog(page);
        WhatsNewDialogHandler handler = new WhatsNewDialogHandler(dialog);
        handler.attach();

        check("the text is fetched on the JavaFX thread, not the EDT",
                Platform.runLaterCalls == runLaterBefore + 1);
        check("the engine is asked for the rendered text",
                dialog.getEngine().lastScript != null
                        && dialog.getEngine().lastScript.contains("innerText"));
        check("blank lines are dropped", handler.newsLines().size() == 3);
        check("the heading comes first",
                "XMage 1.4.61".equals(handler.newsLines().get(0)));
        check("runs of whitespace collapse",
                "Two new sets".equals(handler.newsLines().get(1)));

        System.out.println("Handing the page on");
        handler.openInBrowser();
        check("Ctrl+B opens XMage's own news address",
                WhatsNewDialog.WHATS_NEW_PAGE.equals(AppUtil.lastBrowserUrl));

        handler.copyToClipboard();
        check("Ctrl+C copies the page with its line breaks intact",
                page.equals(AppUtil.lastClipboardText));

        handler.close();
        check("Ctrl+Enter clicks the dialog's own Close button",
                dialog.cancelClicks == 1);
        handler.detach();

        System.out.println("A page that yielded nothing");
        AppUtil.lastClipboardText = null;
        WhatsNewDialog blank = new WhatsNewDialog("   \n \n ");
        WhatsNewDialogHandler blankHandler = new WhatsNewDialogHandler(blank);
        blankHandler.attach();
        check("whitespace alone counts as no page", blankHandler.newsLines().isEmpty());
        blankHandler.copyToClipboard();
        check("nothing is copied when there was nothing to read",
                AppUtil.lastClipboardText == null);
        blankHandler.detach();

        System.out.println("An engine that hands back nothing");
        AppUtil.lastBrowserUrl = null;
        WhatsNewDialog noEngine = new WhatsNewDialog(null);
        WhatsNewDialogHandler noEngineHandler = new WhatsNewDialogHandler(noEngine);
        noEngineHandler.attach();
        check("an engine that returns nothing leaves no lines",
                noEngineHandler.newsLines().isEmpty());
        noEngineHandler.openInBrowser();
        check("the browser route still works without page text",
                WhatsNewDialog.WHATS_NEW_PAGE.equals(AppUtil.lastBrowserUrl));
        noEngineHandler.detach();

        System.out.println();
        System.out.println(failures == 0 ? "ALL CHECKS PASSED" : failures + " CHECK(S) FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }
}
