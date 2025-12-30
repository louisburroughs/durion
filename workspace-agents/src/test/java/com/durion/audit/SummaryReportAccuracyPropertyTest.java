package com.durion.audit;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.assertj.core.api.Assertions;

import com.durion.audit.AuditConfiguration;
import com.durion.audit.AuditMode;
import com.durion.audit.AuditResult;
import com.durion.audit.AuditStatistics;
import com.durion.audit.DefaultReportManager;
import com.durion.audit.MissingIssue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Property-based test for Property 15: Summary report accuracy
 * **Feature: missing-issues-audit, Property 15: Summary report accuracy**
 * **Validates: Requirements 6.3**
 * 
 * For any completed audit, generated summary reports should contain accurate statistics 
 * matching the actual audit results.
 */
public class SummaryReportAccuracyPropertyTest {

    @Property(tries = 100)
    @Label("Property 15: Summary report accuracy - Statistics match audit results")
    void summaryStatisticsShouldMatchAuditResults(
            @ForAll("auditResultData") AuditResultTestData auditData) throws IOException {
        
        // Given: A DefaultReportManager with temporary output directory
        Path tempDir = Files.createTempDirectory("test-summary-accuracy");
        DefaultReportManager reportManager = new DefaultReportManager(tempDir.toString());
        
        try {
            // Create AuditResult from test data
            AuditResult auditResult = createAuditResult(auditData);
            
            // When: Generating JSON summary report
            String jsonPath = tempDir.resolve("test-summary.json").toString();
            reportManager.generateJsonSummary(auditResult, jsonPath);
            
            // Then: JSON content should contain accurate statistics
            String jsonContent = Files.readString(Path.of(jsonPath));
            
            // Verify total counts match
            Assertions.assertThat(jsonContent).contains("\"totalProcessedStories\":" + auditData.totalProcessedStories);
            Assertions.assertThat(jsonContent).contains("\"totalMissingIssues\":" + auditData.getTotalMissingIssues());
            
            // Verify frontend statistics
            Assertions.assertThat(jsonContent).contains("\"missingFrontendCount\":" + auditData.missingFrontendIssues.size());
            Assertions.assertThat(jsonContent).contains("\"totalFrontendIssues\":" + auditData.getFrontendIssuesFound());
            
            // Verify backend statistics
            Assertions.assertThat(jsonContent).contains("\"missingBackendCount\":" + auditData.missingBackendIssues.size());
            Assertions.assertThat(jsonContent).contains("\"totalBackendIssues\":" + auditData.getBackendIssuesFound());
            
            // Verify completion percentages are calculated correctly
            double expectedFrontendCompletion = auditData.getFrontendCompletionPercentage();
            double expectedBackendCompletion = auditData.getBackendCompletionPercentage();
            double expectedOverallCompletion = auditData.getOverallCompletionPercentage();
            
            // Allow for small floating point differences
            String frontendPercentageStr = String.format("%.1f", expectedFrontendCompletion);
            String backendPercentageStr = String.format("%.1f", expectedBackendCompletion);
            String overallPercentageStr = String.format("%.1f", expectedOverallCompletion);
            
            Assertions.assertThat(jsonContent).contains("\"frontendCompletionPercentage\":" + frontendPercentageStr);
            Assertions.assertThat(jsonContent).contains("\"backendCompletionPercentage\":" + backendPercentageStr);
            Assertions.assertThat(jsonContent).contains("\"overallCompletionPercentage\":" + overallPercentageStr);
            
        } finally {
            // Cleanup
            deleteDirectoryRecursively(tempDir);
        }
    }
    
