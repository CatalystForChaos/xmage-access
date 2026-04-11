package xmageaccess.hooks;

import java.lang.reflect.Method;

/**
 * Caches reflection lookups for GamePanelHooks advice classes.
 * ByteBuddy advice is inlined into the target class, so it cannot
 * hold static state directly — this helper class provides the cache.
 */
public class GameStateTrackerBridge {

    private static volatile Class<?> trackerClass;
    private static volatile Method getInstanceMethod;
    private static volatile Method onGameInitMethod;
    private static volatile Method onGameUpdateMethod;
    private static volatile Method onQuestionMethod;
    private static volatile Method onInformMethod;
    private static volatile Method onSelectMethod;
    private static volatile Method onGameEndMethod;

    private static Class<?> getTrackerClass() throws ClassNotFoundException {
        Class<?> c = trackerClass;
        if (c == null) {
            c = Class.forName("xmageaccess.handlers.GameStateTracker");
            trackerClass = c;
        }
        return c;
    }

    private static Object getTracker() throws Exception {
        Class<?> c = getTrackerClass();
        Method m = getInstanceMethod;
        if (m == null) {
            m = c.getMethod("getInstance");
            getInstanceMethod = m;
        }
        return m.invoke(null);
    }

    public static void onGameInit(Object gameView) {
        try {
            Object tracker = getTracker();
            Method m = onGameInitMethod;
            if (m == null) {
                m = getTrackerClass().getMethod("onGameInit", Object.class);
                onGameInitMethod = m;
            }
            m.invoke(tracker, gameView);
        } catch (Exception e) {
            System.err.println("[XMage Access] Hook error (init): " + e.getMessage());
        }
    }

    public static void onGameUpdate(Object gameView) {
        try {
            Object tracker = getTracker();
            Method m = onGameUpdateMethod;
            if (m == null) {
                m = getTrackerClass().getMethod("onGameUpdate", Object.class);
                onGameUpdateMethod = m;
            }
            m.invoke(tracker, gameView);
        } catch (Exception e) {
            System.err.println("[XMage Access] Hook error (updateGame): " + e.getMessage());
        }
    }

    public static void onQuestion(String question) {
        try {
            Object tracker = getTracker();
            Method m = onQuestionMethod;
            if (m == null) {
                m = getTrackerClass().getMethod("onQuestion", String.class);
                onQuestionMethod = m;
            }
            m.invoke(tracker, question);
        } catch (Exception e) {
            System.err.println("[XMage Access] Hook error (ask): " + e.getMessage());
        }
    }

    public static void onInform(String information) {
        try {
            Object tracker = getTracker();
            Method m = onInformMethod;
            if (m == null) {
                m = getTrackerClass().getMethod("onInform", String.class);
                onInformMethod = m;
            }
            m.invoke(tracker, information);
        } catch (Exception e) {
            System.err.println("[XMage Access] Hook error (inform): " + e.getMessage());
        }
    }

    public static void onSelect(Object gameView, String message) {
        try {
            Object tracker = getTracker();
            Method m = onSelectMethod;
            if (m == null) {
                m = getTrackerClass().getMethod("onSelect", Object.class, String.class);
                onSelectMethod = m;
            }
            m.invoke(tracker, gameView, message);
        } catch (Exception e) {
            System.err.println("[XMage Access] Hook error (select): " + e.getMessage());
        }
    }

    public static void onGameEnd(String message) {
        try {
            Object tracker = getTracker();
            Method m = onGameEndMethod;
            if (m == null) {
                m = getTrackerClass().getMethod("onGameEnd", String.class);
                onGameEndMethod = m;
            }
            m.invoke(tracker, message);
        } catch (Exception e) {
            System.err.println("[XMage Access] Hook error (endMessage): " + e.getMessage());
        }
    }
}
