package com.durion.audit;

import com.durion.GitHubApiClientSSLBypass;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;

/**
 * Wrapper class to provide a clean interface to GitHubApiClientSSLBypass for the audit package.
 * Converts between the SSL bypass client's types and the audit system's interfaces.
 */
public class GitHubApiClientWrapper {
    
    private final GitHubApiClientSSLBypass sslBypassClient;
    
    public GitHubApiClientWrapper(String githubToken) {
        this.sslBypassClient = new GitHubApiClientSSLBypass(githubToken);
    }
    
    /**
     * Gets story issues from a repository using the SSL bypass client.
     */
    public List<GitHubIssue> getStoryIssues(String repository) throws IOException, InterruptedException {
        List<GitHubApiClientSSLBypass.GitHubIssue> rawIssues = sslBypassClient.getStoryIssues(repository);
        
        // Convert the results to our GitHubIssue interface
        List<GitHubIssue> convertedIssues = new ArrayList<>();
        for (GitHubApiClientSSLBypass.GitHubIssue rawIssue : rawIssues) {
            convertedIssues.add(new GitHubIssueWrapper(rawIssue));
        }
        
        return convertedIssues;
    }
    
    /**
     * Tests the connection using the SSL bypass client.
     */
    public boolean testConnection() {
        return sslBypassClient.testConnection();
    }
    
    /**
     * Checks rate limit and waits if necessary.
     */
    public void checkRateLimitAndWait() throws IOException, InterruptedException {
        sslBypassClient.checkRateLimitAndWait();
    }
    
    /**
     * Wrapper class for GitHubIssue objects from the SSL bypass client.
     */
    private static class GitHubIssueWrapper implements GitHubIssue {
        private final GitHubApiClientSSLBypass.GitHubIssue delegate;
        
        public GitHubIssueWrapper(GitHubApiClientSSLBypass.GitHubIssue delegate) {
            this.delegate = delegate;
        }
        
        @Override
        public int getNumber() {
            return delegate.getNumber();
        }
        
        @Override
        public String getTitle() {
            return delegate.getTitle();
        }
        
        @Override
        public String getBody() {
            return delegate.getBody();
        }
        
        @Override
        public String getUrl() {
            return delegate.getUrl();
        }
        
        @Override
        public List<String> getLabels() {
            return delegate.getLabels();
        }
        
        @Override
        public String toString() {
            return String.format("Issue #%d: %s (%s)", getNumber(), getTitle(), getUrl());
        }
    }
}