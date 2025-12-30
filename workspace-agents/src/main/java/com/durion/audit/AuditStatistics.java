package com.durion.audit;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Statistics and metrics about an audit operation.
 * Provides summary information for reporting and analysis.
 */
public class AuditStatistics {
    private final int totalProcessedStories;
    private final int totalFrontendIssues;
    private final int totalBackendIssues;
    private final int missingFrontendCount;
    private final int missingBackendCount;
    private final LocalDateTime auditTimestamp;

    public AuditStatistics(int totalProcessedStories,
            int totalFrontendIssues,
            int totalBackendIssues,
            int missingFrontendCount,
            int missingBackendCount,
            LocalDateTime auditTimestamp) {
        this.totalProcessedStories = totalProcessedStories;
        this.totalFrontendIssues = totalFrontendIssues;
        this.totalBackendIssues = totalBackendIssues;
        this.missingFrontendCount = missingFrontendCount;
        this.missingBackendCount = missingBackendCount;
        this.auditTimestamp = Objects.requireNonNull(auditTimestamp, "Audit timestamp cannot be null");
    }

    public int getTotalProcessedStories() {
        return totalProcessedStories;
    }

    public int getTotalFrontendIssues() {
        return totalFrontendIssues;
    }

    public int getTotalBackendIssues() {
        return totalBackendIssues;
    }

    public int getFrontendIssuesFound() {
        return totalFrontendIssues;
    }

    public int getBackendIssuesFound() {
        return totalBackendIssues;
    }

    public int getMissingFrontendCount() {
        return missingFrontendCount;
    }

    public int getMissingBackendCount() {
        return missingBackendCount;
    }

    public int getTotalMissingCount() {
        return missingFrontendCount + missingBackendCount;
    }

    public LocalDateTime getAuditTimestamp() {
        return auditTimestamp;
    }

    /**
     * Calculates the percentage of stories that have frontend implementation
     * issues.
     */
    public double getFrontendCompletionPercentage() {
        if (totalProcessedStories == 0)
            return 0.0;
        int completedFrontend = totalProcessedStories - missingFrontendCount;
        return roundToSingleDecimal((completedFrontend * 100.0) / totalProcessedStories);
    }

    /**
     * Calculates the percentage of stories that have backend implementation issues.
     */
    public double getBackendCompletionPercentage() {
        if (totalProcessedStories == 0)
            return 0.0;
        int completedBackend = totalProcessedStories - missingBackendCount;
        return roundToSingleDecimal((completedBackend * 100.0) / totalProcessedStories);
    }

    /**
     * Calculates the overall completion percentage (both frontend and backend).
     */
    public double getOverallCompletionPercentage() {
        if (totalProcessedStories == 0)
            return 0.0;
        int totalExpectedIssues = totalProcessedStories * 2; // frontend + backend
        int totalMissingIssues = missingFrontendCount + missingBackendCount;
        int totalCompletedIssues = totalExpectedIssues - totalMissingIssues;
        return roundToSingleDecimal((totalCompletedIssues * 100.0) / totalExpectedIssues);
    }

    private double roundToSingleDecimal(double value) {
        return BigDecimal.valueOf(value)
                .setScale(1, RoundingMode.HALF_UP)
                .doubleValue();
    }

    @Override
    public String toString() {
        return String.format(
                "AuditStatistics{processed=%d, frontend=%d/%d (%.1f%%), backend=%d/%d (%.1f%%), overall=%.1f%%}",
                totalProcessedStories,
                totalProcessedStories - missingFrontendCount, totalProcessedStories, getFrontendCompletionPercentage(),
                totalProcessedStories - missingBackendCount, totalProcessedStories, getBackendCompletionPercentage(),
                getOverallCompletionPercentage());
    }
}