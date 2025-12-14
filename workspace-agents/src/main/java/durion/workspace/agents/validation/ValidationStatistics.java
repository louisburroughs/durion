package durion.workspace.agents.validation;

import java.time.Duration;
import java.util.Collection;

/**
 * Statistics for validation performance across all agents
 */
public class ValidationStatistics {
    
    private final int totalValidations;
    private final int successfulValidations;
    private final Duration averageValidationTime;
    private final double overallValidationRate;
    
    public ValidationStatistics(Collection<ValidationHistory> histories) {
        int totalVal = 0;
        int successfulVal = 0;
        Duration totalTime = Duration.ZERO;
        
        for (ValidationHistory history : histories) {
            totalVal += history.getTotalValidations();
            successfulVal += history.getSuccessfulValidations();
            totalTime = totalTime.plus(history.getAverageValidationTime().multipliedBy(history.getTotalValidations()));
        }
        
        this.totalValidations = totalVal;
        this.successfulValidations = successfulVal;
        this.averageValidationTime = totalVal > 0 ? totalTime.dividedBy(totalVal) : Duration.ZERO;
        this.overallValidationRate = totalVal > 0 ? (double) successfulVal / totalVal : 0.0;
    }
    
    // Getters
    public int getTotalValidations() { return totalValidations; }
    public int getSuccessfulValidations() { return successfulValidations; }
    public Duration getAverageValidationTime() { return averageValidationTime; }
    public double getOverallValidationRate() { return overallValidationRate; }
    
    @Override
    public String toString() {
        return String.format("ValidationStatistics{total=%d, successful=%d, rate=%.1f%%, avgTime=%dms}",
                totalValidations, successfulValidations, overallValidationRate * 100, 
                averageValidationTime.toMillis());
    }
}