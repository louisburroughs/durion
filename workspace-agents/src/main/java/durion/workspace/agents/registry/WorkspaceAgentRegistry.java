package durion.workspace.agents.registry;

import durion.workspace.agents.core.*;
import durion.workspace.agents.discovery.AgentMetadata;
import durion.workspace.agents.monitoring.PerformanceMonitor;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * Central registry for workspace agent metadata management and capability registration.
 * Provides cross-layer agent dependency resolution and loading system.
 * 
 * Requirements: 1.1 - Workspace agent registry system for managing cross-layer agents
 */
public class WorkspaceAgentRegistry {
    
    private final Map<String, RegisteredAgent> registeredAgents;
    private final Map<AgentCapability, Set<String>> capabilityIndex;
    private final Map<String, Set<String>> dependencyGraph;
    private final Map<String, AgentVersion> versionRegistry;
    private final ReadWriteLock registryLock;
    private final PerformanceMonitor performanceMonitor;
    
    // Registry statistics
    private final RegistryStatistics statistics;
    
    public WorkspaceAgentRegistry(PerformanceMonitor performanceMonitor) {
        this.registeredAgents = new ConcurrentHashMap<>();
        this.capabilityIndex = new ConcurrentHashMap<>();
        this.dependencyGraph = new ConcurrentHashMap<>();
        this.versionRegistry = new ConcurrentHashMap<>();
        this.registryLock = new ReentrantReadWriteLock();
        this.performanceMonitor = performanceMonitor;
        this.statistics = new RegistryStatistics();
        
        initializeCapabilityIndex();
    }
    
    /**
     * Registers a workspace agent with metadata and capability information
     */
    public RegistrationResult registerAgent(WorkspaceAgent agent, AgentMetadata metadata) {
        registryLock.writeLock().lock();
        try {
            String agentId = agent.getAgentId();
            
            // Check if agent is already registered
            if (registeredAgents.containsKey(agentId)) {
                return RegistrationResult.failure(agentId, "Agent already registered");
            }
            
            // Validate agent dependencies
            ValidationResult dependencyValidation = validateDependencies(agent, metadata);
            if (!dependencyValidation.isValid()) {
                return RegistrationResult.failure(agentId, 
                    "Dependency validation failed: " + dependencyValidation.getErrorMessage());
            }
            
            // Create registered agent entry
            RegisteredAgent registeredAgent = new RegisteredAgent(agent, metadata, Instant.now());
            registeredAgents.put(agentId, registeredAgent);
            
            // Update capability index
            updateCapabilityIndex(agent);
            
            // Update dependency graph
            updateDependencyGraph(agent);
            
            // Register version information
            registerVersion(agentId, metadata.getVersion());
            
            // Set performance monitor
            agent.setPerformanceMonitor(performanceMonitor);
            
            // Update statistics
            statistics.recordRegistration(agentId, metadata.getAgentType());
            
            return RegistrationResult.success(agentId, "Agent registered successfully");
            
        } finally {
            registryLock.writeLock().unlock();
        }
    }
    
    /**
     * Unregisters an agent from the registry
     */
    public RegistrationResult unregisterAgent(String agentId) {
        registryLock.writeLock().lock();
        try {
            RegisteredAgent registeredAgent = registeredAgents.get(agentId);
            if (registeredAgent == null) {
                return RegistrationResult.failure(agentId, "Agent not found");
            }
            
            // Check if other agents depend on this one
            Set<String> dependents = findDependents(agentId);
            if (!dependents.isEmpty()) {
                return RegistrationResult.failure(agentId, 
                    "Cannot unregister agent with dependents: " + dependents);
            }
            
            // Remove from registry
            registeredAgents.remove(agentId);
            
            // Remove from capability index
            removeFromCapabilityIndex(registeredAgent.getAgent());
            
            // Remove from dependency graph
            dependencyGraph.remove(agentId);
            
            // Remove version information
            versionRegistry.remove(agentId);
            
            // Update statistics
            statistics.recordUnregistration(agentId);
            
            return RegistrationResult.success(agentId, "Agent unregistered successfully");
            
        } finally {
            registryLock.writeLock().unlock();
        }
    }
    
    /**
     * Discovers agents by capability with dependency resolution
     */
    public List<WorkspaceAgent> discoverAgentsByCapability(AgentCapability capability) {
        registryLock.readLock().lock();
        try {
            Set<String> agentIds = capabilityIndex.get(capability);
            if (agentIds == null || agentIds.isEmpty()) {
                return new ArrayList<>();
            }
            
            // Get agents and resolve dependencies
            List<WorkspaceAgent> agents = agentIds.stream()
                .map(id -> registeredAgents.get(id))
                .filter(Objects::nonNull)
                .filter(ra -> ra.getAgent().getStatus().healthy())
                .map(RegisteredAgent::getAgent)
                .collect(Collectors.toList());
            
            // Sort by dependency order (dependencies first)
            return resolveDependencyOrder(agents);
            
        } finally {
            registryLock.readLock().unlock();
        }
    }
    
