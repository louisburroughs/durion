package com.durion.audit;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * Configuration settings for the audit system, including filtering options,
 * GitHub token, and operational parameters.
 */
public class AuditConfiguration {
    private final String githubToken;
    private final AuditMode auditMode;
    private final Optional<LocalDate> startDate;
    private final Optional<LocalDate> endDate;
    private final Optional<Integer> storyRangeStart;
    private final Optional<Integer> storyRangeEnd;
    private final boolean createMissingIssues;
    private final boolean useCache;
    private final String outputDirectory;
    private final int rateLimitDelayMs;
    private final int batchSize;
    private final Optional<Integer> daysBack;
    private final boolean resumeMode;
    private final Optional<Integer> resumeFromStory;

    private AuditConfiguration(Builder builder) {
        this.githubToken = Objects.requireNonNull(builder.githubToken, "GitHub token cannot be null");
        this.auditMode = Objects.requireNonNull(builder.auditMode, "Audit mode cannot be null");
        this.startDate = Optional.ofNullable(builder.startDate);
        this.endDate = Optional.ofNullable(builder.endDate);
        this.storyRangeStart = Optional.ofNullable(builder.storyRangeStart);
        this.storyRangeEnd = Optional.ofNullable(builder.storyRangeEnd);
        this.createMissingIssues = builder.createMissingIssues;
        this.useCache = builder.useCache;
        this.outputDirectory = Objects.requireNonNull(builder.outputDirectory, "Output directory cannot be null");
        this.rateLimitDelayMs = builder.rateLimitDelayMs;
        this.batchSize = builder.batchSize;
        this.daysBack = Optional.ofNullable(builder.daysBack);
        this.resumeMode = builder.resumeMode;
        this.resumeFromStory = Optional.ofNullable(builder.resumeFromStory);
    }

    public String getGithubToken() {
        return githubToken;
    }

    public AuditMode getAuditMode() {
        return auditMode;
    }

    public Optional<LocalDate> getStartDate() {
        return startDate;
    }

    public Optional<LocalDate> getEndDate() {
        return endDate;
    }

    public Optional<Integer> getStoryRangeStart() {
        return storyRangeStart;
    }

    public Optional<Integer> getStoryRangeEnd() {
        return storyRangeEnd;
    }

    public boolean isCreateMissingIssues() {
        return createMissingIssues;
    }

    public boolean isUseCache() {
        return useCache;
    }

    public String getOutputDirectory() {
        return outputDirectory;
    }

    public int getRateLimitDelayMs() {
        return rateLimitDelayMs;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public Optional<Integer> getDaysBack() {
        return daysBack;
    }

    public boolean isResumeMode() {
        return resumeMode;
    }

    public Optional<Integer> getResumeFromStory() {
        return resumeFromStory;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String githubToken;
        private AuditMode auditMode = AuditMode.FULL_AUDIT;
        private LocalDate startDate;
        private LocalDate endDate;
        private Integer storyRangeStart;
        private Integer storyRangeEnd;
        private boolean createMissingIssues = false;
        private boolean useCache = true;
        private String outputDirectory = ".github/orchestration/missing-issues/";
        private int rateLimitDelayMs = 2000;
        private int batchSize = 5;
        private Integer daysBack;
        private boolean resumeMode = false;
        private Integer resumeFromStory;

        public Builder githubToken(String githubToken) {
            this.githubToken = githubToken;
            return this;
        }

        public Builder auditMode(AuditMode auditMode) {
            this.auditMode = auditMode;
            return this;
        }

        public Builder dateRange(LocalDate startDate, LocalDate endDate) {
            this.startDate = startDate;
            this.endDate = endDate;
            return this;
        }

        public Builder storyRange(Integer start, Integer end) {
            this.storyRangeStart = start;
            this.storyRangeEnd = end;
            return this;
        }

        public Builder createMissingIssues(boolean createMissingIssues) {
            this.createMissingIssues = createMissingIssues;
            return this;
        }

        public Builder useCache(boolean useCache) {
            this.useCache = useCache;
            return this;
        }

        public Builder outputDirectory(String outputDirectory) {
            this.outputDirectory = outputDirectory;
            return this;
        }

        public Builder rateLimitDelayMs(int rateLimitDelayMs) {
            this.rateLimitDelayMs = rateLimitDelayMs;
            return this;
        }

        public Builder batchSize(int batchSize) {
            this.batchSize = batchSize;
            return this;
        }

        public Builder setDaysBack(Integer daysBack) {
            this.daysBack = daysBack;
            return this;
        }

        public Builder setResumeMode(boolean resumeMode) {
            this.resumeMode = resumeMode;
            return this;
        }

        public Builder setResumeFromStory(Integer resumeFromStory) {
            this.resumeFromStory = resumeFromStory;
            return this;
        }

        public AuditConfiguration build() {
            return new AuditConfiguration(this);
        }
    }

    @Override
    public String toString() {
        return String.format("AuditConfiguration{mode=%s, createIssues=%s, useCache=%s, outputDir='%s'}", 
                           auditMode, createMissingIssues, useCache, outputDirectory);
    }
}