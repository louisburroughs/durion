package com.durion.audit;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Comprehensive rate limiting and retry logic for GitHub API requests.
 * 
 * This class implements the rate limiting strategy defined in the design document:
 * - Primary rate limit monitoring using response headers
 * - Secondary rate limit detection and 60-second retry
 * - Configurable delays between operations (2s base, 10s every 5 operations)
 * 
 * Requirements: 3.2, 3.3, 3.4
 */
public class GitHubRateLimiter {
    
    // Rate limiting configuration constants
    private static final int BASE_DELAY_MS = 2000;  // 2 seconds base delay
    private static final int BATCH_DELAY_MS = 10000; // 10 seconds every 5 operations
    private static final int BATCH_SIZE = 5;
    private static final int SECONDARY_RATE_LIMIT_WAIT_MS = 60000; // 60 seconds
    private static final int CRITICAL_REMAINING_THRESHOLD = 10;
    private static final int LOW_REMAINING_THRESHOLD = 100;
    
    // Operation tracking
    private final AtomicInteger operationCount = new AtomicInteger(0);
    private volatile RateLimitInfo lastKnownRateLimit;
    
    /**
     * Applies rate limiting delays before making a request.
     * This includes base delays and batch delays based on operation count.
     */
    public void applyPreRequestDelay() throws InterruptedException {
        int currentCount = operationCount.incrementAndGet();
        
        // Base delay between all operations
        System.out.println("⏳ Applying base delay (" + BASE_DELAY_MS + "ms) for rate limiting...");
        Thread.sleep(BASE_DELAY_MS);
        
        // Additional delay every BATCH_SIZE operations
        if (currentCount % BATCH_SIZE == 0) {
            System.out.println("⏳ Applying batch delay (" + BATCH_DELAY_MS + "ms) after " + 
                             BATCH_SIZE + " operations (total: " + currentCount + ")...");
            Thread.sleep(BATCH_DELAY_MS);
        }
        
        // Check if we need to wait based on last known rate limit
        if (lastKnownRateLimit != null && lastKnownRateLimit.isCritical()) {
            long waitTime = calculateWaitTime(lastKnownRateLimit);
            if (waitTime > 0) {
                System.out.println("⚠️ Rate limit critical - waiting " + (waitTime / 1000) + " seconds...");
                Thread.sleep(waitTime);
            }
        }
    }
    
    /**
     * Processes a response to extract rate limit information and handle rate limit errors.
     * 
     * @param response The HTTP response to process
     * @return true if the response indicates success, false if rate limited
     * @throws InterruptedException if interrupted during rate limit wait
     */
    public boolean processResponse(HttpResponse<String> response) throws InterruptedException {
        // Extract and update rate limit information from headers
        updateRateLimitFromHeaders(response);
        
        // Handle rate limit errors
        if (response.statusCode() == 403) {
            return handleRateLimitError(response);
        }
        
        return true; // Success
    }
    
    /**
     * Handles rate limit errors, including secondary rate limits.
     * 
     * @param response The rate limit error response
     * @return false to indicate rate limit error was handled
     * @throws InterruptedException if interrupted during wait
     */
    private boolean handleRateLimitError(HttpResponse<String> response) throws InterruptedException {
        String responseBody = response.body();
        
        if (responseBody != null && responseBody.contains("secondary rate limit")) {
            handleSecondaryRateLimit();
            return false; // Caller should retry
        } else {
            System.out.println("❌ Primary rate limit exceeded");
            
            // Apply intelligent delay based on rate limit info
            if (lastKnownRateLimit != null) {
                long waitTime = calculateWaitTime(lastKnownRateLimit);
                if (waitTime > 0) {
                    System.out.println("⏳ Waiting " + (waitTime / 1000) + " seconds for rate limit reset...");
                    Thread.sleep(waitTime);
                }
            } else {
                // Fallback delay if no rate limit info available
                System.out.println("⏳ Applying fallback delay (30 seconds) for rate limit...");
                Thread.sleep(30000);
            }
            
            return false; // Caller should retry
        }
    }
    
    /**
     * Handles secondary rate limit by waiting 60 seconds.
     */
    private void handleSecondaryRateLimit() throws InterruptedException {
        System.out.println("⚠️ Hit secondary rate limit - waiting 60 seconds before retry...");
        Thread.sleep(SECONDARY_RATE_LIMIT_WAIT_MS);
        System.out.println("✅ Secondary rate limit wait complete");
    }
    
