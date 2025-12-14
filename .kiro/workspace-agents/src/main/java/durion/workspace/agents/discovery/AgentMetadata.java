package durion.workspace.agents.discovery;

import durion.workspace.agents.core.*;

import java.time.Instant;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;

/**
 * Metadata about an agent for discovery and routing purposes
 */
public class AgentMetadata {
    
    private final String agentId;
    private final AgentType agentType;
    private final Set<AgentCapability> capabilities;
    private final Set<String> supportedTechnologies;
    private final Set<String> supportedProjects;
    private final boolean crossProjectSupport;
    private final boolean workspaceLevel;
    private final Instant registrationTime;
    private final String version;
    private final Map<String, String> requiredVersions;
    
    public AgentMetadata(WorkspaceAgent agent) {
        this(agent, "1.0.0", new HashMap<>());
    }
    
    public AgentMetadata(WorkspaceAgent agent, String version, Map<String, String> requiredVersions) {
        this.agentId = agent.getAgentId();
        this.agentType = agent.getAgentType();
        this.capabilities = new HashSet<>(agent.getCapabilities());
        this.supportedTechnologies = determineSupportedTechnologies(agent);
        this.supportedProjects = determineSupportedProjects(agent);
        this.crossProjectSupport = determineCrossProjectSupport(agent);
        this.workspaceLevel = determineWorkspaceLevel(agent);
        this.registrationTime = Instant.now();
        this.version = version;
        this.requiredVersions = new HashMap<>(requiredVersions);
    }
    
    private AgentMetadata(String agentId, AgentType agentType, Set<AgentCapability> capabilities,
                         Set<String> supportedTechnologies, Set<String> supportedProjects,
                         boolean crossProjectSupport, boolean workspaceLevel, Instant registrationTime,
                         String version, Map<String, String> requiredVersions) {
        this.agentId = agentId;
        this.agentType = agentType;
        this.capabilities = new HashSet<>(capabilities);
        this.supportedTechnologies = new HashSet<>(supportedTechnologies);
        this.supportedProjects = new HashSet<>(supportedProjects);
        this.crossProjectSupport = crossProjectSupport;
        this.workspaceLevel = workspaceLevel;
        this.registrationTime = registrationTime;
        this.version = version;
        this.requiredVersions = new HashMap<>(requiredVersions);
    }
    
    /**
     * Determines supported technologies based on agent type and capabilities
     */
    private Set<String> determineSupportedTechnologies(WorkspaceAgent agent) {
        Set<String> technologies = new HashSet<>();
        
        // Base technologies all workspace agents support
        technologies.add("rest-api");
        technologies.add("jwt");
        
        // Technology-specific support based on agent type
        switch (agent.getAgentType()) {
            case WORKSPACE_COORDINATION:
                technologies.add("spring-boot");
                technologies.add("moqui");
                technologies.add("vue");
                technologies.add("postgresql");
                technologies.add("aws");
                break;
                
            case TECHNOLOGY_BRIDGE:
                technologies.add("spring-boot");
                technologies.add("moqui");
                technologies.add("openapi");
                technologies.add("graphql");
                break;
                
            case OPERATIONAL_COORDINATION:
                technologies.add("docker");
                technologies.add("aws");
                technologies.add("kubernetes");
                technologies.add("grafana");
                technologies.add("prometheus");
                break;
                
            case GOVERNANCE_COMPLIANCE:
                technologies.add("markdown");
                technologies.add("openapi");
                technologies.add("json-schema");
                break;
        }
        
        // Capability-specific technologies
        if (agent.hasCapability(AgentCapability.API_CONTRACT_MANAGEMENT)) {
            technologies.add("openapi");
            technologies.add("swagger");
        }
        
        if (agent.hasCapability(AgentCapability.DATA_INTEGRATION)) {
            technologies.add("postgresql");
            technologies.add("mysql");
            technologies.add("mongodb");
        }
        
        if (agent.hasCapability(AgentCapability.FRONTEND_BACKEND_BRIDGE)) {
            technologies.add("vue");
            technologies.add("react");
            technologies.add("angular");
        }
        
        return technologies;
    }
    
    /**
     * Determines supported projects based on agent capabilities
     */
    private Set<String> determineSupportedProjects(WorkspaceAgent agent) {
        Set<String> projects = new HashSet<>();
        
        // Workspace-level agents support all projects
        if (agent.getAgentType() == AgentType.WORKSPACE_COORDINATION) {
            projects.add("positivity");
            projects.add("moqui_example");
            projects.add("workspace");
        }
        
        // Technology bridge agents support cross-project scenarios
        else if (agent.getAgentType() == AgentType.TECHNOLOGY_BRIDGE) {
            projects.add("positivity");
            projects.add("moqui_example");
        }
        
        // Operational agents support all projects for deployment/monitoring
        else if (agent.getAgentType() == AgentType.OPERATIONAL_COORDINATION) {
            projects.add("positivity");
            projects.add("moqui_example");
            projects.add("workspace");
        }
        
        // Governance agents support all projects for compliance
        else if (agent.getAgentType() == AgentType.GOVERNANCE_COMPLIANCE) {
            projects.add("positivity");
            projects.add("moqui_example");
            projects.add("workspace");
        }
        
        return projects;
    }
    
