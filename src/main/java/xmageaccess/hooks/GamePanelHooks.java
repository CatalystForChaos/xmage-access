package xmageaccess.hooks;

import net.bytebuddy.asm.Advice;

/**
 * ByteBuddy advice classes that hook into GamePanel methods.
 * Each inner class is an advice that gets inlined into the target method.
 */
public class GamePanelHooks {

    /**
     * Hooks into GamePanel.init(int, GameView, boolean) - game initialization.
     */
    public static class InitAdvice {

        @Advice.OnMethodExit
        public static void afterInit(@Advice.Argument(1) Object gameView) {
            try {
                Class<?> trackerClass = Class.forName("xmageaccess.handlers.GameStateTracker");
                Object tracker = trackerClass.getMethod("getInstance").invoke(null);
                trackerClass.getMethod("onGameInit", Object.class).invoke(tracker, gameView);
            } catch (Exception e) {
                System.err.println("[XMage Access] Hook error (init): " + e.getMessage());
            }
        }
    }

    /**
     * Hooks into GamePanel.updateGame(int, GameView, boolean, Map, Set) - state updates.
     */
    public static class UpdateGameAdvice {

        @Advice.OnMethodExit
        public static void afterUpdateGame(@Advice.Argument(1) Object gameView) {
            try {
                Class<?> trackerClass = Class.forName("xmageaccess.handlers.GameStateTracker");
                Object tracker = trackerClass.getMethod("getInstance").invoke(null);
                trackerClass.getMethod("onGameUpdate", Object.class).invoke(tracker, gameView);
            } catch (Exception e) {
                System.err.println("[XMage Access] Hook error (updateGame): " + e.getMessage());
            }
        }
    }

    /**
     * Hooks into GamePanel.ask(int, GameView, String, Map) - questions.
     */
    public static class AskAdvice {

        @Advice.OnMethodExit
        public static void afterAsk(@Advice.Argument(2) String question) {
            try {
                Class<?> trackerClass = Class.forName("xmageaccess.handlers.GameStateTracker");
                Object tracker = trackerClass.getMethod("getInstance").invoke(null);
                trackerClass.getMethod("onQuestion", String.class).invoke(tracker, question);
            } catch (Exception e) {
                System.err.println("[XMage Access] Hook error (ask): " + e.getMessage());
            }
        }
    }

    /**
     * Hooks into GamePanel.inform(int, GameView, String) - information.
     */
    public static class InformAdvice {

        @Advice.OnMethodExit
        public static void afterInform(@Advice.Argument(2) String information) {
            try {
                Class<?> trackerClass = Class.forName("xmageaccess.handlers.GameStateTracker");
                Object tracker = trackerClass.getMethod("getInstance").invoke(null);
                trackerClass.getMethod("onInform", String.class).invoke(tracker, information);
            } catch (Exception e) {
                System.err.println("[XMage Access] Hook error (inform): " + e.getMessage());
            }
        }
    }

    /**
     * Hooks into GamePanel.select(int, GameView, Map, String) - priority/selection.
     */
    public static class SelectAdvice {

        @Advice.OnMethodExit
        public static void afterSelect(@Advice.Argument(1) Object gameView,
                                       @Advice.Argument(3) String message) {
            try {
                Class<?> trackerClass = Class.forName("xmageaccess.handlers.GameStateTracker");
                Object tracker = trackerClass.getMethod("getInstance").invoke(null);
                trackerClass.getMethod("onSelect", Object.class, String.class)
                        .invoke(tracker, gameView, message);
            } catch (Exception e) {
                System.err.println("[XMage Access] Hook error (select): " + e.getMessage());
            }
        }
    }

    /**
     * Hooks into GamePanel.endMessage(int, GameView, Map, String) - game end.
     */
    public static class EndMessageAdvice {

        @Advice.OnMethodExit
        public static void afterEndMessage(@Advice.Argument(3) String message) {
            try {
                Class<?> trackerClass = Class.forName("xmageaccess.handlers.GameStateTracker");
                Object tracker = trackerClass.getMethod("getInstance").invoke(null);
                trackerClass.getMethod("onGameEnd", String.class).invoke(tracker, message);
            } catch (Exception e) {
                System.err.println("[XMage Access] Hook error (endMessage): " + e.getMessage());
            }
        }
    }
}
