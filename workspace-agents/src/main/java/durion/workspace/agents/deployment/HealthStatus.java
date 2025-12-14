package durion.workspace.agents.deployment;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents the health status of a deployed agent
 */
public class HealthStatus {
    
    private final String agentId;
    private final boolean healthy;
    private final Instant lastChecked;
    private final String errorMessage;
    private final HealthMetrics metrics;
    
    public HealthStatus(String agentId, boolean healthy, Instant lastChecked, String errorMessage) {
        this(agentId, healthy, lastChecked, errorMessage, null);
    }
    
    public HealthStatus(String agentId, boolean healthy, Instant lastChecked, String errorMessage, 
                       HealthMetrics metrics) {
        this.agentId = Objects.requireNonNull(agentId, "Agent ID cannot be null");
        this.healthy = healthy;
        this.lastChecked = Objects.requireNonNull(lastChecked, "Last checked time cannot be null");
        this.errorMessage = errorMessage;
        this.metrics = metrics;
    }
    
    public String getAgentId() {
        return agentId;
    }
    
    public boolean isHealthy() {
        return healthy;
    }
    
    public Instant getLastChecked() {
        return lastChecked;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public HealthMetrics getMetrics() {
        return metrics;
    }
    
    /**
     * Gets time since last health check in milliseconds
     */
    public long getTimeSinceLastCheck() {
        return Instant.now().toEpochMilli() - lastChecked.toEpochMilli();
    }
    
    /**
     * Checks if health status is stale (older than 5 minutes)
     */
    public boolean isStale() {
        return getTimeSinceLastCheck() > 5 * 60 * 1000; // 5 minutes
    }
    
    /**
     * Gets health severity level
     */
    public HealthSeverity getSeverity() {
        if (healthy) {
            return HealthSeverity.HEALTHY;
        } else if (isStale()) {
            return HealthSeverity.CRITICAL;
        } else {
            return HealthSeverity.UNHEALTHY;
        }
    }
    
    /**
     * Creates a new health status with updated information
     */
    public HealthStatus withUpdate(boolean newHealthy, String newErrorMessage) {
        return new HealthStatus(agentId, newHealthy, Instant.now(), newErrorMessage, metrics);
    }
    
    /**
     * Creates a new health status with metrics
     */
    public HealthStatus withMetrics(HealthMetrics newMetrics) {
        return new HealthStatus(agentId, healthy, lastChecked, errorMessage, newMetrics);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        HealthStatus that = (HealthStatus) obj;
        return Objects.equals(agentId, that.agentId) &&
               healthy == that.healthy &&
               Objects.equals(lastChecked, that.lastChecked);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(agentId, healthy, lastChecked);
    }
    
    @Override
    public String toString() {
        return String.format("HealthStatus{agent='%s', healthy=%s, lastChecked=%s, error='%s'}",
                           agentId, healthy, lastChecked, errorMessage);
    }
    
    // Enums and inner classes
    
    public enum HealthSeverity {
        HEALTHY, UNHEALTHY, CRITICAL
    }
    
    public static class HealthMetrics {
        private final double cpuUsage;
        private final double memoryUsage;
        private final long responseTime;
        private final double errorRate;
        private final int activeConnections;
        
        public HealthMetrics(double cpuUsage, double memoryUsage, long responseTime, 
                           double errorRate, int activeConnections) {
            this.cpuUsage = cpuUsage;
            this.memoryUsage = memoryUsage;
            this.responseTime = responseTime;
            this.errorRate = errorRate;
            this.activeConnections = activeConnections;
        }
        
        public double getCpuUsage() { return cpuUsage; }
        public double getMemoryUsage() { return memoryUsage; }
        public long getResponseTime() { return responseTime; }
        public double getErrorRate() { return errorRate; }
        public int getActiveConnections() { return activeConnections; }
        
        /**
         * Checks if metrics indicate healthy performance
         */
        public boolean isHealthy() {
            return cpuUsage < 0.8 &&           // CPU usage < 80%
                   memoryUsage < 0.8 &&        // Memory usage < 80%
                   responseTime < 5000 &&      // Response time < 5 seconds
                   errorRate < 0.05 &&         // Error rate < 5%
                   activeConnections >= 0;     // No negative connections
        }
        
        /**
         * Gets overall health score (0-100)
         */
        public int getHealthScore() {
            int score = 100;
            
            // Deduct points for high resource usage
            if (cpuUsage > 0.8) score -= 20;
            else if (cpuUsage > 0.6) score -= 10;
            
            if (memoryUsage > 0.8) score -= 20;
            else if (memoryUsage > 0.6) score -= 10;
            
            // Deduct points for slow response times
            if (responseTime > 5000) score -= 30;
            else if (responseTime > 3000) score -= 15;
            
            // Deduct points for high error rates
            if (errorRate > 0.05) score -= 20;
            else if (errorRate > 0.02) score -= 10;
            
            return Math.max(0, score);
        }
        
        @Override
        public String toString() {
            return String.format("HealthMetrics{cpu=%.2f%%, memory=%.2f%%, responseTime=%dms, errorRate=%.2f%%, connections=%d}",
                               cpuUsage * 100, memoryUsage * 100, responseTime, errorRate * 100, activeConnections);
        }
    }
}