package durion.workspace.agents.registry;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Statistics for agent dependency loading operations
 */
public class LoadingStatistics {
    
    private final AtomicInteger totalLoadingAttempts = new AtomicInteger(0);
    private final AtomicInteger successfulLoadings = new AtomicInteger(0);
    private final AtomicInteger failedLoadings = new AtomicInteger(0);
    private final AtomicInteger totalUnloadingAttempts = new AtomicInteger(0);
    private final AtomicInteger successfulUnloadings = new AtomicInteger(0);
    private final AtomicInteger failedUnloadings = new AtomicInteger(0);
    
    private final AtomicLong totalAgentsLoaded = new AtomicLong(0);
    private final AtomicLong totalAgentsUnloaded = new AtomicLong(0);
    
    private final Map<String, AgentLoadingRecord> agentLoadingHistory = Collections.synchronizedMap(new HashMap<>());
    private final List<LoadingEvent> loadingEvents = Collections.synchronizedList(new ArrayList<>());
    
    private final Instant startTime = Instant.now();
    
    /**
     * Records a successful loading operation
     */
    public void recordSuccessfulLoading(int requestedAgents, int actuallyLoaded) {
        totalLoadingAttempts.incrementAndGet();
        successfulLoadings.incrementAndGet();
        totalAgentsLoaded.addAndGet(actuallyLoaded);
        
        LoadingEvent event = new LoadingEvent(
            LoadingEvent.Type.LOADING_SUCCESS,
            requestedAgents,
            actuallyLoaded,
            Instant.now()
        );
        loadingEvents.add(event);
    }
    
    /**
     * Records a failed loading operation
     */
    public void recordFailedLoading(int requestedAgents) {
        totalLoadingAttempts.incrementAndGet();
        failedLoadings.incrementAndGet();
        
        LoadingEvent event = new LoadingEvent(
            LoadingEvent.Type.LOADING_FAILURE,
            requestedAgents,
            0,
            Instant.now()
        );
        loadingEvents.add(event);
    }
    
    /**
     * Records a successful unloading operation
     */
    public void recordSuccessfulUnloading(int unloadedAgents) {
        totalUnloadingAttempts.incrementAndGet();
        successfulUnloadings.incrementAndGet();
        totalAgentsUnloaded.addAndGet(unloadedAgents);
        
        LoadingEvent event = new LoadingEvent(
            LoadingEvent.Type.UNLOADING_SUCCESS,
            unloadedAgents,
            unloadedAgents,
            Instant.now()
        );
        loadingEvents.add(event);
    }
    
    /**
     * Records a failed unloading operation
     */
    public void recordFailedUnloading(int requestedUnloads) {
        totalUnloadingAttempts.incrementAndGet();
        failedUnloadings.incrementAndGet();
        
        LoadingEvent event = new LoadingEvent(
            LoadingEvent.Type.UNLOADING_FAILURE,
            requestedUnloads,
            0,
            Instant.now()
        );
        loadingEvents.add(event);
    }
    
    /**
     * Records that a specific agent was loaded
     */
    public void recordAgentLoaded(String agentId) {
        AgentLoadingRecord record = agentLoadingHistory.computeIfAbsent(agentId, 
            k -> new AgentLoadingRecord(agentId));
        record.recordLoaded();
    }
    
    /**
     * Records that a specific agent failed to load
     */
    public void recordAgentLoadFailed(String agentId) {
        AgentLoadingRecord record = agentLoadingHistory.computeIfAbsent(agentId, 
            k -> new AgentLoadingRecord(agentId));
        record.recordLoadFailed();
    }
    
    /**
     * Records that a specific agent was unloaded
     */
    public void recordAgentUnloaded(String agentId) {
        AgentLoadingRecord record = agentLoadingHistory.computeIfAbsent(agentId, 
            k -> new AgentLoadingRecord(agentId));
        record.recordUnloaded();
    }
    
    /**
     * Gets total loading attempts
     */
    public int getTotalLoadingAttempts() {
        return totalLoadingAttempts.get();
    }
    
    /**
     * Gets successful loading count
     */
    public int getSuccessfulLoadings() {
        return successfulLoadings.get();
    }
    
