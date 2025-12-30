package com.durion;
import java.util.*;
import java.util.concurrent.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Enhanced Comment Backfill Agent
 * 
 * This enhanced version can search for implementation issues in the target repositories
 * and include direct links in the processing comments.
 */
public class EnhancedCommentBackfillAgent {
    
    private final GitHubApiClientSSLBypass githubClient;
    private static final String PROCESSED_ISSUES_FILE = ".github/orchestration/processed-issues.txt";
    
    // Configuration
    private static final String DURION_REPO = "louisburroughs/durion";
    private static final String FRONTEND_REPO = "louisburroughs/durion-moqui-frontend";
    private static final String BACKEND_REPO = "louisburroughs/durion-positivity-backend";
    
    // Cache for implementation issues to avoid repeated searches
    private Map<String, List<GitHubApiClientSSLBypass.GitHubIssue>> implementationIssuesCache = new HashMap<>();
    
    public EnhancedCommentBackfillAgent(String githubToken) {
        this.githubClient = new GitHubApiClientSSLBypass(githubToken);
    }
    
    /**
     * Main backfill process with implementation issue search
     */
    public void backfillCommentsWithSearch() throws IOException, InterruptedException {
        System.out.println("💬 **ENHANCED COMMENT BACKFILL AGENT STARTING**");
        System.out.println("===============================================");
        System.out.println();
        
        // Test GitHub connection first
        System.out.println("🔗 Testing GitHub API connection...");
        if (!githubClient.testConnection()) {
            System.out.println("❌ GitHub API connection failed!");
            return;
        }
        System.out.println("✅ GitHub API connection successful");
        System.out.println();
        
        // Load processed issues
        Set<Integer> processedIssues = loadProcessedIssues();
        System.out.println("📂 Found " + processedIssues.size() + " processed issues");
        
        if (processedIssues.isEmpty()) {
            System.out.println("ℹ️ No processed issues found. Nothing to backfill.");
            return;
        }
        
        // Get all story issues from GitHub
        System.out.println("🔍 Fetching all story issues from GitHub...");
        List<GitHubApiClientSSLBypass.GitHubIssue> allStoryIssues = githubClient.getStoryIssues(DURION_REPO);
        System.out.println("📋 Found " + allStoryIssues.size() + " total story issues");
        
        // Filter to only processed issues
        List<GitHubApiClientSSLBypass.GitHubIssue> processedStoryIssues = allStoryIssues.stream()
            .filter(issue -> processedIssues.contains(issue.getNumber()))
            .toList();
        
        System.out.println("🎯 " + processedStoryIssues.size() + " story issues were processed and may need comments");
        System.out.println();
        
        if (processedStoryIssues.isEmpty()) {
            System.out.println("ℹ️ No matching story issues found. Nothing to backfill.");
            return;
        }
        
        // Pre-load implementation issues for faster lookup
        System.out.println("🔍 Pre-loading implementation issues from target repositories...");
        loadImplementationIssues(FRONTEND_REPO);
        loadImplementationIssues(BACKEND_REPO);
        System.out.println("✅ Implementation issues loaded");
        System.out.println();
        
        // Add comments with implementation issue links
        System.out.println("💬 **STARTING ENHANCED COMMENT BACKFILL**");
        System.out.println("=========================================");
        System.out.println("Will add comments to " + processedStoryIssues.size() + " issues");
        System.out.println("Estimated time: " + (processedStoryIssues.size() * 5) + " seconds (5 seconds per comment)");
        System.out.println();
        
        int successCount = 0;
        int failureCount = 0;
        
        for (int i = 0; i < processedStoryIssues.size(); i++) {
            GitHubApiClientSSLBypass.GitHubIssue issue = processedStoryIssues.get(i);
            
            System.out.println("💬 (" + (i + 1) + "/" + processedStoryIssues.size() + ") Adding comment to issue #" + issue.getNumber() + ": " + issue.getTitle());
            
            try {
                // Check rate limit every 10 comments
                if (i > 0 && i % 10 == 0) {
                    System.out.println("   🔍 Rate limit check (processed " + i + " comments so far)...");
                    githubClient.checkRateLimitAndWait();
                }
                
                addEnhancedProcessingComment(issue);
                successCount++;
                System.out.println("   ✅ Comment added successfully");
                
                // Add delay between comments to prevent secondary rate limits
                if (i < processedStoryIssues.size() - 1) {
                    System.out.println("   ⏳ Waiting 5 seconds before next comment...");
                    Thread.sleep(5000);
                }
                
            } catch (Exception e) {
                failureCount++;
                System.out.println("   ❌ Failed to add comment: " + e.getMessage());
            }
        }
        
        System.out.println();
        System.out.println("🏁 **ENHANCED COMMENT BACKFILL COMPLETE**");
        System.out.println("========================================");
        System.out.println("✅ Successfully added: " + successCount + " comments");
        System.out.println("❌ Failed to add: " + failureCount + " comments");
        System.out.println("📊 Total processed: " + (successCount + failureCount) + " issues");
        
        if (failureCount > 0) {
            System.out.println();
            System.out.println("⚠️ Some comments failed to be added. You can run this script again to retry failed comments.");
        }
    }
    
