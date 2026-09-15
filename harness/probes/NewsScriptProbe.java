import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.io.File;
import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Runs the news window's page script, taken from the built JAR, in a real
 * WebKit, and prints what it returns: one line per block, "h" or "t", a tab,
 * the text, a tab, the first link.
 *
 * Run it with the client's own Java 8, which carries JavaFX; see
 * harness/README.md. Arguments: a URL or an HTML file, then a timeout in
 * seconds.
 *
 * The WebView sits in a Scene in a JFXPanel inside a Swing frame, the way
 * XMage's WhatsNewDialog builds its own. That is not decoration: a bare
 * WebEngine stayed in RUNNING and never finished loading even a ten-line
 * local page. The frame is off-screen and non-focusable, so the run shows
 * nothing and takes no keyboard.
 */
public class NewsScriptProbe {

    private static JFXPanel panel;

    public static void main(String[] args) throws Exception {
        Class<?> handler = Class.forName("xmageaccess.ui.WhatsNewDialogHandler");
        Field field = handler.getDeclaredField("READ_BLOCKS_SCRIPT");
        field.setAccessible(true);
        final String script = (String) field.get(null);
        final String url = args[0].startsWith("http") ? args[0] : new File(args[0]).toURI().toString();
        int timeout = args.length > 1 ? Integer.parseInt(args[1]) : 45;

        SwingUtilities.invokeAndWait(() -> {
            JFrame frame = new JFrame();
            frame.setFocusableWindowState(false);
            frame.setSize(400, 300);
            frame.setLocation(-4000, -4000);
            panel = new JFXPanel();
            frame.add(panel);
            frame.setVisible(true);
        });

        final CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            WebView view = new WebView();
            panel.setScene(new Scene(view));
            final WebEngine engine = view.getEngine();
            engine.setJavaScriptEnabled(true);
            engine.getLoadWorker().exceptionProperty().addListener(
                    (obs, old, ex) -> System.out.println("EXCEPTION " + ex));
            engine.getLoadWorker().stateProperty().addListener((obs, old, state) -> {
                System.out.println("STATE " + state);
                if (state == Worker.State.SUCCEEDED || state == Worker.State.FAILED) {
                    try {
                        Object out = engine.executeScript(script);
                        System.out.println("TYPE " + (out == null ? "null" : out.getClass().getName()));
                        System.out.println(out);
                    } catch (Throwable t) {
                        System.out.println("SCRIPT FAILED " + t);
                    }
                    done.countDown();
                }
            });
            engine.load(url);
        });

        if (!done.await(timeout, TimeUnit.SECONDS)) {
            System.out.println("TIMEOUT");
        }
        System.exit(0);
    }
}
