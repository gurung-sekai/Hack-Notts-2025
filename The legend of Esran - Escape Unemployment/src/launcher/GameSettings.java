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
    private static final long serialVersionUID = 2L;

    private Dimension resolution;
    private int refreshRate;
    private Locale language;
    private ControlsProfile controls;
    private boolean fullscreen;
    private float musicVolume;
    private float sfxVolume;
    private boolean musicEnabled;
    private boolean cutscenesEnabled;

    public GameSettings() {
        this(new Dimension(756, 468), 60, Locale.UK, new ControlsProfile());
    }

    public GameSettings(GameSettings other) {
        this(other.resolution(),
                other.refreshRate(),
                other.language(),
                new ControlsProfile(other.controls()),
                other.fullscreen(),
                other.musicVolume(),
                other.sfxVolume(),
                other.musicEnabled(),
                other.cutscenesEnabled());
    }

    public GameSettings(Dimension resolution, int refreshRate, Locale language, ControlsProfile controls) {
        this(resolution, refreshRate, language, controls, true, 0.7f, 0.8f, true, true);
    }

    public GameSettings(Dimension resolution,
                        int refreshRate,
                        Locale language,
                        ControlsProfile controls,
                        boolean fullscreen,
                        float musicVolume,
                        float sfxVolume,
                        boolean musicEnabled,
                        boolean cutscenesEnabled) {
        this.resolution = (Dimension) Objects.requireNonNull(resolution, "resolution").clone();
        this.refreshRate = clampRefresh(refreshRate);
        this.language = Objects.requireNonNullElse(language, Locale.UK);
        this.controls = Objects.requireNonNullElseGet(controls, ControlsProfile::new);
        this.fullscreen = fullscreen;
        this.musicVolume = clampVolume(musicVolume);
        this.sfxVolume = clampVolume(sfxVolume);
        this.musicEnabled = musicEnabled;
        this.cutscenesEnabled = cutscenesEnabled;
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
        this.refreshRate = clampRefresh(refreshRate);
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

    public boolean fullscreen() {
        return fullscreen;
    }

    public void setFullscreen(boolean fullscreen) {
        this.fullscreen = fullscreen;
    }

    public float musicVolume() {
        return musicVolume;
    }

    public void setMusicVolume(float musicVolume) {
        this.musicVolume = clampVolume(musicVolume);
    }

    public float sfxVolume() {
        return sfxVolume;
    }

    public void setSfxVolume(float sfxVolume) {
        this.sfxVolume = clampVolume(sfxVolume);
    }

    public boolean musicEnabled() {
        return musicEnabled;
    }

    public void setMusicEnabled(boolean musicEnabled) {
        this.musicEnabled = musicEnabled;
    }

    public boolean cutscenesEnabled() {
        return cutscenesEnabled;
    }

    public void setCutscenesEnabled(boolean cutscenesEnabled) {
        this.cutscenesEnabled = cutscenesEnabled;
    }

    private static int clampRefresh(int refreshRate) {
        return Math.max(30, Math.min(240, refreshRate));
    }

    private static float clampVolume(float volume) {
        if (Float.isNaN(volume)) {
            return 0f;
        }
        return Math.max(0f, Math.min(1f, volume));
    }
}
