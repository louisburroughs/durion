package durion.workspace.agents;

import durion.workspace.agents.core.AbstractWorkspaceAgent;
import durion.workspace.agents.core.AgentException;
import durion.workspace.agents.core.AgentStatus;
import durion.workspace.agents.core.AgentRequest;
import durion.workspace.agents.core.AgentCapability;
import durion.workspace.agents.registry.WorkspaceAgentRegistry;
import durion.workspace.agents.discovery.AgentDiscoveryService;
import durion.workspace.agents.coordination.*;
import durion.workspace.agents.monitoring.PerformanceMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Main application class for the Workspace Agent Framework.
 * Provides bootstrapping, agent discovery, initialization, and coordination
 * services.
 */
public class WorkspaceAgentFramework {
    private static final Logger logger = LoggerFactory.getLogger(WorkspaceAgentFramework.class);

    private final WorkspaceAgentRegistry registry;
    private final AgentDiscoveryService discoveryService;
    private final ExecutorService executorService;
    private final PerformanceMonitor performanceMonitor;
    private volatile boolean running = false;

    public WorkspaceAgentFramework() {
        this.performanceMonitor = new PerformanceMonitor();
        this.registry = new WorkspaceAgentRegistry(performanceMonitor);
        this.discoveryService = new AgentDiscoveryService(performanceMonitor);
        this.executorService = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "workspace-agent-framework");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Main entry point for the Workspace Agent Framework.
     */
    public static void main(String[] args) {
        WorkspaceAgentFramework framework = new WorkspaceAgentFramework();

        // Add shutdown hook for graceful cleanup
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down Workspace Agent Framework...");
            framework.shutdown();
        }));

        try {
            framework.start();

            // Keep the application running
            synchronized (framework) {
                while (framework.isRunning()) {
                    framework.wait();
                }
            }
        } catch (Exception e) {
            logger.error("Failed to start Workspace Agent Framework", e);
            System.exit(1);
        }
    }

    /**
     * Start the framework and initialize all agents.
     */
    public void start() throws AgentException {
        logger.info("Starting Workspace Agent Framework...");

        try {
            // Initialize and register all available agents
            initializeAgents();

            running = true;
            logger.info("Workspace Agent Framework started successfully");

        } catch (Exception e) {
            logger.error("Failed to start framework", e);
            throw new AgentException("framework", AgentException.AgentErrorType.CONFIGURATION_ERROR,
                    "Framework startup failed", e);
        }
    }

    /**
     * Initialize and register all available workspace agents.
     */
    private void initializeAgents() throws AgentException {
        logger.info("Discovering and initializing workspace agents...");

        // Discover available agent classes
        var discoveryRequest = new AgentRequest("framework-init", "discovery", "Framework initialization discovery",
                AgentCapability.WORKFLOW_COORDINATION, "workspace");
        var discoveryResult = discoveryService.discoverAgents(discoveryRequest);
        logger.info("Discovered {} agent classes", discoveryResult.getCandidateAgents().size());

        // Initialize core coordination agents
        initializeCoreAgents();

        logger.info("Agent initialization completed. {} agents registered",
                registry.getAllRegisteredAgents().size());
    }

    /**
     * Initialize core coordination agents.
     */
    private void initializeCoreAgents() {
        try {
            // Initialize coordination agents
            registerAgent(new CrossProjectTestingAgent());
            registerAgent(new DataGovernanceAgent());
            registerAgent(new DisasterRecoveryAgent());
            registerAgent(new DocumentationCoordinationAgent());
            registerAgent(new PerformanceCoordinationAgent());
            registerAgent(new WorkflowCoordinationAgent());
            registerAgent(new WorkspaceFeatureDevelopmentAgent());
            registerAgent(new WorkspaceReleaseCoordinationAgent());

            logger.info("Core coordination agents initialized successfully");

        } catch (Exception e) {
            logger.error("Failed to initialize core agents", e);
            throw new RuntimeException("Core agent initialization failed", e);
        }
    }

    /**
     * Register an agent with the framework.
     */
    private void registerAgent(AbstractWorkspaceAgent agent) {
        try {
            // Set performance monitor
            agent.setPerformanceMonitor(performanceMonitor);

            // Create agent metadata
            var metadata = new durion.workspace.agents.discovery.AgentMetadata(agent);

            // Register with the registry
            var registrationResult = registry.registerAgent(agent, metadata);

            if (registrationResult.isSuccess()) {
                logger.info("Successfully registered agent: {}", agent.getClass().getSimpleName());
            } else {
                logger.error("Failed to register agent {}: {}", agent.getClass().getSimpleName(),
                        registrationResult.getMessage());
                throw new RuntimeException("Agent registration failed: " + registrationResult.getMessage());
            }

        } catch (Exception e) {
            logger.error("Failed to register agent: {}", agent.getClass().getSimpleName(), e);
            throw new RuntimeException("Agent registration failed: " + agent.getClass().getSimpleName(), e);
        }
    }

    /**
     * Shutdown the framework and cleanup all resources.
     */
    public void shutdown() {
        if (!running) {
            return;
        }

        logger.info("Shutting down Workspace Agent Framework...");
        running = false;

        try {
            // Shutdown all registered agents
            List<WorkspaceAgentRegistry.RegisteredAgent> registeredAgents = registry.getAllRegisteredAgents();
            for (WorkspaceAgentRegistry.RegisteredAgent registeredAgent : registeredAgents) {
                AbstractWorkspaceAgent agent = (AbstractWorkspaceAgent) registeredAgent.getAgent();
                try {
                    AgentStatus status = agent.getStatus();
                    if (status.available()) {
                        logger.debug("Shutdown agent: {}", agent.getClass().getSimpleName());
                        // Agent shutdown is handled by the registry
                    }
                } catch (Exception e) {
                    logger.error("Error shutting down agent: {}", agent.getClass().getSimpleName(), e);
                }
            }

            // Shutdown executor service
            executorService.shutdown();
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }

            logger.info("Workspace Agent Framework shutdown completed");

        } catch (Exception e) {
            logger.error("Error during framework shutdown", e);
        }

        synchronized (this) {
            notifyAll();
        }
    }

    /**
     * Check if the framework is currently running.
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Get the agent registry.
     */
    public WorkspaceAgentRegistry getRegistry() {
        return registry;
    }

    /**
     * Get the discovery service.
     */
    public AgentDiscoveryService getDiscoveryService() {
        return discoveryService;
    }
}