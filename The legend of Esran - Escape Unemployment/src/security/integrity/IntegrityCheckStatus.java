package security.integrity;

/**
 * Summary of the action taken when verifying runtime integrity.
 */
public enum IntegrityCheckStatus {
    /** Verification succeeded and every file matched the expected digest. */
    VERIFIED,
    /** Verification completed but some files were missing or tampered with. */
    VERIFIED_WITH_FAILURES,
    /** Verification skipped because the manifest could not be found. */
    SKIPPED_MANIFEST_MISSING,
    /** Verification skipped because the manifest digest could not be trusted. */
    SKIPPED_MANIFEST_UNTRUSTED,
    /** Verification skipped because the manifest did not list any files. */
    SKIPPED_NO_ENTRIES,
    /** Verification was not executed because the result had already been cached. */
    SKIPPED_ALREADY_VERIFIED
}
