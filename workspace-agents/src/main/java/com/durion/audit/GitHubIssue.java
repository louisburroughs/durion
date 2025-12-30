package com.durion.audit;

import java.util.List;

/**
 * Interface representing a GitHub issue with the essential properties needed for audit operations.
 * This allows the audit system to work with different GitHub issue implementations.
 */
public interface GitHubIssue {
    
    /**
     * Gets the issue number.
     * @return The GitHub issue number
     */
    int getNumber();
    
    /**
     * Gets the issue title.
     * @return The issue title
     */
    String getTitle();
    
    /**
     * Gets the issue body/description.
     * @return The issue body
     */
    String getBody();
    
    /**
     * Gets the issue URL.
     * @return The GitHub issue URL
     */
    String getUrl();
    
    /**
     * Gets the issue labels.
     * @return List of label names
     */
    List<String> getLabels();
}