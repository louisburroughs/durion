package durion.workspace.agents.deployment;

/**
 * Options for backup operations
 */
public class BackupOptions {
    private final boolean incremental;
    private final boolean encrypted;
    private final boolean verifyAfterBackup;
    private final String backupLocation;
    private final int retentionDays;

    public BackupOptions(boolean incremental, boolean encrypted, boolean verifyAfterBackup, String backupLocation, int retentionDays) {
        this.incremental = incremental;
        this.encrypted = encrypted;
        this.verifyAfterBackup = verifyAfterBackup;
        this.backupLocation = backupLocation;
        this.retentionDays = retentionDays;
    }

    public boolean isIncremental() {
        return incremental;
    }

    public boolean isEncrypted() {
        return encrypted;
    }

    public boolean isVerifyAfterBackup() {
        return verifyAfterBackup;
    }

    public String getBackupLocation() {
        return backupLocation;
    }

    public int getRetentionDays() {
        return retentionDays;
    }
}
