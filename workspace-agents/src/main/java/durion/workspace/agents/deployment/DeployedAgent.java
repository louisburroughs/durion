package durion.workspace.agents.deployment;

import durion.workspace.agents.config.EffectiveConfiguration;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents a deployed workspace agent instance
 */
public class DeployedAgent {
    
    private final String agentId;
    private final String packageId;
    private final String version;
    private final String workspaceId;
    private final String environmentId;
    private final EffectiveConfiguration effectiveConfiguration;
    private final Instant deployedAt;
    private final WorkspaceAgentDeploymentManager.DeploymentState state;
    
    public DeployedAgent(String agentId, String packageId, String version, String workspaceId,
                        String environmentId, EffectiveConfiguration effectiveConfiguration,
                        Instant deployedAt, WorkspaceAgentDeploymentManager.DeploymentState state) {
        this.agentId = Objects.requireNonNull(agentId, "Agent ID cannot be null");
        this.packageId = Objects.requireNonNull(packageId, "Package ID cannot be null");
        this.version = Objects.requireNonNull(version, "Version cannot be null");
        this.workspaceId = Objects.requireNonNull(workspaceId, "Workspace ID cannot be null");
        this.environmentId = Objects.requireNonNull(environmentId, "Environment ID cannot be null");
        this.effectiveConfiguration = Objects.requireNonNull(effectiveConfiguration, "Configuration cannot be null");
        this.deployedAt = Objects.requireNonNull(deployedAt, "Deployed time cannot be null");
        this.state = Objects.requireNonNull(state, "State cannot be null");
    }
    
    public String getAgentId() {
        return agentId;
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
    
    public EffectiveConfiguration getEffectiveConfiguration() {
        return effectiveConfiguration;
    }
    
    public Instant getDeployedAt() {
        return deployedAt;
    }
    
    public WorkspaceAgentDeploymentManager.DeploymentState getState() {
        return state;
    }
    
    /**
     * Gets deployment uptime in milliseconds
     */
    public long getUptimeMillis() {
        return Instant.now().toEpochMilli() - deployedAt.toEpochMilli();
    }
    
    /**
     * Checks if deployment is currently active
     */
    public boolean isActive() {
        return state == WorkspaceAgentDeploymentManager.DeploymentState.DEPLOYED;
    }
    
    /**
     * Gets deployment environment type
     */
    public String getEnvironmentType() {
        return effectiveConfiguration.getStringProperty("environment.type", "development");
    }
    
    /**
     * Gets response timeout configuration
     */
    public int getResponseTimeout() {
        return effectiveConfiguration.getIntProperty("performance.response.timeout", 5000);
    }
    
    /**
     * Checks if monitoring is enabled
     */
    public boolean isMonitoringEnabled() {
        return effectiveConfiguration.getBooleanProperty("performance.monitoring.enabled", true);
    }
    
    /**
     * Gets logging level
     */
    public String getLoggingLevel() {
        return effectiveConfiguration.getStringProperty("logging.level", "INFO");
    }
    
    /**
     * Creates a backup of this deployment for rollback purposes
     */
    public DeployedAgent createBackup() {
        return new DeployedAgent(
            agentId + "-backup-" + System.currentTimeMillis(),
            packageId,
            version,
            workspaceId,
            environmentId,
            effectiveConfiguration,
            deployedAt,
            state
        );
    }
    
    /**
     * Creates deployment summary for monitoring and reporting
     */
    public DeploymentSummary getSummary() {
        return new DeploymentSummary(
            agentId,
            version,
            workspaceId,
            environmentId,
            getEnvironmentType(),
            state,
            deployedAt,
            getUptimeMillis(),
            isMonitoringEnabled(),
            getResponseTimeout()
        );
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        DeployedAgent that = (DeployedAgent) obj;
        return Objects.equals(agentId, that.agentId) &&
               Objects.equals(packageId, that.packageId) &&
               Objects.equals(workspaceId, that.workspaceId) &&
               Objects.equals(environmentId, that.environmentId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(agentId, packageId, workspaceId, environmentId);
    }
    
    @Override
    public String toString() {
        return String.format("DeployedAgent{id='%s', version='%s', workspace='%s', environment='%s', state=%s}",
                           agentId, version, workspaceId, environmentId, state);
    }
    
    // Inner class for deployment summary
    public static class DeploymentSummary {
        private final String agentId;
        private final String version;
        private final String workspaceId;
        private final String environmentId;
        private final String environmentType;
        private final WorkspaceAgentDeploymentManager.DeploymentState state;
        private final Instant deployedAt;
        private final long uptimeMillis;
        private final boolean monitoringEnabled;
        private final int responseTimeout;
        
        public DeploymentSummary(String agentId, String version, String workspaceId, String environmentId,
                               String environmentType, WorkspaceAgentDeploymentManager.DeploymentState state,
                               Instant deployedAt, long uptimeMillis, boolean monitoringEnabled,
                               int responseTimeout) {
            this.agentId = agentId;
            this.version = version;
            this.workspaceId = workspaceId;
            this.environmentId = environmentId;
            this.environmentType = environmentType;
            this.state = state;
            this.deployedAt = deployedAt;
            this.uptimeMillis = uptimeMillis;
            this.monitoringEnabled = monitoringEnabled;
            this.responseTimeout = responseTimeout;
        }
        
        // Getters
        public String getAgentId() { return agentId; }
        public String getVersion() { return version; }
        public String getWorkspaceId() { return workspaceId; }
        public String getEnvironmentId() { return environmentId; }
        public String getEnvironmentType() { return environmentType; }
        public WorkspaceAgentDeploymentManager.DeploymentState getState() { return state; }
        public Instant getDeployedAt() { return deployedAt; }
        public long getUptimeMillis() { return uptimeMillis; }
        public boolean isMonitoringEnabled() { return monitoringEnabled; }
        public int getResponseTimeout() { return responseTimeout; }
        
        public boolean isHealthy() {
            return state == WorkspaceAgentDeploymentManager.DeploymentState.DEPLOYED &&
                   uptimeMillis > 0;
        }
        
        @Override
        public String toString() {
            return String.format("DeploymentSummary{agent='%s', state=%s, uptime=%dms, healthy=%s}",
                               agentId, state, uptimeMillis, isHealthy());
        }
    }
}