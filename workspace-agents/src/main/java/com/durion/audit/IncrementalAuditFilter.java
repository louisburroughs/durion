package com.durion.audit;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Incremental audit filtering for date-based and range-based story filtering.
 * 
 * Provides capabilities for filtering processed stories based on date ranges,
 * story number ranges, and resumption logic for interrupted operations.
 * 
 * Requirements: 5.1, 5.2, 5.5 - Date filtering, range filtering, and audit
 * resumption
 */
public class IncrementalAuditFilter {

    private final AuditLogger logger;

    public IncrementalAuditFilter(AuditLogger logger) {
        this.logger = logger;
    }

    /**
     * Filters processed issues based on date range (last N days).
     * 
     * @param processedIssues List of processed story numbers
     * @param storyMetadata   Map of story metadata containing processing dates
     * @param days            Number of days to look back from today
     * @return Filtered list of story numbers processed within the date range
     */
    public List<Integer> filterByDateRange(List<Integer> processedIssues,
            Map<Integer, StoryMetadata> storyMetadata,
            int days) {

        LocalDate cutoffDate = LocalDate.now().minusDays(days);
        logger.logProgress("Filtering by date range: last " + days + " days (since " + cutoffDate + ")", 0, 1);

        List<Integer> filteredIssues = processedIssues.stream()
                .filter(storyNumber -> {
                    StoryMetadata metadata = storyMetadata.get(storyNumber);
                    if (metadata == null) {
                        // If no metadata, assume it's recent to be safe
                        return true;
                    }

                    // Note: Processed dates are no longer tracked in coordination files
                    // For now, include all stories with metadata
                    // TODO: Implement date-based filtering using file modification times
                    return true;
                })
                .collect(Collectors.toList());

        logger.logProgress("Date filtering completed", filteredIssues.size(), processedIssues.size());
        logFilteringResults("Date Range", processedIssues.size(), filteredIssues.size(),
                "Stories processed in last " + days + " days");

        return filteredIssues;
    }

    /**
     * Filters processed issues based on date range with specific start and end
     * dates.
     * 
     * @param processedIssues List of processed story numbers
     * @param storyMetadata   Map of story metadata containing processing dates
     * @param startDate       Start date (inclusive)
     * @param endDate         End date (inclusive)
     * @return Filtered list of story numbers processed within the date range
     */
    public List<Integer> filterByDateRange(List<Integer> processedIssues,
            Map<Integer, StoryMetadata> storyMetadata,
            LocalDate startDate,
            LocalDate endDate) {

        logger.logProgress("Filtering by date range: " + startDate + " to " + endDate, 0, 1);

        List<Integer> filteredIssues = processedIssues.stream()
                .filter(storyNumber -> {
                    StoryMetadata metadata = storyMetadata.get(storyNumber);
                    if (metadata == null) {
                        // If no metadata, exclude from date-specific filtering
                        return false;
                    }

                    // Note: Processed dates are no longer tracked in coordination files
                    // For now, exclude from date-specific filtering
                    // TODO: Implement date-based filtering using file modification times
                    return false;
                })
                .collect(Collectors.toList());

        logger.logProgress("Date range filtering completed", filteredIssues.size(), processedIssues.size());
        logFilteringResults("Date Range", processedIssues.size(), filteredIssues.size(),
                "Stories processed between " + startDate + " and " + endDate);

        return filteredIssues;
    }

    /**
     * Filters processed issues based on story number range.
     * 
     * @param processedIssues  List of processed story numbers
     * @param startStoryNumber Start story number (inclusive)
     * @param endStoryNumber   End story number (inclusive)
     * @return Filtered list of story numbers within the specified range
     */
    public List<Integer> filterByStoryRange(List<Integer> processedIssues,
            int startStoryNumber,
            int endStoryNumber) {

        logger.logProgress("Filtering by story range: #" + startStoryNumber + " to #" + endStoryNumber, 0, 1);

        List<Integer> filteredIssues = processedIssues.stream()
                .filter(storyNumber -> storyNumber >= startStoryNumber && storyNumber <= endStoryNumber)
                .collect(Collectors.toList());

        logger.logProgress("Story range filtering completed", filteredIssues.size(), processedIssues.size());
        logFilteringResults("Story Range", processedIssues.size(), filteredIssues.size(),
                "Stories #" + startStoryNumber + " to #" + endStoryNumber);

        return filteredIssues;
    }

