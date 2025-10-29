package World.cutscene;

import gfx.AnimatedSprite;
import gfx.HiDpiScaler;
import util.ResourceLoader;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
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
        return tintedSequence("goldenThrone", CutsceneBackgrounds::proceduralEmber, 6,
                new Color(255, 208, 96), 0.28f,
                "/resources/Cutscene/Throne",
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
        return tintedSequence("heroResolve", CutsceneBackgrounds::proceduralResolve, 6,
                new Color(96, 176, 255), 0.24f,
                "/resources/Cutscene/Dungeon",
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
        return tintedSequence("shopInterior", CutsceneBackgrounds::proceduralShop, 5,
                new Color(255, 204, 140), 0.22f,
                "/resources/Cutscene/Shop",
                "/resources/Shop/backgrounds",
                "/resources/Shop");
    }

    /** Backwards compatible alias. */
    public static AnimatedBackdrop shopGlow() {
        return shopInterior();
    }

    /**
     * Mossy caverns framing the treasure hoard of the goblin boss.
     */
    public static AnimatedBackdrop bossCatacombs() {
        return tintedSequence("bossCatacombs", CutsceneBackgrounds::proceduralCatacombs, 6,
                new Color(120, 210, 150), 0.32f,
                "/resources/Cutscene/Dungeon");
    }

    /**
     * Ethereal mausoleum haze for the warden of souls.
     */
    public static AnimatedBackdrop bossShroud() {
        return tintedSequence("bossShroud", CutsceneBackgrounds::proceduralShroud, 6,
                new Color(140, 130, 220), 0.30f,
                "/resources/Cutscene/Dungeon");
    }

    /**
     * Blazing forge backdrops for fiery lieutenants.
     */
    public static AnimatedBackdrop bossFoundry() {
        return tintedSequence("bossFoundry", CutsceneBackgrounds::proceduralFoundry, 6,
                new Color(255, 148, 68), 0.28f,
                "/resources/Cutscene/Throne",
                "/resources/Cutscene/Shop");
    }

    /**
     * Gleaming mechanical glow for the automaton guardian.
     */
    public static AnimatedBackdrop bossFoundrySteel() {
        return tintedSequence("bossFoundrySteel", CutsceneBackgrounds::proceduralFoundrySteel, 6,
                new Color(160, 210, 255), 0.22f,
                "/resources/Cutscene/Shop");
    }

    /**
     * Arcane ripple for mind-bending sorcerers.
     */
    public static AnimatedBackdrop bossArcana() {
        return tintedSequence("bossArcana", CutsceneBackgrounds::proceduralArcana, 6,
                new Color(220, 160, 255), 0.30f,
                "/resources/Cutscene/Throne");
    }

    /**
     * Shimmering mirage backdrop for illusion weavers.
     */
    public static AnimatedBackdrop bossMirage() {
        return tintedSequence("bossMirage", CutsceneBackgrounds::proceduralMirage, 6,
                new Color(140, 240, 255), 0.28f,
                "/resources/Cutscene/Throne");
    }

    /**
     * Verdant glow for nature-twisted adversaries.
     */
    public static AnimatedBackdrop bossWildwood() {
        return tintedSequence("bossWildwood", CutsceneBackgrounds::proceduralWildwood, 6,
                new Color(150, 220, 140), 0.30f,
                "/resources/Cutscene/Dungeon");
    }

    private static AnimatedBackdrop cachedSequence(String key, Supplier<AnimatedBackdrop> fallback,
                                                   int frameTicks, String... directories) {
        return sequenceFrom(key, fallback, frameTicks, null, 0f, directories);
    }

    private static AnimatedBackdrop tintedSequence(String key, Supplier<AnimatedBackdrop> fallback,
                                                   int frameTicks, Color overlay, float overlayAlpha,
                                                   String... directories) {
        String cacheKey = overlay == null ? key : key + "#" + Integer.toHexString(overlay.getRGB()) + "@" + overlayAlpha;
        return sequenceFrom(cacheKey, fallback, frameTicks, overlay, overlayAlpha, directories);
    }

    private static AnimatedBackdrop sequenceFrom(String key, Supplier<AnimatedBackdrop> fallback,
                                                 int frameTicks, Color overlay, float overlayAlpha,
                                                 String... directories) {
        return CACHE.computeIfAbsent(key, ignored -> {
            BufferedImage[] frames = loadFrames(directories);
            if (frames.length > 0) {
                return new SequenceBackdrop(frames, frameTicks, overlay, overlayAlpha);
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
        return swirlingBackdrop(new Color(18, 38, 58), new Color(36, 88, 132),
                new Color(120, 220, 255), 0.012, Math.PI / 6.0, 0.58);
    }

    private static AnimatedBackdrop proceduralCatacombs() {
        return swirlingBackdrop(new Color(12, 26, 20), new Color(28, 68, 36),
                new Color(120, 210, 150), 0.010, Math.PI / 7.0, 0.64);
    }

    private static AnimatedBackdrop proceduralShroud() {
        return swirlingBackdrop(new Color(8, 12, 28), new Color(32, 40, 74),
                new Color(150, 140, 240), 0.011, Math.PI / 5.5, 0.60);
    }

    private static AnimatedBackdrop proceduralArcana() {
        return swirlingBackdrop(new Color(22, 12, 36), new Color(48, 26, 78),
                new Color(220, 160, 255), 0.013, Math.PI / 5.0, 0.62);
    }

    private static AnimatedBackdrop proceduralMirage() {
        return swirlingBackdrop(new Color(8, 24, 34), new Color(22, 70, 88),
                new Color(140, 240, 255), 0.012, Math.PI / 5.5, 0.60);
    }

    private static AnimatedBackdrop proceduralWildwood() {
        return swirlingBackdrop(new Color(8, 26, 16), new Color(24, 70, 34),
                new Color(150, 220, 140), 0.010, Math.PI / 6.0, 0.66);
    }

    private static AnimatedBackdrop proceduralFoundry() {
        return forgeBackdrop(new Color(28, 10, 0), new Color(90, 30, 8),
                new Color(255, 150, 60), new Color(255, 200, 120));
    }

    private static AnimatedBackdrop proceduralFoundrySteel() {
        return forgeBackdrop(new Color(10, 18, 30), new Color(40, 60, 86),
                new Color(160, 220, 255), new Color(190, 230, 255));
    }

    private static AnimatedBackdrop swirlingBackdrop(Color top, Color bottom, Color glow,
                                                      double phaseSpeed, double offset, double radiusFactor) {
        return (g, w, h, tick) -> {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setPaint(new GradientPaint(0, 0, top, 0, h, bottom));
            g.fillRect(0, 0, w, h);
            g.setComposite(AlphaComposite.SrcOver.derive(0.28f));
            int waves = 12;
            for (int i = 0; i < waves; i++) {
                double angle = tick * phaseSpeed + i * offset;
                int radius = (int) Math.round(Math.max(80, Math.min(w, h) * radiusFactor));
                int cx = (int) (w / 2 + Math.cos(angle) * w * 0.34);
                int cy = (int) (h / 2 + Math.sin(angle * 0.85) * h * 0.28);
                g.setPaint(new RadialGradientPaint(new Point2D.Float(cx, cy), (float) radius,
                        new float[]{0f, 0.55f, 1f},
                        new Color[]{new Color(glow.getRed(), glow.getGreen(), glow.getBlue(), 210),
                                new Color(glow.getRed(), glow.getGreen(), glow.getBlue(), 80),
                                new Color(0, 0, 0, 0)}));
                g.fillOval(cx - radius, cy - radius, radius * 2, radius * 2);
            }
            g.setComposite(AlphaComposite.SrcOver);
            g.setColor(new Color(glow.getRed(), glow.getGreen(), glow.getBlue(), 45));
            g.setStroke(new BasicStroke(Math.max(2f, w / 480f)));
            int baseline = (int) (h * 0.78 + Math.sin(tick * phaseSpeed * 0.6) * h * 0.05);
            g.drawLine((int) (w * 0.1), baseline, (int) (w * 0.9), baseline - (int) (h * 0.04));
        };
    }

    private static AnimatedBackdrop forgeBackdrop(Color top, Color bottom, Color spark, Color beam) {
        return (g, w, h, tick) -> {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setPaint(new GradientPaint(0, 0, top, 0, h, bottom));
            g.fillRect(0, 0, w, h);
            g.setComposite(AlphaComposite.SrcOver.derive(0.35f));
            int columns = Math.max(4, w / 240);
            for (int column = 0; column < columns; column++) {
                double progress = (tick * 0.035 + column * 0.2) % 1.0;
                int cx = (int) (w * (column + 0.5) / columns);
                int cy = (int) (h * (1.05 - progress));
                int radius = Math.max(60, Math.min(w, h) / 5);
                g.setPaint(new RadialGradientPaint(new Point2D.Float(cx, cy), (float) radius,
                        new float[]{0f, 0.45f, 1f},
                        new Color[]{new Color(spark.getRed(), spark.getGreen(), spark.getBlue(), 220),
                                new Color(spark.getRed(), spark.getGreen(), spark.getBlue(), 90),
                                new Color(0, 0, 0, 0)}));
                g.fillOval(cx - radius, cy - radius, radius * 2, radius * 2);
            }
            g.setComposite(AlphaComposite.SrcOver);
            g.setColor(new Color(beam.getRed(), beam.getGreen(), beam.getBlue(), 70));
            int beams = 4;
            for (int i = 0; i < beams; i++) {
                int y = (int) (h * (0.2 + 0.15 * i) + Math.sin((tick + i * 20) * 0.05) * 18);
                g.fillRect(0, Math.max(0, y - 5), w, 10);
            }
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
        private final Color overlayColor;
        private final float overlayAlpha;
        private final Map<Integer, Map<Long, BufferedImage>> scaledCache = new ConcurrentHashMap<>();

        private SequenceBackdrop(BufferedImage[] rawFrames, int frameTicks) {
            this(rawFrames, frameTicks, null, 0f);
        }

        private SequenceBackdrop(BufferedImage[] rawFrames, int frameTicks, Color overlay, float overlayAlpha) {
            BufferedImage[] normalised = AnimatedSprite.normaliseFrames(rawFrames);
            this.frames = normalised == null ? new BufferedImage[0] : normalised;
            this.frameTicks = Math.max(1, frameTicks);
            this.overlayColor = overlay;
            this.overlayAlpha = overlayAlpha;
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
            if (overlayColor != null && overlayAlpha > 0f) {
                float alpha = Math.max(0f, Math.min(1f, overlayAlpha));
                g.setComposite(AlphaComposite.SrcOver.derive(alpha));
                Color tint = new Color(overlayColor.getRed(), overlayColor.getGreen(), overlayColor.getBlue());
                g.setColor(tint);
                g.fillRect(0, 0, width, height);
            }
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
