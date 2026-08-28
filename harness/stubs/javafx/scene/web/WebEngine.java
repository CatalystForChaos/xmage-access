package javafx.scene.web;

/**
 * Stands in for the WebView's engine. The real executeScript returns a
 * JavaScript string as a java.lang.String; here it returns whatever the
 * harness loaded, and remembers the script it was asked to run.
 */
public class WebEngine {
    public String lastScript;
    public Object result;
    public WebEngine(Object result) { this.result = result; }
    public Object executeScript(String script) { lastScript = script; return result; }
}
