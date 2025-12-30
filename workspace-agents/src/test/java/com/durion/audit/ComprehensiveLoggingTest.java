package com.durion.audit;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.durion.audit.AuditConfiguration;
import com.durion.audit.AuditEngine;
import com.durion.audit.AuditMode;
import com.durion.audit.AuditResult;
import com.durion.audit.GitHubIssue;
import com.durion.audit.MissingIssue;
import com.durion.audit.StoryMetadata;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;

/**
 * **Feature: missing-issues-audit, Property 14: Comprehensive logging**
 * 
 * Property-based test that validates the audit system's comprehensive logging
 * capabilities.
 * This test ensures that for any audit execution, the system logs all API
 * requests,
 * errors with context, and issue creation attempts with sufficient detail for
 * debugging.
 * 
 * **Validates: Requirements 6.1, 6.2, 6.4**
 */
public class ComprehensiveLoggingTest {

    private AuditEngine auditEngine;
    private AuditConfiguration testConfiguration;
    private ByteArrayOutputStream logOutput;
    private PrintStream originalOut;

    @BeforeEach
    void setUp() {
        // Capture console output for log verification
        logOutput = new ByteArrayOutputStream();
        originalOut = System.out;
        System.setOut(new PrintStream(logOutput));

        auditEngine = new AuditEngine();
        testConfiguration = AuditConfiguration.builder()
                .githubToken("test-token")
                .outputDirectory("test-output")
                .build();
    }

    @Test
    void tearDown() {
        // Restore original output
        if (originalOut != null) {
            System.setOut(originalOut);
        }
    }

    @Property(tries = 50)
    @Label("Comprehensive logging should log all audit operations")
    void auditLoggingShouldCaptureAllOperations(
            @ForAll @Size(min = 1, max = 20) List<@IntRange(min = 1, max = 100) Integer> processedIssues,
            @ForAll @Size(max = 10) List<@IntRange(min = 1, max = 100) Integer> frontendImplementedStories,
            @ForAll @Size(max = 10) List<@IntRange(min = 1, max = 100) Integer> backendImplementedStories) {

        ensureAuditEngineInitialized();

        // Create test data
        Map<Integer, StoryMetadata> storyMetadata = createStoryMetadata(processedIssues);
        List<GitHubIssue> frontendIssues = createGitHubIssues(frontendImplementedStories, "[FRONTEND] [STORY]");
        List<GitHubIssue> backendIssues = createGitHubIssues(backendImplementedStories, "[BACKEND] [STORY]");

        // Perform audit
        AuditResult result = auditEngine.performAudit(
                processedIssues, frontendIssues, backendIssues, storyMetadata, testConfiguration);

        String logContent = logOutput.toString();

        // Property: All audit operations should be logged
        assert logContent.contains("MISSING ISSUES AUDIT SESSION STARTED") : "Audit session start should be logged";

        assert logContent.contains("AUDIT PROCESS INITIALIZATION") : "Audit initialization should be logged";

        assert logContent.contains("PROGRESS UPDATE") : "Progress updates should be logged";

        assert logContent.contains("MISSING ISSUE DETECTION RESULTS") : "Detection results should be logged";

        assert logContent.contains("AUDIT SUMMARY") : "Audit summary should be logged";

        // Property: Log should contain session metadata
        assert logContent.contains("Session ID: AUDIT-") : "Session ID should be logged";

        assert logContent.contains("Processed Issues Count: " + processedIssues.size())
                : "Processed issues count should be logged";

        assert logContent.contains("Total Missing Issues: " + result.getTotalMissingIssues())
                : "Total missing issues should be logged";

        // Property: Log should contain timestamps on at least one line
        boolean hasTimestamp = logContent.lines()
                .anyMatch(line -> line.matches(".*\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}.*"));

        assert hasTimestamp : "Timestamps should be present in logs";
    }

