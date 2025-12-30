package com.durion.audit;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;

import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;

/**
 * Test implementation of GitHubIssue for property-based testing.
 */
class TestGitHubIssue implements GitHubIssue {
        private final int number;
        private final String title;
        private final String body;
        private final String url;
        private final List<String> labels;

        public TestGitHubIssue(int number, String title, String body, String url, List<String> labels) {
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

/**
 * **Feature: missing-issues-audit, Property 3: Missing issue detection
 * completeness**
 * 
 * Property-based test that validates the core audit engine's ability to
 * correctly
 * identify missing implementation issues. This test ensures that for any set of
 * processed issues and implementation issues, the system identifies all and
 * only
 * those processed issues that lack corresponding implementation issues in both
 * repositories.
 * 
 * **Validates: Requirements 1.3, 1.4**
 */
public class MissingIssueDetectionCompletenessTest {

        private AuditEngine auditEngine;
        private AuditConfiguration testConfiguration;

        @BeforeEach
        void setUp() {
                auditEngine = new AuditEngine();
                testConfiguration = AuditConfiguration.builder()
                                .githubToken("test-token")
                                .outputDirectory("test-output")
                                .build();
        }

        @Property(tries = 100)
        @Label("Missing issue detection should identify all and only missing issues")
        void missingIssueDetectionCompleteness(
                        @ForAll @Size(min = 1, max = 50) List<@IntRange(min = 1, max = 1000) Integer> processedIssues,
                        @ForAll @Size(max = 30) List<@IntRange(min = 1, max = 1000) Integer> frontendImplementedStories,
                        @ForAll @Size(max = 30) List<@IntRange(min = 1, max = 1000) Integer> backendImplementedStories) {

                ensureAuditEngineInitialized();

                // Create story metadata for all processed issues
                Map<Integer, StoryMetadata> storyMetadata = createStoryMetadata(processedIssues);

                // Create GitHub issues for implemented stories
                List<GitHubIssue> frontendIssues = createGitHubIssues(frontendImplementedStories, "[FRONTEND] [STORY]");
                List<GitHubIssue> backendIssues = createGitHubIssues(backendImplementedStories, "[BACKEND] [STORY]");

                // Perform audit
                AuditResult result = auditEngine.performAudit(
                                processedIssues, frontendIssues, backendIssues, storyMetadata, testConfiguration);

                // Verify completeness: missing issues should be exactly those processed but not
                // implemented
                Set<Integer> frontendImplementedSet = new HashSet<>(frontendImplementedStories);
                Set<Integer> backendImplementedSet = new HashSet<>(backendImplementedStories);

                Set<Integer> expectedMissingFrontend = processedIssues.stream()
                                .filter(story -> !frontendImplementedSet.contains(story))
                                .collect(Collectors.toSet());

                Set<Integer> expectedMissingBackend = processedIssues.stream()
                                .filter(story -> !backendImplementedSet.contains(story))
                                .collect(Collectors.toSet());

                Set<Integer> actualMissingFrontend = result.getMissingFrontendIssues().stream()
                                .map(MissingIssue::getStoryNumber)
                                .collect(Collectors.toSet());

                Set<Integer> actualMissingBackend = result.getMissingBackendIssues().stream()
                                .map(MissingIssue::getStoryNumber)
                                .collect(Collectors.toSet());

                // Property: The audit should identify exactly the missing issues
                assert expectedMissingFrontend.equals(actualMissingFrontend)
                                : String.format("Frontend missing issues mismatch. Expected: %s, Actual: %s",
                                                expectedMissingFrontend, actualMissingFrontend);

                assert expectedMissingBackend.equals(actualMissingBackend)
                                : String.format("Backend missing issues mismatch. Expected: %s, Actual: %s",
                                                expectedMissingBackend, actualMissingBackend);
        }

        @Property(tries = 100)
        @Label("Missing issue detection should handle empty implementation lists")
        void missingIssueDetectionWithEmptyImplementations(
                        @ForAll @Size(min = 1, max = 20) List<@IntRange(min = 1, max = 100) Integer> processedIssues) {

                ensureAuditEngineInitialized();

                Map<Integer, StoryMetadata> storyMetadata = createStoryMetadata(processedIssues);
                List<GitHubIssue> emptyFrontendIssues = Collections.emptyList();
                List<GitHubIssue> emptyBackendIssues = Collections.emptyList();

                AuditResult result = auditEngine.performAudit(
                                processedIssues, emptyFrontendIssues, emptyBackendIssues, storyMetadata,
                                testConfiguration);

                // Property: When no implementation issues exist, all processed issues should be
                // missing
                Set<Integer> processedSet = new HashSet<>(processedIssues);
                Set<Integer> missingFrontendSet = result.getMissingFrontendIssues().stream()
                                .map(MissingIssue::getStoryNumber)
                                .collect(Collectors.toSet());
                Set<Integer> missingBackendSet = result.getMissingBackendIssues().stream()
                                .map(MissingIssue::getStoryNumber)
                                .collect(Collectors.toSet());

                assert processedSet.equals(missingFrontendSet)
                                : "All processed issues should be missing from frontend when no implementations exist";
                assert processedSet.equals(missingBackendSet)
                                : "All processed issues should be missing from backend when no implementations exist";
        }

        @Property(tries = 100)
        @Label("Missing issue detection should handle complete implementation coverage")
        void missingIssueDetectionWithCompleteImplementations(
                        @ForAll @Size(min = 1, max = 20) List<@IntRange(min = 1, max = 100) Integer> processedIssues) {

                ensureAuditEngineInitialized();

                Map<Integer, StoryMetadata> storyMetadata = createStoryMetadata(processedIssues);

                // Create implementation issues for all processed stories
                List<GitHubIssue> frontendIssues = createGitHubIssues(processedIssues, "[FRONTEND] [STORY]");
                List<GitHubIssue> backendIssues = createGitHubIssues(processedIssues, "[BACKEND] [STORY]");

                AuditResult result = auditEngine.performAudit(
                                processedIssues, frontendIssues, backendIssues, storyMetadata, testConfiguration);

                // Property: When all stories have implementations, no issues should be missing
                assert result.getMissingFrontendIssues().isEmpty()
                                : "No frontend issues should be missing when all stories are implemented";
                assert result.getMissingBackendIssues().isEmpty()
                                : "No backend issues should be missing when all stories are implemented";
        }

        @Property(tries = 100)
        @Label("Missing issue detection should preserve story metadata in results")
        void missingIssueDetectionPreservesMetadata(
                        @ForAll @Size(min = 1, max = 20) List<@IntRange(min = 1, max = 100) Integer> processedIssues,
                        @ForAll @Size(max = 10) List<@IntRange(min = 1, max = 100) Integer> implementedStories) {

                ensureAuditEngineInitialized();

                Map<Integer, StoryMetadata> storyMetadata = createStoryMetadata(processedIssues);
                List<GitHubIssue> frontendIssues = createGitHubIssues(implementedStories, "[FRONTEND] [STORY]");
                List<GitHubIssue> backendIssues = createGitHubIssues(implementedStories, "[BACKEND] [STORY]");

                AuditResult result = auditEngine.performAudit(
                                processedIssues, frontendIssues, backendIssues, storyMetadata, testConfiguration);

                // Property: All missing issues should have correct metadata
                for (MissingIssue missingIssue : result.getMissingFrontendIssues()) {
                        StoryMetadata metadata = storyMetadata.get(missingIssue.getStoryNumber());
                        assert metadata != null : "Missing issue should have corresponding story metadata";
                        assert missingIssue.getStoryTitle().equals(metadata.getCleanTitle())
                                        : "Missing issue title should match story metadata";
                        assert missingIssue.getStoryUrl().equals(metadata.getUrl())
                                        : "Missing issue URL should match story metadata";
                        assert missingIssue.getRepositoryType().equals("frontend")
                                        : "Frontend missing issue should have correct repository type";
                        assert missingIssue.getExpectedTitle().startsWith("[FRONTEND] [STORY]")
                                        : "Frontend missing issue should have correct expected title format";
                }

                for (MissingIssue missingIssue : result.getMissingBackendIssues()) {
                        StoryMetadata metadata = storyMetadata.get(missingIssue.getStoryNumber());
                        assert metadata != null : "Missing issue should have corresponding story metadata";
                        assert missingIssue.getStoryTitle().equals(metadata.getCleanTitle())
                                        : "Missing issue title should match story metadata";
                        assert missingIssue.getStoryUrl().equals(metadata.getUrl())
                                        : "Missing issue URL should match story metadata";
                        assert missingIssue.getRepositoryType().equals("backend")
                                        : "Backend missing issue should have correct repository type";
                        assert missingIssue.getExpectedTitle().startsWith("[BACKEND] [STORY]")
                                        : "Backend missing issue should have correct expected title format";
                }
        }

        /**
         * Creates story metadata for a list of story numbers.
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
         * Creates GitHub issues for a list of story numbers with the specified prefix.
         */
        private List<GitHubIssue> createGitHubIssues(List<Integer> storyNumbers, String prefix) {
                return storyNumbers.stream()
                                .map(storyNumber -> {
                                        // Create title that matches story metadata format
                                        // Story metadata has "[STORY] Test Story #X: Sample Title"
                                        // After getCleanTitle(), it becomes "Test Story #X: Sample Title"
                                        // So GitHub issue should be: "[PREFIX] Test Story #X: Sample Title"
                                        String title = prefix + " Test Story #" + storyNumber + ": Sample Title";
                                        return new TestGitHubIssue(
                                                        storyNumber + 1000, // Use different ID to avoid confusion
                                                        title,
                                                        "Test body for story #" + storyNumber,
                                                        "https://github.com/test/repo/issues/" + (storyNumber + 1000),
                                                        Collections.emptyList() // Empty labels list
                                        );
                                })
                                .collect(Collectors.toList());
        }

        private void ensureAuditEngineInitialized() {
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
}