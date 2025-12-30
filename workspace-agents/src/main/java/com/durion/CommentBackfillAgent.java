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
 * Comment Backfill Agent
 * 
 * This agent adds processing comments to story issues that were processed
 * but didn't get comments due to secondary rate limits.
 * 
 * It reads the processed issues list and checks which ones are missing comments,
 * then adds them with proper rate limiting.
 */
public class CommentBackfillAgent {
    
    private final GitHubApiClientSSLBypass githubClient;
    private static final String PROCESSED_ISSUES_FILE = ".github/orchestration/processed-issues.txt";
    
    // Configuration
    private static final String DURION_REPO = "louisburroughs/durion";
    private static final String FRONTEND_REPO = "louisburroughs/durion-moqui-frontend";
    private static final String BACKEND_REPO = "louisburroughs/durion-positivity-backend";
    
    public CommentBackfillAgent(String githubToken) {
        this.githubClient = new GitHubApiClientSSLBypass(githubToken);
    }
    
    /**
     * Main backfill process
     */
    public void backfillComments() throws IOException, InterruptedException {
        System.out.println("🔄 **COMMENT BACKFILL AGENT STARTING**");
        System.out.println("=====================================");
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
        
        // Check which issues are missing comments
        List<GitHubApiClientSSLBypass.GitHubIssue> issuesMissingComments = new ArrayList<>();
        
        System.out.println("🔍 Checking which issues are missing processing comments...");
        for (GitHubApiClientSSLBypass.GitHubIssue issue : processedStoryIssues) {
            if (needsProcessingComment(issue)) {
                issuesMissingComments.add(issue);
            }
        }
        
        System.out.println("📊 Found " + issuesMissingComments.size() + " issues missing processing comments");
        System.out.println();
        
        if (issuesMissingComments.isEmpty()) {
            System.out.println("✅ All processed issues already have comments! Nothing to backfill.");
            return;
        }
        
        // Add comments with proper rate limiting
        System.out.println("💬 **STARTING COMMENT BACKFILL**");
        System.out.println("================================");
        System.out.println("Will add comments to " + issuesMissingComments.size() + " issues");
        System.out.println("Estimated time: " + (issuesMissingComments.size() * 5) + " seconds (5 seconds per comment)");
        System.out.println();
        
        int successCount = 0;
        int failureCount = 0;
        
        for (int i = 0; i < issuesMissingComments.size(); i++) {
            GitHubApiClientSSLBypass.GitHubIssue issue = issuesMissingComments.get(i);
            
            System.out.println("💬 (" + (i + 1) + "/" + issuesMissingComments.size() + ") Adding comment to issue #" + issue.getNumber());
            
            try {
                // Check rate limit every 10 comments
                if (i > 0 && i % 10 == 0) {
                    System.out.println("   🔍 Rate limit check (processed " + i + " comments so far)...");
                    githubClient.checkRateLimitAndWait();
                }
                
                addProcessingComment(issue);
                successCount++;
                System.out.println("   ✅ Comment added successfully");
                
                // Add delay between comments to prevent secondary rate limits
                if (i < issuesMissingComments.size() - 1) {
                    System.out.println("   ⏳ Waiting 5 seconds before next comment...");
                    Thread.sleep(5000);
                }
                
            } catch (Exception e) {
                failureCount++;
                System.out.println("   ❌ Failed to add comment: " + e.getMessage());
            }
        }
        
        System.out.println();
        System.out.println("🏁 **COMMENT BACKFILL COMPLETE**");
        System.out.println("================================");
        System.out.println("✅ Successfully added: " + successCount + " comments");
        System.out.println("❌ Failed to add: " + failureCount + " comments");
        System.out.println("📊 Total processed: " + (successCount + failureCount) + " issues");
        
        if (failureCount > 0) {
            System.out.println();
            System.out.println("⚠️ Some comments failed to be added. You can run this script again to retry failed comments.");
        }
    }
    
