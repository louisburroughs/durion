package durion.workspace.agents.config;

import durion.workspace.agents.core.*;
import durion.workspace.agents.monitoring.PerformanceMonitor;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Manages workspace-specific agent configuration and customization.
 * Provides environment-specific agent behavior adaptation for full-stack scenarios.
 * 
 * Requirements: 1.2 - Workspace-specific agent configuration and customization
 */
public class WorkspaceAgentConfigurationManager {
    
    private final Map<String, WorkspaceConfiguration> workspaceConfigurations;
    private final Map<String, EnvironmentConfiguration> environmentConfigurations;
    private final Map<String, AgentConfiguration> agentConfigurations;
    private final Map<String, ConfigurationProfile> configurationProfiles;
    private final ReadWriteLock configLock;
    private final PerformanceMonitor performanceMonitor;
    
    // Configuration statistics
    private final ConfigurationStatistics statistics;
    
    public WorkspaceAgentConfigurationManager(PerformanceMonitor performanceMonitor) {
        this.workspaceConfigurations = new ConcurrentHashMap<>();
        this.environmentConfigurations = new ConcurrentHashMap<>();
        this.agentConfigurations = new ConcurrentHashMap<>();
        this.configurationProfiles = new ConcurrentHashMap<>();
        this.configLock = new ReentrantReadWriteLock();
        this.performanceMonitor = performanceMonitor;
        this.statistics = new ConfigurationStatistics();
        
        initializeDefaultConfigurations();
    }
    
    /**
     * Creates or updates workspace configuration
     */
    public ConfigurationResult createWorkspaceConfiguration(String workspaceId, 
                                                          WorkspaceConfiguration configuration) {
        configLock.writeLock().lock();
        try {
            // Validate configuration
            ValidationResult validation = validateWorkspaceConfiguration(configuration);
            if (!validation.isValid()) {
                return ConfigurationResult.failure("Invalid configuration: " + validation.getErrorMessage());
            }
            
            // Store configuration
            workspaceConfigurations.put(workspaceId, configuration);
            
            // Update statistics
            statistics.recordConfigurationUpdate(workspaceId, "workspace");
            
            return ConfigurationResult.success("Workspace configuration created/updated successfully");
            
        } finally {
            configLock.writeLock().unlock();
        }
    }
    
    /**
     * Creates or updates environment configuration
     */
    public ConfigurationResult createEnvironmentConfiguration(String environmentId, 
                                                            EnvironmentConfiguration configuration) {
        configLock.writeLock().lock();
        try {
            // Validate configuration
            ValidationResult validation = validateEnvironmentConfiguration(configuration);
            if (!validation.isValid()) {
                return ConfigurationResult.failure("Invalid configuration: " + validation.getErrorMessage());
            }
            
            // Store configuration
            environmentConfigurations.put(environmentId, configuration);
            
            // Update statistics
            statistics.recordConfigurationUpdate(environmentId, "environment");
            
            return ConfigurationResult.success("Environment configuration created/updated successfully");
            
        } finally {
            configLock.writeLock().unlock();
        }
    }
    
    /**
     * Creates or updates agent-specific configuration
     */
    public ConfigurationResult createAgentConfiguration(String agentId, AgentConfiguration configuration) {
        configLock.writeLock().lock();
        try {
            // Validate configuration
            ValidationResult validation = validateAgentConfiguration(configuration);
            if (!validation.isValid()) {
                return ConfigurationResult.failure("Invalid configuration: " + validation.getErrorMessage());
            }
            
            // Store configuration
            agentConfigurations.put(agentId, configuration);
            
            // Update statistics
            statistics.recordConfigurationUpdate(agentId, "agent");
            
            return ConfigurationResult.success("Agent configuration created/updated successfully");
            
        } finally {
            configLock.writeLock().unlock();
        }
    }
    
    /**
     * Gets effective configuration for an agent in a specific workspace and environment
     */
    public EffectiveConfiguration getEffectiveConfiguration(String agentId, String workspaceId, 
                                                          String environmentId) {
        configLock.readLock().lock();
        try {
            // Build configuration hierarchy: defaults < workspace < environment < agent
            Map<String, Object> effectiveConfig = new HashMap<>();
            
            // 1. Start with default configuration
            effectiveConfig.putAll(getDefaultConfiguration());
            
            // 2. Apply workspace configuration
            WorkspaceConfiguration workspaceConfig = workspaceConfigurations.get(workspaceId);
            if (workspaceConfig != null) {
                effectiveConfig.putAll(workspaceConfig.getProperties());
            }
            
            // 3. Apply environment configuration
            EnvironmentConfiguration envConfig = environmentConfigurations.get(environmentId);
            if (envConfig != null) {
                effectiveConfig.putAll(envConfig.getProperties());
            }
            
            // 4. Apply agent-specific configuration
            AgentConfiguration agentConfig = agentConfigurations.get(agentId);
            if (agentConfig != null) {
                effectiveConfig.putAll(agentConfig.getProperties());
            }
            
            // 5. Apply environment-specific adaptations
            Map<String, Object> adaptedConfig = applyEnvironmentAdaptations(effectiveConfig, environmentId);
            
            return new EffectiveConfiguration(agentId, workspaceId, environmentId, adaptedConfig);
            
        } finally {
            configLock.readLock().unlock();
        }
    }
    
