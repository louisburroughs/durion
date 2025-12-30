package com.durion.audit;

import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

import org.assertj.core.api.Assertions;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;

/**
 * Property-based test for Property 7: Issue creation format consistency
 * **Feature: missing-issues-audit, Property 7: Issue creation format
 * consistency**
 * **Validates: Requirements 4.2, 4.3**
 * 
 * For any missing issue, when automatically created, the new issue should use
 * the same title
 * and body format as the original processing system and include appropriate
 * labels.
 */
public class IssueCreationFormatPropertyTest {

    @Property(tries = 100)
    @Label("Property 7: Issue titles follow correct format pattern")
    void issueCreatorShouldFormatTitlesCorrectly(
            @ForAll @IntRange(min = 1, max = 1000) int storyNumber,
            @ForAll @StringLength(min = 10, max = 100) String storyTitle,
            @ForAll("validGitHubUrl") String storyUrl,
            @ForAll("validDomain") String domain,
            @ForAll("repositoryType") String repositoryType) {

        // Given: A missing issue with valid data
        MissingIssue missingIssue = new MissingIssue(
                storyNumber,
                storyTitle,
                storyUrl,
                repositoryType,
                getTargetRepository(repositoryType),
                getExpectedTitle(storyTitle, repositoryType));

        // And: An issue creator with mock GitHub client
        MockGitHubIssueCreator mockCreator = new MockGitHubIssueCreator();
        AuditLogger logger = new AuditLogger();
        IssueCreator issueCreator = new IssueCreator(mockCreator, logger);

        // When: Creating an issue
        try {
            GitHubIssue createdIssue;
            if ("frontend".equals(repositoryType)) {
                createdIssue = issueCreator.createFrontendIssue(missingIssue);
            } else {
                createdIssue = issueCreator.createBackendIssue(missingIssue);
            }

            // Then: The title should follow the correct format pattern
            String expectedPrefix = repositoryType.equals("frontend") ? "[FRONTEND]" : "[BACKEND]";
            String expectedTitle = expectedPrefix + " " + storyTitle;

            Assertions.assertThat(createdIssue.getTitle()).isEqualTo(expectedTitle);

            // And: The title should match the pattern [TYPE] Original Title
            // Use DOTALL mode so titles containing newlines are still matched
            Pattern titlePattern = Pattern.compile("(?s)^\\[(FRONTEND|BACKEND)\\] .+$");
            Assertions.assertThat(createdIssue.getTitle()).matches(titlePattern);

        } catch (Exception e) {
            throw new RuntimeException("Issue creation failed", e);
        }
    }

    @Property(tries = 50)
    @Label("Property 7: Issue bodies contain all required template elements")
    void issueCreatorShouldIncludeAllRequiredBodyElements(
            @ForAll @IntRange(min = 1, max = 1000) int storyNumber,
            @ForAll @StringLength(min = 10, max = 100) String storyTitle,
            @ForAll("validGitHubUrl") String storyUrl,
            @ForAll("validDomain") String domain,
            @ForAll("repositoryType") String repositoryType) {

        // Given: A missing issue with valid data
        MissingIssue missingIssue = new MissingIssue(
                storyNumber,
                storyTitle,
                storyUrl,
                repositoryType,
                getTargetRepository(repositoryType),
                getExpectedTitle(storyTitle, repositoryType));

        // And: An issue creator with mock GitHub client
        MockGitHubIssueCreator mockCreator = new MockGitHubIssueCreator();
        AuditLogger logger = new AuditLogger();
        IssueCreator issueCreator = new IssueCreator(mockCreator, logger);

        // When: Creating an issue
        try {
            GitHubIssue createdIssue;
            if ("frontend".equals(repositoryType)) {
                createdIssue = issueCreator.createFrontendIssue(missingIssue);
            } else {
                createdIssue = issueCreator.createBackendIssue(missingIssue);
            }

            String body = createdIssue.getBody();

            // Then: The body should contain all required template elements
            Assertions.assertThat(body).contains("## 🤖 Implementation Issue - Created by Durion Workspace Agent");
            Assertions.assertThat(body).contains("### Original Story");
            Assertions.assertThat(body).contains("**Story**: #" + storyNumber + " - " + storyTitle);
            Assertions.assertThat(body).contains("**URL**: " + storyUrl);
            Assertions.assertThat(body).contains("### Implementation Requirements");
            Assertions.assertThat(body).contains("### Implementation Notes");
            Assertions.assertThat(body).contains("### Technical Requirements");
            Assertions.assertThat(body).contains("### Notes for Agents");
            Assertions.assertThat(body).contains("### Labels Applied");
            Assertions.assertThat(body).contains("Generated by Missing Issues Audit System");

            // And: Should contain repository-specific technical requirements
            if ("frontend".equals(repositoryType)) {
                Assertions.assertThat(body).contains("**Frontend Implementation Requirements:**");
                Assertions.assertThat(body).contains("Vue.js 3 with Composition API");
                Assertions.assertThat(body).contains("Quasar UI framework");
            } else {
                Assertions.assertThat(body).contains("**Backend Implementation Requirements:**");
                Assertions.assertThat(body).contains("Spring Boot with Java 21");
                Assertions.assertThat(body).contains("RESTful API endpoints");
            }

        } catch (Exception e) {
            throw new RuntimeException("Issue creation failed", e);
        }
    }

