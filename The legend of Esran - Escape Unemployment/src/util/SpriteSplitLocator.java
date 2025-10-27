package util;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Helper that derives likely prefix locations for pre-split sprite frames based on an original sheet path.
 * The game historically sliced boss sheets at runtime; contributors may now provide dedicated frame PNGs
 * laid out using a variety of folder conventions.  This helper enumerates common patterns so the runtime
 * can load the authored frames directly before falling back to the automatic slicer.
 */
public final class SpriteSplitLocator {

    private SpriteSplitLocator() {
    }

    public static List<String> candidates(String resourcePath) {
        if (resourcePath == null || resourcePath.isBlank()) {
            return List.of();
        }

        String trimmed = stripExtension(resourcePath.trim());
        int lastSlash = trimmed.lastIndexOf('/');
        if (lastSlash < 0) {
            return List.of(trimmed + "_");
        }

        String parent = trimmed.substring(0, lastSlash);
        String base = trimmed.substring(lastSlash + 1);
        String capitalized = capitalize(base);

        Set<String> prefixes = new LinkedHashSet<>();

        add(prefixes, trimmed + "_");
        add(prefixes, trimmed + "-");
        add(prefixes, trimmed + "/frame_");
        add(prefixes, trimmed + "/Frame_");
        add(prefixes, parent + "/" + capitalized + "_");
        add(prefixes, parent + "/" + capitalized + "-");

        add(prefixes, parent + "/" + base + "/" + base + "_");
        add(prefixes, parent + "/" + base + "/" + base + "-");
        add(prefixes, parent + "/" + base + "/frame_");
        add(prefixes, parent + "/" + base + "/Frame_");
        add(prefixes, parent + "/" + base + "/" + capitalized + "_");
        add(prefixes, parent + "/" + base + "/Idle/" + base + "_");
        add(prefixes, parent + "/" + base + "/idle/" + base + "_");
        add(prefixes, parent + "/" + base + "/Idle/frame_");
        add(prefixes, parent + "/" + base + "/idle/frame_");

        add(prefixes, parent + "/" + capitalized + "/" + base + "_");
        add(prefixes, parent + "/" + capitalized + "/" + base + "-");
        add(prefixes, parent + "/" + capitalized + "/" + capitalized + "_");
        add(prefixes, parent + "/" + capitalized + "/frame_");
        add(prefixes, parent + "/" + capitalized + "/Frame_");
        add(prefixes, parent + "/" + capitalized + "/Idle/" + base + "_");
        add(prefixes, parent + "/" + capitalized + "/idle/" + base + "_");
        add(prefixes, parent + "/" + capitalized + "/Idle/frame_");
        add(prefixes, parent + "/" + capitalized + "/idle/frame_");

        return new ArrayList<>(prefixes);
    }

    private static void add(Set<String> target, String candidate) {
        if (candidate != null && !candidate.isBlank()) {
            target.add(candidate);
        }
    }

    private static String stripExtension(String path) {
        if (path.endsWith(".png") || path.endsWith(".PNG")) {
            return path.substring(0, path.length() - 4);
        }
        return path;
    }

    private static String capitalize(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        if (value.length() == 1) {
            return value.toUpperCase();
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}

