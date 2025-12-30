package com.durion.audit;

import java.util.Objects;

/**
 * Represents a missing implementation issue that should exist but doesn't.
 * This is a core data model for the audit system.
 */
public class MissingIssue {
    private final int storyNumber;
    private final String storyTitle;
    private final String storyUrl;
    private final String repositoryType; // "frontend" or "backend"
    private final String targetRepository;
    private final String expectedTitle;

    public MissingIssue(int storyNumber, String storyTitle, String storyUrl,
            String repositoryType, String targetRepository,
            String expectedTitle) {
        this.storyNumber = storyNumber;
        this.storyTitle = Objects.requireNonNull(storyTitle, "Story title cannot be null");
        this.storyUrl = Objects.requireNonNull(storyUrl, "Story URL cannot be null");
        this.repositoryType = Objects.requireNonNull(repositoryType, "Repository type cannot be null");
        this.targetRepository = Objects.requireNonNull(targetRepository, "Target repository cannot be null");
        this.expectedTitle = Objects.requireNonNull(expectedTitle, "Expected title cannot be null");
    }

    public int getStoryNumber() {
        return storyNumber;
    }

    public String getStoryTitle() {
        return storyTitle;
    }

    public String getStoryUrl() {
        return storyUrl;
    }

    public String getRepositoryType() {
        return repositoryType;
    }

    public String getTargetRepository() {
        return targetRepository;
    }

    public String getExpectedTitle() {
        return expectedTitle;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        MissingIssue that = (MissingIssue) o;
        return storyNumber == that.storyNumber &&
                Objects.equals(repositoryType, that.repositoryType) &&
                Objects.equals(targetRepository, that.targetRepository);
    }

    @Override
    public int hashCode() {
        return Objects.hash(storyNumber, repositoryType, targetRepository);
    }

    @Override
    public String toString() {
        return String.format("MissingIssue{story=#%d, type=%s, repo=%s, title='%s'}",
                storyNumber, repositoryType, targetRepository, storyTitle);
    }
}