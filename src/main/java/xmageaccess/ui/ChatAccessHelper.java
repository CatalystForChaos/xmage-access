package xmageaccess.ui;

import xmageaccess.AccessibilityManager;
import xmageaccess.speech.SpeechOutput;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Document;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Reusable chat accessibility helper that can be attached to any ChatPanelBasic.
 * Monitors for new messages and provides methods to read/send chat.
 *
 * Features:
 *   - Auto-announces new player messages (not game/status spam)
 *   - readRecentChat(n) reads last N messages
 *   - sendMessage(text) sends a chat message
 *   - focusInput() focuses the chat input field
 */
public class ChatAccessHelper {

    private final Object chatPanel; // ChatPanelBasic instance
    private JEditorPane txtConversation;
    private JTextField txtMessage;
    private UUID chatId;
    private String lastKnownText = "";
    private final List<String> messageHistory = new ArrayList<>();
    private static final int MAX_HISTORY = 50;
    private boolean monitoring = false;

    public ChatAccessHelper(Object chatPanel) {
        this.chatPanel = chatPanel;
    }

    /**
     * Discovers chat components and starts monitoring for new messages.
     * Returns true if successfully attached.
     */
    public boolean attach() {
        try {
            txtConversation = findFieldTyped(chatPanel, "txtConversation", JEditorPane.class);
            txtMessage = findFieldTyped(chatPanel, "txtMessage", JTextField.class);
            chatId = findFieldTyped(chatPanel, "chatId", UUID.class);

            if (txtConversation == null) {
                System.err.println("[XMage Access] Chat: txtConversation not found");
                return false;
            }

            // Capture initial text to avoid announcing old messages
            lastKnownText = getConversationText();

            // Monitor for new messages via document listener
            Document doc = txtConversation.getDocument();
            if (doc != null) {
                doc.addDocumentListener(new DocumentListener() {
                    @Override
                    public void insertUpdate(DocumentEvent e) {
                        onNewContent();
                    }
                    @Override
                    public void removeUpdate(DocumentEvent e) {}
                    @Override
                    public void changedUpdate(DocumentEvent e) {}
                });
                monitoring = true;
            }

            System.out.println("[XMage Access] Chat helper attached (chatId: " + chatId + ")");
            return true;
        } catch (Exception e) {
            System.err.println("[XMage Access] Chat attach error: " + e.getMessage());
            return false;
        }
    }

    public boolean isMonitoring() {
        return monitoring;
    }

