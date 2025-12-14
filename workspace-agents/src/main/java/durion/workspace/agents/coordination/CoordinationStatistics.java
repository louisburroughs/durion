package durion.workspace.agents.coordination;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.HashMap;

/**
 * Statistics for agent coordination performance
 */
public class CoordinationStatistics {
    
    private final Map<String, AgentStatistics> agentStats;
    private final int totalCoordinations;
    private final int successfulCoordinations;
    private final Duration averageCoordinationTime;
    private final double overallSuccessRate;
    
    public CoordinationStatistics(Collection<CoordinationMetrics> metrics) {
        this.agentStats = new HashMap<>();
        
        int totalRequests = 0;
        int successfulRequests = 0;
        Duration totalTime = Duration.ZERO;
        
        // Process metrics for each agent
        for (CoordinationMetrics metric : metrics) {
            AgentStatistics stats = new AgentStatistics(
                metric.getAgentId(),
                metric.getTotalRequests(),
                metric.getSuccessfulRequests(),
                metric.getAverageResponseTime(),
                metric.getSuccessRate()
            );
            agentStats.put(metric.getAgentId(), stats);
            
            totalRequests += metric.getTotalRequests();
            successfulRequests += metric.getSuccessfulRequests();
            totalTime = totalTime.plus(metric.getAverageResponseTime().multipliedBy(metric.getTotalRequests()));
        }
        
        this.totalCoordinations = totalRequests;
        this.successfulCoordinations = successfulRequests;
        this.averageCoordinationTime = totalRequests > 0 ? totalTime.dividedBy(totalRequests) : Duration.ZERO;
        this.overallSuccessRate = totalRequests > 0 ? (double) successfulRequests / totalRequests : 0.0;
    }
    
    // Getters
    public Map<String, AgentStatistics> getAgentStats() { return new HashMap<>(agentStats); }
    public int getTotalCoordinations() { return totalCoordinations; }
    public int getSuccessfulCoordinations() { return successfulCoordinations; }
    public Duration getAverageCoordinationTime() { return averageCoordinationTime; }
    public double getOverallSuccessRate() { return overallSuccessRate; }
    
    /**
     * Gets statistics for a specific agent
     */
    public AgentStatistics getAgentStatistics(String agentId) {
        return agentStats.get(agentId);
    }
    
    /**
     * Checks if coordination performance meets requirements (5-second response time)
     */
    public boolean meetsPerformanceRequirements() {
        return averageCoordinationTime.toMillis() <= 5000; // 5 seconds
    }
    
    /**
     * Checks if coordination availability meets requirements (99.9%)
     */
    public boolean meetsAvailabilityRequirements() {
        return overallSuccessRate >= 0.999; // 99.9%
    }
    
    /**
     * Gets the top performing agents by success rate
     */
    public Map<String, Double> getTopPerformingAgents(int limit) {
        return agentStats.entrySet().stream()
            .sorted((e1, e2) -> Double.compare(e2.getValue().getSuccessRate(), e1.getValue().getSuccessRate()))
            .limit(limit)
            .collect(HashMap::new, 
                    (map, entry) -> map.put(entry.getKey(), entry.getValue().getSuccessRate()),
                    HashMap::putAll);
    }
    
    /**
     * Gets agents that need performance optimization
     */
    public Map<String, String> getAgentsNeedingOptimization() {
        Map<String, String> needsOptimization = new HashMap<>();
        
        for (Map.Entry<String, AgentStatistics> entry : agentStats.entrySet()) {
            AgentStatistics stats = entry.getValue();
            
            if (stats.getSuccessRate() < 0.95) { // Less than 95% success rate
                needsOptimization.put(entry.getKey(), 
                    String.format("Low success rate: %.1f%%", stats.getSuccessRate() * 100));
            } else if (stats.getAverageResponseTime().toMillis() > 5000) { // Slower than 5 seconds
                needsOptimization.put(entry.getKey(), 
                    String.format("Slow response time: %d ms", stats.getAverageResponseTime().toMillis()));
            }
        }
        
        return needsOptimization;
    }
    
    @Override
    public String toString() {
        return String.format("CoordinationStatistics{total=%d, successful=%d, successRate=%.1f%%, avgTime=%dms}",
                totalCoordinations, successfulCoordinations, overallSuccessRate * 100, 
                averageCoordinationTime.toMillis());
    }
    
    /**
     * Inner class for individual agent statistics
     */
    public static class AgentStatistics {
        private final String agentId;
        private final int totalRequests;
        private final int successfulRequests;
        private final Duration averageResponseTime;
        private final double successRate;
        
        public AgentStatistics(String agentId, int totalRequests, int successfulRequests,
                             Duration averageResponseTime, double successRate) {
            this.agentId = agentId;
            this.totalRequests = totalRequests;
            this.successfulRequests = successfulRequests;
            this.averageResponseTime = averageResponseTime;
            this.successRate = successRate;
        }
        
        // Getters
        public String getAgentId() { return agentId; }
        public int getTotalRequests() { return totalRequests; }
        public int getSuccessfulRequests() { return successfulRequests; }
        public Duration getAverageResponseTime() { return averageResponseTime; }
        public double getSuccessRate() { return successRate; }
        
        /**
         * Checks if this agent meets performance requirements
         */
        public boolean meetsPerformanceRequirements() {
            return successRate >= 0.95 && averageResponseTime.toMillis() <= 5000;
        }
        
        @Override
        public String toString() {
            return String.format("AgentStatistics{id='%s', requests=%d, successRate=%.1f%%, avgTime=%dms}",
                    agentId, totalRequests, successRate * 100, averageResponseTime.toMillis());
        }
    }
}