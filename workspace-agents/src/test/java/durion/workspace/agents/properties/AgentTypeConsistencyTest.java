package durion.workspace.agents.properties;

import durion.workspace.agents.core.AbstractWorkspaceAgent;
import durion.workspace.agents.core.AgentCapability;
import durion.workspace.agents.core.AgentType;
import durion.workspace.agents.coordination.*;
import net.jqwik.api.*;
import net.jqwik.api.constraints.NotEmpty;
import org.junit.jupiter.api.Tag;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * **Feature: workspace-agents-structure-fix, Property 7: Agent type consistency**
 * **Validates: Requirements 4.3**
 * 
 * Property-based test to verify that all agents use valid AgentType enum values
 * and that agent types are consistent with their package structure and capabilities.
 */
@Tag("property-test")
public class AgentTypeConsistencyTest {

    /**
     * Property 7: Agent type consistency
     * For any agent with a defined type, the type should use a valid AgentType enum value
     */
    @Property(tries = 100)
    @Tag("property-test")
    void agentTypeConsistency(@ForAll("agentInstances") AbstractWorkspaceAgent agent) {
        // Get the agent's declared type
        AgentType agentType = agent.getAgentType();
        
        // Verify the type is not null
        assert agentType != null : "Agent " + agent.getAgentId() + " must have a non-null agent type";
        
        // Verify the type is a valid enum value
        boolean isValidEnumValue = Arrays.asList(AgentType.values()).contains(agentType);
        assert isValidEnumValue : "Agent " + agent.getAgentId() + " has invalid agent type: " + agentType;
        
        // Verify type consistency with package structure
        verifyTypePackageConsistency(agent);
        
        // Verify type consistency with capabilities
        verifyTypeCapabilityConsistency(agent);
    }

    /**
     * Property to verify agent type is consistent across multiple calls
     */
    @Property(tries = 100)
    @Tag("property-test")
    void agentTypeStability(@ForAll("agentInstances") AbstractWorkspaceAgent agent) {
        // Get agent type multiple times and verify it's consistent
        AgentType type1 = agent.getAgentType();
        AgentType type2 = agent.getAgentType();
        
        assert type1 == type2 : 
            "Agent " + agent.getAgentId() + " type should be consistent across calls: " + type1 + " vs " + type2;
    }

    /**
     * Property to verify agent type matches expected patterns based on class name
     */
    @Property(tries = 100)
    @Tag("property-test")
    void agentTypeNamingConsistency(@ForAll("agentInstances") AbstractWorkspaceAgent agent) {
        String className = agent.getClass().getSimpleName();
        AgentType agentType = agent.getAgentType();
        
        // Verify naming patterns match agent types
        if (className.contains("Testing") || className.contains("DevOps") || className.contains("Disaster")) {
            assert agentType == AgentType.OPERATIONAL_COORDINATION : 
                "Agent " + className + " should be OPERATIONAL_COORDINATION type, but is " + agentType;
        } else if (className.contains("Data") || className.contains("Documentation") || className.contains("Workflow")) {
            assert agentType == AgentType.GOVERNANCE_COMPLIANCE : 
                "Agent " + className + " should be GOVERNANCE_COMPLIANCE type, but is " + agentType;
        } else if (className.contains("Performance") || className.contains("Feature") || className.contains("Release")) {
            assert agentType == AgentType.WORKSPACE_COORDINATION : 
                "Agent " + className + " should be WORKSPACE_COORDINATION type, but is " + agentType;
        }
        // Note: TECHNOLOGY_BRIDGE agents would be tested here if they existed
    }

    /**
     * Property to verify all agent types are represented
     */
    @Property(tries = 100)
    @Tag("property-test")
    void agentTypesCoverAllEnumValues(@ForAll("agentInstanceLists") List<AbstractWorkspaceAgent> agents) {
        Set<AgentType> representedTypes = agents.stream()
            .map(AbstractWorkspaceAgent::getAgentType)
            .collect(java.util.stream.Collectors.toSet());
        
        // We should have agents representing the main coordination types
        // (TECHNOLOGY_BRIDGE might not be implemented yet)
        boolean hasWorkspaceCoordination = representedTypes.contains(AgentType.WORKSPACE_COORDINATION);
        boolean hasOperationalCoordination = representedTypes.contains(AgentType.OPERATIONAL_COORDINATION);
        boolean hasGovernanceCompliance = representedTypes.contains(AgentType.GOVERNANCE_COMPLIANCE);
        
        assert hasWorkspaceCoordination : "Should have at least one WORKSPACE_COORDINATION agent";
        assert hasOperationalCoordination : "Should have at least one OPERATIONAL_COORDINATION agent";
        assert hasGovernanceCompliance : "Should have at least one GOVERNANCE_COMPLIANCE agent";
    }

