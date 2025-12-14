package durion.workspace.agents.config;

/**
 * Request for performance optimization based on current metrics
 */
public class PerformanceOptimizationRequest {
    
    private final String agentId;
    private final double averageResponseTime;
    private final double cpuUtilization;
    private final double memoryUtilization;
    private final double errorRate;
    private final int currentConcurrentRequests;
    private final String optimizationGoal;
    
    public PerformanceOptimizationRequest(String agentId, double averageResponseTime,
                                        double cpuUtilization, double memoryUtilization,
                                        double errorRate, int currentConcurrentRequests,
                                        String optimizationGoal) {
        this.agentId = agentId;
        this.averageResponseTime = averageResponseTime;
        this.cpuUtilization = cpuUtilization;
        this.memoryUtilization = memoryUtilization;
        this.errorRate = errorRate;
        this.currentConcurrentRequests = currentConcurrentRequests;
        this.optimizationGoal = optimizationGoal;
    }
    
    public String getAgentId() { return agentId; }
    public double getAverageResponseTime() { return averageResponseTime; }
    public double getCpuUtilization() { return cpuUtilization; }
    public double getMemoryUtilization() { return memoryUtilization; }
    public double getErrorRate() { return errorRate; }
    public int getCurrentConcurrentRequests() { return currentConcurrentRequests; }
    public String getOptimizationGoal() { return optimizationGoal; }
    
    /**
     * Determines if optimization is needed based on thresholds
     */
    public boolean needsOptimization() {
        return averageResponseTime > 5000 || // Response time target
               cpuUtilization > 0.8 ||       // CPU threshold
               memoryUtilization > 0.8 ||    // Memory threshold
               errorRate > 0.05;             // Error rate threshold
    }
    
    /**
     * Gets optimization priority (1-5, with 5 being highest)
     */
    public int getOptimizationPriority() {
        int priority = 1;
        
        if (averageResponseTime > 10000) priority += 2;
        else if (averageResponseTime > 5000) priority += 1;
        
        if (cpuUtilization > 0.9) priority += 2;
        else if (cpuUtilization > 0.8) priority += 1;
        
        if (memoryUtilization > 0.9) priority += 2;
        else if (memoryUtilization > 0.8) priority += 1;
        
        if (errorRate > 0.1) priority += 2;
        else if (errorRate > 0.05) priority += 1;
        
        return Math.min(5, priority);
    }
    
    @Override
    public String toString() {
        return String.format("OptimizationRequest{agent='%s', responseTime=%.2fms, cpu=%.2f%%, memory=%.2f%%, errors=%.2f%%}",
                           agentId, averageResponseTime, cpuUtilization * 100, memoryUtilization * 100, errorRate * 100);
    }
}