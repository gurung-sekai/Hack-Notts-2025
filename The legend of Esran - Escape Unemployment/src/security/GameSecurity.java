package security;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HexFormat;

/**
 * Simple runtime integrity checks and secure randomness helpers.
 * Intended to provide a lightweight guard when distributing the game on storefronts.
 */
public final class GameSecurity {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Map<Path, String> EXPECTED_HASHES;
    private static volatile boolean verified;

    static {
        Map<Path, String> hashes = new LinkedHashMap<>();
        hashes.put(Path.of("The legend of Esran - Escape Unemployment", "src", "World", "ZeldaRooms.java"),
                "186e8aa272c30f2977fa5fafe9603e4cf59a84581dffb9230f4be6c196d2d8a6");
        hashes.put(Path.of("The legend of Esran - Escape Unemployment", "src", "Battle", "scene", "BossBattlePanel.java"),
                "7cadfd8ca68d5b080f24e4f6f905ffc7958bb5ecc4a8764753dc3309ac185546");
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
            if (!Files.exists(file)) {
                System.err.println("GameSecurity: file not found for integrity check: " + file);
                return;
            }
            byte[] data = Files.readAllBytes(file);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String actual = HexFormat.of().formatHex(digest.digest(data));
            if (!actual.equalsIgnoreCase(expectedHash)) {
                System.err.println("GameSecurity: integrity mismatch for " + file);
            }
        } catch (IOException | NoSuchAlgorithmException ex) {
            System.err.println("GameSecurity: unable to verify " + file + ": " + ex.getMessage());
        }
    }

    /**
     * Shared secure random instance for security-sensitive decisions (boss selection, etc.).
     */
    public static SecureRandom secureRandom() {
        return SECURE_RANDOM;
    }

    /**
     * Generate a random session token that can be used by external services.
     */
    public static String newSessionToken() {
        byte[] buffer = new byte[16];
        SECURE_RANDOM.nextBytes(buffer);
        return HexFormat.of().formatHex(buffer);
    }
}
