package durion.workspace.agents.registry;

import java.time.Instant;
import java.util.*;

/**
 * Feature flag for coordinated rollout management across projects
 */
public class FeatureFlag {
    
    public enum Status {
        DISABLED,
        ENABLED,
        GRADUAL_ROLLOUT,
        FULLY_DEPLOYED
    }
    
    private final String featureName;
    private final List<String> affectedProjects;
    private Status status;
    private double rolloutPercentage;
    private final Instant createdAt;
    private Instant lastModified;
    private final Map<String, Object> configuration;
    
    public FeatureFlag(String featureName, List<String> affectedProjects) {
        this.featureName = featureName;
        this.affectedProjects = new ArrayList<>(affectedProjects);
        this.status = Status.DISABLED;
        this.rolloutPercentage = 0.0;
        this.createdAt = Instant.now();
        this.lastModified = Instant.now();
        this.configuration = new HashMap<>();
    }
    
    public void enable(double rolloutPercentage) {
        this.rolloutPercentage = Math.max(0.0, Math.min(100.0, rolloutPercentage));
        this.status = rolloutPercentage >= 100.0 ? Status.FULLY_DEPLOYED : Status.ENABLED;
        this.lastModified = Instant.now();
    }
    
    public void disable() {
        this.status = Status.DISABLED;
        this.rolloutPercentage = 0.0;
        this.lastModified = Instant.now();
    }
    
    public void gradualRollout(double targetPercentage) {
        this.rolloutPercentage = Math.max(0.0, Math.min(100.0, targetPercentage));
        this.status = Status.GRADUAL_ROLLOUT;
        this.lastModified = Instant.now();
    }
    
    public boolean isEnabledForUser(String userId) {
        if (status == Status.DISABLED) {
            return false;
        }
        
        if (status == Status.FULLY_DEPLOYED) {
            return true;
        }
        
        // Simple hash-based rollout
        int hash = Math.abs(userId.hashCode());
        double userPercentile = (hash % 100) + 1; // 1-100
        
        return userPercentile <= rolloutPercentage;
    }
    
    // Getters
    public String getFeatureName() { return featureName; }
    public List<String> getAffectedProjects() { return new ArrayList<>(affectedProjects); }
    public Status getStatus() { return status; }
    public double getRolloutPercentage() { return rolloutPercentage; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastModified() { return lastModified; }
    
    public void setConfiguration(String key, Object value) {
        configuration.put(key, value);
        this.lastModified = Instant.now();
    }
    
    public Object getConfiguration(String key) {
        return configuration.get(key);
    }
    
    public Map<String, Object> getAllConfiguration() {
        return new HashMap<>(configuration);
    }
}