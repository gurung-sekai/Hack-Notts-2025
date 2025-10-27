package fx;

import gfx.AnimatedSprite;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Utility for loading boss attack animations from authored frame sequences. */
public final class BossFXLibrary {
    private BossFXLibrary() {
    }

    public static FrameAnim attack(String attackId, double fps) {
        return attack(attackId, fps, false);
    }

    public static FrameAnim attack(String attackId, double fps, boolean loop) {
        String normalized = normalize(attackId);
        String base = "/resources/bosses/attacks/" + normalized;
        for (String candidate : attackDirectories(base)) {
            BufferedImage[] frames = AnimatedSprite.loadFramesFromDirectory(candidate);
            if (frames.length > 0) {
                return FrameAnim.fromFrames(frames, fps, loop);
            }
        }
        throw new IllegalStateException("Missing boss attack frames for: " + attackId);
    }

    private static String normalize(String attackId) {
        if (attackId == null || attackId.trim().isEmpty()) {
            throw new IllegalArgumentException("attackId must not be blank");
        }
        return attackId.trim().replace(' ', '_');
    }

    private static List<String> attackDirectories(String base) {
        LinkedHashSet<String> dirs = new LinkedHashSet<>();
        dirs.add(base);
        dirs.add(adjustCase(base, true));
        dirs.add(adjustCase(base, false));
        return new ArrayList<>(dirs);
    }

    private static String adjustCase(String value, boolean lowerFirst) {
        if (value.length() < 1) {
            return value;
        }
        char first = value.charAt(0);
        char adjusted = lowerFirst ? Character.toLowerCase(first) : Character.toUpperCase(first);
        if (adjusted == first) {
            return value;
        }
        return adjusted + value.substring(1);
    }
}
