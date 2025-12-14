package durion.workspace.agents.registry;

import durion.workspace.agents.core.AgentType;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Statistics and metrics for the workspace agent registry
 */
public class RegistryStatistics {
    
    private final AtomicInteger totalRegistrations = new AtomicInteger(0);
    private final AtomicInteger totalUnregistrations = new AtomicInteger(0);
    private final AtomicInteger currentAgentCount = new AtomicInteger(0);
    private final AtomicLong totalVersionUpdates = new AtomicLong(0);
    
    private final Map<AgentType, AtomicInteger> registrationsByType = new ConcurrentHashMap<>();
    private final Map<String, VersionUpdateRecord> versionUpdates = new ConcurrentHashMap<>();
    private final List<RegistrationEvent> registrationHistory = Collections.synchronizedList(new ArrayList<>());
    
    private final Instant startTime = Instant.now();
    
    public RegistryStatistics() {
        // Initialize counters for each agent type
        for (AgentType type : AgentType.values()) {
            registrationsByType.put(type, new AtomicInteger(0));
        }
    }
    
    /**
     * Records a new agent registration
     */
    public void recordRegistration(String agentId, AgentType agentType) {
        totalRegistrations.incrementAndGet();
        currentAgentCount.incrementAndGet();
        registrationsByType.get(agentType).incrementAndGet();
        
        RegistrationEvent event = new RegistrationEvent(
            agentId, agentType, RegistrationEvent.Type.REGISTRATION, Instant.now()
        );
        registrationHistory.add(event);
    }
    
    /**
     * Records an agent unregistration
     */
    public void recordUnregistration(String agentId) {
        totalUnregistrations.incrementAndGet();
        currentAgentCount.decrementAndGet();
        
        RegistrationEvent event = new RegistrationEvent(
            agentId, null, RegistrationEvent.Type.UNREGISTRATION, Instant.now()
        );
        registrationHistory.add(event);
    }
    
    /**
     * Records a version update
     */
    public void recordVersionUpdate(String agentId, String oldVersion, String newVersion) {
        totalVersionUpdates.incrementAndGet();
        
        VersionUpdateRecord record = new VersionUpdateRecord(
            agentId, oldVersion, newVersion, Instant.now()
        );
        versionUpdates.put(agentId + "_" + Instant.now().toEpochMilli(), record);
    }
    
    /**
     * Gets total number of registrations since startup
     */
    public int getTotalRegistrations() {
        return totalRegistrations.get();
    }
    
    /**
     * Gets total number of unregistrations since startup
     */
    public int getTotalUnregistrations() {
        return totalUnregistrations.get();
    }
    
    /**
     * Gets current number of registered agents
     */
    public int getCurrentAgentCount() {
        return currentAgentCount.get();
    }
    
    /**
     * Gets total number of version updates
     */
    public long getTotalVersionUpdates() {
        return totalVersionUpdates.get();
    }
    
    /**
     * Gets registration count by agent type
     */
    public int getRegistrationsByType(AgentType agentType) {
        return registrationsByType.get(agentType).get();
    }
    
    /**
     * Gets all registration counts by type
     */
    public Map<AgentType, Integer> getAllRegistrationsByType() {
        Map<AgentType, Integer> result = new HashMap<>();
        for (Map.Entry<AgentType, AtomicInteger> entry : registrationsByType.entrySet()) {
            result.put(entry.getKey(), entry.getValue().get());
        }
        return result;
    }
    
