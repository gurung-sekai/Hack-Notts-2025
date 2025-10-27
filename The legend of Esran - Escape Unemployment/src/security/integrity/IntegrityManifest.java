package security.integrity;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents the parsed contents of {@code security/integrity.manifest}.
 * <p>
 * The manifest captures a stable view of critical runtime files and their SHA-256 digests. By keeping
 * the parsing logic self-contained we make it easier to test, document, and evolve the security
 * pipeline without touching unrelated systems.
 * </p>
 */
public final class IntegrityManifest {

    private static final Logger LOGGER = Logger.getLogger(IntegrityManifest.class.getName());

    private final Map<Path, String> entries;
    private final byte[] rawBytes;
    private final boolean available;
    private final String pinnedDigest;

    private IntegrityManifest(Map<Path, String> entries, byte[] rawBytes, boolean available, String pinnedDigest) {
        this.entries = entries;
        this.rawBytes = rawBytes;
        this.available = available;
        this.pinnedDigest = pinnedDigest;
    }

    /**
     * Load a manifest from the classpath.
     */
    public static IntegrityManifest load(String resourcePath) {
        Objects.requireNonNull(resourcePath, "resourcePath");
        try (InputStream in = IntegrityManifest.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                LOGGER.log(Level.WARNING, "GameSecurity: integrity manifest {0} not found on classpath", resourcePath);
                return missing();
            }
            byte[] data = in.readAllBytes();
            return fromBytesInternal(data, true);
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "GameSecurity: unable to read integrity manifest " + resourcePath, ex);
            return missing();
        }
    }

    /**
     * Parse a manifest from raw bytes. Tests rely on this helper to construct bespoke manifests.
     */
    public static IntegrityManifest fromBytes(byte[] data) {
        Objects.requireNonNull(data, "data");
        return fromBytesInternal(data.clone(), true);
    }

    /**
     * Represent a manifest that could not be located on disk or the classpath.
     */
    public static IntegrityManifest missing() {
        return new IntegrityManifest(Collections.emptyMap(), new byte[0], false, null);
    }

    public Map<Path, String> entries() {
        return entries;
    }

    public byte[] rawBytes() {
        return rawBytes.clone();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public boolean isAvailable() {
        return available;
    }

    public Optional<String> pinnedDigest() {
        return Optional.ofNullable(pinnedDigest);
    }

    private static IntegrityManifest fromBytesInternal(byte[] data, boolean available) {
        ParseResult parsed = parseEntries(data);
        return new IntegrityManifest(Collections.unmodifiableMap(parsed.entries()), data.clone(), available,
                parsed.pinnedDigest());
    }

    private static ParseResult parseEntries(byte[] data) {
        LinkedHashMap<Path, String> result = new LinkedHashMap<>();
        String pinnedDigest = null;
        Pattern digestPattern = Pattern.compile("#\\s*manifest-digest\\s*:\\s*([0-9a-fA-F]{64})");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ByteArrayInputStream(data), StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                Matcher matcher = digestPattern.matcher(trimmed);
                if (matcher.matches()) {
                    pinnedDigest = matcher.group(1).toLowerCase();
                    continue;
                }
                if (trimmed.startsWith("#")) {
                    continue;
                }
                int equals = trimmed.indexOf('=');
                if (equals <= 0 || equals == trimmed.length() - 1) {
                    LOGGER.log(Level.WARNING,
                            "GameSecurity: ignoring malformed manifest entry at line {0}", lineNumber);
                    continue;
                }
                String pathText = trimmed.substring(0, equals).trim();
                String hashText = trimmed.substring(equals + 1).trim();
                if (!isHex(hashText)) {
                    LOGGER.log(Level.WARNING,
                            "GameSecurity: ignoring manifest entry with invalid digest at line {0}", lineNumber);
                    continue;
                }
                try {
                    Path path = Path.of(pathText).normalize();
                    if (path.isAbsolute() || path.startsWith("..")) {
                        LOGGER.log(Level.WARNING,
                                "GameSecurity: ignoring manifest entry that resolves outside the project: {0}", pathText);
                        continue;
                    }
                    String previous = result.put(path, hashText.toLowerCase());
                    if (previous != null) {
                        LOGGER.log(Level.WARNING,
                                "GameSecurity: duplicate manifest entry for {0} at line {1}",
                                new Object[]{path, lineNumber});
                    }
                } catch (InvalidPathException ex) {
                    LOGGER.log(Level.WARNING,
                            "GameSecurity: ignoring manifest entry with invalid path at line {0}",
                            lineNumber);
                }
            }
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "GameSecurity: unable to parse integrity manifest contents", ex);
            return new ParseResult(Collections.emptyMap(), pinnedDigest);
        }
        return new ParseResult(result, pinnedDigest);
    }

    private static boolean isHex(String candidate) {
        if (candidate.length() != 64) {
            return false;
        }
        for (int i = 0; i < candidate.length(); i++) {
            char c = candidate.charAt(i);
            boolean digit = (c >= '0' && c <= '9')
                    || (c >= 'a' && c <= 'f')
                    || (c >= 'A' && c <= 'F');
            if (!digit) {
                return false;
            }
        }
        return true;
    }

    private record ParseResult(Map<Path, String> entries, String pinnedDigest) {
    }
}
