package World.cutscene;

import gfx.AnimatedSprite;
import gfx.HiDpiScaler;
import util.ResourceLoader;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Factory for animated backdrops used by cutscenes.
 */
public final class CutsceneBackgrounds {
    private static final Map<String, AnimatedBackdrop> CACHE = new ConcurrentHashMap<>();

    private CutsceneBackgrounds() {
    }

    /**
     * Animated glow for the Golden Knight's throne room.
     */
    public static AnimatedBackdrop goldenThrone() {
        return cachedSequence("goldenThrone", CutsceneBackgrounds::proceduralEmber, 6,
                "/resources/Cutscene/GoldenKnight/backgrounds",
                "/resources/Cutscene/GoldenKnight");
    }

    /** Backwards compatible alias kept for callers that still expect the old name. */
    public static AnimatedBackdrop emberSwirl() {
        return goldenThrone();
    }

    /**
     * Animated aura for the princess' prison chamber.
     */
    public static AnimatedBackdrop dungeonCaptivity() {
        return cachedSequence("dungeonCaptivity", CutsceneBackgrounds::proceduralPrison, 6,
                "/resources/Cutscene/Dungeon",
                "/resources/sprites/Princess/backgrounds",
                "/resources/sprites/Princess");
    }

    /** Backwards compatible alias. */
    public static AnimatedBackdrop prisonAura() {
        return dungeonCaptivity();
    }

    /**
     * Animated backdrop for the hero's determination scenes.
     */
    public static AnimatedBackdrop heroResolve() {
        return cachedSequence("heroResolve", CutsceneBackgrounds::proceduralResolve, 6,
                "/resources/Cutscene/Hero",
                "/resources/Cutscene/Bridge",
                "/resources/Cutscene/Confrontation");
    }

    /**
     * Celebration background shown when the queen is rescued.
     */
    public static AnimatedBackdrop victoryCelebration() {
        return cachedSequence("victoryCelebration", CutsceneBackgrounds::proceduralRoyal, 6,
                "/resources/Cutscene/Victory",
                "/resources/Cutscene/Celebration");
    }

    /** Backwards compatible alias. */
    public static AnimatedBackdrop royalCelebration() {
        return victoryCelebration();
    }

    /**
     * Animated background used by the apothecary shop overlay.
     */
    public static AnimatedBackdrop shopInterior() {
        return cachedSequence("shopInterior", CutsceneBackgrounds::proceduralShop, 5,
                "/resources/Cutscene/Shop",
                "/resources/Shop/backgrounds",
                "/resources/Shop");
    }

    /** Backwards compatible alias. */
    public static AnimatedBackdrop shopGlow() {
        return shopInterior();
    }

    private static AnimatedBackdrop cachedSequence(String key, Supplier<AnimatedBackdrop> fallback,
                                                   int frameTicks, String... directories) {
        return CACHE.computeIfAbsent(key, ignored -> {
            BufferedImage[] frames = loadFrames(directories);
            if (frames.length > 0) {
                return new SequenceBackdrop(frames, frameTicks);
            }
            return fallback.get();
        });
    }

    private static BufferedImage[] loadFrames(String... directories) {
        if (directories == null) {
            return new BufferedImage[0];
        }
        for (String dir : directories) {
            if (dir == null || dir.isBlank()) {
                continue;
            }
            String normalized = ensureLeadingSlash(dir);
            BufferedImage[] fromFile = loadSingleResource(normalized);
            if (fromFile.length > 0) {
                return fromFile;
            }
            for (String candidate : expandDirectories(normalized)) {
                BufferedImage[] frames = loadDirectory(candidate);
                if (frames.length > 0) {
                    return frames;
                }
            }
        }
        return new BufferedImage[0];
    }

    private static List<String> expandDirectories(String directory) {
        List<String> candidates = new ArrayList<>();
        if (directory == null || directory.isBlank()) {
            return candidates;
        }
        String normalized = ensureLeadingSlash(directory);
        candidates.add(normalized);
        List<String> children = ResourceLoader.listDirectories(normalized);
        if (!children.isEmpty()) {
            children.stream().sorted().forEach(candidates::add);
        }
        return candidates;
    }

    private static String ensureLeadingSlash(String path) {
        String trimmed = path.trim();
        return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
    }

