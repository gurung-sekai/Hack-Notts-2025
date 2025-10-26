package security;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Simple runtime integrity checks and secure randomness helpers.
 * Intended to provide a lightweight guard when distributing the game on storefronts.
 */
public final class GameSecurity {

    private static final Logger LOGGER = Logger.getLogger(GameSecurity.class.getName());
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final HexFormat HEX_FORMAT = HexFormat.of();
    private static final Map<Path, String> EXPECTED_HASHES;
    private static volatile boolean verified;

    static {
        Map<Path, String> hashes = new LinkedHashMap<>();
        hashes.put(Path.of("src", "World", "DungeonRooms.java"),
                "b5bf31175fffca5df924f40ea1e1c9fcc3f1cacd8d93fcd18e1a84723b23590e");
        hashes.put(Path.of("src", "Battle", "scene", "BossBattlePanel.java"),
                "494e3898875792aed2f2535e587d39063d92fca3c6dff2514309ff8a31da8944");
        EXPECTED_HASHES = Collections.unmodifiableMap(hashes);
    }

    private GameSecurity() {
    }

    /**
     * Ensure the critical files have not been tampered with. Logs a warning if verification fails.
     */
    public static synchronized void verifyIntegrity() {
        if (verified) {
            return;
        }
        EXPECTED_HASHES.forEach(GameSecurity::verifyFile);
        verified = true;
    }

    private static void verifyFile(Path file, String expectedHash) {
        try {
            Optional<Path> candidate = locateExistingFile(file);
            if (candidate.isEmpty()) {
                LOGGER.log(Level.WARNING, "GameSecurity: file not found for integrity check: {0}", file);
                return;
            }

            byte[] data = Files.readAllBytes(candidate.get());
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String actual = HEX_FORMAT.formatHex(digest.digest(data));
            if (!actual.equalsIgnoreCase(expectedHash)) {
                LOGGER.log(Level.WARNING, "GameSecurity: integrity mismatch for {0}", candidate.get());
            } else {
                LOGGER.log(Level.FINE, "GameSecurity: verified {0}", candidate.get());
            }
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "GameSecurity: unable to verify " + file, ex);
        } catch (NoSuchAlgorithmException ex) {
            LOGGER.log(Level.SEVERE, "GameSecurity: SHA-256 algorithm missing", ex);
        }
    }

    private static Optional<Path> locateExistingFile(Path relativePath) {
        Objects.requireNonNull(relativePath, "relativePath");
        List<Path> searchRoots = IntegrityRootLocator.ROOTS;
        for (Path root : searchRoots) {
            Path candidate = root.resolve(relativePath);
            if (Files.isRegularFile(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    /**
     * Shared secure random instance for security-sensitive decisions (boss selection, etc.).
     */
    public static SecureRandom secureRandom() {
        return SECURE_RANDOM;
    }

    /**
     * Generate a random session token that can be used by external services.
     * <p>
     * Tokens are short-lived, non-identifying references which aligns with the UK GDPR and
     * Data Protection Act 2018 principle of data minimisation by avoiding any use of
     * personal data in authentication hand-offs.
     * </p>
     */
    public static String newSessionToken() {
        byte[] buffer = new byte[16];
        SECURE_RANDOM.nextBytes(buffer);
        return HEX_FORMAT.formatHex(buffer);
    }

    private static final class IntegrityRootLocator {
        private static final String PROJECT_ROOT_NAME = "The legend of Esran - Escape Unemployment";
        private static final List<Path> ROOTS = buildRoots();

        private IntegrityRootLocator() {
        }

        private static List<Path> buildRoots() {
            Path cwd = Paths.get("").toAbsolutePath().normalize();
            LinkedHashSet<Path> orderedRoots = new LinkedHashSet<>();
            for (Path cursor = cwd; cursor != null; cursor = cursor.getParent()) {
                orderedRoots.add(cursor);
                orderedRoots.add(cursor.resolve(PROJECT_ROOT_NAME));
            }
            orderedRoots.add(cwd.resolve(PROJECT_ROOT_NAME));
            return List.copyOf(orderedRoots);
        }
    }
}
