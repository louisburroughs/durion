package durion.workspace.agents.validation;

/**
 * Configurable thresholds for validation
 */
public class ValidationThresholds {
    
    private int minimumQualityScore = 60;
    private int minimumConsistencyScore = 80;
    private int maximumDriftThreshold = 70;
    private long maximumValidationTimeMs = 2000;
    
    // Getters
    public int getMinimumQualityScore() { return minimumQualityScore; }
    public int getMinimumConsistencyScore() { return minimumConsistencyScore; }
    public int getMaximumDriftThreshold() { return maximumDriftThreshold; }
    public long getMaximumValidationTimeMs() { return maximumValidationTimeMs; }
    
    // Setters
    public void setMinimumQualityScore(int minimumQualityScore) {
        this.minimumQualityScore = minimumQualityScore;
    }
    
    public void setMinimumConsistencyScore(int minimumConsistencyScore) {
        this.minimumConsistencyScore = minimumConsistencyScore;
    }
    
    public void setMaximumDriftThreshold(int maximumDriftThreshold) {
        this.maximumDriftThreshold = maximumDriftThreshold;
    }
    
    public void setMaximumValidationTimeMs(long maximumValidationTimeMs) {
        this.maximumValidationTimeMs = maximumValidationTimeMs;
    }
}