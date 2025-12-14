package durion.workspace.agents.deployment;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a backup record for disaster recovery
 */
public class BackupRecord {
    private final String backupId;
    private final String agentId;
    private final Instant backupTime;
    private final long sizeBytes;
    private final String location;
    private final BackupStatus status;
    private final String packageId;
    private final String version;
    private final String workspaceId;
    private final String environmentId;
    private final Map<String, Object> configuration;

    public BackupRecord(String backupId, String agentId, Instant backupTime, long sizeBytes, String location, BackupStatus status) {
        this(backupId, agentId, backupTime, sizeBytes, location, status, null, null, null, null, null);
    }

    public BackupRecord(String backupId, String agentId, Instant backupTime, long sizeBytes, String location, 
                       BackupStatus status, String packageId, String version, String workspaceId, 
                       String environmentId, Map<String, Object> configuration) {
        this.backupId = backupId;
        this.agentId = agentId;
        this.backupTime = backupTime;
        this.sizeBytes = sizeBytes;
        this.location = location;
        this.status = status;
        this.packageId = packageId;
        this.version = version;
        this.workspaceId = workspaceId;
        this.environmentId = environmentId;
        this.configuration = configuration != null ? configuration : new HashMap<>();
    }

    public String getBackupId() {
        return backupId;
    }

    public String getAgentId() {
        return agentId;
    }

    public Instant getBackupTime() {
        return backupTime;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getLocation() {
        return location;
    }

    public BackupStatus getStatus() {
        return status;
    }
    
    public String getPackageId() {
        return packageId;
    }
    
    public String getVersion() {
        return version;
    }
    
    public String getWorkspaceId() {
        return workspaceId;
    }
    
    public String getEnvironmentId() {
        return environmentId;
    }
    
    public Map<String, Object> getConfiguration() {
        return configuration;
    }

    public enum BackupStatus {
        PENDING, IN_PROGRESS, COMPLETED, FAILED, EXPIRED
    }
}
