package launcher;

import java.awt.Dimension;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;

/**
 * Simple persistence helper storing launcher settings in a properties file.
 */
public final class SettingsPersistence {
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
        Dimension resolution = new Dimension(
                Integer.parseInt(props.getProperty("resolution.width", "756")),
                Integer.parseInt(props.getProperty("resolution.height", "468")));
        int refresh = Integer.parseInt(props.getProperty("refreshRate", "60"));
        Locale language = Locale.forLanguageTag(props.getProperty("language", Locale.UK.toLanguageTag()));
        ControlsProfile profile = new ControlsProfile();
        for (ControlAction action : ControlAction.values()) {
            String value = props.getProperty("control." + action.name());
            if (value != null) {
                profile.rebind(action, Integer.parseInt(value));
            }
        }
        return Optional.of(new GameSettings(resolution, refresh, language, profile));
    }

    public Path settingsFile() {
        return settingsFile;
    }
}
