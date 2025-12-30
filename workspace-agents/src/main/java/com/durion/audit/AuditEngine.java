package com.durion.audit;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Core audit engine that compares processed issues against implementation
 * issues
 * to identify missing frontend and backend implementation issues.
 * 
 * Enhanced with comprehensive logging and tracking capabilities.
 * 
 * Requirements: 1.3, 1.4 - Missing issue detection for both repositories
 * Requirements: 6.1, 6.2 - Comprehensive logging for audit operations and
 * decisions
 */
public class AuditEngine {

    private static final String FRONTEND_PREFIX = "[FRONTEND] [STORY]";
    private static final String BACKEND_PREFIX = "[BACKEND] [STORY]";
    private static final String FRONTEND_REPO = "louisburroughs/durion-moqui-frontend";
    private static final String BACKEND_REPO = "louisburroughs/durion-positivity-backend";

    private final AuditLogger logger;

    public AuditEngine() {
        this.logger = new AuditLogger();
    }

    /**
     * Constructor with custom logger for testing.
     */
    public AuditEngine(AuditLogger logger) {
        this.logger = logger;
    }

    /**
     * Validates the audit configuration for correctness and completeness.
     * 
     * @param configuration Audit configuration to validate
     * @throws IllegalArgumentException if configuration is invalid
     */
    public void validateConfiguration(AuditConfiguration configuration) {
        logger.logConfigurationValidation(configuration);

        if (configuration == null) {
            String error = "Audit configuration cannot be null";
            logger.logError("Configuration Validation", new IllegalArgumentException(error),
                    "Null configuration provided");
            throw new IllegalArgumentException(error);
        }

        if (configuration.getGithubToken() == null || configuration.getGithubToken().trim().isEmpty()) {
            String error = "GitHub token is required for audit operations";
            logger.logError("Configuration Validation", new IllegalArgumentException(error),
                    "Missing or empty GitHub token");
            throw new IllegalArgumentException(error);
        }

        if (configuration.getOutputDirectory() == null || configuration.getOutputDirectory().trim().isEmpty()) {
            String error = "Output directory must be specified";
            logger.logError("Configuration Validation", new IllegalArgumentException(error),
                    "Missing or empty output directory");
            throw new IllegalArgumentException(error);
        }

        // Validate date range if specified
        if (configuration.getStartDate().isPresent() && configuration.getEndDate().isPresent()) {
            if (configuration.getStartDate().get().isAfter(configuration.getEndDate().get())) {
                String error = "Start date cannot be after end date";
                logger.logError("Configuration Validation", new IllegalArgumentException(error),
                        "Start: " + configuration.getStartDate().get() + ", End: " + configuration.getEndDate().get());
                throw new IllegalArgumentException(error);
            }
        }

        // Validate story range if specified
        if (configuration.getStoryRangeStart().isPresent() && configuration.getStoryRangeEnd().isPresent()) {
            if (configuration.getStoryRangeStart().get() > configuration.getStoryRangeEnd().get()) {
                String error = "Story range start cannot be greater than end";
                logger.logError("Configuration Validation", new IllegalArgumentException(error),
                        "Start: " + configuration.getStoryRangeStart().get() + ", End: "
                                + configuration.getStoryRangeEnd().get());
                throw new IllegalArgumentException(error);
            }
        }

        // Validate rate limiting parameters
        if (configuration.getRateLimitDelayMs() < 0) {
            String error = "Rate limit delay cannot be negative";
            logger.logError("Configuration Validation", new IllegalArgumentException(error),
                    "Delay: " + configuration.getRateLimitDelayMs() + "ms");
            throw new IllegalArgumentException(error);
        }

        if (configuration.getBatchSize() <= 0) {
            String error = "Batch size must be positive";
            logger.logError("Configuration Validation", new IllegalArgumentException(error),
                    "Batch size: " + configuration.getBatchSize());
            throw new IllegalArgumentException(error);
        }
    }

