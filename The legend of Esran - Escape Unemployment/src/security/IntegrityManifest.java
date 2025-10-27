package security;

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
import java.util.logging.Level;
import java.util.logging.Logger;

final class IntegrityManifest {

    private static final Logger LOGGER = Logger.getLogger(IntegrityManifest.class.getName());

    private final Map<Path, String> entries;
    private final byte[] rawBytes;

    private IntegrityManifest(Map<Path, String> entries, byte[] rawBytes) {
        this.entries = entries;
        this.rawBytes = rawBytes;
    }

    static IntegrityManifest load(String resourcePath) {
        try (InputStream in = IntegrityManifest.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                LOGGER.log(Level.WARNING, "GameSecurity: integrity manifest {0} not found on classpath", resourcePath);
                return empty();
            }
            byte[] data = in.readAllBytes();
            Map<Path, String> parsed = parseEntries(data);
            return new IntegrityManifest(Collections.unmodifiableMap(parsed), data.clone());
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "GameSecurity: unable to read integrity manifest " + resourcePath, ex);
            return empty();
        }
    }

    Map<Path, String> entries() {
        return entries;
    }

    byte[] rawBytes() {
        return rawBytes.clone();
    }

    boolean isEmpty() {
        return entries.isEmpty();
    }

    private static IntegrityManifest empty() {
        return new IntegrityManifest(Collections.emptyMap(), new byte[0]);
    }

    private static Map<Path, String> parseEntries(byte[] data) throws IOException {
        LinkedHashMap<Path, String> result = new LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ByteArrayInputStream(data), StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
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
        }
        return result;
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
}