    /**
     * Creates a configuration profile for reuse
     */
    public ConfigurationResult createConfigurationProfile(String profileId, ConfigurationProfile profile) {
        configLock.writeLock().lock();
        try {
            configurationProfiles.put(profileId, profile);
            statistics.recordProfileCreation(profileId);
            return ConfigurationResult.success("Configuration profile created successfully");
        } finally {
            configLock.writeLock().unlock();
        }
    }
    
    /**
     * Applies a configuration profile to an agent
     */
    public ConfigurationResult applyConfigurationProfile(String agentId, String profileId) {
        configLock.writeLock().lock();
        try {
            ConfigurationProfile profile = configurationProfiles.get(profileId);
            if (profile == null) {
                return ConfigurationResult.failure("Configuration profile not found: " + profileId);
            }
            
            // Create agent configuration from profile
            AgentConfiguration agentConfig = profile.toAgentConfiguration();
            agentConfigurations.put(agentId, agentConfig);
            
            statistics.recordProfileApplication(agentId, profileId);
            return ConfigurationResult.success("Configuration profile applied successfully");
            
        } finally {
            configLock.writeLock().unlock();
        }
    }
    
    /**
     * Gets configuration for performance monitoring and optimization
     */
    public PerformanceConfiguration getPerformanceConfiguration(String agentId, String workspaceId, 
                                                              String environmentId) {
        EffectiveConfiguration effective = getEffectiveConfiguration(agentId, workspaceId, environmentId);
        return extractPerformanceConfiguration(effective);
    }
    
    /**
     * Updates configuration based on performance metrics
     */
    public ConfigurationResult optimizeConfiguration(String agentId, String workspaceId, 
                                                   String environmentId, 
                                                   PerformanceOptimizationRequest request) {
        configLock.writeLock().lock();
        try {
            // Get current effective configuration
            EffectiveConfiguration current = getEffectiveConfiguration(agentId, workspaceId, environmentId);
            
            // Generate optimized configuration
            Map<String, Object> optimizedConfig = generateOptimizedConfiguration(current, request);
            
            // Create new agent configuration with optimizations
            AgentConfiguration newConfig = new AgentConfiguration(agentId, optimizedConfig);
            agentConfigurations.put(agentId, newConfig);
            
            statistics.recordConfigurationOptimization(agentId);
            return ConfigurationResult.success("Configuration optimized successfully");
            
        } finally {
            configLock.writeLock().unlock();
        }
    }
    
    /**
     * Gets all workspace configurations
     */
    public Map<String, WorkspaceConfiguration> getAllWorkspaceConfigurations() {
        configLock.readLock().lock();
        try {
            return new HashMap<>(workspaceConfigurations);
        } finally {
            configLock.readLock().unlock();
        }
    }
    
    /**
     * Gets all environment configurations
     */
    public Map<String, EnvironmentConfiguration> getAllEnvironmentConfigurations() {
        configLock.readLock().lock();
        try {
            return new HashMap<>(environmentConfigurations);
        } finally {
            configLock.readLock().unlock();
        }
    }
    
    /**
     * Gets configuration statistics
     */
    public ConfigurationStatistics getStatistics() {
        return statistics.snapshot();
    }
    
    /**
     * Validates workspace configuration
     */
    private ValidationResult validateWorkspaceConfiguration(WorkspaceConfiguration config) {
        if (config == null) {
            return ValidationResult.invalid("Configuration cannot be null");
        }
        
        // Validate required properties
        Map<String, Object> properties = config.getProperties();
        if (!properties.containsKey("workspace.name")) {
            return ValidationResult.invalid("Missing required property: workspace.name");
        }
        
        // Validate performance settings
        if (properties.containsKey("performance.response.timeout")) {
            try {
                int timeout = (Integer) properties.get("performance.response.timeout");
                if (timeout <= 0 || timeout > 30000) {
                    return ValidationResult.invalid("Invalid response timeout: must be between 1 and 30000ms");
                }
            } catch (Exception e) {
                return ValidationResult.invalid("Invalid response timeout format");
            }
        }
        
        return ValidationResult.valid();
    }
    