    /**
     * Updates rate limit information from response headers.
     */
    private void updateRateLimitFromHeaders(HttpResponse<String> response) {
        String limitHeader = getHeader(response, "x-ratelimit-limit");
        String remainingHeader = getHeader(response, "x-ratelimit-remaining");
        String usedHeader = getHeader(response, "x-ratelimit-used");
        String resetHeader = getHeader(response, "x-ratelimit-reset");
        String resourceHeader = getHeader(response, "x-ratelimit-resource");
        
        if (remainingHeader != null && resetHeader != null) {
            try {
                int limit = limitHeader != null ? Integer.parseInt(limitHeader) : 5000;
                int remaining = Integer.parseInt(remainingHeader);
                int used = usedHeader != null ? Integer.parseInt(usedHeader) : (limit - remaining);
                long resetTime = Long.parseLong(resetHeader);
                String resource = resourceHeader != null ? resourceHeader : "core";
                
                LocalDateTime resetDateTime = LocalDateTime.ofEpochSecond(resetTime, 0, ZoneOffset.UTC);
                lastKnownRateLimit = new RateLimitInfo(limit, remaining, used, resetDateTime, resource);
                
                logRateLimitStatus();
                
            } catch (NumberFormatException e) {
                System.out.println("⚠️ Could not parse rate limit headers: " + e.getMessage());
            }
        }
    }
    
    /**
     * Logs the current rate limit status with appropriate warnings.
     */
    private void logRateLimitStatus() {
        if (lastKnownRateLimit == null) return;
        
        System.out.println("📊 Rate limit status from headers:");
        System.out.println("   • Limit: " + lastKnownRateLimit.getLimit() + " requests per hour");
        System.out.println("   • Remaining: " + lastKnownRateLimit.getRemaining() + " requests");
        System.out.println("   • Used: " + lastKnownRateLimit.getUsed() + " requests");
        System.out.println("   • Reset time: " + lastKnownRateLimit.getResetTime());
        System.out.println("   • Resource: " + lastKnownRateLimit.getResource());
        
        // Provide warnings based on remaining requests
        if (lastKnownRateLimit.isCritical()) {
            System.out.println("🚨 Rate limit critical (" + lastKnownRateLimit.getRemaining() + " remaining)");
        } else if (lastKnownRateLimit.isLow()) {
            System.out.println("⚠️ Rate limit low (" + lastKnownRateLimit.getRemaining() + " remaining)");
        } else {
            System.out.println("✅ Rate limit OK (" + lastKnownRateLimit.getRemaining() + " remaining)");
        }
    }
    
    /**
     * Calculates how long to wait based on current rate limit status.
     */
    private long calculateWaitTime(RateLimitInfo rateLimitInfo) {
        if (rateLimitInfo == null) return 0;
        
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime resetTime = rateLimitInfo.getResetTime();
        
        // If reset time has passed, no need to wait
        if (now.isAfter(resetTime)) {
            return 0;
        }
        
        long secondsUntilReset = now.until(resetTime, java.time.temporal.ChronoUnit.SECONDS);
        
        if (rateLimitInfo.isCritical()) {
            // Wait until reset if critical
            return secondsUntilReset * 1000 + 5000; // Add 5 second buffer
        } else if (rateLimitInfo.isLow()) {
            // Smart delay based on remaining requests and time until reset
            long smartDelay = Math.min(secondsUntilReset * 1000 / rateLimitInfo.getRemaining(), 30000);
            return Math.max(smartDelay, 2000); // Minimum 2 seconds
        }
        
        return 0; // No wait needed
    }
    
    /**
     * Helper method to get header value (case-insensitive).
     */
    private String getHeader(HttpResponse<String> response, String headerName) {
        return response.headers().firstValue(headerName).orElse(null);
    }
    
    /**
     * Gets the current rate limit information.
     */
    public RateLimitInfo getCurrentRateLimit() {
        return lastKnownRateLimit;
    }
    
    /**
     * Gets the current operation count.
     */
    public int getOperationCount() {
        return operationCount.get();
    }
    
    /**
     * Resets the operation count (useful for testing or new sessions).
     */
    public void resetOperationCount() {
        operationCount.set(0);
        System.out.println("🔄 Rate limiter operation count reset");
    }
    
    /**
     * Checks if we should pause operations based on current rate limit status.
     */
    public boolean shouldPauseOperations() {
        return lastKnownRateLimit != null && lastKnownRateLimit.isCritical();
    }
    
    /**
     * Provides a summary of rate limiting activity.
     */
    public void logSummary() {
        System.out.println("📊 Rate Limiter Summary:");
        System.out.println("   • Total operations: " + operationCount.get());
        System.out.println("   • Base delays applied: " + operationCount.get());
        System.out.println("   • Batch delays applied: " + (operationCount.get() / BATCH_SIZE));
        
        if (lastKnownRateLimit != null) {
            System.out.println("   • Final rate limit status: " + lastKnownRateLimit.getRemaining() + 
                             "/" + lastKnownRateLimit.getLimit() + " remaining");
        }
    }
}