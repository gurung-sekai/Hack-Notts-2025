package launcher;

import util.ResourceLoader;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Lightweight helper that streams and loops the title music.
 */
final class TitleMusicPlayer implements AutoCloseable {
    private final String resourcePath;
    private Clip clip;
    private FloatControl volumeControl;

    TitleMusicPlayer(String resourcePath) {
        this.resourcePath = resourcePath;
    }

    boolean prepare() {
        if (clip != null) {
            return true;
        }
        try (InputStream raw = ResourceLoader.open(resourcePath)) {
            if (raw == null) {
                return false;
            }
            try (AudioInputStream in = AudioSystem.getAudioInputStream(new BufferedInputStream(raw))) {
                clip = AudioSystem.getClip();
                clip.open(in);
                if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                    volumeControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                }
                return true;
            }
        } catch (UnsupportedAudioFileException | IOException ex) {
            System.err.println("[TitleMusicPlayer] Unable to load music: " + ex.getMessage());
            return false;
        } catch (Exception ex) {
            System.err.println("[TitleMusicPlayer] Unexpected audio error: " + ex.getMessage());
            return false;
        }
    }

    void playLoop(float volume) {
        if (!prepare()) {
            return;
        }
        setVolume(volume);
        clip.loop(Clip.LOOP_CONTINUOUSLY);
        clip.start();
    }

    void stop() {
        if (clip != null) {
            clip.stop();
            clip.flush();
            clip.setFramePosition(0);
        }
    }

    void setVolume(float volume) {
        if (volumeControl == null) {
            return;
        }
        float clamped = Math.max(0f, Math.min(1f, volume));
        float min = volumeControl.getMinimum();
        float max = volumeControl.getMaximum();
        float gain = min + (max - min) * clamped;
        volumeControl.setValue(gain);
    }

    boolean isReady() {
        return clip != null;
    }

    @Override
    public void close() {
        if (clip != null) {
            clip.stop();
            clip.close();
            clip = null;
            volumeControl = null;
        }
    }
}
