package tools;

import util.ResourceLoader;
import util.SpriteSheetSlicer;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Verification harness that slices every boss sheet (including attacks) and asserts that the automatic
 * slicer returns tight, per-sprite frames.
 */
public final class SpriteSheetSlicerVerifier {

    private static final double MAX_AUTO_SHEET_COVERAGE = 0.6d;
    private static final String BOSSES_ROOT = "/resources/bosses";

    private SpriteSheetSlicerVerifier() {
    }

    public static void main(String[] args) throws IOException {
        runVerification();
    }

    public static void runVerification() throws IOException {
        List<Expectation> expectations = loadExpectations();
        if (expectations.isEmpty()) {
            throw new IOException("No boss sprite sheets found under " + BOSSES_ROOT);
        }
        StringBuilder summary = new StringBuilder();
        for (Expectation expectation : expectations) {
            summary.append(checkSheet(expectation)).append('\n');
        }
        System.out.print(summary);
    }

    private static List<Expectation> loadExpectations() throws IOException {
        Path bossesDir = Paths.get("src", "resources", "bosses");
        if (!Files.isDirectory(bossesDir)) {
            throw new IOException("Boss resource directory not found: " + bossesDir.toAbsolutePath());
        }

        Path resourceRoot = bossesDir.getParent(); // src/resources
        List<Path> sheets = new ArrayList<>();
        try (var stream = Files.walk(bossesDir)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
                    .forEach(sheets::add);
        }

        sheets.sort(Comparator.comparing(path -> resourceRoot.relativize(path).toString().replace('\\', '/')));

        List<Expectation> expectations = new ArrayList<>(sheets.size());
        for (Path sheet : sheets) {
            Path relative = resourceRoot.relativize(sheet);
            String resourcePath = "/resources/" + relative.toString().replace('\\', '/');
            int[][] atlas = SpriteSheetSlicer.metadata(resourcePath);
            Integer expected = atlas == null ? null : atlas.length;
            expectations.add(new Expectation(resourcePath, expected));
        }
        return expectations;
    }

    private static String checkSheet(Expectation expectation) throws IOException {
        BufferedImage sheet = ResourceLoader.image(expectation.resourcePath());
        BufferedImage[] frames = SpriteSheetSlicer.slice(expectation.resourcePath(), null);
        if (frames.length == 0) {
            throw new IllegalStateException(String.format(Locale.ROOT,
                    "%s did not produce any frames", expectation.resourcePath()));
        }
        Integer expectedFrames = expectation.expectedFrames();
        if (expectedFrames != null && frames.length != expectedFrames) {
            throw new IllegalStateException(String.format(Locale.ROOT,
                    "%s should have %d frames but had %d", expectation.resourcePath(),
                    expectedFrames, frames.length));
        }

        double sheetArea = (double) sheet.getWidth() * (double) sheet.getHeight();
        for (BufferedImage frame : frames) {
            if (frame.getWidth() > sheet.getWidth()) {
                throw new IllegalStateException(String.format(Locale.ROOT,
                        "%s produced a frame wider than the sheet (%d > %d)", expectation.resourcePath(),
                        frame.getWidth(), sheet.getWidth()));
            }
            if (frame.getHeight() > sheet.getHeight()) {
                throw new IllegalStateException(String.format(Locale.ROOT,
                        "%s produced a frame taller than the sheet (%d > %d)", expectation.resourcePath(),
                        frame.getHeight(), sheet.getHeight()));
            }
            if (expectedFrames == null) {
                if (frame.getWidth() == sheet.getWidth() && frame.getHeight() == sheet.getHeight() && frames.length > 1) {
                    throw new IllegalStateException(String.format(Locale.ROOT,
                            "%s produced a frame that spans the entire sheet", expectation.resourcePath()));
                }
                double ratio = (double) frame.getWidth() * (double) frame.getHeight() / sheetArea;
                if (ratio >= MAX_AUTO_SHEET_COVERAGE && frames.length > 1) {
                    throw new IllegalStateException(String.format(Locale.ROOT,
                            "%s produced a frame covering %.2f%% of the sheet", expectation.resourcePath(), ratio * 100.0));
                }
            }
        }

        String expectedSuffix = expectedFrames == null
                ? ""
                : String.format(Locale.ROOT, " expected %d", expectedFrames);

        return String.format(Locale.ROOT, "%s -> %d frames%s (max %dx%d)", expectation.resourcePath(),
                frames.length, expectedSuffix, maxWidth(frames), maxHeight(frames));
    }

    private static int maxWidth(BufferedImage[] frames) {
        int max = 0;
        for (BufferedImage frame : frames) {
            if (frame.getWidth() > max) {
                max = frame.getWidth();
            }
        }
        return max;
    }

    private static int maxHeight(BufferedImage[] frames) {
        int max = 0;
        for (BufferedImage frame : frames) {
            if (frame.getHeight() > max) {
                max = frame.getHeight();
            }
        }
        return max;
    }

    private record Expectation(String resourcePath, Integer expectedFrames) { }
}
