package com.durion.audit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.assertj.core.api.Assertions;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property-based test for Property 4: Report content completeness
 * **Feature: missing-issues-audit, Property 4: Report content completeness**
 * **Validates: Requirements 1.5, 2.1, 2.2, 2.3, 2.4**
 * 
 * For any set of missing issues, generated reports should contain all required
 * fields:
 * story number, title, URL, expected title format, and target repository.
 */
public class ReportContentCompletenessPropertyTest {

    @Property(tries = 100)
    @Label("Property 4: Report content completeness - CSV reports contain all required fields")
    void csvReportsShouldContainAllRequiredFields(
            @ForAll("missingIssuesList") List<MissingIssue> missingIssues) throws IOException {

        // Given: A DefaultReportManager with temporary output directory
        Path tempDir = Files.createTempDirectory("test-report-completeness");
        DefaultReportManager reportManager = new DefaultReportManager(tempDir.toString());

        try {
            // When: Generating CSV reports for missing issues
            if (!missingIssues.isEmpty()) {
                // Test frontend CSV generation
                String frontendCsvPath = tempDir.resolve("test-frontend.csv").toString();
                List<MissingIssue> frontendIssues = missingIssues.stream()
                        .filter(issue -> "frontend".equals(issue.getRepositoryType()))
                        .toList();

                if (!frontendIssues.isEmpty()) {
                    reportManager.generateMissingFrontendCsv(frontendIssues, frontendCsvPath);

                    // Then: CSV file should exist and contain all required fields
                    Assertions.assertThat(Files.exists(Path.of(frontendCsvPath))).isTrue();

                    String csvContent = Files.readString(Path.of(frontendCsvPath));

                    // Verify CSV header contains all required fields
                    String[] lines = csvContent.split("\n");
                    Assertions.assertThat(lines.length).isGreaterThan(0);

                    String header = lines[0];
                    Assertions.assertThat(header).contains("Story Number");
                    Assertions.assertThat(header).contains("Story Title");
                    Assertions.assertThat(header).contains("Story URL");
                    Assertions.assertThat(header).contains("Expected Title");
                    Assertions.assertThat(header).contains("Target Repository");
                    Assertions.assertThat(header).contains("Domain");

                    // Verify each missing issue is represented in CSV
                    for (MissingIssue issue : frontendIssues) {
                        boolean foundInCsv = false;
                        for (int i = 1; i < lines.length; i++) {
                            String line = lines[i];
                            String[] columns = line.split(",", -1);
                            if (columns.length < 5) {
                                continue;
                            }

                            String storyNumberColumn = columns[0];
                            if (storyNumberColumn.equals(String.valueOf(issue.getStoryNumber()))) {
                                foundInCsv = true;

                                // Verify the line contains all required data
                                Assertions.assertThat(columns[2]).isEqualTo(issue.getStoryUrl());
                                Assertions.assertThat(columns[4]).isEqualTo(issue.getTargetRepository());
                                break;
                            }
                        }
                        Assertions.assertThat(foundInCsv)
                                .withFailMessage("Missing issue #%d should be found in CSV", issue.getStoryNumber())
                                .isTrue();
                    }
                }

                // Test backend CSV generation
                String backendCsvPath = tempDir.resolve("test-backend.csv").toString();
                List<MissingIssue> backendIssues = missingIssues.stream()
                        .filter(issue -> "backend".equals(issue.getRepositoryType()))
                        .toList();

                if (!backendIssues.isEmpty()) {
                    reportManager.generateMissingBackendCsv(backendIssues, backendCsvPath);

                    // Then: CSV file should exist and contain all required fields
                    Assertions.assertThat(Files.exists(Path.of(backendCsvPath))).isTrue();

                    String csvContent = Files.readString(Path.of(backendCsvPath));

                    // Verify each missing issue is represented in CSV
                    String[] lines = csvContent.split("\n");
                    for (MissingIssue issue : backendIssues) {
                        boolean foundInCsv = false;
                        for (int i = 1; i < lines.length; i++) {
                            if (lines[i].contains(String.valueOf(issue.getStoryNumber()))) {
                                foundInCsv = true;
                                break;
                            }
                        }
                        Assertions.assertThat(foundInCsv)
                                .withFailMessage("Missing issue #%d should be found in CSV", issue.getStoryNumber())
                                .isTrue();
                    }
                }
            }

        } finally {
            // Cleanup
            deleteDirectoryRecursively(tempDir);
        }
    }

