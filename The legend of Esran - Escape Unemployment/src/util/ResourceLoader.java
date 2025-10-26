package util;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Lightweight helper that tries both the classpath and common filesystem roots when loading art assets.
 * Many of the development utilities in this project run the game straight from source folders, so we
 * maintain a couple of fallback locations (project/, project/src/, etc.) when the resource is not
 * packaged on the runtime classpath.
 */
public final class ResourceLoader {

    private static final List<Path> SEARCH_ROOTS = List.of(
            Path.of(""),
            Path.of("The legend of Esran - Escape Unemployment"),
            Path.of("The legend of Esran - Escape Unemployment", "src"));

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
            if (Files.exists(candidate)) {
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
}
