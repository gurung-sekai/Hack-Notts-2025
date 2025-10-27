package util;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * Provides a shared view of the project layout so subsystems can discover assets without duplicating
 * directory-walking logic. Centralising this knowledge helps us follow the DRY principle and keeps
 * search behaviour consistent between runtime loaders and integrity checks.
 */
public final class ProjectDirectories {

    /**
     * Canonical directory name used throughout the repository.
     */
    public static final String PROJECT_ROOT_NAME = "The legend of Esran - Escape Unemployment";

    private ProjectDirectories() {
    }

    /**
     * Locate candidate roots that may contain runtime assets or source files.
     * <p>
     * The returned list is ordered from most specific to least specific and includes both the
     * current working directory and its ancestors along with their {@code src/} children. Consumers
     * can iterate over the list and stop on the first match, keeping filesystem probing predictable
     * and easy to reason about.
     * </p>
     *
     * @return ordered list of directories to examine
     */
    public static List<Path> locateSearchRoots() {
        return locateSearchRoots(PROJECT_ROOT_NAME);
    }

    /**
     * Variant of {@link #locateSearchRoots()} that allows tests to provide an alternate project
     * root name.
     */
    public static List<Path> locateSearchRoots(String projectRootName) {
        Objects.requireNonNull(projectRootName, "projectRootName");

        LinkedHashSet<Path> roots = new LinkedHashSet<>();
        roots.add(Path.of(""));
        roots.add(Path.of("src"));

        Path cwd = Paths.get("").toAbsolutePath().normalize();
        for (Path cursor = cwd; cursor != null; cursor = cursor.getParent()) {
            roots.add(cursor);
            roots.add(cursor.resolve("src"));
            Path namedProject = cursor.resolve(projectRootName);
            roots.add(namedProject);
            roots.add(namedProject.resolve("src"));
        }

        return List.copyOf(roots);
    }
}
