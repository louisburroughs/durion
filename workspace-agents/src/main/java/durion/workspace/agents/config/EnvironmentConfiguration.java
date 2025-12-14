package durion.workspace.agents.config;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Configuration specific to deployment environments (development, staging, production)
 */
public class EnvironmentConfiguration {
    
    private final String environmentId;
    private final Map<String, Object> properties;
    private final Instant createdAt;
    private final Instant lastModified;
    
    public EnvironmentConfiguration(String environmentId, Map<String, Object> properties) {
        this.environmentId = Objects.requireNonNull(environmentId, "Environment ID cannot be null");
        this.properties = new HashMap<>(Objects.requireNonNull(properties, "Properties cannot be null"));
        this.createdAt = Instant.now();
        this.lastModified = Instant.now();
    }
    
    private EnvironmentConfiguration(String environmentId, Map<String, Object> properties, 
                                   Instant createdAt, Instant lastModified) {
        this.environmentId = environmentId;
        this.properties = new HashMap<>(properties);
        this.createdAt = createdAt;
        this.lastModified = lastModified;
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
    
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    public Instant getLastModified() {
        return lastModified;
    }
    
    /**
     * Creates a new configuration with updated properties
     */
    public EnvironmentConfiguration withProperty(String key, Object value) {
        Map<String, Object> newProperties = new HashMap<>(this.properties);
        newProperties.put(key, value);
        return new EnvironmentConfiguration(environmentId, newProperties, createdAt, Instant.now());
    }
    
    /**
     * Creates a new configuration with multiple updated properties
     */
    public EnvironmentConfiguration withProperties(Map<String, Object> newProperties) {
        Map<String, Object> merged = new HashMap<>(this.properties);
        merged.putAll(newProperties);
        return new EnvironmentConfiguration(environmentId, merged, createdAt, Instant.now());
    }
    
    /**
     * Gets environment type (development, staging, production)
     */
    public String getEnvironmentType() {
        return (String) getProperty("environment.type", "development");
    }
    
    /**
     * Gets environment name
     */
    public String getEnvironmentName() {
        return (String) getProperty("environment.name", environmentId);
    }
    
    /**
     * Checks if this is a production environment
     */
    public boolean isProduction() {
        return "production".equals(getEnvironmentType());
    }
    
    /**
     * Checks if this is a development environment
     */
    public boolean isDevelopment() {
        return "development".equals(getEnvironmentType());
    }
    
    /**
     * Checks if this is a staging environment
     */
    public boolean isStaging() {
        return "staging".equals(getEnvironmentType());
    }
    
    /**
     * Gets deployment configuration for this environment
     */
    public Map<String, Object> getDeploymentConfiguration() {
        Map<String, Object> deployConfig = new HashMap<>();
        
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            if (entry.getKey().startsWith("deployment.")) {
                deployConfig.put(entry.getKey(), entry.getValue());
            }
        }
        
        return deployConfig;
    }
    
    /**
     * Gets monitoring configuration for this environment
     */
    public Map<String, Object> getMonitoringConfiguration() {
        Map<String, Object> monitoringConfig = new HashMap<>();
        
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            if (entry.getKey().startsWith("monitoring.")) {
                monitoringConfig.put(entry.getKey(), entry.getValue());
            }
        }
        
