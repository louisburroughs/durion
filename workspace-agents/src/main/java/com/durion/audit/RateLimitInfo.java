package com.durion.audit;

import java.time.LocalDateTime;

/**
 * Rate limit information extracted from GitHub API response headers.
 * 
 * Provides comprehensive rate limiting data for monitoring and decision making
 * during API operations.
 */
public class RateLimitInfo {
    
    private final int limit;
    private final int remaining;
    private final int used;
    private final LocalDateTime resetTime;
    private final String resource;
    
    /**
     * Creates rate limit information.
     * 
     * @param limit Total rate limit for the resource
     * @param remaining Remaining requests in current window
     * @param used Used requests in current window
     * @param resetTime When the rate limit window resets
     * @param resource Resource type (core, search, etc.)
     */
    public RateLimitInfo(int limit, int remaining, int used, LocalDateTime resetTime, String resource) {
        this.limit = limit;
        this.remaining = remaining;
        this.used = used;
        this.resetTime = resetTime;
        this.resource = resource;
    }
    
    public int getLimit() {
        return limit;
    }
    
    public int getRemaining() {
        return remaining;
    }
    
    public int getUsed() {
        return used;
    }
    
    public LocalDateTime getResetTime() {
        return resetTime;
    }
    
    public String getResource() {
        return resource;
    }
    
    /**
     * Calculates the percentage of rate limit used.
     * 
     * @return Percentage used (0.0 to 100.0)
     */
    public double getUsagePercentage() {
        return limit > 0 ? (used * 100.0) / limit : 0.0;
    }
    
    /**
     * Checks if rate limit is critically low (less than 10 remaining).
     * 
     * @return true if critically low
     */
    public boolean isCriticallyLow() {
        return remaining < 10;
    }
    
    /**
     * Checks if rate limit is critical (alias for isCriticallyLow).
     * 
     * @return true if critical
     */
    public boolean isCritical() {
        return isCriticallyLow();
    }
    
    /**
     * Checks if rate limit is getting low (less than 100 remaining).
     * 
     * @return true if getting low
     */
    public boolean isGettingLow() {
        return remaining < 100;
    }
    
    /**
     * Checks if rate limit is low (alias for isGettingLow).
     * 
     * @return true if low
     */
    public boolean isLow() {
        return isGettingLow();
    }
    
    @Override
    public String toString() {
        return String.format("RateLimitInfo{resource='%s', remaining=%d/%d, used=%d, resetTime=%s}", 
            resource, remaining, limit, used, resetTime);
    }
}