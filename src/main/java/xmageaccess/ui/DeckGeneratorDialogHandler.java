package xmageaccess.ui;

import xmageaccess.AccessibilityManager;
import xmageaccess.speech.SpeechOutput;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Accessibility handler for the XMage Deck Generator Dialog.
 * The color chooser renders mana symbols as images and is not focusable,
 * so we provide speech descriptions and fix focus.
 */
public class DeckGeneratorDialogHandler {

    private final Component dialog;
    private final Map<Component, String> componentLabels = new LinkedHashMap<>();
    private FocusListener focusListener;
    private KeyEventDispatcher keyDispatcher;

    // Color code to readable name mapping
    private static final Map<String, String> COLOR_NAMES = new LinkedHashMap<>();
    static {
        COLOR_NAMES.put("u", "Blue");
        COLOR_NAMES.put("r", "Red");
        COLOR_NAMES.put("b", "Black");
        COLOR_NAMES.put("g", "Green");
        COLOR_NAMES.put("w", "White");
        COLOR_NAMES.put("x", "Random single color");
        COLOR_NAMES.put("bu", "Black Blue");
        COLOR_NAMES.put("bg", "Black Green");
        COLOR_NAMES.put("br", "Black Red");
        COLOR_NAMES.put("bw", "Black White");
        COLOR_NAMES.put("ug", "Blue Green");
        COLOR_NAMES.put("ur", "Blue Red");
        COLOR_NAMES.put("uw", "Blue White");
        COLOR_NAMES.put("gr", "Green Red");
        COLOR_NAMES.put("gw", "Green White");
        COLOR_NAMES.put("rw", "Red White");
        COLOR_NAMES.put("xx", "Random two colors");
        COLOR_NAMES.put("bur", "Black Blue Red");
        COLOR_NAMES.put("buw", "Black Blue White");
        COLOR_NAMES.put("bug", "Black Blue Green");
        COLOR_NAMES.put("brg", "Black Red Green");
        COLOR_NAMES.put("brw", "Black Red White");
        COLOR_NAMES.put("bgw", "Black Green White");
        COLOR_NAMES.put("wur", "White Blue Red");
        COLOR_NAMES.put("wug", "White Blue Green");
        COLOR_NAMES.put("wrg", "White Red Green");
        COLOR_NAMES.put("rgu", "Red Green Blue");
        COLOR_NAMES.put("xxx", "Random three colors");
    }

    public DeckGeneratorDialogHandler(Component dialog) {
        this.dialog = dialog;
    }

