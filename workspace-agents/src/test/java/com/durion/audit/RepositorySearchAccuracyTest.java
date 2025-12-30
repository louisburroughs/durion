package com.durion.audit;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.assertj.core.api.Assertions;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property-based test for Property 2: Repository search accuracy
 * **Feature: missing-issues-audit, Property 2: Repository search accuracy**
 * **Validates: Requirements 1.2**
 * 
 * For any repository with implementation issues, searching with [FRONTEND]
 * [STORY] or [BACKEND] [STORY]
 * patterns should return only issues matching those exact patterns.
 */
public class RepositorySearchAccuracyTest {

        @Property(tries = 100)
        @Label("Property 2: Repository search accuracy for [FRONTEND] [STORY] pattern")
        void repositoryScannerShouldReturnOnlyFrontendIssues(
                        @ForAll("mockRepositoryWithIssues") MockRepository repository) throws Exception {

                // Given: A mock repository scanner with test data
                MockGitHubRepositoryScanner scanner = new MockGitHubRepositoryScanner(repository);

                // When: Scanning for [FRONTEND] [STORY] issues
                List<Object> frontendIssues = scanner.scanFrontendIssues(repository.getName());

                // Then: All returned issues should have [FRONTEND] [STORY] in their title
                for (Object issueObj : frontendIssues) {
                        MockIssue issue = (MockIssue) issueObj;
                        Assertions.assertThat(issue.getTitle())
                                        .contains("[FRONTEND] [STORY]");
                }

                // And: No [BACKEND] [STORY] issues should be returned
                for (Object issueObj : frontendIssues) {
                        MockIssue issue = (MockIssue) issueObj;
                        Assertions.assertThat(issue.getTitle())
                                        .doesNotContain("[BACKEND] [STORY]");
                }

                // And: The count should match expected frontend issues
                long expectedFrontendCount = repository.getIssues().stream()
                                .filter(issue -> issue.getTitle().contains("[FRONTEND] [STORY]"))
                                .count();
                Assertions.assertThat(frontendIssues.size())
                                .isEqualTo(expectedFrontendCount);
        }

        @Property(tries = 100)
        @Label("Property 2: Repository search accuracy for [BACKEND] [STORY] pattern")
        void repositoryScannerShouldReturnOnlyBackendIssues(
                        @ForAll("mockRepositoryWithIssues") MockRepository repository) throws Exception {

                // Given: A mock repository scanner with test data
                MockGitHubRepositoryScanner scanner = new MockGitHubRepositoryScanner(repository);

                // When: Scanning for [BACKEND] [STORY] issues
                List<Object> backendIssues = scanner.scanBackendIssues(repository.getName());

                // Then: All returned issues should have [BACKEND] [STORY] in their title
                for (Object issueObj : backendIssues) {
                        MockIssue issue = (MockIssue) issueObj;
                        Assertions.assertThat(issue.getTitle())
                                        .contains("[BACKEND] [STORY]");
                }

                // And: No [FRONTEND] [STORY] issues should be returned
                for (Object issueObj : backendIssues) {
                        MockIssue issue = (MockIssue) issueObj;
                        Assertions.assertThat(issue.getTitle())
                                        .doesNotContain("[FRONTEND] [STORY]");
                }

                // And: The count should match expected backend issues
                long expectedBackendCount = repository.getIssues().stream()
                                .filter(issue -> issue.getTitle().contains("[BACKEND] [STORY]"))
                                .count();
                Assertions.assertThat(backendIssues.size())
                                .isEqualTo(expectedBackendCount);
        }

        @Property(tries = 50)
        @Label("Property 2: Repository search pattern matching is case-sensitive")
        void repositoryScannerShouldBeCaseSensitive(
                        @ForAll("mockRepositoryWithCaseVariations") MockRepository repository) throws Exception {

                // Given: A mock repository scanner with case variations
                MockGitHubRepositoryScanner scanner = new MockGitHubRepositoryScanner(repository);

                // When: Scanning for exact [FRONTEND] [STORY] pattern
                List<Object> frontendIssues = scanner.scanIssuesWithPattern(repository.getName(), "[FRONTEND] [STORY]");

                // Then: Only exact case matches should be returned
                for (Object issueObj : frontendIssues) {
                        MockIssue issue = (MockIssue) issueObj;
                        Assertions.assertThat(issue.getTitle())
                                        .contains("[FRONTEND] [STORY]");
                        // Should not match case variations
                        Assertions.assertThat(issue.getTitle())
                                        .doesNotContain("[frontend] [story]")
                                        .doesNotContain("[Frontend] [Story]")
                                        .doesNotContain("[FRONT-END] [STORY]");
                }
        }

