package com.durion.audit;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * Main class for the Missing Issues Audit System.
 * Coordinates all components to perform audits and optionally create missing
 * issues.
 * 
 * Implements the complete audit workflow:
 * 1. Read processed issues from file
 * 2. Parse story metadata from coordination files (frontend-coordination.md and
 * backend-coordination.md)
 * 3. Scan GitHub repositories for implementation issues
 * 4. Compare and identify missing issues
 * 5. Generate comprehensive reports
 * 6. Optionally create missing issues with user confirmation
 * 
 * Requirements: 4.1, 5.4 - Main application workflow and progress tracking
 */
public class MissingIssuesAuditSystem {

    private final GitHubApiClientWrapper githubClient;
    private final AuditEngine auditEngine;
    private final ReportManager reportManager;
    private final GitHubRepositoryScanner repositoryScanner;
    private final IssueCreator issueCreator;
    private final AuditConfiguration configuration;

    // Repository constants
    private static final String FRONTEND_REPO = "louisburroughs/durion-moqui-frontend";
    private static final String BACKEND_REPO = "louisburroughs/durion-positivity-backend";

    public MissingIssuesAuditSystem(AuditConfiguration configuration) {
        this.configuration = configuration;
        this.githubClient = new GitHubApiClientWrapper(configuration.getGithubToken());
        this.auditEngine = new AuditEngine();
        this.reportManager = new DefaultReportManager(configuration.getOutputDirectory());
        this.repositoryScanner = new EnhancedGitHubRepositoryScanner(configuration.getGithubToken());

        // Initialize IssueCreator with SSL bypass GitHub client
        GitHubIssueCreator githubIssueCreator = new SSLBypassGitHubIssueCreator(configuration.getGithubToken());
        this.issueCreator = new IssueCreator(githubIssueCreator, auditEngine.getLogger());

        // Validate configuration
        auditEngine.validateConfiguration(configuration);
    }

    /**
     * Constructor with custom components for testing.
     */
    public MissingIssuesAuditSystem(AuditConfiguration configuration,
            AuditEngine auditEngine,
            ReportManager reportManager,
            GitHubRepositoryScanner repositoryScanner) {
        this.configuration = configuration;
        this.githubClient = new GitHubApiClientWrapper(configuration.getGithubToken());
        this.auditEngine = auditEngine;
        this.reportManager = reportManager;
        this.repositoryScanner = repositoryScanner;

        // Initialize IssueCreator with SSL bypass GitHub client
        GitHubIssueCreator githubIssueCreator = new SSLBypassGitHubIssueCreator(configuration.getGithubToken());
        this.issueCreator = new IssueCreator(githubIssueCreator, auditEngine.getLogger());

        // Validate configuration
        auditEngine.validateConfiguration(configuration);
    }

