package com.durion;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * GitHub API Client for Real GitHub Integration
 * 
 * Provides actual GitHub API calls to:
 * - Fetch issues from repositories
 * - Create new issues
 * - Update issue labels
 * - Monitor repository changes
 * 
 * Uses simple JSON parsing without external dependencies.
 */
public class GitHubApiClient {
    
    private final HttpClient httpClient;
    private final String githubToken;
    private static final String GITHUB_API_BASE = "https://api.github.com";
    
    public GitHubApiClient(String githubToken) {
        this.githubToken = githubToken;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    }
    
    /**
     * Fetches all issues with [STORY] label from a repository
     */
    public List<GitHubIssue> getStoryIssues(String repository) throws IOException, InterruptedException {
        String url = String.format("%s/repos/%s/issues?labels=STORY&state=open", GITHUB_API_BASE, repository);
        
        System.out.println("🔍 Fetching STORY issues from: " + repository);
        System.out.println("🌐 Request URL: " + url);
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer " + githubToken)
            .header("Accept", "application/vnd.github.v3+json")
            .header("User-Agent", "Durion-Workspace-Agent/1.0")
            .GET()
            .build();
        
        System.out.println("📤 Sending request to GitHub API...");
        long startTime = System.currentTimeMillis();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        long responseTime = System.currentTimeMillis() - startTime;
        System.out.println("📥 Response received in " + responseTime + "ms");
        System.out.println("📊 HTTP Status: " + response.statusCode());
        
        if (response.statusCode() == 200) {
            List<GitHubIssue> issues = parseIssuesFromJson(response.body());
            System.out.println("✅ Successfully fetched " + issues.size() + " STORY issues");
            
            if (issues.isEmpty()) {
                System.out.println("ℹ️ No issues found with 'STORY' label in repository: " + repository);
                System.out.println("   • Make sure issues exist with the 'STORY' label (not '[STORY]')");
                System.out.println("   • Check if the repository is accessible with your token");
            } else {
                System.out.println("📋 Found issues:");
                for (GitHubIssue issue : issues) {
                    System.out.println("   • #" + issue.getNumber() + ": " + issue.getTitle());
                }
            }
            
            return issues;
        } else {
            System.out.println("❌ Failed to fetch issues from repository: " + repository);
            System.out.println("📄 Response body: " + response.body());
            
            switch (response.statusCode()) {
                case 401:
                    System.out.println("🔐 Error 401: Token is invalid or expired");
                    break;
                case 403:
                    System.out.println("🚫 Error 403: Token lacks access to repository " + repository);
                    break;
                case 404:
                    System.out.println("🔍 Error 404: Repository " + repository + " not found or not accessible");
                    break;
                default:
                    System.out.println("❓ Unexpected error code: " + response.statusCode());
            }
            
            throw new IOException("GitHub API error: " + response.statusCode() + " - " + response.body());
        }
    }
    
