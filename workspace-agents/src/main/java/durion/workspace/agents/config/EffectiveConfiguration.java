package durion.workspace.agents.config;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The effective configuration for an agent after merging workspace, environment, and agent-specific configurations
 */
public class EffectiveConfiguration {
    
    private final String agentId;
    private final String workspaceId;
    private final String environmentId;
    private final Map<String, Object> properties;
    private final Instant computedAt;
    
    public EffectiveConfiguration(String agentId, String workspaceId, String environmentId, 
                                Map<String, Object> properties) {
        this.agentId = Objects.requireNonNull(agentId, "Agent ID cannot be null");
        this.workspaceId = Objects.requireNonNull(workspaceId, "Workspace ID cannot be null");
        this.environmentId = Objects.requireNonNull(environmentId, "Environment ID cannot be null");
        this.properties = new HashMap<>(Objects.requireNonNull(properties, "Properties cannot be null"));
        this.computedAt = Instant.now();
    }
    
    public String getAgentId() {
        return agentId;
    }
    
    public String getWorkspaceId() {
        return workspaceId;
    }
    
    public String getEnvironmentId() {
        return environmentId;
    }
    
    public Map<String, Object> getProperties() {
        return new HashMap<>(properties);
    }
    
    public Object getProperty(String key) {
        return properties.get(key);
    }
    
    public Object getProperty(String key, Object defaultValue) {
        return properties.getOrDefault(key, defaultValue);
    }
    
    public Instant getComputedAt() {
        return computedAt;
    }
    
    /**
     * Gets typed property value
     */
    @SuppressWarnings("unchecked")
    public <T> T getTypedProperty(String key, Class<T> type, T defaultValue) {
        Object value = properties.get(key);
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        return defaultValue;
    }
    
    /**
     * Gets integer property
     */
    public int getIntProperty(String key, int defaultValue) {
        return getTypedProperty(key, Integer.class, defaultValue);
    }
    
    /**
     * Gets boolean property
     */
    public boolean getBooleanProperty(String key, boolean defaultValue) {
        return getTypedProperty(key, Boolean.class, defaultValue);
    }
    
    /**
     * Gets string property
     */
    public String getStringProperty(String key, String defaultValue) {
        return getTypedProperty(key, String.class, defaultValue);
    }
    
    /**
     * Gets double property
     */
    public double getDoubleProperty(String key, double defaultValue) {
        return getTypedProperty(key, Double.class, defaultValue);
    }
    
    /**
     * Gets long property
     */
    public long getLongProperty(String key, long defaultValue) {
        return getTypedProperty(key, Long.class, defaultValue);
    }
    
    /**
     * Checks if configuration has a specific property
     */
    public boolean hasProperty(String key) {
        return properties.containsKey(key);
    }
    
