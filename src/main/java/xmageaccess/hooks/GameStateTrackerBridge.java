package xmageaccess.hooks;

import java.lang.reflect.Method;

/**
 * Caches reflection lookups for GamePanelHooks advice classes.
 * ByteBuddy advice is inlined into the target class, so it cannot
 * hold static state directly — this helper class provides the cache.
 *
 * <p>All cached references are resolved once inside a holder class's
 * static initializer, which the JVM guarantees runs exactly once and
 * is visible to all threads — no synchronization or double-checked
 * locking needed.
 */
public class GameStateTrackerBridge {

    private static final class Holder {
        static final Class<?> trackerClass;
        static final Method getInstance, onGameInit, onGameUpdate, onQuestion, onInform, onSelect, onGameEnd;

        static {
            Class<?> c = null;
            Method gi = null, oi = null, ou = null, oq = null, oin = null, os = null, oe = null;
            try {
                c = Class.forName("xmageaccess.handlers.GameStateTracker");
                gi = c.getMethod("getInstance");
                oi = c.getMethod("onGameInit", Object.class);
                ou = c.getMethod("onGameUpdate", Object.class);
                oq = c.getMethod("onQuestion", String.class);
                oin = c.getMethod("onInform", String.class);
                os = c.getMethod("onSelect", Object.class, String.class);
                oe = c.getMethod("onGameEnd", String.class);
            } catch (Throwable t) {
                System.err.println("[XMage Access] Bridge init failed: " + t.getMessage());
            }
            trackerClass = c;
            getInstance = gi;
            onGameInit = oi;
            onGameUpdate = ou;
            onQuestion = oq;
            onInform = oin;
            onSelect = os;
            onGameEnd = oe;
        }
    }

    private static Object tracker() throws Exception {
        if (Holder.getInstance == null) return null;
        return Holder.getInstance.invoke(null);
    }

    public static void onGameInit(Object gameView) {
        try {
            Object t = tracker();
            if (t != null && Holder.onGameInit != null) Holder.onGameInit.invoke(t, gameView);
        } catch (Exception e) {
            System.err.println("[XMage Access] Hook error (init): " + e.getMessage());
        }
    }

    public static void onGameUpdate(Object gameView) {
        try {
            Object t = tracker();
            if (t != null && Holder.onGameUpdate != null) Holder.onGameUpdate.invoke(t, gameView);
        } catch (Exception e) {
            System.err.println("[XMage Access] Hook error (updateGame): " + e.getMessage());
        }
    }

    public static void onQuestion(String question) {
        try {
            Object t = tracker();
            if (t != null && Holder.onQuestion != null) Holder.onQuestion.invoke(t, question);
        } catch (Exception e) {
            System.err.println("[XMage Access] Hook error (ask): " + e.getMessage());
        }
    }

    public static void onInform(String information) {
        try {
            Object t = tracker();
            if (t != null && Holder.onInform != null) Holder.onInform.invoke(t, information);
        } catch (Exception e) {
            System.err.println("[XMage Access] Hook error (inform): " + e.getMessage());
        }
    }

    public static void onSelect(Object gameView, String message) {
        try {
            Object t = tracker();
            if (t != null && Holder.onSelect != null) Holder.onSelect.invoke(t, gameView, message);
        } catch (Exception e) {
            System.err.println("[XMage Access] Hook error (select): " + e.getMessage());
        }
    }

    public static void onGameEnd(String message) {
        try {
            Object t = tracker();
            if (t != null && Holder.onGameEnd != null) Holder.onGameEnd.invoke(t, message);
        } catch (Exception e) {
            System.err.println("[XMage Access] Hook error (endMessage): " + e.getMessage());
        }
    }
}
