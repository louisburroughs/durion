package com.durion.core;

import java.time.Duration;
import java.time.Instant;

/**
 * Performance metrics for workspace agents
 */
public class AgentMetrics {
    private final long totalRequests;
    private final long successfulRequests;
    private final long failedRequests;
    private final Duration averageResponseTime;
    private final Duration maxResponseTime;
    private final double currentAvailability;
    private final int activeConnections;
    private final Instant lastUpdated;
    
    public AgentMetrics(long totalRequests, long successfulRequests, long failedRequests,
                       Duration averageResponseTime, Duration maxResponseTime,
                       double currentAvailability, int activeConnections) {
        this.totalRequests = totalRequests;
        this.successfulRequests = successfulRequests;
        this.failedRequests = failedRequests;
        this.averageResponseTime = averageResponseTime;
        this.maxResponseTime = maxResponseTime;
        this.currentAvailability = currentAvailability;
        this.activeConnections = activeConnections;
        this.lastUpdated = Instant.now();
    }
    
    public long getTotalRequests() { return totalRequests; }
    public long getSuccessfulRequests() { return successfulRequests; }
    public long getFailedRequests() { return failedRequests; }
    public Duration getAverageResponseTime() { return averageResponseTime; }
    public Duration getMaxResponseTime() { return maxResponseTime; }
    public double getCurrentAvailability() { return currentAvailability; }
    public int getActiveConnections() { return activeConnections; }
    public Instant getLastUpdated() { return lastUpdated; }
    
    public double getSuccessRate() {
        return totalRequests > 0 ? (double) successfulRequests / totalRequests : 0.0;
    }
    
    public boolean meetsPerformanceTargets() {
        return averageResponseTime.compareTo(Duration.ofSeconds(5)) <= 0 && 
               currentAvailability >= 0.999;
    }
}
