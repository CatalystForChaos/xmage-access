package xmageaccess.hooks;

import net.bytebuddy.asm.Advice;

/**
 * ByteBuddy advice classes that hook into GamePanel methods.
 * Each inner class is an advice that gets inlined into the target method.
 * Delegates to GameStateTrackerBridge which caches reflection lookups.
 */
public class GamePanelHooks {

    /**
     * Hooks into GamePanel.init(int, GameView, boolean) - game initialization.
     */
    public static class InitAdvice {

        @Advice.OnMethodExit
        public static void afterInit(@Advice.Argument(1) Object gameView) {
            GameStateTrackerBridge.onGameInit(gameView);
        }
    }

    /**
     * Hooks into GamePanel.updateGame(int, GameView, boolean, Map, Set) - state updates.
     */
    public static class UpdateGameAdvice {

        @Advice.OnMethodExit
        public static void afterUpdateGame(@Advice.Argument(1) Object gameView) {
            GameStateTrackerBridge.onGameUpdate(gameView);
        }
    }

    /**
     * Hooks into GamePanel.ask(int, GameView, String, Map) - questions.
     */
    public static class AskAdvice {

        @Advice.OnMethodExit
        public static void afterAsk(@Advice.Argument(2) String question) {
            GameStateTrackerBridge.onQuestion(question);
        }
    }

    /**
     * Hooks into GamePanel.inform(int, GameView, String) - information.
     */
    public static class InformAdvice {

        @Advice.OnMethodExit
        public static void afterInform(@Advice.Argument(2) String information) {
            GameStateTrackerBridge.onInform(information);
        }
    }

    /**
     * Hooks into GamePanel.select(int, GameView, Map, String) - priority/selection.
     */
    public static class SelectAdvice {

        @Advice.OnMethodExit
        public static void afterSelect(@Advice.Argument(1) Object gameView,
                                       @Advice.Argument(3) String message) {
            GameStateTrackerBridge.onSelect(gameView, message);
        }
    }

    /**
     * Hooks into GamePanel.endMessage(int, GameView, Map, String) - game end.
     */
    public static class EndMessageAdvice {

        @Advice.OnMethodExit
        public static void afterEndMessage(@Advice.Argument(3) String message) {
            GameStateTrackerBridge.onGameEnd(message);
        }
    }
}
