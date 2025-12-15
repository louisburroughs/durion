package durion.workspace.agents.deployment;

import java.util.*;

/**
 * Options and configuration classes for deployment operations
 */
public class DeploymentOptions {

    // Packaging Options
    public static class PackagingOptions {
        private final Set<String> targetEnvironments;
        private final boolean includeDebugInfo;
        private final boolean optimizeForSize;
        private final Map<String, Object> metadata;

        public PackagingOptions(Set<String> targetEnvironments, boolean includeDebugInfo,
                boolean optimizeForSize, Map<String, Object> metadata) {
            this.targetEnvironments = new HashSet<>(targetEnvironments);
            this.includeDebugInfo = includeDebugInfo;
            this.optimizeForSize = optimizeForSize;
            this.metadata = new HashMap<>(metadata);
        }

        public static PackagingOptions defaultOptions() {
            return new PackagingOptions(
                    Set.of("development", "staging", "production"),
                    false,
                    true,
                    new HashMap<>());
        }

        public Set<String> getTargetEnvironments() {
            return new HashSet<>(targetEnvironments);
        }

        public boolean isIncludeDebugInfo() {
            return includeDebugInfo;
        }

        public boolean isOptimizeForSize() {
            return optimizeForSize;
        }

        public Map<String, Object> getMetadata() {
            return new HashMap<>(metadata);
        }
    }

    // Deployment Options
    public static class DeploymentOption {
        private final boolean validateBeforeDeployment;
        private final boolean enableHealthChecks;
        private final int healthCheckInterval;
        private final boolean autoRollbackOnFailure;
        private final Map<String, String> environmentVariables;

        public DeploymentOption(boolean validateBeforeDeployment, boolean enableHealthChecks,
                int healthCheckInterval, boolean autoRollbackOnFailure,
                Map<String, String> environmentVariables) {
            this.validateBeforeDeployment = validateBeforeDeployment;
            this.enableHealthChecks = enableHealthChecks;
            this.healthCheckInterval = healthCheckInterval;
            this.autoRollbackOnFailure = autoRollbackOnFailure;
            this.environmentVariables = new HashMap<>(environmentVariables);
        }

        public static DeploymentOption defaultOptions() {
            return new DeploymentOption(
                    true,
                    true,
                    30, // 30 seconds
                    true,
                    new HashMap<>());
        }

        public boolean isValidateBeforeDeployment() {
            return validateBeforeDeployment;
        }

        public boolean isEnableHealthChecks() {
            return enableHealthChecks;
        }

        public int getHealthCheckInterval() {
            return healthCheckInterval;
        }

        public boolean isAutoRollbackOnFailure() {
            return autoRollbackOnFailure;
        }

        public Map<String, String> getEnvironmentVariables() {
            return new HashMap<>(environmentVariables);
        }
    }

    // Update Options
    public static class UpdateOptions {
        private final boolean createBackup;
        private final boolean validateAfterUpdate;
        private final boolean rollbackOnFailure;
        private final int maxRollbackAttempts;
        private final String updateStrategy;

        public UpdateOptions(boolean createBackup, boolean validateAfterUpdate,
                boolean rollbackOnFailure, int maxRollbackAttempts,
                String updateStrategy) {
            this.createBackup = createBackup;
            this.validateAfterUpdate = validateAfterUpdate;
            this.rollbackOnFailure = rollbackOnFailure;
            this.maxRollbackAttempts = maxRollbackAttempts;
            this.updateStrategy = updateStrategy;
        }

        public static UpdateOptions defaultOptions() {
            return new UpdateOptions(
                    true,
                    true,
                    true,
                    3,
                    "rolling");
        }

        public boolean isCreateBackup() {
            return createBackup;
        }

        public boolean isValidateAfterUpdate() {
            return validateAfterUpdate;
        }

        public boolean isRollbackOnFailure() {
            return rollbackOnFailure;
        }

        public int getMaxRollbackAttempts() {
            return maxRollbackAttempts;
        }

        public String getUpdateStrategy() {
            return updateStrategy;
        }
    }

    // Uninstall Options
    public static class UninstallOptions {
        private final boolean removeFromRegistry;
        private final boolean cleanupResources;
        private final boolean createBackupBeforeUninstall;
        private final boolean forceUninstall;

        public UninstallOptions(boolean removeFromRegistry, boolean cleanupResources,
                boolean createBackupBeforeUninstall, boolean forceUninstall) {
            this.removeFromRegistry = removeFromRegistry;
            this.cleanupResources = cleanupResources;
            this.createBackupBeforeUninstall = createBackupBeforeUninstall;
            this.forceUninstall = forceUninstall;
        }

        public static UninstallOptions defaultOptions() {
            return new UninstallOptions(true, true, true, false);
        }

        public boolean isRemoveFromRegistry() {
            return removeFromRegistry;
        }

        public boolean isCleanupResources() {
            return cleanupResources;
        }

        public boolean isCreateBackupBeforeUninstall() {
            return createBackupBeforeUninstall;
        }

        public boolean isForceUninstall() {
            return forceUninstall;
        }
    }

    // Note: FailoverOptions moved to standalone class to avoid type conflicts

    // Note: DisasterRecoveryOptions moved to standalone class to avoid type
    // conflicts

    // Backup Options
    public static class BackupOptions {
        private final boolean includeConfiguration;
        private final boolean includeState;
        private final boolean compressBackup;
        private final String backupLocation;
        private final int retentionDays;

        public BackupOptions(boolean includeConfiguration, boolean includeState,
                boolean compressBackup, String backupLocation, int retentionDays) {
            this.includeConfiguration = includeConfiguration;
            this.includeState = includeState;
            this.compressBackup = compressBackup;
            this.backupLocation = backupLocation;
            this.retentionDays = retentionDays;
        }

        public static BackupOptions defaultOptions() {
            return new BackupOptions(true, true, true, "/backups", 30);
        }

        public boolean isIncludeConfiguration() {
            return includeConfiguration;
        }

        public boolean isIncludeState() {
            return includeState;
        }

        public boolean isCompressBackup() {
            return compressBackup;
        }

        public String getBackupLocation() {
            return backupLocation;
        }

        public int getRetentionDays() {
            return retentionDays;
        }
    }

    // Restore Options
    public static class RestoreOptions {
        private final boolean validateBeforeRestore;
        private final boolean preserveCurrentConfiguration;
        private final boolean restoreToOriginalLocation;
        private final String targetWorkspace;
        private final String targetEnvironment;

        public RestoreOptions(boolean validateBeforeRestore, boolean preserveCurrentConfiguration,
                boolean restoreToOriginalLocation, String targetWorkspace,
                String targetEnvironment) {
            this.validateBeforeRestore = validateBeforeRestore;
            this.preserveCurrentConfiguration = preserveCurrentConfiguration;
            this.restoreToOriginalLocation = restoreToOriginalLocation;
            this.targetWorkspace = targetWorkspace;
            this.targetEnvironment = targetEnvironment;
        }

        public static RestoreOptions defaultOptions() {
            return new RestoreOptions(true, false, true, null, null);
        }

        public boolean isValidateBeforeRestore() {
            return validateBeforeRestore;
        }

        public boolean isPreserveCurrentConfiguration() {
            return preserveCurrentConfiguration;
        }

        public boolean isRestoreToOriginalLocation() {
            return restoreToOriginalLocation;
        }

        public String getTargetWorkspace() {
            return targetWorkspace;
        }

        public String getTargetEnvironment() {
            return targetEnvironment;
        }
    }
}