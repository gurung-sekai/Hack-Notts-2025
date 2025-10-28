package World.gfx;

import util.ResourceLoader;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Resolves character idle animations from the resources folder at runtime.
 * The loader scans a set of candidate directories, caches successful loads and
 * emits a warning the first time an expected skin cannot be found so missing
 * assets are easy to diagnose.
 */
public final class CharacterSkinLibrary {

    private static final Map<String, BufferedImage[]> CACHE = new ConcurrentHashMap<>();
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private CharacterSkinLibrary() {
    }

    /**
     * Attempt to load an idle animation for the supplied cache key.
     *
     * @param cacheKey   logical identifier used to cache the loaded frames (e.g. "knight" or "princess")
     * @param fallback   supplier used when no assets can be located; may return {@code null}
     * @param directories list of classpath-style directories or individual image files to probe
     * @return the resolved frames (never {@code null}; may be empty when both lookup and fallback fail)
     */
    public static BufferedImage[] loadIdleAnimation(String cacheKey,
                                                    Supplier<BufferedImage[]> fallback,
                                                    String... directories) {
        String normalizedKey = normalize(cacheKey);
        return CACHE.computeIfAbsent(normalizedKey, key -> {
            BufferedImage[] resolved = loadFramesFromDirectories(directories);
            if (resolved != null && resolved.length > 0) {
                return resolved;
            }
            warnMissing(key, directories);
            BufferedImage[] fallbackFrames = fallback == null ? null : fallback.get();
            return fallbackFrames == null ? new BufferedImage[0] : fallbackFrames;
        });
    }

    /**
     * Clears any cached animations. Useful when the caller wants to reload skins after adding art assets.
     */
    public static void clear() {
        CACHE.clear();
    }

    private static String normalize(String key) {
        if (key == null) {
            return "";
        }
        return key.trim().toLowerCase(Locale.ENGLISH);
    }

    private static BufferedImage[] loadFramesFromDirectories(String... directories) {
        if (directories == null || directories.length == 0) {
            return null;
        }
        for (String directory : directories) {
            if (directory == null || directory.isBlank()) {
                continue;
            }
            for (String candidate : expandCandidates(directory)) {
                BufferedImage[] frames = loadFrames(candidate);
                if (frames != null && frames.length > 0) {
                    return frames;
                }
            }
        }
        return null;
    }

    private static List<String> expandCandidates(String directory) {
        String normalized = ensureLeadingSlash(directory);
        if (normalized.toLowerCase(Locale.ENGLISH).endsWith(".png")) {
            return List.of(normalized);
        }

        String trimmed = normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
        List<String> results = new ArrayList<>();
        results.add(trimmed);
        results.add(trimmed + "/Idle");
        results.add(trimmed + "/idle");
        results.add(trimmed + "/IDLE");
        return results;
    }

    private static String ensureLeadingSlash(String path) {
        String trimmed = path.trim();
        return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
    }

    private static BufferedImage[] loadFrames(String candidate) {
        if (candidate.toLowerCase(Locale.ENGLISH).endsWith(".png")) {
            try {
                BufferedImage single = ResourceLoader.image(candidate);
                return single == null ? null : new BufferedImage[]{single};
            } catch (IOException ex) {
                System.err.println("CharacterSkinLibrary: Failed to load frame " + candidate + " -> " + ex.getMessage());
                return null;
            }
        }

        List<String> pngs = ResourceLoader.listPng(candidate);
        if (pngs.isEmpty()) {
            return null;
        }
        pngs.sort((a, b) -> {
            int cmp = Integer.compare(frameIndex(a), frameIndex(b));
            if (cmp != 0) {
                return cmp;
            }
            return a.compareTo(b);
        });

        List<BufferedImage> frames = new ArrayList<>(pngs.size());
        for (String path : pngs) {
            try {
                BufferedImage frame = ResourceLoader.image(path);
                if (frame != null) {
                    frames.add(frame);
                }
            } catch (IOException ex) {
                System.err.println("CharacterSkinLibrary: Failed to load frame " + path + " -> " + ex.getMessage());
            }
        }
        return frames.isEmpty() ? null : frames.toArray(new BufferedImage[0]);
    }

    private static int frameIndex(String path) {
        if (path == null) {
            return 0;
        }
        String lower = path.toLowerCase(Locale.ENGLISH);
        int pngIndex = lower.lastIndexOf('.');
        String digits = pngIndex >= 0 ? lower.substring(0, pngIndex) : lower;
        int lastDigit = digits.length() - 1;
        while (lastDigit >= 0 && !Character.isDigit(digits.charAt(lastDigit))) {
            lastDigit--;
        }
        if (lastDigit < 0) {
            return 0;
        }
        int firstDigit = lastDigit;
        while (firstDigit > 0 && Character.isDigit(digits.charAt(firstDigit - 1))) {
            firstDigit--;
        }
        try {
            return Integer.parseInt(digits.substring(firstDigit, lastDigit + 1));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static void warnMissing(String key, String... directories) {
        if (!WARNED.add(key)) {
            return;
        }
        String detail = directories == null || directories.length == 0
                ? ""
                : " (searched " + Arrays.toString(Arrays.stream(directories)
                .filter(Objects::nonNull)
                .map(String::trim)
                .toArray(String[]::new)) + ")";
        System.err.println("CharacterSkinLibrary: Missing idle skin for '" + key + "'" + detail);
    }
}
