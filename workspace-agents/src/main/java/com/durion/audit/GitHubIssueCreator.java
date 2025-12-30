package com.durion.audit;

import java.io.IOException;
import java.util.List;

/**
 * Interface for creating GitHub issues in target repositories.
 * This abstraction allows different implementations of GitHub issue creation.
 */
public interface GitHubIssueCreator {
    
    /**
     * Creates a GitHub issue in the specified repository.
     * 
     * @param repository The target repository (e.g., "owner/repo")
     * @param title The issue title
     * @param body The issue body content
     * @param labels List of labels to apply to the issue
     * @return The created GitHub issue
     * @throws IOException if issue creation fails
     * @throws InterruptedException if the operation is interrupted
     */
    GitHubIssue createIssue(String repository, String title, String body, List<String> labels) 
            throws IOException, InterruptedException;
}