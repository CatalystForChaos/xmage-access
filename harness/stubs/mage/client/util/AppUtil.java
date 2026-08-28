package mage.client.util;

/** Records the two static calls the agent makes, standing in for XMage's AppUtil. */
public class AppUtil {
    public static String lastBrowserUrl;
    public static String lastClipboardText;
    public static void openUrlInSystemBrowser(String url) { lastBrowserUrl = url; }
    public static void setClipboardData(String text) { lastClipboardText = text; }
}
