package core;

import java.util.Set;
import java.util.Map;

/**
 * Defines the capabilities and supported operations of a workspace agent
 */
public class AgentCapabilities {
    private final String agentType;
    private final Set<String> supportedOperations;
    private final Map<String, String> operationDescriptions;
    private final Set<String> requiredDependencies;
    private final int maxConcurrentOperations;
    
    public AgentCapabilities(String agentType, 
                           Set<String> supportedOperations,
                           Map<String, String> operationDescriptions,
                           Set<String> requiredDependencies,
                           int maxConcurrentOperations) {
        this.agentType = agentType;
        this.supportedOperations = supportedOperations;
        this.operationDescriptions = operationDescriptions;
        this.requiredDependencies = requiredDependencies;
        this.maxConcurrentOperations = maxConcurrentOperations;
    }
    
    public String getAgentType() { return agentType; }
    public Set<String> getSupportedOperations() { return supportedOperations; }
    public Map<String, String> getOperationDescriptions() { return operationDescriptions; }
    public Set<String> getRequiredDependencies() { return requiredDependencies; }
    public int getMaxConcurrentOperations() { return maxConcurrentOperations; }
    
    public boolean supportsOperation(String operation) {
        return supportedOperations.contains(operation);
    }
}