    @Property(tries = 30)
    @Label("Comprehensive logging should log configuration validation")
    void configurationValidationShouldBeLogged(
            @ForAll("validAuditConfiguration") AuditConfiguration configuration) {

        ensureAuditEngineInitialized();

        // Validate configuration (which should trigger logging)
        auditEngine.validateConfiguration(configuration);

        String logContent = logOutput.toString();

        // Property: Configuration validation should be logged
        assert logContent.contains("CONFIGURATION VALIDATION") : "Configuration validation should be logged";

        assert logContent.contains("Audit Mode: " + configuration.getAuditMode()) : "Audit mode should be logged";

        assert logContent.contains("Output Directory: " + configuration.getOutputDirectory())
                : "Output directory should be logged";

        assert logContent.contains("Use Cache: " + configuration.isUseCache()) : "Cache setting should be logged";

        assert logContent.contains("Configuration validated successfully") : "Successful validation should be logged";
    }

    @Property(tries = 20)
    @Label("Comprehensive logging should log errors with context")
    void errorLoggingShouldIncludeContext(
            @ForAll("invalidAuditConfiguration") AuditConfiguration invalidConfig) {

        ensureAuditEngineInitialized();

        try {
            auditEngine.validateConfiguration(invalidConfig);
            // Should not reach here if configuration is truly invalid
            assert false : "Expected validation to fail for invalid configuration";
        } catch (IllegalArgumentException e) {
            // Expected exception
        }

        String logContent = logOutput.toString();

        // Property: Errors should be logged with context
        assert logContent.contains("ERROR #") : "Error should be logged with error number";

        assert logContent.contains("Operation: Configuration Validation") : "Error operation should be logged";

        assert logContent.contains("Error Type: IllegalArgumentException") : "Error type should be logged";

        assert logContent.contains("Context:") : "Error context should be logged";

        assert logContent.contains("Session: AUDIT-") : "Session ID should be included in error logs";
    }

    @Property(tries = 20)
    @Label("Comprehensive logging should log issue creation attempts")
    void issueCreationLoggingShouldCaptureAttempts(
            @ForAll @IntRange(min = 1, max = 100) int storyNumber,
            @ForAll("repositoryType") String repositoryType,
            @ForAll boolean success) {

        ensureAuditEngineInitialized();

        MissingIssue missingIssue = new MissingIssue(
                storyNumber,
                "Test Story #" + storyNumber,
                "https://github.com/test/repo/issues/" + storyNumber,
                repositoryType,
                "test/repository",
                "[" + repositoryType.toUpperCase() + "] Test Story #" + storyNumber);

        String errorMessage = success ? null : "Test error message";
        auditEngine.logIssueCreation(missingIssue, success, errorMessage);

        String logContent = logOutput.toString();

        // Property: Issue creation attempts should be logged
        assert logContent.contains("ISSUE CREATION") : "Issue creation should be logged";

        assert logContent.contains("Story: #" + storyNumber) : "Story number should be logged";

        assert logContent.contains("Repository: test/repository") : "Target repository should be logged";

        assert logContent.contains("Type: " + repositoryType) : "Repository type should be logged";

        if (success) {
            assert logContent.contains("Issue created successfully") : "Successful creation should be logged";
        } else {
            assert logContent.contains("Issue creation failed") : "Failed creation should be logged";
            assert logContent.contains("Error: " + errorMessage) : "Error message should be logged";
        }
    }

    @Property(tries = 20)
    @Label("Comprehensive logging should maintain session tracking")
    void sessionTrackingShouldBeMaintained(
            @ForAll @Size(min = 1, max = 10) List<@IntRange(min = 1, max = 50) Integer> processedIssues) {

        ensureAuditEngineInitialized();

        Map<Integer, StoryMetadata> storyMetadata = createStoryMetadata(processedIssues);
        List<GitHubIssue> emptyIssues = Collections.emptyList();

        // Perform audit
        auditEngine.performAudit(processedIssues, emptyIssues, emptyIssues, storyMetadata, testConfiguration);

        // End session
        auditEngine.endSession();

        String logContent = logOutput.toString();

        // Property: Session should be tracked consistently
        String sessionId = extractSessionId(logContent);
        assert sessionId != null : "Session ID should be extractable from logs";

        // Count occurrences of session ID in logs
        long sessionIdCount = logContent.lines()
                .filter(line -> line.contains(sessionId))
                .count();

        assert sessionIdCount >= 2 : "Session ID should appear multiple times throughout the audit";

        // Property: Session should have clear start and end
        assert logContent.contains("SESSION STARTED") : "Session start should be logged";

        assert logContent.contains("SESSION COMPLETED") : "Session end should be logged";
    }

