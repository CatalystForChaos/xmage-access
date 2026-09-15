package xmageaccess.ui;

import javafx.application.Platform;
import mage.client.dialog.WhatsNewDialog;
import mage.client.util.AppUtil;

import javax.swing.SwingUtilities;
import java.awt.KeyEventDispatcher;
import java.lang.reflect.Field;

/**
 * Drives WhatsNewDialogHandler and AccessibleNewsWindow against a stand-in
 * news dialog, without ever showing the window.
 *
 * What it pins down: the page is fetched through Platform.runLater, the only
 * legal way to touch a WebEngine, with a script that walks the page's
 * headings, paragraphs and list items. The blocks it hands back become the
 * rows of the news list in page order, with headings and links saying what
 * they are, and the headings become the Sections list; Enter on a section
 * selects that heading in the news and leaves word of where it landed for
 * the moment the list takes the keyboard. Enter on a line with a link hands
 * exactly that link to AppUtil.openUrlInSystemBrowser, the call XMage's own
 * page makes. The actions open XMage's own WHATS_NEW_PAGE, copy the page as
 * text, and close through the dialog's own Close button, once. A page that
 * yielded nothing says so and still offers the browser. And the handler
 * holds no keyboard dispatcher any more: the shortcuts are gone.
 *
 * What it cannot pin is the script itself, which needs WebKit. That was run
 * against XMage's news page in the client's own Java 8 runtime; see the
 * commit that introduced the window.
 */
public class WhatsNewHarness {

    private static int failures = 0;

    private static void check(String what, boolean ok) {
        System.out.println((ok ? "  PASS  " : "  FAIL  ") + what);
        if (!ok) failures++;
    }

    private static ZoneItem row(ZoneListPanel zone, int index) {
        return zone.getList().getModel().getElementAt(index);
    }

    private static int size(ZoneListPanel zone) {
        return zone.getList().getModel().getSize();
    }

    /** Attaches on the EDT and lets the page text arrive there, as it does through invokeLater. */
    private static WhatsNewDialogHandler attached(Object pageText, WhatsNewDialog[] dialogOut) throws Exception {
        final WhatsNewDialog dialog = new WhatsNewDialog(pageText);
        final WhatsNewDialogHandler handler = new WhatsNewDialogHandler(dialog, false);
        SwingUtilities.invokeAndWait(handler::attach);
        SwingUtilities.invokeAndWait(() -> { });
        dialogOut[0] = dialog;
        return handler;
    }

