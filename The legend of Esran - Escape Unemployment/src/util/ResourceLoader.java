package util;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Lightweight helper that tries both the classpath and common filesystem roots when loading art assets.
 * Many of the development utilities in this project run the game straight from source folders, so we
 * maintain a couple of fallback locations (project/, project/src/, etc.) when the resource is not
 * packaged on the runtime classpath.
 */
public final class ResourceLoader {

    static {
        // Avoid ImageIO leaking decompressed sprite data to temporary files on disk which can
        // reveal asset contents or exhaust storage when loading large boss animations.
        ImageIO.setUseCache(false);
    }

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
        String normalized;
        try {
            normalized = normalize(resourcePath);
        } catch (IllegalArgumentException ex) {
            throw new IOException("Invalid resource path: " + resourcePath, ex);
        }

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

    /**
     * Enumerate resources that live directly under the supplied directory.
     * <p>
     * The lookup mirrors {@link #open(String)} by considering both the runtime classpath and the filesystem
     * search roots used during development.  Returned paths are normalised with a leading slash so the caller can
     * pass them straight back into {@link #open(String)} or {@link #image(String)}.
     *
     * @param resourceDirectory classpath-style directory path (e.g. {@code /resources/bosses/Gollum})
     * @param filter predicate that receives the normalised file name (without any path prefixes) and decides whether
     *               it should be included in the results
     * @return ordered list of resource paths relative to the classpath root
     */
    public static List<String> list(String resourceDirectory, Predicate<String> filter) {
        Objects.requireNonNull(filter, "filter");
        String normalizedDir = normalizeDirectory(resourceDirectory);
        String normalizedDirNoSlash = normalizedDir.endsWith("/")
                ? normalizedDir.substring(0, normalizedDir.length() - 1)
                : normalizedDir;

        Set<String> resources = new TreeSet<>();

        collectFromClassLoader(resources, normalizedDir, filter);
        collectFromClassLoader(resources, normalizedDirNoSlash, filter);
        collectFromSearchRoots(resources, normalizedDirNoSlash, filter);

        return new ArrayList<>(resources);
    }

    /**
     * Enumerate PNG resources that live directly under the supplied directory.
     */
    public static List<String> listPng(String resourceDirectory) {
        return list(resourceDirectory, name -> name.toLowerCase(Locale.ROOT).endsWith(".png"));
    }

    /**
     * Enumerate directories that live directly under the supplied resource path.
     */
    public static List<String> listDirectories(String resourceDirectory) {
        String normalizedDir = normalizeDirectory(resourceDirectory);
        String normalizedDirNoSlash = normalizedDir.endsWith("/")
                ? normalizedDir.substring(0, normalizedDir.length() - 1)
                : normalizedDir;

        Set<String> directories = new TreeSet<>();

        collectDirectoriesFromClassLoader(directories, normalizedDir);
        collectDirectoriesFromClassLoader(directories, normalizedDirNoSlash);
        collectDirectoriesFromSearchRoots(directories, normalizedDirNoSlash);

        return new ArrayList<>(directories);
    }

    private static String normalize(String path) {
        Objects.requireNonNull(path, "path");
        String trimmed = path.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("path must not be blank");
        }