    /**
     * Gets failed loading count
     */
    public int getFailedLoadings() {
        return failedLoadings.get();
    }
    
    /**
     * Gets loading success rate (0.0 to 1.0)
     */
    public double getLoadingSuccessRate() {
        int total = totalLoadingAttempts.get();
        return total > 0 ? (double) successfulLoadings.get() / total : 0.0;
    }
    
    /**
     * Gets total unloading attempts
     */
    public int getTotalUnloadingAttempts() {
        return totalUnloadingAttempts.get();
    }
    
    /**
     * Gets successful unloading count
     */
    public int getSuccessfulUnloadings() {
        return successfulUnloadings.get();
    }
    
    /**
     * Gets failed unloading count
     */
    public int getFailedUnloadings() {
        return failedUnloadings.get();
    }
    
    /**
     * Gets unloading success rate (0.0 to 1.0)
     */
    public double getUnloadingSuccessRate() {
        int total = totalUnloadingAttempts.get();
        return total > 0 ? (double) successfulUnloadings.get() / total : 0.0;
    }
    
    /**
     * Gets total agents loaded
     */
    public long getTotalAgentsLoaded() {
        return totalAgentsLoaded.get();
    }
    
    /**
     * Gets total agents unloaded
     */
    public long getTotalAgentsUnloaded() {
        return totalAgentsUnloaded.get();
    }
    
    /**
     * Gets loading record for a specific agent
     */
    public AgentLoadingRecord getAgentLoadingRecord(String agentId) {
        return agentLoadingHistory.get(agentId);
    }
    
    /**
     * Gets all agent loading records
     */
    public Map<String, AgentLoadingRecord> getAllAgentLoadingRecords() {
        synchronized (agentLoadingHistory) {
            return new HashMap<>(agentLoadingHistory);
        }
    }
    
    /**
     * Gets recent loading events (last 100)
     */
    public List<LoadingEvent> getRecentLoadingEvents() {
        synchronized (loadingEvents) {
            return loadingEvents.stream()
                .sorted((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()))
                .limit(100)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        }
    }
    
    /**
     * Gets uptime in milliseconds
     */
    public long getUptimeMillis() {
        return Instant.now().toEpochMilli() - startTime.toEpochMilli();
    }
    
    /**
     * Gets average loading operations per hour
     */
    public double getAverageLoadingOperationsPerHour() {
        long uptimeHours = Math.max(1, getUptimeMillis() / (1000 * 60 * 60));
        return (double) totalLoadingAttempts.get() / uptimeHours;
    }
    
    /**
     * Gets loading health metrics
     */
    public LoadingHealthMetrics getHealthMetrics() {
        return new LoadingHealthMetrics(
            getLoadingSuccessRate(),
            getUnloadingSuccessRate(),
            getTotalAgentsLoaded(),
            getTotalAgentsUnloaded(),
            getAverageLoadingOperationsPerHour()
        );
    }
    
    /**
     * Creates a snapshot of current statistics
     */
    public LoadingStatistics snapshot() {
        LoadingStatistics snapshot = new LoadingStatistics();
        
        // Copy atomic values
        snapshot.totalLoadingAttempts.set(this.totalLoadingAttempts.get());
        snapshot.successfulLoadings.set(this.successfulLoadings.get());
        snapshot.failedLoadings.set(this.failedLoadings.get());
        snapshot.totalUnloadingAttempts.set(this.totalUnloadingAttempts.get());
        snapshot.successfulUnloadings.set(this.successfulUnloadings.get());
        snapshot.failedUnloadings.set(this.failedUnloadings.get());
        snapshot.totalAgentsLoaded.set(this.totalAgentsLoaded.get());
        snapshot.totalAgentsUnloaded.set(this.totalAgentsUnloaded.get());
        
        // Copy collections
        synchronized (this.agentLoadingHistory) {
            snapshot.agentLoadingHistory.putAll(this.agentLoadingHistory);
        }
        
        synchronized (this.loadingEvents) {
            snapshot.loadingEvents.addAll(this.getRecentLoadingEvents());
        }
        
        return snapshot;
    }
    