    @Property(tries = 50)
    @Label("Property 15: Summary report accuracy - Markdown statistics table is accurate")
    void markdownStatisticsTableShouldBeAccurate(
            @ForAll("auditResultData") AuditResultTestData auditData) throws IOException {
        
        // Given: A DefaultReportManager with temporary output directory
        Path tempDir = Files.createTempDirectory("test-markdown-accuracy");
        DefaultReportManager reportManager = new DefaultReportManager(tempDir.toString());
        
        try {
            // Create AuditResult from test data
            AuditResult auditResult = createAuditResult(auditData);
            
            // When: Generating Markdown summary report
            String markdownPath = tempDir.resolve("test-summary.md").toString();
            reportManager.generateMarkdownSummary(auditResult, markdownPath);
            
            // Then: Markdown content should contain accurate statistics table
            String markdownContent = Files.readString(Path.of(markdownPath));
            
            // Verify the statistics table contains correct values
            int frontendFound = auditData.getFrontendIssuesFound();
            int backendFound = auditData.getBackendIssuesFound();
            int totalFound = frontendFound + backendFound;
            
            int frontendMissing = auditData.missingFrontendIssues.size();
            int backendMissing = auditData.missingBackendIssues.size();
            int totalMissing = frontendMissing + backendMissing;
            
            double frontendCompletion = auditData.getFrontendCompletionPercentage();
            double backendCompletion = auditData.getBackendCompletionPercentage();
            double overallCompletion = auditData.getOverallCompletionPercentage();
            
            // Check Issues Found row
            String expectedIssuesFoundRow = String.format("| **Issues Found** | %d | %d | %d |", 
                frontendFound, backendFound, totalFound);
            Assertions.assertThat(markdownContent).contains(expectedIssuesFoundRow);
            
            // Check Missing Issues row
            String expectedMissingIssuesRow = String.format("| **Missing Issues** | %d | %d | %d |", 
                frontendMissing, backendMissing, totalMissing);
            Assertions.assertThat(markdownContent).contains(expectedMissingIssuesRow);
            
            // Check Completion % row
            String expectedCompletionRow = String.format("| **Completion %%** | %.1f%% | %.1f%% | %.1f%% |", 
                frontendCompletion, backendCompletion, overallCompletion);
            Assertions.assertThat(markdownContent).contains(expectedCompletionRow);
            
            // Verify summary information
            Assertions.assertThat(markdownContent).contains("**Total Processed Stories:** " + auditData.totalProcessedStories);
            Assertions.assertThat(markdownContent).contains("**Total Missing Issues:** " + auditData.getTotalMissingIssues());
            
        } finally {
            // Cleanup
            deleteDirectoryRecursively(tempDir);
        }
    }
    
    @Property(tries = 30)
    @Label("Property 15: Summary report accuracy - Missing issues lists are complete and accurate")
    void missingIssuesListsShouldBeCompleteAndAccurate(
            @ForAll("auditResultData") AuditResultTestData auditData) throws IOException {
        
        // Given: A DefaultReportManager with temporary output directory
        Path tempDir = Files.createTempDirectory("test-missing-issues-accuracy");
        DefaultReportManager reportManager = new DefaultReportManager(tempDir.toString());
        
        try {
            // Create AuditResult from test data
            AuditResult auditResult = createAuditResult(auditData);
            
            // When: Generating Markdown summary report
            String markdownPath = tempDir.resolve("test-summary.md").toString();
            reportManager.generateMarkdownSummary(auditResult, markdownPath);
            
            // Then: All missing issues should be listed accurately
            String markdownContent = Files.readString(Path.of(markdownPath));
            
            // Verify frontend missing issues section
            if (!auditData.missingFrontendIssues.isEmpty()) {
                String frontendSectionHeader = "## Missing Frontend Issues (" + auditData.missingFrontendIssues.size() + ")";
                Assertions.assertThat(markdownContent).contains(frontendSectionHeader);
                
                // Verify each frontend missing issue is listed
                for (MissingIssue issue : auditData.missingFrontendIssues) {
                    String issueEntry = String.format("- **Story #%d**: [%s](%s)", 
                        issue.getStoryNumber(), issue.getStoryTitle(), issue.getStoryUrl());
                    Assertions.assertThat(markdownContent).contains(issueEntry);
                    
                    String expectedTitle = String.format("  - **Expected Title**: %s", issue.getExpectedTitle());
                    Assertions.assertThat(markdownContent).contains(expectedTitle);
                    
                    String repository = String.format("  - **Repository**: %s", issue.getTargetRepository());
                    Assertions.assertThat(markdownContent).contains(repository);
                    
             
                }
            }
            
            // Verify backend missing issues section
            if (!auditData.missingBackendIssues.isEmpty()) {
                String backendSectionHeader = "## Missing Backend Issues (" + auditData.missingBackendIssues.size() + ")";
                Assertions.assertThat(markdownContent).contains(backendSectionHeader);
                
                // Verify each backend missing issue is listed
                for (MissingIssue issue : auditData.missingBackendIssues) {
                    String issueEntry = String.format("- **Story #%d**: [%s](%s)", 
                        issue.getStoryNumber(), issue.getStoryTitle(), issue.getStoryUrl());
                    Assertions.assertThat(markdownContent).contains(issueEntry);
                    
                    String expectedTitle = String.format("  - **Expected Title**: %s", issue.getExpectedTitle());
                    Assertions.assertThat(markdownContent).contains(expectedTitle);
                    
                    String repository = String.format("  - **Repository**: %s", issue.getTargetRepository());
                    Assertions.assertThat(markdownContent).contains(repository);
                    
                   
                }
            }
            
        } finally {
            // Cleanup
            deleteDirectoryRecursively(tempDir);
        }
    }
    
