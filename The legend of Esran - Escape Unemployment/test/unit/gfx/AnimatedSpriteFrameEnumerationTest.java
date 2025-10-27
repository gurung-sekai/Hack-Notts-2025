package unit.gfx;

import gfx.AnimatedSprite;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public final class AnimatedSpriteFrameEnumerationTest {

    public static void main(String[] args) {
        AnimatedSprite sprite = new AnimatedSprite(0, 0);
        BufferedImage idleFrameA = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        BufferedImage idleFrameB = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        BufferedImage runFrame = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);

        sprite.add(AnimatedSprite.State.IDLE, new BufferedImage[]{idleFrameA, idleFrameB});
        sprite.add(AnimatedSprite.State.RUN, new BufferedImage[]{runFrame});

        List<BufferedImage> frames = new ArrayList<>();
        sprite.forEachFrame(frames::add);

        if (frames.size() != 3) {
            throw new AssertionError("Expected 3 frames but saw " + frames.size());
        }
        if (!frames.contains(idleFrameA) || !frames.contains(idleFrameB) || !frames.contains(runFrame)) {
            throw new AssertionError("Enumerated frames did not include all expected entries");
        }

        System.out.println("AnimatedSpriteFrameEnumerationTest passed");
    }
}
