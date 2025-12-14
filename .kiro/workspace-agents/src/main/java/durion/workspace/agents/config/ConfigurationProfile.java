package durion.workspace.agents.config;

import durion.workspace.agents.core.AgentConfiguration;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Reusable configuration profile that can be applied to multiple agents
 */
public class ConfigurationProfile {
    
    private final String profileId;
    private final String description;
    private final Map<String, Object> properties;
    private final Instant createdAt;
    private final Instant lastModified;
    
    public ConfigurationProfile(String profileId, String description, Map<String, Object> properties) {
        this.profileId = Objects.requireNonNull(profileId, "Profile ID cannot be null");
        this.description = Objects.requireNonNull(description, "Description cannot be null");
        this.properties = new HashMap<>(Objects.requireNonNull(properties, "Properties cannot be null"));
        this.createdAt = Instant.now();
        this.lastModified = Instant.now();
    }
    
    private ConfigurationProfile(String profileId, String description, Map<String, Object> properties,
                               Instant createdAt, Instant lastModified) {
        this.profileId = profileId;
        this.description = description;
        this.properties = new HashMap<>(properties);
        this.createdAt = createdAt;
        this.lastModified = lastModified;
    }
    
    public String getProfileId() {
        return profileId;
    }
    
    public String getDescription() {
        return description;
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
     * Creates a new profile with updated properties
     */
    public ConfigurationProfile withProperty(String key, Object value) {
        Map<String, Object> newProperties = new HashMap<>(this.properties);
        newProperties.put(key, value);
        return new ConfigurationProfile(profileId, description, newProperties, createdAt, Instant.now());
    }
    
    /**
     * Creates a new profile with multiple updated properties
     */
    public ConfigurationProfile withProperties(Map<String, Object> newProperties) {
        Map<String, Object> merged = new HashMap<>(this.properties);
        merged.putAll(newProperties);
        return new ConfigurationProfile(profileId, description, merged, createdAt, Instant.now());
    }
    
    /**
     * Creates a new profile with updated description
     */
    public ConfigurationProfile withDescription(String newDescription) {
        return new ConfigurationProfile(profileId, newDescription, properties, createdAt, Instant.now());
    }
    
    /**
     * Converts this profile to an AgentConfiguration for a specific agent
     */
    public AgentConfiguration toAgentConfiguration(String agentId) {
        return new AgentConfiguration(agentId, properties);
    }
    
    /**
     * Converts this profile to an AgentConfiguration (uses profile ID as agent ID)
     */
    public AgentConfiguration toAgentConfiguration() {
        return toAgentConfiguration(profileId);
    }
    
    /**
     * Merges this profile with another profile, with the other profile taking precedence
     */
    public ConfigurationProfile mergeWith(ConfigurationProfile other) {
        Map<String, Object> merged = new HashMap<>(this.properties);
        merged.putAll(other.properties);
        
        String mergedDescription = this.description + " (merged with " + other.description + ")";
        String mergedId = this.profileId + "-" + other.profileId;
        
        return new ConfigurationProfile(mergedId, mergedDescription, merged);
    }
    
    /**
     * Gets profile type based on properties
     */
    public ProfileType getProfileType() {
        // Determine profile type based on properties
        if (hasPerformanceOptimizations()) {
            return ProfileType.PERFORMANCE;
        } else if (hasSecurityEnhancements()) {
            return ProfileType.SECURITY;
        } else if (hasResourceConstraints()) {
            return ProfileType.RESOURCE_CONSTRAINED;
        } else if (hasDevelopmentSettings()) {
            return ProfileType.DEVELOPMENT;
        } else {
            return ProfileType.GENERAL;
        }
    }
    
    /**
     * Checks if profile has performance optimizations
     */
    private boolean hasPerformanceOptimizations() {
        return properties.containsKey("performance.response.timeout") ||
               properties.containsKey("performance.max.concurrent.requests") ||
               properties.containsKey("performance.optimization.enabled");
    }
    
    /**
     * Checks if profile has security enhancements
     */
    private boolean hasSecurityEnhancements() {
        return properties.containsKey("security.authentication.required") ||
               properties.containsKey("security.authorization.enabled") ||
               properties.containsKey("security.audit.enabled");
    }
    
    /**
     * Checks if profile has resource constraints
     */
    private boolean hasResourceConstraints() {
        return properties.containsKey("resources.cpu.limit") ||
               properties.containsKey("resources.memory.limit") ||
               properties.containsKey("resources.concurrent.requests");
    }
    
    /**
     * Checks if profile has development settings
     */
    private boolean hasDevelopmentSettings() {
        Object logLevel = properties.get("logging.level");
        return "DEBUG".equals(logLevel) || "TRACE".equals(logLevel) ||
               Boolean.TRUE.equals(properties.get("performance.debug.enabled"));
    }
    
    /**
     * Gets performance-related properties from this profile
     */
    public Map<String, Object> getPerformanceProperties() {
        Map<String, Object> perfProps = new HashMap<>();
        
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            if (entry.getKey().startsWith("performance.")) {
                perfProps.put(entry.getKey(), entry.getValue());
            }
        }
        
        return perfProps;
    }
    
