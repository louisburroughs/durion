package durion.workspace.agents.registry;

import durion.workspace.agents.core.*;
import durion.workspace.agents.core.AgentException.AgentErrorType;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Handles cross-layer agent dependency resolution and loading system.
 * Ensures agents are loaded in correct dependency order.
 * 
 * Requirements: 1.1 - Cross-layer agent dependency resolution and loading system
 */
public class AgentDependencyLoader {
    
    private final WorkspaceAgentRegistry registry;
    private final ExecutorService loadingExecutor;
    private final Map<String, LoadingState> loadingStates;
    private final Map<String, CompletableFuture<WorkspaceAgent>> loadingFutures;
    
    // Loading statistics
    private final LoadingStatistics statistics;
    
    public AgentDependencyLoader(WorkspaceAgentRegistry registry) {
        this.registry = registry;
        this.loadingExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "agent-loader-" + System.currentTimeMillis());
            t.setDaemon(true);
            return t;
        });
        this.loadingStates = new ConcurrentHashMap<>();
        this.loadingFutures = new ConcurrentHashMap<>();
        this.statistics = new LoadingStatistics();
    }
    
    /**
     * Loads agents with their dependencies in correct order
     */
    public CompletableFuture<LoadingResult> loadAgentsWithDependencies(Set<String> agentIds) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Phase 1: Validate all agents exist and are loadable
                ValidationResult validation = validateLoadingRequest(agentIds);
                if (!validation.isValid()) {
                    return LoadingResult.failure("Validation failed: " + validation.getErrorMessage());
                }
                
                // Phase 2: Resolve loading order
                List<String> loadingOrder = resolveLoadingOrder(agentIds);
                
                // Phase 3: Load agents in dependency order
                List<WorkspaceAgent> loadedAgents = loadAgentsInOrder(loadingOrder);
                
                // Phase 4: Verify all dependencies are satisfied
                DependencyVerificationResult verification = verifyDependencies(loadedAgents);
                if (!verification.isValid()) {
                    return LoadingResult.failure("Dependency verification failed: " + verification.getErrorMessage());
                }
                
                statistics.recordSuccessfulLoading(agentIds.size(), loadingOrder.size());
                return LoadingResult.success(loadedAgents, loadingOrder);
                
            } catch (Exception e) {
                statistics.recordFailedLoading(agentIds.size());
                return LoadingResult.failure("Loading failed: " + e.getMessage());
            }
        }, loadingExecutor);
    }
    
    /**
     * Loads a single agent with its dependencies
     */
    public CompletableFuture<WorkspaceAgent> loadAgentWithDependencies(String agentId) {
        // Check if already loading
        CompletableFuture<WorkspaceAgent> existingFuture = loadingFutures.get(agentId);
        if (existingFuture != null) {
            return existingFuture;
        }
        
        CompletableFuture<WorkspaceAgent> future = CompletableFuture.supplyAsync(() -> {
            try {
                loadingStates.put(agentId, LoadingState.LOADING);
                
                // Get agent from registry
                Optional<WorkspaceAgentRegistry.RegisteredAgent> registeredAgent = registry.getRegisteredAgent(agentId);
                if (!registeredAgent.isPresent()) {
                    throw new AgentException(agentId, AgentErrorType.UNAVAILABLE, "Agent not found in registry");
                }
                
                WorkspaceAgent agent = registeredAgent.get().getAgent();
                
                // Load dependencies first
                List<String> dependencies = agent.getCoordinationDependencies();
                for (String dependencyId : dependencies) {
                    loadAgentWithDependencies(dependencyId).join(); // Wait for dependency
                }
                
                // Initialize agent if needed
                if (agent.getStatus() != AgentStatus.healthy(agentId)) {
                    initializeAgent(agent);
                }
                
                loadingStates.put(agentId, LoadingState.LOADED);
                statistics.recordAgentLoaded(agentId);
                
                return agent;
                
            } catch (Exception e) {
                loadingStates.put(agentId, LoadingState.FAILED);
                statistics.recordAgentLoadFailed(agentId);
                throw new RuntimeException("Failed to load agent: " + agentId, e);
            }
        }, loadingExecutor);
        
        loadingFutures.put(agentId, future);
        return future;
    }
    
    /**
     * Unloads agents in reverse dependency order
     */
    public CompletableFuture<UnloadingResult> unloadAgentsWithDependencies(Set<String> agentIds) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Find all dependents that need to be unloaded first
                Set<String> allToUnload = findAllDependents(agentIds);
                allToUnload.addAll(agentIds);
                
                // Resolve unloading order (reverse of loading order)
                List<String> unloadingOrder = resolveUnloadingOrder(allToUnload);
                
                // Unload agents in reverse dependency order
                List<String> unloadedAgents = unloadAgentsInOrder(unloadingOrder);
                
                statistics.recordSuccessfulUnloading(unloadedAgents.size());
                return UnloadingResult.success(unloadedAgents, unloadingOrder);
                
            } catch (Exception e) {
                statistics.recordFailedUnloading(agentIds.size());
                return UnloadingResult.failure("Unloading failed: " + e.getMessage());
            }
        }, loadingExecutor);
    }
    
    /**
     * Gets loading state for an agent
     */
    public LoadingState getLoadingState(String agentId) {
        return loadingStates.getOrDefault(agentId, LoadingState.NOT_LOADED);
    }
    
    /**
     * Gets loading statistics
     */
    public LoadingStatistics getStatistics() {
        return statistics.snapshot();
    }
    
    /**
     * Validates that all agents in the loading request can be loaded
     */
    private ValidationResult validateLoadingRequest(Set<String> agentIds) {
        for (String agentId : agentIds) {
            Optional<WorkspaceAgentRegistry.RegisteredAgent> registeredAgent = registry.getRegisteredAgent(agentId);
            if (!registeredAgent.isPresent()) {
                return ValidationResult.invalid("Agent not found: " + agentId);
            }
            
            WorkspaceAgent agent = registeredAgent.get().getAgent();
            if (!agent.getStatus().healthy()) {
                return ValidationResult.invalid("Agent not healthy: " + agentId);
            }
        }
        
        return ValidationResult.valid();
    }
    
    /**
     * Resolves the correct loading order based on dependencies
     */
    private List<String> resolveLoadingOrder(Set<String> agentIds) {
        // Expand to include all dependencies
        Set<String> allAgents = expandWithDependencies(agentIds);
        
        // Use registry's dependency resolution
        return registry.resolveLoadingOrder(allAgents);
    }
    
    /**
     * Expands agent set to include all transitive dependencies
     */
    private Set<String> expandWithDependencies(Set<String> agentIds) {
        Set<String> expanded = new HashSet<>(agentIds);
        Queue<String> toProcess = new LinkedList<>(agentIds);
        
        while (!toProcess.isEmpty()) {
            String agentId = toProcess.poll();
            
            Optional<WorkspaceAgentRegistry.RegisteredAgent> registeredAgent = registry.getRegisteredAgent(agentId);
            if (registeredAgent.isPresent()) {
                List<String> dependencies = registeredAgent.get().getAgent().getCoordinationDependencies();
                for (String dependency : dependencies) {
                    if (!expanded.contains(dependency)) {
                        expanded.add(dependency);
                        toProcess.offer(dependency);
                    }
                }
            }
        }
        
        return expanded;
    }
    
    /**
     * Loads agents in the specified order
     */
    private List<WorkspaceAgent> loadAgentsInOrder(List<String> loadingOrder) {
        List<WorkspaceAgent> loadedAgents = new ArrayList<>();
        
        for (String agentId : loadingOrder) {
            try {
                WorkspaceAgent agent = loadAgentWithDependencies(agentId).join();
                loadedAgents.add(agent);
            } catch (Exception e) {
                throw new RuntimeException("Failed to load agent in order: " + agentId, e);
            }
        }
        
        return loadedAgents;
    }
    
    /**
     * Initializes an agent if it's not already active
     */
    private void initializeAgent(WorkspaceAgent agent) throws AgentException {
        // Basic initialization - in a real implementation, this might involve
        // setting up connections, loading configuration, etc.
        AgentConfiguration config = agent.getConfiguration();
        if (config != null) {
            agent.updateConfiguration(config);
        }
    }
    
    /**
     * Verifies that all dependencies are satisfied after loading
     */
    private DependencyVerificationResult verifyDependencies(List<WorkspaceAgent> loadedAgents) {
        Map<String, WorkspaceAgent> agentMap = loadedAgents.stream()
            .collect(Collectors.toMap(WorkspaceAgent::getAgentId, agent -> agent));
        
        for (WorkspaceAgent agent : loadedAgents) {
            for (String dependencyId : agent.getCoordinationDependencies()) {
                WorkspaceAgent dependency = agentMap.get(dependencyId);
                if (dependency == null || !dependency.getStatus().healthy()) {
                    return DependencyVerificationResult.invalid(
                        "Dependency not satisfied: " + agent.getAgentId() + " -> " + dependencyId);
                }
            }
        }
        
        return DependencyVerificationResult.valid();
    }
    
    /**
     * Finds all agents that depend on the given agents (transitively)
     */
    private Set<String> findAllDependents(Set<String> agentIds) {
        Set<String> allDependents = new HashSet<>();
        Queue<String> toProcess = new LinkedList<>(agentIds);
        
        while (!toProcess.isEmpty()) {
            String agentId = toProcess.poll();
            
            // Find direct dependents
            List<WorkspaceAgentRegistry.RegisteredAgent> allAgents = registry.getAllRegisteredAgents();
            for (WorkspaceAgentRegistry.RegisteredAgent registeredAgent : allAgents) {
                WorkspaceAgent agent = registeredAgent.getAgent();
                if (agent.getCoordinationDependencies().contains(agentId)) {
                    String dependentId = agent.getAgentId();
                    if (!allDependents.contains(dependentId)) {
                        allDependents.add(dependentId);
                        toProcess.offer(dependentId);
                    }
                }
            }
        }
        
        return allDependents;
    }
    
    /**
     * Resolves unloading order (reverse of loading order)
     */
    private List<String> resolveUnloadingOrder(Set<String> agentIds) {
        List<String> loadingOrder = resolveLoadingOrder(agentIds);
        Collections.reverse(loadingOrder);
        return loadingOrder;
    }
    
    /**
     * Unloads agents in the specified order
     */
    private List<String> unloadAgentsInOrder(List<String> unloadingOrder) {
        List<String> unloadedAgents = new ArrayList<>();
        
        for (String agentId : unloadingOrder) {
            try {
                // Mark as unloading
                loadingStates.put(agentId, LoadingState.UNLOADING);
                
                // Remove from futures
                loadingFutures.remove(agentId);
                
                // Mark as not loaded
                loadingStates.put(agentId, LoadingState.NOT_LOADED);
                
                unloadedAgents.add(agentId);
                statistics.recordAgentUnloaded(agentId);
                
            } catch (Exception e) {
                loadingStates.put(agentId, LoadingState.FAILED);
                throw new RuntimeException("Failed to unload agent: " + agentId, e);
            }
        }
        
        return unloadedAgents;
    }
    
    /**
     * Shuts down the dependency loader
     */
    public void shutdown() {
        loadingExecutor.shutdown();
    }
    
    // Enums and inner classes
    
    public enum LoadingState {
        NOT_LOADED, LOADING, LOADED, UNLOADING, FAILED
    }
    
    public static class LoadingResult {
        private final boolean success;
        private final List<WorkspaceAgent> loadedAgents;
        private final List<String> loadingOrder;
        private final String errorMessage;
        
        private LoadingResult(boolean success, List<WorkspaceAgent> loadedAgents, 
                            List<String> loadingOrder, String errorMessage) {
            this.success = success;
            this.loadedAgents = loadedAgents != null ? loadedAgents : new ArrayList<>();
            this.loadingOrder = loadingOrder != null ? loadingOrder : new ArrayList<>();
            this.errorMessage = errorMessage;
        }
        
        public static LoadingResult success(List<WorkspaceAgent> loadedAgents, List<String> loadingOrder) {
            return new LoadingResult(true, loadedAgents, loadingOrder, null);
        }
        
        public static LoadingResult failure(String errorMessage) {
            return new LoadingResult(false, null, null, errorMessage);
        }
        
        public boolean isSuccess() { return success; }
        public List<WorkspaceAgent> getLoadedAgents() { return loadedAgents; }
        public List<String> getLoadingOrder() { return loadingOrder; }
        public String getErrorMessage() { return errorMessage; }
    }
    
    public static class UnloadingResult {
        private final boolean success;
        private final List<String> unloadedAgents;
        private final List<String> unloadingOrder;
        private final String errorMessage;
        
        private UnloadingResult(boolean success, List<String> unloadedAgents, 
                              List<String> unloadingOrder, String errorMessage) {
            this.success = success;
            this.unloadedAgents = unloadedAgents != null ? unloadedAgents : new ArrayList<>();
            this.unloadingOrder = unloadingOrder != null ? unloadingOrder : new ArrayList<>();
            this.errorMessage = errorMessage;
        }
        
        public static UnloadingResult success(List<String> unloadedAgents, List<String> unloadingOrder) {
            return new UnloadingResult(true, unloadedAgents, unloadingOrder, null);
        }
        
        public static UnloadingResult failure(String errorMessage) {
            return new UnloadingResult(false, null, null, errorMessage);
        }
        
        public boolean isSuccess() { return success; }
        public List<String> getUnloadedAgents() { return unloadedAgents; }
        public List<String> getUnloadingOrder() { return unloadingOrder; }
        public String getErrorMessage() { return errorMessage; }
    }
    
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
        
        public boolean isValid() { return valid; }
        public String getErrorMessage() { return errorMessage; }
    }
    
    public static class DependencyVerificationResult {
        private final boolean valid;
        private final String errorMessage;
        
        private DependencyVerificationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }
        
        public static DependencyVerificationResult valid() {
            return new DependencyVerificationResult(true, null);
        }
        
        public static DependencyVerificationResult invalid(String errorMessage) {
            return new DependencyVerificationResult(false, errorMessage);
        }
        
        public boolean isValid() { return valid; }
        public String getErrorMessage() { return errorMessage; }
    }
}