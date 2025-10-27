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
    private static final String MANIFEST_RESOURCE = "/security/integrity.manifest";
    private static final String EXPECTED_MANIFEST_HASH = "bcff6009580dbae53546dbf994fa9e36b08ffe48caaf67842bbdae18d78d3987";
    private static final IntegrityManifest MANIFEST = IntegrityManifest.load(MANIFEST_RESOURCE);
    private static final Map<Path, String> EXPECTED_HASHES =
            Collections.unmodifiableMap(new LinkedHashMap<>(MANIFEST.entries()));
    private static final boolean MANIFEST_TRUSTED = validateManifestDigest();
    private static volatile boolean verified;

    private GameSecurity() {
    }

    /**
     * Ensure the critical files have not been tampered with. Logs a warning if verification fails.
     */
    public static synchronized void verifyIntegrity() {
        if (verified) {
            return;
        }
        if (!MANIFEST_TRUSTED) {
            LOGGER.log(Level.WARNING, "GameSecurity: manifest trust not established; integrity checks skipped");
            verified = true;
            return;
        }
        if (EXPECTED_HASHES.isEmpty()) {
            LOGGER.log(Level.WARNING, "GameSecurity: integrity manifest contained no entries; checks skipped");
            verified = true;
            return;
        }
        EXPECTED_HASHES.forEach(GameSecurity::verifyFile);
        verified = true;
    }

    private static boolean validateManifestDigest() {
        if (MANIFEST.isEmpty()) {
            LOGGER.log(Level.WARNING, "GameSecurity: integrity manifest missing; runtime checks disabled");
            return false;
        }
        if (EXPECTED_MANIFEST_HASH == null || EXPECTED_MANIFEST_HASH.isBlank()) {
            LOGGER.log(Level.INFO, "GameSecurity: manifest digest not pinned; continuing without tamper detection");
            return true;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] expected = HEX_FORMAT.parseHex(EXPECTED_MANIFEST_HASH);
            byte[] actual = digest.digest(MANIFEST.rawBytes());
            if (!MessageDigest.isEqual(actual, expected)) {
                LOGGER.log(Level.SEVERE,
                        "GameSecurity: manifest digest mismatch (expected {0}, found {1}); runtime checks compromised",
                        new Object[]{EXPECTED_MANIFEST_HASH, HEX_FORMAT.formatHex(actual)});
                return false;
            }
            LOGGER.log(Level.FINE, "GameSecurity: integrity manifest verified");
            return true;
        } catch (NoSuchAlgorithmException ex) {
            LOGGER.log(Level.SEVERE, "GameSecurity: SHA-256 algorithm missing", ex);
        } catch (IllegalArgumentException ex) {
            LOGGER.log(Level.SEVERE, "GameSecurity: integrity manifest hash is malformed", ex);
        }
        return false;
    }

    private static void verifyFile(Path file, String expectedHash) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(expectedHash, "expectedHash");
        try {
            Optional<Path> candidate = locateExistingFile(file);
            if (candidate.isEmpty()) {
                LOGGER.log(Level.WARNING, "GameSecurity: file not found for integrity check: {0}", file);
                return;
            }

            byte[] data = Files.readAllBytes(candidate.get());
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] actual = digest.digest(data);
            byte[] expected = HEX_FORMAT.parseHex(expectedHash);
            if (!MessageDigest.isEqual(actual, expected)) {
                LOGGER.log(Level.WARNING, "GameSecurity: integrity mismatch for {0}", candidate.get());
            } else {
                LOGGER.log(Level.FINE, "GameSecurity: verified {0}", candidate.get());
            }
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "GameSecurity: unable to verify " + file, ex);
        } catch (NoSuchAlgorithmException ex) {
            LOGGER.log(Level.SEVERE, "GameSecurity: SHA-256 algorithm missing", ex);
        } catch (IllegalArgumentException ex) {
            LOGGER.log(Level.SEVERE, "GameSecurity: malformed digest for " + file, ex);
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