        String withoutLeadingSlash = trimmed.startsWith("/") ? trimmed.substring(1) : trimmed;
        if (withoutLeadingSlash.indexOf(':') >= 0) {
            throw new IllegalArgumentException("path may not contain drive designators");
        }
        Path candidate = Paths.get(withoutLeadingSlash).normalize();
        for (Path element : candidate) {
            if ("..".equals(element.toString())) {
                throw new IllegalArgumentException("path may not contain parent directory navigation");
            }
        }
        if (candidate.isAbsolute()) {
            throw new IllegalArgumentException("path must be relative");
        }
        // Normalise separators so classpath lookup remains stable and directory traversal is prevented in line with
        // UK National Cyber Security Centre (NCSC) platform security guidance.
        return candidate.toString().replace('\\', '/');
    }

    private static String normalizeDirectory(String path) {
        String normalized = normalize(path);
        return normalized.endsWith("/") ? normalized : normalized + "/";
    }

    private static void collectFromClassLoader(Set<String> resources, String normalizedDir, Predicate<String> filter) {
        if (normalizedDir.isEmpty()) {
            return;
        }
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        ClassLoader fallback = ResourceLoader.class.getClassLoader();
        collectFromLoader(resources, context, normalizedDir, filter);
        if (fallback != context) {
            collectFromLoader(resources, fallback, normalizedDir, filter);
        }
    }

    private static void collectDirectoriesFromClassLoader(Set<String> directories, String normalizedDir) {
        if (normalizedDir.isEmpty()) {
            return;
        }
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        ClassLoader fallback = ResourceLoader.class.getClassLoader();
        collectDirectoriesFromLoader(directories, context, normalizedDir);
        if (fallback != context) {
            collectDirectoriesFromLoader(directories, fallback, normalizedDir);
        }
    }

    private static void collectFromLoader(Set<String> resources, ClassLoader loader, String normalizedDir,
                                          Predicate<String> filter) {
        if (loader == null) {
            return;
        }
        try {
            Enumeration<URL> urls = loader.getResources(normalizedDir);
            while (urls.hasMoreElements()) {
                URL url = urls.nextElement();
                collectFromUrl(resources, normalizedDir, url, filter);
            }
        } catch (IOException ignored) {
            // Fallback to filesystem search roots if the classloader cannot enumerate entries.
        }
    }

    private static void collectDirectoriesFromLoader(Set<String> directories, ClassLoader loader, String normalizedDir) {
        if (loader == null) {
            return;
        }
        try {
            Enumeration<URL> urls = loader.getResources(normalizedDir);
            while (urls.hasMoreElements()) {
                URL url = urls.nextElement();
                collectDirectoriesFromUrl(directories, normalizedDir, url);
            }
        } catch (IOException ignored) {
            // Ignore loader enumeration failures and fall back to filesystem search roots.
        }
    }

    private static void collectFromUrl(Set<String> resources, String normalizedDir, URL url,
                                       Predicate<String> filter) {
        String protocol = url.getProtocol();
        if ("file".equals(protocol)) {
            try {
                Path dir = Paths.get(url.toURI());
                if (Files.isDirectory(dir)) {
                    try (var stream = Files.list(dir)) {
                        stream.filter(Files::isRegularFile)
                                .forEach(path -> addIfMatches(resources, normalizedDir, path.getFileName().toString(), filter));
                    }
                }
            } catch (IOException | URISyntaxException ignored) {
                // Ignore and fall back to explicit search roots.
            }
        } else if ("jar".equals(protocol)) {
            try {
                JarURLConnection connection = (JarURLConnection) url.openConnection();
                try (JarFile jar = connection.getJarFile()) {
                    String entryPrefix = connection.getEntryName();
                    String normalizedPrefix = (entryPrefix == null || entryPrefix.isBlank())
                            ? normalizedDir
                            : (entryPrefix.endsWith("/") ? entryPrefix : entryPrefix + "/");
                    jar.stream()
                            .filter(entry -> !entry.isDirectory())
                            .forEach(entry -> addJarEntry(resources, normalizedPrefix, entry, filter));
                }
            } catch (IOException ignored) {
                // Ignore jar enumeration failures.
            }
        }
    }

    private static void collectDirectoriesFromUrl(Set<String> directories, String normalizedDir, URL url) {
        String protocol = url.getProtocol();
        if ("file".equals(protocol)) {
            try {
                Path dir = Paths.get(url.toURI());
                if (Files.isDirectory(dir)) {
                    try (var stream = Files.list(dir)) {
                        stream.filter(Files::isDirectory)
                                .forEach(path -> addDirectory(directories, normalizedDir, path.getFileName().toString()));
                    }
                }
            } catch (IOException | URISyntaxException ignored) {
                // Ignore filesystem enumeration failures.
            }
        } else if ("jar".equals(protocol)) {
            try {
                JarURLConnection connection = (JarURLConnection) url.openConnection();
                try (JarFile jar = connection.getJarFile()) {
                    String entryPrefix = connection.getEntryName();
                    String normalizedPrefix = (entryPrefix == null || entryPrefix.isBlank())
                            ? normalizedDir
                            : (entryPrefix.endsWith("/") ? entryPrefix : entryPrefix + "/");
                    jar.stream()
                            .filter(JarEntry::isDirectory)
                            .forEach(entry -> addJarDirectory(directories, normalizedPrefix, entry));
                }
            } catch (IOException ignored) {
                // Ignore jar enumeration failures.
            }
        }
    }

    private static void addJarEntry(Set<String> resources, String normalizedPrefix, JarEntry entry,
                                    Predicate<String> filter) {
        String name = entry.getName();
        if (!name.startsWith(normalizedPrefix)) {
            return;
        }
        String remainder = name.substring(normalizedPrefix.length());
        if (remainder.isEmpty() || remainder.contains("/")) {
            return;
        }
        if (filter.test(remainder)) {
            resources.add("/" + name);
        }
    }

    private static void addJarDirectory(Set<String> directories, String normalizedPrefix, JarEntry entry) {
        String name = entry.getName();
        if (!name.startsWith(normalizedPrefix)) {
            return;
        }
        String remainder = name.substring(normalizedPrefix.length());
        if (remainder.isEmpty()) {
            return;
        }
        if (remainder.endsWith("/")) {
            remainder = remainder.substring(0, remainder.length() - 1);
        }
        if (remainder.contains("/")) {
            return;
        }
        directories.add("/" + normalizedPrefix + remainder);
    }

    private static void collectFromSearchRoots(Set<String> resources, String normalizedDir,
                                               Predicate<String> filter) {
        for (Path root : SEARCH_ROOTS) {
            Path dir = root.resolve(normalizedDir);
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (var stream = Files.list(dir)) {
                stream.filter(Files::isRegularFile)
                        .forEach(path -> addIfMatches(resources, normalizedDir, path.getFileName().toString(), filter));
            } catch (IOException ignored) {
                // Ignore directories that vanish mid-enumeration.
            }
        }
    }

    private static void collectDirectoriesFromSearchRoots(Set<String> directories, String normalizedDir) {
        for (Path root : SEARCH_ROOTS) {
            Path dir = root.resolve(normalizedDir);
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (var stream = Files.list(dir)) {
                stream.filter(Files::isDirectory)
                        .forEach(path -> addDirectory(directories, normalizedDir, path.getFileName().toString()));
            } catch (IOException ignored) {
                // Ignore directories that vanish mid-enumeration.
            }
        }
    }

    private static void addIfMatches(Set<String> resources, String normalizedDir, String filename,
                                     Predicate<String> filter) {
        if (!filter.test(filename)) {
            return;
        }
        String base = normalizedDir.endsWith("/") ? normalizedDir : normalizedDir + "/";
        resources.add("/" + base + filename);
    }

    private static void addDirectory(Set<String> directories, String normalizedDir, String directoryName) {
        if (directoryName == null || directoryName.isEmpty()) {
            return;
        }
        String base = normalizedDir.endsWith("/") ? normalizedDir : normalizedDir + "/";
        directories.add("/" + base + directoryName);
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