    /**
     * Validates environment configuration
     */
    private ValidationResult validateEnvironmentConfiguration(EnvironmentConfiguration config) {
        if (config == null) {
            return ValidationResult.invalid("Configuration cannot be null");
        }
        
        // Validate environment type
        String envType = (String) config.getProperties().get("environment.type");
        if (envType == null || (!envType.equals("development") && !envType.equals("staging") && 
                               !envType.equals("production"))) {
            return ValidationResult.invalid("Invalid environment type: must be development, staging, or production");
        }
        
        return ValidationResult.valid();
    }
    
    /**
     * Validates agent configuration
     */
    private ValidationResult validateAgentConfiguration(AgentConfiguration config) {
        if (config == null) {
            return ValidationResult.invalid("Configuration cannot be null");
        }
        
        // Validate agent ID
        if (config.getAgentId() == null || config.getAgentId().trim().isEmpty()) {
            return ValidationResult.invalid("Agent ID cannot be null or empty");
        }
        
        return ValidationResult.valid();
    }
    
    /**
     * Gets default configuration properties
     */
    private Map<String, Object> getDefaultConfiguration() {
        Map<String, Object> defaults = new HashMap<>();
        
        // Performance defaults
        defaults.put("performance.response.timeout", 5000); // 5 seconds
        defaults.put("performance.monitoring.enabled", true);
        defaults.put("performance.metrics.collection.interval", 60000); // 1 minute
        
        // Logging defaults
        defaults.put("logging.level", "INFO");
        defaults.put("logging.format", "JSON");
        
        // Security defaults
        defaults.put("security.authentication.required", true);
        defaults.put("security.authorization.enabled", true);
        
        // Coordination defaults
        defaults.put("coordination.retry.attempts", 3);
        defaults.put("coordination.retry.delay", 1000);
        
        return defaults;
    }
    
    /**
     * Applies environment-specific adaptations to configuration
     */
    private Map<String, Object> applyEnvironmentAdaptations(Map<String, Object> config, String environmentId) {
        Map<String, Object> adapted = new HashMap<>(config);
        
        EnvironmentConfiguration envConfig = environmentConfigurations.get(environmentId);
        if (envConfig == null) {
            return adapted;
        }
        
        String envType = (String) envConfig.getProperties().get("environment.type");
        
        // Apply environment-specific adaptations
        switch (envType) {
            case "development":
                // More verbose logging in development
                adapted.put("logging.level", "DEBUG");
                // Shorter timeouts for faster feedback
                adapted.put("performance.response.timeout", 3000);
                // More frequent metrics collection
                adapted.put("performance.metrics.collection.interval", 30000);
                break;
                
            case "staging":
                // Production-like settings but with more monitoring
                adapted.put("logging.level", "INFO");
                adapted.put("performance.response.timeout", 5000);
                adapted.put("performance.metrics.collection.interval", 30000);
                break;
                
            case "production":
                // Optimized for performance and stability
                adapted.put("logging.level", "WARN");
                adapted.put("performance.response.timeout", 5000);
                adapted.put("performance.metrics.collection.interval", 60000);
                // Enhanced security in production
                adapted.put("security.audit.enabled", true);
                break;
        }
        
        return adapted;
    }
    
    /**
     * Extracts performance configuration from effective configuration
     */
    private PerformanceConfiguration extractPerformanceConfiguration(EffectiveConfiguration effective) {
        Map<String, Object> config = effective.getProperties();
        
        return new PerformanceConfiguration(
            (Integer) config.getOrDefault("performance.response.timeout", 5000),
            (Boolean) config.getOrDefault("performance.monitoring.enabled", true),
            (Integer) config.getOrDefault("performance.metrics.collection.interval", 60000),
            (Integer) config.getOrDefault("performance.max.concurrent.requests", 100),
            (Double) config.getOrDefault("performance.cpu.threshold", 0.8),
            (Double) config.getOrDefault("performance.memory.threshold", 0.8)
        );
    }
    
