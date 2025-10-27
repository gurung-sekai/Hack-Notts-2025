package fx;

import gfx.AnimatedSprite;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Utility for loading boss attack animations from authored frame sequences. */
public final class BossFXLibrary {
    private BossFXLibrary() {
    }

    private static final Map<String, BufferedImage[]> ATTACK_CACHE = new LinkedHashMap<>();

    public static FrameAnim attack(String attackId, double fps) {
        return attack(attackId, fps, false);
    }

    public static FrameAnim attack(String attackId, double fps, boolean loop) {
        String normalized = normalize(attackId);
        BufferedImage[] frames = loadFrames(normalized);
        if (frames.length == 0) {
            throw new IllegalStateException("Missing boss attack frames for: " + attackId);
        }
        return FrameAnim.fromFrames(frames, fps, loop);
    }

    /**
     * Expose the cached attack frames so callers can pre-warm scaling caches without duplicating assets.
     */
    public static BufferedImage[] attackFrames(String attackId) {
        String normalized = normalize(attackId);
        BufferedImage[] frames = loadFrames(normalized);
        return frames.clone();
    }

    private static String normalize(String attackId) {
        if (attackId == null || attackId.trim().isEmpty()) {
            throw new IllegalArgumentException("attackId must not be blank");
        }
        return attackId.trim().replace(' ', '_');
    }

    private static BufferedImage[] loadFrames(String normalizedAttackId) {
        synchronized (ATTACK_CACHE) {
            BufferedImage[] cached = ATTACK_CACHE.get(normalizedAttackId);
            if (cached != null) {
                return cached;
            }
            String base = "/resources/bosses/attacks/" + normalizedAttackId;
            for (String candidate : attackDirectories(base)) {
                BufferedImage[] frames = AnimatedSprite.loadFramesFromDirectory(candidate);
                if (frames.length > 0) {
                    BufferedImage[] aligned = AnimatedSprite.normaliseFrames(frames);
                    BufferedImage[] stored = (aligned != null) ? aligned : frames;
                    ATTACK_CACHE.put(normalizedAttackId, stored);
                    trimCache();
                    return stored;
                }
            }
            ATTACK_CACHE.put(normalizedAttackId, new BufferedImage[0]);
            trimCache();
            return new BufferedImage[0];
        }
    }

    private static void trimCache() {
        final int maxEntries = 64;
        if (ATTACK_CACHE.size() <= maxEntries) {
            return;
        }
        int excess = ATTACK_CACHE.size() - maxEntries;
        Iterator<Map.Entry<String, BufferedImage[]>> it = ATTACK_CACHE.entrySet().iterator();
        while (excess-- > 0 && it.hasNext()) {
            it.next();
            it.remove();
        }
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
