package security.integrity;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Structured output describing the result of an integrity verification pass.
 */
public final class IntegrityCheckReport {

    private final IntegrityCheckStatus status;
    private final List<Path> failedEntries;

    private IntegrityCheckReport(IntegrityCheckStatus status, List<Path> failedEntries) {
        this.status = Objects.requireNonNull(status, "status");
        this.failedEntries = List.copyOf(failedEntries);
    }

    public static IntegrityCheckReport of(IntegrityCheckStatus status) {
        return new IntegrityCheckReport(status, Collections.emptyList());
    }

    public static IntegrityCheckReport withFailures(List<Path> failedEntries) {
        return new IntegrityCheckReport(IntegrityCheckStatus.VERIFIED_WITH_FAILURES, failedEntries);
    }

    public IntegrityCheckStatus status() {
        return status;
    }

    public List<Path> failedEntries() {
        return failedEntries;
    }

    public boolean hasFailures() {
        return status == IntegrityCheckStatus.VERIFIED_WITH_FAILURES;
    }
}
