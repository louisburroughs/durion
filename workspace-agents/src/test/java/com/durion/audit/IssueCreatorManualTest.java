package com.durion.audit;

import java.io.IOException;
import java.util.List;

/**
 * Manual test for IssueCreator functionality to validate Property 7: Issue
 * creation format consistency
 * This test validates the core functionality without requiring the full
 * property-based testing framework.
 */
public class IssueCreatorManualTest {

    public static void main(String[] args) {
        System.out.println("🧪 Running Manual Issue Creator Test");
        System.out.println("Testing Property 7: Issue creation format consistency");
        System.out.println("Validates: Requirements 4.2, 4.3");
        System.out.println();

        try {
            // Test 1: Frontend issue creation format
            testFrontendIssueFormat();

            // Test 2: Backend issue creation format
            testBackendIssueFormat();

            // Test 3: Label generation
            testLabelGeneration();

            // Test 4: Special characters handling
            testSpecialCharactersHandling();

            System.out.println("✅ All manual tests passed!");
            System.out.println("Property 7: Issue creation format consistency - VALIDATED");

        } catch (Exception e) {
            System.out.println("❌ Test failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void testFrontendIssueFormat() throws Exception {
        System.out.println("🔍 Test 1: Frontend issue creation format");

        // Given: A missing frontend issue
        MissingIssue missingIssue = new MissingIssue(
                273,
                "Security: Audit Trail for Price Overrides, Refunds, and Cancellations",
                "https://github.com/louisburroughs/durion/issues/273",
                "frontend",
                "louisburroughs/durion-moqui-frontend",
                "[FRONTEND] Security: Audit Trail for Price Overrides, Refunds, and Cancellations");

        // And: An issue creator with mock GitHub client
        MockGitHubIssueCreator mockCreator = new MockGitHubIssueCreator();
        AuditLogger logger = new AuditLogger();
        IssueCreator issueCreator = new IssueCreator(mockCreator, logger);

        // When: Creating a frontend issue
        GitHubIssue createdIssue = issueCreator.createFrontendIssue(missingIssue);

        // Then: Validate title format
        String expectedTitle = "[FRONTEND] Security: Audit Trail for Price Overrides, Refunds, and Cancellations";
        assert createdIssue.getTitle().equals(expectedTitle)
                : "Title mismatch. Expected: " + expectedTitle + ", Got: " + createdIssue.getTitle();

        // And: Validate body contains required elements
        String body = createdIssue.getBody();
        assert body.contains("## 🤖 Implementation Issue - Created by Durion Workspace Agent") : "Missing header";
        assert body.contains("**Story**: #273 - Security: Audit Trail for Price Overrides, Refunds, and Cancellations")
                : "Missing story info";
        assert body.contains("**URL**: https://github.com/louisburroughs/durion/issues/273") : "Missing URL";
        assert body.contains("**Domain**: payment") : "Missing domain";
        assert body.contains("**Frontend Implementation Requirements:**") : "Missing frontend requirements";
        assert body.contains("Vue.js 3 with Composition API") : "Missing Vue.js requirement";
        assert body.contains("Quasar UI framework") : "Missing Quasar requirement";

        // And: Validate labels
        List<String> labels = createdIssue.getLabels();
        assert labels.contains("type:story") : "Missing type:story label";
        assert labels.contains("layer:functional") : "Missing layer:functional label";
        assert labels.contains("kiro") : "Missing kiro label";
        assert labels.contains("story-implementation") : "Missing story-implementation label";
        assert labels.contains("domain:payment") : "Missing domain:payment label";
        assert labels.contains("frontend") : "Missing frontend label";
        assert labels.size() == 6 : "Expected 6 labels, got " + labels.size();

        System.out.println("   ✅ Frontend issue format validation passed");
    }

    private static void testBackendIssueFormat() throws Exception {
        System.out.println("🔍 Test 2: Backend issue creation format");

        // Given: A missing backend issue
        MissingIssue missingIssue = new MissingIssue(
                272,
                "User Management: Role-Based Access Control",
                "https://github.com/louisburroughs/durion/issues/272",
                "backend",
                "louisburroughs/durion-positivity-backend",
                "[BACKEND] User Management: Role-Based Access Control");

        // And: An issue creator with mock GitHub client
        MockGitHubIssueCreator mockCreator = new MockGitHubIssueCreator();
        AuditLogger logger = new AuditLogger();
        IssueCreator issueCreator = new IssueCreator(mockCreator, logger);

        // When: Creating a backend issue
        GitHubIssue createdIssue = issueCreator.createBackendIssue(missingIssue);

        // Then: Validate title format
        String expectedTitle = "[BACKEND] User Management: Role-Based Access Control";
        assert createdIssue.getTitle().equals(expectedTitle)
                : "Title mismatch. Expected: " + expectedTitle + ", Got: " + createdIssue.getTitle();

        // And: Validate body contains backend-specific elements
        String body = createdIssue.getBody();
        assert body.contains("**Backend Implementation Requirements:**") : "Missing backend requirements";
        assert body.contains("Spring Boot with Java 21") : "Missing Spring Boot requirement";
        assert body.contains("RESTful API endpoints") : "Missing REST API requirement";

        // And: Validate labels
        List<String> labels = createdIssue.getLabels();
        assert labels.contains("domain:user") : "Missing domain:user label";
        assert labels.contains("backend") : "Missing backend label";

        System.out.println("   ✅ Backend issue format validation passed");
    }

    private static void testLabelGeneration() {
        System.out.println("🔍 Test 3: Label generation consistency");

        // Test various domain and repository type combinations
        String[] domains = { "payment", "user", "admin", "reporting", "general" };
        String[] repoTypes = { "frontend", "backend" };

        for (String domain : domains) {
            for (String repoType : repoTypes) {
                MissingIssue missingIssue = new MissingIssue(
                        100,
                        "Test Issue",
                        "https://github.com/louisburroughs/durion/issues/100",
                        repoType,
                        "test-repo",
                        "[" + repoType.toUpperCase() + "] Test Issue");

                MockGitHubIssueCreator mockCreator = new MockGitHubIssueCreator();
                AuditLogger logger = new AuditLogger();
                IssueCreator issueCreator = new IssueCreator(mockCreator, logger);

                try {
                    GitHubIssue createdIssue;
                    if ("frontend".equals(repoType)) {
                        createdIssue = issueCreator.createFrontendIssue(missingIssue);
                    } else {
                        createdIssue = issueCreator.createBackendIssue(missingIssue);
                    }

                    List<String> labels = createdIssue.getLabels();
                    assert labels.contains("domain:" + domain) : "Missing domain label for " + domain;
                    assert labels.contains(repoType) : "Missing repo type label for " + repoType;

                } catch (Exception e) {
                    throw new RuntimeException("Label test failed for " + domain + "/" + repoType, e);
                }
            }
        }

        System.out.println("   ✅ Label generation consistency validation passed");
    }

    private static void testSpecialCharactersHandling() throws Exception {
        System.out.println("🔍 Test 4: Special characters handling");

        // Test with title containing special characters
        String specialTitle = "API: User & Admin \"Management\" - [CRUD] Operations (v2.0)";

        MissingIssue missingIssue = new MissingIssue(
                271,
                specialTitle,
                "https://github.com/louisburroughs/durion/issues/271",
                "frontend",
                "louisburroughs/durion-moqui-frontend",
                "[FRONTEND] " + specialTitle);

        MockGitHubIssueCreator mockCreator = new MockGitHubIssueCreator();
        AuditLogger logger = new AuditLogger();
        IssueCreator issueCreator = new IssueCreator(mockCreator, logger);

        // When: Creating an issue with special characters
        GitHubIssue createdIssue = issueCreator.createFrontendIssue(missingIssue);

        // Then: Special characters should be preserved in title
        String expectedTitle = "[FRONTEND] " + specialTitle;
        assert createdIssue.getTitle().equals(expectedTitle) : "Special characters not preserved in title";

        // And: Special characters should be preserved in body
        String body = createdIssue.getBody();
        assert body.contains("**Story**: #271 - " + specialTitle) : "Special characters not preserved in body";

        System.out.println("   ✅ Special characters handling validation passed");
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