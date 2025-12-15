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
import java.util.stream.Collectors;

/**
 * **Feature: workspace-agents-structure-fix, Property 6: Capability definition validity**
 * **Validates: Requirements 4.2**
 * 
 * Property-based test to verify that all agent capabilities are defined using valid AgentCapability enum values
 * and that agents properly declare their capabilities.
 */
@Tag("property-test")
public class AgentCapabilityDefinitionValidityTest {

    /**
     * Property 6: Capability definition validity
     * For any agent with defined capabilities, all capabilities should use valid AgentCapability enum values
     */
    @Property(tries = 100)
    @Tag("property-test")
    void agentCapabilityDefinitionValidity(@ForAll("agentInstances") AbstractWorkspaceAgent agent) {
        // Get the agent's declared capabilities
        Set<AgentCapability> capabilities = agent.getCapabilities();
        
        // Verify all capabilities are valid enum values
        for (AgentCapability capability : capabilities) {
            assert capability != null : "Agent " + agent.getAgentId() + " has null capability";
            
            // Verify the capability is a valid enum value
            boolean isValidEnumValue = Arrays.asList(AgentCapability.values()).contains(capability);
            assert isValidEnumValue : "Agent " + agent.getAgentId() + " has invalid capability: " + capability;
        }
        
        // Verify agent has at least one capability
        assert !capabilities.isEmpty() : "Agent " + agent.getAgentId() + " must have at least one capability";
        
        // Verify capabilities are consistent with agent type
        AgentType agentType = agent.getAgentType();
        verifyCapabilitiesConsistentWithType(agent.getAgentId(), agentType, capabilities);
    }

    /**
     * Property to verify capability scoring consistency
     */
    @Property(tries = 100)
    @Tag("property-test")
    void agentCapabilityScoring(@ForAll("agentInstances") AbstractWorkspaceAgent agent,
                               @ForAll("validCapabilities") AgentCapability capability) {
        
        int score = agent.getCapabilityScore(capability);
        
        // Score should be between 0 and 100
        assert score >= 0 && score <= 100 : 
            "Agent " + agent.getAgentId() + " capability score for " + capability + " should be 0-100, got: " + score;
        
        // If agent has the capability, score should be > 0
        if (agent.hasCapability(capability)) {
            assert score > 0 : 
                "Agent " + agent.getAgentId() + " has capability " + capability + " but score is 0";
        } else {
            // If agent doesn't have the capability, score should be 0
            assert score == 0 : 
                "Agent " + agent.getAgentId() + " doesn't have capability " + capability + " but score is " + score;
        }
    }

    /**
     * Property to verify primary capability identification
     */
    @Property(tries = 100)
    @Tag("property-test")
    void agentPrimaryCapabilityIdentification(@ForAll("agentInstances") AbstractWorkspaceAgent agent) {
        Set<AgentCapability> capabilities = agent.getCapabilities();
        
        // At least one capability should be primary
        boolean hasPrimaryCapability = false;
        for (AgentCapability capability : capabilities) {
            if (agent.getCapabilityScore(capability) >= 70) { // High score indicates primary capability
                hasPrimaryCapability = true;
                break;
            }
        }
        
        assert hasPrimaryCapability : 
            "Agent " + agent.getAgentId() + " should have at least one primary capability (score >= 70)";
    }

    /**
     * Property to verify capability consistency across agent lifecycle
     */
    @Property(tries = 100)
    @Tag("property-test")
    void agentCapabilityConsistency(@ForAll("agentInstances") AbstractWorkspaceAgent agent) {
        // Get capabilities multiple times and verify they're consistent
        Set<AgentCapability> capabilities1 = agent.getCapabilities();
        Set<AgentCapability> capabilities2 = agent.getCapabilities();
        
        assert capabilities1.equals(capabilities2) : 
            "Agent " + agent.getAgentId() + " capabilities should be consistent across calls";
        
        // Verify hasCapability is consistent with getCapabilities
        for (AgentCapability capability : AgentCapability.values()) {
            boolean hasCapability = agent.hasCapability(capability);
            boolean inCapabilitySet = capabilities1.contains(capability);
            
            assert hasCapability == inCapabilitySet : 
                "Agent " + agent.getAgentId() + " hasCapability(" + capability + ") = " + hasCapability + 
                " but getCapabilities().contains() = " + inCapabilitySet;
        }
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
     * Provides valid AgentCapability enum values
     */
    @Provide
    Arbitrary<AgentCapability> validCapabilities() {
        return Arbitraries.of(AgentCapability.values());
    }

    /**
     * Helper method to verify capabilities are consistent with agent type
     */
    private void verifyCapabilitiesConsistentWithType(String agentId, AgentType agentType, 
                                                     Set<AgentCapability> capabilities) {
        switch (agentType) {
            case WORKSPACE_COORDINATION:
                // Workspace coordination agents should have coordination-related capabilities
                boolean hasCoordinationCapability = capabilities.stream()
                    .anyMatch(cap -> cap.name().contains("COORDINATION") || 
                                   cap.name().contains("INTEGRATION") ||
                                   cap.name().contains("OPTIMIZATION"));
                assert hasCoordinationCapability : 
                    "Workspace coordination agent " + agentId + " should have coordination-related capabilities";
                break;
                
            case TECHNOLOGY_BRIDGE:
                // Technology bridge agents should have bridge-related capabilities
                boolean hasBridgeCapability = capabilities.stream()
                    .anyMatch(cap -> cap.name().contains("BRIDGE") || 
                                   cap.name().contains("INTEGRATION") ||
                                   cap.name().contains("CONTRACT"));
                assert hasBridgeCapability : 
                    "Technology bridge agent " + agentId + " should have bridge-related capabilities";
                break;
                
            case OPERATIONAL_COORDINATION:
                // Operational agents should have operational capabilities
                boolean hasOperationalCapability = capabilities.stream()
                    .anyMatch(cap -> cap.name().contains("TESTING") || 
                                   cap.name().contains("DEVOPS") ||
                                   cap.name().contains("DISASTER") ||
                                   cap.name().contains("MONITORING"));
                assert hasOperationalCapability : 
                    "Operational coordination agent " + agentId + " should have operational capabilities";
                break;
                
            case GOVERNANCE_COMPLIANCE:
                // Governance agents should have governance-related capabilities
                boolean hasGovernanceCapability = capabilities.stream()
                    .anyMatch(cap -> cap.name().contains("GOVERNANCE") || 
                                   cap.name().contains("COMPLIANCE") ||
                                   cap.name().contains("DOCUMENTATION") ||
                                   cap.name().contains("WORKFLOW"));
                assert hasGovernanceCapability : 
                    "Governance compliance agent " + agentId + " should have governance-related capabilities";
                break;
                
            default:
                // Unknown agent type - this itself is an error
                assert false : "Agent " + agentId + " has unknown agent type: " + agentType;
        }
    }
}