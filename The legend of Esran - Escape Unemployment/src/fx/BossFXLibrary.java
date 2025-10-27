package fx;

import gfx.AnimatedSprite;
import util.SpriteSheetSlicer;
import util.SpriteSplitLocator;

import java.awt.image.BufferedImage;
import java.io.IOException;

/** Utility for loading boss attack animations without storing split PNGs in git. */
public final class BossFXLibrary {
    private static final SpriteSheetSlicer.Options ATTACK_OPTIONS = SpriteSheetSlicer.Options.DEFAULT
            .withPadding(1)
            .withAlphaThreshold(10)
            .withMinFrameArea(96)
            .withJoinGap(2);

    private BossFXLibrary() {
    }

    public static FrameAnim attack(String attackId, double fps) {
        return attack(attackId, fps, false);
    }

    public static FrameAnim attack(String attackId, double fps, boolean loop) {
        String normalized = normalize(attackId);
        String resourcePath = "/resources/bosses/attacks/" + normalized + ".png";
        for (String prefix : SpriteSplitLocator.candidates(resourcePath)) {
            BufferedImage[] frames = AnimatedSprite.loadFramesFromPrefix(prefix);
            if (frames.length > 0) {
                return FrameAnim.fromFrames(frames, fps, loop);
            }
        }
        try {
            BufferedImage[] frames = SpriteSheetSlicer.slice(resourcePath, ATTACK_OPTIONS);
            return FrameAnim.fromFrames(frames, fps, loop);
        } catch (IOException e) {
            throw new RuntimeException("Missing boss attack sheet: " + attackId, e);
        }
    }

    private static String normalize(String attackId) {
        if (attackId == null || attackId.trim().isEmpty()) {
            throw new IllegalArgumentException("attackId must not be blank");
        }
        return attackId.trim().replace(' ', '_');
    }
}
