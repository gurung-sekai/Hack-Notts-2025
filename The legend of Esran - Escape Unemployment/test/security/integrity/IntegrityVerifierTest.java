package security.integrity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

public final class IntegrityVerifierTest {

    private static final Logger LOGGER = Logger.getLogger(IntegrityVerifierTest.class.getName());

    public static void main(String[] args) throws Exception {
        IntegrityVerifierTest test = new IntegrityVerifierTest();
        test.shouldSkipWhenManifestMissing();
        test.shouldSkipWhenManifestDigestDoesNotMatch();
        test.shouldVerifyFilesWhenDigestsMatch();
        test.shouldReportFailuresWhenDigestsDiffer();
        test.shouldSkipWhenManifestHasNoEntries();
        test.shouldUsePinnedDigestFromManifestComment();
        System.out.println("IntegrityVerifierTest: all scenarios passed");
    }

    private void shouldSkipWhenManifestMissing() throws Exception {
        Path tempDir = createTempDir();
        try {
            IntegrityVerifier verifier = new IntegrityVerifier(
                    IntegrityManifest::missing,
                    () -> List.of(tempDir),
                    "0".repeat(64),
                    LOGGER);

            IntegrityCheckReport report = verifier.verify();
            assertEquals(IntegrityCheckStatus.SKIPPED_MANIFEST_MISSING, report.status(),
                    "Expected manifest-missing status");
        } finally {
            deleteRecursively(tempDir);
        }
    }

    private void shouldSkipWhenManifestDigestDoesNotMatch() throws Exception {
        Path tempDir = createTempDir();
        try {
            String manifestContent = "foo.txt=" + sha256("foo");
            IntegrityManifest manifest = IntegrityManifest.fromBytes(manifestContent.getBytes(StandardCharsets.UTF_8));
            IntegrityVerifier verifier = new IntegrityVerifier(
                    () -> manifest,
                    () -> List.of(tempDir),
                    "0".repeat(64),
                    LOGGER);

            IntegrityCheckReport report = verifier.verify();
            assertEquals(IntegrityCheckStatus.SKIPPED_MANIFEST_UNTRUSTED, report.status(),
                    "Expected manifest-untrusted status");
        } finally {
            deleteRecursively(tempDir);
        }
    }

    private void shouldVerifyFilesWhenDigestsMatch() throws Exception {
        Path tempDir = createTempDir();
        try {
            Path file = tempDir.resolve("boss.txt");
            Files.writeString(file, "mighty boss");

            String manifestContent = "boss.txt=" + sha256("mighty boss");
            IntegrityManifest manifest = IntegrityManifest.fromBytes(manifestContent.getBytes(StandardCharsets.UTF_8));
            IntegrityVerifier verifier = new IntegrityVerifier(
                    () -> manifest,
                    () -> List.of(tempDir),
                    "",
                    LOGGER);

            IntegrityCheckReport report = verifier.verify();
            assertEquals(IntegrityCheckStatus.VERIFIED, report.status(),
                    "Expected verification success");
            assertFalse(report.hasFailures(), "No failures should be reported");
        } finally {
            deleteRecursively(tempDir);
        }
    }

    private void shouldReportFailuresWhenDigestsDiffer() throws Exception {
        Path tempDir = createTempDir();
        try {
            Path file = tempDir.resolve("attack.txt");
            Files.writeString(file, "phase-one");

            String manifestContent = "attack.txt=" + sha256("phase-two");
            IntegrityManifest manifest = IntegrityManifest.fromBytes(manifestContent.getBytes(StandardCharsets.UTF_8));
            IntegrityVerifier verifier = new IntegrityVerifier(
                    () -> manifest,
                    () -> List.of(tempDir),
                    "",
                    LOGGER);

            IntegrityCheckReport report = verifier.verify();
            assertEquals(IntegrityCheckStatus.VERIFIED_WITH_FAILURES, report.status(),
                    "Expected verification failures");
            assertTrue(report.hasFailures(), "Failures flag should be true");
            assertEquals(List.of(Path.of("attack.txt")), report.failedEntries(),
                    "Failure list should contain attack.txt");
        } finally {
            deleteRecursively(tempDir);
        }
    }

    private void shouldSkipWhenManifestHasNoEntries() throws Exception {
        Path tempDir = createTempDir();
        try {
            IntegrityManifest manifest = IntegrityManifest.fromBytes(new byte[0]);
            IntegrityVerifier verifier = new IntegrityVerifier(
                    () -> manifest,
                    () -> List.of(tempDir),
                    "",
                    LOGGER);

            IntegrityCheckReport report = verifier.verify();
            assertEquals(IntegrityCheckStatus.SKIPPED_NO_ENTRIES, report.status(),
                    "Expected no-entries skip status");
        } finally {
            deleteRecursively(tempDir);
        }
    }

    private void shouldUsePinnedDigestFromManifestComment() throws Exception {
        Path tempDir = createTempDir();
        try {
            Path file = tempDir.resolve("hero.txt");
            Files.writeString(file, "heroic data");

            String fileDigest = sha256("heroic data");
            String manifestBody = "hero.txt=" + fileDigest + "\n";
            String expectedManifestDigest = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(manifestBody.getBytes(StandardCharsets.UTF_8)));
            String manifestContent = "# manifest-digest: " + expectedManifestDigest + "\n" + manifestBody;

            IntegrityManifest manifest = IntegrityManifest.fromBytes(manifestContent.getBytes(StandardCharsets.UTF_8));
            IntegrityVerifier verifier = new IntegrityVerifier(
                    () -> manifest,
                    () -> List.of(tempDir),
                    null,
                    LOGGER);

            IntegrityCheckReport report = verifier.verify();
            assertEquals(expectedManifestDigest, manifest.pinnedDigest().orElseThrow(),
                    "Manifest should expose pinned digest from comment");
            assertEquals(IntegrityCheckStatus.VERIFIED, report.status(),
                    "Manifest with comment should verify successfully");
        } finally {
            deleteRecursively(tempDir);
        }
    }

    private static Path createTempDir() throws IOException {
        return Files.createTempDirectory("integrity-test");
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return;
        }
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public java.nio.file.FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return java.nio.file.FileVisitResult.CONTINUE;
            }

            @Override
            public java.nio.file.FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.deleteIfExists(dir);
                return java.nio.file.FileVisitResult.CONTINUE;
            }
        });
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(message + " (expected=" + expected + ", actual=" + actual + ")");
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertFalse(boolean condition, String message) {
        if (condition) {
            throw new AssertionError(message);
        }
    }

    private static String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
