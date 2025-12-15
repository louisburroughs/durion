package durion.workspace.agents.deployment;

import durion.workspace.agents.core.*;
import durion.workspace.agents.config.WorkspaceAgentConfigurationManager;
import durion.workspace.agents.registry.WorkspaceAgentRegistry;
import durion.workspace.agents.monitoring.PerformanceMonitor;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Manages workspace agent packaging, distribution, installation, and updates.
 * Provides health monitoring, failover capabilities, and disaster recovery.
 * 
 * Requirements: 1.1, 9.1, 9.2, 11.1 - Agent deployment, performance monitoring,
 * disaster recovery
 */
public class WorkspaceAgentDeploymentManager {

    private final WorkspaceAgentRegistry registry;
    private final WorkspaceAgentConfigurationManager configManager;
    private final PerformanceMonitor performanceMonitor;
    private final ExecutorService deploymentExecutor;
    private final ScheduledExecutorService healthMonitorExecutor;
    private final ReadWriteLock deploymentLock;

    // Deployment state management
    private final Map<String, DeploymentState> deploymentStates;
    private final Map<String, AgentPackage> availablePackages;
    private final Map<String, DeployedAgent> deployedAgents;
    private final Map<String, HealthStatus> agentHealthStatus;

    // Disaster recovery and failover
    private final DisasterRecoveryManager disasterRecoveryManager;
    private final FailoverManager failoverManager;

    // Statistics and monitoring
    private final DeploymentStatistics statistics;