    /**
     * Gets recent version updates (last 100)
     */
    public List<VersionUpdateRecord> getRecentVersionUpdates() {
        return versionUpdates.values().stream()
            .sorted((a, b) -> b.getUpdateTime().compareTo(a.getUpdateTime()))
            .limit(100)
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
    
    /**
     * Gets recent registration events (last 100)
     */
    public List<RegistrationEvent> getRecentRegistrationEvents() {
        synchronized (registrationHistory) {
            return registrationHistory.stream()
                .sorted((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()))
                .limit(100)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        }
    }
    
    /**
     * Gets registry uptime
     */
    public long getUptimeMillis() {
        return Instant.now().toEpochMilli() - startTime.toEpochMilli();
    }
    
    /**
     * Gets average registrations per hour
     */
    public double getAverageRegistrationsPerHour() {
        long uptimeHours = Math.max(1, getUptimeMillis() / (1000 * 60 * 60));
        return (double) totalRegistrations.get() / uptimeHours;
    }
    
    /**
     * Gets registry health metrics
     */
    public RegistryHealthMetrics getHealthMetrics() {
        return new RegistryHealthMetrics(
            currentAgentCount.get(),
            totalRegistrations.get(),
            totalUnregistrations.get(),
            getUptimeMillis(),
            getAverageRegistrationsPerHour()
        );
    }
    
    /**
     * Creates a snapshot of current statistics
     */
    public RegistryStatistics snapshot() {
        RegistryStatistics snapshot = new RegistryStatistics();
        
        // Copy current values
        snapshot.totalRegistrations.set(this.totalRegistrations.get());
        snapshot.totalUnregistrations.set(this.totalUnregistrations.get());
        snapshot.currentAgentCount.set(this.currentAgentCount.get());
        snapshot.totalVersionUpdates.set(this.totalVersionUpdates.get());
        
        // Copy type-specific registrations
        for (Map.Entry<AgentType, AtomicInteger> entry : this.registrationsByType.entrySet()) {
            snapshot.registrationsByType.get(entry.getKey()).set(entry.getValue().get());
        }
        
        // Copy recent events (don't copy all history for performance)
        synchronized (this.registrationHistory) {
            snapshot.registrationHistory.addAll(this.getRecentRegistrationEvents());
        }
        
        snapshot.versionUpdates.putAll(this.versionUpdates);
        
        return snapshot;
    }
    
    /**
     * Resets all statistics (for testing purposes)
     */
    public void reset() {
        totalRegistrations.set(0);
        totalUnregistrations.set(0);
        currentAgentCount.set(0);
        totalVersionUpdates.set(0);
        
        for (AtomicInteger counter : registrationsByType.values()) {
            counter.set(0);
        }
        
        versionUpdates.clear();
        registrationHistory.clear();
    }
    
    // Inner classes for statistics data
    
    public static class RegistrationEvent {
        public enum Type { REGISTRATION, UNREGISTRATION }
        
        private final String agentId;
        private final AgentType agentType;
        private final Type type;
        private final Instant timestamp;
        
        public RegistrationEvent(String agentId, AgentType agentType, Type type, Instant timestamp) {
            this.agentId = agentId;
            this.agentType = agentType;
            this.type = type;
            this.timestamp = timestamp;
        }
        
        public String getAgentId() { return agentId; }
        public AgentType getAgentType() { return agentType; }
        public Type getType() { return type; }
        public Instant getTimestamp() { return timestamp; }
    }
    
    public static class VersionUpdateRecord {
        private final String agentId;
        private final String oldVersion;
        private final String newVersion;
        private final Instant updateTime;
        
        public VersionUpdateRecord(String agentId, String oldVersion, String newVersion, Instant updateTime) {
            this.agentId = agentId;
            this.oldVersion = oldVersion;
            this.newVersion = newVersion;
            this.updateTime = updateTime;
        }
        
        public String getAgentId() { return agentId; }
        public String getOldVersion() { return oldVersion; }
        public String getNewVersion() { return newVersion; }
        public Instant getUpdateTime() { return updateTime; }
    }
    
    public static class RegistryHealthMetrics {
        private final int currentAgentCount;
        private final int totalRegistrations;
        private final int totalUnregistrations;
        private final long uptimeMillis;
        private final double averageRegistrationsPerHour;
        
        public RegistryHealthMetrics(int currentAgentCount, int totalRegistrations, 
                                   int totalUnregistrations, long uptimeMillis, 
                                   double averageRegistrationsPerHour) {
            this.currentAgentCount = currentAgentCount;
            this.totalRegistrations = totalRegistrations;
            this.totalUnregistrations = totalUnregistrations;
            this.uptimeMillis = uptimeMillis;
            this.averageRegistrationsPerHour = averageRegistrationsPerHour;
        }
        
        public int getCurrentAgentCount() { return currentAgentCount; }
        public int getTotalRegistrations() { return totalRegistrations; }
        public int getTotalUnregistrations() { return totalUnregistrations; }
        public long getUptimeMillis() { return uptimeMillis; }
        public double getAverageRegistrationsPerHour() { return averageRegistrationsPerHour; }
        
        public boolean isHealthy() {
            // Registry is healthy if:
            // 1. Has at least some agents registered
            // 2. Registration rate is reasonable
            // 3. Not too many unregistrations compared to registrations
            return currentAgentCount > 0 && 
                   (totalUnregistrations == 0 || totalRegistrations / (double) totalUnregistrations > 2.0);
        }
    }
}