    /**
     * Gets security-related properties from this profile
     */
    public Map<String, Object> getSecurityProperties() {
        Map<String, Object> secProps = new HashMap<>();
        
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            if (entry.getKey().startsWith("security.")) {
                secProps.put(entry.getKey(), entry.getValue());
            }
        }
        
        return secProps;
    }
    
    /**
     * Validates the configuration profile
     */
    public ValidationResult validate() {
        // Check that profile has at least one property
        if (properties.isEmpty()) {
            return ValidationResult.invalid("Profile must have at least one property");
        }
        
        // Validate performance properties
        Object timeout = properties.get("performance.response.timeout");
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
        
        // Validate logging level
        Object logLevel = properties.get("logging.level");
        if (logLevel != null) {
            String level = (String) logLevel;
            if (!level.matches("TRACE|DEBUG|INFO|WARN|ERROR")) {
                return ValidationResult.invalid("Invalid logging level: " + level);
            }
        }
        
        return ValidationResult.valid();
    }
    
    /**
     * Creates a summary of the profile
     */
    public ProfileSummary getSummary() {
        return new ProfileSummary(
            profileId,
            description,
            getProfileType(),
            properties.size(),
            createdAt,
            lastModified
        );
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        ConfigurationProfile that = (ConfigurationProfile) obj;
        return Objects.equals(profileId, that.profileId) &&
               Objects.equals(properties, that.properties);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(profileId, properties);
    }
    
    @Override
    public String toString() {
        return String.format("ConfigurationProfile{id='%s', type=%s, properties=%d}",
                           profileId, getProfileType(), properties.size());
    }
    
    // Enums and inner classes
    
    public enum ProfileType {
        GENERAL, PERFORMANCE, SECURITY, RESOURCE_CONSTRAINED, DEVELOPMENT
    }
    
    public static class ProfileSummary {
        private final String profileId;
        private final String description;
        private final ProfileType type;
        private final int propertyCount;
        private final Instant createdAt;
        private final Instant lastModified;
        
        public ProfileSummary(String profileId, String description, ProfileType type,
                            int propertyCount, Instant createdAt, Instant lastModified) {
            this.profileId = profileId;
            this.description = description;
            this.type = type;
            this.propertyCount = propertyCount;
            this.createdAt = createdAt;
            this.lastModified = lastModified;
        }
        
        public String getProfileId() { return profileId; }
        public String getDescription() { return description; }
        public ProfileType getType() { return type; }
        public int getPropertyCount() { return propertyCount; }
        public Instant getCreatedAt() { return createdAt; }
        public Instant getLastModified() { return lastModified; }
        
        @Override
        public String toString() {
            return String.format("ProfileSummary{id='%s', type=%s, properties=%d}",
                               profileId, type, propertyCount);
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