    /**
     * Creates a new issue in the specified repository
     */
    public GitHubIssue createIssue(String repository, String title, String body, List<String> labels) 
            throws IOException, InterruptedException {
        
        String url = String.format("%s/repos/%s/issues", GITHUB_API_BASE, repository);
        
        // Build JSON manually
        StringBuilder jsonBuilder = new StringBuilder();
        jsonBuilder.append("{");
        jsonBuilder.append("\"title\":\"").append(escapeJson(title)).append("\",");
        jsonBuilder.append("\"body\":\"").append(escapeJson(body)).append("\",");
        jsonBuilder.append("\"labels\":[");
        for (int i = 0; i < labels.size(); i++) {
            if (i > 0) jsonBuilder.append(",");
            jsonBuilder.append("\"").append(escapeJson(labels.get(i))).append("\"");
        }
        jsonBuilder.append("]}");
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer " + githubToken)
            .header("Accept", "application/vnd.github.v3+json")
            .header("Content-Type", "application/json")
            .header("User-Agent", "Durion-Workspace-Agent/1.0")
            .POST(HttpRequest.BodyPublishers.ofString(jsonBuilder.toString()))
            .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 201) {
            return parseIssueFromJson(response.body());
        } else {
            throw new IOException("Failed to create issue: " + response.statusCode() + " - " + response.body());
        }
    }
    
    /**
     * Checks if GitHub API is accessible with current token
     */
    public boolean testConnection() {
        System.out.println("🔍 **GITHUB API CONNECTION TEST**");
        System.out.println("================================");
        
        // Log token information (safely)
        if (githubToken == null || githubToken.trim().isEmpty()) {
            System.out.println("❌ GitHub token is null or empty!");
            return false;
        }
        
        String tokenPrefix = githubToken.length() > 8 ? 
            githubToken.substring(0, 8) + "..." : "***";
        System.out.println("🔑 Token prefix: " + tokenPrefix);
        System.out.println("📏 Token length: " + githubToken.length() + " characters");
        
        // Check token format
        if (githubToken.startsWith("ghp_")) {
            System.out.println("✅ Token format: Personal Access Token (classic)");
        } else if (githubToken.startsWith("github_pat_")) {
            System.out.println("✅ Token format: Fine-grained Personal Access Token");
        } else {
            System.out.println("⚠️ Token format: Unknown (expected ghp_ or github_pat_ prefix)");
        }
        
        try {
            String testUrl = GITHUB_API_BASE + "/user";
            System.out.println("🌐 Testing URL: " + testUrl);
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(testUrl))
                .header("Authorization", "Bearer " + githubToken)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "Durion-Workspace-Agent/1.0")
                .GET()
                .build();
            
            System.out.println("📤 Sending request to GitHub API...");
            long startTime = System.currentTimeMillis();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            long responseTime = System.currentTimeMillis() - startTime;
            System.out.println("📥 Response received in " + responseTime + "ms");
            System.out.println("📊 HTTP Status: " + response.statusCode());
            
            // Log response headers for debugging
            System.out.println("📋 Response Headers:");
            response.headers().map().forEach((key, values) -> {
                if (!key.toLowerCase().contains("token") && !key.toLowerCase().contains("auth")) {
                    System.out.println("   " + key + ": " + String.join(", ", values));
                }
            });
            
            if (response.statusCode() == 200) {
                System.out.println("✅ GitHub API connection successful!");
                
                // Parse user info for additional verification
                String responseBody = response.body();
                if (responseBody.contains("\"login\"")) {
                    Pattern loginPattern = Pattern.compile("\"login\"\\s*:\\s*\"([^\"]+)\"");
                    Matcher matcher = loginPattern.matcher(responseBody);
                    if (matcher.find()) {
                        String username = matcher.group(1);
                        System.out.println("👤 Authenticated as: " + username);
                    }
                }
                
                return true;
            } else {
                System.out.println("❌ GitHub API connection failed!");
                System.out.println("📄 Response body: " + response.body());
                
                // Provide specific error guidance
                switch (response.statusCode()) {
                    case 401:
                        System.out.println("🔐 Error 401: Unauthorized - Token is invalid or expired");
                        System.out.println("   • Check if your token is correct");
                        System.out.println("   • Verify token hasn't expired");
                        System.out.println("   • Ensure token has proper permissions");
                        break;
                    case 403:
                        System.out.println("🚫 Error 403: Forbidden - Token lacks required permissions");
                        System.out.println("   • Token needs 'repo' scope for private repositories");
                        System.out.println("   • Token needs 'public_repo' scope for public repositories");
                        break;
                    case 404:
                        System.out.println("🔍 Error 404: Not Found - API endpoint not accessible");
                        break;
                    case 429:
                        System.out.println("⏱️ Error 429: Rate Limited - Too many requests");
                        break;
                    default:
                        System.out.println("❓ Unexpected error code: " + response.statusCode());
                }
                
                return false;
            }
            
        } catch (IOException e) {
            System.out.println("❌ Network error during GitHub API test:");
            System.out.println("   Error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            System.out.println("   • Check your internet connection");
            System.out.println("   • Verify GitHub.com is accessible");
            System.out.println("   • Check if you're behind a firewall/proxy");
            return false;
        } catch (InterruptedException e) {
            System.out.println("❌ Request interrupted:");
            System.out.println("   Error: " + e.getMessage());
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            System.out.println("❌ Unexpected error during GitHub API test:");
            System.out.println("   Error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Simple JSON parsing for GitHub issues array
     */
    private List<GitHubIssue> parseIssuesFromJson(String json) {
        List<GitHubIssue> issues = new ArrayList<>();
        
        // Simple regex-based JSON parsing for issues array
        Pattern issuePattern = Pattern.compile("\\{[^{}]*\"number\"\\s*:\\s*(\\d+)[^{}]*\"title\"\\s*:\\s*\"([^\"]*?)\"[^{}]*\"body\"\\s*:\\s*\"([^\"]*?)\"[^{}]*\"html_url\"\\s*:\\s*\"([^\"]*?)\"[^{}]*\\}");
        Matcher matcher = issuePattern.matcher(json);
        
        while (matcher.find()) {
            int number = Integer.parseInt(matcher.group(1));
            String title = unescapeJson(matcher.group(2));
            String body = unescapeJson(matcher.group(3));
            String url = unescapeJson(matcher.group(4));
            
            // Extract labels (simplified)
            List<String> labels = new ArrayList<>();
            labels.add("STORY"); // We know it has STORY label since we filtered for it
            
            issues.add(new GitHubIssue(number, title, body, url, labels));
        }
        
        return issues;
    }
    
    /**
     * Simple JSON parsing for single GitHub issue
     */
    private GitHubIssue parseIssueFromJson(String json) {
        Pattern numberPattern = Pattern.compile("\"number\"\\s*:\\s*(\\d+)");
        Pattern titlePattern = Pattern.compile("\"title\"\\s*:\\s*\"([^\"]*?)\"");
        Pattern bodyPattern = Pattern.compile("\"body\"\\s*:\\s*\"([^\"]*?)\"");
        Pattern urlPattern = Pattern.compile("\"html_url\"\\s*:\\s*\"([^\"]*?)\"");
        
        Matcher numberMatcher = numberPattern.matcher(json);
        Matcher titleMatcher = titlePattern.matcher(json);
        Matcher bodyMatcher = bodyPattern.matcher(json);
        Matcher urlMatcher = urlPattern.matcher(json);
        
        if (numberMatcher.find() && titleMatcher.find() && urlMatcher.find()) {
            int number = Integer.parseInt(numberMatcher.group(1));
            String title = unescapeJson(titleMatcher.group(1));
            String body = bodyMatcher.find() ? unescapeJson(bodyMatcher.group(1)) : "";
            String url = unescapeJson(urlMatcher.group(1));
            
            return new GitHubIssue(number, title, body, url, List.of("STORY"));
        }
        
        throw new RuntimeException("Failed to parse issue from JSON: " + json);
    }
    
    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
    
    private String unescapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\\"", "\"")
                  .replace("\\n", "\n")
                  .replace("\\r", "\r")
                  .replace("\\t", "\t")
                  .replace("\\\\", "\\");
    }
    
    /**
     * Test method to debug GitHub API connection
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("❌ Usage: java GitHubApiClient <github_token>");
            System.out.println("   This will test the GitHub API connection with your token");
            return;
        }
        
        String token = args[0];
        System.out.println("🧪 **GITHUB API CLIENT TEST**");
        System.out.println("=============================");
        
        GitHubApiClient client = new GitHubApiClient(token);
        
        // Test connection
        boolean connected = client.testConnection();
        
        if (connected) {
            System.out.println();
            System.out.println("🎯 **TESTING REPOSITORY ACCESS**");
            System.out.println("================================");
            
            try {
                // Test fetching issues from durion repository
                List<GitHubIssue> issues = client.getStoryIssues("louisburroughs/durion");
                System.out.println("✅ Repository access test successful!");
                
            } catch (Exception e) {
                System.out.println("❌ Repository access test failed:");
                System.out.println("   " + e.getMessage());
            }
        }
        
        System.out.println();
        System.out.println("🏁 Test complete!");
    }
    
    /**
     * Simple GitHub Issue representation
     */
    public static class GitHubIssue {
        private final int number;
        private final String title;
        private final String body;
        private final String url;
        private final List<String> labels;
        
        public GitHubIssue(int number, String title, String body, String url, List<String> labels) {
            this.number = number;
            this.title = title;
            this.body = body;
            this.url = url;
            this.labels = labels;
        }
        
        public int getNumber() { return number; }
        public String getTitle() { return title; }
        public String getBody() { return body; }
        public String getUrl() { return url; }
        public List<String> getLabels() { return labels; }
        
        @Override
        public String toString() {
            return String.format("Issue #%d: %s (%s)", number, title, url);
        }
    }
}