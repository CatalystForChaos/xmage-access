package xmageaccess.speech;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Platform-detecting speech output. Routes text to the appropriate
 * screen reader or TTS engine based on the operating system.
 *
 * <p>All engine calls run on a single daemon background thread so the
 * Swing EDT (or any caller) never blocks on a TTS process. Rapidly
 * issued interrupt-speak calls coalesce: if a new {@link #speak(String)}
 * arrives while a previous one is still queued, the queued one is
 * cancelled and only the newest text is spoken. Identical text within
 * a 250 ms window is dropped as a duplicate.
 */
public class SpeechOutput {

    private static final long DEDUP_WINDOW_MS = 250;

    private SpeechEngine engine;
    private ScheduledExecutorService scheduler;
    private volatile ScheduledFuture<?> pendingInterrupt;
    private volatile String lastSpoken;
    private volatile long lastSpokenAt;

    public void initialize() {
        String os = System.getProperty("os.name", "").toLowerCase();

        if (os.contains("mac")) {
            engine = new MacOSSpeech();
            System.out.println("[XMage Access] Speech: macOS detected, using 'say' command.");
        } else if (os.contains("win")) {
            engine = new WindowsSpeech();
        } else {
            engine = new LinuxSpeech();
            System.out.println("[XMage Access] Speech: Linux detected, using speech-dispatcher.");
        }

        scheduler = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "XMageAccess-Speech");
                t.setDaemon(true);
                return t;
            }
        });
    }

    /**
     * Speak text, interrupting any currently scheduled (but not yet started)
     * interrupt-speak. If TTS is already in flight, the engine's own
     * silence-and-restart logic takes over.
     */
    public void speak(final String text) {
        if (engine == null || text == null || text.isEmpty()) return;
        if (isDuplicate(text)) return;
        lastSpoken = text;
        lastSpokenAt = System.currentTimeMillis();

        ScheduledFuture<?> prev = pendingInterrupt;
        if (prev != null) prev.cancel(false);

        ScheduledExecutorService s = scheduler;
        if (s == null) return;
        pendingInterrupt = s.schedule(new Runnable() {
            @Override
            public void run() {
                try {
                    engine.speak(text, true);
                } catch (Throwable t) {
                    System.err.println("[XMage Access] Speech error: " + t.getMessage());
                }
            }
        }, 0, TimeUnit.MILLISECONDS);
    }

    /**
     * Speak text without interrupting current speech. Queued announcements
     * don't coalesce — every distinct queued call is delivered to the engine
     * in order, subject only to the duplicate window.
     */
    public void speakQueued(final String text) {
        if (engine == null || text == null || text.isEmpty()) return;
        if (isDuplicate(text)) return;
        lastSpoken = text;
        lastSpokenAt = System.currentTimeMillis();

        ScheduledExecutorService s = scheduler;
        if (s == null) return;
        s.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    engine.speak(text, false);
                } catch (Throwable t) {
                    System.err.println("[XMage Access] Speech error: " + t.getMessage());
                }
            }
        });
    }

    /**
     * Stop all current and queued speech.
     */
    public void silence() {
        ScheduledFuture<?> prev = pendingInterrupt;
        if (prev != null) prev.cancel(false);
        pendingInterrupt = null;
        lastSpoken = null;

        ScheduledExecutorService s = scheduler;
        if (s == null || engine == null) return;
        s.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    engine.silence();
                } catch (Throwable t) {
                    // Engine errors during silence are not actionable.
                }
            }
        });
    }

    /**
     * Release scheduler and engine resources. Called on JVM shutdown.
     */
    public void shutdown() {
        ScheduledExecutorService s = scheduler;
        if (s != null) {
            s.shutdownNow();
            try {
                s.awaitTermination(500, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            scheduler = null;
        }
        if (engine instanceof WindowsSpeech) {
            ((WindowsSpeech) engine).shutdown();
        }
    }

    private boolean isDuplicate(String text) {
        return text.equals(lastSpoken)
                && System.currentTimeMillis() - lastSpokenAt < DEDUP_WINDOW_MS;
    }
}
