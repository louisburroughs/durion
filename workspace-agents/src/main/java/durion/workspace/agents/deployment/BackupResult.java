package durion.workspace.agents.deployment;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Result of backup operation
 */
public class BackupResult {
    private final boolean successful;
    private final Map<String, BackupRecord> backups;
    private final Instant backupTime;
    private final String errorMessage;

    private BackupResult(boolean successful, Map<String, BackupRecord> backups, Instant backupTime, String errorMessage) {
        this.successful = successful;
        this.backups = backups != null ? new HashMap<>(backups) : new HashMap<>();
        this.backupTime = backupTime;
        this.errorMessage = errorMessage;
    }

    public boolean isSuccessful() {
        return successful;
    }

    public Map<String, BackupRecord> getBackups() {
        return new HashMap<>(backups);
    }

    public Instant getBackupTime() {
        return backupTime;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
    
    /**
     * Creates a successful backup result
     */
    public static BackupResult success(Map<String, BackupRecord> backups, Instant backupTime) {
        return new BackupResult(true, backups, backupTime, null);
    }
    
    /**
     * Creates a failed backup result
     */
    public static BackupResult failure(String errorMessage) {
        return new BackupResult(false, new HashMap<>(), Instant.now(), errorMessage);
    }
}