    /**
     * Resets all statistics (for testing)
     */
    public void reset() {
        totalLoadingAttempts.set(0);
        successfulLoadings.set(0);
        failedLoadings.set(0);
        totalUnloadingAttempts.set(0);
        successfulUnloadings.set(0);
        failedUnloadings.set(0);
        totalAgentsLoaded.set(0);
        totalAgentsUnloaded.set(0);
        
        agentLoadingHistory.clear();
        loadingEvents.clear();
    }
    
    // Inner classes for statistics data
    
    public static class AgentLoadingRecord {
        private final String agentId;
        private final AtomicInteger loadAttempts = new AtomicInteger(0);
        private final AtomicInteger loadSuccesses = new AtomicInteger(0);
        private final AtomicInteger loadFailures = new AtomicInteger(0);
        private final AtomicInteger unloadCount = new AtomicInteger(0);
        private volatile Instant lastLoaded;
        private volatile Instant lastUnloaded;
        
        public AgentLoadingRecord(String agentId) {
            this.agentId = agentId;
        }
        
        public void recordLoaded() {
            loadAttempts.incrementAndGet();
            loadSuccesses.incrementAndGet();
            lastLoaded = Instant.now();
        }
        
        public void recordLoadFailed() {
            loadAttempts.incrementAndGet();
            loadFailures.incrementAndGet();
        }
        
        public void recordUnloaded() {
            unloadCount.incrementAndGet();
            lastUnloaded = Instant.now();
        }
        
        public String getAgentId() { return agentId; }
        public int getLoadAttempts() { return loadAttempts.get(); }
        public int getLoadSuccesses() { return loadSuccesses.get(); }
        public int getLoadFailures() { return loadFailures.get(); }
        public int getUnloadCount() { return unloadCount.get(); }
        public Instant getLastLoaded() { return lastLoaded; }
        public Instant getLastUnloaded() { return lastUnloaded; }
        
        public double getLoadSuccessRate() {
            int attempts = loadAttempts.get();
            return attempts > 0 ? (double) loadSuccesses.get() / attempts : 0.0;
        }
    }
    
    public static class LoadingEvent {
        public enum Type { LOADING_SUCCESS, LOADING_FAILURE, UNLOADING_SUCCESS, UNLOADING_FAILURE }
        
        private final Type type;
        private final int requestedCount;
        private final int actualCount;
        private final Instant timestamp;
        
        public LoadingEvent(Type type, int requestedCount, int actualCount, Instant timestamp) {
            this.type = type;
            this.requestedCount = requestedCount;
            this.actualCount = actualCount;
            this.timestamp = timestamp;
        }
        
        public Type getType() { return type; }
        public int getRequestedCount() { return requestedCount; }
        public int getActualCount() { return actualCount; }
        public Instant getTimestamp() { return timestamp; }
    }
    
    public static class LoadingHealthMetrics {
        private final double loadingSuccessRate;
        private final double unloadingSuccessRate;
        private final long totalAgentsLoaded;
        private final long totalAgentsUnloaded;
        private final double averageOperationsPerHour;
        
        public LoadingHealthMetrics(double loadingSuccessRate, double unloadingSuccessRate,
                                  long totalAgentsLoaded, long totalAgentsUnloaded,
                                  double averageOperationsPerHour) {
            this.loadingSuccessRate = loadingSuccessRate;
            this.unloadingSuccessRate = unloadingSuccessRate;
            this.totalAgentsLoaded = totalAgentsLoaded;
            this.totalAgentsUnloaded = totalAgentsUnloaded;
            this.averageOperationsPerHour = averageOperationsPerHour;
        }
        
        public double getLoadingSuccessRate() { return loadingSuccessRate; }
        public double getUnloadingSuccessRate() { return unloadingSuccessRate; }
        public long getTotalAgentsLoaded() { return totalAgentsLoaded; }
        public long getTotalAgentsUnloaded() { return totalAgentsUnloaded; }
        public double getAverageOperationsPerHour() { return averageOperationsPerHour; }
        
        public boolean isHealthy() {
            // Loading system is healthy if:
            // 1. High success rates for both loading and unloading
            // 2. Reasonable operation frequency
            return loadingSuccessRate >= 0.95 && 
                   unloadingSuccessRate >= 0.95 &&
                   averageOperationsPerHour > 0;
        }
    }
}