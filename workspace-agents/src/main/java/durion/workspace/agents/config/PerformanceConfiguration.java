package durion.workspace.agents.config;

/**
 * Performance-specific configuration extracted from effective configuration
 */
public class PerformanceConfiguration {
    
    private final int responseTimeout;
    private final boolean monitoringEnabled;
    private final int metricsCollectionInterval;
    private final int maxConcurrentRequests;
    private final double cpuThreshold;
    private final double memoryThreshold;
    
    public PerformanceConfiguration(int responseTimeout, boolean monitoringEnabled,
                                  int metricsCollectionInterval, int maxConcurrentRequests,
                                  double cpuThreshold, double memoryThreshold) {
        this.responseTimeout = responseTimeout;
        this.monitoringEnabled = monitoringEnabled;
        this.metricsCollectionInterval = metricsCollectionInterval;
        this.maxConcurrentRequests = maxConcurrentRequests;
        this.cpuThreshold = cpuThreshold;
        this.memoryThreshold = memoryThreshold;
    }
    
    public int getResponseTimeout() { return responseTimeout; }
    public boolean isMonitoringEnabled() { return monitoringEnabled; }
    public int getMetricsCollectionInterval() { return metricsCollectionInterval; }
    public int getMaxConcurrentRequests() { return maxConcurrentRequests; }
    public double getCpuThreshold() { return cpuThreshold; }
    public double getMemoryThreshold() { return memoryThreshold; }
    
    /**
     * Checks if performance configuration meets requirements
     */
    public boolean meetsRequirements() {
        return responseTimeout <= 5000 && // 5-second response time target
               maxConcurrentRequests >= 100 && // Support 100 concurrent users
               cpuThreshold <= 0.9 && // Reasonable CPU threshold
               memoryThreshold <= 0.9; // Reasonable memory threshold
    }
    
    @Override
    public String toString() {
        return String.format("PerformanceConfig{timeout=%dms, monitoring=%s, maxRequests=%d}",
                           responseTimeout, monitoringEnabled, maxConcurrentRequests);
    }
}