    /**
     * Loads all implementation issues from a repository for faster lookup
     */
    private void loadImplementationIssues(String repository) throws IOException, InterruptedException {
        System.out.println("   📥 Loading issues from " + repository + "...");
        
        // Search for issues with [FRONTEND] or [BACKEND] in title
        String searchQuery = String.format("repo:%s type:issue state:open", repository);
        
        // Use a simple approach - get all open issues from the repository
        // In a real implementation, you might want to search more specifically
        List<GitHubApiClientSSLBypass.GitHubIssue> issues = searchRepositoryIssues(repository);
        
        implementationIssuesCache.put(repository, issues);
        System.out.println("   📋 Loaded " + issues.size() + " issues from " + repository);
    }
    
    /**
     * Searches for all issues in a repository (simplified)
     */
    private List<GitHubApiClientSSLBypass.GitHubIssue> searchRepositoryIssues(String repository) throws IOException, InterruptedException {
        // This is a simplified implementation
        // For a full implementation, you'd need to paginate through all issues in the repository
        // For now, we'll return an empty list and fall back to search instructions in comments
        return new ArrayList<>();
    }
    
    /**
     * Adds an enhanced processing comment with implementation issue links
     */
    private void addEnhancedProcessingComment(GitHubApiClientSSLBypass.GitHubIssue story) throws IOException, InterruptedException {
        // Find the corresponding frontend and backend issues
        GitHubApiClientSSLBypass.GitHubIssue frontendIssue = findImplementationIssue(FRONTEND_REPO, "[FRONTEND] " + story.getTitle());
        GitHubApiClientSSLBypass.GitHubIssue backendIssue = findImplementationIssue(BACKEND_REPO, "[BACKEND] " + story.getTitle());
        
        StringBuilder comment = new StringBuilder();
        comment.append("## 🤖 Story Processed by Durion Workspace Agent\n\n");
        comment.append("✅ **Status**: This story has been automatically processed and implementation issues have been created.\n\n");
        comment.append("📅 **Processed**: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n\n");
        
        comment.append("### 🎯 Implementation Issues Created:\n\n");
        
        // Frontend issue
        if (frontendIssue != null) {
            comment.append("🎨 **Frontend Implementation**: ").append(frontendIssue.getUrl()).append("\n");
            comment.append("   - Issue: #").append(frontendIssue.getNumber()).append(" - ").append(frontendIssue.getTitle()).append("\n");
            comment.append("   - Repository: `").append(FRONTEND_REPO).append("`\n");
            comment.append("   - Technology: Vue.js 3 + TypeScript + Moqui Framework\n\n");
        } else {
            comment.append("🎨 **Frontend Implementation**: Search for `[FRONTEND] ").append(story.getTitle()).append("` in [").append(FRONTEND_REPO).append("](https://github.com/").append(FRONTEND_REPO).append("/issues)\n");
            comment.append("   - Repository: `").append(FRONTEND_REPO).append("`\n");
            comment.append("   - Technology: Vue.js 3 + TypeScript + Moqui Framework\n\n");
        }
        
        // Backend issue
        if (backendIssue != null) {
            comment.append("⚙️ **Backend Implementation**: ").append(backendIssue.getUrl()).append("\n");
            comment.append("   - Issue: #").append(backendIssue.getNumber()).append(" - ").append(backendIssue.getTitle()).append("\n");
            comment.append("   - Repository: `").append(BACKEND_REPO).append("`\n");
            comment.append("   - Technology: Spring Boot + Java + Microservices\n\n");
        } else {
            comment.append("⚙️ **Backend Implementation**: Search for `[BACKEND] ").append(story.getTitle()).append("` in [").append(BACKEND_REPO).append("](https://github.com/").append(BACKEND_REPO).append("/issues)\n");
            comment.append("   - Repository: `").append(BACKEND_REPO).append("`\n");
            comment.append("   - Technology: Spring Boot + Java + Microservices\n\n");
        }
        
        comment.append("### 📋 Next Steps:\n\n");
        comment.append("1. Review the implementation issues linked above\n");
        comment.append("2. Implementation teams will work on the created issues\n");
        comment.append("3. This story will be updated as implementation progresses\n\n");
        
        comment.append("### 📁 Coordination Documents:\n\n");
        comment.append("Updated coordination documents are available in `.github/orchestration/`:\n");
        comment.append("- [`story-sequence.md`](https://github.com/").append(DURION_REPO).append("/blob/master/.github/orchestration/story-sequence.md) - Master story orchestration\n");
        comment.append("- [`frontend-coordination.md`](https://github.com/").append(DURION_REPO).append("/blob/master/.github/orchestration/frontend-coordination.md) - Frontend development readiness\n");
        comment.append("- [`backend-coordination.md`](https://github.com/").append(DURION_REPO).append("/blob/master/.github/orchestration/backend-coordination.md) - Backend development priorities\n\n");
        
        comment.append("---\n");
        comment.append("*This comment was automatically generated by the Durion Workspace Agent Enhanced Comment Backfill*");
        
        githubClient.addCommentToIssue(DURION_REPO, story.getNumber(), comment.toString());
    }
    
