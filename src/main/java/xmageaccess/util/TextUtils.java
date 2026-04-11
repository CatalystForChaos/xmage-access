package xmageaccess.util;

/**
 * Shared text formatting utilities for accessible speech output.
 */
public final class TextUtils {

    private TextUtils() {}

    /**
     * Convert mana cost symbols like {W}, {U}, {B} to spoken words.
     */
    public static String formatManaCost(String manaCost) {
        if (manaCost == null) return "";
        return manaCost
                .replace("{W}", "white ")
                .replace("{U}", "blue ")
                .replace("{B}", "black ")
                .replace("{R}", "red ")
                .replace("{G}", "green ")
                .replace("{C}", "colorless ")
                .replace("{X}", "X ")
                .replaceAll("\\{(\\d+)\\}", "$1 ")
                .trim();
    }

    /**
     * Strip HTML tags and decode common entities for speech output.
     */
    public static String cleanHtml(String text) {
        if (text == null) return "";
        return text.replaceAll("<[^>]*>", " ")
                .replaceAll("&nbsp;", " ")
                .replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
