package mage.client.dialog;

import javafx.scene.web.WebEngine;
import javax.swing.JButton;
import javax.swing.JInternalFrame;

/**
 * Stands in for XMage's news dialog: same field names and the same public
 * page constant, on a JInternalFrame because the real one extends MageDialog,
 * which extends JInternalFrame.
 */
public class WhatsNewDialog extends JInternalFrame {
    public static final String WHATS_NEW_PAGE = "https://jaydi85.github.io/xmage-web-news/news.html";

    public int cancelClicks;

    private WebEngine engine;
    private JButton buttonCancel = new JButton("Close");

    public WebEngine getEngine() { return engine; }

    public WhatsNewDialog(Object pageText) {
        this.engine = new WebEngine(pageText);
        buttonCancel.addActionListener(e -> cancelClicks++);
    }
}
