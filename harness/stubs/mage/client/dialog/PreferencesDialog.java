package mage.client.dialog;
import java.util.LinkedHashMap;
import java.util.Map;

/** Records preference writes, standing in for XMage's PreferencesDialog. */
public class PreferencesDialog {
    public static final Map<String, String> SAVED = new LinkedHashMap<>();
    public static void saveValue(String key, String value) { SAVED.put(key, value); }
}
