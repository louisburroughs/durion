package com.durion.core;

import java.util.Map;
import java.util.Properties;

/**
 * Configuration for workspace agents
 */
public class AgentConfiguration {
    private final String agentId;
    private final Properties properties;
    private final Map<String, Object> settings;
    
    public AgentConfiguration(String agentId, Properties properties, Map<String, Object> settings) {
        this.agentId = agentId;
        this.properties = properties;
        this.settings = settings;
    }
    
    public String getAgentId() { return agentId; }
    public Properties getProperties() { return properties; }
    public Map<String, Object> getSettings() { return settings; }
    
    public String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }
    
    @SuppressWarnings("unchecked")
    public <T> T getSetting(String key, T defaultValue) {
        return (T) settings.getOrDefault(key, defaultValue);
    }
    
    public int getMaxConcurrentUsers() {
        return getSetting("maxConcurrentUsers", 100);
    }
    
    public int getResponseTimeoutSeconds() {
        return getSetting("responseTimeoutSeconds", 5);
    }
}
