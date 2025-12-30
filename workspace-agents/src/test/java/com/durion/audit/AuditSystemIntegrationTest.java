package com.durion.audit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * Integration tests for the Missing Issues Audit System.
 * 
 * Tests complete audit workflows from file reading to report generation
 * using mock GitHub API responses for consistent testing.
 * 
 * Requirements: All - Complete audit workflow validation
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AuditSystemIntegrationTest {

    private static Path tempDir;
    private static Path processedIssuesFile;
    private static Path storySequenceFile;
    private static Path outputDir;

    @BeforeAll
    static void setUpTestEnvironment() throws IOException {
        // Create temporary directory structure for testing
        tempDir = Files.createTempDirectory("audit-integration-test");
        outputDir = tempDir.resolve("missing-issues");
        Files.createDirectories(outputDir);

        // Create test processed-issues.txt
        processedIssuesFile = tempDir.resolve("processed-issues.txt");
        createTestProcessedIssuesFile();

        // Create test story-sequence.md
        storySequenceFile = tempDir.resolve("story-sequence.md");
        createTestStorySequenceFile();

        System.out.println("🧪 Integration Test Environment Created");
        System.out.println("   📁 Temp Directory: " + tempDir);
        System.out.println("   📄 Processed Issues: " + processedIssuesFile);
        System.out.println("   📄 Story Sequence: " + storySequenceFile);
        System.out.println("   📁 Output Directory: " + outputDir);
    }

    @AfterAll
    static void cleanUpTestEnvironment() throws IOException {
        // Clean up temporary files
        if (tempDir != null && Files.exists(tempDir)) {
            Files.walk(tempDir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            System.err.println("Failed to delete: " + path);
                        }
                    });
        }
        System.out.println("🧹 Integration Test Environment Cleaned Up");
    }

    private static void createTestProcessedIssuesFile() throws IOException {
        // Create a test file with 10 processed issues
        StringBuilder content = new StringBuilder();
        for (int i = 10; i >= 1; i--) {
            content.append(i).append("\n");
        }
        Files.writeString(processedIssuesFile, content.toString());
    }

    private static void createTestStorySequenceFile() throws IOException {
        StringBuilder content = new StringBuilder();
        content.append("# Story Sequence\n\n");

        for (int i = 10; i >= 1; i--) {
            content.append("### Story #").append(i).append(": [STORY] Test Story ").append(i).append(" Title\n");
            content.append("**URL**: https://github.com/test/repo/issues/").append(i).append("\n");
            content.append("**Domain**: test-domain\n");
            content.append("**Status**: Processed\n\n");
        }

        Files.writeString(storySequenceFile, content.toString());
    }

    @Test
    @Order(1)
    @DisplayName("Test ProcessedIssuesReader reads test file correctly")
    void testProcessedIssuesReaderIntegration() throws Exception {
        ProcessedIssuesReader reader = new ProcessedIssuesReader();
        List<Integer> issues = reader.readProcessedIssues(processedIssuesFile.toString());

        assertNotNull(issues, "Issues list should not be null");
        assertEquals(10, issues.size(), "Should read 10 processed issues");
        assertTrue(issues.contains(1), "Should contain issue #1");
        assertTrue(issues.contains(10), "Should contain issue #10");

        System.out.println("✅ ProcessedIssuesReader integration test passed");
    }

    @Test
    @Order(2)
    @DisplayName("Test StorySequenceParser parses test file correctly")
    void testStorySequenceParserIntegration() throws Exception {
        StorySequenceParser parser = new StorySequenceParser();
        Map<Integer, StoryMetadata> metadata = parser.parseStorySequence(storySequenceFile.toString());

        assertNotNull(metadata, "Metadata map should not be null");
        assertEquals(10, metadata.size(), "Should parse 10 stories");

        // Verify story #5 metadata
        StoryMetadata story5 = metadata.get(5);
        assertNotNull(story5, "Story #5 should exist");
        assertTrue(story5.getTitle().contains("Test Story 5"), "Story #5 title should be correct");
        assertEquals("https://github.com/test/repo/issues/5", story5.getUrl(), "Story #5 URL should be correct");

        System.out.println("✅ StorySequenceParser integration test passed");
    }

    @Test
    @Order(3)
    @DisplayName("Test AuditEngine performs audit correctly")
    void testAuditEngineIntegration() throws Exception {
        // Setup
        ProcessedIssuesReader reader = new ProcessedIssuesReader();
        List<Integer> processedIssues = reader.readProcessedIssues(processedIssuesFile.toString());

        StorySequenceParser parser = new StorySequenceParser();
        Map<Integer, StoryMetadata> storyMetadata = parser.parseStorySequence(storySequenceFile.toString());

        // Create mock GitHub issues - only stories 1-5 have frontend, only 3-7 have
        // backend
        List<GitHubIssue> frontendIssues = createMockGitHubIssues(Arrays.asList(1, 2, 3, 4, 5), "[FRONTEND] [STORY]");
        List<GitHubIssue> backendIssues = createMockGitHubIssues(Arrays.asList(3, 4, 5, 6, 7), "[BACKEND] [STORY]");

        AuditConfiguration config = AuditConfiguration.builder()
                .githubToken("test-token")
                .outputDirectory(outputDir.toString())
                .build();

        AuditEngine engine = new AuditEngine();

        // Execute audit
        AuditResult result = engine.performAudit(
                processedIssues, frontendIssues, backendIssues, storyMetadata, config);

        // Verify results
        assertNotNull(result, "Audit result should not be null");
        assertEquals(10, result.getTotalProcessedStories(), "Should have 10 processed stories");

        // Missing frontend: 6, 7, 8, 9, 10 (5 issues)
        assertEquals(5, result.getMissingFrontendIssues().size(), "Should have 5 missing frontend issues");

        // Missing backend: 1, 2, 8, 9, 10 (5 issues)
        assertEquals(5, result.getMissingBackendIssues().size(), "Should have 5 missing backend issues");

        // Verify specific missing issues
        Set<Integer> missingFrontendNumbers = new HashSet<>();
        for (MissingIssue issue : result.getMissingFrontendIssues()) {
            missingFrontendNumbers.add(issue.getStoryNumber());
        }
        assertTrue(missingFrontendNumbers.contains(6), "Story #6 should be missing frontend");
        assertTrue(missingFrontendNumbers.contains(10), "Story #10 should be missing frontend");

        System.out.println("✅ AuditEngine integration test passed");
        System.out.println("   📊 Missing Frontend: " + result.getMissingFrontendIssues().size());
        System.out.println("   📊 Missing Backend: " + result.getMissingBackendIssues().size());
    }

    @Test
    @Order(4)
    @DisplayName("Test DefaultReportManager generates reports correctly")
    void testReportManagerIntegration() throws Exception {
        // Create a test audit result
        AuditConfiguration config = AuditConfiguration.builder()
                .githubToken("test-token")
                .outputDirectory(outputDir.toString())
                .build();

        List<MissingIssue> missingFrontend = Arrays.asList(
                new MissingIssue(6, "Test Story 6", "https://github.com/test/repo/issues/6",
                        "frontend", "test/frontend-repo", "[FRONTEND] Test Story 6"),
                new MissingIssue(7, "Test Story 7", "https://github.com/test/repo/issues/7",
                        "frontend", "test/frontend-repo", "[FRONTEND] Test Story 7"));

        List<MissingIssue> missingBackend = Arrays.asList(
                new MissingIssue(1, "Test Story 1", "https://github.com/test/repo/issues/1",
                        "backend", "test/backend-repo", "[BACKEND] Test Story 1"));

        LocalDateTime timestamp = LocalDateTime.now();
        AuditStatistics stats = new AuditStatistics(10, 5, 5, 2, 1, timestamp);

        AuditResult result = new AuditResult(
                missingFrontend, missingBackend, 10, timestamp, config, stats);

        // Generate reports
        DefaultReportManager reportManager = new DefaultReportManager(outputDir.toString());
        List<String> reportPaths = reportManager.generateReports(result);

        // Verify reports were generated
        assertNotNull(reportPaths, "Report paths should not be null");
        assertFalse(reportPaths.isEmpty(), "Should generate at least one report");

        // Verify files exist
        for (String path : reportPaths) {
            assertTrue(Files.exists(Path.of(path)), "Report file should exist: " + path);
            assertTrue(Files.size(Path.of(path)) > 0, "Report file should not be empty: " + path);
        }

        System.out.println("✅ ReportManager integration test passed");
        System.out.println("   📄 Generated " + reportPaths.size() + " report files");
    }

    @Test
    @Order(5)
    @DisplayName("Test complete audit workflow end-to-end")
    void testCompleteAuditWorkflow() throws Exception {
        System.out.println("\n🔄 Testing Complete Audit Workflow");

        // Step 1: Read processed issues
        ProcessedIssuesReader reader = new ProcessedIssuesReader();
        List<Integer> processedIssues = reader.readProcessedIssues(processedIssuesFile.toString());
        System.out.println("   ✅ Step 1: Read " + processedIssues.size() + " processed issues");

        // Step 2: Parse story metadata
        StorySequenceParser parser = new StorySequenceParser();
        Map<Integer, StoryMetadata> storyMetadata = parser.parseStorySequence(storySequenceFile.toString());
        System.out.println("   ✅ Step 2: Parsed " + storyMetadata.size() + " story metadata entries");

        // Step 3: Create mock implementation issues
        List<GitHubIssue> frontendIssues = createMockGitHubIssues(Arrays.asList(1, 2, 3), "[FRONTEND]");
        List<GitHubIssue> backendIssues = createMockGitHubIssues(Arrays.asList(1, 2, 3, 4, 5), "[BACKEND]");
        System.out.println("   ✅ Step 3: Created mock GitHub issues (Frontend: " +
                frontendIssues.size() + ", Backend: " + backendIssues.size() + ")");

        // Step 4: Configure and run audit
        AuditConfiguration config = AuditConfiguration.builder()
                .githubToken("test-token")
                .outputDirectory(outputDir.toString())
                .build();

        AuditEngine engine = new AuditEngine();
        AuditResult result = engine.performAudit(
                processedIssues, frontendIssues, backendIssues, storyMetadata, config);
        System.out.println("   ✅ Step 4: Audit completed - Found " +
                result.getTotalMissingIssues() + " missing issues");

        // Step 5: Generate reports
        DefaultReportManager reportManager = new DefaultReportManager(outputDir.toString());
        List<String> reportPaths = reportManager.generateReports(result);
        System.out.println("   ✅ Step 5: Generated " + reportPaths.size() + " reports");

        // Verify complete workflow results
        assertNotNull(result, "Audit result should not be null");
        assertTrue(result.getTotalMissingIssues() > 0, "Should find missing issues");
        assertFalse(reportPaths.isEmpty(), "Should generate reports");

        // Verify statistics
        AuditStatistics stats = result.getStatistics();
        assertNotNull(stats, "Statistics should not be null");
        assertEquals(10, stats.getTotalProcessedStories(), "Should have correct processed count");

        System.out.println("\n✅ Complete Audit Workflow Test PASSED");
        System.out.println("   📊 Total Processed: " + stats.getTotalProcessedStories());
        System.out.println("   📊 Missing Frontend: " + stats.getMissingFrontendCount());
        System.out.println("   📊 Missing Backend: " + stats.getMissingBackendCount());
        System.out.println(
                "   📊 Frontend Completion: " + String.format("%.1f%%", stats.getFrontendCompletionPercentage()));
        System.out.println(
                "   📊 Backend Completion: " + String.format("%.1f%%", stats.getBackendCompletionPercentage()));
    }

    @Test
    @Order(6)
    @DisplayName("Test error handling for missing files")
    void testErrorHandlingMissingFiles() {
        ProcessedIssuesReader reader = new ProcessedIssuesReader();

        // Test with non-existent file
        assertThrows(ProcessedIssuesReader.ProcessedIssuesReaderException.class, () -> {
            reader.readProcessedIssues("/non/existent/path/processed-issues.txt");
        }, "Should throw exception for missing file");

        StorySequenceParser parser = new StorySequenceParser();
        assertThrows(StorySequenceParser.StorySequenceParserException.class, () -> {
            parser.parseStorySequence("/non/existent/path/story-sequence.md");
        }, "Should throw exception for missing story sequence file");

        System.out.println("✅ Error handling for missing files test passed");
    }

    @Test
    @Order(7)
    @DisplayName("Test error handling for invalid file content")
    void testErrorHandlingInvalidContent() throws IOException {
        // Create file with invalid content
        Path invalidFile = tempDir.resolve("invalid-processed-issues.txt");
        Files.writeString(invalidFile, "not-a-number\nabc\n123");

        ProcessedIssuesReader reader = new ProcessedIssuesReader();
        assertThrows(ProcessedIssuesReader.ProcessedIssuesReaderException.class, () -> {
            reader.readProcessedIssues(invalidFile.toString());
        }, "Should throw exception for invalid content");

        // Clean up
        Files.deleteIfExists(invalidFile);

        System.out.println("✅ Error handling for invalid content test passed");
    }

    @Test
    @Order(8)
    @DisplayName("Test AuditConfiguration validation")
    void testAuditConfigurationValidation() {
        AuditEngine engine = new AuditEngine();

        // Test null configuration - throws NullPointerException due to logger access
        assertThrows(NullPointerException.class, () -> {
            engine.validateConfiguration(null);
        }, "Should reject null configuration");

        // Test missing token
        assertThrows(IllegalArgumentException.class, () -> {
            AuditConfiguration config = AuditConfiguration.builder()
                    .githubToken("")
                    .outputDirectory("test")
                    .build();
            engine.validateConfiguration(config);
        }, "Should reject empty token");

        // Test valid configuration
        AuditConfiguration validConfig = AuditConfiguration.builder()
                .githubToken("test-token")
                .outputDirectory("test-output")
                .build();

        assertDoesNotThrow(() -> {
            engine.validateConfiguration(validConfig);
        }, "Should accept valid configuration");

        System.out.println("✅ AuditConfiguration validation test passed");
    }

    @Test
    @Order(9)
    @DisplayName("Test incremental audit filtering by story range")
    void testIncrementalAuditFilteringByRange() throws Exception {
        ProcessedIssuesReader reader = new ProcessedIssuesReader();
        List<Integer> processedIssues = reader.readProcessedIssues(processedIssuesFile.toString());

        AuditLogger logger = new AuditLogger();
        IncrementalAuditFilter filter = new IncrementalAuditFilter(logger);

        // Filter to stories 3-7
        List<Integer> filtered = filter.filterByStoryRange(processedIssues, 3, 7);

        assertEquals(5, filtered.size(), "Should have 5 stories in range 3-7");
        assertTrue(filtered.contains(3), "Should contain story #3");
        assertTrue(filtered.contains(7), "Should contain story #7");
        assertFalse(filtered.contains(1), "Should not contain story #1");
        assertFalse(filtered.contains(10), "Should not contain story #10");

        System.out.println("✅ Incremental audit filtering by range test passed");
    }

    @Test
    @Order(10)
    @DisplayName("Test audit with empty implementation lists")
    void testAuditWithEmptyImplementations() throws Exception {
        ProcessedIssuesReader reader = new ProcessedIssuesReader();
        List<Integer> processedIssues = reader.readProcessedIssues(processedIssuesFile.toString());

        StorySequenceParser parser = new StorySequenceParser();
        Map<Integer, StoryMetadata> storyMetadata = parser.parseStorySequence(storySequenceFile.toString());

        AuditConfiguration config = AuditConfiguration.builder()
                .githubToken("test-token")
                .outputDirectory(outputDir.toString())
                .build();

        AuditEngine engine = new AuditEngine();

        // Run audit with no implementation issues
        AuditResult result = engine.performAudit(
                processedIssues,
                Collections.emptyList(),
                Collections.emptyList(),
                storyMetadata,
                config);

        // All processed issues should be missing
        assertEquals(10, result.getMissingFrontendIssues().size(),
                "All 10 stories should be missing frontend issues");
        assertEquals(10, result.getMissingBackendIssues().size(),
                "All 10 stories should be missing backend issues");
        assertEquals(20, result.getTotalMissingIssues(),
                "Total missing should be 20 (10 frontend + 10 backend)");

        System.out.println("✅ Audit with empty implementations test passed");
    }

    /**
     * Helper method to create mock GitHub issues for testing.
     */
    private List<GitHubIssue> createMockGitHubIssues(List<Integer> storyNumbers, String prefix) {
        List<GitHubIssue> issues = new ArrayList<>();
        for (Integer storyNumber : storyNumbers) {
            // Create title that matches story metadata format
            // Story metadata has titles like "[STORY] Test Story X Title"
            // After getCleanTitle(), it becomes "Test Story X Title"
            // So GitHub issue should be: "[PREFIX] Test Story X Title" to match
            issues.add(new MockGitHubIssue(
                    storyNumber + 1000,
                    prefix + " Test Story " + storyNumber + " Title",
                    "Test body for story #" + storyNumber,
                    "https://github.com/test/repo/issues/" + (storyNumber + 1000),
                    Collections.emptyList()));
        }
        return issues;
    }

    /**
     * Mock implementation of GitHubIssue for testing.
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
            this.labels = labels;
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
    }
}