    private static BufferedImage[] loadDirectory(String directory) {
        BufferedImage[] pngFrames = AnimatedSprite.loadFramesFromDirectory(directory);
        if (pngFrames.length > 0) {
            return pngFrames;
        }
        List<String> gifs = ResourceLoader.list(directory, name -> name.toLowerCase(Locale.ROOT).endsWith(".gif"));
        for (String gif : gifs) {
            BufferedImage[] frames = AnimatedSprite.loadGifFrames(gif);
            if (frames.length > 0) {
                return frames;
            }
        }
        return new BufferedImage[0];
    }

    private static BufferedImage[] loadSingleResource(String resourcePath) {
        String lower = resourcePath.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".gif")) {
            return AnimatedSprite.loadGifFrames(resourcePath);
        }
        if (lower.endsWith(".png")) {
            try {
                BufferedImage single = ResourceLoader.image(resourcePath);
                return single == null ? new BufferedImage[0] : new BufferedImage[]{single};
            } catch (IOException e) {
                throw new RuntimeException("Failed to load backdrop image: " + resourcePath, e);
            }
        }
        return new BufferedImage[0];
    }

    private static AnimatedBackdrop proceduralEmber() {
        return (g, w, h, tick) -> {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            float phase = (tick % 360) / 360f;
            Color base = blend(new Color(32, 12, 8), new Color(180, 70, 30), phase);
            g.setPaint(new GradientPaint(0, 0, base, w, h, base.brighter()));
            g.fillRect(0, 0, w, h);
            g.setComposite(AlphaComposite.SrcOver.derive(0.25f));
            for (int i = 0; i < 18; i++) {
                double angle = (tick * 0.02 + i * Math.PI * 2 / 18);
                int radius = (int) (Math.min(w, h) * 0.18);
                int cx = (int) (w / 2 + Math.cos(angle) * w * 0.25);
                int cy = (int) (h / 2 + Math.sin(angle * 1.5) * h * 0.25);
                g.setPaint(new RadialGradientPaint(new Point2D.Float(cx, cy), radius,
                        new float[]{0f, 0.8f, 1f},
                        new Color[]{new Color(255, 160, 60, 160), new Color(255, 210, 120, 80), new Color(0, 0, 0, 0)}));
                g.fillOval(cx - radius, cy - radius, radius * 2, radius * 2);
            }
            g.setComposite(AlphaComposite.SrcOver);
        };
    }

    private static AnimatedBackdrop proceduralPrison() {
        return (g, w, h, tick) -> {
            g.setColor(new Color(16, 22, 42));
            g.fillRect(0, 0, w, h);
            g.setComposite(AlphaComposite.SrcOver.derive(0.65f));
            for (int i = 0; i < 6; i++) {
                int offset = (int) ((tick + i * 40) % h);
                g.setColor(new Color(120, 120, 220, 90));
                g.fillRect(i * w / 6, (offset + h / 2) % h, w / 6 + 12, h / 3);
            }
            g.setComposite(AlphaComposite.SrcOver.derive(0.3f));
            g.setColor(new Color(255, 200, 230));
            g.fillOval(w / 4, h / 5, w / 2, h / 2);
            g.setComposite(AlphaComposite.SrcOver);
        };
    }

    private static AnimatedBackdrop proceduralRoyal() {
        return (g, w, h, tick) -> {
            g.setColor(new Color(16, 24, 44));
            g.fillRect(0, 0, w, h);
            Random rnd = new Random(42);
            for (int i = 0; i < 80; i++) {
                rnd.setSeed(i * 13L + tick);
                int x = rnd.nextInt(w);
                int y = rnd.nextInt(h);
                int size = 2 + rnd.nextInt(4);
                g.setColor(new Color(220, 220, 255, 120 + rnd.nextInt(120)));
                g.fillOval(x, (y + (int) (Math.sin((tick + i) * 0.05) * 15) + h) % h, size, size);
            }
            g.setColor(new Color(255, 240, 170, 160));
            g.fillRoundRect(w / 5, h / 3, w * 3 / 5, h / 3, 30, 30);
        };
    }

    private static AnimatedBackdrop proceduralShop() {
        return (g, w, h, tick) -> {
            float phase = (float) Math.sin(tick * 0.05);
            Color outer = blend(new Color(12, 26, 38), new Color(24, 48, 64), (phase + 1) / 2f);
            g.setColor(outer);
            g.fillRect(0, 0, w, h);
            g.setComposite(AlphaComposite.SrcOver.derive(0.55f));
            for (int i = 0; i < 5; i++) {
                int radius = w / 6 + i * 40;
                g.setColor(new Color(255, 220, 140, 120 - i * 18));
                g.fillOval(w / 2 - radius, h / 2 - radius, radius * 2, radius * 2);
            }
            g.setComposite(AlphaComposite.SrcOver);
        };
    }

    private static AnimatedBackdrop proceduralResolve() {
        return (g, w, h, tick) -> {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setPaint(new GradientPaint(0, 0, new Color(18, 38, 58), w, h, new Color(42, 92, 126)));
            g.fillRect(0, 0, w, h);
            g.setComposite(AlphaComposite.SrcOver.derive(0.22f));
            for (int i = 0; i < 12; i++) {
                double angle = tick * 0.01 + i * Math.PI / 6.0;
                int radius = Math.max(w, h);
                int cx = (int) (w / 2 + Math.cos(angle) * w * 0.35);
                int cy = (int) (h / 2 + Math.sin(angle * 1.1) * h * 0.25);
                g.setPaint(new RadialGradientPaint(new Point2D.Float(cx, cy), radius / 2f,
                        new float[]{0f, 1f},
                        new Color[]{new Color(120, 220, 255, 90), new Color(0, 0, 0, 0)}));
                g.fillOval(cx - radius / 2, cy - radius / 2, radius, radius);
            }
            g.setComposite(AlphaComposite.SrcOver);
            g.setColor(new Color(255, 255, 255, 30));
            g.drawLine(0, h - 60, w, h - 120);
        };
    }

    private static Color blend(Color a, Color b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int r = (int) (a.getRed() * (1 - t) + b.getRed() * t);
        int g = (int) (a.getGreen() * (1 - t) + b.getGreen() * t);
        int bC = (int) (a.getBlue() * (1 - t) + b.getBlue() * t);
        return new Color(r, g, bC);
    }

    private static final class SequenceBackdrop implements AnimatedBackdrop {
        private final BufferedImage[] frames;
        private final int frameTicks;
        private final Map<Integer, Map<Long, BufferedImage>> scaledCache = new ConcurrentHashMap<>();

        private SequenceBackdrop(BufferedImage[] rawFrames, int frameTicks) {
            BufferedImage[] normalised = AnimatedSprite.normaliseFrames(rawFrames);
            this.frames = normalised == null ? new BufferedImage[0] : normalised;
            this.frameTicks = Math.max(1, frameTicks);
        }

        @Override
        public void paint(Graphics2D g, int width, int height, long tick) {
            if (frames.length == 0 || width <= 0 || height <= 0) {
                return;
            }
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int index = (int) ((tick / frameTicks) % frames.length);
            BufferedImage scaled = scaledFrame(index, width, height);
            if (scaled == null) {
                return;
            }
            int drawX = (width - scaled.getWidth()) / 2;
            int drawY = (height - scaled.getHeight()) / 2;
            g.drawImage(scaled, drawX, drawY, null);
            g.setComposite(AlphaComposite.SrcOver.derive(0.18f));
            g.setColor(new Color(0, 0, 0, 200));
            g.fillRect(0, 0, width, height);
            g.setComposite(AlphaComposite.SrcOver);
        }

        private BufferedImage scaledFrame(int index, int width, int height) {
            Map<Long, BufferedImage> perFrame = scaledCache.computeIfAbsent(index, key -> new ConcurrentHashMap<>());
            long key = (((long) width) << 32) ^ (height & 0xffffffffL);
            return perFrame.computeIfAbsent(key, ignored -> scaleFrame(frames[index], width, height));
        }

        private BufferedImage scaleFrame(BufferedImage frame, int width, int height) {
            if (frame == null) {
                return null;
            }
            double scale = Math.max(width / (double) frame.getWidth(), height / (double) frame.getHeight());
            if (!Double.isFinite(scale) || scale <= 0.0) {
                scale = 1.0;
            }
            int targetW = Math.max(width, (int) Math.round(frame.getWidth() * scale));
            int targetH = Math.max(height, (int) Math.round(frame.getHeight() * scale));
            BufferedImage scaled = HiDpiScaler.scale(frame, targetW, targetH);
            return scaled == null ? frame : scaled;
        }
    }
}
