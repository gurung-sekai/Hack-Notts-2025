package launcher;

import java.awt.Dimension;
import java.io.Serial;
import java.io.Serializable;
import java.util.Locale;
import java.util.Objects;

/**
 * Aggregates launcher-configurable settings.
 */
public final class GameSettings implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Dimension resolution;
    private int refreshRate;
    private Locale language;
    private ControlsProfile controls;

    public GameSettings() {
        this(new Dimension(756, 468), 60, Locale.UK, new ControlsProfile());
    }

    public GameSettings(GameSettings other) {
        this(other.resolution(), other.refreshRate(), other.language(), new ControlsProfile(other.controls()));
    }

    public GameSettings(Dimension resolution, int refreshRate, Locale language, ControlsProfile controls) {
        this.resolution = (Dimension) Objects.requireNonNull(resolution, "resolution").clone();
        this.refreshRate = Math.max(30, refreshRate);
        this.language = Objects.requireNonNullElse(language, Locale.UK);
        this.controls = Objects.requireNonNullElseGet(controls, ControlsProfile::new);
    }

    public Dimension resolution() {
        return (Dimension) resolution.clone();
    }

    public void setResolution(Dimension resolution) {
        this.resolution = (Dimension) Objects.requireNonNull(resolution, "resolution").clone();
    }

    public int refreshRate() {
        return refreshRate;
    }

    public void setRefreshRate(int refreshRate) {
        this.refreshRate = Math.max(30, refreshRate);
    }

    public Locale language() {
        return language;
    }

    public void setLanguage(Locale language) {
        this.language = Objects.requireNonNullElse(language, Locale.UK);
    }

    public ControlsProfile controls() {
        return new ControlsProfile(controls);
    }

    public void setControls(ControlsProfile controls) {
        this.controls = Objects.requireNonNullElseGet(controls, ControlsProfile::new);
    }

    public ControlsProfile mutableControls() {
        return controls;
    }
}