    /**
     * Extracts session ID from log content for verification.
     */
    private String extractSessionId(String logContent) {
        return logContent.lines()
                .filter(line -> line.contains("Session ID: AUDIT-"))
                .findFirst()
                .map(line -> line.substring(line.indexOf("AUDIT-")))
                .map(sessionPart -> sessionPart.split("\\s+")[0])
                .orElse(null);
    }

    /**
     * Creates story metadata for testing.
     */
    private Map<Integer, StoryMetadata> createStoryMetadata(List<Integer> storyNumbers) {
        Map<Integer, StoryMetadata> metadata = new HashMap<>();
        for (Integer storyNumber : storyNumbers) {
            metadata.put(storyNumber, new StoryMetadata(
                    storyNumber,
                    "[STORY] Test Story #" + storyNumber + ": Sample Title",
                    "https://github.com/louisburroughs/durion/issues/" + storyNumber,
                    true,
                    true));
        }
        return metadata;
    }

    /**
     * Creates GitHub issues for testing.
     */
    private List<GitHubIssue> createGitHubIssues(List<Integer> storyNumbers, String prefix) {
        return storyNumbers.stream()
                .map(storyNumber -> {
                    String title = prefix + " #" + storyNumber + " Test Story #" + storyNumber + ": Sample Title";
                    return new TestGitHubIssue(
                            storyNumber + 1000,
                            title,
                            "Test body for story #" + storyNumber,
                            "https://github.com/test/repo/issues/" + (storyNumber + 1000),
                            Collections.emptyList());
                })
                .collect(Collectors.toList());
    }

    private void ensureAuditEngineInitialized() {
        if (logOutput == null) {
            logOutput = new ByteArrayOutputStream();
            if (originalOut == null) {
                originalOut = System.out;
            }
            System.setOut(new PrintStream(logOutput));
        }

        if (auditEngine == null) {
            auditEngine = new AuditEngine();
        }

        if (testConfiguration == null) {
            testConfiguration = AuditConfiguration.builder()
                    .githubToken("test-token")
                    .outputDirectory("test-output")
                    .build();
        }
    }

    @Provide
    Arbitrary<AuditConfiguration> validAuditConfiguration() {
        return Arbitraries.create(() -> AuditConfiguration.builder()
                .githubToken("ghp_test_token_1234567890123456789012345678")
                .outputDirectory("test-output-" + System.currentTimeMillis())
                .auditMode(AuditMode.FULL_AUDIT)
                .useCache(Arbitraries.of(true, false).sample())
                .rateLimitDelayMs(Arbitraries.integers().between(1000, 5000).sample())
                .batchSize(Arbitraries.integers().between(1, 10).sample())
                .build());
    }

    @Provide
    Arbitrary<AuditConfiguration> invalidAuditConfiguration() {
        return Arbitraries.oneOf(
                // Empty token
                Arbitraries.create(() -> AuditConfiguration.builder()
                        .githubToken("")
                        .outputDirectory("test-output")
                        .build()),
                // Negative rate limit delay
                Arbitraries.create(() -> AuditConfiguration.builder()
                        .githubToken("test-token")
                        .outputDirectory("test-output")
                        .rateLimitDelayMs(-1000)
                        .build()),
                // Zero batch size
                Arbitraries.create(() -> AuditConfiguration.builder()
                        .githubToken("test-token")
                        .outputDirectory("test-output")
                        .batchSize(0)
                        .build()));
    }

    @Provide
    Arbitrary<String> repositoryType() {
        return Arbitraries.of("frontend", "backend");
    }
}

/**
 * Test implementation of GitHubIssue for property-based testing.
 */
class TestGitHubIssueForLogging implements GitHubIssue {
    private final int number;
    private final String title;
    private final String body;
    private final String url;
    private final List<String> labels;

    public TestGitHubIssueForLogging(int number, String title, String body, String url, List<String> labels) {
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

    @Override
    public String toString() {
        return String.format("TestGitHubIssue{#%d: %s}", number, title);
    }
}