    public static void main(String[] args) throws Exception {
        String page = "h\tXMage news\t\n"
                + "t\tAug 12, 2026, JayDi85\t\n"
                + "h\tImproved AI, images, and new cards\t\n"
                + "t\tNew release contains improved AI.\t\n"
                + "h\tCard fixes\t\n"
                + "t\tFixed Lightning Bolt (#14118)\thttps://github.com/magefree/mage/issues/14118\n"
                + "t\t\t\n"
                + "not a block\n";

        System.out.println("Reading the page out of the WebView");
        int runLaterBefore = Platform.runLaterCalls;
        final WhatsNewDialog[] dialogs = new WhatsNewDialog[1];
        final WhatsNewDialogHandler handler = attached(page, dialogs);
        final WhatsNewDialog dialog = dialogs[0];
        final AccessibleNewsWindow window = handler.getWindow();

        check("the page is fetched on the JavaFX thread, not the EDT",
                Platform.runLaterCalls == runLaterBefore + 1);
        check("with a script that walks headings, paragraphs and list items",
                dialog.getEngine().lastScript != null
                        && dialog.getEngine().lastScript.contains("querySelectorAll('h1,h2,h3,h4,h5,h6,p,li')"));
        check("every block with text is a row, in page order, and nothing else is",
                size(window.newsZone()) == 6
                        && "Aug 12, 2026, JayDi85".equals(row(window.newsZone(), 1).getDisplayName()));
        check("a heading says it is one",
                "XMage news, heading".equals(row(window.newsZone(), 0).getDisplayName()));
        check("so does a line with a link",
                "Fixed Lightning Bolt (#14118), link".equals(row(window.newsZone(), 5).getDisplayName()));

        System.out.println("Sections");
        check("the headings are the sections, in order",
                size(window.sectionsZone()) == 3
                        && "Card fixes".equals(row(window.sectionsZone(), 2).getDisplayName()));
        SwingUtilities.invokeAndWait(() -> window.jumpToSection(row(window.sectionsZone(), 2)));
        check("Enter on a section selects its heading in the news",
                window.newsZone().getList().getSelectedIndex() == 4);
        check("and says where it landed once the news list has the keyboard",
                "Card fixes, heading. Line 5 of 6.".equals(window.newsZone().pendingFocusAnnouncement()));

        System.out.println("Links and actions");
        AppUtil.lastBrowserUrl = null;
        SwingUtilities.invokeAndWait(() -> window.activateNews(row(window.newsZone(), 3)));
        check("Enter on a line without a link opens nothing", AppUtil.lastBrowserUrl == null);
        SwingUtilities.invokeAndWait(() -> window.activateNews(row(window.newsZone(), 5)));
        check("Enter on a line with a link opens exactly that link",
                "https://github.com/magefree/mage/issues/14118".equals(AppUtil.lastBrowserUrl));
        SwingUtilities.invokeAndWait(() -> window.runAction(row(window.actionsZone(), 0)));
        check("the first action opens XMage's own news address",
                WhatsNewDialog.WHATS_NEW_PAGE.equals(AppUtil.lastBrowserUrl));
        SwingUtilities.invokeAndWait(() -> window.runAction(row(window.actionsZone(), 1)));
        check("copying gives the page as text, a line per row",
                ("XMage news\nAug 12, 2026, JayDi85\nImproved AI, images, and new cards\n"
                        + "New release contains improved AI.\nCard fixes\nFixed Lightning Bolt (#14118)")
                        .equals(AppUtil.lastClipboardText));
        SwingUtilities.invokeAndWait(() -> window.runAction(row(window.actionsZone(), 2)));
        check("closing goes through the dialog's own Close button", dialog.cancelClicks == 1);
        SwingUtilities.invokeAndWait(() -> window.runAction(row(window.actionsZone(), 2)));
        check("and only once, however often it is asked", dialog.cancelClicks == 1);

        System.out.println("A page that yielded nothing");
        AppUtil.lastClipboardText = null;
        final WhatsNewDialogHandler blank = attached("  \n", dialogs);
        final AccessibleNewsWindow blankWindow = blank.getWindow();
        check("says the text could not be read",
                row(blankWindow.newsZone(), 0).getDisplayName().startsWith("The news text could not be read"));
        check("and has no sections to jump to",
                size(blankWindow.sectionsZone()) == 1
                        && row(blankWindow.sectionsZone(), 0).getSourceObject() == null);
        SwingUtilities.invokeAndWait(() -> blankWindow.runAction(row(blankWindow.actionsZone(), 1)));
        check("nothing is copied when there was nothing to read", AppUtil.lastClipboardText == null);
        SwingUtilities.invokeAndWait(blank::detach);

        System.out.println("An engine that hands back nothing");
        AppUtil.lastBrowserUrl = null;
        final WhatsNewDialogHandler nothing = attached(null, dialogs);
        SwingUtilities.invokeAndWait(() ->
                nothing.getWindow().runAction(row(nothing.getWindow().actionsZone(), 0)));
        check("the browser route still works without page text",
                WhatsNewDialog.WHATS_NEW_PAGE.equals(AppUtil.lastBrowserUrl));
        SwingUtilities.invokeAndWait(nothing::detach);

        System.out.println("No shortcuts");
        boolean dispatcher = false;
        for (Field field : WhatsNewDialogHandler.class.getDeclaredFields()) {
            if (KeyEventDispatcher.class.isAssignableFrom(field.getType())) dispatcher = true;
        }
        check("the handler holds no keyboard dispatcher any more", !dispatcher);

        System.out.println();
        System.out.println(failures == 0 ? "ALL CHECKS PASSED" : failures + " CHECK(S) FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }
}