    /**
     * Performs a complete audit comparing processed issues against implementation
     * issues.
     * 
     * @param processedIssues List of processed story issue numbers
     * @param frontendIssues  List of frontend implementation issues from GitHub
     * @param backendIssues   List of backend implementation issues from GitHub
     * @param storyMetadata   Map of story number to story metadata
     * @param configuration   Audit configuration settings
     * @return AuditResult containing all missing issues and statistics
     */
    public AuditResult performAudit(List<Integer> processedIssues,
            List<GitHubIssue> frontendIssues,
            List<GitHubIssue> backendIssues,
            Map<Integer, StoryMetadata> storyMetadata,
            AuditConfiguration configuration) {

        logger.logAuditStart(processedIssues, storyMetadata);

        LocalDateTime auditTimestamp = LocalDateTime.now();

        try {
            // Extract story numbers from implementation issues
            logger.logProgress("Extracting story numbers from frontend issues", 0, 2);
            Set<Integer> frontendStoryNumbers = extractStoryNumbers(frontendIssues, FRONTEND_PREFIX, storyMetadata);

            logger.logProgress("Extracting story numbers from backend issues", 1, 2);
            Set<Integer> backendStoryNumbers = extractStoryNumbers(backendIssues, BACKEND_PREFIX, storyMetadata);

            logger.logProgress("Story number extraction completed", 2, 2);

            // Find missing issues
            logger.logProgress("Finding missing frontend issues", 0, 2);
            List<MissingIssue> missingFrontendIssues = findMissingIssues(
                    processedIssues, frontendStoryNumbers, storyMetadata, "frontend", FRONTEND_REPO, FRONTEND_PREFIX);

            logger.logProgress("Finding missing backend issues", 1, 2);
            List<MissingIssue> missingBackendIssues = findMissingIssues(
                    processedIssues, backendStoryNumbers, storyMetadata, "backend", BACKEND_REPO, BACKEND_PREFIX);

            logger.logProgress("Missing issue detection completed", 2, 2);

            // Log detection results
            logger.logMissingIssueDetection(missingFrontendIssues, missingBackendIssues);

            // Create audit statistics
            AuditStatistics statistics = new AuditStatistics(
                    processedIssues.size(),
                    frontendIssues.size(),
                    backendIssues.size(),
                    missingFrontendIssues.size(),
                    missingBackendIssues.size(),
                    auditTimestamp);

            AuditResult result = new AuditResult(
                    missingFrontendIssues,
                    missingBackendIssues,
                    processedIssues.size(),
                    auditTimestamp,
                    configuration,
                    statistics);

            // Log audit summary
            logger.logAuditSummary(result);

            return result;

        } catch (Exception e) {
            logger.logError("Audit Execution", e, "Failed during audit process execution");
            throw e;
        }
    }

