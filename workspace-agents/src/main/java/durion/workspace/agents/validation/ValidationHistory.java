package durion.workspace.agents.validation;

import java.time.Duration;

/**
 * Tracks validation history for an agent
 */
public class ValidationHistory {
    
    private final String agentId;
    private int totalValidations = 0;
    private int successfulValidations = 0;
    private Duration totalValidationTime = Duration.ZERO;
    
    public ValidationHistory(String agentId) {
        this.agentId = agentId;
    }
    
    public synchronized void recordValidation(Duration validationTime, boolean success) {
        totalValidations++;
        if (success) {
            successfulValidations++;
        }
        totalValidationTime = totalValidationTime.plus(validationTime);
    }
    
    public synchronized double getValidationRate() {
        return totalValidations > 0 ? (double) successfulValidations / totalValidations : 0.0;
    }
    
    public synchronized Duration getAverageValidationTime() {
        return totalValidations > 0 ? totalValidationTime.dividedBy(totalValidations) : Duration.ZERO;
    }
    
    // Getters
    public String getAgentId() { return agentId; }
    public int getTotalValidations() { return totalValidations; }
    public int getSuccessfulValidations() { return successfulValidations; }
}