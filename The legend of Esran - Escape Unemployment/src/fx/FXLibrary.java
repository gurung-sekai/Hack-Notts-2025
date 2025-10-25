package fx;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

/** Factory of ready-made effects matching your folder layout. */
public final class FXLibrary {
    private FXLibrary() {}

    private static BufferedImage load(String path) {
        try { return ImageIO.read(FXLibrary.class.getResource(path)); }
        catch (Exception e) { throw new RuntimeException("Missing FX image: " + path, e); }
    }

    // ----------------- Smoke Effects -----------------

    public static Effect smokeSmall(double wx, double wy) {
        var a = FrameAnim.fromSequence1Based(
                "/resources/effects/Smoke Effects/FX001/FX001_", ".png", 5, 12, false);
        return new Effect(a, Effect.Space.WORLD, Effect.Blend.NORMAL, wx, wy).scale(1.0);
    }

    public static Effect smokeLarge(double wx, double wy) {
        var a = FrameAnim.fromSequence1Based(
                "/resources/effects/Smoke Effects/FX002/FX002_", ".png", 8, 12, false);
        return new Effect(a, Effect.Space.WORLD, Effect.Blend.NORMAL, wx, wy).scale(1.2);
    }

    // ----------------- FireEffect -----------------

    public static Effect fireBreath(double wx, double wy, double dirX, double dirY) {
        BufferedImage sheet = load("/resources/effects/FireEffect/Fire Breath SpriteSheet.png");
        var a = FrameAnim.fromSheet(sheet, 8, 2, 16, true);
        double speed = 140;
        return new Effect(a, Effect.Space.WORLD, Effect.Blend.ADDITIVE, wx, wy)
                .vel(dirX * speed, dirY * speed)
                .life(0.8);
    }

    public static Effect fireHit(double wx, double wy) {
        BufferedImage sheet = load("/resources/effects/FireEffect/Fire Breath hit effect SpriteSheet.png");
        var a = FrameAnim.fromSheet(sheet, 6, 1, 20, false);
        return new Effect(a, Effect.Space.WORLD, Effect.Blend.ADDITIVE, wx, wy).scale(1.2);
    }

    // ----------------- Thunder -----------------

    public static Effect thunderStrike(double wx, double wy) {
        BufferedImage sheet = load("/resources/effects/Thunder Strike/Thunderstrike wo blur.png");
        var a = FrameAnim.fromSheet(sheet, 8, 1, 18, false);
        return new Effect(a, Effect.Space.WORLD, Effect.Blend.ADDITIVE, wx, wy);
    }

    public static Effect thunderSplash(double wx, double wy) {
        BufferedImage sheet = load("/resources/effects/Thunder Splash/Thunder splash wo blur.png");
        var a = FrameAnim.fromSheet(sheet, 8, 1, 18, false);
        return new Effect(a, Effect.Space.WORLD, Effect.Blend.ADDITIVE, wx, wy);
    }

    // Optional blurred variants:
    public static Effect thunderStrikeBlur(double wx, double wy) {
        BufferedImage sheet = load("/resources/effects/Thunder Strike/Thunderstrike w blur.png");
        var a = FrameAnim.fromSheet(sheet, 8, 1, 18, false);
        return new Effect(a, Effect.Space.WORLD, Effect.Blend.ADDITIVE, wx, wy);
    }
    public static Effect thunderSplashBlur(double wx, double wy) {
        BufferedImage sheet = load("/resources/effects/Thunder Splash/Thunder splash w blur.png");
        var a = FrameAnim.fromSheet(sheet, 8, 1, 18, false);
        return new Effect(a, Effect.Space.WORLD, Effect.Blend.ADDITIVE, wx, wy);
    }

    // ----------------- Block (Shield + coin UI flip) -----------------

    /** Screen-space sparkle for a successful block/parry. */
    public static Effect shieldBlockScreen(double sx, double sy) {
        BufferedImage sheet = load("/resources/effects/Block/ShieldBlock.png");
        var a = FrameAnim.fromSheet(sheet, 8, 1, 24, false);
        return new Effect(a, Effect.Space.SCREEN, Effect.Blend.ADDITIVE, sx, sy);
    }

    /** Gold coin flip (UI). */
    public static Effect coinFlipGold(double sx, double sy) {
        var a = FrameAnim.fromSequence1Based(
                "/resources/effects/Block/Coin Flip (animation frames)/goldcoin-frame", ".png", 6, 14, false);
        return new Effect(a, Effect.Space.SCREEN, Effect.Blend.NORMAL, sx, sy);
    }

    /** Silver coin flip (UI). */
    public static Effect coinFlipSilver(double sx, double sy) {
        var a = FrameAnim.fromSequence1Based(
                "/resources/effects/Block/Coin Flip (animation frames)/silvercoin-frame", ".png", 6, 14, false);
        return new Effect(a, Effect.Space.SCREEN, Effect.Blend.NORMAL, sx, sy);
    }
}