    public WorkspaceAgentDeploymentManager(WorkspaceAgentRegistry registry,
            WorkspaceAgentConfigurationManager configManager,
            PerformanceMonitor performanceMonitor) {
        this.registry = registry;
        this.configManager = configManager;
        this.performanceMonitor = performanceMonitor;
        this.deploymentExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "deployment-" + System.currentTimeMillis());
            t.setDaemon(true);
            return t;
        });
        this.healthMonitorExecutor = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "health-monitor-" + System.currentTimeMillis());
            t.setDaemon(true);
            return t;
        });
        this.deploymentLock = new ReentrantReadWriteLock();

        this.deploymentStates = new ConcurrentHashMap<>();
        this.availablePackages = new ConcurrentHashMap<>();
        this.deployedAgents = new ConcurrentHashMap<>();
        this.agentHealthStatus = new ConcurrentHashMap<>();

        this.disasterRecoveryManager = new DisasterRecoveryManager(this);
        this.failoverManager = new FailoverManager(this);
        this.statistics = new DeploymentStatistics();

        initializeHealthMonitoring();
    }

    /**
     * Packages an agent for distribution
     */
    public CompletableFuture<PackagingResult> packageAgent(String agentId, PackagingOptions options) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                deploymentLock.writeLock().lock();

                // Get agent from registry
                Optional<WorkspaceAgentRegistry.RegisteredAgent> registeredAgent = registry.getRegisteredAgent(agentId);
                if (!registeredAgent.isPresent()) {
                    return PackagingResult.failure("Agent not found: " + agentId);
                }

                // Create agent package
                AgentPackage agentPackage = createAgentPackage(registeredAgent.get(), options);

                // Store package
                availablePackages.put(agentPackage.getPackageId(), agentPackage);

                // Update statistics
                statistics.recordPackaging(agentId, true);

                return PackagingResult.success(agentPackage);

            } catch (Exception e) {
                statistics.recordPackaging(agentId, false);
                return PackagingResult.failure("Packaging failed: " + e.getMessage());
            } finally {
                deploymentLock.writeLock().unlock();
            }
        }, deploymentExecutor);
    }

    /**
     * Deploys an agent from a package
     */
    public CompletableFuture<DeploymentResults.DeploymentResult> deployAgent(String packageId, String workspaceId,
            String environmentId, DeploymentOptions.DeploymentOption options) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                deploymentLock.writeLock().lock();

                // Get package
                AgentPackage agentPackage = availablePackages.get(packageId);
                if (agentPackage == null) {
                    return DeploymentResults.DeploymentResult.failure("Package not found: " + packageId);
                }

                String agentId = agentPackage.getAgentId();

                // Update deployment state
                deploymentStates.put(agentId, DeploymentState.DEPLOYING);

                // Validate deployment environment
                ValidationResult validation = validateDeploymentEnvironment(workspaceId, environmentId);
                if (!validation.isValid()) {
                    deploymentStates.put(agentId, DeploymentState.FAILED);
                    return DeploymentResults.DeploymentResult
                            .failure("Environment validation failed: " + validation.getErrorMessage());
                }

                // Deploy agent
                DeployedAgent deployedAgent = performDeployment(agentPackage, workspaceId, environmentId, options);

                // Register deployed agent
                deployedAgents.put(agentId, deployedAgent);
                deploymentStates.put(agentId, DeploymentState.DEPLOYED);

                // Initialize health monitoring
                initializeAgentHealthMonitoring(agentId);

                // Update statistics
                statistics.recordDeployment(agentId, true);

                return DeploymentResults.DeploymentResult.success(deployedAgent);

            } catch (Exception e) {
                String agentId = availablePackages.get(packageId).getAgentId();
                deploymentStates.put(agentId, DeploymentState.FAILED);
                statistics.recordDeployment(agentId, false);
                return DeploymentResults.DeploymentResult.failure("Deployment failed: " + e.getMessage());
            } finally {
                deploymentLock.writeLock().unlock();
            }
        }, deploymentExecutor);
    }

    /**
     * Updates a deployed agent to a new version
     */
    public CompletableFuture<DeploymentResults.UpdateResult> updateAgent(String agentId, String newPackageId,
            DeploymentOptions.UpdateOptions options) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                deploymentLock.writeLock().lock();

                // Get current deployment and new package
                DeployedAgent currentDeployment = deployedAgents.get(agentId);
                AgentPackage newPackage = availablePackages.get(newPackageId);

                if (currentDeployment == null) {
                    return DeploymentResults.UpdateResult.failure("Agent not currently deployed: " + agentId);
                }
                if (newPackage == null) {
                    return DeploymentResults.UpdateResult.failure("New package not found: " + newPackageId);
                }

                // Update deployment state
                deploymentStates.put(agentId, DeploymentState.UPDATING);

                // Perform update with rollback capability
                DeploymentResults.UpdateResult result = performUpdate(currentDeployment, newPackage, options);

                if (result.isSuccess()) {
                    deploymentStates.put(agentId, DeploymentState.DEPLOYED);
                    statistics.recordUpdate(agentId, true);
                } else {
                    deploymentStates.put(agentId, DeploymentState.FAILED);
                    statistics.recordUpdate(agentId, false);
                }

                return result;

            } catch (Exception e) {
                deploymentStates.put(agentId, DeploymentState.FAILED);
                statistics.recordUpdate(agentId, false);
                return DeploymentResults.UpdateResult.failure("Update failed: " + e.getMessage());
            } finally {
                deploymentLock.writeLock().unlock();
            }
        }, deploymentExecutor);
    }

    /**
     * Uninstalls a deployed agent
     */
    public CompletableFuture<DeploymentResults.UninstallResult> uninstallAgent(String agentId,
            DeploymentOptions.UninstallOptions options) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                deploymentLock.writeLock().lock();

                DeployedAgent deployedAgent = deployedAgents.get(agentId);
                if (deployedAgent == null) {
                    return DeploymentResults.UninstallResult.failure("Agent not deployed: " + agentId);
                }

                // Update deployment state
                deploymentStates.put(agentId, DeploymentState.UNINSTALLING);

                // Perform uninstallation
                performUninstallation(deployedAgent, options);

                // Clean up
                deployedAgents.remove(agentId);
                deploymentStates.remove(agentId);
                agentHealthStatus.remove(agentId);

                // Update statistics
                statistics.recordUninstall(agentId, true);

                return DeploymentResults.UninstallResult.success("Agent uninstalled successfully");

            } catch (Exception e) {
                deploymentStates.put(agentId, DeploymentState.FAILED);
                statistics.recordUninstall(agentId, false);
                return DeploymentResults.UninstallResult.failure("Uninstall failed: " + e.getMessage());
            } finally {
                deploymentLock.writeLock().unlock();
            }
        }, deploymentExecutor);
    }

    /**
     * Gets health status for all deployed agents
     */
    public Map<String, HealthStatus> getAgentHealthStatus() {
        deploymentLock.readLock().lock();
        try {
            return new HashMap<>(agentHealthStatus);
        } finally {
            deploymentLock.readLock().unlock();
        }
    }

    /**
     * Gets health status for a specific agent
     */
    public HealthStatus getAgentHealthStatus(String agentId) {
        return agentHealthStatus.get(agentId);
    }

    /**
     * Triggers failover for an unhealthy agent
     */
    public CompletableFuture<DeploymentResults.FailoverResult> triggerFailover(String agentId,
            FailoverOptions options) {
        return failoverManager.performFailover(agentId, options);
    }

    /**
     * Initiates disaster recovery procedures
     */
    public CompletableFuture<DisasterRecoveryResult> initiateDisasterRecovery(
            DisasterRecoveryOptions options) {
        return disasterRecoveryManager.initiateRecovery(options);
    }

    /**
     * Gets deployment statistics
     */
    public DeploymentStatistics getStatistics() {
        return statistics.snapshot();
    }

    /**
     * Gets all deployment states
     */
    public Map<String, DeploymentState> getDeploymentStates() {
        deploymentLock.readLock().lock();
        try {
            return new HashMap<>(deploymentStates);
        } finally {
            deploymentLock.readLock().unlock();
        }
    }

    /**
     * Creates an agent package from a registered agent
     */
    private AgentPackage createAgentPackage(WorkspaceAgentRegistry.RegisteredAgent registeredAgent,
            PackagingOptions options) {
        WorkspaceAgent agent = registeredAgent.getAgent();

        return new AgentPackage(
                generatePackageId(agent.getAgentId()),
                agent.getAgentId(),
                registeredAgent.getMetadata().getVersion(),
                agent.getAgentType(),
                agent.getCapabilities(),
                agent.getCoordinationDependencies(),
                options.getTargetEnvironments(),
                Instant.now());
    }

    /**
     * Validates deployment environment
     */
    private ValidationResult validateDeploymentEnvironment(String workspaceId, String environmentId) {
        // Check workspace configuration exists
        if (!configManager.getAllWorkspaceConfigurations().containsKey(workspaceId)) {
            return ValidationResult.invalid("Workspace configuration not found: " + workspaceId);
        }

        // Check environment configuration exists
        if (!configManager.getAllEnvironmentConfigurations().containsKey(environmentId)) {
            return ValidationResult.invalid("Environment configuration not found: " + environmentId);
        }

        return ValidationResult.valid();
    }

    /**
     * Performs the actual deployment
     */
    private DeployedAgent performDeployment(AgentPackage agentPackage, String workspaceId,
            String environmentId, DeploymentOptions.DeploymentOption options) {
        // Get effective configuration for the agent
        var effectiveConfig = configManager.getEffectiveConfiguration(
                agentPackage.getAgentId(), workspaceId, environmentId);

        // Create deployed agent instance
        return new DeployedAgent(
                agentPackage.getAgentId(),
                agentPackage.getPackageId(),
                agentPackage.getVersion(),
                workspaceId,
                environmentId,
                effectiveConfig,
                Instant.now(),
                DeploymentState.DEPLOYED);
    }

    /**
     * Performs agent update with rollback capability
     */
    private DeploymentResults.UpdateResult performUpdate(DeployedAgent currentDeployment, AgentPackage newPackage,
            DeploymentOptions.UpdateOptions options) {
        try {
            // Create backup of current deployment
            DeployedAgent backup = currentDeployment.createBackup();

            // Perform update
            DeployedAgent updatedAgent = new DeployedAgent(
                    currentDeployment.getAgentId(),
                    newPackage.getPackageId(),
                    newPackage.getVersion(),
                    currentDeployment.getWorkspaceId(),
                    currentDeployment.getEnvironmentId(),
                    currentDeployment.getEffectiveConfiguration(),
                    currentDeployment.getDeployedAt(),
                    DeploymentState.DEPLOYED);

            // Update deployed agents map
            deployedAgents.put(currentDeployment.getAgentId(), updatedAgent);

            return DeploymentResults.UpdateResult.success(updatedAgent, backup);

        } catch (Exception e) {
            return DeploymentResults.UpdateResult.failure("Update operation failed: " + e.getMessage());
        }
    }

    /**
     * Performs agent uninstallation
     */
    private void performUninstallation(DeployedAgent deployedAgent, DeploymentOptions.UninstallOptions options) {
        // Stop health monitoring
        // Clean up resources
        // Remove from registry if requested
        if (options.isRemoveFromRegistry()) {
            registry.unregisterAgent(deployedAgent.getAgentId());
        }
    }

    /**
     * Initializes health monitoring for all agents
     */
    private void initializeHealthMonitoring() {
        // Schedule periodic health checks
        healthMonitorExecutor.scheduleAtFixedRate(
                this::performHealthChecks,
                0, 30, TimeUnit.SECONDS);

        // Schedule performance monitoring
        healthMonitorExecutor.scheduleAtFixedRate(
                this::performPerformanceMonitoring,
                0, 60, TimeUnit.SECONDS);
    }

    /**
     * Initializes health monitoring for a specific agent
     */
    private void initializeAgentHealthMonitoring(String agentId) {
        agentHealthStatus.put(agentId, new HealthStatus(agentId, true, Instant.now(), null));
    }

    /**
     * Performs health checks on all deployed agents
     */
    private void performHealthChecks() {
        for (DeployedAgent deployedAgent : deployedAgents.values()) {
            try {
                boolean isHealthy = checkAgentHealth(deployedAgent);
                HealthStatus currentStatus = agentHealthStatus.get(deployedAgent.getAgentId());

                if (currentStatus == null || currentStatus.isHealthy() != isHealthy) {
                    HealthStatus newStatus = new HealthStatus(
                            deployedAgent.getAgentId(),
                            isHealthy,
                            Instant.now(),
                            isHealthy ? null : "Health check failed");
                    agentHealthStatus.put(deployedAgent.getAgentId(), newStatus);

                    // Trigger failover if agent becomes unhealthy
                    if (!isHealthy && currentStatus != null && currentStatus.isHealthy()) {
                        triggerFailover(deployedAgent.getAgentId(), new FailoverOptions());
                    }
                }

            } catch (Exception e) {
                HealthStatus errorStatus = new HealthStatus(
                        deployedAgent.getAgentId(),
                        false,
                        Instant.now(),
                        "Health check error: " + e.getMessage());
                agentHealthStatus.put(deployedAgent.getAgentId(), errorStatus);
            }
        }
    }

    /**
     * Performs performance monitoring
     */
    private void performPerformanceMonitoring() {
        for (DeployedAgent deployedAgent : deployedAgents.values()) {
            try {
                // Monitor response times and availability
                performanceMonitor.recordAgentPerformance(deployedAgent.getAgentId());

                // Check if performance meets requirements (5-second response time, 99.9%
                // availability)
                if (!performanceMonitor.meetsPerformanceRequirements(deployedAgent.getAgentId())) {
                    // Trigger performance optimization
                    optimizeAgentPerformance(deployedAgent);
                }

            } catch (Exception e) {
                // Log performance monitoring error
            }
        }
    }

    /**
     * Checks health of a specific agent
     */
    private boolean checkAgentHealth(DeployedAgent deployedAgent) {
        // In a real implementation, this would check:
        // - Agent responsiveness
        // - Resource usage
        // - Error rates
        // - Dependency availability

        // For now, simulate health check
        return deployedAgent.getState() == DeploymentState.DEPLOYED;
    }

    /**
     * Optimizes agent performance based on monitoring data
     */
    private void optimizeAgentPerformance(DeployedAgent deployedAgent) {
        // Get performance metrics
        var performanceData = performanceMonitor.getPerformanceData(deployedAgent.getAgentId());

        // Create optimization request
        var optimizationRequest = new durion.workspace.agents.config.PerformanceOptimizationRequest(
                deployedAgent.getAgentId(),
                performanceData.averageResponseTime().toMillis(),
                performanceData.getCpuUtilization(),
                performanceData.getMemoryUtilization(),
                performanceData.getErrorRate(),
                performanceData.getConcurrentRequests(),
                "performance");

        // Apply optimization
        configManager.optimizeConfiguration(
                deployedAgent.getAgentId(),
                deployedAgent.getWorkspaceId(),
                deployedAgent.getEnvironmentId(),
                optimizationRequest);
    }

    /**
     * Generates a unique package ID
     */
    private String generatePackageId(String agentId) {
        return agentId + "-" + System.currentTimeMillis();
    }

    /**
     * Shuts down the deployment manager
     */
    public void shutdown() {
        deploymentExecutor.shutdown();
        healthMonitorExecutor.shutdown();

        try {
            if (!deploymentExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                deploymentExecutor.shutdownNow();
            }
            if (!healthMonitorExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                healthMonitorExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            deploymentExecutor.shutdownNow();
            healthMonitorExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // Enums for deployment states
    public enum DeploymentState {
        PACKAGING, DEPLOYING, DEPLOYED, UPDATING, UNINSTALLING, FAILED
    }

    // Inner classes for validation and results
    public static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;

        private ValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }

        public static ValidationResult valid() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult invalid(String errorMessage) {
            return new ValidationResult(false, errorMessage);
        }

        public boolean isValid() {
            return valid;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}