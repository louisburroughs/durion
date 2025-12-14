package durion.workspace.agents.config;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Statistics for configuration management operations
 */
public class ConfigurationStatistics {
    
    private final AtomicInteger totalConfigurationUpdates = new AtomicInteger(0);
    private final AtomicInteger totalProfileCreations = new AtomicInteger(0);
    private final AtomicInteger totalProfileApplications = new AtomicInteger(0);
    private final AtomicInteger totalOptimizations = new AtomicInteger(0);
    
    private final Map<String, AtomicInteger> updatesByType = new ConcurrentHashMap<>();
    private final Map<String, ConfigurationEvent> recentEvents = new ConcurrentHashMap<>();
    private final List<ConfigurationEvent> eventHistory = Collections.synchronizedList(new ArrayList<>());
    
    private final Instant startTime = Instant.now();
    
    public ConfigurationStatistics() {
        // Initialize counters for different configuration types
        updatesByType.put("workspace", new AtomicInteger(0));
        updatesByType.put("environment", new AtomicInteger(0));
        updatesByType.put("agent", new AtomicInteger(0));
    }
    
    /**
     * Records a configuration update
     */
    public void recordConfigurationUpdate(String configId, String configurationType) {
        totalConfigurationUpdates.incrementAndGet();
        updatesByType.get(configurationType).incrementAndGet();
        
        ConfigurationEvent event = new ConfigurationEvent(
            ConfigurationEvent.Type.CONFIGURATION_UPDATE,
            configId,
            configurationType,
            Instant.now()
        );
        
        recentEvents.put(configId, event);
        eventHistory.add(event);
    }
    
    /**
     * Records a profile creation
     */
    public void recordProfileCreation(String profileId) {
        totalProfileCreations.incrementAndGet();
        
        ConfigurationEvent event = new ConfigurationEvent(
            ConfigurationEvent.Type.PROFILE_CREATION,
            profileId,
            "profile",
            Instant.now()
        );
        
        recentEvents.put(profileId, event);
        eventHistory.add(event);
    }
    
    /**
     * Records a profile application
     */
    public void recordProfileApplication(String agentId, String profileId) {
        totalProfileApplications.incrementAndGet();
        
        ConfigurationEvent event = new ConfigurationEvent(
            ConfigurationEvent.Type.PROFILE_APPLICATION,
            agentId + "->" + profileId,
            "profile-application",
            Instant.now()
        );
        
        recentEvents.put(agentId, event);
        eventHistory.add(event);
    }
    
    /**
     * Records a configuration optimization
     */
    public void recordConfigurationOptimization(String agentId) {
        totalOptimizations.incrementAndGet();
        
        ConfigurationEvent event = new ConfigurationEvent(
            ConfigurationEvent.Type.OPTIMIZATION,
            agentId,
            "optimization",
            Instant.now()
        );
        
        recentEvents.put(agentId, event);
        eventHistory.add(event);
    }
    
    /**
     * Gets total configuration updates
     */
    public int getTotalConfigurationUpdates() {
        return totalConfigurationUpdates.get();
    }
    
    /**
     * Gets total profile creations
     */
    public int getTotalProfileCreations() {
        return totalProfileCreations.get();
    }
    
    /**
     * Gets total profile applications
     */
    public int getTotalProfileApplications() {
        return totalProfileApplications.get();
    }
    
    /**
     * Gets total optimizations
     */
    public int getTotalOptimizations() {
        return totalOptimizations.get();
    }
    
    /**
     * Gets updates by configuration type
     */
    public int getUpdatesByType(String configurationType) {
        AtomicInteger counter = updatesByType.get(configurationType);
        return counter != null ? counter.get() : 0;
    }
    
    /**
     * Gets all updates by type
     */
    public Map<String, Integer> getAllUpdatesByType() {
        Map<String, Integer> result = new HashMap<>();
        for (Map.Entry<String, AtomicInteger> entry : updatesByType.entrySet()) {
            result.put(entry.getKey(), entry.getValue().get());
        }
        return result;
    }
    
    /**
     * Gets recent configuration events (last 100)
     */
    public List<ConfigurationEvent> getRecentEvents() {
        synchronized (eventHistory) {
            return eventHistory.stream()
                .sorted((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()))
                .limit(100)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        }
    }
    
    /**
     * Gets recent event for a specific configuration
     */
    public ConfigurationEvent getRecentEvent(String configId) {
        return recentEvents.get(configId);
    }
    
    /**
     * Gets uptime in milliseconds
     */
    public long getUptimeMillis() {
        return Instant.now().toEpochMilli() - startTime.toEpochMilli();
    }
    
