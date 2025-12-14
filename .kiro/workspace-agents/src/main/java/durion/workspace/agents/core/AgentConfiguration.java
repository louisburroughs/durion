package durion.workspace.agents.core;

import java.time.Duration;
import java.util.Map;
import java.util.HashMap;

/**
 * Configuration for workspace agents
 */
public class AgentConfiguration {
    
    private final String agentId;
    private final Map<String, Object> properties;
    private Duration responseTimeout;
    private int maxConcurrentRequests;
    private boolean enablePerformanceMonitoring;
    
    public AgentConfiguration(String agentId) {
        this.agentId = agentId;
        this.properties = new HashMap<>();
        this.responseTimeout = Duration.ofSeconds(5); // Default 5-second target
        this.maxConcurrentRequests = 10;
        this.enablePerformanceMonitoring = true;
    }
    
    // Getters and setters
    public String getAgentId() { return agentId; }
    
    public Duration getResponseTimeout() { return responseTimeout; }
    public void setResponseTimeout(Duration timeout) { this.responseTimeout = timeout; }
    
    public int getMaxConcurrentRequests() { return maxConcurrentRequests; }
    public void setMaxConcurrentRequests(int max) { this.maxConcurrentRequests = max; }
    
    public boolean isPerformanceMonitoringEnabled() { return enablePerformanceMonitoring; }
    public void setPerformanceMonitoringEnabled(boolean enabled) { this.enablePerformanceMonitoring = enabled; }
    
    // Property management
    public void setProperty(String key, Object value) {
        properties.put(key, value);
    }
    
    public Object getProperty(String key) {
        return properties.get(key);
    }
    
    public <T> T getProperty(String key, Class<T> type, T defaultValue) {
        Object value = properties.get(key);
        if (value != null && type.isInstance(value)) {
            return type.cast(value);
        }
        return defaultValue;
    }
    
    public Map<String, Object> getAllProperties() {
        return new HashMap<>(properties);
    }
}