    @Property(tries = 50)
    @Label("Property 4: Report content completeness - JSON summary contains all audit metadata")
    void jsonSummaryShouldContainAllAuditMetadata(
            @ForAll("auditResultData") AuditResultData auditData) throws IOException {

        // Given: A DefaultReportManager with temporary output directory
        Path tempDir = Files.createTempDirectory("test-json-completeness");
        DefaultReportManager reportManager = new DefaultReportManager(tempDir.toString());

        try {
            // Create AuditResult from test data
            AuditResult auditResult = createAuditResult(auditData);

            // When: Generating JSON summary report
            String jsonPath = tempDir.resolve("test-summary.json").toString();
            reportManager.generateJsonSummary(auditResult, jsonPath);

            // Then: JSON file should exist and contain all required metadata
            Assertions.assertThat(Files.exists(Path.of(jsonPath))).isTrue();

            String jsonContent = Files.readString(Path.of(jsonPath));

            // Verify JSON contains all required audit metadata
            Assertions.assertThat(jsonContent).contains("auditTimestamp");
            Assertions.assertThat(jsonContent).contains("totalProcessedStories");
            Assertions.assertThat(jsonContent).contains("totalMissingIssues");
            Assertions.assertThat(jsonContent).contains("configuration");
            Assertions.assertThat(jsonContent).contains("statistics");
            Assertions.assertThat(jsonContent).contains("missingFrontendIssues");
            Assertions.assertThat(jsonContent).contains("missingBackendIssues");

            // Verify statistics section contains completion percentages
            Assertions.assertThat(jsonContent).contains("frontendCompletionPercentage");
            Assertions.assertThat(jsonContent).contains("backendCompletionPercentage");
            Assertions.assertThat(jsonContent).contains("overallCompletionPercentage");

            // Verify configuration section contains audit settings
            Assertions.assertThat(jsonContent).contains("outputDirectory");
            Assertions.assertThat(jsonContent).contains("auditMode");
            Assertions.assertThat(jsonContent).contains("rateLimitDelayMs");
            Assertions.assertThat(jsonContent).contains("batchSize");

            // Verify missing issues contain all required fields
            for (MissingIssue issue : auditData.missingFrontendIssues) {
                Assertions.assertThat(jsonContent).contains(String.valueOf(issue.getStoryNumber()));
                Assertions.assertThat(jsonContent).contains(issue.getStoryUrl());
                Assertions.assertThat(jsonContent).contains(issue.getTargetRepository());
            }

            for (MissingIssue issue : auditData.missingBackendIssues) {
                Assertions.assertThat(jsonContent).contains(String.valueOf(issue.getStoryNumber()));
                Assertions.assertThat(jsonContent).contains(issue.getStoryUrl());
                Assertions.assertThat(jsonContent).contains(issue.getTargetRepository());
            }

        } finally {
            // Cleanup
            deleteDirectoryRecursively(tempDir);
        }
    }

