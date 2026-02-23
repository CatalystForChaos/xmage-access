package xmageaccess.ui;

import xmageaccess.AccessibilityManager;
import xmageaccess.speech.SpeechOutput;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.util.List;

/**
 * Accessibility handler for the XMage PickMultiNumberDialog.
 * Used for distributing values across multiple items (damage distribution, mana payment, etc).
 *
 * Keyboard shortcuts:
 *   Ctrl+Up/Down       - Navigate between items
 *   Ctrl+Left/Right    - Decrease/increase current item value
 *   Ctrl+Enter         - Confirm (when valid)
 *   Ctrl+R             - Re-read all items and total
 */
public class PickMultiNumberDialogHandler {

    private final Component dialog;
    private List<?> infoList;   // List<MageTextArea>
    private List<?> spinnerList; // List<JSpinner>
    private JButton btnOk;
    private JButton btnCancel;
    private int currentIndex = 0;

    public PickMultiNumberDialogHandler(Component dialog) {
        this.dialog = dialog;
    }

    public void attach() {
        try {
            discoverComponents();
            addKeyboardShortcuts();
            announceDialog();
        } catch (Exception e) {
            System.err.println("[XMage Access] Error attaching to PickMultiNumberDialog: " + e.getMessage());
        }
    }

    public void detach() {}

    @SuppressWarnings("unchecked")
    private void discoverComponents() {
        Class<?> clazz = dialog.getClass();
        infoList = getField(clazz, "infoList", List.class);
        spinnerList = getField(clazz, "spinnerList", List.class);
        btnOk = getField(clazz, "btnOk", JButton.class);
        btnCancel = getField(clazz, "btnCancel", JButton.class);
    }

    private void announceDialog() {
        StringBuilder sb = new StringBuilder("Distribute values. ");

        // Read header
        String header = readLabelText("header");
        if (header != null && !header.isEmpty()) {
            sb.append(cleanHtml(header)).append(". ");
        }

        int itemCount = getItemCount();
        sb.append(itemCount).append(" items. ");

        // Read counter (total)
        String counter = readLabelText("counterText");
        if (counter != null && !counter.isEmpty()) {
            sb.append("Total: ").append(counter).append(". ");
        }

        // Announce first item
        if (itemCount > 0) {
            currentIndex = 0;
            sb.append("First: ").append(describeItem(0)).append(". ");
        }

        sb.append("Ctrl+Up, Down to navigate items. Ctrl+Left, Right to adjust value. Ctrl+Enter to confirm.");
        speak(sb.toString());
    }

