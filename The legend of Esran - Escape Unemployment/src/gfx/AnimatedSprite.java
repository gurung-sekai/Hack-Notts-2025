package gfx;

import util.ResourceLoader;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Lightweight sprite animator.
 * Loads frames by prefix: prefix + "0.png", "1.png", ... until a miss.
 */
public class AnimatedSprite {
    public enum State { IDLE, RUN, HIT }

    private final Map<State, BufferedImage[]> states = new EnumMap<>(State.class);
    private int maxFrameWidth = 0;
    private int maxFrameHeight = 0;
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
        BufferedImage[] normalised = normaliseFrames(frames);
        if (normalised != null && normalised.length > 0) {
            states.put(s, normalised);
            for (BufferedImage frame : normalised) {
                if (frame == null) {
                    continue;
                }
                if (frame.getWidth() > maxFrameWidth) {
                    maxFrameWidth = frame.getWidth();
                }
                if (frame.getHeight() > maxFrameHeight) {
                    maxFrameHeight = frame.getHeight();
                }
            }
        }
    }

    /** Load frames from a classpath prefix: /path/name_f  -> name_f0.png ... */
    public boolean addFromPrefix(State s, String resourcePrefix) {
        BufferedImage[] frames = loadFramesFromPrefix(resourcePrefix);
        if (frames.length == 0) {
            return false;
        }
        add(s, frames);
        return true;
    }

    /** Attempt to load a frame sequence with flexible numbering (0/1-based, padded). */
    public static BufferedImage[] loadFramesFromPrefix(String resourcePrefix) {
        return loadFramesFromPrefix(resourcePrefix, 128);
    }

    private static BufferedImage[] loadFramesFromPrefix(String resourcePrefix, int maxFrames) {
        if (resourcePrefix == null || resourcePrefix.isBlank()) {
            return new BufferedImage[0];
        }

        int[] starts = {0, 1};
        int[] padWidths = {0, 2, 3};
        for (int start : starts) {
            for (int pad : padWidths) {
                BufferedImage[] attempt = loadSequence(resourcePrefix, start, pad, maxFrames);
                if (attempt.length > 0) {
                    return attempt;
                }
            }
        }
        return new BufferedImage[0];
    }

    private static BufferedImage[] loadSequence(String resourcePrefix, int startIndex, int padWidth, int maxFrames) {
        ArrayList<BufferedImage> frames = new ArrayList<>();
        int limit = Math.max(1, maxFrames);
        for (int idx = startIndex; idx < startIndex + limit; idx++) {
            String suffix = formatIndex(idx, padWidth);
            BufferedImage frame = readFrame(resourcePrefix + suffix);
            if (frame == null) {
                if (frames.isEmpty()) {
                    return new BufferedImage[0];
                }
                break;
            }
            frames.add(frame);
        }
        return frames.toArray(new BufferedImage[0]);
    }

    private static String formatIndex(int idx, int padWidth) {
        if (padWidth <= 1) {
            return String.valueOf(idx);
        }
        return String.format(Locale.ROOT, "%0" + padWidth + "d", idx);
    }

    private static BufferedImage readFrame(String basePath) {
        String[] extensions = {".png", ".PNG"};
        for (String ext : extensions) {
            String path = basePath + ext;
            try (InputStream is = ResourceLoader.open(path)) {
                if (is == null) {
                    continue;
                }
                BufferedImage image = ImageIO.read(is);
                if (image != null) {
                    return image;
                }
            } catch (IOException ex) {
                System.err.println("Failed to load anim frame: " + path + " -> " + ex.getMessage());
                return null;
            }
        }
        return null;
    }

    /** Load all PNG frames that live directly under a resource directory. */
    public static BufferedImage[] loadFramesFromDirectory(String resourceDirectory) {
        List<String> resources = ResourceLoader.listPng(resourceDirectory);
        if (resources.isEmpty()) {
            return new BufferedImage[0];
        }

        resources.sort(AnimatedSprite::compareFramePaths);
        List<BufferedImage> frames = new ArrayList<>(resources.size());
        for (String resource : resources) {
            try {
                frames.add(ResourceLoader.image(resource));
            } catch (IOException e) {
                throw new RuntimeException("Failed to load sprite frame: " + resource, e);
            }
        }
        return frames.toArray(new BufferedImage[0]);
    }

    /** Populate this sprite from all PNG frames inside a directory. */
    public boolean addFromDirectory(State state, String resourceDirectory) {
        BufferedImage[] frames = loadFramesFromDirectory(resourceDirectory);
        if (frames.length == 0) {
            return false;
        }
        add(state, frames);
        return true;
    }

    private static int compareFramePaths(String left, String right) {
        return Comparator.comparing(AnimatedSprite::frameKey)
                .thenComparing(String::compareTo)
                .compare(left, right);
    }

    private static FrameKey frameKey(String resourcePath) {
        String filename = resourcePath;
        int slash = filename.lastIndexOf('/');
        if (slash >= 0) {
            filename = filename.substring(slash + 1);
        }
        int dot = filename.lastIndexOf('.');
        if (dot >= 0) {
            filename = filename.substring(0, dot);
        }

        int idx = filename.length() - 1;
        while (idx >= 0 && Character.isDigit(filename.charAt(idx))) {
            idx--;
        }
        String prefix = filename.substring(0, idx + 1);
        int number = -1;
        if (idx + 1 < filename.length()) {
            try {
                number = Integer.parseInt(filename.substring(idx + 1));
            } catch (NumberFormatException ignored) {
                number = -1;
            }
        }
        return new FrameKey(prefix.toLowerCase(Locale.ROOT), number);
    }

    private record FrameKey(String prefix, int number) implements Comparable<FrameKey> {
        @Override
        public int compareTo(FrameKey other) {
            int cmp = prefix.compareTo(other.prefix);
            if (cmp != 0) {
                return cmp;
            }
            return Integer.compare(number, other.number);
        }
        add(state, frames);
        return true;
    }

    public static BufferedImage[] normaliseFrames(BufferedImage[] frames) {
        if (frames == null || frames.length == 0) {
            return null;
        }

        int maxW = 0;
        int maxH = 0;
        for (BufferedImage frame : frames) {
            if (frame == null) {
                continue;
            }
            if (frame.getWidth() > maxW) {
                maxW = frame.getWidth();
            }
            if (frame.getHeight() > maxH) {
                maxH = frame.getHeight();
            }
        }

        if (maxW <= 0 || maxH <= 0) {
            return new BufferedImage[0];
        }

        boolean uniform = true;
        for (BufferedImage frame : frames) {
            if (frame == null) {
                continue;
            }
            if (frame.getWidth() != maxW || frame.getHeight() != maxH) {
                uniform = false;
                break;
            }
        }
        if (uniform) {
            return frames.clone();
        }

        BufferedImage[] aligned = new BufferedImage[frames.length];
        for (int i = 0; i < frames.length; i++) {
            BufferedImage frame = frames[i];
            if (frame == null) {
                frame = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            }
            BufferedImage padded = new BufferedImage(maxW, maxH, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = padded.createGraphics();
            try {
                int x = (maxW - frame.getWidth()) / 2;
                int y = maxH - frame.getHeight();
                g.drawImage(frame, x, y, null);
            } finally {
                g.dispose();
            }
            aligned[i] = padded;
        }
        return aligned;
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

    public int maxFrameWidth() {
        return maxFrameWidth;
    }

    public int maxFrameHeight() {
        return maxFrameHeight;
    }

    /**
     * Visit every frame stored across all animation states.
     * Frames are provided in insertion order so callers can perform
     * cache warm-up or analysis without exposing the internal arrays.
     */
    public void forEachFrame(Consumer<BufferedImage> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        for (BufferedImage[] sequence : states.values()) {
            if (sequence == null) {
                continue;
            }
            for (BufferedImage frame : sequence) {
                if (frame != null) {
                    consumer.accept(frame);
                }
            }
        }
    }
}
