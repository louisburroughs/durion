package durion.workspace.agents.coordination;

import durion.workspace.agents.core.*;

import java.util.Set;
import java.util.UUID;

/**
 * Represents a coordination workflow between multiple agents
 */
public class CoordinationWorkflow {
    
    private final String coordinationId;
    private final WorkspaceAgent primaryAgent;
    private final Set<WorkspaceAgent> dependentAgents;
    private final AgentRequest originalRequest;
    
    public CoordinationWorkflow(String coordinationId, WorkspaceAgent primaryAgent,
                               Set<WorkspaceAgent> dependentAgents, AgentRequest originalRequest) {
        this.coordinationId = coordinationId;
        this.primaryAgent = primaryAgent;
        this.dependentAgents = dependentAgents;
        this.originalRequest = originalRequest;
    }
    
    public String getCoordinationId() {
        return coordinationId;
    }
    
    public WorkspaceAgent getPrimaryAgent() {
        return primaryAgent;
    }
    
    public Set<WorkspaceAgent> getDependentAgents() {
        return dependentAgents;
    }
    
    public AgentRequest getRequest() {
        return originalRequest;
    }
    
    /**
     * Creates a preparation request for dependent agents
     */
    public AgentRequest createPreparationRequest() {
        String prepRequestId = UUID.randomUUID().toString();
        AgentRequest prepRequest = new AgentRequest(
            prepRequestId,
            "coordination-preparation",
            "Prepare for coordination: " + originalRequest.getDescription(),
            originalRequest.getRequiredCapability(),
            originalRequest.getSourceProject(),
            originalRequest.getTargetProject(),
            originalRequest.getPriority()
        );
        
        // Copy relevant parameters
        prepRequest.addParameter("original-request-id", originalRequest.getRequestId());
        prepRequest.addParameter("coordination-id", coordinationId);
        prepRequest.addParameter("primary-agent", primaryAgent.getAgentId());
        
        return prepRequest;
    }
    
    /**
     * Gets the total number of agents involved
     */
    public int getTotalAgentCount() {
        return 1 + dependentAgents.size(); // primary + dependents
    }
    
    /**
     * Checks if this is a complex coordination (multiple agents)
     */
    public boolean isComplexCoordination() {
        return dependentAgents.size() > 1;
    }
    
    @Override
    public String toString() {
        return String.format("CoordinationWorkflow{id='%s', primary='%s', dependents=%d, request='%s'}",
                coordinationId, primaryAgent.getAgentId(), dependentAgents.size(), 
                originalRequest.getRequestType());
    }
}