package xmageaccess.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared text formatting utilities for accessible speech output.
 */
public final class TextUtils {

    private TextUtils() {}

    // Hybrid/compound symbols like {W/U}, {2/G}, {B/P}, {G/W/P}
    private static final Pattern COMPOUND_MANA = Pattern.compile("\\{([WUBRGC\\d]+)/([WUBRGCP])(?:/([P]))?\\}");
    // Any remaining braced symbol
    private static final Pattern LEFTOVER_BRACES = Pattern.compile("\\{([^}]*)\\}");

    /**
     * Convert mana cost symbols like {W}, {U}, {2}, {G/W}, {B/P} to spoken words.
     */
    public static String formatManaCost(String manaCost) {
        if (manaCost == null) return "";
        String s = manaCost
                .replace("{W}", "white ")
                .replace("{U}", "blue ")
                .replace("{B}", "black ")
                .replace("{R}", "red ")
                .replace("{G}", "green ")
                .replace("{C}", "colorless ")
                .replace("{X}", "X ")
                .replace("{S}", "snow ")
                .replace("{E}", "energy ")
                .replace("{T}", "tap ")
                .replace("{Q}", "untap ")
                .replaceAll("\\{(\\d+)\\}", "$1 ");

        // Hybrid and Phyrexian symbols: {G/W} -> "green or white", {B/P} -> "black or phyrexian"
        Matcher m = COMPOUND_MANA.matcher(s);
        if (m.find()) {
            StringBuffer sb = new StringBuffer();
            do {
                String replacement = manaWord(m.group(1)) + " or " + manaWord(m.group(2));
                if (m.group(3) != null) {
                    replacement += " or " + manaWord(m.group(3));
                }
                m.appendReplacement(sb, Matcher.quoteReplacement(replacement + " "));
            } while (m.find());
            m.appendTail(sb);
            s = sb.toString();
        }

        // Unwrap anything still braced so screen readers don't read "{...}" literally
        s = LEFTOVER_BRACES.matcher(s).replaceAll("$1 ");
        return s.trim();
    }

    private static String manaWord(String symbol) {
        switch (symbol) {
            case "W": return "white";
            case "U": return "blue";
            case "B": return "black";
            case "R": return "red";
            case "G": return "green";
            case "C": return "colorless";
            case "P": return "phyrexian";
            default:  return symbol; // numbers like "2"
        }
    }

    /**
     * Strip HTML tags and decode common entities for speech output.
     * Note: &amp; must be decoded last, otherwise "&amp;lt;" would be
     * double-decoded into "<".
     */
    public static String cleanHtml(String text) {
        if (text == null) return "";
        return text.replaceAll("<[^>]*>", " ")
                .replaceAll("&nbsp;", " ")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("&quot;", "\"")
                .replaceAll("&#0?39;", "'")
                .replaceAll("&apos;", "'")
                .replaceAll("&amp;", "&")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
