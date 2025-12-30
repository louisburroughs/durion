package com.durion;
import java.util.*;
import java.util.concurrent.*;

import com.durion.agents.*;
import com.durion.core.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Production GitHub Story Monitor - Real GitHub Integration
 * 
 * This is the production version that:
 * - Actually connects to GitHub API
 * - Writes real coordination files
 * - Creates real GitHub issues
 * - Monitors real repositories
 */
public class ProductionStoryMonitor {
    
    private final StoryOrchestrationAgent orchestrationAgent;
    private final GitHubIssueCreationAgent issueCreationAgent;
    private final GitHubApiClient githubClient;
    private final ScheduledExecutorService scheduler;
    private final AgentConfiguration config;
    private volatile boolean running = false;
    private Set<Integer> processedIssueNumbers = new HashSet<>();
    
    // Configuration
    private static final int POLLING_INTERVAL_MINUTES = 5;
    private static final String DURION_REPO = "louisburroughs/durion";
    private static final String FRONTEND_REPO = "louisburroughs/durion-moqui-frontend";
    private static final String BACKEND_REPO = "louisburroughs/durion-positivity-backend";
    
    public ProductionStoryMonitor(String githubToken) {
        this.githubClient = new GitHubApiClient(githubToken);
        this.orchestrationAgent = new StoryOrchestrationAgent();
        this.issueCreationAgent = new GitHubIssueCreationAgent();
        this.scheduler = Executors.newScheduledThreadPool(2);
        this.config = createConfiguration();
        
        // Initialize agents
        this.orchestrationAgent.initialize(config);
        this.issueCreationAgent.initialize(config);
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
        System.out.println("🔗 Testing GitHub API connection...");
        if (!githubClient.testConnection()) {
            System.out.println("❌ GitHub API connection failed!");
            System.out.println("   Please check your GitHub token and network connection.");
            System.out.println("   Set GITHUB_TOKEN environment variable or pass token as argument.");
            return;
        }
        System.out.println("✅ GitHub API connection successful");
        
        running = true;
        System.out.println();
        System.out.println("🚀 **STARTING PRODUCTION STORY PROCESSING**");
        System.out.println("==========================================");
        System.out.println("📋 Monitoring: " + DURION_REPO);
        System.out.println("⏱️ Polling Interval: " + POLLING_INTERVAL_MINUTES + " minutes");
        System.out.println("🎯 Looking for: [STORY] labeled issues");
        System.out.println("📁 Writing files to: .github/orchestration/");
        System.out.println("🔗 Creating issues in: " + FRONTEND_REPO + ", " + BACKEND_REPO);
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
            List<GitHubApiClient.GitHubIssue> storyIssues = githubClient.getStoryIssues(DURION_REPO);
            System.out.println("   📋 Found " + storyIssues.size() + " [STORY] issues in repository");
            
            // Step 2: Check for new stories
            List<GitHubApiClient.GitHubIssue> newStories = storyIssues.stream()
                .filter(issue -> !processedIssueNumbers.contains(issue.getNumber()))
                .toList();
            
            if (newStories.isEmpty()) {
                System.out.println("   ℹ️ No new stories detected");
                
                // Still update coordination documents with current stories
                updateCoordinationDocuments(storyIssues);
                return;
            }
            
            System.out.println("   🆕 " + newStories.size() + " new stories detected! Processing...");
            for (GitHubApiClient.GitHubIssue story : newStories) {
                System.out.println("      • #" + story.getNumber() + ": " + story.getTitle());
            }
            
            // Step 3: Process new stories
            for (GitHubApiClient.GitHubIssue story : newStories) {
                processNewStory(story);
                processedIssueNumbers.add(story.getNumber());
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
    private void processNewStory(GitHubApiClient.GitHubIssue story) {
        try {
            System.out.println("   🔄 Processing story #" + story.getNumber() + ": " + story.getTitle());
            
            // Extract domain from story (simple heuristic)
            String domain = extractDomain(story.getTitle(), story.getBody());
            
            // Create implementation issues using the GitHubIssueCreationAgent
            GitHubIssueCreationAgent.IssueCreationResult result = 
                issueCreationAgent.createIssuesFromStory(story.getTitle(), story.getBody(), domain);
            
            if (result.success()) {
                System.out.println("      ✅ Created implementation issues:");
                if (result.frontendIssueUrl() != null) {
                    System.out.println("         🎨 Frontend: " + result.frontendIssueUrl());
                }
                if (result.backendIssueUrl() != null) {
                    System.out.println("         ⚙️ Backend: " + result.backendIssueUrl());
                }
            } else {
                System.out.println("      ⚠️ Issue creation failed: " + result.message());
            }
            
        } catch (Exception e) {
            System.out.println("      ❌ Error processing story: " + e.getMessage());
        }
    }
    
    /**
     * Updates coordination documents with current story state
     */
    private void updateCoordinationDocuments(List<GitHubApiClient.GitHubIssue> stories) {
        try {
            System.out.println("   📝 Updating coordination documents...");
            
            // Use the StoryOrchestrationAgent to generate documents
            CompletableFuture<AgentResult> sequenceDoc = orchestrationAgent.execute("GENERATE_SEQUENCE_DOCUMENT", new HashMap<>());
            AgentResult sequenceResult = sequenceDoc.get(30, TimeUnit.SECONDS);
            
            if (sequenceResult.isSuccess()) {
                System.out.println("      ✅ Updated story-sequence.md");
            } else {
                System.out.println("      ⚠️ Failed to update story-sequence.md: " + sequenceResult.getMessage());
            }
            
            CompletableFuture<AgentResult> frontendDoc = orchestrationAgent.execute("GENERATE_FRONTEND_COORDINATION", new HashMap<>());
            AgentResult frontendResult = frontendDoc.get(30, TimeUnit.SECONDS);
            
            if (frontendResult.isSuccess()) {
                System.out.println("      ✅ Updated frontend-coordination.md");
            } else {
                System.out.println("      ⚠️ Failed to update frontend-coordination.md: " + frontendResult.getMessage());
            }
            
            CompletableFuture<AgentResult> backendDoc = orchestrationAgent.execute("GENERATE_BACKEND_COORDINATION", new HashMap<>());
            AgentResult backendResult = backendDoc.get(30, TimeUnit.SECONDS);
            
            if (backendResult.isSuccess()) {
                System.out.println("      ✅ Updated backend-coordination.md");
            } else {
                System.out.println("      ⚠️ Failed to update backend-coordination.md: " + backendResult.getMessage());
            }
            
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
            return "🟢 ACTIVE - Monitoring " + DURION_REPO + " every " + POLLING_INTERVAL_MINUTES + " minutes (PRODUCTION MODE)";
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
    
    private AgentConfiguration createConfiguration() {
        Properties props = new Properties();
        Map<String, Object> settings = new HashMap<>();
        settings.put("github.repository", DURION_REPO);
        settings.put("github.frontend.repository", FRONTEND_REPO);
        settings.put("github.backend.repository", BACKEND_REPO);
        settings.put("maxConcurrentUsers", 100);
        settings.put("responseTimeoutSeconds", 30);
        
        return new AgentConfiguration("production-story-monitor", props, settings);
    }
    
    /**
     * Main method to run the Production Story Monitor
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
            System.out.println("      java -cp \"target/classes\" ProductionStoryMonitor <your-github-token>");
            System.out.println();
            System.out.println("   To create a GitHub token:");
            System.out.println("   1. Go to GitHub Settings > Developer settings > Personal access tokens");
            System.out.println("   2. Generate new token with 'repo' and 'issues' permissions");
            System.out.println("   3. Copy the token and use it here");
            return;
        }
        
        ProductionStoryMonitor monitor = new ProductionStoryMonitor(githubToken);
        
        System.out.println("🎯 **PRODUCTION GITHUB STORY MONITOR**");
        System.out.println("=====================================");
        System.out.println("This is the PRODUCTION version with real GitHub API integration");
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
            }
        } catch (InterruptedException e) {
            System.out.println("🛑 Monitor interrupted, shutting down...");
            monitor.stopMonitoring();
        }
    }
}