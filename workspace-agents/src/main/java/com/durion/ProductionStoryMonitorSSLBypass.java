package com.durion;
import java.util.*;
import java.util.concurrent.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * Production GitHub Story Monitor with SSL Bypass
 * 
 * This version uses GitHubApiClientSSLBypass to work around SSL certificate issues
 * commonly encountered in corporate environments.
 * 
 * ⚠️ WARNING: Only use this in development environments!
 */
public class ProductionStoryMonitorSSLBypass {
    
    private final GitHubApiClientSSLBypass githubClient;
    private final ScheduledExecutorService scheduler;
    private volatile boolean running = false;
    private Set<Integer> processedIssueNumbers = new HashSet<>();
    private static final String PROCESSED_ISSUES_FILE = ".github/orchestration/processed-issues.txt";
    
    // Configuration
    private static final int POLLING_INTERVAL_MINUTES = 5;
    private static final String DURION_REPO = "louisburroughs/durion";
    private static final String FRONTEND_REPO = "louisburroughs/durion-moqui-frontend";
    private static final String BACKEND_REPO = "louisburroughs/durion-positivity-backend";
    
    public ProductionStoryMonitorSSLBypass(String githubToken) {
        this.githubClient = new GitHubApiClientSSLBypass(githubToken);
        this.scheduler = Executors.newScheduledThreadPool(2);
        
        // Load previously processed issues
        loadProcessedIssues();
    }
    
    /**
     * Starts the production story monitoring service
     */
    public void startMonitoring() {
        if (running) {
            System.out.println("⚠️ Story monitoring is already running");
            return;
        }
        
        // Test GitHub connection first
        System.out.println("🔗 Testing GitHub API connection (SSL bypass mode)...");
        if (!githubClient.testConnection()) {
            System.out.println("❌ GitHub API connection failed!");
            System.out.println("   Please check your GitHub token.");
            return;
        }
        System.out.println("✅ GitHub API connection successful");
        
        running = true;
        System.out.println();
        System.out.println("🚀 **STARTING PRODUCTION STORY PROCESSING (SSL BYPASS)**");
        System.out.println("========================================================");
        System.out.println("📋 Monitoring: " + DURION_REPO);
        System.out.println("⏱️ Polling Interval: " + POLLING_INTERVAL_MINUTES + " minutes");
        System.out.println("🎯 Looking for: [STORY] labeled issues");
        System.out.println("📁 Writing files to: .github/orchestration/");
        System.out.println("🔗 Creating issues in: " + FRONTEND_REPO + ", " + BACKEND_REPO);
        System.out.println("⚠️ SSL Certificate validation bypassed for development");
        System.out.println();
        
        // Create orchestration directory if it doesn't exist
        try {
            Path orchestrationDir = Paths.get(".github/orchestration");
            Files.createDirectories(orchestrationDir);
            System.out.println("📁 Created orchestration directory: " + orchestrationDir.toAbsolutePath());
        } catch (IOException e) {
            System.out.println("⚠️ Warning: Could not create orchestration directory: " + e.getMessage());
        }
        
        // Schedule periodic story processing
        scheduler.scheduleAtFixedRate(
            this::processStories,
            0, // Start immediately
            POLLING_INTERVAL_MINUTES,
            TimeUnit.MINUTES
        );
        
        System.out.println("✅ **PRODUCTION STORY PROCESSING STARTED**");
        System.out.println("   The system will now:");
        System.out.println("   1. 🔍 Monitor " + DURION_REPO + " for [STORY] issues via GitHub API");
        System.out.println("   2. 📊 Analyze and sequence new stories");
        System.out.println("   3. 📝 Write coordination documents to .github/orchestration/");
        System.out.println("   4. 🎯 Create real implementation issues in target repositories");
        System.out.println();
        System.out.println("📊 **MONITORING STATUS: ACTIVE** 🟢");
        System.out.println();
    }
    
