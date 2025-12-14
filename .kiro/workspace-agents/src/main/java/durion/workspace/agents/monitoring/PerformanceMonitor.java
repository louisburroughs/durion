package durion.workspace.agents.monitoring;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.List;
import java.util.ArrayList;

/**
 * Performance monitoring for workspace agents.
 * Tracks response times, availability, and concurrent users.
 */
public class PerformanceMonitor {
    
    private final AtomicInteger concurrentUsers = new AtomicInteger(0);
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong successfulRequests = new AtomicLong(0);
    private final ConcurrentLinkedQueue<RequestMetric> recentRequests = new ConcurrentLinkedQueue<>();
    
    // Performance targets
    private static final Duration RESPONSE_TIME_TARGET = Duration.ofSeconds(5);
    private static final double AVAILABILITY_TARGET = 0.999; // 99.9%
    private static final int MAX_CONCURRENT_USERS = 100;
    private static final int METRICS_RETENTION_SIZE = 1000;
    
    /**
     * Records a request for performance tracking
     */
    public void recordRequest(String requestId, Duration responseTime, boolean success) {
        totalRequests.incrementAndGet();
        if (success) {
            successfulRequests.incrementAndGet();
        }
        
        RequestMetric metric = new RequestMetric(requestId, responseTime, success, Instant.now());
        recentRequests.offer(metric);
        
        // Keep only recent metrics to prevent memory issues
        while (recentRequests.size() > METRICS_RETENTION_SIZE) {
            recentRequests.poll();
        }
    }
    
    /**
     * Increments concurrent user count
     */
    public void incrementConcurrentUsers() {
        concurrentUsers.incrementAndGet();
    }
    
    /**
     * Decrements concurrent user count
     */
    public void decrementConcurrentUsers() {
        concurrentUsers.decrementAndGet();
    }
    
    /**
     * Gets current concurrent user count
     */
    public int getConcurrentUsers() {
        return concurrentUsers.get();
    }
    
    /**
     * Gets comprehensive performance metrics
     */
    public PerformanceMetrics getMetrics() {
        List<RequestMetric> metrics = new ArrayList<>(recentRequests);
        
        if (metrics.isEmpty()) {
            return new PerformanceMetrics(
                Duration.ZERO, Duration.ZERO, Duration.ZERO,
                1.0, 0, 0, 0, true, true, true
            );
        }
        
        // Calculate response time statistics
        List<Duration> responseTimes = metrics.stream()
                .map(RequestMetric::responseTime)
                .sorted()
                .toList();
        
        Duration averageResponseTime = calculateAverage(responseTimes);
        Duration medianResponseTime = calculateMedian(responseTimes);
        Duration p95ResponseTime = calculatePercentile(responseTimes, 0.95);
        
        // Calculate availability
        long successful = metrics.stream().mapToLong(m -> m.success() ? 1 : 0).sum();
        double availability = (double) successful / metrics.size();
        
        // Check performance requirements
        boolean meetsResponseTimeTarget = p95ResponseTime.compareTo(RESPONSE_TIME_TARGET) <= 0;
        boolean meetsAvailabilityTarget = availability >= AVAILABILITY_TARGET;
        boolean meetsConcurrencyTarget = concurrentUsers.get() <= MAX_CONCURRENT_USERS;
        
        return new PerformanceMetrics(
            averageResponseTime, medianResponseTime, p95ResponseTime,
            availability, concurrentUsers.get(), totalRequests.get(), 
            successfulRequests.get(), meetsResponseTimeTarget, 
            meetsAvailabilityTarget, meetsConcurrencyTarget
        );
    }
    
    /**
     * Checks if system meets all performance requirements
     */
    public boolean meetsPerformanceRequirements() {
        PerformanceMetrics metrics = getMetrics();
        return metrics.meetsResponseTimeTarget() && 
               metrics.meetsAvailabilityTarget() && 
               metrics.meetsConcurrencyTarget();
    }
    
    /**
     * Gets performance health status
     */
    public PerformanceHealth getHealthStatus() {
        PerformanceMetrics metrics = getMetrics();
        
        if (metrics.meetsResponseTimeTarget() && 
            metrics.meetsAvailabilityTarget() && 
            metrics.meetsConcurrencyTarget()) {
            return PerformanceHealth.HEALTHY;
        } else if (metrics.availability() > 0.95 && 
                   metrics.get95thPercentileResponseTime().compareTo(Duration.ofSeconds(10)) < 0) {
            return PerformanceHealth.DEGRADED;
        } else {
            return PerformanceHealth.CRITICAL;
        }
    }
    
    private Duration calculateAverage(List<Duration> durations) {
        if (durations.isEmpty()) return Duration.ZERO;
        
        long totalNanos = durations.stream()
                .mapToLong(Duration::toNanos)
                .sum();
        
        return Duration.ofNanos(totalNanos / durations.size());
    }
    
    private Duration calculateMedian(List<Duration> sortedDurations) {
        if (sortedDurations.isEmpty()) return Duration.ZERO;
        
        int size = sortedDurations.size();
        if (size % 2 == 0) {
            Duration d1 = sortedDurations.get(size / 2 - 1);
            Duration d2 = sortedDurations.get(size / 2);
            return Duration.ofNanos((d1.toNanos() + d2.toNanos()) / 2);
        } else {
            return sortedDurations.get(size / 2);
        }
    }
    
    private Duration calculatePercentile(List<Duration> sortedDurations, double percentile) {
        if (sortedDurations.isEmpty()) return Duration.ZERO;
        
        int index = (int) Math.ceil(percentile * sortedDurations.size()) - 1;
        index = Math.max(0, Math.min(sortedDurations.size() - 1, index));
        
        return sortedDurations.get(index);
    }
    
    /**
     * Request metric record
     */
    public record RequestMetric(
        String requestId,
        Duration responseTime,
        boolean success,
        Instant timestamp
    ) {}
    
    /**
     * Performance health status
     */
    public enum PerformanceHealth {
        HEALTHY,    // All targets met
        DEGRADED,   // Some targets missed but system functional
        CRITICAL    // Major performance issues
    }
}