    public void attach() {
        try {
            discoverComponents();
            addFocusAnnouncements();
            addKeyboardShortcuts();

            // Build welcome message
            StringBuilder welcome = new StringBuilder("Deck generator. ");

            // Read current color selection
            JComboBox<?> colorsChooser = findColorsChooser();
            if (colorsChooser != null) {
                // Fix: make it focusable so users can tab to it
                colorsChooser.setFocusable(true);
                Object selected = colorsChooser.getSelectedItem();
                if (selected != null) {
                    String colorName = COLOR_NAMES.getOrDefault(selected.toString(), selected.toString());
                    welcome.append("Colors: ").append(colorName).append(". ");
                }
            }

            JComboBox<?> cbSets = findField("cbSets", JComboBox.class);
            if (cbSets != null && cbSets.getSelectedItem() != null) {
                welcome.append("Format: ").append(cbSets.getSelectedItem()).append(". ");
            }

            JComboBox<?> cbDeckSize = findField("cbDeckSize", JComboBox.class);
            if (cbDeckSize != null && cbDeckSize.getSelectedItem() != null) {
                welcome.append("Deck size: ").append(cbDeckSize.getSelectedItem()).append(". ");
            }

            welcome.append("Tab through options. Ctrl+Enter to generate.");
            speak(welcome.toString());

        } catch (Exception e) {
            System.err.println("[XMage Access] Error attaching to deck generator: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void detach() {
        if (focusListener != null) {
            for (Component comp : componentLabels.keySet()) {
                comp.removeFocusListener(focusListener);
            }
        }
        if (keyDispatcher != null) {
            KeyboardFocusManager.getCurrentKeyboardFocusManager()
                    .removeKeyEventDispatcher(keyDispatcher);
            keyDispatcher = null;
        }
    }

    private void discoverComponents() {
        // Find the color chooser - it's inside a panel, not a direct field of the dialog
        JComboBox<?> colorsChooser = findColorsChooser();
        if (colorsChooser != null) {
            componentLabels.put(colorsChooser, "Deck colors");
        }

        addField("cbSets", "Card set or format");
        addField("cbDeckSize", "Deck size");
        addField("cSingleton", "Singleton mode, one copy of each card");
        addField("cArtifacts", "Include artifacts");
        addField("cNonBasicLands", "Use non-basic lands");
        addField("cColorless", "Allow colorless mana cards");
        addField("cCommander", "Add legendary creature as commander");
        addField("cAdvanced", "Advanced distribution settings");
        addField("cbCMC", "Average mana cost");
        addField("btnGenerate", "Generate deck");
        addField("btnCancel", "Cancel");
        addField("btnReset", "Reset advanced settings");
    }

    /**
     * The ColorsChooser is not a direct field of the dialog - it's created
     * locally in the constructor. We need to find it by scanning components.
     */
    private JComboBox<?> findColorsChooser() {
        return findComboBoxByClass(dialog, "ColorsChooser");
    }

    private JComboBox<?> findComboBoxByClass(Component comp, String className) {
        if (comp == null) return null;
        if (comp.getClass().getSimpleName().equals(className) && comp instanceof JComboBox) {
            return (JComboBox<?>) comp;
        }
        if (comp instanceof Container) {
            for (Component child : ((Container) comp).getComponents()) {
                JComboBox<?> result = findComboBoxByClass(child, className);
                if (result != null) return result;
            }
        }
        return null;
    }

    private void addField(String fieldName, String label) {
        Component comp = findField(fieldName, Component.class);
        if (comp != null) {
            componentLabels.put(comp, label);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T findField(String fieldName, Class<T> type) {
        // Search the dialog and its content pane for the field
        // The DeckGeneratorDialog uses local variables, so we scan by component tree
        try {
            // Try direct field access first
            Class<?> clazz = dialog.getClass();
            while (clazz != null) {
                try {
                    Field field = clazz.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    Object val = field.get(dialog);
                    if (type.isInstance(val)) {
                        return (T) val;
                    }
                } catch (NoSuchFieldException e) {
                    clazz = clazz.getSuperclass();
                }
            }
        } catch (Exception e) {
            // Fall through to component scan
        }

        // Scan the component tree by name
        Component found = findComponentByName(dialog, fieldName);
        if (found != null && type.isInstance(found)) {
            return (T) found;
        }
        return null;
    }

    private Component findComponentByName(Component comp, String name) {
        if (comp == null) return null;
        if (name.equals(comp.getName())) return comp;
        if (comp instanceof Container) {
            for (Component child : ((Container) comp).getComponents()) {
                Component result = findComponentByName(child, name);
                if (result != null) return result;
            }
        }
        return null;
    }

    private void addFocusAnnouncements() {
        focusListener = new FocusListener() {
            @Override
            public void focusGained(FocusEvent e) {
                Component comp = e.getComponent();
                String label = componentLabels.get(comp);

                // Check parent for spinner editors
                if (label == null && comp.getParent() != null) {
                    label = componentLabels.get(comp.getParent());
                }
                if (label == null) return;

                StringBuilder announcement = new StringBuilder(label);

                if (comp instanceof JComboBox) {
                    Object selected = ((JComboBox<?>) comp).getSelectedItem();
                    if (selected != null) {
                        // Special handling for color chooser
                        if (comp.getClass().getSimpleName().equals("ColorsChooser")) {
                            String colorName = COLOR_NAMES.getOrDefault(
                                    selected.toString(), selected.toString());
                            announcement.append(": ").append(colorName);
                        } else {
                            announcement.append(": ").append(selected);
                        }
                    }
                } else if (comp instanceof JCheckBox) {
                    boolean checked = ((JCheckBox) comp).isSelected();
                    announcement.append(": ").append(checked ? "checked" : "not checked");
                } else if (comp instanceof JTextField) {
                    String text = ((JTextField) comp).getText();
                    if (text != null && !text.isEmpty()) {
                        announcement.append(": ").append(text);
                    }
                }

                speak(announcement.toString());
            }

            @Override
            public void focusLost(FocusEvent e) {}
        };

        for (Component comp : componentLabels.keySet()) {
            comp.addFocusListener(focusListener);

            // Set accessible name
            if (comp instanceof JComponent) {
                String label = componentLabels.get(comp);
                if (label != null) {
                    comp.getAccessibleContext().setAccessibleName(label);
                }
            }
        }

        // Also add a listener to the color chooser to announce changes
        JComboBox<?> colorsChooser = findColorsChooser();
        if (colorsChooser != null) {
            colorsChooser.addActionListener(e -> {
                Object selected = colorsChooser.getSelectedItem();
                if (selected != null) {
                    String colorName = COLOR_NAMES.getOrDefault(
                            selected.toString(), selected.toString());
                    speak("Colors: " + colorName);
                }
            });
        }
    }

    private void addKeyboardShortcuts() {
        keyDispatcher = e -> {
                    if (e.getID() != java.awt.event.KeyEvent.KEY_PRESSED) return false;
                    if (!dialog.isVisible()) return false;

                    // Ctrl+Enter = Generate
                    if (e.isControlDown() && e.getKeyCode() == java.awt.event.KeyEvent.VK_ENTER) {
                        Component btn = findField("btnGenerate", Component.class);
                        if (btn instanceof JButton && btn.isEnabled()) {
                            speak("Generating deck.");
                            ((JButton) btn).doClick();
                        }
                        return true;
                    }

                    return false;
                };
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addKeyEventDispatcher(keyDispatcher);
    }

    private void speak(String text) {
        SpeechOutput speech = AccessibilityManager.getInstance().getSpeech();
        if (speech != null) {
            speech.speak(text);
        }
    }
}
