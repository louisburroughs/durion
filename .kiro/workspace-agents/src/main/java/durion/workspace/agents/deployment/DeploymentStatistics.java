package durion.workspace.agents.deployment;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Statistics for deployment operations and performance monitoring
 */
public class DeploymentStatistics {
    
    private final AtomicInteger totalPackagingAttempts = new AtomicInteger(0);
    private final AtomicInteger successfulPackaging = new AtomicInteger(0);
    private final AtomicInteger totalDeploymentAttempts = new AtomicInteger(0);
    private final AtomicInteger successfulDeployments = new AtomicInteger(0);
    private final AtomicInteger totalUpdateAttempts = new AtomicInteger(0);
    private final AtomicInteger successfulUpdates = new AtomicInteger(0);
    private final AtomicInteger totalUninstallAttempts = new AtomicInteger(0);
    private final AtomicInteger successfulUninstalls = new AtomicInteger(0);
    
    private final AtomicLong totalDeploymentTime = new AtomicLong(0);
    private final AtomicLong totalUpdateTime = new AtomicLong(0);
    
    private final Map<String, DeploymentEvent> recentEvents = Collections.synchronizedMap(new HashMap<>());
    private final List<DeploymentEvent> eventHistory = Collections.synchronizedList(new ArrayList<>());
    
    private final Instant startTime = Instant.now();
    
    /**
     * Records a packaging operation
     */
    public void recordPackaging(String agentId, boolean success) {
        totalPackagingAttempts.incrementAndGet();
        if (success) {
            successfulPackaging.incrementAndGet();
        }
        
        DeploymentEvent event = new DeploymentEvent(
            DeploymentEvent.Type.PACKAGING,
            agentId,
            success,
            Instant.now(),
            null
        );
        
        recentEvents.put(agentId + "-packaging", event);
        eventHistory.add(event);
    }
    
    /**
     * Records a deployment operation
     */
    public void recordDeployment(String agentId, boolean success) {
        totalDeploymentAttempts.incrementAndGet();
        if (success) {
            successfulDeployments.incrementAndGet();
        }
        
        DeploymentEvent event = new DeploymentEvent(
            DeploymentEvent.Type.DEPLOYMENT,
            agentId,
            success,
            Instant.now(),
            null
        );
        
        recentEvents.put(agentId + "-deployment", event);
        eventHistory.add(event);
    }
    
    /**
     * Records an update operation
     */
    public void recordUpdate(String agentId, boolean success) {
        totalUpdateAttempts.incrementAndGet();
        if (success) {
            successfulUpdates.incrementAndGet();
        }
        
        DeploymentEvent event = new DeploymentEvent(
            DeploymentEvent.Type.UPDATE,
            agentId,
            success,
            Instant.now(),
            null
        );
        
        recentEvents.put(agentId + "-update", event);
        eventHistory.add(event);
    }
    
    /**
     * Records an uninstall operation
     */
    public void recordUninstall(String agentId, boolean success) {
        totalUninstallAttempts.incrementAndGet();
        if (success) {
            successfulUninstalls.incrementAndGet();
        }
        
        DeploymentEvent event = new DeploymentEvent(
            DeploymentEvent.Type.UNINSTALL,
            agentId,
            success,
            Instant.now(),
            null
        );
        
        recentEvents.put(agentId + "-uninstall", event);
        eventHistory.add(event);
    }
    
    /**
     * Records deployment timing
     */
    public void recordDeploymentTime(Duration deploymentTime) {
        totalDeploymentTime.addAndGet(deploymentTime.toMillis());
    }
    
    /**
     * Records update timing
     */
    public void recordUpdateTime(Duration updateTime) {
        totalUpdateTime.addAndGet(updateTime.toMillis());
    }
    
    /**
     * Gets packaging success rate
     */
    public double getPackagingSuccessRate() {
        int total = totalPackagingAttempts.get();
        return total > 0 ? (double) successfulPackaging.get() / total : 0.0;
    }
    