    @Property(tries = 30)
    @Label("Property 4: Report content completeness - Markdown summary is human-readable")
    void markdownSummaryShouldBeHumanReadable(
            @ForAll("auditResultData") AuditResultData auditData) throws IOException {

        // Given: A DefaultReportManager with temporary output directory
        Path tempDir = Files.createTempDirectory("test-markdown-completeness");
        DefaultReportManager reportManager = new DefaultReportManager(tempDir.toString());

        try {
            // Create AuditResult from test data
            AuditResult auditResult = createAuditResult(auditData);

            // When: Generating Markdown summary report
            String markdownPath = tempDir.resolve("test-summary.md").toString();
            reportManager.generateMarkdownSummary(auditResult, markdownPath);

            // Then: Markdown file should exist and be human-readable
            Assertions.assertThat(Files.exists(Path.of(markdownPath))).isTrue();

            String markdownContent = Files.readString(Path.of(markdownPath));

            // Verify Markdown structure and content
            Assertions.assertThat(markdownContent).contains("# Missing Issues Audit Report");
            Assertions.assertThat(markdownContent).contains("## Summary Statistics");
            Assertions.assertThat(markdownContent).contains("## Audit Configuration");

            // Verify statistics table format
            Assertions.assertThat(markdownContent).contains("| Metric | Frontend | Backend | Overall |");
            Assertions.assertThat(markdownContent).contains("| **Issues Found** |");
            Assertions.assertThat(markdownContent).contains("| **Missing Issues** |");
            Assertions.assertThat(markdownContent).contains("| **Completion %** |");

            // Verify missing issues sections exist when there are missing issues
            if (!auditData.missingFrontendIssues.isEmpty()) {
                Assertions.assertThat(markdownContent).contains("## Missing Frontend Issues");

                // Verify each frontend issue is listed with required information
                for (MissingIssue issue : auditData.missingFrontendIssues) {
                    Assertions.assertThat(markdownContent).contains("Story #" + issue.getStoryNumber());
                    Assertions.assertThat(markdownContent).contains(issue.getStoryUrl());
                    Assertions.assertThat(markdownContent).contains("**Expected Title**:");
                    Assertions.assertThat(markdownContent).contains("**Repository**:");
                }
            }

            if (!auditData.missingBackendIssues.isEmpty()) {
                Assertions.assertThat(markdownContent).contains("## Missing Backend Issues");

                // Verify each backend issue is listed with required information
                for (MissingIssue issue : auditData.missingBackendIssues) {
                    Assertions.assertThat(markdownContent).contains("Story #" + issue.getStoryNumber());
                    Assertions.assertThat(markdownContent).contains(issue.getStoryUrl());
                    Assertions.assertThat(markdownContent).contains("**Expected Title**:");
                    Assertions.assertThat(markdownContent).contains("**Repository**:");
                }
            }

            // Verify configuration details are present
            Assertions.assertThat(markdownContent).contains("**Output Directory**:");
            Assertions.assertThat(markdownContent).contains("**Audit Mode**:");
            Assertions.assertThat(markdownContent).contains("**Rate Limit Delay**:");
            Assertions.assertThat(markdownContent).contains("**Batch Size**:");

        } finally {
            // Cleanup
            deleteDirectoryRecursively(tempDir);
        }
    }

    @Property(tries = 20)
    @Label("Property 4: Report content completeness - All reports generated together")
    void allReportsShouldBeGeneratedTogether(
            @ForAll("auditResultData") AuditResultData auditData) throws IOException {

        // Given: A DefaultReportManager with temporary output directory
        Path tempDir = Files.createTempDirectory("test-all-reports");
        DefaultReportManager reportManager = new DefaultReportManager(tempDir.toString());

        try {
            // Create AuditResult from test data
            AuditResult auditResult = createAuditResult(auditData);

            // When: Generating all reports together
            List<String> generatedFiles = reportManager.generateReports(auditResult);

            // Then: All expected report files should be generated
            Assertions.assertThat(generatedFiles).isNotEmpty();

            // Verify all generated files exist
            for (String filePath : generatedFiles) {
                Assertions.assertThat(Files.exists(Path.of(filePath)))
                        .withFailMessage("Generated file should exist: %s", filePath)
                        .isTrue();
            }

            // Verify expected file types are generated
            boolean hasJsonSummary = generatedFiles.stream()
                    .anyMatch(path -> path.contains("audit-") && path.endsWith(".json"));
            boolean hasMarkdownSummary = generatedFiles.stream()
                    .anyMatch(path -> path.contains("summary-") && path.endsWith(".md"));

            Assertions.assertThat(hasJsonSummary).isTrue();
            Assertions.assertThat(hasMarkdownSummary).isTrue();

            // CSV files should be generated only if there are missing issues
            if (!auditData.missingFrontendIssues.isEmpty()) {
                boolean hasFrontendCsv = generatedFiles.stream()
                        .anyMatch(path -> path.contains("missing-frontend-") && path.endsWith(".csv"));
                Assertions.assertThat(hasFrontendCsv).isTrue();
            }

            if (!auditData.missingBackendIssues.isEmpty()) {
                boolean hasBackendCsv = generatedFiles.stream()
                        .anyMatch(path -> path.contains("missing-backend-") && path.endsWith(".csv"));
                Assertions.assertThat(hasBackendCsv).isTrue();
            }

        } finally {
            // Cleanup
            deleteDirectoryRecursively(tempDir);
        }
    }