    private void addKeyboardShortcuts() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addKeyEventDispatcher(e -> {
                    if (e.getID() != KeyEvent.KEY_PRESSED) return false;
                    if (!isDialogVisible()) return false;

                    if (e.isControlDown() && !e.isShiftDown()) {
                        switch (e.getKeyCode()) {
                            case KeyEvent.VK_UP:
                                navigateItem(-1);
                                return true;
                            case KeyEvent.VK_DOWN:
                                navigateItem(1);
                                return true;
                            case KeyEvent.VK_LEFT:
                                adjustValue(-1);
                                return true;
                            case KeyEvent.VK_RIGHT:
                                adjustValue(1);
                                return true;
                            case KeyEvent.VK_ENTER:
                                confirmSelection();
                                return true;
                            case KeyEvent.VK_R:
                                announceDialog();
                                return true;
                        }
                    }

                    return false;
                });
    }

    private void navigateItem(int direction) {
        int count = getItemCount();
        if (count == 0) {
            speak("No items.");
            return;
        }

        currentIndex += direction;
        if (currentIndex < 0) currentIndex = 0;
        if (currentIndex >= count) currentIndex = count - 1;

        speak((currentIndex + 1) + " of " + count + ". " + describeItem(currentIndex));
    }

    private void adjustValue(int direction) {
        if (spinnerList == null || currentIndex < 0 || currentIndex >= spinnerList.size()) return;

        Object spinnerObj = spinnerList.get(currentIndex);
        if (!(spinnerObj instanceof JSpinner)) return;

        JSpinner spinner = (JSpinner) spinnerObj;
        SpinnerModel model = spinner.getModel();
        if (model instanceof SpinnerNumberModel) {
            SpinnerNumberModel numModel = (SpinnerNumberModel) model;
            int current = ((Number) numModel.getValue()).intValue();
            int next = current + direction;

            Comparable<?> min = numModel.getMinimum();
            Comparable<?> max = numModel.getMaximum();

            if (min instanceof Number && next < ((Number) min).intValue()) {
                speak("Minimum: " + min);
                return;
            }
            if (max instanceof Number && next > ((Number) max).intValue()) {
                speak("Maximum: " + max);
                return;
            }

            numModel.setValue(next);

            // Read updated counter
            String counter = readLabelText("counterText");
            String counterInfo = counter != null ? ". Total: " + counter : "";
            speak(next + counterInfo);
        }
    }

    private void confirmSelection() {
        if (btnOk != null && btnOk.isEnabled()) {
            speak("Confirming.");
            btnOk.doClick();
        } else {
            String counter = readLabelText("counterText");
            speak("Cannot confirm yet. " + (counter != null ? "Total: " + counter : ""));
        }
    }

    private String describeItem(int index) {
        StringBuilder sb = new StringBuilder();

        // Get the label text
        if (infoList != null && index < infoList.size()) {
            Object infoObj = infoList.get(index);
            // MageTextArea extends JEditorPane
            if (infoObj instanceof JEditorPane) {
                String text = ((JEditorPane) infoObj).getText();
                text = cleanHtml(text);
                if (!text.isEmpty()) sb.append(text).append(": ");
            }
        }

        // Get the spinner value
        if (spinnerList != null && index < spinnerList.size()) {
            Object spinnerObj = spinnerList.get(index);
            if (spinnerObj instanceof JSpinner) {
                JSpinner spinner = (JSpinner) spinnerObj;
                sb.append(spinner.getValue());

                if (spinner.getModel() instanceof SpinnerNumberModel) {
                    SpinnerNumberModel numModel = (SpinnerNumberModel) spinner.getModel();
                    sb.append(" (").append(numModel.getMinimum()).append(" to ").append(numModel.getMaximum()).append(")");
                }
            }
        }

        return sb.length() > 0 ? sb.toString() : "Unknown item";
    }

    private int getItemCount() {
        if (spinnerList != null) return spinnerList.size();
        if (infoList != null) return infoList.size();
        return 0;
    }

    private String readLabelText(String fieldName) {
        try {
            Field field = findField(dialog.getClass(), fieldName);
            if (field == null) return null;
            field.setAccessible(true);
            Object label = field.get(dialog);
            if (label instanceof JLabel) {
                String text = ((JLabel) label).getText();
                return cleanHtml(text);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String cleanHtml(String text) {
        if (text == null) return "";
        return text.replaceAll("<[^>]*>", "").replaceAll("&nbsp;", " ").replaceAll("\\s+", " ").trim();
    }

    private boolean isDialogVisible() {
        if (!dialog.isVisible()) return false;
        Component c = dialog;
        while (c != null) {
            if (!c.isVisible()) return false;
            c = c.getParent();
        }
        return true;
    }

    private Field findField(Class<?> clazz, String name) {
        while (clazz != null) {
            try {
                return clazz.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private <T> T getField(Class<?> clazz, String name, Class<T> type) {
        try {
            Field field = findField(clazz, name);
            if (field == null) return null;
            field.setAccessible(true);
            Object val = field.get(dialog);
            if (type.isInstance(val)) return (T) val;
        } catch (Exception ignored) {}
        return null;
    }

    private void speak(String text) {
        SpeechOutput speech = AccessibilityManager.getInstance().getSpeech();
        if (speech != null) speech.speak(text);
    }
}