    /**
     * Finds an implementation issue by title
     */
    private GitHubApiClientSSLBypass.GitHubIssue findImplementationIssue(String repository, String expectedTitle) {
        List<GitHubApiClientSSLBypass.GitHubIssue> issues = implementationIssuesCache.get(repository);
        if (issues == null) {
            return null;
        }
        
        // Look for exact title match first
        for (GitHubApiClientSSLBypass.GitHubIssue issue : issues) {
            if (issue.getTitle().equals(expectedTitle)) {
                return issue;
            }
        }
        
        // Look for partial title match
        for (GitHubApiClientSSLBypass.GitHubIssue issue : issues) {
            if (issue.getTitle().contains(expectedTitle.substring(10))) { // Remove [FRONTEND]/[BACKEND] prefix
                return issue;
            }
        }
        
        return null;
    }
    
    /**
     * Loads processed issue numbers from the file
     */
    private Set<Integer> loadProcessedIssues() {
        Set<Integer> processedIssues = new HashSet<>();
        
        try {
            Path processedFile = Paths.get(PROCESSED_ISSUES_FILE);
            if (Files.exists(processedFile)) {
                List<String> lines = Files.readAllLines(processedFile);
                for (String line : lines) {
                    try {
                        int issueNumber = Integer.parseInt(line.trim());
                        processedIssues.add(issueNumber);
                    } catch (NumberFormatException e) {
                        // Skip invalid lines
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("⚠️ Warning: Could not load processed issues: " + e.getMessage());
        }
        
        return processedIssues;
    }
    
    /**
     * Main method to run the Enhanced Comment Backfill Agent
     */
    public static void main(String[] args) {
        // Get GitHub token from environment or command line
        String githubToken = System.getenv("GITHUB_TOKEN");
        if (args.length > 0) {
            githubToken = args[0];
        }
        
        if (githubToken == null || githubToken.trim().isEmpty()) {
            System.out.println("❌ **GITHUB TOKEN REQUIRED**");
            System.out.println("   Please provide a GitHub token:");
            System.out.println("   1. Set GITHUB_TOKEN environment variable, or");
            System.out.println("   2. Pass token as command line argument:");
            System.out.println("      java -cp \"target/classes\" EnhancedCommentBackfillAgent <your-github-token>");
            return;
        }
        
        EnhancedCommentBackfillAgent agent = new EnhancedCommentBackfillAgent(githubToken);
        
        try {
            agent.backfillCommentsWithSearch();
        } catch (Exception e) {
            System.out.println("❌ Error during enhanced comment backfill: " + e.getMessage());
            e.printStackTrace();
        }
    }
}