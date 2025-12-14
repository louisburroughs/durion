package durion.workspace.agents.coordination;

import java.time.Duration;

/**
 * Metrics for agent coordination performance
 */
public class CoordinationMetrics {
    private final String agentId;
    private int totalRequests = 0;
    private int successfulRequests = 0;
    private Duration totalResponseTime = Duration.ZERO;
    
    public CoordinationMetrics(String agentId) {
        this.agentId = agentId;
    }
    
    public synchronized void recordRequest(Duration responseTime, boolean success) {
        totalRequests++;
        if (success) {
            successfulRequests++;
        }
        totalResponseTime = totalResponseTime.plus(responseTime);
    }
    
    public synchronized double getSuccessRate() {
        return totalRequests > 0 ? (double) successfulRequests / totalRequests : 0.0;
    }
    
    public synchronized Duration getAverageResponseTime() {
        return totalRequests > 0 ? totalResponseTime.dividedBy(totalRequests) : Duration.ZERO;
    }
    
    public String getAgentId() { return agentId; }
    public int getTotalRequests() { return totalRequests; }
    public int getSuccessfulRequests() { return successfulRequests; }
}