    /**
     * Determines if agent supports cross-project coordination
     */
    private boolean determineCrossProjectSupport(WorkspaceAgent agent) {
        return agent.getAgentType() == AgentType.WORKSPACE_COORDINATION ||
               agent.getAgentType() == AgentType.TECHNOLOGY_BRIDGE ||
               agent.hasCapability(AgentCapability.FULL_STACK_INTEGRATION) ||
               agent.hasCapability(AgentCapability.API_CONTRACT_MANAGEMENT);
    }
    
    /**
     * Determines if agent operates at workspace level
     */
    private boolean determineWorkspaceLevel(WorkspaceAgent agent) {
        return agent.getAgentType() == AgentType.WORKSPACE_COORDINATION ||
               agent.hasCapability(AgentCapability.ARCHITECTURAL_CONSISTENCY) ||
               agent.hasCapability(AgentCapability.WORKFLOW_COORDINATION);
    }
    
    // Getters
    public String getAgentId() { return agentId; }
    public AgentType getAgentType() { return agentType; }
    public Set<AgentCapability> getCapabilities() { return new HashSet<>(capabilities); }
    public Set<String> getSupportedTechnologies() { return new HashSet<>(supportedTechnologies); }
    public Set<String> getSupportedProjects() { return new HashSet<>(supportedProjects); }
    public boolean supportsCrossProject() { return crossProjectSupport; }
    public boolean isWorkspaceLevel() { return workspaceLevel; }
    public Instant getRegistrationTime() { return registrationTime; }
    public String getVersion() { return version; }
    public Map<String, String> getRequiredVersions() { return new HashMap<>(requiredVersions); }
    
    /**
     * Checks if agent supports a specific technology
     */
    public boolean supportsTechnology(String technology) {
        return supportedTechnologies.contains(technology) || isGeneralPurpose();
    }
    
    /**
     * Checks if agent supports a specific project
     */
    public boolean supportsProject(String project) {
        return supportedProjects.contains(project) || isWorkspaceLevel();
    }
    
    /**
     * Checks if agent supports positivity backend
     */
    public boolean supportsPositivity() {
        return supportedProjects.contains("positivity");
    }
    
    /**
     * Checks if agent supports moqui_example frontend
     */
    public boolean supportsMoqui() {
        return supportedProjects.contains("moqui_example");
    }
    
    /**
     * Checks if agent is general purpose (supports all technologies)
     */
    public boolean isGeneralPurpose() {
        return agentType == AgentType.WORKSPACE_COORDINATION ||
               capabilities.contains(AgentCapability.ARCHITECTURAL_CONSISTENCY);
    }
    
    /**
     * Gets specialization score for a specific capability (0-100)
     */
    public int getSpecializationScore(AgentCapability capability) {
        if (!capabilities.contains(capability)) {
            return 0;
        }
        
        // Higher score for agents with fewer capabilities (more specialized)
        int baseScore = 100 - (capabilities.size() * 5);
        
        // Bonus for primary capabilities based on agent type
        int typeBonus = 0;
        switch (agentType) {
            case WORKSPACE_COORDINATION:
                if (capability == AgentCapability.FULL_STACK_INTEGRATION ||
                    capability == AgentCapability.ARCHITECTURAL_CONSISTENCY) {
                    typeBonus = 20;
                }
                break;
                
            case TECHNOLOGY_BRIDGE:
                if (capability == AgentCapability.API_CONTRACT_MANAGEMENT ||
                    capability == AgentCapability.DATA_INTEGRATION) {
                    typeBonus = 20;
                }
                break;
                
            case OPERATIONAL_COORDINATION:
                if (capability == AgentCapability.DEVOPS_COORDINATION ||
                    capability == AgentCapability.OBSERVABILITY_UNIFICATION) {
                    typeBonus = 20;
                }
                break;
                
            case GOVERNANCE_COMPLIANCE:
                if (capability == AgentCapability.DATA_GOVERNANCE ||
                    capability == AgentCapability.DOCUMENTATION_COORDINATION) {
                    typeBonus = 20;
                }
                break;
        }
        
        return Math.min(100, Math.max(0, baseScore + typeBonus));
    }
    
    /**
     * Creates a new metadata instance with updated version
     */
    public AgentMetadata withVersion(String newVersion) {
        return new AgentMetadata(agentId, agentType, capabilities, supportedTechnologies,
                               supportedProjects, crossProjectSupport, workspaceLevel,
                               registrationTime, newVersion, requiredVersions);
    }
    
    /**
     * Creates a new metadata instance with updated required versions
     */
    public AgentMetadata withRequiredVersions(Map<String, String> newRequiredVersions) {
        return new AgentMetadata(agentId, agentType, capabilities, supportedTechnologies,
                               supportedProjects, crossProjectSupport, workspaceLevel,
                               registrationTime, version, newRequiredVersions);
    }
    
    @Override
    public String toString() {
        return String.format("AgentMetadata{id='%s', type=%s, version='%s', capabilities=%d, crossProject=%s, workspaceLevel=%s}",
                agentId, agentType, version, capabilities.size(), crossProjectSupport, workspaceLevel);
    }
}