package com.durion.audit;

import java.io.IOException;
import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.nio.file.NoSuchFileException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Comprehensive error handling for the Missing Issues Audit System.
 * 
 * This class provides:
 * - Graceful handling of network timeouts and connection errors
 * - Clear error messages for different types of failures
 * - System exits cleanly on unrecoverable errors
 * - Error recovery strategies and retry logic
 * 
 * Requirements: 3.5
 */
public class AuditSystemErrorHandler {
    
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 5000; // 5 seconds
    
    private final AtomicInteger errorCount = new AtomicInteger(0);
    private final AtomicInteger recoveredErrorCount = new AtomicInteger(0);
    
    /**
     * Handles file system errors with appropriate error messages and recovery suggestions.
     */
    public void handleFileSystemError(Exception e, String operation, String filePath) {
        errorCount.incrementAndGet();
        
        System.err.println("📁 File System Error during " + operation + ":");
        System.err.println("   File: " + filePath);
        System.err.println("   Error: " + e.getMessage());
        
        if (e instanceof NoSuchFileException) {
            System.err.println("   Issue: Required file not found");
            System.err.println("   Solutions:");
            System.err.println("   • Ensure the file exists at the specified path");
            System.err.println("   • Check if you're running from the correct directory");
            System.err.println("   • Verify file permissions");
            
            if (filePath.contains("processed-issues.txt")) {
                System.err.println("   • The processed-issues.txt file should be in .github/orchestration/");
                System.err.println("   • This file is created by the story processing system");
            } else if (filePath.contains("story-sequence.md")) {
                System.err.println("   • The story-sequence.md file should be in .github/orchestration/");
                System.err.println("   • This file contains story metadata and URLs");
            }
            
        } else if (e instanceof java.nio.file.AccessDeniedException) {
            System.err.println("   Issue: Permission denied");
            System.err.println("   Solutions:");
            System.err.println("   • Check file permissions");
            System.err.println("   • Ensure you have read access to the file");
            System.err.println("   • Try running with appropriate permissions");
            
        } else if (e instanceof IOException) {
            System.err.println("   Issue: I/O error occurred");
            System.err.println("   Solutions:");
            System.err.println("   • Check if the disk is full");
            System.err.println("   • Verify the file is not corrupted");
            System.err.println("   • Ensure the file is not locked by another process");
        }
    }
    
    /**
     * Handles network errors with retry logic and clear error messages.
     */
    public boolean handleNetworkError(Exception e, String operation, int attemptNumber) {
        errorCount.incrementAndGet();
        
        System.err.println("🌐 Network Error (attempt " + attemptNumber + "/" + MAX_RETRY_ATTEMPTS + ") during " + operation + ":");
        System.err.println("   Error: " + e.getMessage());
        
        if (e instanceof HttpTimeoutException) {
            System.err.println("   Issue: Request timed out");
            System.err.println("   Possible causes:");
            System.err.println("   • Slow network connection");
            System.err.println("   • GitHub API temporarily slow");
            System.err.println("   • Corporate firewall or proxy delays");
            
        } else if (e instanceof ConnectException) {
            System.err.println("   Issue: Cannot connect to GitHub API");
            System.err.println("   Possible causes:");
            System.err.println("   • No internet connection");
            System.err.println("   • GitHub API temporarily unavailable");
            System.err.println("   • Corporate firewall blocking access");
            System.err.println("   • DNS resolution issues");
            
        } else if (e instanceof IOException) {
            System.err.println("   Issue: Network I/O error");
            System.err.println("   Possible causes:");
            System.err.println("   • Connection interrupted");
            System.err.println("   • Network instability");
            System.err.println("   • Proxy or firewall interference");
        }
        
        // Determine if we should retry
        if (attemptNumber < MAX_RETRY_ATTEMPTS && isRetryableError(e)) {
            System.err.println("   🔄 Will retry in " + (RETRY_DELAY_MS / 1000) + " seconds...");
            
            try {
                Thread.sleep(RETRY_DELAY_MS);
                recoveredErrorCount.incrementAndGet();
                return true; // Indicate retry should be attempted
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                System.err.println("   ❌ Retry interrupted");
                return false;
            }
        } else {
            System.err.println("   ❌ Maximum retry attempts reached or error not retryable");
            System.err.println("   Suggestions:");
            System.err.println("   • Check your internet connection");
            System.err.println("   • Try again in a few minutes");
            System.err.println("   • Contact your network administrator if behind a corporate firewall");
            return false;
        }
    }
    
