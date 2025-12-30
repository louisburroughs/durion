package com.durion.audit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.assertj.core.api.Assertions;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.StringLength;

/**
 * Property-based test for Property 5: Report output location consistency
 * **Feature: missing-issues-audit, Property 5: Report output location
 * consistency**
 * **Validates: Requirements 2.5**
 * 
 * For any audit execution, all generated reports should be saved to the
 * .github/orchestration/missing-issues/ directory with proper timestamps.
 */
public class ReportOutputLocationConsistencyPropertyTest {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    @Property(tries = 100)
    @Label("Property 5: Report output location consistency - FileOutputManager creates correct directory structure")
    void fileOutputManagerShouldCreateCorrectDirectoryStructure(
            @ForAll("validDirectoryName") String baseDirectory,
            @ForAll("validTimestamp") LocalDateTime timestamp) throws IOException {

        // Given: A FileOutputManager with a base directory
        Path tempBaseDir = Files.createTempDirectory("test-output-consistency");
        Path testBaseDir = tempBaseDir.resolve(baseDirectory);

        FileOutputManager fileOutputManager = new FileOutputManager(testBaseDir.toString());

        try {
            // When: Creating output directory structure
            Path missingIssuesDir = fileOutputManager.createOutputDirectoryStructure(timestamp);

            // Then: The directory structure should be correct
            Assertions.assertThat(missingIssuesDir).exists();
            Assertions.assertThat(missingIssuesDir.getParent()).isEqualTo(testBaseDir);
            Assertions.assertThat(missingIssuesDir.getFileName().toString()).isEqualTo("missing-issues");

            // And: The audit-cache directory should also be created
            Path auditCacheDir = testBaseDir.resolve("audit-cache");
            Assertions.assertThat(auditCacheDir).exists();

            // And: Both directories should be writable
            Assertions.assertThat(Files.isWritable(missingIssuesDir)).isTrue();
            Assertions.assertThat(Files.isWritable(auditCacheDir)).isTrue();

        } finally {
            // Cleanup
            deleteDirectoryRecursively(tempBaseDir);
        }
    }

    @Property(tries = 50)
    @Label("Property 5: Report output location consistency - Timestamped filenames are consistent")
    void timestampedFilenamesShouldBeConsistent(
            @ForAll("validFilenamePrefix") String prefix,
            @ForAll("validFileExtension") String extension,
            @ForAll("validTimestamp") LocalDateTime timestamp) {

        // Given: A FileOutputManager
        Path tempDir = Paths.get(System.getProperty("java.io.tmpdir"), "test-filename-consistency");
        FileOutputManager fileOutputManager = new FileOutputManager(tempDir.toString());

        // When: Generating timestamped filenames
        String filename1 = fileOutputManager.generateTimestampedFilename(prefix, extension, timestamp);
        String filename2 = fileOutputManager.generateTimestampedFilename(prefix, extension, timestamp);

        // Then: The filenames should be identical for the same inputs
        Assertions.assertThat(filename1).isEqualTo(filename2);

        // And: The filename should contain the expected components
        String expectedTimestamp = timestamp.format(TIMESTAMP_FORMAT);
        String expectedFilename = String.format("%s-%s.%s", prefix, expectedTimestamp, extension);
        Assertions.assertThat(filename1).isEqualTo(expectedFilename);

        // And: The filename should be valid for file systems
        Assertions.assertThat(filename1).doesNotContain("/");
        Assertions.assertThat(filename1).doesNotContain("\\");
        Assertions.assertThat(filename1).doesNotContain(":");
        Assertions.assertThat(filename1).doesNotContain("*");
        Assertions.assertThat(filename1).doesNotContain("?");
        Assertions.assertThat(filename1).doesNotContain("\"");
        Assertions.assertThat(filename1).doesNotContain("<");
        Assertions.assertThat(filename1).doesNotContain(">");
        Assertions.assertThat(filename1).doesNotContain("|");
    }