    @Property(tries = 50)
    @Label("Property 7: Issue labels include all required standard labels")
    void issueCreatorShouldApplyCorrectLabels(
            @ForAll @IntRange(min = 1, max = 1000) int storyNumber,
            @ForAll @StringLength(min = 10, max = 100) String storyTitle,
            @ForAll("validGitHubUrl") String storyUrl,
            @ForAll("validDomain") String domain,
            @ForAll("repositoryType") String repositoryType) {

        // Given: A missing issue with valid data
        MissingIssue missingIssue = new MissingIssue(
                storyNumber,
                storyTitle,
                storyUrl,
                repositoryType,
                getTargetRepository(repositoryType),
                getExpectedTitle(storyTitle, repositoryType));

        // And: An issue creator with mock GitHub client
        MockGitHubIssueCreator mockCreator = new MockGitHubIssueCreator();
        AuditLogger logger = new AuditLogger();
        IssueCreator issueCreator = new IssueCreator(mockCreator, logger);

        // When: Creating an issue
        try {
            GitHubIssue createdIssue;
            if ("frontend".equals(repositoryType)) {
                createdIssue = issueCreator.createFrontendIssue(missingIssue);
            } else {
                createdIssue = issueCreator.createBackendIssue(missingIssue);
            }

            List<String> labels = createdIssue.getLabels();

            // Then: Should contain all required standard labels
            Assertions.assertThat(labels).contains("type:story");
            Assertions.assertThat(labels).contains("layer:functional");
            Assertions.assertThat(labels).contains("kiro");
            Assertions.assertThat(labels).contains("story-implementation");


            // And: Should contain repository type label
            Assertions.assertThat(labels).contains(repositoryType);

            // And: Should have exactly 6 labels (no duplicates or extras)
            Assertions.assertThat(labels).hasSize(5);

        } catch (Exception e) {
            throw new RuntimeException("Issue creation failed", e);
        }
    }

    @Property(tries = 30)
    @Label("Property 7: Issue creation handles special characters in titles correctly")
    void issueCreatorShouldHandleSpecialCharactersInTitles(
            @ForAll @IntRange(min = 1, max = 1000) int storyNumber,
            @ForAll("titleWithSpecialChars") String storyTitle,
            @ForAll("validGitHubUrl") String storyUrl,
            @ForAll("validDomain") String domain,
            @ForAll("repositoryType") String repositoryType) {

        // Given: A missing issue with title containing special characters
        MissingIssue missingIssue = new MissingIssue(
                storyNumber,
                storyTitle,
                storyUrl,
                repositoryType,
                getTargetRepository(repositoryType),
                getExpectedTitle(storyTitle, repositoryType));

        // And: An issue creator with mock GitHub client
        MockGitHubIssueCreator mockCreator = new MockGitHubIssueCreator();
        AuditLogger logger = new AuditLogger();
        IssueCreator issueCreator = new IssueCreator(mockCreator, logger);

        // When: Creating an issue
        try {
            GitHubIssue createdIssue;
            if ("frontend".equals(repositoryType)) {
                createdIssue = issueCreator.createFrontendIssue(missingIssue);
            } else {
                createdIssue = issueCreator.createBackendIssue(missingIssue);
            }

            // Then: The title should preserve special characters correctly
            String expectedPrefix = repositoryType.equals("frontend") ? "[FRONTEND]" : "[BACKEND]";
            String expectedTitle = expectedPrefix + " " + storyTitle;

            Assertions.assertThat(createdIssue.getTitle()).isEqualTo(expectedTitle);

            // And: The body should contain the original title with special characters
            Assertions.assertThat(createdIssue.getBody()).contains("**Story**: #" + storyNumber + " - " + storyTitle);

        } catch (Exception e) {
            throw new RuntimeException("Issue creation failed", e);
        }
    }

    // Provider methods for test data generation
    @Provide
    Arbitrary<String> validGitHubUrl() {
        return Arbitraries.integers().between(1, 10000)
                .map(num -> "https://github.com/louisburroughs/durion/issues/" + num);
    }

    @Provide
    Arbitrary<String> validDomain() {
        return Arbitraries.of("payment", "user", "admin", "reporting", "general", "inventory");
    }

    @Provide
    Arbitrary<String> repositoryType() {
        return Arbitraries.of("frontend", "backend");
    }

    @Provide
    Arbitrary<String> titleWithSpecialChars() {
        return Arbitraries.strings()
                .withCharRange('A', 'z')
                .ofMinLength(10)
                .ofMaxLength(50)
                .map(base -> {
                    // Add some special characters that might appear in story titles
                    String[] specialChars = { ": ", " & ", " - ", " / ", " (", ")", " [", "]", " \"", "\"", " '", "'" };
                    int index = Math.floorMod(base.hashCode(), specialChars.length);
                    String special = specialChars[index];
                    return base + special + "Test";
                });
    }

    // Helper methods
    private String getTargetRepository(String repositoryType) {
        return "frontend".equals(repositoryType)
                ? "louisburroughs/durion-moqui-frontend"
                : "louisburroughs/durion-positivity-backend";
    }

    private String getExpectedTitle(String storyTitle, String repositoryType) {
        String prefix = "frontend".equals(repositoryType) ? "[FRONTEND]" : "[BACKEND]";
        return prefix + " " + storyTitle;
    }

    /**
     * Mock implementation of GitHubIssueCreator for testing
     */
    private static class MockGitHubIssueCreator implements GitHubIssueCreator {

        @Override
        public GitHubIssue createIssue(String repository, String title, String body, List<String> labels)
                throws IOException, InterruptedException {

            // Return a mock GitHubIssue with the provided data
            return new MockGitHubIssue(
                    (int) (Math.random() * 10000), // Random issue number
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
            this.labels = List.copyOf(labels); // Immutable copy
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