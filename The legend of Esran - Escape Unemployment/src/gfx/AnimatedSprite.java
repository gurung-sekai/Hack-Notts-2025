package gfx;

import util.ResourceLoader;
import util.SpriteSheetSlicer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Map;

/**
 * Lightweight sprite animator.
 * Loads frames by prefix: prefix + "0.png", "1.png", ... until a miss.
 */
public class AnimatedSprite {
    public enum State { IDLE, RUN, HIT }

    private final Map<State, BufferedImage[]> states = new EnumMap<>(State.class);
    private State state = State.IDLE;
    private double fps = 8.0, t = 0.0;
    private int i = 0;
    private final int drawW, drawH;

    public AnimatedSprite(int drawW, int drawH) {
        this.drawW = drawW; this.drawH = drawH;
    }

    public void setFps(double fps) { this.fps = fps; }
    public void setState(State s) { if (state != s) { state = s; i = 0; t = 0; } }
    public int w() { return drawW; }
    public int h() { return drawH; }

    public void add(State s, BufferedImage[] frames) {
        if (frames != null && frames.length > 0) states.put(s, frames);
    }

    /** Load frames from a classpath prefix: /path/name_f  -> name_f0.png ... */
    public void addFromPrefix(State s, String resourcePrefix) {
        ArrayList<BufferedImage> list = new ArrayList<>();
        for (int k = 0; k < 64; k++) {
            String path = resourcePrefix + k + ".png";
            try (InputStream is = ResourceLoader.open(path)) {
                if (is == null) break;
                list.add(ImageIO.read(is));
            } catch (IOException ex) {
                System.err.println("Failed to load anim frame: " + path + " -> " + ex.getMessage());
                break;
            }
        }
        if (!list.isEmpty()) {
            add(s, list.toArray(new BufferedImage[0]));
        }
    }

    /** Slice an irregular sheet using {@link SpriteSheetSlicer}. */
    public void addFromSheet(State state, String resourcePath) {
        addFromSheet(state, resourcePath, SpriteSheetSlicer.Options.DEFAULT);
    }

    /** Slice an irregular sheet using {@link SpriteSheetSlicer}. */
    public void addFromSheet(State state, String resourcePath, SpriteSheetSlicer.Options options) {
        try {
            add(state, SpriteSheetSlicer.slice(resourcePath, options));
        } catch (IOException e) {
            throw new RuntimeException("Failed to slice sprite sheet: " + resourcePath, e);
        }
    }

    public void update(double dt) {
        var seq = states.get(state);
        if (seq == null || seq.length <= 1) return;
        t += dt; double fpf = 1.0 / fps;
        while (t >= fpf) { t -= fpf; i = (i + 1) % seq.length; }
    }

    public BufferedImage frame() {
        var seq = states.get(state);
        return (seq == null || seq.length == 0) ? null : seq[i % seq.length];
    }
}