    @Property(tries = 30)
    @Label("Property 5: Report output location consistency - Report file paths are within correct directory")
    void reportFilePathsShouldBeWithinCorrectDirectory(
            @ForAll("validDirectoryName") String baseDirectory,
            @ForAll("validFilename") String filename,
            @ForAll("validTimestamp") LocalDateTime timestamp) throws IOException {

        // Given: A FileOutputManager with a base directory
        Path tempBaseDir = Files.createTempDirectory("test-path-consistency");
        Path testBaseDir = tempBaseDir.resolve(baseDirectory);

        FileOutputManager fileOutputManager = new FileOutputManager(testBaseDir.toString());

        try {
            // When: Creating report file paths
            Path reportPath = fileOutputManager.createReportFilePath(filename, timestamp);
            Path cachePath = fileOutputManager.createCacheFilePath(filename, timestamp);

            // Then: Report path should be within missing-issues directory
            Path expectedReportParent = testBaseDir.resolve("missing-issues");
            Assertions.assertThat(reportPath.getParent()).isEqualTo(expectedReportParent);
            Assertions.assertThat(reportPath.getFileName().toString()).isEqualTo(filename);

            // And: Cache path should be within audit-cache directory
            Path expectedCacheParent = testBaseDir.resolve("audit-cache");
            Assertions.assertThat(cachePath.getParent()).isEqualTo(expectedCacheParent);
            Assertions.assertThat(cachePath.getFileName().toString()).isEqualTo(filename);

            // And: Both parent directories should exist
            Assertions.assertThat(expectedReportParent).exists();
            Assertions.assertThat(expectedCacheParent).exists();

        } finally {
            // Cleanup
            deleteDirectoryRecursively(tempBaseDir);
        }
    }

    @Property(tries = 20)
    @Label("Property 5: Report output location consistency - Directory paths are consistent across calls")
    void directoryPathsShouldBeConsistentAcrossCalls(
            @ForAll("validDirectoryName") String baseDirectory) throws IOException {

        // Given: A FileOutputManager with a base directory
        Path tempBaseDir = Files.createTempDirectory("test-directory-consistency");
        Path testBaseDir = tempBaseDir.resolve(baseDirectory);

        FileOutputManager fileOutputManager = new FileOutputManager(testBaseDir.toString());

        try {
            // When: Getting directory paths multiple times
            Path baseDir1 = fileOutputManager.getBaseOutputDirectory();
            Path baseDir2 = fileOutputManager.getBaseOutputDirectory();

            Path missingIssuesDir1 = fileOutputManager.getMissingIssuesDirectory();
            Path missingIssuesDir2 = fileOutputManager.getMissingIssuesDirectory();

            Path auditCacheDir1 = fileOutputManager.getAuditCacheDirectory();
            Path auditCacheDir2 = fileOutputManager.getAuditCacheDirectory();

            // Then: The paths should be consistent across calls
            Assertions.assertThat(baseDir1).isEqualTo(baseDir2);
            Assertions.assertThat(missingIssuesDir1).isEqualTo(missingIssuesDir2);
            Assertions.assertThat(auditCacheDir1).isEqualTo(auditCacheDir2);

            // And: The paths should have the correct relationships
            Assertions.assertThat(baseDir1).isEqualTo(testBaseDir);
            Assertions.assertThat(missingIssuesDir1).isEqualTo(testBaseDir.resolve("missing-issues"));
            Assertions.assertThat(auditCacheDir1).isEqualTo(testBaseDir.resolve("audit-cache"));

        } finally {
            // Cleanup
            deleteDirectoryRecursively(tempBaseDir);
        }
    }

    @Property(tries = 15)
    @Label("Property 5: Report output location consistency - File permissions are set correctly")
    void filePermissionsShouldBeSetCorrectly(
            @ForAll("validDirectoryName") String baseDirectory,
            @ForAll("validFilename") String filename,
            @ForAll("validTimestamp") LocalDateTime timestamp) throws IOException {

        // Given: A FileOutputManager with a base directory
        Path tempBaseDir = Files.createTempDirectory("test-permissions-consistency");
        Path testBaseDir = tempBaseDir.resolve(baseDirectory);

        FileOutputManager fileOutputManager = new FileOutputManager(testBaseDir.toString());

        try {
            // When: Creating a file path and ensuring it's writable
            Path filePath = fileOutputManager.createReportFilePath(filename, timestamp);
            fileOutputManager.ensureFileWritable(filePath);

            // Then: The file should exist and be writable
            Assertions.assertThat(filePath).exists();
            Assertions.assertThat(Files.isWritable(filePath)).isTrue();
            Assertions.assertThat(Files.isReadable(filePath)).isTrue();

            // And: The parent directory should be writable
            Assertions.assertThat(Files.isWritable(filePath.getParent())).isTrue();

            // And: File permissions validation should pass
            Assertions.assertThatCode(() -> fileOutputManager.validateFileWritePermissions(filePath))
                    .doesNotThrowAnyException();

        } finally {
            // Cleanup
            deleteDirectoryRecursively(tempBaseDir);
        }
    }

