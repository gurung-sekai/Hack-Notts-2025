package launcher;

import java.awt.Dimension;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

/**
 * Simple persistence helper storing launcher settings in a properties file.
 */
public final class SettingsPersistence {
    private static final Dimension DEFAULT_RESOLUTION = new Dimension(756, 468);
    private static final Locale DEFAULT_LOCALE = Locale.UK;
    private static final Set<Locale> SUPPORTED_LOCALES = Set.of(
            DEFAULT_LOCALE,
            new Locale("cy", "GB")
    );
    private static final int MIN_DIMENSION = 360;
    private static final int MAX_DIMENSION = 3840;

    private final Path settingsFile;

    public SettingsPersistence(Path storageDir) {
        this.settingsFile = storageDir.resolve("settings.properties");
    }

    public void save(GameSettings settings) throws IOException {
        Files.createDirectories(settingsFile.getParent());
        Properties props = new Properties();
        Dimension resolution = settings.resolution();
        props.setProperty("resolution.width", Integer.toString(resolution.width));
        props.setProperty("resolution.height", Integer.toString(resolution.height));
        props.setProperty("refreshRate", Integer.toString(settings.refreshRate()));
        props.setProperty("language", settings.language().toLanguageTag());
        for (var entry : settings.controls().view().entrySet()) {
            props.setProperty("control." + entry.getKey().name(), Integer.toString(entry.getValue()));
        }
        try (OutputStream out = Files.newOutputStream(settingsFile)) {
            props.store(out, "Dungeon Rooms launcher settings");
        }
    }

    public Optional<GameSettings> load() {
        if (!Files.isRegularFile(settingsFile)) {
            return Optional.empty();
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(settingsFile)) {
            props.load(in);
        } catch (IOException ex) {
            return Optional.empty();
        }
        try {
            Dimension resolution = parseResolution(props);
            int refresh = parseRefreshRate(props);
            Locale language = parseLocale(props);
            ControlsProfile profile = parseControls(props);
            return Optional.of(new GameSettings(resolution, refresh, language, profile));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    public Path settingsFile() {
        return settingsFile;
    }

    private static Dimension parseResolution(Properties props) {
        int width = parseInt(props, "resolution.width", DEFAULT_RESOLUTION.width);
        int height = parseInt(props, "resolution.height", DEFAULT_RESOLUTION.height);
        width = clamp(width, MIN_DIMENSION, MAX_DIMENSION);
        height = clamp(height, MIN_DIMENSION, MAX_DIMENSION);
        return new Dimension(width, height);
    }

    private static int parseRefreshRate(Properties props) {
        int refresh = parseInt(props, "refreshRate", 60);
        return Math.max(30, Math.min(240, refresh));
    }

    private static Locale parseLocale(Properties props) {
        String tag = props.getProperty("language", DEFAULT_LOCALE.toLanguageTag());
        Locale requested = Locale.forLanguageTag(tag);
        return SUPPORTED_LOCALES.stream()
                .filter(locale -> locale.toLanguageTag().equalsIgnoreCase(requested.toLanguageTag()))
                .findFirst()
                .orElse(DEFAULT_LOCALE);
    }

    private static ControlsProfile parseControls(Properties props) {
        ControlsProfile profile = new ControlsProfile();
        for (ControlAction action : ControlAction.values()) {
            String propertyKey = "control." + action.name();
            String value = props.getProperty(propertyKey);
            if (value == null) {
                continue;
            }
            int keyCode = parseInt(props, propertyKey, profile.keyFor(action));
            if (keyCode < 0 || keyCode > KeyEvent.KEY_LAST) {
                continue;
            }
            profile.rebind(action, keyCode);
        }
        return profile;
    }

    private static int parseInt(Properties props, String key, int defaultValue) {
        String text = props.getProperty(key);
        if (text == null || text.isBlank()) {
            return defaultValue;
        }
        return Integer.parseInt(text.trim());
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
