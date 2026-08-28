package javafx.application;

/**
 * Stands in for the JavaFX toolkit. The real runLater hands the job to the
 * JavaFX application thread; here it runs inline, so a harness can assert on
 * the result without a toolkit. Safe to put on the classpath: this JDK 8 ships
 * no JavaFX, so nothing on the bootclasspath shadows it.
 */
public class Platform {
    public static int runLaterCalls;
    public static void runLater(Runnable job) { runLaterCalls++; job.run(); }
}