    @Property(tries = 10)
    @Label("Property 5: Report output location consistency - Disk space validation works correctly")
    void diskSpaceValidationShouldWorkCorrectly(
            @ForAll("validDirectoryName") String baseDirectory) throws IOException {

        // Given: A FileOutputManager with a base directory
        Path tempBaseDir = Files.createTempDirectory("test-diskspace-consistency");
        Path testBaseDir = tempBaseDir.resolve(baseDirectory);

        FileOutputManager fileOutputManager = new FileOutputManager(testBaseDir.toString());

        try {
            // When: Checking available disk space
            long availableSpace = fileOutputManager.getAvailableDiskSpace();

            // Then: Available space should be positive
            Assertions.assertThat(availableSpace).isPositive();

            // And: Validation with reasonable size should pass
            long reasonableSize = 1024 * 1024; // 1MB
            Assertions.assertThatCode(() -> fileOutputManager.validateDiskSpace(reasonableSize))
                    .doesNotThrowAnyException();

            // And: Validation with excessive size should fail
            long excessiveSize = availableSpace + (100L * 1024 * 1024 * 1024); // Available + 100GB
            Assertions.assertThatThrownBy(() -> fileOutputManager.validateDiskSpace(excessiveSize))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("Insufficient disk space");

        } finally {
            // Cleanup
            deleteDirectoryRecursively(tempBaseDir);
        }
    }

    @Property(tries = 10)
    @Label("Property 5: Report output location consistency - Cleanup operations work correctly")
    void cleanupOperationsShouldWorkCorrectly(
            @ForAll("validDirectoryName") String baseDirectory) throws IOException {

        // Given: A FileOutputManager with a base directory
        Path tempBaseDir = Files.createTempDirectory("test-cleanup-consistency");
        Path testBaseDir = tempBaseDir.resolve(baseDirectory);

        FileOutputManager fileOutputManager = new FileOutputManager(testBaseDir.toString());

        try {
            // Create some test files with different ages
            Path missingIssuesDir = fileOutputManager.createOutputDirectoryStructure(LocalDateTime.now());

            Path oldFile = missingIssuesDir.resolve("old-report.csv");
            Path newFile = missingIssuesDir.resolve("new-report.csv");

            Files.createFile(oldFile);
            Files.createFile(newFile);

            // Set old file to be older (simulate by setting last modified time)
            Files.setLastModifiedTime(oldFile,
                    java.nio.file.attribute.FileTime.from(
                            LocalDateTime.now().minusDays(10).atZone(java.time.ZoneId.systemDefault()).toInstant()));

            // When: Cleaning up old reports (older than 5 days)
            fileOutputManager.cleanupOldReports(5);

            // Then: Old file should be deleted, new file should remain
            Assertions.assertThat(oldFile).doesNotExist();
            Assertions.assertThat(newFile).exists();

        } finally {
            // Cleanup
            deleteDirectoryRecursively(tempBaseDir);
        }
    }

    @Provide
    Arbitrary<String> validDirectoryName() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .withChars('-', '_')
                .ofMinLength(5)
                .ofMaxLength(30)
                .filter(s -> !s.isBlank() && Character.isLetterOrDigit(s.charAt(0)));
    }

    @Provide
    Arbitrary<String> validFilename() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .withChars('-', '_', '.')
                .ofMinLength(5)
                .ofMaxLength(20)
                .filter(s -> !s.isBlank() && Character.isLetterOrDigit(s.charAt(0)));
    }

    @Provide
    Arbitrary<String> validFilenamePrefix() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .withChars('-', '_')
                .ofMinLength(3)
                .ofMaxLength(20)
                .filter(s -> !s.isBlank() && Character.isLetterOrDigit(s.charAt(0)));
    }

    @Provide
    Arbitrary<String> validFileExtension() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('0', '9')
                .ofMinLength(2)
                .ofMaxLength(5)
                .filter(s -> !s.isBlank() && Character.isLetter(s.charAt(0)));
    }

    @Provide
    Arbitrary<LocalDateTime> validTimestamp() {
        return Arbitraries.of(
                LocalDateTime.of(2020, 1, 1, 0, 0, 0),
                LocalDateTime.of(2025, 6, 15, 12, 30, 0),
                LocalDateTime.of(2030, 12, 31, 23, 59, 59));
    }

    // Helper method to delete directory recursively
    private void deleteDirectoryRecursively(Path directory) throws IOException {
        if (Files.exists(directory)) {
            Files.walk(directory)
                    .sorted((a, b) -> b.compareTo(a)) // Delete files before directories
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            // Ignore cleanup errors in tests
                        }
                    });
        }
    }
}