    /**
     * Runs a complete audit operation according to the configuration.
     * 
     * @return AuditResult containing all missing issues and statistics
     * @throws IOException          if the audit operation fails
     * @throws InterruptedException if the operation is interrupted
     */
    public AuditResult runAudit() throws IOException, InterruptedException {
        System.out.println("🔍 Starting Missing Issues Audit System");
        System.out.println("======================================");

        try {
            // Step 1: Read processed issues
            System.out.println("📋 Step 1: Reading processed issues...");
            ProcessedIssuesReader issuesReader = new ProcessedIssuesReader();
            List<Integer> processedIssues = issuesReader.readProcessedIssues();
            System.out.println("   ✅ Found " + processedIssues.size() + " processed issues");

            // Apply filtering based on configuration
            List<Integer> filteredIssues = applyFiltering(processedIssues);
            System.out.println("   📊 After filtering: " + filteredIssues.size() + " issues to audit");

            // Step 2: Parse story metadata
            System.out.println("📖 Step 2: Parsing story metadata...");
            StoryMetadataParser metadataParser = new StoryMetadataParser();
            Map<Integer, StoryMetadata> storyMetadata = metadataParser.parseCoordinationFiles();
            System.out.println("   ✅ Loaded metadata for " + storyMetadata.size() + " stories");

            // Step 3: Test repository access
            System.out.println("🔗 Step 3: Testing repository access...");
            if (!testRepositoryAccess()) {
                throw new IOException("Repository access test failed. Check GitHub token permissions.");
            }
            System.out.println("   ✅ Repository access confirmed");

            // Step 4: Scan repositories for implementation issues
            System.out.println("🔍 Step 4: Scanning repositories for implementation issues...");

            System.out.println("   📱 Scanning frontend repository: " + FRONTEND_REPO);
            List<Object> frontendIssuesRaw = repositoryScanner.scanFrontendIssues(FRONTEND_REPO);
            List<GitHubIssue> frontendIssues = convertToGitHubIssues(frontendIssuesRaw);
            System.out.println("   ✅ Found " + frontendIssues.size() + " frontend implementation issues");

            System.out.println("   🖥️ Scanning backend repository: " + BACKEND_REPO);
            List<Object> backendIssuesRaw = repositoryScanner.scanBackendIssues(BACKEND_REPO);
            List<GitHubIssue> backendIssues = convertToGitHubIssues(backendIssuesRaw);
            System.out.println("   ✅ Found " + backendIssues.size() + " backend implementation issues");

            // Step 5: Perform audit comparison
            System.out.println("🔍 Step 5: Performing audit comparison...");
            AuditResult auditResult = auditEngine.performAudit(
                    filteredIssues, frontendIssues, backendIssues, storyMetadata, configuration);

            System.out.println("   📊 Audit Results:");
            System.out.println("      • Missing Frontend Issues: " + auditResult.getMissingFrontendIssues().size());
            System.out.println("      • Missing Backend Issues: " + auditResult.getMissingBackendIssues().size());
            System.out.println("      • Total Missing Issues: " + auditResult.getTotalMissingIssues());

            // Step 6: Generate reports
            System.out.println("📄 Step 6: Generating audit reports...");
            List<String> reportPaths = reportManager.generateReports(auditResult);
            System.out.println("   ✅ Generated " + reportPaths.size() + " report files:");
            for (String path : reportPaths) {
                System.out.println("      • " + path);
            }

            // Step 7: Handle issue creation if requested
            if (configuration.isCreateMissingIssues() && auditResult.getTotalMissingIssues() > 0) {
                System.out.println("🎯 Step 7: Creating missing issues...");
                handleIssueCreation(auditResult);
            } else if (auditResult.getTotalMissingIssues() > 0) {
                System.out.println("💡 To create missing issues automatically, use the --create-issues flag");
            }

            System.out.println();
            System.out.println("✅ Audit completed successfully!");
            System.out.println("📊 Summary: " + auditResult.getTotalMissingIssues() + " missing issues found");

            return auditResult;

        } catch (ProcessedIssuesReader.ProcessedIssuesReaderException e) {
            System.err.println("❌ Error reading processed issues: " + e.getMessage());
            throw new IOException("Failed to read processed issues", e);
        } catch (StoryMetadataParser.StoryMetadataParserException e) {
            System.err.println("❌ Error parsing story metadata: " + e.getMessage());
            throw new IOException("Failed to parse story metadata", e);
        } catch (Exception e) {
            System.err.println("❌ Unexpected error during audit: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Creates missing implementation issues from an existing audit report.
     * 
     * @param reportPath Path to the audit report JSON file
     * @return List of successfully created issues
     * @throws IOException          if issue creation fails
     * @throws InterruptedException if the operation is interrupted
     */
    public List<Object> createMissingIssues(String reportPath)
            throws IOException, InterruptedException {
        System.out.println("🎯 Creating missing issues from report: " + reportPath);

        // TODO: Implement issue creation from report
        // This will be implemented when issue creation functionality is added
        throw new UnsupportedOperationException("Issue creation from report not yet implemented");
    }

    /**
     * Applies filtering based on audit configuration (date range, story range,
     * etc.).
     */
    private List<Integer> applyFiltering(List<Integer> processedIssues) {
        if (configuration.getAuditMode() == AuditMode.FULL_AUDIT) {
            return processedIssues;
        }

        // Apply incremental filtering using IncrementalAuditFilter
        IncrementalAuditFilter filter = new IncrementalAuditFilter(auditEngine.getLogger());

        // For now, implement basic filtering based on audit mode
        if (configuration.getAuditMode() == AuditMode.INCREMENTAL_RANGE) {
            if (configuration.getStoryRangeStart().isPresent() && configuration.getStoryRangeEnd().isPresent()) {
                return filter.filterByStoryRange(processedIssues,
                        configuration.getStoryRangeStart().get(),
                        configuration.getStoryRangeEnd().get());
            }
        }

        // For other modes, return all issues for now
        // TODO: Implement date-based filtering when story metadata includes processing
        // dates
        return processedIssues;
    }

    /**
     * Tests access to both frontend and backend repositories.
     */
    private boolean testRepositoryAccess() {
        System.out.println("   🔍 Testing frontend repository access...");
        boolean frontendAccess = repositoryScanner.testRepositoryAccess(FRONTEND_REPO);

        System.out.println("   🔍 Testing backend repository access...");
        boolean backendAccess = repositoryScanner.testRepositoryAccess(BACKEND_REPO);

        return frontendAccess && backendAccess;
    }

    /**
     * Converts raw repository scanner results to GitHubIssue objects.
     */
    private List<GitHubIssue> convertToGitHubIssues(List<Object> rawIssues) {
        List<GitHubIssue> gitHubIssues = new java.util.ArrayList<>();

        for (Object rawIssue : rawIssues) {
            if (rawIssue instanceof GitHubIssue) {
                gitHubIssues.add((GitHubIssue) rawIssue);
            } else {
                System.err.println("⚠️ Warning: Unexpected issue type: " + rawIssue.getClass().getName());
            }
        }

        return gitHubIssues;
    }

    /**
     * Handles the issue creation process with user confirmation and progress
     * tracking.
     */
    private void handleIssueCreation(AuditResult auditResult) throws IOException, InterruptedException {
        int totalMissingIssues = auditResult.getTotalMissingIssues();

        System.out.println("🎯 Issue Creation Process");
        System.out.println("========================");
        System.out.println("Found " + totalMissingIssues + " missing issues to create:");
        System.out.println("  • Frontend: " + auditResult.getMissingFrontendIssues().size());
        System.out.println("  • Backend: " + auditResult.getMissingBackendIssues().size());
        System.out.println();

        // Get user confirmation
        if (!getUserConfirmation("Do you want to proceed with creating these issues? (y/N): ")) {
            System.out.println("❌ Issue creation cancelled by user");
            return;
        }

        System.out.println("🚀 Starting issue creation process...");
        System.out.println();

        int successCount = 0;
        int errorCount = 0;
        int current = 0;

        // Create frontend issues
        if (!auditResult.getMissingFrontendIssues().isEmpty()) {
            System.out.println("📱 Creating frontend implementation issues...");
            for (MissingIssue missingIssue : auditResult.getMissingFrontendIssues()) {
                current++;
                trackProgress("Frontend issues", current, auditResult.getMissingFrontendIssues().size());

                try {
                    GitHubIssue createdIssue = issueCreator.createFrontendIssue(missingIssue);
                    System.out.println("   ✅ Created: " + createdIssue.getUrl());
                    successCount++;

                    // Add delay between issue creations to respect rate limits
                    if (current < auditResult.getMissingFrontendIssues().size()) {
                        Thread.sleep(configuration.getRateLimitDelayMs());
                    }
                } catch (IOException | InterruptedException e) {
                    System.out.println("   ❌ Failed to create issue for story #" + missingIssue.getStoryNumber() + ": "
                            + e.getMessage());
                    errorCount++;
                }
            }
            System.out.println();
        }

        // Create backend issues
        if (!auditResult.getMissingBackendIssues().isEmpty()) {
            System.out.println("🖥️ Creating backend implementation issues...");
            current = 0;
            for (MissingIssue missingIssue : auditResult.getMissingBackendIssues()) {
                current++;
                trackProgress("Backend issues", current, auditResult.getMissingBackendIssues().size());

                try {
                    GitHubIssue createdIssue = issueCreator.createBackendIssue(missingIssue);
                    System.out.println("   ✅ Created: " + createdIssue.getUrl());
                    successCount++;

                    // Add delay between issue creations to respect rate limits
                    if (current < auditResult.getMissingBackendIssues().size()) {
                        Thread.sleep(configuration.getRateLimitDelayMs());
                    }
                } catch (IOException | InterruptedException e) {
                    System.out.println("   ❌ Failed to create issue for story #" + missingIssue.getStoryNumber() + ": "
                            + e.getMessage());
                    errorCount++;
                }
            }
            System.out.println();
        }

        // Display summary
        System.out.println("✅ Issue creation completed!");
        System.out.println("   📊 Successfully created: " + successCount + " issues");
        if (errorCount > 0) {
            System.out.println("   ⚠️ Failed to create: " + errorCount + " issues");
        }
    }

    /**
     * Gets user confirmation for issue creation.
     */
    private boolean getUserConfirmation(String prompt) {
        System.out.print(prompt);
        Scanner scanner = new Scanner(System.in);
        String response = scanner.nextLine().trim().toLowerCase();
        return response.equals("y") || response.equals("yes");
    }

    /**
     * Provides progress tracking for long-running operations.
     */
    public void trackProgress(String operation, int current, int total) {
        double percentage = (double) current / total * 100;
        System.out.printf("   📊 %s: %d/%d (%.1f%%)%n", operation, current, total, percentage);
    }

    /**
     * Gets the GitHub API client for direct access if needed.
     * 
     * @return The configured GitHub API client
     */
    public GitHubApiClientWrapper getGithubClient() {
        return githubClient;
    }

    /**
     * Gets the audit configuration.
     * 
     * @return The audit configuration
     */
    public AuditConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * Gets the audit engine for external access.
     * 
     * @return The audit engine
     */
    public AuditEngine getAuditEngine() {
        return auditEngine;
    }

    /**
     * Gets the report manager for external access.
     * 
     * @return The report manager
     */
    public ReportManager getReportManager() {
        return reportManager;
    }

    /**
     * Gets the repository scanner for external access.
     * 
     * @return The repository scanner
     */
    public GitHubRepositoryScanner getRepositoryScanner() {
        return repositoryScanner;
    }
}