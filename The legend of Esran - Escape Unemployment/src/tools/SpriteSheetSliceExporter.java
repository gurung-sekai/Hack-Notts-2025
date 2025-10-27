package tools;

import util.SpriteSheetSlicer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Locale;

/**
 * Utility that exports every automatically sliced boss sprite frame to disk so artists can review the output
 * without instrumenting the game runtime.
 */
public final class SpriteSheetSliceExporter {

    private SpriteSheetSliceExporter() {
    }

    public static void main(String[] args) throws IOException {
        Path outputRoot = args.length > 0 ? Path.of(args[0]) : Path.of("build", "slicerPreview");
        export(outputRoot);
    }

    public static void export(Path outputRoot) throws IOException {
        if (outputRoot == null) {
            throw new IllegalArgumentException("outputRoot");
        }

        List<SpriteSheetSlicerVerifier.SheetSpec> sheets = SpriteSheetSlicerVerifier.listBossSheets();
        if (sheets.isEmpty()) {
            throw new IOException("No boss sprite sheets discovered under /resources/bosses");
        }

        cleanDirectory(outputRoot);
        Files.createDirectories(outputRoot);

        StringBuilder summary = new StringBuilder();
        for (SpriteSheetSlicerVerifier.SheetSpec spec : sheets) {
            BufferedImage[] frames = SpriteSheetSlicer.slice(spec.resourcePath(), null);
            Path sheetRoot = outputRoot.resolve(sheetDirectory(spec.relativePath()));
            Files.createDirectories(sheetRoot);
            summary.append(writeFrames(outputRoot, sheetRoot, frames, spec.relativePath()));
        }

        System.out.print(summary);
    }

    private static Path sheetDirectory(String relativePath) {
        String normalized = relativePath.replace('\\', '/');
        int lastSlash = normalized.lastIndexOf('/');
        int lastDot = normalized.lastIndexOf('.');
        String base = (lastDot > lastSlash) ? normalized.substring(0, lastDot) : normalized;
        return Path.of(base.replace('/', java.io.File.separatorChar));
    }

    private static String writeFrames(Path outputRoot, Path sheetRoot, BufferedImage[] frames, String relativePath) throws IOException {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < frames.length; index++) {
            BufferedImage frame = frames[index];
            Path file = sheetRoot.resolve(String.format(Locale.ROOT, "%03d.png", index));
            ImageIO.write(frame, "png", file.toFile());
            builder.append(String.format(Locale.ROOT,
                    "%s -> %s (%dx%d)%n",
                    relativePath,
                    outputRelativePath(outputRoot, file),
                    frame.getWidth(),
                    frame.getHeight()));
        }
        return builder.toString();
    }

    private static String outputRelativePath(Path outputRoot, Path file) {
        Path relative = outputRoot.relativize(file);
        return relative.toString().replace('\\', '/');
    }

    private static void cleanDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        Files.walkFileTree(directory, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
