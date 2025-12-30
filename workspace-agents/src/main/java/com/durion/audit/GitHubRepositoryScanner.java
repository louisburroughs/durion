package com.durion.audit;

import java.io.IOException;
import java.util.List;

/**
 * Interface for scanning GitHub repositories to find implementation issues.
 */
public interface GitHubRepositoryScanner {

        /**
         * Scans a repository for frontend implementation issues with [FRONTEND] [STORY]
         * prefix.
         * 
         * @param repository The repository to scan (e.g.,
         *                   "louisburroughs/durion-moqui-frontend")
         * @return List of frontend implementation issues found
         * @throws IOException          if repository scanning fails
         * @throws InterruptedException if the operation is interrupted
         */
        List<Object> scanFrontendIssues(String repository) // Temporarily using Object
                        throws IOException, InterruptedException;

        /**
         * Scans a repository for backend implementation issues with [BACKEND] [STORY]
         * prefix.
         * 
         * @param repository The repository to scan (e.g.,
         *                   "louisburroughs/durion-positivity-backend")
         * @return List of backend implementation issues found
         * @throws IOException          if repository scanning fails
         * @throws InterruptedException if the operation is interrupted
         */
        List<Object> scanBackendIssues(String repository) // Temporarily using Object
                        throws IOException, InterruptedException;

        /**
         * Scans a repository for implementation issues with a specific title pattern.
         * 
         * @param repository   The repository to scan
         * @param titlePattern The pattern to search for in issue titles (e.g.,
         *                     "[FRONTEND] [STORY]", "[BACKEND] [STORY]")
         * @return List of implementation issues matching the pattern
         * @throws IOException          if repository scanning fails
         * @throws InterruptedException if the operation is interrupted
         */
        List<Object> scanIssuesWithPattern(String repository, String titlePattern) // Temporarily using Object
                        throws IOException, InterruptedException;

        /**
         * Tests the connection to GitHub API and validates repository access.
         * 
         * @param repository The repository to test access for
         * @return true if the repository is accessible, false otherwise
         */
        boolean testRepositoryAccess(String repository);

        /**
         * Gets the current rate limit status from GitHub API.
         * 
         * @return Rate limit information or null if unavailable
         * @throws IOException          if rate limit check fails
         * @throws InterruptedException if the operation is interrupted
         */
        RateLimitInfo getRateLimitStatus() throws IOException, InterruptedException;
}