package xmageaccess.ui;

import xmageaccess.AccessibilityManager;
import xmageaccess.speech.SpeechOutput;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Accessibility handler for the XMage PickCheckBoxDialog.
 * Used for multi-select choices (choose multiple cards/options).
 *
 * Keyboard shortcuts:
 *   Ctrl+Up/Down   - Navigate items
 *   Ctrl+Space     - Toggle checkbox on current item
 *   Ctrl+Enter     - Confirm selection
 *   Ctrl+R         - Re-read message and selected items
 *   Escape         - Cancel (if allowed)
 */
public class PickCheckBoxDialogHandler {

    private final Component dialog;
    private JList<?> listChoices;
    private JButton btOK;
    private JButton btCancel;
    private JButton btClear;
    private JTextField editSearch;
    private KeyEventDispatcher keyDispatcher;

    public PickCheckBoxDialogHandler(Component dialog) {
        this.dialog = dialog;
    }

    public void attach() {
        try {
            discoverComponents();
            addKeyboardShortcuts();
            announceDialog();
        } catch (Exception e) {
            System.err.println("[XMage Access] Error attaching to PickCheckBoxDialog: " + e.getMessage());
        }
    }

    public void detach() {
        if (keyDispatcher != null) {
            KeyboardFocusManager.getCurrentKeyboardFocusManager()
                    .removeKeyEventDispatcher(keyDispatcher);
            keyDispatcher = null;
        }
    }

    private void discoverComponents() {
        Class<?> clazz = dialog.getClass();
        listChoices = getField(clazz, "listChoices", JList.class);
        btOK = getField(clazz, "btOK", JButton.class);
        btCancel = getField(clazz, "btCancel", JButton.class);
        btClear = getField(clazz, "btClear", JButton.class);
        editSearch = getField(clazz, "editSearch", JTextField.class);
    }

    private void announceDialog() {
        StringBuilder sb = new StringBuilder("Multi-select. ");

        // Read message
        String message = readLabel("labelMessage");
        if (message != null && !message.isEmpty()) {
            sb.append(cleanHtml(message)).append(". ");
        }

        String subMessage = readLabel("labelSubMessage");
        if (subMessage != null && !subMessage.isEmpty()) {
            sb.append(cleanHtml(subMessage)).append(". ");
        }

        if (listChoices != null) {
            int count = listChoices.getModel().getSize();
            sb.append(count).append(" items. ");

            // Count checked items
            int checked = countChecked();
            if (checked > 0) {
                sb.append(checked).append(" selected. ");
            }

            // Announce first item
            int sel = listChoices.getSelectedIndex();
            if (sel < 0 && count > 0) {
                listChoices.setSelectedIndex(0);
                sel = 0;
            }
            if (sel >= 0) {
                sb.append("Current: ").append(getItemText(sel)).append(". ");
            }
        }

        sb.append("Ctrl+Up, Down to navigate. Ctrl+Space to toggle. Ctrl+Enter to confirm.");
        speak(sb.toString());
    }

    private void addKeyboardShortcuts() {
        keyDispatcher = e -> {
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
                            case KeyEvent.VK_SPACE:
                                toggleItem();
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
                };
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addKeyEventDispatcher(keyDispatcher);
    }

    private void navigateItem(int direction) {
        if (listChoices == null) return;

        int size = listChoices.getModel().getSize();
        if (size == 0) {
            speak("No items.");
            return;
        }

        int current = listChoices.getSelectedIndex();
        int next = current + direction;
        if (next < 0) next = 0;
        if (next >= size) next = size - 1;

        listChoices.setSelectedIndex(next);
        listChoices.ensureIndexIsVisible(next);

        String text = getItemText(next);
        boolean checked = isItemChecked(next);
        speak((next + 1) + " of " + size + ". " + (checked ? "checked. " : "") + text);
    }