    /**
     * Gets deployment success rate
     */
    public double getDeploymentSuccessRate() {
        int total = totalDeploymentAttempts.get();
        return total > 0 ? (double) successfulDeployments.get() / total : 0.0;
    }
    
    /**
     * Gets update success rate
     */
    public double getUpdateSuccessRate() {
        int total = totalUpdateAttempts.get();
        return total > 0 ? (double) successfulUpdates.get() / total : 0.0;
    }
    
    /**
     * Gets uninstall success rate
     */
    public double getUninstallSuccessRate() {
        int total = totalUninstallAttempts.get();
        return total > 0 ? (double) successfulUninstalls.get() / total : 0.0;
    }
    
    /**
     * Gets average deployment time
     */
    public Duration getAverageDeploymentTime() {
        int deployments = successfulDeployments.get();
        if (deployments == 0) return Duration.ZERO;
        
        long avgMillis = totalDeploymentTime.get() / deployments;
        return Duration.ofMillis(avgMillis);
    }
    
    /**
     * Gets average update time
     */
    public Duration getAverageUpdateTime() {
        int updates = successfulUpdates.get();
        if (updates == 0) return Duration.ZERO;
        
        long avgMillis = totalUpdateTime.get() / updates;
        return Duration.ofMillis(avgMillis);
    }
    
    /**
     * Gets total operations count
     */
    public int getTotalOperations() {
        return totalPackagingAttempts.get() + totalDeploymentAttempts.get() + 
               totalUpdateAttempts.get() + totalUninstallAttempts.get();
    }
    
    /**
     * Gets overall success rate
     */
    public double getOverallSuccessRate() {
        int totalAttempts = getTotalOperations();
        if (totalAttempts == 0) return 0.0;
        
        int totalSuccesses = successfulPackaging.get() + successfulDeployments.get() + 
                           successfulUpdates.get() + successfulUninstalls.get();
        
        return (double) totalSuccesses / totalAttempts;
    }
    
    /**
     * Gets recent deployment events (last 100)
     */
    public List<DeploymentEvent> getRecentEvents() {
        synchronized (eventHistory) {
            return eventHistory.stream()
                .sorted((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()))
                .limit(100)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        }
    }
    
    /**
     * Gets recent event for a specific agent and operation
     */
    public DeploymentEvent getRecentEvent(String agentId, String operationType) {
        return recentEvents.get(agentId + "-" + operationType);
    }
    
    /**
     * Gets uptime in milliseconds
     */
    public long getUptimeMillis() {
        return Instant.now().toEpochMilli() - startTime.toEpochMilli();
    }
    
    /**
     * Gets average operations per hour
     */
    public double getAverageOperationsPerHour() {
        long uptimeHours = Math.max(1, getUptimeMillis() / (1000 * 60 * 60));
        return (double) getTotalOperations() / uptimeHours;
    }
    
    /**
     * Gets deployment health metrics
     */
    public DeploymentHealthMetrics getHealthMetrics() {
        return new DeploymentHealthMetrics(
            getDeploymentSuccessRate(),
            getUpdateSuccessRate(),
            getAverageDeploymentTime(),
            getAverageUpdateTime(),
            getOverallSuccessRate(),
            getAverageOperationsPerHour()
        );
    }
    
    /**
     * Checks if deployment system meets performance requirements
     */
    public boolean meetsPerformanceRequirements() {
        return getDeploymentSuccessRate() >= 0.95 &&  // 95% success rate
               getAverageDeploymentTime().toMinutes() <= 15 &&  // 15 minutes max deployment
               getOverallSuccessRate() >= 0.90;  // 90% overall success
    }
    
