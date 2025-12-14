package durion.workspace.agents.deployment;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Statistics for recovery operations
 */
public class RecoveryStatistics {
    private final Map<String, Integer> recoveryStats = new HashMap<>();
    private final List<RecoveryEvent> events = new ArrayList<>();
    private final List<Duration> recoveryTimes = new ArrayList<>();
    private final List<Duration> dataLosses = new ArrayList<>();

    public void recordRecoveryEvent(String type, String details) {
        events.add(new RecoveryEvent(type, details, Instant.now()));
        recoveryStats.merge(type, 1, Integer::sum);
    }

    public int getRecoveryCount(String type) {
        return recoveryStats.getOrDefault(type, 0);
    }

    public List<RecoveryEvent> getEvents() {
        return new ArrayList<>(events);
    }
    
    public void recordRecovery(Duration duration, boolean success) {
        if (success) {
            recoveryTimes.add(duration);
            recoveryStats.merge("RECOVERY_SUCCESS", 1, Integer::sum);
        } else {
            recoveryStats.merge("RECOVERY_FAILURE", 1, Integer::sum);
        }
    }
    
    public void recordBackup(int count, boolean success) {
        if (success) {
            recoveryStats.merge("BACKUP_SUCCESS", count, Integer::sum);
        } else {
            recoveryStats.merge("BACKUP_FAILURE", count, Integer::sum);
        }
    }
    
    public void recordRestore(String agentId, boolean success) {
        if (success) {
            recoveryStats.merge("RESTORE_SUCCESS", 1, Integer::sum);
        } else {
            recoveryStats.merge("RESTORE_FAILURE", 1, Integer::sum);
        }
    }
    
    public Duration getAverageRecoveryTime() {
        if (recoveryTimes.isEmpty()) return Duration.ZERO;
        
        long totalNanos = recoveryTimes.stream()
            .mapToLong(Duration::toNanos)
            .sum();
        
        return Duration.ofNanos(totalNanos / recoveryTimes.size());
    }
    
    public Duration getMaxDataLoss() {
        if (dataLosses.isEmpty()) return Duration.ZERO;
        return dataLosses.stream()
            .max(Comparator.naturalOrder())
            .orElse(Duration.ZERO);
    }
    
    public RecoveryStatisticsSnapshot snapshot() {
        return new RecoveryStatisticsSnapshot(
            new HashMap<>(recoveryStats),
            new ArrayList<>(events),
            getAverageRecoveryTime(),
            getMaxDataLoss(),
            recoveryStats.getOrDefault("RECOVERY_SUCCESS", 0),
            recoveryStats.getOrDefault("RECOVERY_FAILURE", 0)
        );
    }

    public static class RecoveryEvent {
        private final String type;
        private final String details;
        private final Instant timestamp;

        public RecoveryEvent(String type, String details, Instant timestamp) {
            this.type = type;
            this.details = details;
            this.timestamp = timestamp;
        }

        public String getType() {
            return type;
        }

        public String getDetails() {
            return details;
        }

        public Instant getTimestamp() {
            return timestamp;
        }
    }
    
    public static class RecoveryStatisticsSnapshot {
        private final Map<String, Integer> stats;
        private final List<RecoveryEvent> events;
        private final Duration averageRecoveryTime;
        private final Duration maxDataLoss;
        private final int successCount;
        private final int failureCount;
        
        public RecoveryStatisticsSnapshot(Map<String, Integer> stats, List<RecoveryEvent> events,
                                         Duration averageRecoveryTime, Duration maxDataLoss,
                                         int successCount, int failureCount) {
            this.stats = stats;
            this.events = events;
            this.averageRecoveryTime = averageRecoveryTime;
            this.maxDataLoss = maxDataLoss;
            this.successCount = successCount;
            this.failureCount = failureCount;
        }
        
        public Map<String, Integer> getStats() {
            return stats;
        }
        
        public List<RecoveryEvent> getEvents() {
            return events;
        }
        
        public Duration getAverageRecoveryTime() {
            return averageRecoveryTime;
        }
        
        public Duration getMaxDataLoss() {
            return maxDataLoss;
        }
        
        public int getSuccessCount() {
            return successCount;
        }
        
        public int getFailureCount() {
            return failureCount;
        }
    }
}
