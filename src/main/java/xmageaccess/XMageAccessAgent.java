package xmageaccess;

import java.lang.instrument.Instrumentation;

/**
 * Java Agent entry point. Loaded via -javaagent:xmage-access.jar before
 * the XMage client starts. Sets up accessibility hooks.
 */
public class XMageAccessAgent {

    private static Instrumentation instrumentation;

    public static void premain(String agentArgs, Instrumentation inst) {
        instrumentation = inst;
        System.out.println("[XMage Access] Agent loading...");

        AccessibilityManager manager = AccessibilityManager.getInstance();
        manager.initialize(inst);

        System.out.println("[XMage Access] Agent loaded successfully.");
    }

    public static Instrumentation getInstrumentation() {
        return instrumentation;
    }
}
