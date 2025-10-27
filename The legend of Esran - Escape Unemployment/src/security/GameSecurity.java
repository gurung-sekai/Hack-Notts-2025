package security;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import security.integrity.IntegrityCheckReport;
import security.integrity.IntegrityCheckStatus;
import security.integrity.IntegrityVerifier;

/**
 * Simple runtime integrity checks and secure randomness helpers.
 * Intended to provide a lightweight guard when distributing the game on storefronts.
 */
public final class GameSecurity {

    private static final Logger LOGGER = Logger.getLogger(GameSecurity.class.getName());
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final HexFormat HEX_FORMAT = HexFormat.of();
    private static final String MANIFEST_RESOURCE = "/security/integrity.manifest";
    private static final IntegrityVerifier VERIFIER =
            new IntegrityVerifier(MANIFEST_RESOURCE, null, LOGGER);
    private static volatile boolean verified;
    private static volatile IntegrityCheckReport lastReport;

    private GameSecurity() {
    }

    /**
     * Ensure the critical files have not been tampered with. Logs a warning if verification fails.
     */
    public static synchronized IntegrityCheckReport verifyIntegrity() {
        if (verified) {
            return IntegrityCheckReport.of(IntegrityCheckStatus.SKIPPED_ALREADY_VERIFIED);
        }
        IntegrityCheckReport report = VERIFIER.verify();
        if (report.hasFailures()) {
            LOGGER.log(Level.WARNING, "GameSecurity: integrity mismatches detected: {0}", report.failedEntries());
        }
        lastReport = report;
        verified = true;
        return report;
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

    public static Optional<IntegrityCheckReport> lastIntegrityReport() {
        return Optional.ofNullable(lastReport);
    }
}
