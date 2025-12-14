package durion.workspace.agents.coordination;

import durion.workspace.agents.core.*;

import java.util.Set;
import java.util.HashSet;
import java.util.function.Predicate;

/**
 * Defines rules for agent coordination based on request characteristics
 */
public class AgentCoordinationRule {
    
    private final String ruleId;
    private final Set<String> requiredAgents;
    private final Set<Predicate<AgentRequest>> conditions;
    private final Set<AgentCapability> triggerCapabilities;
    private final Set<String> triggerRequestTypes;
    private boolean requiresCrossProject;
    
    public AgentCoordinationRule(String ruleId) {
        this.ruleId = ruleId;
        this.requiredAgents = new HashSet<>();
        this.conditions = new HashSet<>();
        this.triggerCapabilities = new HashSet<>();
        this.triggerRequestTypes = new HashSet<>();
        this.requiresCrossProject = false;
    }
    
    /**
     * Adds a required agent to this coordination rule
     */
    public AgentCoordinationRule requireAgent(String agentId) {
        requiredAgents.add(agentId);
        return this;
    }
    
    /**
     * Adds a capability trigger for this rule
     */
    public AgentCoordinationRule whenCapability(AgentCapability capability) {
        triggerCapabilities.add(capability);
        return this;
    }
    
    /**
     * Adds a request type trigger for this rule
     */
    public AgentCoordinationRule whenRequestType(String requestType) {
        triggerRequestTypes.add(requestType);
        return this;
    }
    
    /**
     * Sets this rule to trigger only for cross-project requests
     */
    public AgentCoordinationRule whenCrossProject() {
        requiresCrossProject = true;
        return this;
    }
    
    /**
     * Adds a custom condition for this rule
     */
    public AgentCoordinationRule whenCondition(Predicate<AgentRequest> condition) {
        conditions.add(condition);
        return this;
    }
    
    /**
     * Checks if this rule applies to the given agent and request
     */
    public boolean applies(WorkspaceAgent primaryAgent, AgentRequest request) {
        // Check capability triggers
        if (!triggerCapabilities.isEmpty() && 
            !triggerCapabilities.contains(request.getRequiredCapability())) {
            return false;
        }
        
        // Check request type triggers
        if (!triggerRequestTypes.isEmpty() && 
            !triggerRequestTypes.contains(request.getRequestType())) {
            return false;
        }
        
        // Check cross-project requirement
        if (requiresCrossProject && !request.isCrossProject()) {
            return false;
        }
        
        // Check custom conditions
        for (Predicate<AgentRequest> condition : conditions) {
            if (!condition.test(request)) {
                return false;
            }
        }
        
        return true;
    }
    
    // Getters
    public String getRuleId() { return ruleId; }
    public Set<String> getRequiredAgents() { return new HashSet<>(requiredAgents); }
    
    /**
     * Gets the priority of this rule (higher number = higher priority)
     */
    public int getPriority() {
        int priority = 0;
        
        // Higher priority for more specific rules
        if (!triggerCapabilities.isEmpty()) priority += 10;
        if (!triggerRequestTypes.isEmpty()) priority += 10;
        if (requiresCrossProject) priority += 5;
        if (!conditions.isEmpty()) priority += conditions.size() * 5;
        
        return priority;
    }
    
    @Override
    public String toString() {
        return String.format("AgentCoordinationRule{id='%s', requiredAgents=%s, priority=%d}",
                ruleId, requiredAgents, getPriority());
    }
}