    /**
     * Called when new content is added to the chat pane.
     * Extracts the new portion and announces player messages.
     */
    private void onNewContent() {
        try {
            String fullText = getConversationText();
            if (fullText == null || fullText.equals(lastKnownText)) return;

            // Extract only the new part
            String newPart = fullText;
            if (lastKnownText != null && fullText.startsWith(lastKnownText)) {
                newPart = fullText.substring(lastKnownText.length()).trim();
            }
            lastKnownText = fullText;

            if (newPart.isEmpty()) return;

            // Split into lines and process each
            String[] lines = newPart.split("\n");
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty()) continue;

                // Store in history
                if (messageHistory.size() >= MAX_HISTORY) {
                    messageHistory.remove(0);
                }
                messageHistory.add(line);

                // Announce player chat messages (contains ": " from "username: message")
                // Skip game log lines that are typically status/action messages
                if (isPlayerMessage(line)) {
                    speak(line);
                }
            }
        } catch (Exception e) {
            // Don't crash on monitoring errors
        }
    }

    /**
     * Heuristic to detect player chat vs. game/system messages.
     * Player messages have format "HH:MM username: message"
     */
    private boolean isPlayerMessage(String line) {
        // Player messages typically start with a timestamp and contain ": "
        // Game actions tend to be longer status-like messages
        // Simple heuristic: if line contains a colon-space after a short word, it's chat
        if (line.contains(": ")) {
            // Check it's not a common game log pattern
            String lower = line.toLowerCase();
            if (lower.contains("casts ") || lower.contains("draws ")
                    || lower.contains("plays ") || lower.contains("attacks ")
                    || lower.contains("blocks ") || lower.contains("activates ")
                    || lower.contains("triggers ") || lower.contains("puts ")
                    || lower.contains("sacrifices ") || lower.contains("destroys ")
                    || lower.contains("exiles ") || lower.contains("counters ")
                    || lower.contains("discards ") || lower.contains("taps ")
                    || lower.contains("untaps ") || lower.contains("turn ")
                    || lower.contains("is the")
                    || lower.contains("concedes") || lower.contains("wins")
                    || lower.contains("loses") || lower.contains("has joined")
                    || lower.contains("has left") || lower.contains("connected")
                    || lower.contains("disconnected")) {
                return false;
            }
            return true;
        }
        return false;
    }

    /**
     * Returns the last N lines from message history for display in a window.
     */
    public List<String> getRecentLines(int count) {
        if (messageHistory.isEmpty()) return new ArrayList<>();
        int start = Math.max(0, messageHistory.size() - count);
        return new ArrayList<>(messageHistory.subList(start, messageHistory.size()));
    }

    /**
     * Reads the last N chat messages aloud.
     */
    public void readRecentChat(int count) {
        if (messageHistory.isEmpty()) {
            speak("No chat messages.");
            return;
        }

        int start = Math.max(0, messageHistory.size() - count);
        StringBuilder sb = new StringBuilder();
        sb.append("Chat. ");
        for (int i = start; i < messageHistory.size(); i++) {
            sb.append(messageHistory.get(i)).append(". ");
        }
        speak(sb.toString());
    }

    /**
     * Sends a chat message.
     */
    public void sendMessage(String text) {
        if (text == null || text.isEmpty()) return;

        try {
            // Use SessionHandler.sendChatMessage(chatId, text)
            UUID sendChatId = chatId;

            // Check if this panel has a parentChatRef (used for connected/redirected panels)
            UUID parentId = getParentChatId();
            if (parentId != null) {
                sendChatId = parentId;
            }

            if (sendChatId != null) {
                Class<?> sessionHandler = Class.forName("mage.client.SessionHandler");
                Method sendChat = sessionHandler.getMethod("sendChatMessage", UUID.class, String.class);
                sendChat.invoke(null, sendChatId, text);
                speak("Sent: " + text);
            } else {
                speak("Chat not connected.");
            }
        } catch (Exception e) {
            speak("Failed to send message.");
            System.err.println("[XMage Access] Chat send error: " + e.getMessage());
        }
    }

    /**
     * Focuses the chat input field so the user can type.
     */
    public void focusInput() {
        if (txtMessage != null) {
            txtMessage.requestFocusInWindow();
            speak("Chat input focused. Type message and press Enter to send. Escape to leave.");
        } else {
            speak("Chat input not available.");
        }
    }

    private UUID getParentChatId() {
        try {
            Object parentRef = findFieldDeep(chatPanel, "parentChatRef");
            if (parentRef != null) {
                return findFieldTyped(parentRef, "chatId", UUID.class);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String getConversationText() {
        if (txtConversation == null) return "";
        try {
            // Get plain text content, stripping HTML
            String text = txtConversation.getText();
            if (text == null) return "";
            // Strip HTML tags
            text = text.replaceAll("<[^>]*>", "");
            text = text.replaceAll("&nbsp;", " ");
            text = text.replaceAll("&amp;", "&");
            text = text.replaceAll("&lt;", "<");
            text = text.replaceAll("&gt;", ">");
            text = text.replaceAll("\\s+", " ").trim();
            return text;
        } catch (Exception e) {
            return "";
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T findFieldTyped(Object obj, String name, Class<T> type) {
        try {
            Class<?> clazz = obj.getClass();
            while (clazz != null) {
                try {
                    Field field = clazz.getDeclaredField(name);
                    field.setAccessible(true);
                    Object val = field.get(obj);
                    if (type.isInstance(val)) return (T) val;
                    return null;
                } catch (NoSuchFieldException e) {
                    clazz = clazz.getSuperclass();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private Object findFieldDeep(Object obj, String name) {
        try {
            Class<?> clazz = obj.getClass();
            while (clazz != null) {
                try {
                    Field field = clazz.getDeclaredField(name);
                    field.setAccessible(true);
                    return field.get(obj);
                } catch (NoSuchFieldException e) {
                    clazz = clazz.getSuperclass();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void speak(String text) {
        SpeechOutput speech = AccessibilityManager.getInstance().getSpeech();
        if (speech != null) speech.speak(text);
    }
}