    /**
     * Generates optimized configuration based on performance metrics
     */
    private Map<String, Object> generateOptimizedConfiguration(EffectiveConfiguration current, 
                                                             PerformanceOptimizationRequest request) {
        Map<String, Object> optimized = new HashMap<>(current.getProperties());
        
        // Optimize based on performance metrics
        if (request.getAverageResponseTime() > 5000) {
            // Increase timeout if responses are consistently slow
            optimized.put("performance.response.timeout", 
                Math.min(10000, (int) (request.getAverageResponseTime() * 1.2)));
        }
        
        if (request.getCpuUtilization() > 0.8) {
            // Reduce concurrent requests if CPU is high
            int currentMax = (Integer) optimized.getOrDefault("performance.max.concurrent.requests", 100);
            optimized.put("performance.max.concurrent.requests", Math.max(10, currentMax - 10));
        }
        
        if (request.getMemoryUtilization() > 0.8) {
            // Increase metrics collection interval to reduce memory pressure
            int currentInterval = (Integer) optimized.getOrDefault("performance.metrics.collection.interval", 60000);
            optimized.put("performance.metrics.collection.interval", Math.min(300000, currentInterval * 2));
        }
        
        if (request.getErrorRate() > 0.05) {
            // Increase retry attempts if error rate is high
            int currentRetries = (Integer) optimized.getOrDefault("coordination.retry.attempts", 3);
            optimized.put("coordination.retry.attempts", Math.min(5, currentRetries + 1));
        }
        
        return optimized;
    }
    
    /**
     * Initializes default configurations for common scenarios
     */
    private void initializeDefaultConfigurations() {
        // Create default workspace configuration
        Map<String, Object> defaultWorkspaceProps = new HashMap<>();
        defaultWorkspaceProps.put("workspace.name", "default");
        defaultWorkspaceProps.put("workspace.type", "full-stack");
        defaultWorkspaceProps.putAll(getDefaultConfiguration());
        
        WorkspaceConfiguration defaultWorkspace = new WorkspaceConfiguration("default", defaultWorkspaceProps);
        workspaceConfigurations.put("default", defaultWorkspace);
        
        // Create default environment configurations
        createDefaultEnvironmentConfigurations();
        
        // Create default configuration profiles
        createDefaultConfigurationProfiles();
    }
    
    /**
     * Creates default environment configurations
     */
    private void createDefaultEnvironmentConfigurations() {
        // Development environment
        Map<String, Object> devProps = new HashMap<>(getDefaultConfiguration());
        devProps.put("environment.type", "development");
        devProps.put("environment.name", "development");
        EnvironmentConfiguration devConfig = new EnvironmentConfiguration("development", devProps);
        environmentConfigurations.put("development", devConfig);
        
        // Staging environment
        Map<String, Object> stagingProps = new HashMap<>(getDefaultConfiguration());
        stagingProps.put("environment.type", "staging");
        stagingProps.put("environment.name", "staging");
        EnvironmentConfiguration stagingConfig = new EnvironmentConfiguration("staging", stagingProps);
        environmentConfigurations.put("staging", stagingConfig);
        
        // Production environment
        Map<String, Object> prodProps = new HashMap<>(getDefaultConfiguration());
        prodProps.put("environment.type", "production");
        prodProps.put("environment.name", "production");
        EnvironmentConfiguration prodConfig = new EnvironmentConfiguration("production", prodProps);
        environmentConfigurations.put("production", prodConfig);
    }
    
    /**
     * Creates default configuration profiles
     */
    private void createDefaultConfigurationProfiles() {
        // High-performance profile
        Map<String, Object> highPerfProps = new HashMap<>();
        highPerfProps.put("performance.response.timeout", 3000);
        highPerfProps.put("performance.max.concurrent.requests", 200);
        highPerfProps.put("performance.metrics.collection.interval", 30000);
        ConfigurationProfile highPerf = new ConfigurationProfile("high-performance", 
            "High-performance configuration for critical agents", highPerfProps);
        configurationProfiles.put("high-performance", highPerf);
        
        // Low-resource profile
        Map<String, Object> lowResourceProps = new HashMap<>();
        lowResourceProps.put("performance.response.timeout", 10000);
        lowResourceProps.put("performance.max.concurrent.requests", 50);
        lowResourceProps.put("performance.metrics.collection.interval", 120000);
        ConfigurationProfile lowResource = new ConfigurationProfile("low-resource", 
            "Low-resource configuration for constrained environments", lowResourceProps);
        configurationProfiles.put("low-resource", lowResource);
    }
    
    // Inner classes for validation and results
    
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
    
    public static class ConfigurationResult {
        private final boolean success;
        private final String message;
        
        private ConfigurationResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
        
        public static ConfigurationResult success(String message) {
            return new ConfigurationResult(true, message);
        }
        
        public static ConfigurationResult failure(String message) {
            return new ConfigurationResult(false, message);
        }
        
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
    }
}