package util;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Lightweight helper that tries both the classpath and common filesystem roots when loading art assets.
 * Many of the development utilities in this project run the game straight from source folders, so we
 * maintain a couple of fallback locations (project/, project/src/, etc.) when the resource is not
 * packaged on the runtime classpath.
 */
public final class ResourceLoader {

    private static final List<Path> SEARCH_ROOTS = buildSearchRoots();

    private ResourceLoader() {
    }

    /**
     * Open an {@link InputStream} for the given resource path.
     *
     * @param resourcePath classpath-style path, e.g. "/resources/sprites/Hero/frame0.png"
     * @return stream for the resource, or {@code null} if not found anywhere
     * @throws IOException if a filesystem fallback exists but cannot be opened
     */
    public static InputStream open(String resourcePath) throws IOException {
        String normalized = normalize(resourcePath);

        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader != null) {
            InputStream stream = loader.getResourceAsStream(normalized);
            if (stream != null) {
                return stream;
            }
        }

        InputStream stream = ResourceLoader.class.getResourceAsStream("/" + normalized);
        if (stream != null) {
            return stream;
        }

        for (Path root : SEARCH_ROOTS) {
            Path candidate = root.resolve(normalized);
            if (Files.isRegularFile(candidate)) {
                return Files.newInputStream(candidate);
            }
        }

        return null;
    }

    /**
     * Convenience wrapper that reads a {@link BufferedImage} from the same lookup scheme as {@link #open(String)}.
     */
    public static BufferedImage image(String resourcePath) throws IOException {
        try (InputStream in = open(resourcePath)) {
            if (in == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            BufferedImage image = ImageIO.read(in);
            if (image == null) {
                throw new IOException("Unsupported image format: " + resourcePath);
            }
            return image;
        }
    }

    private static String normalize(String path) {
        Objects.requireNonNull(path, "path");
        if (path.startsWith("/")) {
            return path.substring(1);
        }
        return path;
    }

    private static List<Path> buildSearchRoots() {
        Set<Path> roots = new LinkedHashSet<>();

        // Always try relative lookups first so unit tests that set an explicit working directory succeed.
        roots.add(Path.of(""));
        roots.add(Path.of("src"));

        // Walk up from the current working directory, recording both the directory and its src child.
        Path cwd = Paths.get("").toAbsolutePath().normalize();
        for (Path cursor = cwd; cursor != null; cursor = cursor.getParent()) {
            roots.add(cursor);
            roots.add(cursor.resolve("src"));

            // If the project root lives under this directory, include it explicitly so running from submodules works.
            roots.add(cursor.resolve("The legend of Esran - Escape Unemployment"));
            roots.add(cursor.resolve("The legend of Esran - Escape Unemployment").resolve("src"));
        }

        // Materialize the ordered set into an immutable list for iteration.
        return new ArrayList<>(roots);
    }
}
