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
     * Format card color from an ObjectColor object via reflection.
     * Returns a readable string like "white blue" or "colorless", or null if unavailable.
     */
    public static String formatColor(Object colorObj) {
        if (colorObj == null) return null;
        try {
            StringBuilder sb = new StringBuilder();
            java.lang.reflect.Method m;
            try { m = colorObj.getClass().getMethod("isWhite"); if ((Boolean) m.invoke(colorObj)) sb.append("white "); } catch (Exception ignored) {}
            try { m = colorObj.getClass().getMethod("isBlue"); if ((Boolean) m.invoke(colorObj)) sb.append("blue "); } catch (Exception ignored) {}
            try { m = colorObj.getClass().getMethod("isBlack"); if ((Boolean) m.invoke(colorObj)) sb.append("black "); } catch (Exception ignored) {}
            try { m = colorObj.getClass().getMethod("isRed"); if ((Boolean) m.invoke(colorObj)) sb.append("red "); } catch (Exception ignored) {}
            try { m = colorObj.getClass().getMethod("isGreen"); if ((Boolean) m.invoke(colorObj)) sb.append("green "); } catch (Exception ignored) {}
            String result = sb.toString().trim();
            if (result.isEmpty()) {
                try { m = colorObj.getClass().getMethod("isColorless"); if ((Boolean) m.invoke(colorObj)) return "colorless"; } catch (Exception ignored) {}
                return null;
            }
            return result;
        } catch (Exception e) {
            return null;
        }
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