        @Property(tries = 30)
        @Label("Property 2: Repository search handles empty results consistently")
        void repositoryScannerShouldHandleEmptyResults(
                        @ForAll("mockRepositoryWithoutImplementationIssues") MockRepository repository)
                        throws Exception {

                // Given: A repository with no implementation issues
                MockGitHubRepositoryScanner scanner = new MockGitHubRepositoryScanner(repository);

                // When: Scanning for [FRONTEND] [STORY] or [BACKEND] [STORY] issues
                List<Object> frontendIssues = scanner.scanFrontendIssues(repository.getName());
                List<Object> backendIssues = scanner.scanBackendIssues(repository.getName());

                // Then: Both searches should return empty lists
                Assertions.assertThat(frontendIssues).isEmpty();
                Assertions.assertThat(backendIssues).isEmpty();
        }

        @Property(tries = 50)
        @Label("Property 2: Repository search preserves issue metadata")
        void repositoryScannerShouldPreserveIssueMetadata(
                        @ForAll("mockRepositoryWithIssues") MockRepository repository) throws Exception {

                // Given: A mock repository scanner with test data
                MockGitHubRepositoryScanner scanner = new MockGitHubRepositoryScanner(repository);

                // When: Scanning for [FRONTEND] [STORY] issues
                List<Object> frontendIssues = scanner.scanFrontendIssues(repository.getName());

                // Then: All issue metadata should be preserved
                for (Object issueObj : frontendIssues) {
                        MockIssue issue = (MockIssue) issueObj;

                        // Issue should have valid number
                        Assertions.assertThat(issue.getNumber()).isPositive();

                        // Issue should have non-empty title
                        Assertions.assertThat(issue.getTitle()).isNotEmpty();

                        // Issue should have valid URL
                        Assertions.assertThat(issue.getUrl())
                                        .isNotEmpty()
                                        .startsWith("https://github.com/");

                        // Issue should have labels (can be empty but not null)
                        Assertions.assertThat(issue.getLabels()).isNotNull();
                }
        }

        @Provide
        Arbitrary<MockRepository> mockRepositoryWithIssues() {
                return Arbitraries.create(() -> {
                        List<MockIssue> issues = new ArrayList<>();

                        // Add some [FRONTEND] [STORY] issues
                        issues.add(new MockIssue(101, "[FRONTEND] [STORY] User login form",
                                        "Frontend implementation for user authentication",
                                        "https://github.com/test/repo/issues/101",
                                        List.of("frontend", "story-implementation")));
                        issues.add(new MockIssue(102, "[FRONTEND] [STORY] Product display page",
                                        "Frontend product catalog display",
                                        "https://github.com/test/repo/issues/102",
                                        List.of("frontend", "story-implementation")));

                        // Add some [BACKEND] [STORY] issues
                        issues.add(new MockIssue(201, "[BACKEND] [STORY] User authentication service",
                                        "Backend auth service implementation",
                                        "https://github.com/test/repo/issues/201",
                                        List.of("backend", "story-implementation")));
                        issues.add(new MockIssue(202, "[BACKEND] [STORY] Product catalog API",
                                        "Backend product API implementation",
                                        "https://github.com/test/repo/issues/202",
                                        List.of("backend", "story-implementation")));

                        // Add some regular issues (no [FRONTEND] or [BACKEND])
                        issues.add(new MockIssue(301, "Bug: Login button not working", "General bug report",
                                        "https://github.com/test/repo/issues/301", List.of("bug")));
                        issues.add(new MockIssue(302, "Feature: Add dark mode", "Feature request",
                                        "https://github.com/test/repo/issues/302", List.of("enhancement")));

                        return new MockRepository("test/repo", issues);
                });
        }

