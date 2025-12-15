package durion.workspace.agents.deployment;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Result classes for deployment operations
 */
public class DeploymentResults {

    // Note: PackagingResult moved to standalone class to avoid type conflicts

    // Deployment Result
    public static class DeploymentResult {
        private final boolean success;
        private final DeployedAgent deployedAgent;
        private final String errorMessage;

        private DeploymentResult(boolean success, DeployedAgent deployedAgent, String errorMessage) {
            this.success = success;
            this.deployedAgent = deployedAgent;
            this.errorMessage = errorMessage;
        }

        public static DeploymentResult success(DeployedAgent deployedAgent) {
            return new DeploymentResult(true, deployedAgent, null);
        }

        public static DeploymentResult failure(String errorMessage) {
            return new DeploymentResult(false, null, errorMessage);
        }

        public boolean isSuccess() {
            return success;
        }

        public DeployedAgent getDeployedAgent() {
            return deployedAgent;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }

    // Update Result
    public static class UpdateResult {
        private final boolean success;
        private final DeployedAgent updatedAgent;
        private final DeployedAgent backupAgent;
        private final String errorMessage;

        private UpdateResult(boolean success, DeployedAgent updatedAgent, DeployedAgent backupAgent,
                String errorMessage) {
            this.success = success;
            this.updatedAgent = updatedAgent;
            this.backupAgent = backupAgent;
            this.errorMessage = errorMessage;
        }

        public static UpdateResult success(DeployedAgent updatedAgent, DeployedAgent backupAgent) {
            return new UpdateResult(true, updatedAgent, backupAgent, null);
        }

        public static UpdateResult failure(String errorMessage) {
            return new UpdateResult(false, null, null, errorMessage);
        }

        public boolean isSuccess() {
            return success;
        }

        public DeployedAgent getUpdatedAgent() {
            return updatedAgent;
        }

        public DeployedAgent getBackupAgent() {
            return backupAgent;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }

    // Uninstall Result
    public static class UninstallResult {
        private final boolean success;
        private final String message;

        private UninstallResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public static UninstallResult success(String message) {
            return new UninstallResult(true, message);
        }

        public static UninstallResult failure(String message) {
            return new UninstallResult(false, message);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }
    }

    // Failover Result
    public static class FailoverResult {
        private final boolean success;
        private final String agentId;
        private final String newInstanceId;
        private final Duration failoverTime;
        private final String errorMessage;

        private FailoverResult(boolean success, String agentId, String newInstanceId,
                Duration failoverTime, String errorMessage) {
            this.success = success;
            this.agentId = agentId;
            this.newInstanceId = newInstanceId;
            this.failoverTime = failoverTime;
            this.errorMessage = errorMessage;
        }

        public static FailoverResult success(String agentId, String newInstanceId, Duration failoverTime) {
            return new FailoverResult(true, agentId, newInstanceId, failoverTime, null);
        }

        public static FailoverResult failure(String agentId, String errorMessage) {
            return new FailoverResult(false, agentId, null, null, errorMessage);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getAgentId() {
            return agentId;
        }

        public String getNewInstanceId() {
            return newInstanceId;
        }

        public Duration getFailoverTime() {
            return failoverTime;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }

    // Disaster Recovery Result
    public static class DisasterRecoveryResult {
        private final boolean success;
        private final Duration recoveryTime;
        private final List<String> recoveredAgents;
        private final String errorMessage;

        private DisasterRecoveryResult(boolean success, Duration recoveryTime,
                List<String> recoveredAgents, String errorMessage) {
            this.success = success;
            this.recoveryTime = recoveryTime;
            this.recoveredAgents = recoveredAgents;
            this.errorMessage = errorMessage;
        }

        public static DisasterRecoveryResult success(Duration recoveryTime, List<String> recoveredAgents) {
            return new DisasterRecoveryResult(true, recoveryTime, recoveredAgents, null);
        }

        public static DisasterRecoveryResult failure(String errorMessage) {
            return new DisasterRecoveryResult(false, null, null, errorMessage);
        }

        public boolean isSuccess() {
            return success;
        }

        public Duration getRecoveryTime() {
            return recoveryTime;
        }

        public List<String> getRecoveredAgents() {
            return recoveredAgents;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public boolean meetsRTO() {
            return recoveryTime != null && recoveryTime.compareTo(Duration.ofHours(4)) <= 0;
        }
    }

    // Backup Result
    public static class BackupResult {
        private final boolean success;
        private final Map<String, BackupRecord> backups;
        private final Instant backupTime;
        private final String errorMessage;

        private BackupResult(boolean success, Map<String, BackupRecord> backups,
                Instant backupTime, String errorMessage) {
            this.success = success;
            this.backups = backups;
            this.backupTime = backupTime;
            this.errorMessage = errorMessage;
        }

        public static BackupResult success(Map<String, BackupRecord> backups, Instant backupTime) {
            return new BackupResult(true, backups, backupTime, null);
        }

        public static BackupResult failure(String errorMessage) {
            return new BackupResult(false, null, null, errorMessage);
        }

        public boolean isSuccess() {
            return success;
        }

        public Map<String, BackupRecord> getBackups() {
            return backups;
        }

        public Instant getBackupTime() {
            return backupTime;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }

    // Restore Result
    public static class RestoreResult {
        private final boolean success;
        private final DeployedAgent restoredAgent;
        private final String errorMessage;

        private RestoreResult(boolean success, DeployedAgent restoredAgent, String errorMessage) {
            this.success = success;
            this.restoredAgent = restoredAgent;
            this.errorMessage = errorMessage;
        }

        public static RestoreResult success(DeployedAgent restoredAgent) {
            return new RestoreResult(true, restoredAgent, null);
        }

        public static RestoreResult failure(String errorMessage) {
            return new RestoreResult(false, null, errorMessage);
        }

        public boolean isSuccess() {
            return success;
        }

        public DeployedAgent getRestoredAgent() {
            return restoredAgent;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}