package durion.workspace.agents.config;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Configuration specific to a workspace containing multiple projects
 */
public class WorkspaceConfiguration {
    
    private final String workspaceId;
    private final Map<String, Object> properties;
    private final Instant createdAt;
    private final Instant lastModified;
    
    public WorkspaceConfiguration(String workspaceId, Map<String, Object> properties) {
        this.workspaceId = Objects.requireNonNull(workspaceId, "Workspace ID cannot be null");
        this.properties = new HashMap<>(Objects.requireNonNull(properties, "Properties cannot be null"));
        this.createdAt = Instant.now();
        this.lastModified = Instant.now();
    }
    
    private WorkspaceConfiguration(String workspaceId, Map<String, Object> properties, 
                                 Instant createdAt, Instant lastModified) {
        this.workspaceId = workspaceId;
        this.properties = new HashMap<>(properties);
        this.createdAt = createdAt;
        this.lastModified = lastModified;
    }
    
    public String getWorkspaceId() {
        return workspaceId;
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
    public WorkspaceConfiguration withProperty(String key, Object value) {
        Map<String, Object> newProperties = new HashMap<>(this.properties);
        newProperties.put(key, value);
        return new WorkspaceConfiguration(workspaceId, newProperties, createdAt, Instant.now());
    }
    
    /**
     * Creates a new configuration with multiple updated properties
     */
    public WorkspaceConfiguration withProperties(Map<String, Object> newProperties) {
        Map<String, Object> merged = new HashMap<>(this.properties);
        merged.putAll(newProperties);
        return new WorkspaceConfiguration(workspaceId, merged, createdAt, Instant.now());
    }
    
    /**
     * Creates a new configuration without a specific property
     */
    public WorkspaceConfiguration withoutProperty(String key) {
        Map<String, Object> newProperties = new HashMap<>(this.properties);
        newProperties.remove(key);
        return new WorkspaceConfiguration(workspaceId, newProperties, createdAt, Instant.now());
    }
    
    /**
     * Checks if configuration has a specific property
     */
    public boolean hasProperty(String key) {
        return properties.containsKey(key);
    }
    
    /**
     * Gets workspace name from configuration
     */
    public String getWorkspaceName() {
        return (String) getProperty("workspace.name", workspaceId);
    }
    
    /**
     * Gets workspace type (e.g., "full-stack", "backend-only", "frontend-only")
     */
    public String getWorkspaceType() {
        return (String) getProperty("workspace.type", "full-stack");
    }
    
    /**
     * Gets supported projects in this workspace
     */
    @SuppressWarnings("unchecked")
    public java.util.List<String> getSupportedProjects() {
        Object projects = getProperty("workspace.projects");
        if (projects instanceof java.util.List) {
            return (java.util.List<String>) projects;
        }
        return java.util.Arrays.asList("positivity", "moqui_example");
    }
    
    /**
     * Gets performance configuration for the workspace
     */
    public Map<String, Object> getPerformanceConfiguration() {
        Map<String, Object> perfConfig = new HashMap<>();
        
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            if (entry.getKey().startsWith("performance.")) {
                perfConfig.put(entry.getKey(), entry.getValue());
            }
        }
        
        return perfConfig;
    }
    
    /**
     * Gets security configuration for the workspace
     */
    public Map<String, Object> getSecurityConfiguration() {
        Map<String, Object> secConfig = new HashMap<>();
        
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            if (entry.getKey().startsWith("security.")) {
                secConfig.put(entry.getKey(), entry.getValue());
            }
        }
        
        return secConfig;
    }
    
    /**
     * Validates the workspace configuration
     */
    public ValidationResult validate() {
        // Check required properties
        if (!hasProperty("workspace.name")) {
            return ValidationResult.invalid("Missing required property: workspace.name");
        }
        
        // Validate workspace type
        String type = getWorkspaceType();
        if (!type.equals("full-stack") && !type.equals("backend-only") && !type.equals("frontend-only")) {
            return ValidationResult.invalid("Invalid workspace type: " + type);
        }
        
        // Validate performance settings
        Object timeout = getProperty("performance.response.timeout");
        if (timeout != null) {
            try {
                int timeoutValue = (Integer) timeout;
                if (timeoutValue <= 0 || timeoutValue > 30000) {
                    return ValidationResult.invalid("Invalid response timeout: must be between 1 and 30000ms");
                }
            } catch (ClassCastException e) {
                return ValidationResult.invalid("Invalid response timeout format: must be integer");
            }
        }
        
        return ValidationResult.valid();
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        WorkspaceConfiguration that = (WorkspaceConfiguration) obj;
        return Objects.equals(workspaceId, that.workspaceId) &&
               Objects.equals(properties, that.properties);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(workspaceId, properties);
    }
    
    @Override
    public String toString() {
        return String.format("WorkspaceConfiguration{id='%s', properties=%d, created=%s}", 
                           workspaceId, properties.size(), createdAt);
    }
    
    // Inner class for validation results
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