    /**
     * Gets all properties with a specific prefix
     */
    public Map<String, Object> getPropertiesWithPrefix(String prefix) {
        Map<String, Object> filtered = new HashMap<>();
        
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                filtered.put(entry.getKey(), entry.getValue());
            }
        }
        
        return filtered;
    }
    
    /**
     * Gets performance-related properties
     */
    public Map<String, Object> getPerformanceProperties() {
        return getPropertiesWithPrefix("performance.");
    }
    
    /**
     * Gets security-related properties
     */
    public Map<String, Object> getSecurityProperties() {
        return getPropertiesWithPrefix("security.");
    }
    
    /**
     * Gets logging-related properties
     */
    public Map<String, Object> getLoggingProperties() {
        return getPropertiesWithPrefix("logging.");
    }
    
    /**
     * Gets coordination-related properties
     */
    public Map<String, Object> getCoordinationProperties() {
        return getPropertiesWithPrefix("coordination.");
    }
    
    /**
     * Gets monitoring-related properties
     */
    public Map<String, Object> getMonitoringProperties() {
        return getPropertiesWithPrefix("monitoring.");
    }
    
    /**
     * Creates a summary of the configuration for logging/debugging
     */
    public ConfigurationSummary getSummary() {
        return new ConfigurationSummary(
            agentId,
            workspaceId,
            environmentId,
            properties.size(),
            getIntProperty("performance.response.timeout", 5000),
            getBooleanProperty("performance.monitoring.enabled", true),
            getStringProperty("logging.level", "INFO"),
            getBooleanProperty("security.authentication.required", true),
            computedAt
        );
    }
    
    /**
     * Validates that the effective configuration is complete and valid
     */
    public ValidationResult validate() {
        // Check for required properties
        if (!hasProperty("performance.response.timeout")) {
            return ValidationResult.invalid("Missing required property: performance.response.timeout");
        }
        
        // Validate performance timeout
        int timeout = getIntProperty("performance.response.timeout", 0);
        if (timeout <= 0 || timeout > 30000) {
            return ValidationResult.invalid("Invalid performance timeout: " + timeout);
        }
        
        // Validate logging level
        String logLevel = getStringProperty("logging.level", "INFO");
        if (!logLevel.matches("TRACE|DEBUG|INFO|WARN|ERROR")) {
            return ValidationResult.invalid("Invalid logging level: " + logLevel);
        }
        
        return ValidationResult.valid();
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        EffectiveConfiguration that = (EffectiveConfiguration) obj;
        return Objects.equals(agentId, that.agentId) &&
               Objects.equals(workspaceId, that.workspaceId) &&
               Objects.equals(environmentId, that.environmentId) &&
               Objects.equals(properties, that.properties);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(agentId, workspaceId, environmentId, properties);
    }
    
    @Override
    public String toString() {
        return String.format("EffectiveConfiguration{agent='%s', workspace='%s', environment='%s', properties=%d, computed=%s}",
                           agentId, workspaceId, environmentId, properties.size(), computedAt);
    }
    
    // Inner classes
    
    public static class ConfigurationSummary {
        private final String agentId;
        private final String workspaceId;
        private final String environmentId;
        private final int propertyCount;
        private final int responseTimeout;
        private final boolean monitoringEnabled;
        private final String loggingLevel;
        private final boolean authenticationRequired;
        private final Instant computedAt;
        
        public ConfigurationSummary(String agentId, String workspaceId, String environmentId,
                                  int propertyCount, int responseTimeout, boolean monitoringEnabled,
                                  String loggingLevel, boolean authenticationRequired, Instant computedAt) {
            this.agentId = agentId;
            this.workspaceId = workspaceId;
            this.environmentId = environmentId;
            this.propertyCount = propertyCount;
            this.responseTimeout = responseTimeout;
            this.monitoringEnabled = monitoringEnabled;
            this.loggingLevel = loggingLevel;
            this.authenticationRequired = authenticationRequired;
            this.computedAt = computedAt;
        }
        
        // Getters
        public String getAgentId() { return agentId; }
        public String getWorkspaceId() { return workspaceId; }
        public String getEnvironmentId() { return environmentId; }
        public int getPropertyCount() { return propertyCount; }
        public int getResponseTimeout() { return responseTimeout; }
        public boolean isMonitoringEnabled() { return monitoringEnabled; }
        public String getLoggingLevel() { return loggingLevel; }
        public boolean isAuthenticationRequired() { return authenticationRequired; }
        public Instant getComputedAt() { return computedAt; }
        
        @Override
        public String toString() {
            return String.format("ConfigSummary{agent='%s', timeout=%dms, monitoring=%s, logging=%s}",
                               agentId, responseTimeout, monitoringEnabled, loggingLevel);
        }
    }
    
    public static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;
        
        private ValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }
        
        public static ValidationResult valid() {
            return new ValidationResult(true, null);
        }
        
        public static ValidationResult invalid(String errorMessage) {
            return new ValidationResult(false, errorMessage);
        }
        
        public boolean isValid() { return valid; }
        public String getErrorMessage() { return errorMessage; }
    }
}