    /**
     * Creates a snapshot of current statistics
     */
    public DeploymentStatistics snapshot() {
        DeploymentStatistics snapshot = new DeploymentStatistics();
        
        // Copy atomic values
        snapshot.totalPackagingAttempts.set(this.totalPackagingAttempts.get());
        snapshot.successfulPackaging.set(this.successfulPackaging.get());
        snapshot.totalDeploymentAttempts.set(this.totalDeploymentAttempts.get());
        snapshot.successfulDeployments.set(this.successfulDeployments.get());
        snapshot.totalUpdateAttempts.set(this.totalUpdateAttempts.get());
        snapshot.successfulUpdates.set(this.successfulUpdates.get());
        snapshot.totalUninstallAttempts.set(this.totalUninstallAttempts.get());
        snapshot.successfulUninstalls.set(this.successfulUninstalls.get());
        snapshot.totalDeploymentTime.set(this.totalDeploymentTime.get());
        snapshot.totalUpdateTime.set(this.totalUpdateTime.get());
        
        // Copy collections
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
        totalPackagingAttempts.set(0);
        successfulPackaging.set(0);
        totalDeploymentAttempts.set(0);
        successfulDeployments.set(0);
        totalUpdateAttempts.set(0);
        successfulUpdates.set(0);
        totalUninstallAttempts.set(0);
        successfulUninstalls.set(0);
        totalDeploymentTime.set(0);
        totalUpdateTime.set(0);
        
        recentEvents.clear();
        eventHistory.clear();
    }
    
    // Inner classes for statistics data
    
    public static class DeploymentEvent {
        public enum Type { PACKAGING, DEPLOYMENT, UPDATE, UNINSTALL, FAILOVER, RECOVERY }
        
        private final Type type;
        private final String agentId;
        private final boolean success;
        private final Instant timestamp;
        private final Duration duration;
        
        public DeploymentEvent(Type type, String agentId, boolean success, Instant timestamp, Duration duration) {
            this.type = type;
            this.agentId = agentId;
            this.success = success;
            this.timestamp = timestamp;
            this.duration = duration;
        }
        
        public Type getType() { return type; }
        public String getAgentId() { return agentId; }
        public boolean isSuccess() { return success; }
        public Instant getTimestamp() { return timestamp; }
        public Duration getDuration() { return duration; }
        
        @Override
        public String toString() {
            return String.format("DeploymentEvent{type=%s, agent='%s', success=%s, time=%s}",
                               type, agentId, success, timestamp);
        }
    }
    
    public static class DeploymentHealthMetrics {
        private final double deploymentSuccessRate;
        private final double updateSuccessRate;
        private final Duration averageDeploymentTime;
        private final Duration averageUpdateTime;
        private final double overallSuccessRate;
        private final double averageOperationsPerHour;
        
        public DeploymentHealthMetrics(double deploymentSuccessRate, double updateSuccessRate,
                                     Duration averageDeploymentTime, Duration averageUpdateTime,
                                     double overallSuccessRate, double averageOperationsPerHour) {
            this.deploymentSuccessRate = deploymentSuccessRate;
            this.updateSuccessRate = updateSuccessRate;
            this.averageDeploymentTime = averageDeploymentTime;
            this.averageUpdateTime = averageUpdateTime;
            this.overallSuccessRate = overallSuccessRate;
            this.averageOperationsPerHour = averageOperationsPerHour;
        }
        
        public double getDeploymentSuccessRate() { return deploymentSuccessRate; }
        public double getUpdateSuccessRate() { return updateSuccessRate; }
        public Duration getAverageDeploymentTime() { return averageDeploymentTime; }
        public Duration getAverageUpdateTime() { return averageUpdateTime; }
        public double getOverallSuccessRate() { return overallSuccessRate; }
        public double getAverageOperationsPerHour() { return averageOperationsPerHour; }
        
        public boolean isHealthy() {
            return deploymentSuccessRate >= 0.95 &&
                   updateSuccessRate >= 0.95 &&
                   averageDeploymentTime.toMinutes() <= 15 &&
                   overallSuccessRate >= 0.90;
        }
        
        @Override
        public String toString() {
            return String.format("DeploymentHealthMetrics{deploySuccess=%.2f%%, updateSuccess=%.2f%%, avgDeployTime=%s, overallSuccess=%.2f%%}",
                               deploymentSuccessRate * 100, updateSuccessRate * 100, averageDeploymentTime, overallSuccessRate * 100);
        }
    }
}