    /**
     * Handles GitHub API specific errors with detailed guidance.
     */
    public void handleGitHubApiError(int statusCode, String responseBody, String operation) {
        errorCount.incrementAndGet();
        
        System.err.println("🐙 GitHub API Error during " + operation + ":");
        System.err.println("   HTTP Status: " + statusCode);
        
        switch (statusCode) {
            case 401:
                System.err.println("   Issue: Unauthorized - Invalid or expired token");
                System.err.println("   Solutions:");
                System.err.println("   • Check that your GitHub token is correct");
                System.err.println("   • Ensure the token hasn't expired");
                System.err.println("   • Verify the token format (ghp_ or github_pat_)");
                break;
                
            case 403:
                if (responseBody != null && responseBody.contains("rate limit")) {
                    System.err.println("   Issue: Rate limit exceeded");
                    System.err.println("   Solutions:");
                    System.err.println("   • Wait for rate limit to reset (check X-RateLimit-Reset header)");
                    System.err.println("   • Use a token with higher rate limits");
                    System.err.println("   • Reduce the frequency of requests");
                } else {
                    System.err.println("   Issue: Forbidden - Insufficient permissions");
                    System.err.println("   Solutions:");
                    System.err.println("   • Ensure your token has 'repo' scope");
                    System.err.println("   • Check if you have access to the repository");
                    System.err.println("   • Verify the repository name is correct");
                }
                break;
                
            case 404:
                System.err.println("   Issue: Not Found - Repository or resource doesn't exist");
                System.err.println("   Solutions:");
                System.err.println("   • Check the repository name spelling");
                System.err.println("   • Ensure the repository exists and is accessible");
                System.err.println("   • Verify you have permission to access the repository");
                break;
                
            case 422:
                System.err.println("   Issue: Unprocessable Entity - Invalid request parameters");
                System.err.println("   Solutions:");
                System.err.println("   • Check the search query syntax");
                System.err.println("   • Verify repository names are correct");
                System.err.println("   • Ensure request parameters are valid");
                break;
                
            case 500:
            case 502:
            case 503:
            case 504:
                System.err.println("   Issue: GitHub server error (temporary)");
                System.err.println("   Solutions:");
                System.err.println("   • Try again in a few minutes");
                System.err.println("   • Check GitHub status at https://www.githubstatus.com/");
                System.err.println("   • The issue is likely temporary");
                break;
                
            default:
                System.err.println("   Issue: Unexpected HTTP status code");
                System.err.println("   Response: " + (responseBody != null ? responseBody.substring(0, Math.min(200, responseBody.length())) : "No response body"));
                System.err.println("   Solutions:");
                System.err.println("   • Check GitHub API documentation");
                System.err.println("   • Try again later");
                System.err.println("   • Contact support if the issue persists");
                break;
        }
    }
    
    /**
     * Handles data parsing errors with helpful guidance.
     */
    public void handleDataParsingError(Exception e, String dataType, String source) {
        errorCount.incrementAndGet();
        
        System.err.println("📊 Data Parsing Error:");
        System.err.println("   Data Type: " + dataType);
        System.err.println("   Source: " + source);
        System.err.println("   Error: " + e.getMessage());
        
        if (dataType.contains("JSON")) {
            System.err.println("   Issue: Invalid JSON format");
            System.err.println("   Possible causes:");
            System.err.println("   • GitHub API response format changed");
            System.err.println("   • Incomplete or corrupted response");
            System.err.println("   • Network interference with response");
            
        } else if (dataType.contains("processed-issues")) {
            System.err.println("   Issue: Invalid processed-issues.txt format");
            System.err.println("   Expected format: One issue number per line");
            System.err.println("   Possible causes:");
            System.err.println("   • File contains non-numeric data");
            System.err.println("   • File is corrupted or incomplete");
            System.err.println("   • File encoding issues");
            
        } else if (dataType.contains("story-sequence")) {
            System.err.println("   Issue: Invalid story-sequence.md format");
            System.err.println("   Expected format: Markdown with story metadata");
            System.err.println("   Possible causes:");
            System.err.println("   • File format changed");
            System.err.println("   • Missing required metadata fields");
            System.err.println("   • File corruption");
        }
        
        System.err.println("   Solutions:");
        System.err.println("   • Verify the source file format");
        System.err.println("   • Check for file corruption");
        System.err.println("   • Try regenerating the source data");
    }
    
    /**
     * Determines if an error is retryable.
     */
    private boolean isRetryableError(Exception e) {
        return e instanceof HttpTimeoutException ||
               e instanceof ConnectException ||
               (e instanceof IOException && !(e instanceof java.nio.file.NoSuchFileException));
    }
    
    /**
     * Exits the system cleanly with appropriate error code and summary.
     */
    public void exitWithError(String message, int exitCode) {
        System.err.println();
        System.err.println("❌ FATAL ERROR: " + message);
        System.err.println();
        
        // Provide error summary
        System.err.println("📊 Error Summary:");
        System.err.println("   • Total errors encountered: " + errorCount.get());
        System.err.println("   • Errors recovered from: " + recoveredErrorCount.get());
        System.err.println("   • Fatal errors: " + (errorCount.get() - recoveredErrorCount.get()));
        System.err.println();
        
        // Provide general guidance
        System.err.println("💡 General Troubleshooting:");
        System.err.println("   • Check your internet connection");
        System.err.println("   • Verify your GitHub token is valid and has required permissions");
        System.err.println("   • Ensure all required files exist in the correct locations");
        System.err.println("   • Try running the command again in a few minutes");
        System.err.println("   • Check GitHub status at https://www.githubstatus.com/");
        System.err.println();
        
        System.err.println("🚪 The application will now exit with code " + exitCode);
        System.exit(exitCode);
    }
    
    /**
     * Provides a summary of error handling activity.
     */
    public void logErrorSummary() {
        if (errorCount.get() > 0) {
            System.out.println("📊 Error Handling Summary:");
            System.out.println("   • Total errors encountered: " + errorCount.get());
            System.out.println("   • Errors recovered from: " + recoveredErrorCount.get());
            System.out.println("   • Success rate: " + 
                String.format("%.1f%%", (recoveredErrorCount.get() * 100.0 / errorCount.get())));
        } else {
            System.out.println("✅ No errors encountered during execution");
        }
    }
    
    /**
     * Resets error counters (useful for testing or new operations).
     */
    public void resetCounters() {
        errorCount.set(0);
        recoveredErrorCount.set(0);
    }
    
    /**
     * Gets the total number of errors encountered.
     */
    public int getErrorCount() {
        return errorCount.get();
    }
    
    /**
     * Gets the number of errors that were recovered from.
     */
    public int getRecoveredErrorCount() {
        return recoveredErrorCount.get();
    }
    
    /**
     * Checks if any fatal errors have occurred.
     */
    public boolean hasFatalErrors() {
        return (errorCount.get() - recoveredErrorCount.get()) > 0;
    }
}