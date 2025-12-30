package com.durion.audit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Manual test for Property 9: Report update consistency
 * **Feature: missing-issues-audit, Property 9: Report update consistency**
 * **Validates: Requirements 4.5**
 * 
 * For any completed issue creation batch, audit reports should be updated to
 * reflect
 * the newly created issues with accurate status changes.
 */
public class ReportUpdateConsistencyTest {

    public static void main(String[] args) {
        System.out.println("🧪 Running Report Update Consistency Test");
        System.out.println("Testing Property 9: Report update consistency");
        System.out.println("Validates: Requirements 4.5");
        System.out.println();

        try {
            // Test 1: Creation status report generation
            testCreationStatusReportGeneration();

            // Test 2: CSV report updates
            testCsvReportUpdates();

            // Test 3: Summary report generation
            testSummaryReportGeneration();

            // Test 4: Report consistency validation
            testReportConsistencyValidation();

            // Test 5: Batch operation report updates
            testBatchOperationReportUpdates();

            System.out.println("✅ All report update consistency tests passed!");
            System.out.println("Property 9: Report update consistency - VALIDATED");

        } catch (Exception e) {
            System.out.println("❌ Test failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void testCreationStatusReportGeneration() throws Exception {
        System.out.println("🔍 Test 1: Creation status report generation");

        // Given: A batch creation result and original missing issues
        List<MissingIssue> originalIssues = createTestMissingIssues(3);
        IssueCreator.BatchCreationResult batchResult = createMockBatchResult(originalIssues);

        // And: A report updater
        Path tempDir = Files.createTempDirectory("test-reports");
        AuditLogger logger = new AuditLogger();
        ReportUpdater reportUpdater = new ReportUpdater(logger, tempDir.toString());

        try {
            // When: Creating a creation status report
            String reportPath = reportUpdater.createCreationStatusReport(batchResult, originalIssues);

            // Then: Report should be created and contain expected content
            assert Files.exists(Paths.get(reportPath)) : "Status report file should exist";

            String content = Files.readString(Paths.get(reportPath));
            assert content.contains("\"totalCount\": 3") : "Should contain total count";
            assert content.contains("\"successCount\": 2") : "Should contain success count";
            assert content.contains("\"failureCount\": 1") : "Should contain failure count";
            assert content.contains("\"storyNumber\": 1") : "Should contain story details";

            System.out.println("   ✅ Creation status report generation validation passed");

        } finally {
            // Cleanup
            deleteDirectoryRecursively(tempDir);
        }
    }

    private static void testCsvReportUpdates() throws Exception {
        System.out.println("🔍 Test 2: CSV report updates");

        // Given: An original CSV report
        Path tempDir = Files.createTempDirectory("test-csv");
        Path originalCsv = tempDir.resolve("original-report.csv");

        String csvContent = """
                Story Number,Story Title,Story URL,Expected Title,Target Repository,Domain
                1,Test Story 1,https://github.com/test/repo/issues/1,[FRONTEND] Test Story 1,test-repo,general
                2,Test Story 2,https://github.com/test/repo/issues/2,[BACKEND] Test Story 2,test-repo,user
                3,Test Story 3,https://github.com/test/repo/issues/3,[FRONTEND] Test Story 3,test-repo,payment
                """;
        Files.writeString(originalCsv, csvContent);

        // And: Creation results
        List<IssueCreator.IssueCreationResult> creationResults = createMockCreationResults();

        // And: A report updater
        AuditLogger logger = new AuditLogger();
        ReportUpdater reportUpdater = new ReportUpdater(logger, tempDir.toString());

        try {
            // When: Updating the CSV report
            String updatedCsvPath = reportUpdater.updateCsvReport(originalCsv.toString(), creationResults);

            // Then: Updated CSV should exist and contain status information
            assert Files.exists(Paths.get(updatedCsvPath)) : "Updated CSV should exist";

            String updatedContent = Files.readString(Paths.get(updatedCsvPath));
            assert updatedContent.contains("Creation Status") : "Should contain creation status column";
            assert updatedContent.contains("Success") : "Should contain success status";
            assert updatedContent.contains("Failed") : "Should contain failure status";
            assert updatedContent.contains("Created Issue URL") : "Should contain issue URL column";

            // And: Should have more lines than original (header + status columns)
            String[] originalLines = csvContent.split("\n");
            String[] updatedLines = updatedContent.split("\n");
            assert updatedLines.length >= originalLines.length : "Updated CSV should have at least as many lines";

            System.out.println("   ✅ CSV report updates validation passed");

        } finally {
            // Cleanup
            deleteDirectoryRecursively(tempDir);
        }
    }

    private static void testSummaryReportGeneration() throws Exception {
        System.out.println("🔍 Test 3: Summary report generation");

        // Given: A batch creation result and original missing issues
        List<MissingIssue> originalIssues = createTestMissingIssues(4);
        IssueCreator.BatchCreationResult batchResult = createMockBatchResult(originalIssues);

        // And: A report updater
        Path tempDir = Files.createTempDirectory("test-summary");
        AuditLogger logger = new AuditLogger();
        ReportUpdater reportUpdater = new ReportUpdater(logger, tempDir.toString());

        try {
            // When: Generating a creation summary
            String summaryPath = reportUpdater.generateCreationSummary(batchResult, originalIssues);

            // Then: Summary should be created and contain expected sections
            assert Files.exists(Paths.get(summaryPath)) : "Summary report should exist";

            String content = Files.readString(Paths.get(summaryPath));
            assert content.contains("# Issue Creation Summary Report") : "Should have title";
            assert content.contains("**Total Issues Processed**: 4") : "Should show total count";
            assert content.contains("**Successfully Created**: 2") : "Should show success count";
            assert content.contains("**Failed to Create**: 2") : "Should show failure count";
            assert content.contains("## ✅ Successfully Created Issues") : "Should have success section";
            assert content.contains("## ❌ Failed to Create Issues") : "Should have failure section";
            assert content.contains("Story #1") : "Should contain story details";

            System.out.println("   ✅ Summary report generation validation passed");

        } finally {
            // Cleanup
            deleteDirectoryRecursively(tempDir);
        }
    }

    private static void testReportConsistencyValidation() throws Exception {
        System.out.println("🔍 Test 4: Report consistency validation");

        // Given: Original and updated reports
        Path tempDir = Files.createTempDirectory("test-consistency");
        Path originalReport = tempDir.resolve("original.json");
        Path updatedReport = tempDir.resolve("updated.json");

        String originalContent = "{\"audit\": \"original\"}";
        String updatedContent = "{\"audit\": \"original\", \"creationResults\": {\"added\": true}}";

        Files.writeString(originalReport, originalContent);
        Files.writeString(updatedReport, updatedContent);

        // And: Creation results
        List<IssueCreator.IssueCreationResult> creationResults = createMockCreationResults();

        // And: A report updater
        AuditLogger logger = new AuditLogger();
        ReportUpdater reportUpdater = new ReportUpdater(logger, tempDir.toString());

        try {
            // When: Validating report consistency
            boolean isConsistent = reportUpdater.validateReportConsistency(
                    originalReport.toString(),
                    updatedReport.toString(),
                    creationResults);

            // Then: Should validate consistency correctly
            assert isConsistent : "Reports should be consistent";

            // And: Test with inconsistent reports (same size)
            Files.writeString(updatedReport, originalContent); // Same content
            boolean isInconsistent = reportUpdater.validateReportConsistency(
                    originalReport.toString(),
                    updatedReport.toString(),
                    creationResults);

            assert !isInconsistent : "Should detect inconsistent reports";

            System.out.println("   ✅ Report consistency validation passed");

        } finally {
            // Cleanup
            deleteDirectoryRecursively(tempDir);
        }
    }

    private static void testBatchOperationReportUpdates() throws Exception {
        System.out.println("🔍 Test 5: Batch operation report updates");

        // Given: Missing issues and a mock issue creator
        List<MissingIssue> missingIssues = createTestMissingIssues(3);
        MockGitHubIssueCreator mockCreator = new MockGitHubIssueCreator();
        AuditLogger logger = new AuditLogger();
        IssueCreator issueCreator = new IssueCreator(mockCreator, logger);

        // And: A report updater
        Path tempDir = Files.createTempDirectory("test-batch");
        ReportUpdater reportUpdater = new ReportUpdater(logger, tempDir.toString());

        try {
            // When: Running batch creation with reporting
            IssueCreator.BatchCreationResult result = issueCreator.createIssuesBatchWithReporting(missingIssues,
                    reportUpdater);

            // Then: Batch should complete successfully
            assert result.getTotalCount() == 3 : "Should process all 3 issues";
            assert result.getSuccessCount() == 3 : "All should succeed with mock creator";

            // And: Reports should be generated
            List<Path> reportFiles = Files.list(tempDir)
                    .filter(path -> path.getFileName().toString().startsWith("issue-creation-status-") ||
                            path.getFileName().toString().startsWith("creation-summary-"))
                    .toList();

            assert reportFiles.size() >= 2 : "Should generate at least status and summary reports";

            // And: Reports should contain expected content
            boolean hasStatusReport = reportFiles.stream()
                    .anyMatch(path -> path.getFileName().toString().startsWith("issue-creation-status-"));
            boolean hasSummaryReport = reportFiles.stream()
                    .anyMatch(path -> path.getFileName().toString().startsWith("creation-summary-"));

            assert hasStatusReport : "Should generate status report";
            assert hasSummaryReport : "Should generate summary report";

            System.out.println("   ✅ Batch operation report updates validation passed");

        } finally {
            // Cleanup
            deleteDirectoryRecursively(tempDir);
        }
    }

    // Helper methods

    private static List<MissingIssue> createTestMissingIssues(int count) {
        List<MissingIssue> issues = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            issues.add(new MissingIssue(
                    i,
                    "Test Story " + i,
                    "https://github.com/test/repo/issues/" + i,
                    "frontend",
                    "test-repo",
                    "[FRONTEND] Test Story " + i));
        }
        return issues;
    }

    private static IssueCreator.BatchCreationResult createMockBatchResult(List<MissingIssue> originalIssues) {
        List<IssueCreator.IssueCreationResult> results = new ArrayList<>();

        // Create mixed success/failure results
        for (int i = 0; i < originalIssues.size(); i++) {
            boolean success = (i % 2 == 0); // Alternate success/failure

            if (success) {
                MockGitHubIssue mockIssue = new MockGitHubIssue(
                        i + 1000,
                        "[FRONTEND] Test Story " + (i + 1),
                        "Test body",
                        "https://github.com/test-repo/issues/" + (i + 1000),
                        List.of("frontend", "test"));
                results.add(new IssueCreator.IssueCreationResult(
                        mockIssue, null, true, "Success", List.of()));
            } else {
                results.add(new IssueCreator.IssueCreationResult(
                        null, null, false, "Failed", List.of("Test error")));
            }
        }

        int successCount = (int) results.stream().mapToLong(r -> r.isSuccess() ? 1 : 0).sum();
        int failureCount = results.size() - successCount;

        return new IssueCreator.BatchCreationResult(results, successCount, failureCount, List.of());
    }

    private static List<IssueCreator.IssueCreationResult> createMockCreationResults() {
        List<IssueCreator.IssueCreationResult> results = new ArrayList<>();

        // Success result
        MockGitHubIssue successIssue = new MockGitHubIssue(
                1001, "[FRONTEND] Test Story 1", "Test body",
                "https://github.com/test-repo/issues/1001", List.of("frontend"));
        results.add(new IssueCreator.IssueCreationResult(
                successIssue, null, true, "Success", List.of()));

        // Failure result
        results.add(new IssueCreator.IssueCreationResult(
                null, null, false, "Failed", List.of("Test error")));

        // Another success result
        MockGitHubIssue successIssue2 = new MockGitHubIssue(
                1003, "[FRONTEND] Test Story 3", "Test body",
                "https://github.com/test-repo/issues/1003", List.of("frontend"));
        results.add(new IssueCreator.IssueCreationResult(
                successIssue2, null, true, "Success", List.of()));

        return results;
    }

    private static void deleteDirectoryRecursively(Path directory) throws IOException {
        if (Files.exists(directory)) {
            Files.walk(directory)
                    .sorted((a, b) -> b.compareTo(a)) // Delete files before directories
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            // Ignore cleanup errors
                        }
                    });
        }
    }

    /**
     * Mock implementation of GitHubIssueCreator for testing
     */
    private static class MockGitHubIssueCreator implements GitHubIssueCreator {

        @Override
        public GitHubIssue createIssue(String repository, String title, String body, List<String> labels)
                throws IOException, InterruptedException {

            // Always succeed for simplicity in this test
            return new MockGitHubIssue(
                    (int) (Math.random() * 10000),
                    title,
                    body,
                    "https://github.com/" + repository + "/issues/" + (int) (Math.random() * 10000),
                    labels);
        }
    }

    /**
     * Mock implementation of GitHubIssue for testing
     */
    private static class MockGitHubIssue implements GitHubIssue {
        private final int number;
        private final String title;
        private final String body;
        private final String url;
        private final List<String> labels;

        public MockGitHubIssue(int number, String title, String body, String url, List<String> labels) {
            this.number = number;
            this.title = title;
            this.body = body;
            this.url = url;
            this.labels = List.copyOf(labels);
        }

        @Override
        public int getNumber() {
            return number;
        }

        @Override
        public String getTitle() {
            return title;
        }

        @Override
        public String getBody() {
            return body;
        }

        @Override
        public String getUrl() {
            return url;
        }

        @Override
        public List<String> getLabels() {
            return labels;
        }

        @Override
        public String toString() {
            return String.format("MockGitHubIssue{number=%d, title='%s', url='%s'}", number, title, url);
        }
    }
}