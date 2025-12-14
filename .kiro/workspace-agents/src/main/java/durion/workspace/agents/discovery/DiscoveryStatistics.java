package durion.workspace.agents.discovery;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.HashMap;

/**
 * Statistics for agent discovery performance across all capabilities
 */
public class DiscoveryStatistics {
    
    private final Map<String, DiscoveryMetrics> metricsByCapability;
    private final int totalDiscoveries;
    private final int successfulDiscoveries;
    private final Duration averageDiscoveryTime;
    private final double overallSuccessRate;
    private final double averageCandidatesFound;
    
    public DiscoveryStatistics(Collection<DiscoveryMetrics> metrics) {
        this.metricsByCapability = new HashMap<>();
        
        int totalDisc = 0;
        int successfulDisc = 0;
        Duration totalTime = Duration.ZERO;
        int totalCandidates = 0;
        
        for (DiscoveryMetrics metric : metrics) {
            metricsByCapability.put(metric.getMetricId(), metric);
            
            totalDisc += metric.getTotalDiscoveries();
            successfulDisc += metric.getSuccessfulDiscoveries();
            totalTime = totalTime.plus(metric.getAverageDiscoveryTime().multipliedBy(metric.getTotalDiscoveries()));
            totalCandidates += metric.getTotalCandidatesFound();
        }
        
        this.totalDiscoveries = totalDisc;
        this.successfulDiscoveries = successfulDisc;
        this.averageDiscoveryTime = totalDisc > 0 ? totalTime.dividedBy(totalDisc) : Duration.ZERO;
        this.overallSuccessRate = totalDisc > 0 ? (double) successfulDisc / totalDisc : 0.0;
        this.averageCandidatesFound = totalDisc > 0 ? (double) totalCandidates / totalDisc : 0.0;
    }
    
    // Getters
    public Map<String, DiscoveryMetrics> getMetricsByCapability() { return new HashMap<>(metricsByCapability); }
    public int getTotalDiscoveries() { return totalDiscoveries; }
    public int getSuccessfulDiscoveries() { return successfulDiscoveries; }
    public Duration getAverageDiscoveryTime() { return averageDiscoveryTime; }
    public double getOverallSuccessRate() { return overallSuccessRate; }
    public double getAverageCandidatesFound() { return averageCandidatesFound; }
    
    /**
     * Gets metrics for a specific capability
     */
    public DiscoveryMetrics getMetricsForCapability(String capability) {
        return metricsByCapability.get(capability);
    }
    
    /**
     * Checks if discovery performance meets requirements
     */
    public boolean meetsPerformanceRequirements() {
        return averageDiscoveryTime.toMillis() <= 1000 && // 1 second for discovery
               overallSuccessRate >= 0.99; // 99% success rate
    }
    
    /**
     * Gets capabilities with poor discovery performance
     */
    public Map<String, String> getCapabilitiesNeedingImprovement() {
        Map<String, String> needsImprovement = new HashMap<>();
        
        for (Map.Entry<String, DiscoveryMetrics> entry : metricsByCapability.entrySet()) {
            DiscoveryMetrics metrics = entry.getValue();
            
            if (metrics.getSuccessRate() < 0.95) {
                needsImprovement.put(entry.getKey(), 
                    String.format("Low success rate: %.1f%%", metrics.getSuccessRate() * 100));
            } else if (metrics.getAverageDiscoveryTime().toMillis() > 1000) {
                needsImprovement.put(entry.getKey(), 
                    String.format("Slow discovery: %d ms", metrics.getAverageDiscoveryTime().toMillis()));
            } else if (metrics.getAverageCandidatesFound() < 1.0) {
                needsImprovement.put(entry.getKey(), 
                    String.format("Few candidates found: %.1f avg", metrics.getAverageCandidatesFound()));
            }
        }
        
        return needsImprovement;
    }
    
    @Override
    public String toString() {
        return String.format("DiscoveryStatistics{total=%d, successful=%d, successRate=%.1f%%, avgTime=%dms, avgCandidates=%.1f}",
                totalDiscoveries, successfulDiscoveries, overallSuccessRate * 100, 
                averageDiscoveryTime.toMillis(), averageCandidatesFound);
    }
}