    @Property(tries = 20)
    @Label("Property 15: Summary report accuracy - Configuration details are accurate")
    void configurationDetailsShouldBeAccurate(
            @ForAll("auditResultData") AuditResultTestData auditData) throws IOException {
        
        // Given: A DefaultReportManager with temporary output directory
        Path tempDir = Files.createTempDirectory("test-config-accuracy");
        DefaultReportManager reportManager = new DefaultReportManager(tempDir.toString());
        
        try {
            // Create AuditResult from test data
            AuditResult auditResult = createAuditResult(auditData);
            
            // When: Generating both JSON and Markdown summary reports
            String jsonPath = tempDir.resolve("test-summary.json").toString();
            String markdownPath = tempDir.resolve("test-summary.md").toString();
            
            reportManager.generateJsonSummary(auditResult, jsonPath);
            reportManager.generateMarkdownSummary(auditResult, markdownPath);
            
            // Then: Configuration details should be accurate in both formats
            String jsonContent = Files.readString(Path.of(jsonPath));
            String markdownContent = Files.readString(Path.of(markdownPath));
            
            AuditConfiguration config = auditResult.getConfiguration();
            
            // Verify JSON configuration section
            Assertions.assertThat(jsonContent).contains("\"outputDirectory\":\"" + config.getOutputDirectory() + "\"");
            Assertions.assertThat(jsonContent).contains("\"auditMode\":\"" + config.getAuditMode().toString() + "\"");
            Assertions.assertThat(jsonContent).contains("\"rateLimitDelayMs\":" + config.getRateLimitDelayMs());
            Assertions.assertThat(jsonContent).contains("\"batchSize\":" + config.getBatchSize());
            
            // Verify Markdown configuration section
            Assertions.assertThat(markdownContent).contains("- **Output Directory**: " + config.getOutputDirectory());
            Assertions.assertThat(markdownContent).contains("- **Audit Mode**: " + config.getAuditMode());
            Assertions.assertThat(markdownContent).contains("- **Rate Limit Delay**: " + config.getRateLimitDelayMs() + "ms");
            Assertions.assertThat(markdownContent).contains("- **Batch Size**: " + config.getBatchSize());
            
            // Verify optional configuration fields if present
            if (config.getStartDate().isPresent() || config.getEndDate().isPresent()) {
                String dateRange = "- **Date Range**: " + 
                    config.getStartDate().map(Object::toString).orElse("(start)") + 
                    " to " + 
                    config.getEndDate().map(Object::toString).orElse("(end)");
                Assertions.assertThat(markdownContent).contains(dateRange);
            }
            
            if (config.getStoryRangeStart().isPresent() || config.getStoryRangeEnd().isPresent()) {
                String storyRange = "- **Story Range**: " + 
                    config.getStoryRangeStart().map(Object::toString).orElse("(start)") + 
                    " to " + 
                    config.getStoryRangeEnd().map(Object::toString).orElse("(end)");
                Assertions.assertThat(markdownContent).contains(storyRange);
            }
            
        } finally {
            // Cleanup
            deleteDirectoryRecursively(tempDir);
        }
    }
    
    @Property(tries = 15)
    @Label("Property 15: Summary report accuracy - Timestamp and metadata are consistent")
    void timestampAndMetadataShouldBeConsistent(
            @ForAll("auditResultData") AuditResultTestData auditData) throws IOException {
        
        // Given: A DefaultReportManager with temporary output directory
        Path tempDir = Files.createTempDirectory("test-timestamp-accuracy");
        DefaultReportManager reportManager = new DefaultReportManager(tempDir.toString());
        
        try {
            // Create AuditResult from test data
            LocalDateTime fixedTimestamp = LocalDateTime.of(2024, 12, 26, 10, 30, 45);
            AuditResult auditResult = createAuditResultWithTimestamp(auditData, fixedTimestamp);
            
            // When: Generating summary reports
            String jsonPath = tempDir.resolve("test-summary.json").toString();
            String markdownPath = tempDir.resolve("test-summary.md").toString();
            
            reportManager.generateJsonSummary(auditResult, jsonPath);
            reportManager.generateMarkdownSummary(auditResult, markdownPath);
            
            // Then: Timestamp should be consistent across reports
            String jsonContent = Files.readString(Path.of(jsonPath));
            String markdownContent = Files.readString(Path.of(markdownPath));
            
            // Verify JSON timestamp format
            Assertions.assertThat(jsonContent).contains("\"auditTimestamp\":\"2024-12-26T10:30:45\"");
            
            // Verify Markdown timestamp format
            Assertions.assertThat(markdownContent).contains("**Generated:** 2024-12-26 10:30:45");
            
            // Verify metadata consistency
            Assertions.assertThat(jsonContent).contains("\"totalProcessedStories\":" + auditData.totalProcessedStories);
            Assertions.assertThat(markdownContent).contains("**Total Processed Stories:** " + auditData.totalProcessedStories);
            
            Assertions.assertThat(jsonContent).contains("\"totalMissingIssues\":" + auditData.getTotalMissingIssues());
            Assertions.assertThat(markdownContent).contains("**Total Missing Issues:** " + auditData.getTotalMissingIssues());
            
        } finally {
            // Cleanup
            deleteDirectoryRecursively(tempDir);
        }
    }