    private void toggleItem() {
        if (listChoices == null) return;

        int sel = listChoices.getSelectedIndex();
        if (sel < 0) {
            speak("No item selected.");
            return;
        }

        // The CheckBoxList toggles on click, simulate it
        try {
            // Get the tList (CheckBoxList) from the dialog
            Object tList = getField(dialog.getClass(), "tList", Object.class);
            if (tList != null) {
                // CheckBoxList has toggleCheckbox or we can simulate via model
                Method toggleMethod = null;
                try {
                    toggleMethod = tList.getClass().getMethod("toggleCheckbox", int.class);
                } catch (NoSuchMethodException ignored) {}

                if (toggleMethod != null) {
                    toggleMethod.invoke(tList, sel);
                } else {
                    // Try getting the model and toggling directly
                    Object model = callMethod(tList, "getModel");
                    if (model != null) {
                        Object item = callMethodWithArg(model, "getElementAt", int.class, sel);
                        if (item != null) {
                            // CheckBoxListItem has isSelected/setSelected
                            Method isSelected = item.getClass().getMethod("isSelected");
                            Method setSelected = item.getClass().getMethod("setSelected", boolean.class);
                            boolean current = (Boolean) isSelected.invoke(item);
                            setSelected.invoke(item, !current);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Fallback: try clicking the list cell
            try {
                Rectangle cellBounds = listChoices.getCellBounds(sel, sel);
                if (cellBounds != null) {
                    java.awt.event.MouseEvent clickEvent = new java.awt.event.MouseEvent(
                            listChoices, java.awt.event.MouseEvent.MOUSE_CLICKED,
                            System.currentTimeMillis(), 0,
                            cellBounds.x + 5, cellBounds.y + cellBounds.height / 2,
                            1, false);
                    listChoices.dispatchEvent(clickEvent);
                }
            } catch (Exception ignored) {}
        }

        // Re-read the item state
        boolean checked = isItemChecked(sel);
        String text = getItemText(sel);
        speak((checked ? "Checked" : "Unchecked") + ": " + text);
    }

    private void confirmSelection() {
        if (btOK != null && btOK.isEnabled()) {
            int checked = countChecked();
            speak("Confirming " + checked + " selected.");
            btOK.doClick();
        } else {
            speak("Cannot confirm.");
        }
    }

    private String getItemText(int index) {
        if (listChoices == null || index < 0 || index >= listChoices.getModel().getSize()) {
            return "Unknown";
        }
        Object item = listChoices.getModel().getElementAt(index);
        if (item == null) return "Unknown";

        // Try to get plain text value via reflection (KeyValueItem.Value or .value)
        try {
            Field valueField = item.getClass().getDeclaredField("Value");
            valueField.setAccessible(true);
            String val = (String) valueField.get(item);
            if (val != null && !val.isEmpty()) return val;
        } catch (Exception ignored) {}

        try {
            Field valueField = item.getClass().getDeclaredField("value");
            valueField.setAccessible(true);
            String val = (String) valueField.get(item);
            if (val != null && !val.isEmpty()) return val;
        } catch (Exception ignored) {}

        String text = item.toString();
        return text.replaceAll("<[^>]*>", "").trim();
    }

    private boolean isItemChecked(int index) {
        try {
            Object tList = getField(dialog.getClass(), "tList", Object.class);
            if (tList != null) {
                Object model = callMethod(tList, "getModel");
                if (model != null) {
                    Object item = callMethodWithArg(model, "getElementAt", int.class, index);
                    if (item != null) {
                        Method isSelected = item.getClass().getMethod("isSelected");
                        return (Boolean) isSelected.invoke(item);
                    }
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    private int countChecked() {
        int count = 0;
        if (listChoices == null) return 0;
        int size = listChoices.getModel().getSize();
        for (int i = 0; i < size; i++) {
            if (isItemChecked(i)) count++;
        }
        return count;
    }

    private Object callMethod(Object obj, String name) {
        try {
            Method m = obj.getClass().getMethod(name);
            return m.invoke(obj);
        } catch (Exception ignored) {}
        return null;
    }

    private Object callMethodWithArg(Object obj, String name, Class<?> argType, Object arg) {
        try {
            Method m = obj.getClass().getMethod(name, argType);
            return m.invoke(obj, arg);
        } catch (Exception ignored) {}
        return null;
    }

    private String readLabel(String fieldName) {
        try {
            Field field = findField(dialog.getClass(), fieldName);
            if (field == null) return null;
            field.setAccessible(true);
            Object label = field.get(dialog);
            if (label instanceof JLabel) return ((JLabel) label).getText();
        } catch (Exception ignored) {}
        return null;
    }

    private String cleanHtml(String text) {
        if (text == null) return "";
        return text.replaceAll("<[^>]*>", "").replaceAll("\\s+", " ").trim();
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