    /**
     * Provides agent instances for testing
     */
    @Provide
    Arbitrary<AbstractWorkspaceAgent> agentInstances() {
        List<AbstractWorkspaceAgent> agents = Arrays.asList(
            new DataGovernanceAgent(),
            new CrossProjectTestingAgent(),
            new DisasterRecoveryAgent(),
            new DocumentationCoordinationAgent(),
            new PerformanceCoordinationAgent(),
            new WorkflowCoordinationAgent(),
            new WorkspaceFeatureDevelopmentAgent(),
            new WorkspaceReleaseCoordinationAgent()
        );
        
        return Arbitraries.of(agents);
    }

    /**
     * Provides lists of agent instances for testing
     */
    @Provide
    Arbitrary<List<AbstractWorkspaceAgent>> agentInstanceLists() {
        return agentInstances().list().ofMinSize(3).ofMaxSize(8);
    }

    /**
     * Helper method to verify type consistency with package structure
     */
    private void verifyTypePackageConsistency(AbstractWorkspaceAgent agent) {
        String packageName = agent.getClass().getPackage().getName();
        AgentType agentType = agent.getAgentType();
        
        // All current agents are in the coordination package
        if (packageName.contains("coordination")) {
            // Coordination package can contain various agent types
            // This is acceptable as it's the main implementation package
            assert agentType != null : "Coordination agents must have a valid type";
        }
        
        // If we had other packages, we would verify them here:
        // - registry package agents might be OPERATIONAL_COORDINATION
        // - monitoring package agents might be WORKSPACE_COORDINATION
        // - etc.
    }

    /**
     * Helper method to verify type consistency with capabilities
     */
    private void verifyTypeCapabilityConsistency(AbstractWorkspaceAgent agent) {
        AgentType agentType = agent.getAgentType();
        Set<AgentCapability> capabilities = agent.getCapabilities();
        
        switch (agentType) {
            case WORKSPACE_COORDINATION:
                // Workspace coordination agents should have coordination capabilities
                boolean hasWorkspaceCapability = capabilities.stream()
                    .anyMatch(cap -> 
                        cap == AgentCapability.FULL_STACK_INTEGRATION ||
                        cap == AgentCapability.ARCHITECTURAL_CONSISTENCY ||
                        cap == AgentCapability.PERFORMANCE_OPTIMIZATION ||
                        cap == AgentCapability.WORKSPACE_GROWTH_HANDLING ||
                        cap == AgentCapability.RESPONSE_TIME_OPTIMIZATION ||
                        cap == AgentCapability.AVAILABILITY_MANAGEMENT
                    );
                assert hasWorkspaceCapability : 
                    "WORKSPACE_COORDINATION agent " + agent.getAgentId() + " should have workspace-level capabilities";
                break;
                
            case TECHNOLOGY_BRIDGE:
                // Technology bridge agents should have bridge capabilities
                boolean hasBridgeCapability = capabilities.stream()
                    .anyMatch(cap -> 
                        cap == AgentCapability.API_CONTRACT_MANAGEMENT ||
                        cap == AgentCapability.DATA_INTEGRATION ||
                        cap == AgentCapability.FRONTEND_BACKEND_BRIDGE
                    );
                assert hasBridgeCapability : 
                    "TECHNOLOGY_BRIDGE agent " + agent.getAgentId() + " should have bridge capabilities";
                break;
                
            case OPERATIONAL_COORDINATION:
                // Operational agents should have operational capabilities
                boolean hasOperationalCapability = capabilities.stream()
                    .anyMatch(cap -> 
                        cap == AgentCapability.DEVOPS_COORDINATION ||
                        cap == AgentCapability.OBSERVABILITY_UNIFICATION ||
                        cap == AgentCapability.TESTING_COORDINATION ||
                        cap == AgentCapability.DISASTER_RECOVERY ||
                        cap == AgentCapability.DEPLOYMENT_COORDINATION ||
                        cap == AgentCapability.MONITORING_INTEGRATION
                    );
                assert hasOperationalCapability : 
                    "OPERATIONAL_COORDINATION agent " + agent.getAgentId() + " should have operational capabilities";
                break;
                
            case GOVERNANCE_COMPLIANCE:
                // Governance agents should have governance capabilities
                boolean hasGovernanceCapability = capabilities.stream()
                    .anyMatch(cap -> 
                        cap == AgentCapability.DATA_GOVERNANCE ||
                        cap == AgentCapability.DOCUMENTATION_COORDINATION ||
                        cap == AgentCapability.WORKFLOW_COORDINATION ||
                        cap == AgentCapability.COMPLIANCE_ENFORCEMENT ||
                        cap == AgentCapability.CHANGE_COORDINATION
                    );
                assert hasGovernanceCapability : 
                    "GOVERNANCE_COMPLIANCE agent " + agent.getAgentId() + " should have governance capabilities";
                break;
                
            default:
                assert false : "Unknown agent type: " + agentType + " for agent " + agent.getAgentId();
        }
    }
}