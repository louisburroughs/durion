package com.durion.audit;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Contains the results of an audit operation, including all missing issues
 * and metadata about the audit execution.
 */
public class AuditResult {
    private final List<MissingIssue> missingFrontendIssues;
    private final List<MissingIssue> missingBackendIssues;
    private final int totalProcessedStories;
    private final LocalDateTime auditTimestamp;
    private final AuditConfiguration configuration;
    private final AuditStatistics statistics;

    public AuditResult(List<MissingIssue> missingFrontendIssues,
                      List<MissingIssue> missingBackendIssues,
                      int totalProcessedStories,
                      LocalDateTime auditTimestamp,
                      AuditConfiguration configuration,
                      AuditStatistics statistics) {
        this.missingFrontendIssues = Objects.requireNonNull(missingFrontendIssues, "Missing frontend issues list cannot be null");
        this.missingBackendIssues = Objects.requireNonNull(missingBackendIssues, "Missing backend issues list cannot be null");
        this.totalProcessedStories = totalProcessedStories;
        this.auditTimestamp = Objects.requireNonNull(auditTimestamp, "Audit timestamp cannot be null");
        this.configuration = Objects.requireNonNull(configuration, "Audit configuration cannot be null");
        this.statistics = Objects.requireNonNull(statistics, "Audit statistics cannot be null");
    }

    public List<MissingIssue> getMissingFrontendIssues() {
        return missingFrontendIssues;
    }

    public List<MissingIssue> getMissingBackendIssues() {
        return missingBackendIssues;
    }

    public int getTotalProcessedStories() {
        return totalProcessedStories;
    }

    public LocalDateTime getAuditTimestamp() {
        return auditTimestamp;
    }

    public AuditConfiguration getConfiguration() {
        return configuration;
    }

    public AuditStatistics getStatistics() {
        return statistics;
    }

    public int getTotalMissingIssues() {
        return missingFrontendIssues.size() + missingBackendIssues.size();
    }

    public List<MissingIssue> getAllMissingIssues() {
        List<MissingIssue> allMissing = new java.util.ArrayList<>(missingFrontendIssues);
        allMissing.addAll(missingBackendIssues);
        return allMissing;
    }

    public boolean hasAnyMissingIssues() {
        return !missingFrontendIssues.isEmpty() || !missingBackendIssues.isEmpty();
    }

    @Override
    public String toString() {
        return String.format("AuditResult{timestamp=%s, processed=%d, missingFrontend=%d, missingBackend=%d}", 
                           auditTimestamp, totalProcessedStories, 
                           missingFrontendIssues.size(), missingBackendIssues.size());
    }
}