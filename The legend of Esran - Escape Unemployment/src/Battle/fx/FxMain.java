package Battle.fx;

/**
 * Legacy Gradle entry point that delegates to the Swing-based {@link launcher.GameLauncher}.
 * <p>
 * The Gradle application plugin is configured to launch this class by default. The original
 * project expected a JavaFX bootstrapper here, but the current launcher is Swing-only. To avoid
 * breaking existing launch scripts (e.g. {@code ./gradlew run} or the "gamelauncher" alias), we
 * provide this thin adapter that simply forwards to the real launcher.
 */
public final class FxMain {
    private FxMain() {
    }

    public static void main(String[] args) {
        launcher.GameLauncher.main(args);
    }
}