    /**
     * Main story processing loop - connects to real GitHub API
     */
    private void processStories() {
        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            System.out.println("🔍 [" + timestamp + "] Fetching [STORY] issues from GitHub...");
            
            // Step 1: Fetch real stories from GitHub
            List<GitHubApiClientSSLBypass.GitHubIssue> storyIssues = githubClient.getStoryIssues(DURION_REPO);
            System.out.println("   📋 Found " + storyIssues.size() + " [STORY] issues in repository");
            
            // Step 2: Check for new stories
            List<GitHubApiClientSSLBypass.GitHubIssue> newStories = storyIssues.stream()
                .filter(issue -> !processedIssueNumbers.contains(issue.getNumber()))
                .toList();
            
            if (newStories.isEmpty()) {
                System.out.println("   ℹ️ No new stories detected");
                
                // Still update coordination documents with current stories
                updateCoordinationDocuments(storyIssues);
                return;
            }
            
            System.out.println("   🆕 " + newStories.size() + " new stories detected! Processing...");
            for (GitHubApiClientSSLBypass.GitHubIssue story : newStories) {
                System.out.println("      • #" + story.getNumber() + ": " + story.getTitle());
            }
            
            // Check rate limit before processing stories
            System.out.println("   🔍 Checking rate limit before processing " + newStories.size() + " stories...");
            githubClient.checkRateLimitAndWait();
            
            // Step 3: Process new stories with rate limiting and secondary rate limit protection
            for (int i = 0; i < newStories.size(); i++) {
                GitHubApiClientSSLBypass.GitHubIssue story = newStories.get(i);
                
                System.out.println("   📊 Processing story " + (i + 1) + " of " + newStories.size());
                
                // Check rate limit every 5 stories (reduced from 10 for secondary rate limit protection)
                if (i > 0 && i % 5 == 0) {
                    System.out.println("   🔍 Rate limit check (processed " + i + " stories so far)...");
                    githubClient.checkRateLimitAndWait();
                    
                    // Add extra delay to prevent secondary rate limits
                    System.out.println("   ⏳ Adding 10 second delay to prevent secondary rate limits...");
                    Thread.sleep(10000);
                }
                
                processNewStory(story);
                processedIssueNumbers.add(story.getNumber());
                saveProcessedIssue(story.getNumber()); // Persist to file
                
                // Add delay between each story to be more respectful
                if (i < newStories.size() - 1) { // Don't delay after the last story
                    Thread.sleep(2000); // 2 second delay between stories
                }
            }
            
            // Step 4: Update coordination documents with all stories
            updateCoordinationDocuments(storyIssues);
            
            System.out.println("   ✅ Story processing complete!");
            System.out.println("   📝 Updated coordination documents in .github/orchestration/");
            System.out.println("   🎯 Created implementation issues for " + newStories.size() + " stories");
            System.out.println();
            
        } catch (Exception e) {
            System.out.println("❌ Error during story processing: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Processes a single new story
     */
    private void processNewStory(GitHubApiClientSSLBypass.GitHubIssue story) {
        try {
            System.out.println("   🔄 Processing story #" + story.getNumber() + ": " + story.getTitle());
            
            // Extract domain from story (simple heuristic)
            String domain = extractDomain(story.getTitle(), story.getBody());
            
            // Create implementation issues directly using GitHub API
            String frontendUrl = null;
            String backendUrl = null;
            
            try {
                // Create frontend issue
                String frontendTitle = "[FRONTEND] " + story.getTitle();
                String frontendBody = createFrontendIssueBody(story.getTitle(), story.getBody(), domain);
                GitHubApiClientSSLBypass.GitHubIssue frontendIssue = githubClient.createIssue(
                    FRONTEND_REPO, frontendTitle, frontendBody, Arrays.asList("frontend", "story-implementation", domain)
                );
                frontendUrl = frontendIssue.getUrl();
                System.out.println("         🎨 Frontend: " + frontendUrl);
                
                // Create backend issue
                String backendTitle = "[BACKEND] " + story.getTitle();
                String backendBody = createBackendIssueBody(story.getTitle(), story.getBody(), domain);
                GitHubApiClientSSLBypass.GitHubIssue backendIssue = githubClient.createIssue(
                    BACKEND_REPO, backendTitle, backendBody, Arrays.asList("backend", "story-implementation", domain)
                );
                backendUrl = backendIssue.getUrl();
                System.out.println("         ⚙️ Backend: " + backendUrl);
                
                System.out.println("      ✅ Created implementation issues successfully");
                
            } catch (Exception e) {
                System.out.println("      ⚠️ Issue creation failed: " + e.getMessage());
            }
            
            // Add comment to original story issue with links to implementation issues
            addProcessingComment(story, frontendUrl, backendUrl);
            
        } catch (Exception e) {
            System.out.println("      ❌ Error processing story: " + e.getMessage());
        }
    }
    
    /**
     * Adds a comment to the original story issue indicating it has been processed
     */
    private void addProcessingComment(GitHubApiClientSSLBypass.GitHubIssue story, String frontendUrl, String backendUrl) {
        try {
            System.out.println("      💬 Adding processing comment to original story...");
            
            StringBuilder comment = new StringBuilder();
            comment.append("## 🤖 Story Processed by Durion Workspace Agent\n\n");
            comment.append("✅ **Status**: This story has been automatically processed and implementation issues have been created.\n\n");
            comment.append("📅 **Processed**: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n\n");
            
            comment.append("### 🎯 Implementation Issues Created:\n\n");
            
            if (frontendUrl != null) {
                comment.append("🎨 **Frontend Implementation**: ").append(frontendUrl).append("\n");
                comment.append("   - Repository: `").append(FRONTEND_REPO).append("`\n");
                comment.append("   - Technology: Vue.js 3 + TypeScript + Moqui Framework\n\n");
            }
            
            if (backendUrl != null) {
                comment.append("⚙️ **Backend Implementation**: ").append(backendUrl).append("\n");
                comment.append("   - Repository: `").append(BACKEND_REPO).append("`\n");
                comment.append("   - Technology: Spring Boot + Java + Microservices\n\n");
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
            comment.append("*This comment was automatically generated by the Durion Workspace Agent*");
            
            githubClient.addCommentToIssue(DURION_REPO, story.getNumber(), comment.toString());
            System.out.println("      ✅ Added processing comment to story #" + story.getNumber());
            
        } catch (Exception e) {
            System.out.println("      ⚠️ Warning: Could not add comment to story #" + story.getNumber() + ": " + e.getMessage());
            // Don't fail the entire processing if comment fails
        }
    }
    
    /**
     * Updates coordination documents with current story state
     */
    private void updateCoordinationDocuments(List<GitHubApiClientSSLBypass.GitHubIssue> stories) {
        try {
            System.out.println("   📝 Updating coordination documents...");
            
            // Create coordination documents directly
            createStorySequenceDocument(stories);
            createFrontendCoordinationDocument(stories);
            createBackendCoordinationDocument(stories);
            
            System.out.println("      ✅ Updated story-sequence.md");
            System.out.println("      ✅ Updated frontend-coordination.md");
            System.out.println("      ✅ Updated backend-coordination.md");
            
        } catch (Exception e) {
            System.out.println("      ❌ Error updating coordination documents: " + e.getMessage());
        }
    }
    
    /**
     * Extracts domain from story title and body
     */
    private String extractDomain(String title, String body) {
        String text = (title + " " + body).toLowerCase();
        
        // Simple domain detection heuristics
        if (text.contains("payment") || text.contains("billing")) return "payment";
        if (text.contains("user") || text.contains("auth") || text.contains("login")) return "user";
        if (text.contains("product") || text.contains("catalog")) return "product";
        if (text.contains("order") || text.contains("cart")) return "order";
        if (text.contains("inventory") || text.contains("stock")) return "inventory";
        if (text.contains("customer") || text.contains("crm")) return "customer";
        if (text.contains("report") || text.contains("analytics")) return "reporting";
        if (text.contains("admin") || text.contains("management")) return "admin";
        
        return "general";
    }
    
    /**
     * Stops the monitoring service
     */
    public void stopMonitoring() {
        if (!running) {
            System.out.println("⚠️ Story monitoring is not running");
            return;
        }
        
        running = false;
        scheduler.shutdown();
        
        System.out.println("🛑 **STOPPING PRODUCTION STORY PROCESSING**");
        System.out.println("📊 **MONITORING STATUS: STOPPED** 🔴");
    }
    
    /**
     * Gets current monitoring status
     */
    public String getStatus() {
        if (running) {
            return "🟢 ACTIVE - Monitoring " + DURION_REPO + " every " + POLLING_INTERVAL_MINUTES + " minutes (SSL BYPASS MODE)";
        } else {
            return "🔴 STOPPED - Not monitoring";
        }
    }
    
    /**
     * Manual trigger for immediate processing
     */
    public void triggerImmediateProcessing() {
        if (!running) {
            System.out.println("⚠️ Monitoring service is not running. Start monitoring first.");
            return;
        }
        
        System.out.println("🚀 **MANUAL TRIGGER: Processing stories immediately...**");
        processStories();
    }
    
    /**
     * Creates frontend issue body content
     */
    private String createFrontendIssueBody(String storyTitle, String storyBody, String domain) {
        StringBuilder body = new StringBuilder();
        body.append("## Frontend Implementation for Story\n\n");
        body.append("**Original Story**: ").append(storyTitle).append("\n\n");
        body.append("**Domain**: ").append(domain).append("\n\n");
        body.append("### Story Description\n\n");
        body.append(storyBody).append("\n\n");
        body.append("### Frontend Requirements\n\n");
        body.append("- Implement Vue.js 3 components with TypeScript\n");
        body.append("- Use Quasar framework for UI components\n");
        body.append("- Integrate with Moqui Framework backend\n");
        body.append("- Ensure responsive design and accessibility\n\n");
        body.append("### Technical Stack\n\n");
        body.append("- Vue.js 3 with Composition API\n");
        body.append("- TypeScript 5.x\n");
        body.append("- Quasar v2.x\n");
        body.append("- Moqui Framework integration\n\n");
        body.append("---\n");
        body.append("*This issue was automatically created by the Durion Workspace Agent*");
        return body.toString();
    }

    /**
     * Creates backend issue body content
     */
    private String createBackendIssueBody(String storyTitle, String storyBody, String domain) {
        StringBuilder body = new StringBuilder();
        body.append("## Backend Implementation for Story\n\n");
        body.append("**Original Story**: ").append(storyTitle).append("\n\n");
        body.append("**Domain**: ").append(domain).append("\n\n");
        body.append("### Story Description\n\n");
        body.append(storyBody).append("\n\n");
        body.append("### Backend Requirements\n\n");
        body.append("- Implement Spring Boot microservices\n");
        body.append("- Create REST API endpoints\n");
        body.append("- Implement business logic and data access\n");
        body.append("- Ensure proper security and validation\n\n");
        body.append("### Technical Stack\n\n");
        body.append("- Spring Boot 3.2.6\n");
        body.append("- Java 21\n");
        body.append("- Spring Data JPA\n");
        body.append("- PostgreSQL/MySQL\n\n");
        body.append("---\n");
        body.append("*This issue was automatically created by the Durion Workspace Agent*");
        return body.toString();
    }

    /**
     * Creates story sequence coordination document
     */
    private void createStorySequenceDocument(List<GitHubApiClientSSLBypass.GitHubIssue> stories) throws IOException {
        Path sequenceFile = Paths.get(".github/orchestration/story-sequence.md");
        Files.createDirectories(sequenceFile.getParent());
        
        StringBuilder content = new StringBuilder();
        content.append("# Story Sequence Coordination\n\n");
        content.append("**Generated**: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n\n");
        content.append("## Active Stories\n\n");
        
        for (GitHubApiClientSSLBypass.GitHubIssue story : stories) {
            content.append("### Story #").append(story.getNumber()).append(": ").append(story.getTitle()).append("\n\n");
            content.append("**URL**: ").append(story.getUrl()).append("\n\n");
            content.append("**Domain**: ").append(extractDomain(story.getTitle(), story.getBody())).append("\n\n");
            content.append("**Status**: ").append(processedIssueNumbers.contains(story.getNumber()) ? "Processed" : "Pending").append("\n\n");
            content.append("---\n\n");
        }
        
        Files.write(sequenceFile, content.toString().getBytes());
    }

    /**
     * Creates frontend coordination document
     */
    private void createFrontendCoordinationDocument(List<GitHubApiClientSSLBypass.GitHubIssue> stories) throws IOException {
        Path frontendFile = Paths.get(".github/orchestration/frontend-coordination.md");
        Files.createDirectories(frontendFile.getParent());
        
        StringBuilder content = new StringBuilder();
        content.append("# Frontend Development Coordination\n\n");
        content.append("**Generated**: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n\n");
        content.append("## Frontend Implementation Status\n\n");
        content.append("**Repository**: ").append(FRONTEND_REPO).append("\n\n");
        content.append("**Technology Stack**:\n");
        content.append("- Vue.js 3 with Composition API\n");
        content.append("- TypeScript 5.x\n");
        content.append("- Quasar v2.x\n");
        content.append("- Moqui Framework integration\n\n");
        
        content.append("## Stories Ready for Frontend Development\n\n");
        for (GitHubApiClientSSLBypass.GitHubIssue story : stories) {
            if (processedIssueNumbers.contains(story.getNumber())) {
                content.append("- ✅ **Story #").append(story.getNumber()).append("**: ").append(story.getTitle()).append("\n");
            } else {
                content.append("- ⏳ **Story #").append(story.getNumber()).append("**: ").append(story.getTitle()).append(" (Pending)\n");
            }
        }
        
        Files.write(frontendFile, content.toString().getBytes());
    }

    /**
     * Creates backend coordination document
     */
    private void createBackendCoordinationDocument(List<GitHubApiClientSSLBypass.GitHubIssue> stories) throws IOException {
        Path backendFile = Paths.get(".github/orchestration/backend-coordination.md");
        Files.createDirectories(backendFile.getParent());
        
        StringBuilder content = new StringBuilder();
        content.append("# Backend Development Coordination\n\n");
        content.append("**Generated**: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n\n");
        content.append("## Backend Implementation Status\n\n");
        content.append("**Repository**: ").append(BACKEND_REPO).append("\n\n");
        content.append("**Technology Stack**:\n");
        content.append("- Spring Boot 3.2.6\n");
        content.append("- Java 21\n");
        content.append("- Spring Data JPA\n");
        content.append("- PostgreSQL/MySQL\n\n");
        
        content.append("## Stories Ready for Backend Development\n\n");
        for (GitHubApiClientSSLBypass.GitHubIssue story : stories) {
            if (processedIssueNumbers.contains(story.getNumber())) {
                content.append("- ✅ **Story #").append(story.getNumber()).append("**: ").append(story.getTitle()).append("\n");
            } else {
                content.append("- ⏳ **Story #").append(story.getNumber()).append("**: ").append(story.getTitle()).append(" (Pending)\n");
            }
        }
        
        Files.write(backendFile, content.toString().getBytes());
    }
    
    /**
     * Loads previously processed issue numbers from persistent storage
     */
    private void loadProcessedIssues() {
        try {
            Path processedFile = Paths.get(PROCESSED_ISSUES_FILE);
            if (Files.exists(processedFile)) {
                List<String> lines = Files.readAllLines(processedFile);
                for (String line : lines) {
                    try {
                        int issueNumber = Integer.parseInt(line.trim());
                        processedIssueNumbers.add(issueNumber);
                    } catch (NumberFormatException e) {
                        // Skip invalid lines
                    }
                }
                System.out.println("📂 Loaded " + processedIssueNumbers.size() + " previously processed issues");
            } else {
                System.out.println("📂 No previous processing history found (starting fresh)");
            }
        } catch (IOException e) {
            System.out.println("⚠️ Warning: Could not load processed issues history: " + e.getMessage());
        }
    }
    
    /**
     * Saves a processed issue number to persistent storage
     */
    private void saveProcessedIssue(int issueNumber) {
        try {
            Path processedFile = Paths.get(PROCESSED_ISSUES_FILE);
            Files.createDirectories(processedFile.getParent());
            
            String issueEntry = issueNumber + "\n";
            Files.write(processedFile, issueEntry.getBytes(), 
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                
            System.out.println("      💾 Saved issue #" + issueNumber + " to processing history");
        } catch (IOException e) {
            System.out.println("      ⚠️ Warning: Could not save processed issue: " + e.getMessage());
        }
    }
    
    /**
     * Gets list of all processed issue numbers (for debugging)
     */
    public Set<Integer> getProcessedIssues() {
        return new HashSet<>(processedIssueNumbers);
    }
    
    /**
     * Manually marks an issue as processed (useful for skipping issues)
     */
    public void markIssueAsProcessed(int issueNumber) {
        processedIssueNumbers.add(issueNumber);
        saveProcessedIssue(issueNumber);
        System.out.println("✅ Manually marked issue #" + issueNumber + " as processed");
    }
    
    /**
     * Clears processing history (use with caution!)
     */
    public void clearProcessingHistory() {
        try {
            Path processedFile = Paths.get(PROCESSED_ISSUES_FILE);
            Files.deleteIfExists(processedFile);
            processedIssueNumbers.clear();
            System.out.println("🗑️ Cleared all processing history");
        } catch (IOException e) {
            System.out.println("❌ Error clearing processing history: " + e.getMessage());
        }
    }
    
    /**
     * Main method to run the Production Story Monitor with SSL Bypass
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
            System.out.println("      java -cp \"target/classes\" ProductionStoryMonitorSSLBypass <your-github-token>");
            return;
        }
        
        ProductionStoryMonitorSSLBypass monitor = new ProductionStoryMonitorSSLBypass(githubToken);
        
        System.out.println("🎯 **PRODUCTION GITHUB STORY MONITOR (SSL BYPASS)**");
        System.out.println("==================================================");
        System.out.println("This version bypasses SSL certificate validation for development");
        System.out.println("⚠️ WARNING: Only use in development environments!");
        System.out.println();
        
        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println();
            System.out.println("🛑 Shutting down production story monitor...");
            monitor.stopMonitoring();
            System.out.println("✅ Production story monitor stopped gracefully");
        }));
        
        // Start monitoring
        monitor.startMonitoring();
        
        // Keep running and show status
        try {
            while (true) {
                Thread.sleep(30000);
                System.out.println("📊 Status: " + monitor.getStatus());
                System.out.println("📂 Processed Issues: " + monitor.getProcessedIssues().size() + " total");
            }
        } catch (InterruptedException e) {
            System.out.println("🛑 Monitor interrupted, shutting down...");
            monitor.stopMonitoring();
        }
    }
}