    /**
     * Gets average configuration operations per hour
     */
    public double getAverageOperationsPerHour() {
        long uptimeHours = Math.max(1, getUptimeMillis() / (1000 * 60 * 60));
        int totalOperations = totalConfigurationUpdates.get() + totalProfileCreations.get() + 
                            totalProfileApplications.get() + totalOptimizations.get();
        return (double) totalOperations / uptimeHours;
    }
    
    /**
     * Gets configuration health metrics
     */
    public ConfigurationHealthMetrics getHealthMetrics() {
        return new ConfigurationHealthMetrics(
            getTotalConfigurationUpdates(),
            getTotalProfileCreations(),
            getTotalProfileApplications(),
            getTotalOptimizations(),
            getAverageOperationsPerHour(),
            getUptimeMillis()
        );
    }
    
    /**
     * Creates a snapshot of current statistics
     */
    public ConfigurationStatistics snapshot() {
        ConfigurationStatistics snapshot = new ConfigurationStatistics();
        
        // Copy atomic values
        snapshot.totalConfigurationUpdates.set(this.totalConfigurationUpdates.get());
        snapshot.totalProfileCreations.set(this.totalProfileCreations.get());
        snapshot.totalProfileApplications.set(this.totalProfileApplications.get());
        snapshot.totalOptimizations.set(this.totalOptimizations.get());
        
        // Copy type-specific counters
        for (Map.Entry<String, AtomicInteger> entry : this.updatesByType.entrySet()) {
            snapshot.updatesByType.get(entry.getKey()).set(entry.getValue().get());
        }
        
        // Copy recent events
        snapshot.recentEvents.putAll(this.recentEvents);
        
        synchronized (this.eventHistory) {
            snapshot.eventHistory.addAll(this.getRecentEvents());
        }
        
        return snapshot;
    }
    
    /**
     * Resets all statistics (for testing)
     */
    public void reset() {
        totalConfigurationUpdates.set(0);
        totalProfileCreations.set(0);
        totalProfileApplications.set(0);
        totalOptimizations.set(0);
        
        for (AtomicInteger counter : updatesByType.values()) {
            counter.set(0);
        }
        
        recentEvents.clear();
        eventHistory.clear();
    }
    
    // Inner classes for statistics data
    
    public static class ConfigurationEvent {
        public enum Type { 
            CONFIGURATION_UPDATE, PROFILE_CREATION, PROFILE_APPLICATION, OPTIMIZATION 
        }
        
        private final Type type;
        private final String configId;
        private final String configurationType;
        private final Instant timestamp;
        
        public ConfigurationEvent(Type type, String configId, String configurationType, Instant timestamp) {
            this.type = type;
            this.configId = configId;
            this.configurationType = configurationType;
            this.timestamp = timestamp;
        }
        
        public Type getType() { return type; }
        public String getConfigId() { return configId; }
        public String getConfigurationType() { return configurationType; }
        public Instant getTimestamp() { return timestamp; }
        
        @Override
        public String toString() {
            return String.format("ConfigEvent{type=%s, id='%s', configType='%s', time=%s}",
                               type, configId, configurationType, timestamp);
        }
    }
    
    public static class ConfigurationHealthMetrics {
        private final int totalUpdates;
        private final int totalProfileCreations;
        private final int totalProfileApplications;
        private final int totalOptimizations;
        private final double averageOperationsPerHour;
        private final long uptimeMillis;
        
        public ConfigurationHealthMetrics(int totalUpdates, int totalProfileCreations,
                                        int totalProfileApplications, int totalOptimizations,
                                        double averageOperationsPerHour, long uptimeMillis) {
            this.totalUpdates = totalUpdates;
            this.totalProfileCreations = totalProfileCreations;
            this.totalProfileApplications = totalProfileApplications;
            this.totalOptimizations = totalOptimizations;
            this.averageOperationsPerHour = averageOperationsPerHour;
            this.uptimeMillis = uptimeMillis;
        }
        
        public int getTotalUpdates() { return totalUpdates; }
        public int getTotalProfileCreations() { return totalProfileCreations; }
        public int getTotalProfileApplications() { return totalProfileApplications; }
        public int getTotalOptimizations() { return totalOptimizations; }
        public double getAverageOperationsPerHour() { return averageOperationsPerHour; }
        public long getUptimeMillis() { return uptimeMillis; }
        
        public boolean isHealthy() {
            // Configuration system is healthy if:
            // 1. Has reasonable activity levels
            // 2. Optimizations are being performed when needed
            return averageOperationsPerHour > 0 && 
                   (totalOptimizations == 0 || totalUpdates / (double) totalOptimizations < 10);
        }
        
        @Override
        public String toString() {
            return String.format("ConfigHealthMetrics{updates=%d, profiles=%d, applications=%d, optimizations=%d}",
                               totalUpdates, totalProfileCreations, totalProfileApplications, totalOptimizations);
        }
    }
}