package fx;

import util.ResourceLoader;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Factory of ready-made effects matching your folder layout. */
public final class FXLibrary {
    private FXLibrary() {}

    private static final Map<String, BufferedImage> IMAGE_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, BufferedImage[]> FRAME_CACHE = new ConcurrentHashMap<>();

    private static BufferedImage load(String path) {
        return IMAGE_CACHE.computeIfAbsent(path, FXLibrary::loadImageUnchecked);
    }

    private static BufferedImage loadImageUnchecked(String path) {
        try {
            return ResourceLoader.image(path);
        } catch (IOException e) {
            throw new RuntimeException("Missing FX image: " + path, e);
        }
    }

    private static FrameAnim sequence1Based(String base, String ext, int count, double fps, boolean loop) {
        String key = "seq|" + base + "|" + ext + "|" + count;
        BufferedImage[] frames = FRAME_CACHE.computeIfAbsent(key,
                unused -> loadSequenceFrames(base, ext, count));
        return FrameAnim.fromFrames(frames, fps, loop);
    }

    private static BufferedImage[] loadSequenceFrames(String base, String ext, int count) {
        BufferedImage[] frames = new BufferedImage[count];
        for (int k = 1; k <= count; k++) {
            String num = (k < 10 && base.endsWith("_")) ? "0" + k : String.valueOf(k);
            String path = base + num + ext;
            frames[k - 1] = load(path);
        }
        return frames;
    }

    private static FrameAnim sheet(String path, int cols, int rows, double fps, boolean loop) {
        String key = "sheet|" + path + "|" + cols + "x" + rows;
        BufferedImage[] frames = FRAME_CACHE.computeIfAbsent(key,
                unused -> sliceSheet(path, cols, rows));
        return FrameAnim.fromFrames(frames, fps, loop);
    }

    private static BufferedImage[] sliceSheet(String path, int cols, int rows) {
        BufferedImage sheet = load(path);
        int w = sheet.getWidth() / cols;
        int h = sheet.getHeight() / rows;
        BufferedImage[] frames = new BufferedImage[cols * rows];
        int idx = 0;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                frames[idx++] = sheet.getSubimage(c * w, r * h, w, h);
            }
        }
        return frames;
    }

    // ----------------- Smoke Effects -----------------

    public static Effect smokeSmall(double wx, double wy) {
        var a = sequence1Based(
                "/resources/effects/Smoke Effects/FX001/FX001_", ".png", 5, 12, false);
        return new Effect(a, Effect.Space.WORLD, Effect.Blend.NORMAL, wx, wy).scale(1.0);
    }

    public static Effect smokeLarge(double wx, double wy) {
        var a = sequence1Based(
                "/resources/effects/Smoke Effects/FX002/FX002_", ".png", 8, 12, false);
        return new Effect(a, Effect.Space.WORLD, Effect.Blend.NORMAL, wx, wy).scale(1.2);
    }

    // ----------------- FireEffect -----------------

    public static Effect fireBreath(double wx, double wy, double dirX, double dirY) {
        var a = sheet("/resources/effects/FireEffect/Fire Breath SpriteSheet.png", 8, 2, 16, true);
        double speed = 140;
        return new Effect(a, Effect.Space.WORLD, Effect.Blend.ADDITIVE, wx, wy)
                .vel(dirX * speed, dirY * speed)
                .life(0.8);
    }

    public static Effect fireHit(double wx, double wy) {
        var a = sheet("/resources/effects/FireEffect/Fire Breath hit effect SpriteSheet.png", 6, 1, 20, false);
        return new Effect(a, Effect.Space.WORLD, Effect.Blend.ADDITIVE, wx, wy).scale(1.2);
    }

    // ----------------- Thunder -----------------

    public static Effect thunderStrike(double wx, double wy) {
        var a = sheet("/resources/effects/Thunder Strike/Thunderstrike wo blur.png", 8, 1, 18, false);
        return new Effect(a, Effect.Space.WORLD, Effect.Blend.ADDITIVE, wx, wy);
    }

    public static Effect thunderSplash(double wx, double wy) {
        var a = sheet("/resources/effects/Thunder Splash/Thunder splash wo blur.png", 8, 1, 18, false);
        return new Effect(a, Effect.Space.WORLD, Effect.Blend.ADDITIVE, wx, wy);
    }

    // Optional blurred variants:
    public static Effect thunderStrikeBlur(double wx, double wy) {
        var a = sheet("/resources/effects/Thunder Strike/Thunderstrike w blur.png", 8, 1, 18, false);
        return new Effect(a, Effect.Space.WORLD, Effect.Blend.ADDITIVE, wx, wy);
    }
    public static Effect thunderSplashBlur(double wx, double wy) {
        var a = sheet("/resources/effects/Thunder Splash/Thunder splash w blur.png", 8, 1, 18, false);
        return new Effect(a, Effect.Space.WORLD, Effect.Blend.ADDITIVE, wx, wy);
    }

    // ----------------- Block (Shield + coin UI flip) -----------------

    /** Screen-space sparkle for a successful block/parry. */
    public static Effect shieldBlockScreen(double sx, double sy) {
        var a = sheet("/resources/effects/Block/ShieldBlock.png", 8, 1, 24, false);
        return new Effect(a, Effect.Space.SCREEN, Effect.Blend.ADDITIVE, sx, sy);
    }

    /** Gold coin flip (UI). */
    public static Effect coinFlipGold(double sx, double sy) {
        var a = sequence1Based(
                "/resources/effects/Block/Coin Flip (animation frames)/goldcoin-frame", ".png", 6, 14, false);
        return new Effect(a, Effect.Space.SCREEN, Effect.Blend.NORMAL, sx, sy);
    }

    /** Silver coin flip (UI). */
    public static Effect coinFlipSilver(double sx, double sy) {
        var a = sequence1Based(
                "/resources/effects/Block/Coin Flip (animation frames)/silvercoin-frame", ".png", 6, 14, false);
        return new Effect(a, Effect.Space.SCREEN, Effect.Blend.NORMAL, sx, sy);
    }
}
