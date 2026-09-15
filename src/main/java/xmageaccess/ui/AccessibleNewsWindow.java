package xmageaccess.ui;

import xmageaccess.AccessibilityManager;
import xmageaccess.speech.SpeechOutput;
import xmageaccess.util.UiUtils;

import javax.swing.AbstractAction;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FocusTraversalPolicy;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Accessible window for XMage's news page: the page as rows you read with
 * the arrow keys, its headings as a list to jump from, and what can be done
 * with the page as a third list.
 *
 * <p>The page used to be read through shortcuts on the dialog itself —
 * Ctrl+Down and Ctrl+Up line by line, Ctrl+R, Ctrl+B, Ctrl+C, Ctrl+Enter.
 * That worked in the first test round, but the verdict was that it should be
 * a window with navigable lines and that the keys were a nuisance. So it is
 * built like the other accessible windows: Tab between the lists, the arrow
 * keys within one, Enter to act, D for a row's detail, Escape to close. The
 * page is long — a few hundred rows, most of them list items under a couple
 * of dozen headings — which is what the Sections list is for.
 *
 * <p>XMage's news dialog is modal. MageDialog runs an event loop of its own
 * while it is up, which drops mouse events outside the dialog but dispatches
 * key events as usual, so this window answers the keyboard, not the mouse,
 * while the dialog stands.
 *
 * Keys:
 *   Tab/Shift+Tab - move between What's new, Sections and Actions
 *   Up/Down       - move through a list
 *   Enter         - on a line with a link, open it; on a section, jump to
 *                   its heading; on an action, do it
 *   D             - a line's link address, a section's position, what an
 *                   action does
 *   Escape        - close the news
 */
public class AccessibleNewsWindow extends JFrame {

    /** One block of the page: a heading, or a paragraph or list item, with its first link. */
    static final class Block {
        final boolean heading;
        final String text;
        final String link;

        Block(boolean heading, String text, String link) {
            this.heading = heading;
            this.text = text;
            this.link = link;
        }
    }

    private static final String ACTION_BROWSER = "browser";
    private static final String ACTION_COPY = "copy";
    private static final String ACTION_CLOSE = "close";

    private final WhatsNewDialogHandler handler;

    private final ZoneListPanel newsZone;
    private final ZoneListPanel sectionsZone;
    private final ZoneListPanel actionsZone;

    /** Whether the page gave any text; decides where the keyboard lands. */
    private boolean hasText;

    AccessibleNewsWindow(WhatsNewDialogHandler handler) {
        super("XMage Accessible News");
        this.handler = handler;
        newsZone = new ZoneListPanel("What's new");
        sectionsZone = new ZoneListPanel("Sections");
        actionsZone = new ZoneListPanel("Actions");

        buildUI();
        bindKeys();
        setBlocks(Collections.<Block>emptyList());
        actionsZone.updateItems(Arrays.asList(
                new ZoneItem("Open the news page in your browser",
                        "The page with its links, in your system browser.",
                        ACTION_BROWSER, ZoneItem.ActionType.NONE),
                new ZoneItem("Copy the news to the clipboard",
                        "The text of the page, a line for each row.",
                        ACTION_COPY, ZoneItem.ActionType.NONE),
                new ZoneItem("Close the news",
                        "Closes the news page, as Escape does.",
                        ACTION_CLOSE, ZoneItem.ActionType.NONE)));
    }

