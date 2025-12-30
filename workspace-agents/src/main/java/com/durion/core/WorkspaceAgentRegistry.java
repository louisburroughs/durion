package com.durion.core;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Registry for managing workspace agents with capability tracking
 * Supports 13 cross-layer agents with performance monitoring
 */
public class WorkspaceAgentRegistry {
    private final Map<String, WorkspaceAgent> agents = new ConcurrentHashMap<>();
    private final Map<String, AgentCapabilities> capabilities = new ConcurrentHashMap<>();
    private final Map<String, AgentMetrics> metrics = new ConcurrentHashMap<>();
    
    /**
     * Register a workspace agent
     */
    public void registerAgent(WorkspaceAgent agent) {
        String agentId = agent.getAgentId();
        agents.put(agentId, agent);
        capabilities.put(agentId, agent.getCapabilities());
        updateMetrics(agentId, agent.getMetrics());
    }
    
    /**
     * Unregister a workspace agent
     */
    public void unregisterAgent(String agentId) {
        WorkspaceAgent agent = agents.remove(agentId);
        if (agent != null) {
            agent.shutdown();
        }
        capabilities.remove(agentId);
        metrics.remove(agentId);
    }
    
    /**
     * Get agent by ID
     */
    public Optional<WorkspaceAgent> getAgent(String agentId) {
        return Optional.ofNullable(agents.get(agentId));
    }
    
    /**
     * Find agents that support a specific operation
     */
    public List<WorkspaceAgent> findAgentsForOperation(String operation) {
        return capabilities.entrySet().stream()
            .filter(entry -> entry.getValue().supportsOperation(operation))
            .map(entry -> agents.get(entry.getKey()))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }
    
    /**
     * Get all registered agents
     */
    public Collection<WorkspaceAgent> getAllAgents() {
        return new ArrayList<>(agents.values());
    }
    
    /**
     * Get agent capabilities
     */
    public Optional<AgentCapabilities> getCapabilities(String agentId) {
        return Optional.ofNullable(capabilities.get(agentId));
    }
    
    /**
     * Update agent metrics
     */
    public void updateMetrics(String agentId, AgentMetrics newMetrics) {
        metrics.put(agentId, newMetrics);
    }
    
    /**
     * Get agent metrics
     */
    public Optional<AgentMetrics> getMetrics(String agentId) {
        return Optional.ofNullable(metrics.get(agentId));
    }
    
    /**
     * Get health status of all agents
     */
    public Map<String, AgentHealth> getHealthStatus() {
        return agents.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().getHealth()
            ));
    }
    
    /**
     * Check if registry meets performance targets
     */
    public boolean meetsPerformanceTargets() {
        return metrics.values().stream()
            .allMatch(AgentMetrics::meetsPerformanceTargets);
    }
    
    /**
     * Get count of registered agents (target: 13)
     */
    public int getAgentCount() {
        return agents.size();
    }
    
    /**
     * Execute operation on best available agent
     */
    public CompletableFuture<AgentResult> executeOperation(String operation, Map<String, Object> parameters) {
        List<WorkspaceAgent> availableAgents = findAgentsForOperation(operation);
        
        if (availableAgents.isEmpty()) {
            return CompletableFuture.completedFuture(
                AgentResult.failure("No agents available for operation: " + operation, 0)
            );
        }
        
        // Select agent with best performance metrics
        WorkspaceAgent bestAgent = availableAgents.stream()
            .min(Comparator.comparing(agent -> 
                getMetrics(agent.getAgentId())
                    .map(m -> m.getAverageResponseTime().toMillis())
                    .orElse(Long.MAX_VALUE)))
            .orElse(availableAgents.get(0));
        
        return bestAgent.execute(operation, parameters);
    }
}
