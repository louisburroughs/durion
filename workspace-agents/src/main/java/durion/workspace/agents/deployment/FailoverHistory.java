package durion.workspace.agents.deployment;

import java.time.Instant;
import java.util.*;

/**
 * Tracks failover history for agents
 */
public class FailoverHistory {
    private final List<FailoverRecord> records = new ArrayList<>();

    public void addRecord(FailoverRecord record) {
        records.add(record);
    }

    public List<FailoverRecord> getRecords() {
        return new ArrayList<>(records);
    }

    public static class FailoverRecord {
        private final String agentId;
        private final Instant failoverTime;
        private final String reason;
        private final boolean successful;

        public FailoverRecord(String agentId, Instant failoverTime, String reason, boolean successful) {
            this.agentId = agentId;
            this.failoverTime = failoverTime;
            this.reason = reason;
            this.successful = successful;
        }

        public String getAgentId() {
            return agentId;
        }

        public Instant getFailoverTime() {
            return failoverTime;
        }

        public String getReason() {
            return reason;
        }

        public boolean isSuccessful() {
            return successful;
        }
    }
}
