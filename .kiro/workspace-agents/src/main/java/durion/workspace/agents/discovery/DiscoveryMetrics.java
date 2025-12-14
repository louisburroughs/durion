package durion.workspace.agents.discovery;

import java.time.Duration;

/**
 * Metrics for agent discovery performance
 */
public class DiscoveryMetrics {
    
    private final String metricId;
    private int totalDiscoveries = 0;
    private int successfulDiscoveries = 0;
    private Duration totalDiscoveryTime = Duration.ZERO;
    private int totalCandidatesFound = 0;
    
    public DiscoveryMetrics(String metricId) {
        this.metricId = metricId;
    }
    
    public synchronized void recordDiscovery(Duration discoveryTime, boolean success, int candidatesFound) {
        totalDiscoveries++;
        if (success) {
            successfulDiscoveries++;
        }
        totalDiscoveryTime = totalDiscoveryTime.plus(discoveryTime);
        totalCandidatesFound += candidatesFound;
    }
    
    public synchronized double getSuccessRate() {
        return totalDiscoveries > 0 ? (double) successfulDiscoveries / totalDiscoveries : 0.0;
    }
    
    public synchronized Duration getAverageDiscoveryTime() {
        return totalDiscoveries > 0 ? totalDiscoveryTime.dividedBy(totalDiscoveries) : Duration.ZERO;
    }
    
    public synchronized double getAverageCandidatesFound() {
        return totalDiscoveries > 0 ? (double) totalCandidatesFound / totalDiscoveries : 0.0;
    }
    
    // Getters
    public String getMetricId() { return metricId; }
    public int getTotalDiscoveries() { return totalDiscoveries; }
    public int getSuccessfulDiscoveries() { return successfulDiscoveries; }
    public int getTotalCandidatesFound() { return totalCandidatesFound; }
    
    @Override
    public String toString() {
        return String.format("DiscoveryMetrics{id='%s', discoveries=%d, successRate=%.1f%%, avgTime=%dms}",
                metricId, totalDiscoveries, getSuccessRate() * 100, getAverageDiscoveryTime().toMillis());
    }
}