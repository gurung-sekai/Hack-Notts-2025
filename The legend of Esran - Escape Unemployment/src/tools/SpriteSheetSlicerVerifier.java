package tools;

import util.ResourceLoader;
import util.SpriteSheetSlicer;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Locale;

/**
 * Simple verification harness that slices a handful of historically-problematic boss sheets and asserts
 * that the automatic slicer returns tight, per-sprite frames.
 */
public final class SpriteSheetSlicerVerifier {

    private static final Expectation[] EXPECTATIONS = new Expectation[] {
            new Expectation("/resources/bosses/theWelch.png", 10),
            new Expectation("/resources/bosses/toxicTree.png", 28),
            new Expectation("/resources/bosses/goldMech.png", 10),
            new Expectation("/resources/bosses/attacks/theWelchAttack1.png", 23)
    };

    private SpriteSheetSlicerVerifier() {
    }

    public static void main(String[] args) throws IOException {
        runVerification();
    }

    public static void runVerification() throws IOException {
        StringBuilder summary = new StringBuilder();
        for (Expectation expectation : EXPECTATIONS) {
            summary.append(checkSheet(expectation)).append('\n');
        }
        System.out.print(summary);
    }

    private static String checkSheet(Expectation expectation) throws IOException {
        BufferedImage sheet = ResourceLoader.image(expectation.resourcePath());
        BufferedImage[] frames = SpriteSheetSlicer.slice(expectation.resourcePath(), null);
        if (frames.length != expectation.expectedFrames()) {
            throw new IllegalStateException(String.format(Locale.ROOT,
                    "%s should have %d frames but had %d", expectation.resourcePath(),
                    expectation.expectedFrames(), frames.length));
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
            if (frame.getWidth() == sheet.getWidth() && frame.getHeight() == sheet.getHeight()) {
                throw new IllegalStateException(String.format(Locale.ROOT,
                        "%s produced a frame that spans the entire sheet", expectation.resourcePath()));
            }
            double ratio = (double) frame.getWidth() * (double) frame.getHeight() / sheetArea;
            if (ratio >= 0.6d) {
                throw new IllegalStateException(String.format(Locale.ROOT,
                        "%s produced a frame covering %.2f%% of the sheet", expectation.resourcePath(), ratio * 100.0));
            }
        }

        return String.format(Locale.ROOT, "%s -> %d frames (max %dx%d)", expectation.resourcePath(),
                frames.length, maxWidth(frames), maxHeight(frames));
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

    private record Expectation(String resourcePath, int expectedFrames) { }
}
