package durion.workspace.agents.deployment;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Options for restore operations
 */
public class RestoreOptions {
    private final String backupId;
    private final Duration rpoLimit; // Recovery Point Objective
    private final boolean validateIntegrity;
    private final Map<String, Object> customOptions;
    
    public RestoreOptions(String backupId) {
        this.backupId = backupId;
        this.rpoLimit = Duration.ofHours(1); // Default 1 hour
        this.validateIntegrity = true;
        this.customOptions = new HashMap<>();
    }
    
    public RestoreOptions(String backupId, Duration rpoLimit, boolean validateIntegrity) {
        this.backupId = backupId;
        this.rpoLimit = rpoLimit;
        this.validateIntegrity = validateIntegrity;
        this.customOptions = new HashMap<>();
    }
    
    public String getBackupId() {
        return backupId;
    }
    
    public Duration getRpoLimit() {
        return rpoLimit;
    }
    
    public boolean isValidateIntegrity() {
        return validateIntegrity;
    }
    
    public Map<String, Object> getCustomOptions() {
        return customOptions;
    }
    
    public void setCustomOption(String key, Object value) {
        customOptions.put(key, value);
    }
    
    public Object getCustomOption(String key) {
        return customOptions.get(key);
    }
}
