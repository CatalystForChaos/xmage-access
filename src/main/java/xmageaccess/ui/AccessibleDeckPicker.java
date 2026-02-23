package xmageaccess.ui;

import xmageaccess.AccessibilityManager;
import xmageaccess.speech.SpeechOutput;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Accessible deck file picker that replaces the inaccessible JFileChooser.
 * Scans the sample-decks directory and presents decks via speech with
 * keyboard navigation.
 *
 * Usage: Call show() to display the picker. When a deck is selected,
 * the path is set in the target text field.
 *
 * Navigation:
 *   Up/Down - browse folders at current level
 *   Enter   - open folder or select deck
 *   Backspace - go up one folder
 *   Escape  - cancel
 */
public class AccessibleDeckPicker {

    private final JTextField targetField;
    private File currentDir;
    private List<File> currentEntries = new ArrayList<>();
    private int currentIndex = -1;
    private boolean active = false;
    private KeyEventDispatcher dispatcher;

    public AccessibleDeckPicker(JTextField targetField, File startDir) {
        this.targetField = targetField;
        this.currentDir = startDir;
    }

    public void show() {
        active = true;
        loadDirectory(currentDir);

        dispatcher = e -> {
            if (!active) return false;
            if (e.getID() != KeyEvent.KEY_PRESSED) return false;

            switch (e.getKeyCode()) {
                case KeyEvent.VK_DOWN:
                    navigate(1);
                    return true;
                case KeyEvent.VK_UP:
                    navigate(-1);
                    return true;
                case KeyEvent.VK_ENTER:
                    select();
                    return true;
                case KeyEvent.VK_BACK_SPACE:
                    goUp();
                    return true;
                case KeyEvent.VK_ESCAPE:
                    cancel();
                    return true;
            }
            return false;
        };

        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addKeyEventDispatcher(dispatcher);
    }

    private void loadDirectory(File dir) {
        currentDir = dir;
        currentEntries.clear();
        currentIndex = -1;

        File[] files = dir.listFiles();
        if (files == null) {
            speak("Empty folder.");
            return;
        }

        // Sort: folders first, then .dck files, alphabetically
        Arrays.sort(files, (a, b) -> {
            if (a.isDirectory() && !b.isDirectory()) return -1;
            if (!a.isDirectory() && b.isDirectory()) return 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });

        for (File f : files) {
            if (f.isDirectory() || f.getName().toLowerCase().endsWith(".dck")) {
                currentEntries.add(f);
            }
        }

        // Count folders and decks
        int folders = 0;
        int decks = 0;
        for (File f : currentEntries) {
            if (f.isDirectory()) folders++;
            else decks++;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Deck picker. Folder: ").append(dir.getName()).append(". ");
        if (folders > 0) sb.append(folders).append(" folders. ");
        if (decks > 0) sb.append(decks).append(" decks. ");
        sb.append("Up and Down to browse, Enter to open or select, Backspace to go back, Escape to cancel.");
        speak(sb.toString());
    }

    private void navigate(int direction) {
        if (currentEntries.isEmpty()) {
            speak("Empty folder.");
            return;
        }

        currentIndex += direction;
        if (currentIndex < 0) {
            currentIndex = 0;
            speak("At the beginning.");
            return;
        }
        if (currentIndex >= currentEntries.size()) {
            currentIndex = currentEntries.size() - 1;
            speak("At the end.");
            return;
        }

        File entry = currentEntries.get(currentIndex);
        String name = entry.getName();
        if (name.toLowerCase().endsWith(".dck")) {
            name = name.substring(0, name.length() - 4); // Remove .dck extension
        }

        StringBuilder sb = new StringBuilder();
        sb.append(currentIndex + 1).append(" of ").append(currentEntries.size()).append(". ");
        if (entry.isDirectory()) {
            sb.append("Folder: ").append(name);
        } else {
            sb.append(name);
        }
        speak(sb.toString());
    }

    private void select() {
        if (currentIndex < 0 || currentIndex >= currentEntries.size()) {
            speak("Nothing selected. Use Up and Down to browse.");
            return;
        }

        File entry = currentEntries.get(currentIndex);
        if (entry.isDirectory()) {
            loadDirectory(entry);
        } else {
            // Deck file selected
            String name = entry.getName();
            if (name.toLowerCase().endsWith(".dck")) {
                name = name.substring(0, name.length() - 4);
            }
            speak("Selected deck: " + name);

            // Set the path in the target text field
            if (targetField != null) {
                targetField.setText(entry.getAbsolutePath());
            }

            close();
        }
    }

    private void goUp() {
        File parent = currentDir.getParentFile();
        if (parent != null && parent.exists()) {
            loadDirectory(parent);
        } else {
            speak("Already at the top level.");
        }
    }

    private void cancel() {
        speak("Deck picker cancelled.");
        close();
    }

    private void close() {
        active = false;
        if (dispatcher != null) {
            KeyboardFocusManager.getCurrentKeyboardFocusManager()
                    .removeKeyEventDispatcher(dispatcher);
            dispatcher = null;
        }
    }

    public boolean isActive() {
        return active;
    }

    private void speak(String text) {
        SpeechOutput speech = AccessibilityManager.getInstance().getSpeech();
        if (speech != null) {
            speech.speak(text);
        }
    }
}