    @Provide
    Arbitrary<AuditResultTestData> auditResultData() {
        return Combinators.combine(
            Arbitraries.integers().between(10, 100), // totalProcessedStories
            Arbitraries.integers().between(0, 20),   // missingFrontendCount
            Arbitraries.integers().between(0, 20),   // missingBackendCount
            Arbitraries.integers().between(1000, 5000), // rateLimitDelayMs
            Arbitraries.integers().between(5, 20)    // batchSize
        ).as((totalStories, frontendMissing, backendMissing, rateLimitDelay, batchSize) -> {
            
            List<MissingIssue> frontendIssues = new ArrayList<>();
            for (int i = 0; i < frontendMissing; i++) {
                frontendIssues.add(new MissingIssue(
                    100 + i,
                    "Frontend Story " + (100 + i),
                    "https://github.com/louisburroughs/durion/issues/" + (100 + i),
                    "frontend",
                    "louisburroughs/durion-moqui-frontend",
                    "[FRONTEND] Frontend Story " + (100 + i)
                ));
            }
            
            List<MissingIssue> backendIssues = new ArrayList<>();
            for (int i = 0; i < backendMissing; i++) {
                backendIssues.add(new MissingIssue(
                    200 + i,
                    "Backend Story " + (200 + i),
                    "https://github.com/louisburroughs/durion/issues/" + (200 + i),
                    "backend",
                    "louisburroughs/durion-positivity-backend",
                    "[BACKEND] Backend Story " + (200 + i)
                ));
            }
            
            return new AuditResultTestData(
                totalStories,
                frontendIssues,
                backendIssues,
                rateLimitDelay,
                batchSize
            );
        });
    }
    
    // Helper method to create AuditResult from test data
    private AuditResult createAuditResult(AuditResultTestData data) {
        return createAuditResultWithTimestamp(data, LocalDateTime.now());
    }
    
    // Helper method to create AuditResult with specific timestamp
    private AuditResult createAuditResultWithTimestamp(AuditResultTestData data, LocalDateTime timestamp) {
        AuditConfiguration config = new AuditConfiguration.Builder()
            .githubToken("test-token")
            .outputDirectory("/tmp/test")
            .auditMode(AuditMode.FULL_AUDIT)
            .rateLimitDelayMs(data.rateLimitDelayMs)
            .batchSize(data.batchSize)
            .build();
        
        AuditStatistics statistics = new AuditStatistics(
            data.totalProcessedStories,
            data.getFrontendIssuesFound(),
            data.getBackendIssuesFound(),
            data.missingFrontendIssues.size(),
            data.missingBackendIssues.size(),
            timestamp
        );
        
        return new AuditResult(
            data.missingFrontendIssues,
            data.missingBackendIssues,
            data.totalProcessedStories,
            timestamp,
            config,
            statistics
        );
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
    public static class AuditResultTestData {
        public final int totalProcessedStories;
        public final List<MissingIssue> missingFrontendIssues;
        public final List<MissingIssue> missingBackendIssues;
        public final int rateLimitDelayMs;
        public final int batchSize;
        
        public AuditResultTestData(int totalProcessedStories, 
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
        
        public int getTotalMissingIssues() {
            return missingFrontendIssues.size() + missingBackendIssues.size();
        }
        
        public int getFrontendIssuesFound() {
            return totalProcessedStories - missingFrontendIssues.size();
        }
        
        public int getBackendIssuesFound() {
            return totalProcessedStories - missingBackendIssues.size();
        }
        
        public double getFrontendCompletionPercentage() {
            if (totalProcessedStories == 0) return 0.0;
            return (getFrontendIssuesFound() * 100.0) / totalProcessedStories;
        }
        
        public double getBackendCompletionPercentage() {
            if (totalProcessedStories == 0) return 0.0;
            return (getBackendIssuesFound() * 100.0) / totalProcessedStories;
        }
        
        public double getOverallCompletionPercentage() {
            if (totalProcessedStories == 0) return 0.0;
            int totalExpectedIssues = totalProcessedStories * 2; // frontend + backend
            int totalCompletedIssues = getFrontendIssuesFound() + getBackendIssuesFound();
            return (totalCompletedIssues * 100.0) / totalExpectedIssues;
        }
    }
}