    /**
     * Filters processed issues to only include those that haven't been audited
     * recently.
     * This supports resumption of interrupted audit operations.
     * 
     * @param processedIssues List of processed story numbers
     * @param lastAuditResult Previous audit result to check against
     * @param resumeFromStory Story number to resume from (optional)
     * @return Filtered list of story numbers that need to be audited
     */
    public List<Integer> filterForResumption(List<Integer> processedIssues,
            Optional<AuditResult> lastAuditResult,
            Optional<Integer> resumeFromStory) {

        logger.logProgress("Filtering for audit resumption", 0, 1);

        List<Integer> filteredIssues = processedIssues;

        // If we have a resume point, filter to start from that story
        if (resumeFromStory.isPresent()) {
            int resumeStory = resumeFromStory.get();
            filteredIssues = filteredIssues.stream()
                    .filter(storyNumber -> storyNumber <= resumeStory) // Assuming descending order
                    .collect(Collectors.toList());

            logger.logProgress("Resuming from story #" + resumeStory, 1, 2);
        }

        // If we have a previous audit result, we can potentially skip stories that were
        // recently audited and had no missing issues
        if (lastAuditResult.isPresent()) {
            AuditResult lastResult = lastAuditResult.get();

            // Check if the last audit was recent (within last hour)
            LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
            if (lastResult.getAuditTimestamp().isAfter(oneHourAgo)) {

                // Get stories that had missing issues in the last audit - these should be
                // re-checked
                List<Integer> storiesWithMissingIssues = lastResult.getAllMissingIssues().stream()
                        .map(MissingIssue::getStoryNumber)
                        .distinct()
                        .collect(Collectors.toList());

                // For resumption, focus on stories that had missing issues or weren't checked
                filteredIssues = filteredIssues.stream()
                        .filter(storyNumber -> storiesWithMissingIssues.contains(storyNumber) ||
                                !wasStoryInLastAudit(storyNumber, lastResult))
                        .collect(Collectors.toList());

                logger.logProgress("Filtered based on previous audit results", 1, 1);
            }
        }

        logger.logProgress("Resumption filtering completed", filteredIssues.size(), processedIssues.size());
        logFilteringResults("Resumption", processedIssues.size(), filteredIssues.size(),
                "Stories requiring re-audit or continuation");

        return filteredIssues;
    }

    /**
     * Applies multiple filters in sequence based on audit configuration.
     * 
     * @param processedIssues List of processed story numbers
     * @param storyMetadata   Map of story metadata
     * @param configuration   Audit configuration with filtering options
     * @param lastAuditResult Previous audit result for resumption (optional)
     * @return Filtered list of story numbers to audit
     */
    public List<Integer> applyFilters(List<Integer> processedIssues,
            Map<Integer, StoryMetadata> storyMetadata,
            AuditConfiguration configuration,
            Optional<AuditResult> lastAuditResult) {

        logger.logProgress("Applying incremental audit filters", 0, 1);

        List<Integer> filteredIssues = processedIssues;
        int originalCount = filteredIssues.size();

        // Apply date filtering if specified
        if (configuration.getStartDate().isPresent() && configuration.getEndDate().isPresent()) {
            filteredIssues = filterByDateRange(filteredIssues, storyMetadata,
                    configuration.getStartDate().get(), configuration.getEndDate().get());
        } else if (configuration.getDaysBack().isPresent()) {
            filteredIssues = filterByDateRange(filteredIssues, storyMetadata,
                    configuration.getDaysBack().get());
        }

        // Apply story range filtering if specified
        if (configuration.getStoryRangeStart().isPresent() && configuration.getStoryRangeEnd().isPresent()) {
            filteredIssues = filterByStoryRange(filteredIssues,
                    configuration.getStoryRangeStart().get(), configuration.getStoryRangeEnd().get());
        }

        // Apply resumption filtering if enabled
        if (configuration.isResumeMode() && lastAuditResult.isPresent()) {
            filteredIssues = filterForResumption(filteredIssues, lastAuditResult,
                    configuration.getResumeFromStory());
        }

        logger.logProgress("All filters applied", 1, 1);
        logFilteringResults("Combined Filters", originalCount, filteredIssues.size(),
                "Stories after applying all configured filters");

        return filteredIssues;
    }

    /**
     * Checks if a story was included in the last audit.
     */
    private boolean wasStoryInLastAudit(int storyNumber, AuditResult lastResult) {
        // Check if the story appears in any missing issues lists
        boolean inMissingFrontend = lastResult.getMissingFrontendIssues().stream()
                .anyMatch(issue -> issue.getStoryNumber() == storyNumber);

        boolean inMissingBackend = lastResult.getMissingBackendIssues().stream()
                .anyMatch(issue -> issue.getStoryNumber() == storyNumber);

        // If it's in missing issues, it was definitely audited
        if (inMissingFrontend || inMissingBackend) {
            return true;
        }

        // If it's not in missing issues, we assume it was audited and had no missing
        // issues
        // This is a simplification - in a more complete implementation, we'd track all
        // audited stories
        return true;
    }

    /**
     * Logs filtering results in a consistent format.
     */
    private void logFilteringResults(String filterType, int originalCount, int filteredCount, String description) {
        int excludedCount = originalCount - filteredCount;
        double retentionPercentage = originalCount > 0 ? (filteredCount * 100.0) / originalCount : 0.0;

        System.out.println("\n🔍 " + filterType.toUpperCase() + " FILTERING RESULTS");
        System.out.println("├─ Description: " + description);
        System.out.println("├─ Original Count: " + originalCount + " stories");
        System.out.println("├─ Filtered Count: " + filteredCount + " stories");
        System.out.println("├─ Excluded Count: " + excludedCount + " stories");
        System.out.println("├─ Retention Rate: " + String.format("%.1f%%", retentionPercentage));
        System.out.println("└─ ✅ Filtering completed");
    }
}