    @Provide
    Arbitrary<List<MissingIssue>> missingIssuesList() {
        return Arbitraries.integers().between(1, 10)
                .flatMap(size -> Arbitraries.integers().between(1, 1000).list().ofSize(size)
                        .map(numbers -> numbers.stream().distinct().collect(java.util.stream.Collectors.toList()))
                        .flatMap(uniqueNumbers -> Combinators.combine(
                                Arbitraries.just(uniqueNumbers),
                                Arbitraries.strings().withCharRange('A', 'z').ofMinLength(10).ofMaxLength(50).list()
                                        .ofSize(uniqueNumbers.size()),
                                Arbitraries.of("frontend", "backend").list().ofSize(uniqueNumbers.size()),
                                Arbitraries.of("payment", "user", "admin", "reporting", "general").list()
                                        .ofSize(uniqueNumbers.size()))
                                .as((nums, titles, types, domains) -> {
                                    List<MissingIssue> result = new ArrayList<>();
                                    for (int i = 0; i < nums.size(); i++) {
                                        String repoType = types.get(i);
                                        String targetRepo = "frontend".equals(repoType)
                                                ? "louisburroughs/durion-moqui-frontend"
                                                : "louisburroughs/durion-positivity-backend";
                                        String prefix = "frontend".equals(repoType) ? "[FRONTEND]" : "[BACKEND]";

                                        result.add(new MissingIssue(
                                                nums.get(i),
                                                titles.get(i),
                                                "https://github.com/louisburroughs/durion/issues/" + nums.get(i),
                                                repoType,
                                                targetRepo,
                                                prefix + " " + titles.get(i)));
                                    }
                                    return result;
                                })));
    }

    @Provide
    Arbitrary<AuditResultData> auditResultData() {
        return Combinators.combine(
                Arbitraries.integers().between(10, 100), // totalProcessedStories
                missingIssuesList(), // all missing issues
                Arbitraries.integers().between(1000, 5000), // rateLimitDelayMs
                Arbitraries.integers().between(5, 20) // batchSize
        ).as((totalStories, allMissingIssues, rateLimitDelay, batchSize) -> {
            List<MissingIssue> frontendIssues = allMissingIssues.stream()
                    .filter(issue -> "frontend".equals(issue.getRepositoryType()))
                    .toList();
            List<MissingIssue> backendIssues = allMissingIssues.stream()
                    .filter(issue -> "backend".equals(issue.getRepositoryType()))
                    .toList();

            return new AuditResultData(
                    totalStories,
                    frontendIssues,
                    backendIssues,
                    rateLimitDelay,
                    batchSize);
        });
    }

    // Helper method to create AuditResult from test data
    private AuditResult createAuditResult(AuditResultData data) {
        LocalDateTime timestamp = LocalDateTime.now();

        AuditConfiguration config = new AuditConfiguration.Builder()
                .githubToken("test-token")
                .outputDirectory("/tmp/test")
                .auditMode(AuditMode.FULL_AUDIT)
                .rateLimitDelayMs(data.rateLimitDelayMs)
                .batchSize(data.batchSize)
                .build();

        AuditStatistics statistics = new AuditStatistics(
                data.totalProcessedStories,
                data.totalProcessedStories - data.missingFrontendIssues.size(), // frontend issues found
                data.totalProcessedStories - data.missingBackendIssues.size(), // backend issues found
                data.missingFrontendIssues.size(),
                data.missingBackendIssues.size(),
                timestamp);

        return new AuditResult(
                data.missingFrontendIssues,
                data.missingBackendIssues,
                data.totalProcessedStories,
                timestamp,
                config,
                statistics);
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

    // Helper class for test data
    public static class AuditResultData {
        public final int totalProcessedStories;
        public final List<MissingIssue> missingFrontendIssues;
        public final List<MissingIssue> missingBackendIssues;
        public final int rateLimitDelayMs;
        public final int batchSize;

        public AuditResultData(int totalProcessedStories,
                List<MissingIssue> missingFrontendIssues,
                List<MissingIssue> missingBackendIssues,
                int rateLimitDelayMs,
                int batchSize) {
            this.totalProcessedStories = totalProcessedStories;
            this.missingFrontendIssues = missingFrontendIssues;
            this.missingBackendIssues = missingBackendIssues;
            this.rateLimitDelayMs = rateLimitDelayMs;
            this.batchSize = batchSize;
        }
    }
}