    private void buildUI() {
        // Closing the window closes the news: the dialog behind it is modal
        // and would otherwise stay up with nothing left to read it.
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                handler.close();
            }
        });
        setSize(700, 700);
        setLocationRelativeTo(null);

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        newsZone.setPreferredSize(new Dimension(680, 420));
        sectionsZone.setPreferredSize(new Dimension(680, 160));
        actionsZone.setPreferredSize(new Dimension(680, 90));
        actionsZone.setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));
        mainPanel.add(newsZone);
        mainPanel.add(sectionsZone);
        mainPanel.add(actionsZone);
        add(new JScrollPane(mainPanel), BorderLayout.CENTER);

        setFocusCycleRoot(true);
        final List<Component> order = Arrays.<Component>asList(
                newsZone.getList(), sectionsZone.getList(), actionsZone.getList());
        setFocusTraversalPolicy(new FocusTraversalPolicy() {
            @Override
            public Component getComponentAfter(Container container, Component component) {
                int idx = order.indexOf(component);
                return order.get((idx + 1) % order.size());
            }

            @Override
            public Component getComponentBefore(Container container, Component component) {
                int idx = order.indexOf(component);
                if (idx < 0) return order.get(order.size() - 1);
                return order.get((idx - 1 + order.size()) % order.size());
            }

            @Override
            public Component getFirstComponent(Container container) {
                return order.get(0);
            }

            @Override
            public Component getLastComponent(Container container) {
                return order.get(order.size() - 1);
            }

            @Override
            public Component getDefaultComponent(Container container) {
                // Where the keyboard lands when the window takes it: the
                // page, or the actions when the page gave nothing to read.
                // The JDK restores focus to this before the helper asks for
                // the same list, so the two requests cannot disagree.
                return hasText ? newsZone.getList() : actionsZone.getList();
            }
        });
    }

    private void bindKeys() {
        onEnter(newsZone, this::activateNews);
        onEnter(sectionsZone, this::jumpToSection);
        onEnter(actionsZone, this::runAction);
        for (final ZoneListPanel zone : Arrays.asList(newsZone, sectionsZone, actionsZone)) {
            zone.getList().getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_D, 0), "readDetail");
            zone.getList().getActionMap().put("readDetail", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    readDetail(zone.getSelectedItem());
                }
            });
        }

        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "closeNews");
        getRootPane().getActionMap().put("closeNews", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                handler.close();
            }
        });
    }

    private static void onEnter(final ZoneListPanel zone, final Consumer<ZoneItem> action) {
        zone.getList().getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "activateItem");
        zone.getList().getActionMap().put("activateItem", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                ZoneItem item = zone.getSelectedItem();
                if (item != null) action.accept(item);
            }
        });
    }

    // ========== CONTENT ==========

    /**
     * Fills the news and sections lists from the page's blocks. Headings say
     * they are headings and lines with a link say so, since neither shows in
     * the text itself.
     */
    void setBlocks(List<Block> blocks) {
        List<ZoneItem> news = new ArrayList<>();
        List<Integer> headingRows = new ArrayList<>();
        for (Block block : blocks) {
            if (block.heading) {
                headingRows.add(news.size());
                news.add(new ZoneItem(block.text + ", heading", block.text, block,
                        ZoneItem.ActionType.NONE));
            } else if (block.link != null) {
                news.add(new ZoneItem(block.text + ", link", block.link, block,
                        ZoneItem.ActionType.OPEN_LINK));
            } else {
                news.add(new ZoneItem(block.text, block.text, block, ZoneItem.ActionType.NONE));
            }
        }
        hasText = !news.isEmpty();

        List<ZoneItem> sections = new ArrayList<>();
        for (int row : headingRows) {
            Block block = (Block) news.get(row).getSourceObject();
            sections.add(new ZoneItem(block.text, "Line " + (row + 1) + " of " + news.size(),
                    Integer.valueOf(row), ZoneItem.ActionType.NONE));
        }

        if (news.isEmpty()) {
            news.add(new ZoneItem("The news text could not be read. "
                    + "The first action opens the page in your browser.",
                    null, null, ZoneItem.ActionType.NONE));
        }
        if (sections.isEmpty()) {
            sections.add(new ZoneItem("No headings on this page", null, null,
                    ZoneItem.ActionType.NONE));
        }
        newsZone.updateItems(news);
        sectionsZone.updateItems(sections);
    }

    /** Shows the window and gives it the keyboard, saying what opened. */
    void open() {
        setVisible(true);
        if (hasText) {
            newsZone.announceOnNextFocus("What's new. " + newsZone.getList().getModel().getSize()
                    + " lines. Tab reaches the sections and the actions, Escape closes.");
            UiUtils.focusAgentWindow(this, newsZone.getList());
        } else {
            actionsZone.announceOnNextFocus("What's new. The page text could not be read; "
                    + "the first action opens it in your browser. Escape closes.");
            UiUtils.focusAgentWindow(this, actionsZone.getList());
        }
    }

    // ========== WHAT THE ROWS DO ==========

    /** Enter in the news: open the line's link, the way a click on it in XMage's page does. */
    void activateNews(ZoneItem item) {
        Object source = item.getSourceObject();
        if (source instanceof Block && ((Block) source).link != null) {
            handler.openLink(((Block) source).link);
        } else {
            speak("No link on this line.");
        }
    }

    /** Enter in Sections: select the heading in the news and move there. */
    void jumpToSection(ZoneItem item) {
        if (!(item.getSourceObject() instanceof Integer)) {
            speak("There are no headings to jump to.");
            return;
        }
        int row = (Integer) item.getSourceObject();
        int size = newsZone.getList().getModel().getSize();
        if (row < 0 || row >= size) return;
        newsZone.selectQuietly(row);
        newsZone.announceOnNextFocus(newsZone.getList().getModel().getElementAt(row).getDisplayName()
                + ". Line " + (row + 1) + " of " + size + ".");
        newsZone.getList().requestFocusInWindow();
    }

    /** Enter in Actions. */
    void runAction(ZoneItem item) {
        Object key = item.getSourceObject();
        if (ACTION_BROWSER.equals(key)) {
            handler.openInBrowser();
        } else if (ACTION_COPY.equals(key)) {
            handler.copyToClipboard();
        } else if (ACTION_CLOSE.equals(key)) {
            handler.close();
        }
    }

    void readDetail(ZoneItem item) {
        if (item == null) return;
        Object source = item.getSourceObject();
        if (source instanceof Block && ((Block) source).link != null) {
            speak("Link to " + ((Block) source).link);
        } else if (item.getDetailText() != null) {
            speak(item.getDetailText());
        } else {
            speak(item.getDisplayName());
        }
    }

    // ========== FOR THE HARNESS ==========

    ZoneListPanel newsZone() {
        return newsZone;
    }

    ZoneListPanel sectionsZone() {
        return sectionsZone;
    }

    ZoneListPanel actionsZone() {
        return actionsZone;
    }

    private void speak(String text) {
        SpeechOutput speech = AccessibilityManager.getInstance().getSpeech();
        if (speech != null) speech.speak(text);
    }
}