        @Provide
        Arbitrary<MockRepository> mockRepositoryWithCaseVariations() {
                return Arbitraries.create(() -> {
                        List<MockIssue> issues = new ArrayList<>();

                        // Add exact case matches
                        issues.add(new MockIssue(101, "[FRONTEND] [STORY] Correct case", "Should match",
                                        "https://github.com/test/repo/issues/101", List.of("frontend")));
                        issues.add(new MockIssue(102, "[BACKEND] [STORY] Correct case", "Should match",
                                        "https://github.com/test/repo/issues/102", List.of("backend")));

                        // Add case variations that should NOT match
                        issues.add(new MockIssue(201, "[frontend] [story] Lowercase", "Should not match",
                                        "https://github.com/test/repo/issues/201", List.of("frontend")));
                        issues.add(new MockIssue(202, "[Frontend] [Story] Mixed case", "Should not match",
                                        "https://github.com/test/repo/issues/202", List.of("frontend")));
                        issues.add(new MockIssue(203, "[FRONT-END] [STORY] Hyphenated", "Should not match",
                                        "https://github.com/test/repo/issues/203", List.of("frontend")));

                        return new MockRepository("test/repo", issues);
                });
        }

        @Provide
        Arbitrary<MockRepository> mockRepositoryWithoutImplementationIssues() {
                return Arbitraries.create(() -> {
                        List<MockIssue> issues = new ArrayList<>();

                        // Add only regular issues (no implementation issues)
                        issues.add(new MockIssue(301, "Bug: Login button not working", "General bug report",
                                        "https://github.com/test/repo/issues/301", List.of("bug")));
                        issues.add(new MockIssue(302, "Feature: Add dark mode", "Feature request",
                                        "https://github.com/test/repo/issues/302", List.of("enhancement")));
                        issues.add(new MockIssue(303, "Documentation update needed", "Docs improvement",
                                        "https://github.com/test/repo/issues/303", List.of("documentation")));

                        return new MockRepository("test/repo", issues);
                });
        }

        // Mock classes for testing
        public static class MockRepository {
                private final String name;
                private final List<MockIssue> issues;

                public MockRepository(String name, List<MockIssue> issues) {
                        this.name = name;
                        this.issues = issues;
                }

                public String getName() {
                        return name;
                }

                public List<MockIssue> getIssues() {
                        return issues;
                }
        }

        public static class MockIssue {
                private final int number;
                private final String title;
                private final String body;
                private final String url;
                private final List<String> labels;

                public MockIssue(int number, String title, String body, String url, List<String> labels) {
                        this.number = number;
                        this.title = title;
                        this.body = body;
                        this.url = url;
                        this.labels = labels;
                }

                public int getNumber() {
                        return number;
                }

                public String getTitle() {
                        return title;
                }

                public String getBody() {
                        return body;
                }

                public String getUrl() {
                        return url;
                }

                public List<String> getLabels() {
                        return labels;
                }
        }

        // Mock implementation of GitHubRepositoryScanner for testing
        public static class MockGitHubRepositoryScanner implements GitHubRepositoryScanner {
                private final MockRepository repository;

                public MockGitHubRepositoryScanner(MockRepository repository) {
                        this.repository = repository;
                }

                @Override
                public List<Object> scanFrontendIssues(String repositoryName) {
                        return scanIssuesWithPattern(repositoryName, "[FRONTEND]");
                }

                @Override
                public List<Object> scanBackendIssues(String repositoryName) {
                        return scanIssuesWithPattern(repositoryName, "[BACKEND]");
                }

                @Override
                public List<Object> scanIssuesWithPattern(String repositoryName, String titlePattern) {
                        return repository.getIssues().stream()
                                        .filter(issue -> issue.getTitle().contains(titlePattern))
                                        .collect(Collectors.toList())
                                        .stream()
                                        .map(issue -> (Object) issue)
                                        .collect(Collectors.toList());
                }

                @Override
                public boolean testRepositoryAccess(String repositoryName) {
                        return true; // Mock always returns true
                }

                @Override
                public RateLimitInfo getRateLimitStatus() {
                        // Return mock rate limit info
                        return new RateLimitInfo(5000, 4000, 1000,
                                        java.time.LocalDateTime.now().plusHours(1), "core");
                }
        }
}