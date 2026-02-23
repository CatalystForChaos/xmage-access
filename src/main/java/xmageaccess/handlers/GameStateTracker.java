package xmageaccess.handlers;

import xmageaccess.AccessibilityManager;
import xmageaccess.speech.SpeechOutput;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tracks game state changes and generates screen reader announcements.
 * Uses reflection to access XMage's GameView and PlayerView classes
 * since they aren't on the agent's compile-time classpath.
 */
public class GameStateTracker {

    private static final GameStateTracker INSTANCE = new GameStateTracker();

    private String lastStep = "";
    private String lastActivePlayer = "";
    private String lastPriorityPlayer = "";
    private int lastTurn = -1;
    private final Map<String, Integer> lastLifeTotals = new HashMap<>();
    private boolean gameActive = false;

    private GameStateTracker() {
    }

    public static GameStateTracker getInstance() {
        return INSTANCE;
    }

    /**
     * Called when a game is initialized.
     */
    public void onGameInit(Object gameView) {
        gameActive = true;
        lastTurn = -1;
        lastStep = "";
        lastActivePlayer = "";
        lastPriorityPlayer = "";
        lastLifeTotals.clear();

        speak("Game started.");
        onGameUpdate(gameView);
    }

    /**
     * Called when game state is updated. Detects changes and announces them.
     */
    public void onGameUpdate(Object gameView) {
        if (gameView == null || !gameActive) return;

        try {
            announceStateChanges(gameView);
        } catch (Exception e) {
            System.err.println("[XMage Access] Error tracking game state: " + e.getMessage());
        }
    }

    /**
     * Called when the game asks the player a question.
     */
    public void onQuestion(String question) {
        if (question != null && !question.isEmpty()) {
            speak(question);
        }
    }

    /**
     * Called when the game shows an information message.
     */
    public void onInform(String information) {
        if (information != null && !information.isEmpty()) {
            speak(information);
        }
    }

    /**
     * Called when the player gets priority and must make a selection.
     */
    public void onSelect(Object gameView, String message) {
        if (message != null && !message.isEmpty()) {
            speak(message);
        }
    }

    /**
     * Called when the game ends.
     */
    public void onGameEnd(String message) {
        gameActive = false;
        if (message != null && !message.isEmpty()) {
            speak(message);
        }
    }

    private void announceStateChanges(Object gameView) throws Exception {
        StringBuilder announcement = new StringBuilder();

        // Extract turn number
        int turn = callIntMethod(gameView, "getTurn");
        if (turn != lastTurn && turn > 0) {
            lastTurn = turn;
            announcement.append("Turn ").append(turn).append(". ");
        }

        // Extract active player
        String activePlayer = callStringMethod(gameView, "getActivePlayerName");
        if (activePlayer != null && !activePlayer.equals(lastActivePlayer)) {
            lastActivePlayer = activePlayer;
            announcement.append(activePlayer).append("'s turn. ");
        }

        // Extract step
        Object stepObj = callMethod(gameView, "getStep");
        String step = stepObj != null ? stepObj.toString() : "";
        if (!step.isEmpty() && !step.equals(lastStep)) {
            lastStep = step;
            announcement.append(step).append(". ");
        }

        // Extract priority player
        String priorityPlayer = callStringMethod(gameView, "getPriorityPlayerName");
        if (priorityPlayer != null && !priorityPlayer.equals(lastPriorityPlayer)) {
            lastPriorityPlayer = priorityPlayer;
            // Only announce priority changes when it's not the active player
            // (to reduce verbosity - active player normally has priority)
            if (!priorityPlayer.equals(lastActivePlayer)) {
                announcement.append("Priority: ").append(priorityPlayer).append(". ");
            }
        }

        // Extract life totals from players
        Object playersList = callMethod(gameView, "getPlayers");
        if (playersList instanceof List) {
            for (Object player : (List<?>) playersList) {
                String name = callStringMethod(player, "getName");
                int life = callIntMethod(player, "getLife");

                if (name != null) {
                    Integer previousLife = lastLifeTotals.get(name);
                    if (previousLife != null && previousLife != life) {
                        int change = life - previousLife;
                        String changeText = change > 0 ? "+" + change : String.valueOf(change);
                        announcement.append(name).append(" life: ")
                                .append(life).append(" (").append(changeText).append("). ");
                    }
                    lastLifeTotals.put(name, life);
                }
            }
        }

        // Speak if there's anything to announce
        String text = announcement.toString().trim();
        if (!text.isEmpty()) {
            speak(text);
        }
    }

    private void speak(String text) {
        SpeechOutput speech = AccessibilityManager.getInstance().getSpeech();
        if (speech != null) {
            speech.speak(text);
        }
    }

    // Reflection helpers to call methods on XMage classes

    private static Object callMethod(Object obj, String methodName) throws Exception {
        Method method = obj.getClass().getMethod(methodName);
        return method.invoke(obj);
    }

    private static String callStringMethod(Object obj, String methodName) {
        try {
            Object result = callMethod(obj, methodName);
            return result != null ? result.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static int callIntMethod(Object obj, String methodName) {
        try {
            Object result = callMethod(obj, methodName);
            if (result instanceof Number) {
                return ((Number) result).intValue();
            }
        } catch (Exception e) {
            // Ignore
        }
        return 0;
    }
}
