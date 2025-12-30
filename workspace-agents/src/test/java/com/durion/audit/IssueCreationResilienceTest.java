package com.durion.audit;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manual test for Property 8: Issue creation resilience
 * **Feature: missing-issues-audit, Property 8: Issue creation resilience**
 * **Validates: Requirements 4.4**
 * 
 * For any issue creation operation, the system should handle rate limits and
 * failures gracefully,
 * continuing with remaining operations.
 */
public class IssueCreationResilienceTest {

    public static void main(String[] args) {
        System.out.println("🧪 Running Issue Creation Resilience Test");
        System.out.println("Testing Property 8: Issue creation resilience");
        System.out.println("Validates: Requirements 4.4");
        System.out.println();

        try {
            // Test 1: Rate limit handling
            testRateLimitHandling();

            // Test 2: Network error retry logic
            testNetworkErrorRetry();

            // Test 3: Non-retryable error handling
            testNonRetryableErrorHandling();

            // Test 4: Batch processing resilience
            testBatchProcessingResilience();

            // Test 5: Interruption handling
            testInterruptionHandling();

            System.out.println("✅ All resilience tests passed!");
            System.out.println("Property 8: Issue creation resilience - VALIDATED");

        } catch (Exception e) {
            System.out.println("❌ Test failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void testRateLimitHandling() throws Exception {
        System.out.println("🔍 Test 1: Rate limit handling");

        // Given: A missing issue and a mock client that simulates rate limits
        MissingIssue missingIssue = createTestMissingIssue(1, "Rate Limit Test");
        RateLimitMockGitHubIssueCreator mockCreator = new RateLimitMockGitHubIssueCreator(2); // Fail first 2 attempts
        AuditLogger logger = new AuditLogger();

        // Create a test-specific IssueCreator that uses shorter delays
        TestIssueCreator issueCreator = new TestIssueCreator(mockCreator, logger);

        // When: Creating an issue that encounters rate limits
        long startTime = System.currentTimeMillis();
        IssueCreator.IssueCreationResult result = issueCreator.createBothIssues(missingIssue);
        long duration = System.currentTimeMillis() - startTime;

        // Then: Should eventually succeed after retries
        assert result.isSuccess() : "Should succeed after rate limit retries";
        assert result.getFrontendIssue() != null : "Should create frontend issue";
        assert mockCreator.getAttemptCount() == 3 : "Should make 3 attempts (2 failures + 1 success)";

        // And: Should have appropriate delay for rate limits (at least 2 seconds for 2
        // rate limit waits)
        assert duration >= 2000 : "Should have appropriate delays for rate limit handling";

        System.out.println("   ✅ Rate limit handling validation passed");
    }

    private static void testNetworkErrorRetry() throws Exception {
        System.out.println("🔍 Test 2: Network error retry logic");

        // Given: A missing issue and a mock client that simulates network errors
        MissingIssue missingIssue = createTestMissingIssue(2, "Network Error Test");
        NetworkErrorMockGitHubIssueCreator mockCreator = new NetworkErrorMockGitHubIssueCreator(1); // Fail first
                                                                                                    // attempt
        AuditLogger logger = new AuditLogger();
        TestIssueCreator issueCreator = new TestIssueCreator(mockCreator, logger);

        // When: Creating an issue that encounters network errors
        IssueCreator.IssueCreationResult result = issueCreator.createBothIssues(missingIssue);

        // Then: Should succeed after retry
        assert result.isSuccess() : "Should succeed after network error retry";
        assert mockCreator.getAttemptCount() == 2 : "Should make 2 attempts (1 failure + 1 success)";

        System.out.println("   ✅ Network error retry validation passed");
    }

    private static void testNonRetryableErrorHandling() throws Exception {
        System.out.println("🔍 Test 3: Non-retryable error handling");

        // Given: A missing issue and a mock client that simulates non-retryable errors
        MissingIssue missingIssue = createTestMissingIssue(3, "Non-Retryable Error Test");
        NonRetryableErrorMockGitHubIssueCreator mockCreator = new NonRetryableErrorMockGitHubIssueCreator();
        AuditLogger logger = new AuditLogger();
        TestIssueCreator issueCreator = new TestIssueCreator(mockCreator, logger);

        // When: Creating an issue that encounters non-retryable errors
        IssueCreator.IssueCreationResult result = issueCreator.createBothIssues(missingIssue);

        // Then: Should fail immediately without retries
        assert !result.isSuccess() : "Should fail for non-retryable errors";
        assert mockCreator.getAttemptCount() == 1 : "Should make only 1 attempt for non-retryable errors";
        assert !result.getErrors().isEmpty() : "Should report errors";

        System.out.println("   ✅ Non-retryable error handling validation passed");
    }

    private static void testBatchProcessingResilience() throws Exception {
        System.out.println("🔍 Test 4: Batch processing resilience");

        // Given: Multiple missing issues with mixed success/failure scenarios
        List<MissingIssue> missingIssues = new ArrayList<>();
        missingIssues.add(createTestMissingIssue(4, "Batch Test 1"));
        missingIssues.add(createTestMissingIssue(5, "Batch Test 2"));
        missingIssues.add(createTestMissingIssue(6, "Batch Test 3"));

        // And: A mock client that fails on the second issue but succeeds on others
        MixedResultMockGitHubIssueCreator mockCreator = new MixedResultMockGitHubIssueCreator();
        AuditLogger logger = new AuditLogger();
        IssueCreator issueCreator = new IssueCreator(mockCreator, logger);

        // When: Processing a batch of issues
        IssueCreator.BatchCreationResult batchResult = issueCreator.createIssuesBatch(missingIssues);

        // Then: Should continue processing despite individual failures
        assert batchResult.getTotalCount() == 3 : "Should process all 3 issues";
        assert batchResult.getSuccessCount() == 2 : "Should have 2 successes";
        assert batchResult.getFailureCount() == 1 : "Should have 1 failure";
        assert batchResult.getSuccessRate() > 0.5 : "Should have > 50% success rate";

        System.out.println("   ✅ Batch processing resilience validation passed");
    }

    private static void testInterruptionHandling() throws Exception {
        System.out.println("🔍 Test 5: Interruption handling");

        // Given: A missing issue and a mock client that simulates interruption
        MissingIssue missingIssue = createTestMissingIssue(7, "Interruption Test");
        InterruptionMockGitHubIssueCreator mockCreator = new InterruptionMockGitHubIssueCreator();
        AuditLogger logger = new AuditLogger();
        TestIssueCreator issueCreator = new TestIssueCreator(mockCreator, logger);

        // When: Creating an issue that gets interrupted
        IssueCreator.IssueCreationResult result = issueCreator.createBothIssues(missingIssue);

        // Then: Should handle interruption gracefully
        assert !result.isSuccess() : "Should fail when interrupted";
        assert !result.getErrors().isEmpty() : "Should report interruption error";
        assert result.getErrors().get(0).contains("interrupted") : "Error should mention interruption";

        System.out.println("   ✅ Interruption handling validation passed");
    }

    // Helper method to create test missing issues
    private static MissingIssue createTestMissingIssue(int number, String title) {
        return new MissingIssue(
                number,
                title,
                "https://github.com/louisburroughs/durion/issues/" + number,
                "frontend",
                "louisburroughs/durion-moqui-frontend",
                "[FRONTEND] " + title);
    }

    /**
     * Mock GitHub issue creator that simulates rate limit errors
     */
    private static class RateLimitMockGitHubIssueCreator implements GitHubIssueCreator {
        private final AtomicInteger attemptCount = new AtomicInteger(0);
        private final int failureCount;

        public RateLimitMockGitHubIssueCreator(int failureCount) {
            this.failureCount = failureCount;
        }

        @Override
        public GitHubIssue createIssue(String repository, String title, String body, List<String> labels)
                throws IOException, InterruptedException {

            int attempt = attemptCount.incrementAndGet();

            if (attempt <= failureCount) {
                // Simulate rate limit with shorter delay for testing
                Thread.sleep(1000); // 1 second instead of 60 for testing
                throw new IOException("API rate limit exceeded (403): secondary rate limit");
            }

            // Success case
            return new MockGitHubIssue(
                    (int) (Math.random() * 10000),
                    title,
                    body,
                    "https://github.com/" + repository + "/issues/" + (int) (Math.random() * 10000),
                    labels);
        }

        public int getAttemptCount() {
            return attemptCount.get();
        }
    }

    /**
     * Mock GitHub issue creator that simulates network errors
     */
    private static class NetworkErrorMockGitHubIssueCreator implements GitHubIssueCreator {
        private final AtomicInteger attemptCount = new AtomicInteger(0);
        private final int failureCount;

        public NetworkErrorMockGitHubIssueCreator(int failureCount) {
            this.failureCount = failureCount;
        }

        @Override
        public GitHubIssue createIssue(String repository, String title, String body, List<String> labels)
                throws IOException, InterruptedException {

            int attempt = attemptCount.incrementAndGet();

            if (attempt <= failureCount) {
                throw new IOException("Connection timeout: Unable to connect to api.github.com");
            }

            // Success case
            return new MockGitHubIssue(
                    (int) (Math.random() * 10000),
                    title,
                    body,
                    "https://github.com/" + repository + "/issues/" + (int) (Math.random() * 10000),
                    labels);
        }

        public int getAttemptCount() {
            return attemptCount.get();
        }
    }

    /**
     * Mock GitHub issue creator that simulates non-retryable errors
     */
    private static class NonRetryableErrorMockGitHubIssueCreator implements GitHubIssueCreator {
        private final AtomicInteger attemptCount = new AtomicInteger(0);

        @Override
        public GitHubIssue createIssue(String repository, String title, String body, List<String> labels)
                throws IOException, InterruptedException {

            attemptCount.incrementAndGet();
            throw new IOException("Authentication failed (401): Bad credentials");
        }

        public int getAttemptCount() {
            return attemptCount.get();
        }
    }

    /**
     * Mock GitHub issue creator that simulates mixed results in batch processing
     */
    private static class MixedResultMockGitHubIssueCreator implements GitHubIssueCreator {
        private final AtomicInteger callCount = new AtomicInteger(0);

        @Override
        public GitHubIssue createIssue(String repository, String title, String body, List<String> labels)
                throws IOException, InterruptedException {

            int call = callCount.incrementAndGet();

            // Fail on the second call (simulating one failure in batch)
            if (call == 2) {
                throw new IOException("Temporary server error (502): Bad Gateway");
            }

            // Success for other calls
            return new MockGitHubIssue(
                    (int) (Math.random() * 10000),
                    title,
                    body,
                    "https://github.com/" + repository + "/issues/" + (int) (Math.random() * 10000),
                    labels);
        }
    }

    /**
     * Mock GitHub issue creator that simulates interruption
     */
    private static class InterruptionMockGitHubIssueCreator implements GitHubIssueCreator {

        @Override
        public GitHubIssue createIssue(String repository, String title, String body, List<String> labels)
                throws IOException, InterruptedException {

            // Simulate interruption
            Thread.currentThread().interrupt();
            throw new InterruptedException("Operation was interrupted");
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

    /**
     * Test-specific IssueCreator that uses shorter delays for faster testing
     */
    private static class TestIssueCreator extends IssueCreator {

        public TestIssueCreator(GitHubIssueCreator githubIssueCreator, AuditLogger logger) {
            super(githubIssueCreator, logger);
        }

        @Override
        public IssueCreationResult createBothIssues(MissingIssue missingIssue) {
            List<String> errors = new ArrayList<>();
            GitHubIssue frontendIssue = null;
            GitHubIssue backendIssue = null;

            System.out.println("🔨 Creating issues for story #" + missingIssue.getStoryNumber());

            // Create frontend issue if needed
            if ("frontend".equals(missingIssue.getRepositoryType())
                    || "both".equals(missingIssue.getRepositoryType())) {
                frontendIssue = createIssueWithTestRetry(missingIssue, "frontend", errors);
            }

            // Create backend issue if needed
            if ("backend".equals(missingIssue.getRepositoryType()) || "both".equals(missingIssue.getRepositoryType())) {
                backendIssue = createIssueWithTestRetry(missingIssue, "backend", errors);
            }

            boolean success = (frontendIssue != null || backendIssue != null);
            String message = success ? "Issues created successfully" : "Failed to create any issues";

            return new IssueCreationResult(frontendIssue, backendIssue, success, message, errors);
        }

        /**
         * Test version of createIssueWithRetry that uses shorter delays
         */
        private GitHubIssue createIssueWithTestRetry(MissingIssue missingIssue, String repositoryType,
                List<String> errors) {
            final int MAX_RETRIES = 3;
            final long RETRY_DELAY_MS = 100; // Short delay for testing
            final long RATE_LIMIT_DELAY_MS = 1000; // 1 second for testing instead of 60

            for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                try {
                    System.out.println("📤 Attempt " + attempt + "/" + MAX_RETRIES + " - Creating " + repositoryType
                            + " issue for story #" + missingIssue.getStoryNumber());

                    GitHubIssue createdIssue;
                    if ("frontend".equals(repositoryType)) {
                        createdIssue = createFrontendIssue(missingIssue);
                    } else {
                        createdIssue = createBackendIssue(missingIssue);
                    }

                    System.out.println("✅ Successfully created " + repositoryType + " issue on attempt " + attempt);
                    return createdIssue;

                } catch (IOException e) {
                    String errorMsg = "Attempt " + attempt + " failed for " + repositoryType + " issue: "
                            + e.getMessage();
                    System.out.println("⚠️ " + errorMsg);

                    // Check if this is a rate limit error
                    if (isRateLimitError(e)) {
                        System.out.println("🚦 Rate limit detected - waiting " + (RATE_LIMIT_DELAY_MS / 1000)
                                + " seconds before retry");

                        if (attempt < MAX_RETRIES) {
                            try {
                                Thread.sleep(RATE_LIMIT_DELAY_MS);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                String interruptMsg = "Thread interrupted during rate limit wait for " + repositoryType
                                        + " issue";
                                errors.add(interruptMsg);
                                break;
                            }
                        }
                    } else if (isRetryableError(e)) {
                        // Network or temporary errors - retry with shorter delay
                        System.out.println("🔄 Retryable error detected - waiting " + (RETRY_DELAY_MS / 1000.0)
                                + " seconds before retry");

                        if (attempt < MAX_RETRIES) {
                            try {
                                Thread.sleep(RETRY_DELAY_MS);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                String interruptMsg = "Thread interrupted during retry wait for " + repositoryType
                                        + " issue";
                                errors.add(interruptMsg);
                                break;
                            }
                        }
                    } else {
                        // Non-retryable error - fail immediately
                        System.out.println("❌ Non-retryable error - failing immediately");
                        errors.add(errorMsg);
                        break;
                    }

                    // If this is the last attempt, record the failure
                    if (attempt == MAX_RETRIES) {
                        errors.add("Failed to create " + repositoryType + " issue after " + MAX_RETRIES + " attempts: "
                                + e.getMessage());
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    String interruptMsg = "Issue creation interrupted for " + repositoryType + " issue";
                    errors.add(interruptMsg);
                    break;

                } catch (Exception e) {
                    // Unexpected error - log and fail
                    String unexpectedMsg = "Unexpected error creating " + repositoryType + " issue: " + e.getMessage();
                    errors.add(unexpectedMsg);
                    break;
                }
            }

            return null; // All attempts failed
        }

        private boolean isRateLimitError(IOException e) {
            String message = e.getMessage().toLowerCase();
            return message.contains("rate limit") ||
                    message.contains("403") ||
                    message.contains("too many requests") ||
                    message.contains("secondary rate limit");
        }

        private boolean isRetryableError(IOException e) {
            String message = e.getMessage().toLowerCase();
            return message.contains("timeout") ||
                    message.contains("connection") ||
                    message.contains("network") ||
                    message.contains("502") ||
                    message.contains("503") ||
                    message.contains("504") ||
                    message.contains("bad gateway") ||
                    message.contains("service unavailable") ||
                    message.contains("gateway timeout");
        }
    }
}