    /**
     * Extracts story numbers from implementation issues by parsing titles.
     * Matches titles against story metadata to identify which stories have
     * implementation issues.
     * 
     * @param issues         List of GitHub issues
     * @param expectedPrefix Expected prefix ([FRONTEND] [STORY] or [BACKEND]
     *                       [STORY])
     * @param storyMetadata  Map of story metadata for title matching
     * @return Set of story numbers found in the issues
     */
    private Set<Integer> extractStoryNumbers(List<GitHubIssue> issues,
            String expectedPrefix,
            Map<Integer, StoryMetadata> storyMetadata) {
        return issues.stream()
                .filter(issue -> issue.getTitle().startsWith(expectedPrefix))
                .map(issue -> extractStoryNumberFromTitle(issue.getTitle(), expectedPrefix, storyMetadata))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /**
     * Extracts story number from an implementation issue title by matching against
     * story metadata.
     * Expected format: "[PREFIX] Original Story Title" where the original story
     * title exactly matches a story from the story metadata.
     * 
     * @param title         Issue title
     * @param prefix        Expected prefix
     * @param storyMetadata Map of story metadata for title matching
     * @return Story number if found, null otherwise
     */
    private Integer extractStoryNumberFromTitle(String title, String prefix,
            Map<Integer, StoryMetadata> storyMetadata) {
        if (!title.startsWith(prefix)) {
            return null;
        }

        // Remove the prefix and trim
        String titleWithoutPrefix = title.substring(prefix.length()).trim();

        // Match the title against story metadata
        for (Map.Entry<Integer, StoryMetadata> entry : storyMetadata.entrySet()) {
            String storyTitle = entry.getValue().getCleanTitle();

            // Exact match
            if (titleWithoutPrefix.equals(storyTitle)) {
                return entry.getKey();
            }
        }

        // No match found
        return null;
    }

    /**
     * Finds missing issues by comparing processed issues against implementation
     * issues.
     * 
     * @param processedIssues            List of processed story numbers
     * @param implementationStoryNumbers Set of story numbers that have
     *                                   implementation issues
     * @param storyMetadata              Map of story metadata
     * @param repositoryType             "frontend" or "backend"
     * @param targetRepository           Target repository name
     * @param prefix                     Expected title prefix
     * @return List of missing issues
     */
    private List<MissingIssue> findMissingIssues(List<Integer> processedIssues,
            Set<Integer> implementationStoryNumbers,
            Map<Integer, StoryMetadata> storyMetadata,
            String repositoryType,
            String targetRepository,
            String prefix) {

        List<MissingIssue> missingIssues = new ArrayList<>();

        for (Integer storyNumber : processedIssues) {
            // Skip if implementation issue already exists
            if (implementationStoryNumbers.contains(storyNumber)) {
                continue;
            }

            // Get story metadata
            StoryMetadata metadata = storyMetadata.get(storyNumber);
            if (metadata == null) {
                // Story not found in metadata - create a basic missing issue entry
                missingIssues.add(new MissingIssue(
                        storyNumber,
                        "Story #" + storyNumber + " (metadata not found)",
                        "https://github.com/louisburroughs/durion/issues/" + storyNumber,
                        repositoryType,
                        targetRepository,
                        prefix + " Story #" + storyNumber + " (metadata not found)"));
                continue;
            }

            // Create missing issue with full metadata
            String expectedTitle = prefix + " " + metadata.getCleanTitle();

            missingIssues.add(new MissingIssue(
                    storyNumber,
                    metadata.getCleanTitle(),
                    metadata.getUrl(),
                    repositoryType,
                    targetRepository,
                    expectedTitle));
        }

        return missingIssues;
    }

    /**
     * Enhanced story number extraction that matches against known story metadata.
     * This method provides more accurate matching by comparing titles.
     * 
     * @param issues         List of GitHub issues
     * @param expectedPrefix Expected prefix
     * @param storyMetadata  Map of story metadata for title matching
     * @return Set of story numbers found in the issues
     */
    public Set<Integer> extractStoryNumbersWithMetadata(List<GitHubIssue> issues,
            String expectedPrefix,
            Map<Integer, StoryMetadata> storyMetadata) {
        return issues.stream()
                .filter(issue -> issue.getTitle().startsWith(expectedPrefix))
                .map(issue -> matchIssueToStory(issue.getTitle(), expectedPrefix, storyMetadata))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /**
     * Matches an implementation issue title to a story by comparing the title
     * content.
     * 
     * @param issueTitle    Implementation issue title
     * @param prefix        Expected prefix
     * @param storyMetadata Map of story metadata
     * @return Story number if matched, null otherwise
     */
    private Integer matchIssueToStory(String issueTitle, String prefix, Map<Integer, StoryMetadata> storyMetadata) {
        if (!issueTitle.startsWith(prefix)) {
            return null;
        }

        String titleWithoutPrefix = issueTitle.substring(prefix.length()).trim();

        // Try exact title matching first
        for (Map.Entry<Integer, StoryMetadata> entry : storyMetadata.entrySet()) {
            StoryMetadata metadata = entry.getValue();
            if (titleWithoutPrefix.equals(metadata.getCleanTitle())) {
                return entry.getKey();
            }
        }

        // Try fuzzy matching (contains check)
        for (Map.Entry<Integer, StoryMetadata> entry : storyMetadata.entrySet()) {
            StoryMetadata metadata = entry.getValue();
            String cleanTitle = metadata.getCleanTitle();

            // Check if the implementation title contains the story title or vice versa
            if (titleWithoutPrefix.contains(cleanTitle) || cleanTitle.contains(titleWithoutPrefix)) {
                return entry.getKey();
            }
        }

        return null;
    }

    /**
     * Logs issue creation attempt and result.
     * 
     * @param missingIssue The missing issue being created
     * @param success      Whether the creation was successful
     * @param errorMessage Error message if creation failed
     */
    public void logIssueCreation(MissingIssue missingIssue, boolean success, String errorMessage) {
        logger.logIssueCreation(missingIssue, success, errorMessage);
    }

    /**
     * Gets the audit logger for external access.
     * 
     * @return The audit logger instance
     */
    public AuditLogger getLogger() {
        return logger;
    }

    /**
     * Logs the end of the audit session.
     */
    public void endSession() {
        logger.logSessionEnd();
    }
}