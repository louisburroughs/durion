package com.durion.audit;

import com.durion.GitHubApiClientSSLBypass;
import java.io.IOException;
import java.util.List;

/**
 * Implementation of GitHubIssueCreator that uses GitHubApiClientSSLBypass.
 * This class bridges the audit package with the SSL bypass client.
 */
public class SSLBypassGitHubIssueCreator implements GitHubIssueCreator {
    
    private final GitHubApiClientSSLBypass sslBypassClient;
    
    public SSLBypassGitHubIssueCreator(String githubToken) {
        this.sslBypassClient = new GitHubApiClientSSLBypass(githubToken);
    }
    
    @Override
    public GitHubIssue createIssue(String repository, String title, String body, List<String> labels) 
            throws IOException, InterruptedException {
        
        GitHubApiClientSSLBypass.GitHubIssue rawIssue = sslBypassClient.createIssue(repository, title, body, labels);
        
        // Convert to audit.GitHubIssue interface
        return new GitHubIssueAdapter(rawIssue);
    }
    
    /**
     * Adapter class to convert GitHubApiClientSSLBypass.GitHubIssue to audit.GitHubIssue interface
     */
    private static class GitHubIssueAdapter implements GitHubIssue {
        private final GitHubApiClientSSLBypass.GitHubIssue delegate;
        
        public GitHubIssueAdapter(GitHubApiClientSSLBypass.GitHubIssue delegate) {
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
            return delegate.toString();
        }
    }
}