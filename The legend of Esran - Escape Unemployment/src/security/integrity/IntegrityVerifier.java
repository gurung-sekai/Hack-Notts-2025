package security.integrity;

import util.ProjectDirectories;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Coordinates manifest validation and file hashing to keep {@link security.GameSecurity} leaner.
 */
public final class IntegrityVerifier {

    private static final HexFormat HEX = HexFormat.of();
    private static final Pattern MANIFEST_DIGEST_LINE =
            Pattern.compile("(?m)^#\\s*manifest-digest\\s*:[^\n]*\\n?");

    private final Supplier<IntegrityManifest> manifestSupplier;
    private final Supplier<List<Path>> searchRootsSupplier;
    private final String pinnedManifestDigest;
    private final Logger logger;

    public IntegrityVerifier(Supplier<IntegrityManifest> manifestSupplier,
                             Supplier<List<Path>> searchRootsSupplier,
                             String pinnedManifestDigest,
                             Logger logger) {
        this.manifestSupplier = Objects.requireNonNull(manifestSupplier, "manifestSupplier");
        this.searchRootsSupplier = Objects.requireNonNull(searchRootsSupplier, "searchRootsSupplier");
        this.pinnedManifestDigest = pinnedManifestDigest;
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public IntegrityVerifier(String manifestResource,
                             String pinnedManifestDigest,
                             Logger logger) {
        this(() -> IntegrityManifest.load(manifestResource),
                ProjectDirectories::locateSearchRoots,
                pinnedManifestDigest,
                logger);
    }

    public IntegrityCheckReport verify() {
        IntegrityManifest manifest = manifestSupplier.get();
        if (!manifest.isAvailable()) {
            logger.log(Level.WARNING, "GameSecurity: integrity manifest unavailable; checks skipped");
            return IntegrityCheckReport.of(IntegrityCheckStatus.SKIPPED_MANIFEST_MISSING);
        }

        if (!manifestDigestTrusted(manifest)) {
            return IntegrityCheckReport.of(IntegrityCheckStatus.SKIPPED_MANIFEST_UNTRUSTED);
        }

        if (manifest.isEmpty()) {
            logger.log(Level.WARNING, "GameSecurity: integrity manifest contained no entries; checks skipped");
            return IntegrityCheckReport.of(IntegrityCheckStatus.SKIPPED_NO_ENTRIES);
        }

        Map<Path, String> entries = manifest.entries();
        List<Path> failedEntries = new ArrayList<>();
        for (Map.Entry<Path, String> entry : entries.entrySet()) {
            Path relativePath = entry.getKey();
            String expectedDigest = entry.getValue();
            Optional<Path> resolved = locate(relativePath);
            if (resolved.isEmpty()) {
                logger.log(Level.WARNING, "GameSecurity: file not found for integrity check: {0}", relativePath);
                failedEntries.add(relativePath);
                continue;
            }
            Path actualPath = resolved.get();
            try {
                byte[] data = Files.readAllBytes(actualPath);
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] actual = digest.digest(data);
                byte[] expected = HEX.parseHex(expectedDigest);
                if (!MessageDigest.isEqual(actual, expected)) {
                    logger.log(Level.WARNING, "GameSecurity: integrity mismatch for {0}", actualPath);
                    failedEntries.add(relativePath);
                } else {
                    logger.log(Level.FINE, "GameSecurity: verified {0}", actualPath);
                }
            } catch (IOException ex) {
                logger.log(Level.SEVERE, "GameSecurity: unable to verify " + actualPath, ex);
                failedEntries.add(relativePath);
            } catch (NoSuchAlgorithmException ex) {
                logger.log(Level.SEVERE, "GameSecurity: SHA-256 algorithm missing", ex);
                return IntegrityCheckReport.of(IntegrityCheckStatus.SKIPPED_MANIFEST_UNTRUSTED);
            } catch (IllegalArgumentException ex) {
                logger.log(Level.SEVERE, "GameSecurity: malformed digest for " + actualPath, ex);
                failedEntries.add(relativePath);
            }
        }

        if (failedEntries.isEmpty()) {
            logger.log(Level.FINE, "GameSecurity: integrity manifest verified");
            return IntegrityCheckReport.of(IntegrityCheckStatus.VERIFIED);
        }
        return IntegrityCheckReport.withFailures(failedEntries);
    }

    private boolean manifestDigestTrusted(IntegrityManifest manifest) {
        String expectedDigest = pinnedManifestDigest;
        if (expectedDigest == null || expectedDigest.isBlank()) {
            expectedDigest = manifest.pinnedDigest().orElse(null);
        }
        if (expectedDigest == null || expectedDigest.isBlank()) {
            logger.log(Level.INFO, "GameSecurity: manifest digest not pinned; continuing without tamper detection");
            return true;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] expected = HEX.parseHex(expectedDigest);
            byte[] actual = digest.digest(normalizeManifestBytes(manifest.rawBytes()));
            if (!MessageDigest.isEqual(actual, expected)) {
                logger.log(Level.SEVERE,
                        "GameSecurity: manifest digest mismatch (expected {0}, found {1}); runtime checks compromised",
                        new Object[]{expectedDigest, HEX.formatHex(actual)});
                return false;
            }
            return true;
        } catch (NoSuchAlgorithmException ex) {
            logger.log(Level.SEVERE, "GameSecurity: SHA-256 algorithm missing", ex);
        } catch (IllegalArgumentException ex) {
            logger.log(Level.SEVERE, "GameSecurity: integrity manifest hash is malformed", ex);
        }
        return false;
    }

    private Optional<Path> locate(Path relativePath) {
        Objects.requireNonNull(relativePath, "relativePath");
        List<Path> roots = searchRootsSupplier.get();
        for (Path root : roots) {
            Path candidate = root.resolve(relativePath);
            if (Files.isRegularFile(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private byte[] normalizeManifestBytes(byte[] rawBytes) {
        String content = new String(rawBytes, StandardCharsets.UTF_8);
        Matcher matcher = MANIFEST_DIGEST_LINE.matcher(content);
        String normalized = matcher.replaceFirst("");
        return normalized.getBytes(StandardCharsets.UTF_8);
    }
}