    /**
     * Gets agent by ID with metadata
     */
    public Optional<RegisteredAgent> getRegisteredAgent(String agentId) {
        registryLock.readLock().lock();
        try {
            return Optional.ofNullable(registeredAgents.get(agentId));
        } finally {
            registryLock.readLock().unlock();
        }
    }
    
    /**
     * Gets all registered agents
     */
    public List<RegisteredAgent> getAllRegisteredAgents() {
        registryLock.readLock().lock();
        try {
            return new ArrayList<>(registeredAgents.values());
        } finally {
            registryLock.readLock().unlock();
        }
    }
    
    /**
     * Gets agents by type
     */
    public List<WorkspaceAgent> getAgentsByType(AgentType agentType) {
        registryLock.readLock().lock();
        try {
            return registeredAgents.values().stream()
                .filter(ra -> ra.getMetadata().getAgentType() == agentType)
                .filter(ra -> ra.getAgent().getStatus().healthy())
                .map(RegisteredAgent::getAgent)
                .collect(Collectors.toList());
        } finally {
            registryLock.readLock().unlock();
        }
    }
    
    /**
     * Resolves agent loading order based on dependencies
     */
    public List<String> resolveLoadingOrder(Set<String> agentIds) {
        registryLock.readLock().lock();
        try {
            return topologicalSort(agentIds);
        } finally {
            registryLock.readLock().unlock();
        }
    }
    
    /**
     * Updates agent version and handles version compatibility
     */
    public VersionUpdateResult updateAgentVersion(String agentId, String newVersion) {
        registryLock.writeLock().lock();
        try {
            RegisteredAgent registeredAgent = registeredAgents.get(agentId);
            if (registeredAgent == null) {
                return VersionUpdateResult.failure(agentId, "Agent not found");
            }
            
            // Check version compatibility with dependents
            CompatibilityResult compatibility = checkVersionCompatibility(agentId, newVersion);
            if (!compatibility.isCompatible()) {
                return VersionUpdateResult.failure(agentId, 
                    "Version incompatible with dependents: " + compatibility.getIncompatibleAgents());
            }
            
            // Update version registry
            AgentVersion oldVersion = versionRegistry.get(agentId);
            AgentVersion newAgentVersion = new AgentVersion(newVersion, Instant.now());
            versionRegistry.put(agentId, newAgentVersion);
            
            // Update metadata
            AgentMetadata updatedMetadata = registeredAgent.getMetadata().withVersion(newVersion);
            RegisteredAgent updatedAgent = new RegisteredAgent(
                registeredAgent.getAgent(), 
                updatedMetadata, 
                registeredAgent.getRegistrationTime()
            );
            registeredAgents.put(agentId, updatedAgent);
            
            // Record version update
            statistics.recordVersionUpdate(agentId, oldVersion.getVersion(), newVersion);
            
            return VersionUpdateResult.success(agentId, oldVersion.getVersion(), newVersion);
            
        } finally {
            registryLock.writeLock().unlock();
        }
    }
    
    /**
     * Gets version information for an agent
     */
    public Optional<AgentVersion> getAgentVersion(String agentId) {
        return Optional.ofNullable(versionRegistry.get(agentId));
    }
    
    /**
     * Gets registry statistics
     */
    public RegistryStatistics getStatistics() {
        return statistics.snapshot();
    }
    
    /**
     * Validates agent dependencies
     */
    private ValidationResult validateDependencies(WorkspaceAgent agent, AgentMetadata metadata) {
        List<String> dependencies = agent.getCoordinationDependencies();
        
        for (String dependencyId : dependencies) {
            RegisteredAgent dependency = registeredAgents.get(dependencyId);
            if (dependency == null) {
                return ValidationResult.invalid("Dependency not found: " + dependencyId);
            }
            
            // Check version compatibility
            if (!isVersionCompatible(dependency.getMetadata().getVersion(), metadata.getRequiredVersions().get(dependencyId))) {
                return ValidationResult.invalid("Version incompatible with dependency: " + dependencyId);
            }
        }
        
        // Check for circular dependencies
        if (wouldCreateCircularDependency(agent.getAgentId(), dependencies)) {
            return ValidationResult.invalid("Would create circular dependency");
        }
        
        return ValidationResult.valid();
    }
    
