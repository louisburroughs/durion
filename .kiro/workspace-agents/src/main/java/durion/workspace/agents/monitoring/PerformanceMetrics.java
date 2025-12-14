package durion.workspace.agents.monitoring;

import java.time.Duration;

/**
 * Performance metrics record for workspace agents
 */
public record PerformanceMetrics(
    Duration averageResponseTime,
    Duration medianResponseTime,
    Duration get95thPercentileResponseTime,
    double availability,
    int currentConcurrentUsers,
    long totalRequests,
    long successfulRequests,
    boolean meetsResponseTimeTarget,
    boolean meetsAvailabilityTarget,
    boolean meetsConcurrencyTarget
) {
    
    /**
     * Gets the overall performance score (0-100)
     */
    public int getPerformanceScore() {
        int score = 0;
        
        // Response time score (40 points max)
        if (meetsResponseTimeTarget) {
            score += 40;
        } else {
            // Partial credit based on how close we are
            double ratio = Duration.ofSeconds(5).toNanos() / (double) get95thPercentileResponseTime.toNanos();
            score += (int) (40 * Math.min(1.0, ratio));
        }
        
        // Availability score (40 points max)
        if (meetsAvailabilityTarget) {
            score += 40;
        } else {
            // Partial credit based on availability percentage
            score += (int) (40 * availability);
        }
        
        // Concurrency score (20 points max)
        if (meetsConcurrencyTarget) {
            score += 20;
        } else {
            // Partial credit if we're not too far over
            double ratio = 100.0 / Math.max(100, currentConcurrentUsers);
            score += (int) (20 * ratio);
        }
        
        return Math.min(100, score);
    }
    
    /**
     * Gets error rate as percentage
     */
    public double getErrorRate() {
        if (totalRequests == 0) return 0.0;
        return ((double) (totalRequests - successfulRequests) / totalRequests) * 100.0;
    }
    
    /**
     * Checks if all performance targets are met
     */
    public boolean allTargetsMet() {
        return meetsResponseTimeTarget && meetsAvailabilityTarget && meetsConcurrencyTarget;
    }
    
    /**
     * Gets a human-readable performance summary
     */
    public String getPerformanceSummary() {
        return String.format(
            "Performance: %d/100 | Response Time: %dms (95th) | Availability: %.2f%% | Users: %d/100 | Error Rate: %.2f%%",
            getPerformanceScore(),
            get95thPercentileResponseTime.toMillis(),
            availability * 100,
            currentConcurrentUsers,
            getErrorRate()
        );
    }
}