        return monitoringConfig;
    }
    
    /**
     * Gets logging configuration for this environment
     */
    public Map<String, Object> getLoggingConfiguration() {
        Map<String, Object> loggingConfig = new HashMap<>();
        
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            if (entry.getKey().startsWith("logging.")) {
                loggingConfig.put(entry.getKey(), entry.getValue());
            }
        }
        
        return loggingConfig;
    }
    
    /**
     * Gets security configuration for this environment
     */
    public Map<String, Object> getSecurityConfiguration() {
        Map<String, Object> securityConfig = new HashMap<>();
        
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            if (entry.getKey().startsWith("security.")) {
                securityConfig.put(entry.getKey(), entry.getValue());
            }
        }
        
        return securityConfig;
    }
    
    /**
     * Gets performance configuration adapted for this environment
     */
    public Map<String, Object> getPerformanceConfiguration() {
        Map<String, Object> perfConfig = new HashMap<>();
        
        // Base performance configuration
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            if (entry.getKey().startsWith("performance.")) {
                perfConfig.put(entry.getKey(), entry.getValue());
            }
        }
        
        // Environment-specific adaptations
        String envType = getEnvironmentType();
        switch (envType) {
            case "development":
                perfConfig.put("performance.response.timeout", 3000);
                perfConfig.put("performance.metrics.collection.interval", 30000);
                perfConfig.put("performance.debug.enabled", true);
                break;
                
            case "staging":
                perfConfig.put("performance.response.timeout", 5000);
                perfConfig.put("performance.metrics.collection.interval", 30000);
                perfConfig.put("performance.debug.enabled", false);
                break;
                
            case "production":
                perfConfig.put("performance.response.timeout", 5000);
                perfConfig.put("performance.metrics.collection.interval", 60000);
                perfConfig.put("performance.debug.enabled", false);
                perfConfig.put("performance.optimization.enabled", true);
                break;
        }
        
        return perfConfig;
    }
    
    /**
     * Gets resource limits for this environment
     */
    public ResourceLimits getResourceLimits() {
        String envType = getEnvironmentType();
        
        switch (envType) {
            case "development":
                return new ResourceLimits(
                    (Integer) getProperty("resources.cpu.limit", 2),
                    (Long) getProperty("resources.memory.limit", 2048L * 1024 * 1024), // 2GB
                    (Integer) getProperty("resources.concurrent.requests", 50)
                );
                
            case "staging":
                return new ResourceLimits(
                    (Integer) getProperty("resources.cpu.limit", 4),
                    (Long) getProperty("resources.memory.limit", 4096L * 1024 * 1024), // 4GB
                    (Integer) getProperty("resources.concurrent.requests", 100)
                );
                
            case "production":
                return new ResourceLimits(
                    (Integer) getProperty("resources.cpu.limit", 8),
                    (Long) getProperty("resources.memory.limit", 8192L * 1024 * 1024), // 8GB
                    (Integer) getProperty("resources.concurrent.requests", 200)
                );
                
            default:
                return new ResourceLimits(2, 2048L * 1024 * 1024, 50);
        }
    }
    
    /**
     * Validates the environment configuration
     */
    public ValidationResult validate() {
        // Check required properties
        if (!properties.containsKey("environment.type")) {
            return ValidationResult.invalid("Missing required property: environment.type");
        }
        
        // Validate environment type
        String type = getEnvironmentType();
        if (!type.equals("development") && !type.equals("staging") && !type.equals("production")) {
            return ValidationResult.invalid("Invalid environment type: " + type);
        }
        
        return ValidationResult.valid();
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        EnvironmentConfiguration that = (EnvironmentConfiguration) obj;
        return Objects.equals(environmentId, that.environmentId) &&
               Objects.equals(properties, that.properties);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(environmentId, properties);
    }
    
    @Override
    public String toString() {
        return String.format("EnvironmentConfiguration{id='%s', type='%s', properties=%d}", 
                           environmentId, getEnvironmentType(), properties.size());
    }
    
    // Inner classes
    
    public static class ResourceLimits {
        private final int cpuLimit;
        private final long memoryLimit;
        private final int concurrentRequestLimit;
        
        public ResourceLimits(int cpuLimit, long memoryLimit, int concurrentRequestLimit) {
            this.cpuLimit = cpuLimit;
            this.memoryLimit = memoryLimit;
            this.concurrentRequestLimit = concurrentRequestLimit;
        }
        
        public int getCpuLimit() { return cpuLimit; }
        public long getMemoryLimit() { return memoryLimit; }
        public int getConcurrentRequestLimit() { return concurrentRequestLimit; }
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