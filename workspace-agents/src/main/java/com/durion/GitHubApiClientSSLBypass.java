package com.durion;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;
import java.security.NoSuchAlgorithmException;
import java.security.KeyManagementException;

/**
 * GitHub API Client with SSL Certificate Bypass
 * 
 * This version bypasses SSL certificate validation for development environments
 * where corporate firewalls or proxy servers interfere with certificate chains.
 * 
 * ⚠️ WARNING: Only use this in development environments, not production!
 */
public class GitHubApiClientSSLBypass {
    
    private final HttpClient httpClient;
    private final String githubToken;
    private static final String GITHUB_API_BASE = "https://api.github.com";
    
    public GitHubApiClientSSLBypass(String githubToken) {
        this.githubToken = githubToken;
        this.httpClient = createSSLBypassHttpClient();
    }
    
    /**
     * Creates an HttpClient that bypasses SSL certificate validation
     */
    private HttpClient createSSLBypassHttpClient() {
        try {
            // Create a trust manager that accepts all certificates
            TrustManager[] trustAllCerts = new TrustManager[] {
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return null; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) { }
                    public void checkServerTrusted(X509Certificate[] certs, String authType) { }
                }
            };
            
            // Install the all-trusting trust manager
            SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            
            System.out.println("⚠️ SSL Certificate validation bypassed for development");
            
            return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .sslContext(sslContext)
                .build();
                
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            System.out.println("❌ Failed to create SSL bypass client: " + e.getMessage());
            System.out.println("   Falling back to default HTTP client");
            