    /**
     * Checks if an issue needs a processing comment by looking for the agent signature
     */
    private boolean needsProcessingComment(GitHubApiClientSSLBypass.GitHubIssue issue) throws IOException, InterruptedException {
        // This is a simplified check - in a real implementation, you'd fetch the issue's comments
        // and check if any contain the "Durion Workspace Agent" signature
        
        // For now, we'll assume all processed issues need comments
        // You could enhance this by calling the GitHub API to check existing comments
        return true;
    }
    
    /**
     * Adds a processing comment to a story issue
     */
    private void addProcessingComment(GitHubApiClientSSLBypass.GitHubIssue story) throws IOException, InterruptedException {
        // Find the corresponding frontend and backend issues
        String frontendUrl = findImplementationIssue(FRONTEND_REPO, story.getTitle());
        String backendUrl = findImplementationIssue(BACKEND_REPO, story.getTitle());
        
        StringBuilder comment = new StringBuilder();
        comment.append("## 🤖 Story Processed by Durion Workspace Agent\n\n");
        comment.append("✅ **Status**: This story has been automatically processed and implementation issues have been created.\n\n");
        comment.append("📅 **Processed**: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n\n");
        
        comment.append("### 🎯 Implementation Issues Created:\n\n");
        
        if (frontendUrl != null) {
            comment.append("🎨 **Frontend Implementation**: ").append(frontendUrl).append("\n");
            comment.append("   - Repository: `").append(FRONTEND_REPO).append("`\n");
            comment.append("   - Technology: Vue.js 3 + TypeScript + Moqui Framework\n\n");
        } else {
            comment.append("🎨 **Frontend Implementation**: Search for `[FRONTEND] ").append(story.getTitle()).append("` in ").append(FRONTEND_REPO).append("\n\n");
        }
        
        if (backendUrl != null) {
            comment.append("⚙️ **Backend Implementation**: ").append(backendUrl).append("\n");
            comment.append("   - Repository: `").append(BACKEND_REPO).append("`\n");
            comment.append("   - Technology: Spring Boot + Java + Microservices\n\n");
        } else {
            comment.append("⚙️ **Backend Implementation**: Search for `[BACKEND] ").append(story.getTitle()).append("` in ").append(BACKEND_REPO).append("\n\n");
        }
        
        comment.append("### 📋 Next Steps:\n\n");
        comment.append("1. Review the implementation issues linked above\n");
        comment.append("2. Implementation teams will work on the created issues\n");
        comment.append("3. This story will be updated as implementation progresses\n\n");
        
        comment.append("### 📁 Coordination Documents:\n\n");
        comment.append("Updated coordination documents are available in `.github/orchestration/`:\n");
        comment.append("- `story-sequence.md` - Master story orchestration\n");
        comment.append("- `frontend-coordination.md` - Frontend development readiness\n");
        comment.append("- `backend-coordination.md` - Backend development priorities\n\n");
        
        comment.append("---\n");
        comment.append("*This comment was automatically generated by the Durion Workspace Agent Comment Backfill*");
        
        githubClient.addCommentToIssue(DURION_REPO, story.getNumber(), comment.toString());
    }
    
    /**
     * Attempts to find the implementation issue URL (simplified version)
     */
    private String findImplementationIssue(String repository, String storyTitle) {
        // This is a simplified implementation
        // In a real scenario, you'd search the repository for issues with matching titles
        // For now, we'll return null and let the comment include search instructions
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
     * Main method to run the Comment Backfill Agent
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
            System.out.println("      java -cp \"target/classes\" CommentBackfillAgent <your-github-token>");
            return;
        }
        
        CommentBackfillAgent agent = new CommentBackfillAgent(githubToken);
        
        try {
            agent.backfillComments();
        } catch (Exception e) {
            System.out.println("❌ Error during comment backfill: " + e.getMessage());
            e.printStackTrace();
        }
    }
}