package com.durion.audit;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Pattern;

/**
 * Comprehensive authentication and error handling for GitHub API integration.
 * 
 * This class provides:
 * - Clear error messages for authentication failures
 * - Graceful handling of network timeouts and connection errors
 * - System exits cleanly on unrecoverable errors
 * - GitHub token validation and format checking
 * 
 * Requirements: 3.5
 */
public class GitHubAuthenticationHandler {
    
    private final String githubToken;
    private final HttpClient httpClient;
    private static final String GITHUB_API_BASE = "https://api.github.com";
    
    // Token format patterns
    private static final Pattern CLASSIC_TOKEN_PATTERN = Pattern.compile("^ghp_[a-zA-Z0-9]{36}$");
    private static final Pattern FINE_GRAINED_TOKEN_PATTERN = Pattern.compile("^github_pat_[a-zA-Z0-9_]{82}$");
    
    // Timeout configurations
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);
    
    public GitHubAuthenticationHandler(String githubToken) {
        this.githubToken = githubToken;
        this.httpClient = createHttpClient();
    }
    
    /**
     * Creates an HTTP client with appropriate timeout configurations.
     */
    private HttpClient createHttpClient() {
        return HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();
    }
    
    /**
     * Validates the GitHub token format and permissions.
     * 
     * @return true if token is valid and has required permissions
     * @throws AuthenticationException if token is invalid or lacks permissions
     */
    public boolean validateToken() throws AuthenticationException {
        System.out.println("🔐 Validating GitHub token...");
        
        // Step 1: Check token format
        validateTokenFormat();
        
        // Step 2: Test token authentication
        validateTokenAuthentication();
        
        // Step 3: Check token permissions
        validateTokenPermissions();
        
        System.out.println("✅ GitHub token validation successful");
        return true;
    }
    
    /**
     * Validates the token format (ghp_ or github_pat_ prefix).
     */
    private void validateTokenFormat() throws AuthenticationException {
        if (githubToken == null || githubToken.trim().isEmpty()) {
            throw new AuthenticationException(
                "GitHub token is null or empty. Please provide a valid GitHub token.",
                AuthenticationException.ErrorType.MISSING_TOKEN
            );
        }
        
        String tokenPrefix = githubToken.length() > 8 ? 
            githubToken.substring(0, 8) + "..." : "***";
        System.out.println("🔑 Token prefix: " + tokenPrefix);
        System.out.println("📏 Token length: " + githubToken.length() + " characters");
        
        if (githubToken.startsWith("ghp_")) {
            System.out.println("✅ Token format: Personal Access Token (classic)");
            if (!CLASSIC_TOKEN_PATTERN.matcher(githubToken).matches()) {
                throw new AuthenticationException(
                    "Invalid classic Personal Access Token format. Expected format: ghp_ followed by 36 characters.",
                    AuthenticationException.ErrorType.INVALID_TOKEN_FORMAT
                );
            }
        } else if (githubToken.startsWith("github_pat_")) {
            System.out.println("✅ Token format: Fine-grained Personal Access Token");
            if (!FINE_GRAINED_TOKEN_PATTERN.matcher(githubToken).matches()) {
                throw new AuthenticationException(
                    "Invalid fine-grained Personal Access Token format. Expected format: github_pat_ followed by 82 characters.",
                    AuthenticationException.ErrorType.INVALID_TOKEN_FORMAT
                );
            }
        } else {
            throw new AuthenticationException(
                "Unknown token format. Expected token to start with 'ghp_' (classic) or 'github_pat_' (fine-grained).",
                AuthenticationException.ErrorType.INVALID_TOKEN_FORMAT
            );
        }
    }
    
    /**
     * Tests token authentication by making a request to /user endpoint.
     */
    private void validateTokenAuthentication() throws AuthenticationException {
        System.out.println("🔍 Testing token authentication...");
        
        try {
            String testUrl = GITHUB_API_BASE + "/user";
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(testUrl))
                .header("Authorization", "Bearer " + githubToken)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "Durion-Missing-Issues-Audit/1.0")
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
            
            System.out.println("📤 Sending authentication test request...");
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            System.out.println("📊 HTTP Status: " + response.statusCode());
            
            if (response.statusCode() == 200) {
                System.out.println("✅ Token authentication successful");
                
                // Extract and log user information
                String responseBody = response.body();
                String username = extractUsernameFromResponse(responseBody);
                if (username != null) {
                    System.out.println("👤 Authenticated as: " + username);
                }
                
            } else if (response.statusCode() == 401) {
                throw new AuthenticationException(
                    "Authentication failed. The provided GitHub token is invalid or expired.",
                    AuthenticationException.ErrorType.INVALID_CREDENTIALS
                );
            } else if (response.statusCode() == 403) {
                throw new AuthenticationException(
                    "Authentication forbidden. The token may lack required permissions or be rate limited.",
                    AuthenticationException.ErrorType.INSUFFICIENT_PERMISSIONS
                );
            } else {
                throw new AuthenticationException(
                    "Unexpected authentication response: HTTP " + response.statusCode() + " - " + response.body(),
                    AuthenticationException.ErrorType.AUTHENTICATION_ERROR
                );
            }
            
        } catch (java.net.http.HttpTimeoutException e) {
            throw new AuthenticationException(
                "Authentication request timed out after " + REQUEST_TIMEOUT.getSeconds() + " seconds. " +
                "Please check your network connection and try again.",
                AuthenticationException.ErrorType.TIMEOUT,
                e
            );
        } catch (IOException e) {
            throw new AuthenticationException(
                "Network error during authentication: " + e.getMessage() + 
                ". Please check your internet connection and try again.",
                AuthenticationException.ErrorType.NETWORK_ERROR,
                e
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AuthenticationException(
                "Authentication request was interrupted.",
                AuthenticationException.ErrorType.INTERRUPTED,
                e
            );
        }
    }
    
    /**
     * Validates that the token has required repository permissions.
     */
    private void validateTokenPermissions() throws AuthenticationException {
        System.out.println("🔍 Checking token permissions...");
        
        try {
            // Test repository access by searching for issues
            String testUrl = GITHUB_API_BASE + "/search/issues?q=repo:louisburroughs/durion+type:issue&per_page=1";
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(testUrl))
                .header("Authorization", "Bearer " + githubToken)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "Durion-Missing-Issues-Audit/1.0")
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
            
            System.out.println("📤 Testing repository access permissions...");
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            System.out.println("📊 Permission test status: " + response.statusCode());
            
            if (response.statusCode() == 200) {
                System.out.println("✅ Repository access permissions confirmed");
            } else if (response.statusCode() == 403) {
                throw new AuthenticationException(
                    "Insufficient permissions. The token lacks 'repo' scope required to access repository issues. " +
                    "Please ensure your token has 'repo' permissions.",
                    AuthenticationException.ErrorType.INSUFFICIENT_PERMISSIONS
                );
            } else if (response.statusCode() == 404) {
                throw new AuthenticationException(
                    "Repository not found or not accessible. Please ensure the token has access to the required repositories.",
                    AuthenticationException.ErrorType.REPOSITORY_ACCESS_DENIED
                );
            } else {
                System.out.println("⚠️ Unexpected permission test response: " + response.statusCode());
                System.out.println("   This may indicate rate limiting or temporary issues, but authentication appears valid.");
            }
            
        } catch (IOException e) {
            throw new AuthenticationException(
                "Network error during permission validation: " + e.getMessage(),
                AuthenticationException.ErrorType.NETWORK_ERROR,
                e
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AuthenticationException(
                "Permission validation was interrupted.",
                AuthenticationException.ErrorType.INTERRUPTED,
                e
            );
        }
    }
    
    /**
     * Extracts username from GitHub API response.
     */
    private String extractUsernameFromResponse(String responseBody) {
        try {
            java.util.regex.Pattern loginPattern = java.util.regex.Pattern.compile("\"login\"\\s*:\\s*\"([^\"]+)\"");
            java.util.regex.Matcher matcher = loginPattern.matcher(responseBody);
            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (Exception e) {
            System.out.println("⚠️ Could not extract username from response: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Handles various types of errors that can occur during API operations.
     */
    public static class ErrorHandler {
        
        /**
         * Handles network timeouts with appropriate error messages.
         */
        public static void handleTimeout(Exception e, String operation) {
            System.err.println("⏰ Timeout Error during " + operation + ":");
            System.err.println("   " + e.getMessage());
            System.err.println("   This may be due to:");
            System.err.println("   • Slow network connection");
            System.err.println("   • GitHub API being temporarily slow");
            System.err.println("   • Corporate firewall or proxy issues");
            System.err.println("   Suggestion: Try again in a few minutes or check your network connection.");
        }
        
        /**
         * Handles connection errors with appropriate error messages.
         */
        public static void handleConnectionError(Exception e, String operation) {
            System.err.println("🌐 Connection Error during " + operation + ":");
            System.err.println("   " + e.getMessage());
            System.err.println("   This may be due to:");
            System.err.println("   • No internet connection");
            System.err.println("   • GitHub API being temporarily unavailable");
            System.err.println("   • DNS resolution issues");
            System.err.println("   • Corporate firewall blocking access");
            System.err.println("   Suggestion: Check your internet connection and try again.");
        }
        
        /**
         * Handles authentication errors with clear guidance.
         */
        public static void handleAuthenticationError(AuthenticationException e) {
            System.err.println("🔐 Authentication Error:");
            System.err.println("   " + e.getMessage());
            
            switch (e.getErrorType()) {
                case MISSING_TOKEN:
                    System.err.println("   Solution: Provide a GitHub token using:");
                    System.err.println("   • Command line: --token <your_token>");
                    System.err.println("   • Environment variable: GITHUB_TOKEN=<your_token>");
                    break;
                    
                case INVALID_TOKEN_FORMAT:
                    System.err.println("   Solution: Ensure your token follows the correct format:");
                    System.err.println("   • Classic tokens: ghp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx (40 chars)");
                    System.err.println("   • Fine-grained tokens: github_pat_xxxxxxxxxx... (variable length)");
                    break;
                    
                case INVALID_CREDENTIALS:
                    System.err.println("   Solution: Check that your token is:");
                    System.err.println("   • Not expired");
                    System.err.println("   • Correctly copied (no extra spaces)");
                    System.err.println("   • Valid for the GitHub account");
                    break;
                    
                case INSUFFICIENT_PERMISSIONS:
                    System.err.println("   Solution: Ensure your token has 'repo' scope:");
                    System.err.println("   • Go to GitHub Settings > Developer settings > Personal access tokens");
                    System.err.println("   • Create a new token or update existing token");
                    System.err.println("   • Select 'repo' scope for full repository access");
                    break;
                    
                case REPOSITORY_ACCESS_DENIED:
                    System.err.println("   Solution: Ensure you have access to the required repositories:");
                    System.err.println("   • louisburroughs/durion");
                    System.err.println("   • louisburroughs/durion-moqui-frontend");
                    System.err.println("   • louisburroughs/durion-positivity-backend");
                    break;
                    
                default:
                    System.err.println("   Solution: Check your token and network connection, then try again.");
                    break;
            }
        }
        
        /**
         * Determines if an error is recoverable and the operation should be retried.
         */
        public static boolean isRecoverable(Exception e) {
            if (e instanceof java.net.http.HttpTimeoutException) {
                return true; // Timeouts are often temporary
            }
            if (e instanceof java.net.ConnectException) {
                return true; // Connection issues may be temporary
            }
            if (e instanceof AuthenticationException) {
                AuthenticationException authEx = (AuthenticationException) e;
                return authEx.getErrorType() == AuthenticationException.ErrorType.NETWORK_ERROR ||
                       authEx.getErrorType() == AuthenticationException.ErrorType.TIMEOUT;
            }
            return false;
        }
        
        /**
         * Exits the system cleanly with appropriate error code and message.
         */
        public static void exitWithError(String message, int exitCode) {
            System.err.println("❌ Fatal Error: " + message);
            System.err.println("   The application cannot continue and will exit.");
            System.err.println("   Exit code: " + exitCode);
            System.exit(exitCode);
        }
    }
    
    /**
     * Custom exception for authentication-related errors.
     */
    public static class AuthenticationException extends Exception {
        
        public enum ErrorType {
            MISSING_TOKEN,
            INVALID_TOKEN_FORMAT,
            INVALID_CREDENTIALS,
            INSUFFICIENT_PERMISSIONS,
            REPOSITORY_ACCESS_DENIED,
            NETWORK_ERROR,
            TIMEOUT,
            INTERRUPTED,
            AUTHENTICATION_ERROR
        }
        
        private final ErrorType errorType;
        
        public AuthenticationException(String message, ErrorType errorType) {
            super(message);
            this.errorType = errorType;
        }
        
        public AuthenticationException(String message, ErrorType errorType, Throwable cause) {
            super(message, cause);
            this.errorType = errorType;
        }
        
        public ErrorType getErrorType() {
            return errorType;
        }
        
        /**
         * Returns true if this error indicates the system should exit.
         */
        public boolean isFatal() {
            return errorType == ErrorType.MISSING_TOKEN ||
                   errorType == ErrorType.INVALID_TOKEN_FORMAT ||
                   errorType == ErrorType.INVALID_CREDENTIALS ||
                   errorType == ErrorType.INSUFFICIENT_PERMISSIONS;
        }
        
        /**
         * Returns the appropriate exit code for this error.
         */
        public int getExitCode() {
            switch (errorType) {
                case MISSING_TOKEN:
                case INVALID_TOKEN_FORMAT:
                    return 2; // Configuration error
                case INVALID_CREDENTIALS:
                case INSUFFICIENT_PERMISSIONS:
                    return 3; // Authentication error
                case REPOSITORY_ACCESS_DENIED:
                    return 4; // Permission error
                case NETWORK_ERROR:
                case TIMEOUT:
                    return 5; // Network error
                default:
                    return 1; // General error
            }
        }
    }
}