package xmageaccess.ui;

import xmageaccess.AccessibilityManager;
import xmageaccess.speech.SpeechOutput;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Modal, screen-reader-friendly single-select list dialog.
 * The whole interaction happens on one list widget:
 *
 *   Up/Down (and PageUp/PageDown, Home/End)  browse — every entry is spoken
 *   any letter/digit                          narrows the list (type-to-filter)
 *   Backspace                                 removes the last filter character
 *   Enter                                     picks the selected entry
 *   Escape                                    cancels
 *
 * The chosen entry is delivered to the callback after the dialog closes.
 */
public class AccessibleListPicker extends JDialog {

    private final List<String> allItems;
    private final String itemNoun;
    private final Consumer<String> onPick;

    private final DefaultListModel<String> model = new DefaultListModel<>();
    private final JList<String> list;
    private final StringBuilder filter = new StringBuilder();
    private boolean suppressSpeech = false;

    public AccessibleListPicker(Window owner, String title, String itemNoun,
                                List<String> items, Consumer<String> onPick) {
        super(owner, title, ModalityType.APPLICATION_MODAL);
        this.allItems = new ArrayList<>(items);
        this.itemNoun = itemNoun;
        this.onPick = onPick;

        // Swallow KEY_TYPED before BasicListUI's own type-ahead sees it,
        // and use the typed characters for our spoken filter instead.
        list = new JList<String>(model) {
            @Override
            protected void processKeyEvent(KeyEvent e) {
                if (e.getID() == KeyEvent.KEY_TYPED) {
                    char c = e.getKeyChar();
                    if (!Character.isISOControl(c)) {
                        filter.append(c);
                        applyFilter();
                    }
                    e.consume();
                    return;
                }
                super.processKeyEvent(e);
            }
        };
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.getAccessibleContext().setAccessibleName(title);

        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !suppressSpeech) {
                int idx = list.getSelectedIndex();
                if (idx >= 0) {
                    speak((idx + 1) + " of " + model.getSize() + ": " + list.getSelectedValue());
                }
            }
        });

        list.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    e.consume();
                    choose();
                } else if (e.getKeyCode() == KeyEvent.VK_BACK_SPACE) {
                    e.consume();
                    if (filter.length() > 0) {
                        filter.setLength(filter.length() - 1);
                        applyFilter();
                    } else {
                        speak("Filter is empty.");
                    }
                }
            }
        });

        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancelPicker");
        getRootPane().getActionMap().put("cancelPicker", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                speak("Cancelled.");
                dispose();
            }
        });

        add(new JScrollPane(list), BorderLayout.CENTER);
        setSize(new Dimension(420, 520));
        setLocationRelativeTo(owner);

        suppressSpeech = true;
        for (String item : allItems) {
            model.addElement(item);
        }
        if (model.getSize() > 0) {
            list.setSelectedIndex(0);
        }
        suppressSpeech = false;

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                list.requestFocusInWindow();
            }
        });
    }

    /** Shows the dialog (blocks until picked or cancelled). */
    public void showPicker() {
        speak(allItems.size() + " " + itemNoun + ". Up and down to browse, "
                + "type letters to filter, Enter to choose, Escape to cancel."
                + (model.getSize() > 0 ? " First: " + model.get(0) + "." : ""));
        setVisible(true);
    }

    private void applyFilter() {
        String needle = filter.toString().trim().toLowerCase();
        suppressSpeech = true;
        model.clear();
        for (String item : allItems) {
            if (needle.isEmpty() || item.toLowerCase().contains(needle)) {
                model.addElement(item);
            }
        }
        if (model.getSize() > 0) {
            list.setSelectedIndex(0);
            list.ensureIndexIsVisible(0);
        }
        suppressSpeech = false;

        if (needle.isEmpty()) {
            speak("Filter cleared. " + model.getSize() + " " + itemNoun + "."
                    + (model.getSize() > 0 ? " First: " + model.get(0) + "." : ""));
        } else if (model.getSize() == 0) {
            speak("No matches for " + spellOut(needle) + ".");
        } else {
            speak("Filter " + spellOut(needle) + ". " + model.getSize() + " matches."
                    + " First: " + model.get(0) + ".");
        }
    }

    private void choose() {
        String value = list.getSelectedValue();
        if (value == null) {
            speak("Nothing selected.");
            return;
        }
        dispose();
        if (onPick != null) {
            onPick.accept(value);
        }
    }

    /** Short filters are spelled out so single letters are intelligible. */
    private static String spellOut(String s) {
        if (s.length() > 3) return s;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (i > 0) sb.append(' ');
            sb.append(s.charAt(i));
        }
        return sb.toString();
    }

    private void speak(String text) {
        SpeechOutput speech = AccessibilityManager.getInstance().getSpeech();
        if (speech != null) {
            speech.speak(text);
        }
    }
}