    /**
     * Updates capability index when agent is registered
     */
    private void updateCapabilityIndex(WorkspaceAgent agent) {
        for (AgentCapability capability : agent.getCapabilities()) {
            capabilityIndex.computeIfAbsent(capability, k -> ConcurrentHashMap.newKeySet())
                          .add(agent.getAgentId());
        }
    }
    
    /**
     * Removes agent from capability index
     */
    private void removeFromCapabilityIndex(WorkspaceAgent agent) {
        for (AgentCapability capability : agent.getCapabilities()) {
            Set<String> agentIds = capabilityIndex.get(capability);
            if (agentIds != null) {
                agentIds.remove(agent.getAgentId());
            }
        }
    }
    
    /**
     * Updates dependency graph
     */
    private void updateDependencyGraph(WorkspaceAgent agent) {
        String agentId = agent.getAgentId();
        Set<String> dependencies = new HashSet<>(agent.getCoordinationDependencies());
        dependencyGraph.put(agentId, dependencies);
    }
    
    /**
     * Finds agents that depend on the given agent
     */
    private Set<String> findDependents(String agentId) {
        return dependencyGraph.entrySet().stream()
            .filter(entry -> entry.getValue().contains(agentId))
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());
    }
    
    /**
     * Resolves dependency order for agents
     */
    private List<WorkspaceAgent> resolveDependencyOrder(List<WorkspaceAgent> agents) {
        Set<String> agentIds = agents.stream()
            .map(WorkspaceAgent::getAgentId)
            .collect(Collectors.toSet());
        
        List<String> orderedIds = topologicalSort(agentIds);
        
        Map<String, WorkspaceAgent> agentMap = agents.stream()
            .collect(Collectors.toMap(WorkspaceAgent::getAgentId, agent -> agent));
        
        return orderedIds.stream()
            .map(agentMap::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }
    
    /**
     * Performs topological sort for dependency resolution
     */
    private List<String> topologicalSort(Set<String> agentIds) {
        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, Set<String>> adjacencyList = new HashMap<>();
        
        // Initialize
        for (String agentId : agentIds) {
            inDegree.put(agentId, 0);
            adjacencyList.put(agentId, new HashSet<>());
        }
        
        // Build graph and calculate in-degrees
        for (String agentId : agentIds) {
            Set<String> dependencies = dependencyGraph.get(agentId);
            if (dependencies != null) {
                for (String dependency : dependencies) {
                    if (agentIds.contains(dependency)) {
                        adjacencyList.get(dependency).add(agentId);
                        inDegree.put(agentId, inDegree.get(agentId) + 1);
                    }
                }
            }
        }
        
        // Kahn's algorithm
        Queue<String> queue = new LinkedList<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.offer(entry.getKey());
            }
        }
        
        List<String> result = new ArrayList<>();
        while (!queue.isEmpty()) {
            String current = queue.poll();
            result.add(current);
            
            for (String dependent : adjacencyList.get(current)) {
                inDegree.put(dependent, inDegree.get(dependent) - 1);
                if (inDegree.get(dependent) == 0) {
                    queue.offer(dependent);
                }
            }
        }
        
        return result;
    }
    
    /**
     * Checks if adding dependencies would create a circular dependency
     */
    private boolean wouldCreateCircularDependency(String agentId, List<String> newDependencies) {
        // Create temporary graph with new dependencies
        Map<String, Set<String>> tempGraph = new HashMap<>(dependencyGraph);
        tempGraph.put(agentId, new HashSet<>(newDependencies));
        
        // Check for cycles using DFS
        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();
        
        for (String node : tempGraph.keySet()) {
            if (!visited.contains(node)) {
                if (hasCycleDFS(node, tempGraph, visited, recursionStack)) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * DFS helper for cycle detection
     */
    private boolean hasCycleDFS(String node, Map<String, Set<String>> graph, 
                               Set<String> visited, Set<String> recursionStack) {
        visited.add(node);
        recursionStack.add(node);
        
        Set<String> neighbors = graph.get(node);
        if (neighbors != null) {
            for (String neighbor : neighbors) {
                if (!visited.contains(neighbor)) {
                    if (hasCycleDFS(neighbor, graph, visited, recursionStack)) {
                        return true;
                    }
                } else if (recursionStack.contains(neighbor)) {
                    return true;
                }
            }
        }
        
        recursionStack.remove(node);
        return false;
    }
    
    /**
     * Registers version information
     */
    private void registerVersion(String agentId, String version) {
        versionRegistry.put(agentId, new AgentVersion(version, Instant.now()));
    }
    
    /**
     * Checks version compatibility
     */
    private CompatibilityResult checkVersionCompatibility(String agentId, String newVersion) {
        Set<String> dependents = findDependents(agentId);
        List<String> incompatibleAgents = new ArrayList<>();
        
        for (String dependentId : dependents) {
            RegisteredAgent dependent = registeredAgents.get(dependentId);
            if (dependent != null) {
                String requiredVersion = dependent.getMetadata().getRequiredVersions().get(agentId);
                if (requiredVersion != null && !isVersionCompatible(newVersion, requiredVersion)) {
                    incompatibleAgents.add(dependentId);
                }
            }
        }
        
        return incompatibleAgents.isEmpty() ? 
            CompatibilityResult.compatible() : 
            CompatibilityResult.incompatible(incompatibleAgents);
    }
    
    /**
     * Checks if versions are compatible (simple semantic versioning)
     */
    private boolean isVersionCompatible(String actualVersion, String requiredVersion) {
        if (requiredVersion == null) return true;
        
        // Simple semantic versioning compatibility check
        // For now, just check if major versions match
        String[] actualParts = actualVersion.split("\\.");
        String[] requiredParts = requiredVersion.split("\\.");
        
        if (actualParts.length > 0 && requiredParts.length > 0) {
            return actualParts[0].equals(requiredParts[0]);
        }
        
        return actualVersion.equals(requiredVersion);
    }
    
    /**
     * Initializes capability index
     */
    private void initializeCapabilityIndex() {
        for (AgentCapability capability : AgentCapability.values()) {
            capabilityIndex.put(capability, ConcurrentHashMap.newKeySet());
        }
    }
    
    // Inner classes for registry data structures
    
    public static class RegisteredAgent {
        private final WorkspaceAgent agent;
        private final AgentMetadata metadata;
        private final Instant registrationTime;
        
        public RegisteredAgent(WorkspaceAgent agent, AgentMetadata metadata, Instant registrationTime) {
            this.agent = agent;
            this.metadata = metadata;
            this.registrationTime = registrationTime;
        }
        
        public WorkspaceAgent getAgent() { return agent; }
        public AgentMetadata getMetadata() { return metadata; }
        public Instant getRegistrationTime() { return registrationTime; }
    }
    
    public static class RegistrationResult {
        private final boolean success;
        private final String agentId;
        private final String message;
        
        private RegistrationResult(boolean success, String agentId, String message) {
            this.success = success;
            this.agentId = agentId;
            this.message = message;
        }
        
        public static RegistrationResult success(String agentId, String message) {
            return new RegistrationResult(true, agentId, message);
        }
        
        public static RegistrationResult failure(String agentId, String message) {
            return new RegistrationResult(false, agentId, message);
        }
        
        public boolean isSuccess() { return success; }
        public String getAgentId() { return agentId; }
        public String getMessage() { return message; }
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
    
    public static class VersionUpdateResult {
        private final boolean success;
        private final String agentId;
        private final String oldVersion;
        private final String newVersion;
        private final String message;
        
        private VersionUpdateResult(boolean success, String agentId, String oldVersion, 
                                  String newVersion, String message) {
            this.success = success;
            this.agentId = agentId;
            this.oldVersion = oldVersion;
            this.newVersion = newVersion;
            this.message = message;
        }
        
        public static VersionUpdateResult success(String agentId, String oldVersion, String newVersion) {
            return new VersionUpdateResult(true, agentId, oldVersion, newVersion, 
                "Version updated successfully");
        }
        
        public static VersionUpdateResult failure(String agentId, String message) {
            return new VersionUpdateResult(false, agentId, null, null, message);
        }
        
        public boolean isSuccess() { return success; }
        public String getAgentId() { return agentId; }
        public String getOldVersion() { return oldVersion; }
        public String getNewVersion() { return newVersion; }
        public String getMessage() { return message; }
    }
    
    public static class CompatibilityResult {
        private final boolean compatible;
        private final List<String> incompatibleAgents;
        
        private CompatibilityResult(boolean compatible, List<String> incompatibleAgents) {
            this.compatible = compatible;
            this.incompatibleAgents = incompatibleAgents != null ? incompatibleAgents : new ArrayList<>();
        }
        
        public static CompatibilityResult compatible() {
            return new CompatibilityResult(true, null);
        }
        
        public static CompatibilityResult incompatible(List<String> incompatibleAgents) {
            return new CompatibilityResult(false, incompatibleAgents);
        }
        
        public boolean isCompatible() { return compatible; }
        public List<String> getIncompatibleAgents() { return incompatibleAgents; }
    }
}