            return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        }
    }
    
    /**
     * Fetches all story issues from a repository using correct search patterns with pagination
     */
    public List<GitHubIssue> getStoryIssues(String repository) throws IOException, InterruptedException {
        System.out.println("🔍 Searching for story issues in: " + repository);
        
        // Try the two correct search patterns
        String[] searchQueries = {
            String.format("repo:%s type:issue state:open label:\"type:story\"", repository),
            String.format("repo:%s type:issue state:open \"[STORY]\" in:title", repository)
        };
        
        for (String searchQuery : searchQueries) {
            System.out.println("🔍 Trying search: " + searchQuery);
            
            List<GitHubIssue> allResults = getAllPaginatedResults(searchQuery);
            
            if (!allResults.isEmpty()) {
                System.out.println("✅ Found " + allResults.size() + " story issues with this pattern");
                System.out.println("📋 Story issues found:");
                for (GitHubIssue issue : allResults) {
                    System.out.println("   • #" + issue.getNumber() + ": " + issue.getTitle());
                }
                return allResults;
            } else {
                System.out.println("ℹ️ No results with this pattern, trying next...");
            }
        }
        
        System.out.println("ℹ️ No story issues found with either search pattern");
        System.out.println("   • Make sure issues have label 'type:story' OR");
        System.out.println("   • Make sure issue titles contain '[STORY]'");
        
        return new ArrayList<>();
    }
    
    /**
     * Fetches all paginated results for a search query
     */
    private List<GitHubIssue> getAllPaginatedResults(String searchQuery) throws IOException, InterruptedException {
        List<GitHubIssue> allResults = new ArrayList<>();
        int page = 1;
        int perPage = 100; // GitHub allows up to 100 per page
        boolean hasMorePages = true;
        
        while (hasMorePages) {
            System.out.println("📄 Fetching page " + page + " (up to " + perPage + " items per page)...");
            
            String encodedQuery = java.net.URLEncoder.encode(searchQuery, "UTF-8");
            String url = String.format("%s/search/issues?q=%s&page=%d&per_page=%d", 
                GITHUB_API_BASE, encodedQuery, page, perPage);
            System.out.println("🌐 Request URL: " + url);
            
            List<GitHubIssue> pageResults = searchIssuesWithQuery(url);
            
            if (pageResults.isEmpty()) {
                System.out.println("📄 Page " + page + " returned 0 results - end of pagination");
                hasMorePages = false;
            } else {
                System.out.println("📄 Page " + page + " returned " + pageResults.size() + " results");
                allResults.addAll(pageResults);
                
                // If we got fewer results than per_page, we've reached the end
                if (pageResults.size() < perPage) {
                    System.out.println("📄 Page " + page + " returned fewer than " + perPage + " results - end of pagination");
                    hasMorePages = false;
                } else {
                    page++;
                    
                    // Add a small delay between requests to be respectful to GitHub API
                    Thread.sleep(100);
                }
            }
        }
        
        System.out.println("📊 Total results across all pages: " + allResults.size());
        return allResults;
    }
    
    /**
     * Searches issues with a specific query URL
     */
    private List<GitHubIssue> searchIssuesWithQuery(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer " + githubToken)
            .header("Accept", "application/vnd.github.v3+json")
            .header("User-Agent", "Durion-Workspace-Agent/1.0")
            .GET()
            .build();
        
        System.out.println("📤 Sending search request to GitHub API...");
        long startTime = System.currentTimeMillis();
        
        HttpResponse<String> response = sendRequestWithRateLimit(request);
        
        long responseTime = System.currentTimeMillis() - startTime;
        System.out.println("📥 Response received in " + responseTime + "ms");
        System.out.println("📊 HTTP Status: " + response.statusCode());
        
        if (response.statusCode() == 200) {
            String responseBody = response.body();
            System.out.println("📄 Response body length: " + responseBody.length() + " characters");
            
            // Log first 500 characters of response for debugging
            String preview = responseBody.length() > 500 ? 
                responseBody.substring(0, 500) + "..." : responseBody;
            System.out.println("📄 Response preview: " + preview);
            
            return parseSearchResultsFromJson(responseBody);
        } else if (response.statusCode() == 422) {
            System.out.println("⚠️ Search query validation failed (422)");
            return new ArrayList<>();
        } else {
            System.out.println("❌ Search failed with status: " + response.statusCode());
            System.out.println("📄 Response: " + response.body());
            return new ArrayList<>();
        }
    }
    
    /**
     * Parses GitHub search results JSON
     */
    private List<GitHubIssue> parseSearchResultsFromJson(String json) {
        List<GitHubIssue> issues = new ArrayList<>();
        
        System.out.println("🔍 Parsing JSON response...");
        
        // Find the total_count first
        Pattern totalCountPattern = Pattern.compile("\"total_count\"\\s*:\\s*(\\d+)");
        Matcher totalCountMatcher = totalCountPattern.matcher(json);
        if (totalCountMatcher.find()) {
            String totalCount = totalCountMatcher.group(1);
            System.out.println("📊 GitHub reports total_count: " + totalCount + " issues");
        }
        
        // Find the start of the items array
        int itemsStart = json.indexOf("\"items\":");
        if (itemsStart == -1) {
            System.out.println("❌ Could not find 'items' field in JSON response");
            return issues;
        }
        
        // Find the opening bracket of the items array
        int arrayStart = json.indexOf("[", itemsStart);
        if (arrayStart == -1) {
            System.out.println("❌ Could not find opening bracket of items array");
            return issues;
        }
        
        // Find the matching closing bracket by counting brackets
        int bracketCount = 0;
        int arrayEnd = -1;
        for (int i = arrayStart; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '[') {
                bracketCount++;
            } else if (c == ']') {
                bracketCount--;
                if (bracketCount == 0) {
                    arrayEnd = i;
                    break;
                }
            }
        }
        
        if (arrayEnd == -1) {
            System.out.println("❌ Could not find closing bracket of items array");
            return issues;
        }
        
        // Extract the items array content (without the brackets)
        String itemsArray = json.substring(arrayStart + 1, arrayEnd);
        System.out.println("✅ Found 'items' array, length: " + itemsArray.length() + " characters");
        
        // Check if items array is empty
        if (itemsArray.trim().isEmpty()) {
            System.out.println("ℹ️ Items array is empty - no issues found");
            return issues;
        }
        
        // Parse individual issue objects by finding complete JSON objects
        List<String> issueJsonObjects = extractJsonObjects(itemsArray);
        System.out.println("📊 Extracted " + issueJsonObjects.size() + " complete JSON objects");
        
        for (int i = 0; i < issueJsonObjects.size(); i++) {
            String issueJson = issueJsonObjects.get(i);
            System.out.println("🔍 Processing issue object " + (i + 1) + "...");
            
            try {
                GitHubIssue issue = parseIssueFromJsonFragment(issueJson);
                if (issue != null) {
                    issues.add(issue);
                    System.out.println("   ✅ Successfully parsed issue #" + issue.getNumber() + ": " + issue.getTitle());
                } else {
                    System.out.println("   ⚠️ Failed to parse issue object " + (i + 1));
                }
            } catch (Exception e) {
                System.out.println("   ❌ Exception parsing issue object " + (i + 1) + ": " + e.getMessage());
            }
        }
        
        System.out.println("📋 Final result: parsed " + issues.size() + " issues successfully");
        return issues;
    }
    
    /**
     * Extracts individual JSON objects from a JSON array content
     */
    private List<String> extractJsonObjects(String arrayContent) {
        List<String> objects = new ArrayList<>();
        
        int braceCount = 0;
        int objectStart = -1;
        boolean inString = false;
        boolean escaped = false;
        
        for (int i = 0; i < arrayContent.length(); i++) {
            char c = arrayContent.charAt(i);
            
            if (escaped) {
                escaped = false;
                continue;
            }
            
            if (c == '\\') {
                escaped = true;
                continue;
            }
            
            if (c == '"') {
                inString = !inString;
                continue;
            }
            
            if (inString) {
                continue;
            }
            
            if (c == '{') {
                if (braceCount == 0) {
                    objectStart = i;
                }
                braceCount++;
            } else if (c == '}') {
                braceCount--;
                if (braceCount == 0 && objectStart != -1) {
                    String jsonObject = arrayContent.substring(objectStart, i + 1);
                    objects.add(jsonObject);
                    objectStart = -1;
                }
            }
        }
        
        return objects;
    }
    
    /**
     * Checks GitHub API rate limit status and waits if necessary
     */
    public void checkRateLimitAndWait() throws IOException, InterruptedException {
        System.out.println("🔍 Checking GitHub API rate limit status...");
        
        String url = GITHUB_API_BASE + "/rate_limit";
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer " + githubToken)
            .header("Accept", "application/vnd.github.v3+json")
            .header("User-Agent", "Durion-Workspace-Agent/1.0")
            .GET()
            .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 200) {
            String responseBody = response.body();
            
            // Parse rate limit info
            Pattern coreRemainingPattern = Pattern.compile("\"core\"\\s*:\\s*\\{[^}]*\"remaining\"\\s*:\\s*(\\d+)");
            Pattern coreResetPattern = Pattern.compile("\"core\"\\s*:\\s*\\{[^}]*\"reset\"\\s*:\\s*(\\d+)");
            
            Matcher remainingMatcher = coreRemainingPattern.matcher(responseBody);
            Matcher resetMatcher = coreResetPattern.matcher(responseBody);
            
            if (remainingMatcher.find() && resetMatcher.find()) {
                int remaining = Integer.parseInt(remainingMatcher.group(1));
                long resetTime = Long.parseLong(resetMatcher.group(1));
                long currentTime = System.currentTimeMillis() / 1000;
                long waitTime = resetTime - currentTime;
                
                System.out.println("📊 Rate limit status:");
                System.out.println("   • Remaining requests: " + remaining);
                System.out.println("   • Reset time: " + new java.util.Date(resetTime * 1000));
                
                if (remaining < 10) {
                    System.out.println("⚠️ Rate limit low (" + remaining + " remaining)");
                    if (waitTime > 0) {
                        System.out.println("⏳ Waiting " + waitTime + " seconds for rate limit reset...");
                        Thread.sleep(waitTime * 1000 + 5000); // Add 5 seconds buffer
                        System.out.println("✅ Rate limit should be reset now");
                    }
                } else if (remaining < 100) {
                    System.out.println("⚠️ Rate limit getting low (" + remaining + " remaining) - adding delay");
                    Thread.sleep(2000); // 2 second delay when getting low
                } else {
                    System.out.println("✅ Rate limit OK (" + remaining + " remaining)");
                }
            } else {
                System.out.println("⚠️ Could not parse rate limit information");
            }
        } else {
            System.out.println("⚠️ Could not check rate limit (status: " + response.statusCode() + ")");
        }
    }
    
    /**
     * Checks GitHub API rate limit status using response headers and waits if necessary
     */
    public void checkRateLimitFromHeaders(HttpResponse<String> lastResponse) throws InterruptedException {
        if (lastResponse == null) {
            // Fallback to API endpoint if no response available
            try {
                checkRateLimitAndWait();
            } catch (IOException e) {
                System.out.println("⚠️ Could not check rate limit: " + e.getMessage());
            }
            return;
        }
        
        System.out.println("🔍 Checking rate limit from response headers...");
        
        // Extract rate limit headers
        String limitHeader = getHeader(lastResponse, "x-ratelimit-limit");
        String remainingHeader = getHeader(lastResponse, "x-ratelimit-remaining");
        String usedHeader = getHeader(lastResponse, "x-ratelimit-used");
        String resetHeader = getHeader(lastResponse, "x-ratelimit-reset");
        String resourceHeader = getHeader(lastResponse, "x-ratelimit-resource");
        
        if (remainingHeader != null && resetHeader != null) {
            try {
                int remaining = Integer.parseInt(remainingHeader);
                long resetTime = Long.parseLong(resetHeader);
                long currentTime = System.currentTimeMillis() / 1000;
                long waitTime = resetTime - currentTime;
                
                System.out.println("📊 Rate limit status (from headers):");
                if (limitHeader != null) {
                    System.out.println("   • Limit: " + limitHeader + " requests per hour");
                }
                System.out.println("   • Remaining: " + remaining + " requests");
                if (usedHeader != null) {
                    System.out.println("   • Used: " + usedHeader + " requests");
                }
                System.out.println("   • Reset time: " + new java.util.Date(resetTime * 1000));
                if (resourceHeader != null) {
                    System.out.println("   • Resource: " + resourceHeader);
                }
                
                if (remaining < 5) {
                    System.out.println("🚨 Rate limit critical (" + remaining + " remaining)");
                    if (waitTime > 0) {
                        System.out.println("⏳ Waiting " + waitTime + " seconds for rate limit reset...");
                        Thread.sleep(waitTime * 1000 + 5000); // Add 5 seconds buffer
                        System.out.println("✅ Rate limit should be reset now");
                    }
                } else if (remaining < 20) {
                    System.out.println("⚠️ Rate limit low (" + remaining + " remaining)");
                    // Calculate smart delay based on remaining requests and reset time
                    long smartDelay = Math.min(waitTime * 1000 / remaining, 30000); // Max 30 seconds
                    if (smartDelay > 1000) {
                        System.out.println("⏳ Adding smart delay of " + (smartDelay / 1000) + " seconds...");
                        Thread.sleep(smartDelay);
                    }
                } else if (remaining < 100) {
                    System.out.println("⚠️ Rate limit getting low (" + remaining + " remaining) - adding small delay");
                    Thread.sleep(2000); // 2 second delay when getting low
                } else {
                    System.out.println("✅ Rate limit OK (" + remaining + " remaining)");
                }
                
            } catch (NumberFormatException e) {
                System.out.println("⚠️ Could not parse rate limit headers");
                // Fallback to small delay
                Thread.sleep(1000);
            }
        } else {
            System.out.println("⚠️ Rate limit headers not found in response");
            // Fallback to small delay
            Thread.sleep(1000);
        }
    }
    
    /**
     * Helper method to get header value (case-insensitive)
     */
    private String getHeader(HttpResponse<String> response, String headerName) {
        return response.headers().firstValue(headerName).orElse(null);
    }
    
    /**
     * Enhanced method that returns both response and handles rate limiting
     */
    private HttpResponse<String> sendRequestWithRateLimit(HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        // Check rate limit from this response for next request
        checkRateLimitFromHeaders(response);
        
        return response;
    }
    
    /**
     * Creates a new issue in the specified repository
     */
    public GitHubIssue createIssue(String repository, String title, String body, List<String> labels) 
            throws IOException, InterruptedException {
        
        // Check rate limit before making the request
        checkRateLimitAndWait();
        
        String url = String.format("%s/repos/%s/issues", GITHUB_API_BASE, repository);
        
        System.out.println("🎯 Creating issue in repository: " + repository);
        System.out.println("📝 Title: " + title);
        
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
        
        System.out.println("📤 Sending issue creation request...");
        HttpResponse<String> response = sendRequestWithRateLimit(request);
        
        System.out.println("📊 HTTP Status: " + response.statusCode());
        
        if (response.statusCode() == 201) {
            GitHubIssue issue = parseIssueFromJson(response.body());
            System.out.println("✅ Issue created successfully: " + issue.getUrl());
            return issue;
        } else if (response.statusCode() == 403 && response.body().contains("secondary rate limit")) {
            System.out.println("⚠️ Hit secondary rate limit - waiting 60 seconds before retry...");
            Thread.sleep(60000); // Wait 60 seconds for secondary rate limit
            
            // Retry once after waiting
            System.out.println("🔄 Retrying issue creation after secondary rate limit wait...");
            HttpResponse<String> retryResponse = sendRequestWithRateLimit(request);
            
            if (retryResponse.statusCode() == 201) {
                GitHubIssue issue = parseIssueFromJson(retryResponse.body());
                System.out.println("✅ Issue created successfully: " + issue.getUrl() + " (after retry)");
                return issue;
            } else {
                System.out.println("❌ Failed to create issue even after retry");
                System.out.println("📄 Response: " + retryResponse.body());
                throw new IOException("Failed to create issue after retry: " + retryResponse.statusCode() + " - " + retryResponse.body());
            }
        } else {
            System.out.println("❌ Failed to create issue");
            System.out.println("📄 Response: " + response.body());
            throw new IOException("Failed to create issue: " + response.statusCode() + " - " + response.body());
        }
    }
    
    /**
     * Adds a comment to an existing issue
     */
    public void addCommentToIssue(String repository, int issueNumber, String comment) 
            throws IOException, InterruptedException {
        
        // Check rate limit before making the request
        checkRateLimitAndWait();
        
        String url = String.format("%s/repos/%s/issues/%d/comments", GITHUB_API_BASE, repository, issueNumber);
        
        System.out.println("💬 Adding comment to issue #" + issueNumber + " in " + repository);
        
        // Build JSON manually
        StringBuilder jsonBuilder = new StringBuilder();
        jsonBuilder.append("{");
        jsonBuilder.append("\"body\":\"").append(escapeJson(comment)).append("\"");
        jsonBuilder.append("}");
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer " + githubToken)
            .header("Accept", "application/vnd.github.v3+json")
            .header("Content-Type", "application/json")
            .header("User-Agent", "Durion-Workspace-Agent/1.0")
            .POST(HttpRequest.BodyPublishers.ofString(jsonBuilder.toString()))
            .build();
        
        System.out.println("📤 Sending comment request...");
        HttpResponse<String> response = sendRequestWithRateLimit(request);
        
        System.out.println("📊 HTTP Status: " + response.statusCode());
        
        if (response.statusCode() == 201) {
            System.out.println("✅ Comment added successfully to issue #" + issueNumber);
        } else if (response.statusCode() == 403 && response.body().contains("secondary rate limit")) {
            System.out.println("⚠️ Hit secondary rate limit - waiting 60 seconds before retry...");
            Thread.sleep(60000); // Wait 60 seconds for secondary rate limit
            
            // Retry once after waiting
            System.out.println("🔄 Retrying comment after secondary rate limit wait...");
            HttpResponse<String> retryResponse = sendRequestWithRateLimit(request);
            
            if (retryResponse.statusCode() == 201) {
                System.out.println("✅ Comment added successfully to issue #" + issueNumber + " (after retry)");
            } else {
                System.out.println("❌ Failed to add comment to issue #" + issueNumber + " even after retry");
                System.out.println("📄 Response: " + retryResponse.body());
                throw new IOException("Failed to add comment after retry: " + retryResponse.statusCode() + " - " + retryResponse.body());
            }
        } else {
            System.out.println("❌ Failed to add comment to issue #" + issueNumber);
            System.out.println("📄 Response: " + response.body());
            throw new IOException("Failed to add comment: " + response.statusCode() + " - " + response.body());
        }
    }
    
    /**
     * Checks if GitHub API is accessible with current token
     */
    public boolean testConnection() {
        System.out.println("🔍 **GITHUB API CONNECTION TEST (SSL BYPASS)**");
        System.out.println("==============================================");
        
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
                return false;
            }
            
        } catch (Exception e) {
            System.out.println("❌ Error during GitHub API test:");
            System.out.println("   Error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return false;
        }
    }
    
    // JSON parsing methods (enhanced to extract labels)
    private List<GitHubIssue> parseIssuesFromJson(String json) {
        List<GitHubIssue> issues = new ArrayList<>();
        
        // Split the JSON array into individual issue objects
        String[] issueObjects = json.split("\\},\\s*\\{");
        
        for (String issueJson : issueObjects) {
            // Clean up the JSON fragment
            if (!issueJson.startsWith("{")) issueJson = "{" + issueJson;
            if (!issueJson.endsWith("}")) issueJson = issueJson + "}";
            
            try {
                GitHubIssue issue = parseIssueFromJsonFragment(issueJson);
                if (issue != null) {
                    issues.add(issue);
                }
            } catch (Exception e) {
                // Skip malformed issue objects
                System.out.println("⚠️ Skipping malformed issue JSON fragment");
            }
        }
        
        return issues;
    }
    
    private GitHubIssue parseIssueFromJsonFragment(String json) {
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
            
            // Extract labels from the JSON
            List<String> labels = extractLabelsFromJson(json);
            
            return new GitHubIssue(number, title, body, url, labels);
        }
        
        return null;
    }
    
    private List<String> extractLabelsFromJson(String json) {
        List<String> labels = new ArrayList<>();
        
        // Find the labels array in the JSON
        Pattern labelsPattern = Pattern.compile("\"labels\"\\s*:\\s*\\[(.*?)\\]");
        Matcher labelsMatcher = labelsPattern.matcher(json);
        
        if (labelsMatcher.find()) {
            String labelsArray = labelsMatcher.group(1);
            
            // Extract individual label names
            Pattern labelNamePattern = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]*?)\"");
            Matcher labelNameMatcher = labelNamePattern.matcher(labelsArray);
            
            while (labelNameMatcher.find()) {
                String labelName = unescapeJson(labelNameMatcher.group(1));
                labels.add(labelName);
            }
        }
        
        return labels;
    }
    
    private GitHubIssue parseIssueFromJson(String json) {
        return parseIssueFromJsonFragment(json);
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
     * GitHub Issue representation
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
    
    /**
     * Test method for SSL bypass client
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("❌ Usage: java GitHubApiClientSSLBypass <github_token> [--test-connection|--test-search|--test-comment]");
            System.out.println("   This will test the GitHub API connection with SSL bypass");
            return;
        }
        
        String token = args[0];
        boolean testConnection = args.length > 1 && args[1].equals("--test-connection");
        boolean testSearch = args.length > 1 && args[1].equals("--test-search");
        boolean testComment = args.length > 1 && args[1].equals("--test-comment");
        
        System.out.println("🧪 **GITHUB API CLIENT TEST (SSL BYPASS)**");
        System.out.println("==========================================");
        
        GitHubApiClientSSLBypass client = new GitHubApiClientSSLBypass(token);
        
        // Test connection
        boolean connected = client.testConnection();
        
        if (connected && (testSearch || (!testConnection && !testComment))) {
            System.out.println();
            System.out.println("🎯 **TESTING REPOSITORY ACCESS**");
            System.out.println("================================");
            
            try {
                // Test fetching issues from durion repository
                List<GitHubIssue> issues = client.getStoryIssues("louisburroughs/durion");
                System.out.println("✅ Repository access test successful!");
                
                // Test comment functionality (optional)
                if (testComment && !issues.isEmpty()) {
                    GitHubIssue firstIssue = issues.get(0);
                    System.out.println();
                    System.out.println("🧪 **TESTING COMMENT FUNCTIONALITY**");
                    System.out.println("===================================");
                    
                    String commentText = "🧪 **Test Comment from Durion Workspace Agent**\n\n" +
                                       "This is a test comment to verify the comment functionality works correctly.\n\n" +
                                       "Timestamp: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\n\n" +
                                       "---\n*This is a test comment and can be safely ignored.*";
                    
                    client.addCommentToIssue("louisburroughs/durion", firstIssue.getNumber(), commentText);
                    System.out.println("✅ Comment test successful on issue #" + firstIssue.getNumber());
                } else if (testComment) {
                    System.out.println("⚠️ No issues available for comment testing");
                }
                
            } catch (Exception e) {
                System.out.println("❌ Repository access test failed:");
                System.out.println("   " + e.getMessage());
            }
        }
        
        System.out.println();
        System.out.println("🏁 Test complete!");
    }
}