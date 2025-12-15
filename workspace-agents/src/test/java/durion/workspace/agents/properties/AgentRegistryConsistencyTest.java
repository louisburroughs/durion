package durion.workspace.agents.properties;

import durion.workspace.agents.WorkspaceAgentFramework;
import durion.workspace.agents.core.*;
import durion.workspace.agents.registry.WorkspaceAgentRegistry;
import durion.workspace.agents.discovery.AgentMetadata;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Assertions;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Property-based test for agent registry consistency
 * 
 * **Feature: workspace-agents-structure-fix, Property 2: Agent registry consistency**
 * **Validates: Requirements 3.3**
 * 
 * Property 2: Agent registry consistency
 * For any initialized agent, that agent should appear in the registry with correct metadata and status
 */
@Tag("property-test")
public class AgentRegistryConsistencyTest {

    /**
     * Property: Registered agents have consistent metadata
     * 
     * For any agent registered in the framework, the registry should maintain
     * consistent metadata that matches the agent's actual properties
     */
    @Property(tries = 100)
    void shouldMaintainConsistentAgentMetadata(
            @ForAll("agentTypes") AgentType agentType,
            @ForAll("capabilitySets") Set<AgentCapability> capabilities) {
        
        // Given: A framework with registered agents
        WorkspaceAgentFramework framework = new WorkspaceAgentFramework();
        
        try {
            framework.start();
            WorkspaceAgentRegistry registry = framework.getRegistry();
            
            // When: Querying registered agents
            List<WorkspaceAgentRegistry.RegisteredAgent> registeredAgents = registry.getAllRegisteredAgents();
            
            // Then: Each registered agent should have consistent metadata
            for (WorkspaceAgentRegistry.RegisteredAgent registeredAgent : registeredAgents) {
                WorkspaceAgent agent = registeredAgent.getAgent();
                AgentMetadata metadata = registeredAgent.getMetadata();
                
                // Verify agent ID consistency
                Assertions.assertEquals(agent.getAgentId(), metadata.getAgentId(),
                    "Agent ID should be consistent between agent and metadata");
                
                // Verify agent type consistency
                Assertions.assertEquals(agent.getAgentType(), metadata.getAgentType(),
                    "Agent type should be consistent between agent and metadata");
                
                // Verify capabilities consistency
                Set<AgentCapability> agentCapabilities = agent.getCapabilities();
                Set<AgentCapability> metadataCapabilities = metadata.getCapabilities();
                Assertions.assertEquals(agentCapabilities, metadataCapabilities,
                    "Capabilities should be consistent between agent and metadata");
                
                // Verify registration time is set
                Assertions.assertNotNull(registeredAgent.getRegistrationTime(),
                    "Registration time should be set for registered agent");
            }
            
        } catch (AgentException e) {
            Assertions.fail("Framework should start successfully: " + e.getMessage());
        } finally {
            framework.shutdown();
        }
    }

    /**
     * Property: Registry maintains agent status consistency
     * 
     * For any registered agent, the status reported by the registry should
     * match the agent's actual status
     */
    @Property(tries = 100)
    void shouldMaintainAgentStatusConsistency(
            @ForAll("statusScenarios") StatusScenario scenario) {
        
        // Given: A framework with registered agents
        WorkspaceAgentFramework framework = new WorkspaceAgentFramework();
        
        try {
            framework.start();
            WorkspaceAgentRegistry registry = framework.getRegistry();
            
            // When: Checking agent status through different access methods
            List<WorkspaceAgentRegistry.RegisteredAgent> registeredAgents = registry.getAllRegisteredAgents();
            
            for (WorkspaceAgentRegistry.RegisteredAgent registeredAgent : registeredAgents) {
                WorkspaceAgent agent = registeredAgent.getAgent();
                String agentId = agent.getAgentId();
                
                // Get status directly from agent
                AgentStatus directStatus = agent.getStatus();
                
                // Get agent through registry and check status
                Optional<WorkspaceAgentRegistry.RegisteredAgent> registryAgent = 
                    registry.getRegisteredAgent(agentId);
                
                Assertions.assertTrue(registryAgent.isPresent(),
                    "Agent should be findable in registry: " + agentId);
                
                AgentStatus registryStatus = registryAgent.get().getAgent().getStatus();
                
                // Then: Status should be consistent
                Assertions.assertEquals(directStatus.available(), registryStatus.available(),
                    "Agent availability should be consistent between direct and registry access");
                
                Assertions.assertEquals(directStatus.healthy(), registryStatus.healthy(),
                    "Agent health should be consistent between direct and registry access");
            }
            
        } catch (AgentException e) {
            Assertions.fail("Framework should maintain status consistency: " + e.getMessage());
        } finally {
            framework.shutdown();
        }
    }

    /**
     * Property: Registry capability index consistency
     * 
     * For any agent with specific capabilities, the registry's capability index
     * should correctly include that agent when queried by capability
     */
    @Property(tries = 100)
    void shouldMaintainCapabilityIndexConsistency(
            @ForAll("capabilityQueries") AgentCapability queryCapability) {
        
        // Given: A framework with registered agents
        WorkspaceAgentFramework framework = new WorkspaceAgentFramework();
        
        try {
            framework.start();
            WorkspaceAgentRegistry registry = framework.getRegistry();
            
            // When: Querying agents by capability
            List<WorkspaceAgent> agentsWithCapability = registry.discoverAgentsByCapability(queryCapability);
            List<WorkspaceAgentRegistry.RegisteredAgent> allAgents = registry.getAllRegisteredAgents();
            
            // Then: All returned agents should actually have the capability
            for (WorkspaceAgent agent : agentsWithCapability) {
                Assertions.assertTrue(agent.hasCapability(queryCapability),
                    "Agent " + agent.getAgentId() + " should have capability: " + queryCapability);
            }
            
            // And: All agents with the capability should be included in results
            Set<String> foundAgentIds = agentsWithCapability.stream()
                .map(WorkspaceAgent::getAgentId)
                .collect(Collectors.toSet());
            
            for (WorkspaceAgentRegistry.RegisteredAgent registeredAgent : allAgents) {
                WorkspaceAgent agent = registeredAgent.getAgent();
                if (agent.hasCapability(queryCapability) && agent.getStatus().available()) {
                    Assertions.assertTrue(foundAgentIds.contains(agent.getAgentId()),
                        "Available agent " + agent.getAgentId() + " with capability " + 
                        queryCapability + " should be discoverable");
                }
            }
            
        } catch (AgentException e) {
            Assertions.fail("Framework should maintain capability index consistency: " + e.getMessage());
        } finally {
            framework.shutdown();
        }
    }

    /**
     * Property: Registry maintains unique agent registrations
     * 
     * For any set of registered agents, each agent ID should appear exactly once
     * in the registry with no duplicates
     */
    @Property(tries = 100)
    void shouldMaintainUniqueAgentRegistrations(
            @ForAll("registrationScenarios") RegistrationScenario scenario) {
        
        // Given: A framework with registered agents
        WorkspaceAgentFramework framework = new WorkspaceAgentFramework();
        
        try {
            framework.start();
            WorkspaceAgentRegistry registry = framework.getRegistry();
            
            // When: Getting all registered agents
            List<WorkspaceAgentRegistry.RegisteredAgent> registeredAgents = registry.getAllRegisteredAgents();
            
            // Then: Each agent ID should be unique
            Set<String> agentIds = new HashSet<>();
            Set<String> duplicateIds = new HashSet<>();
            
            for (WorkspaceAgentRegistry.RegisteredAgent registeredAgent : registeredAgents) {
                String agentId = registeredAgent.getAgent().getAgentId();
                if (!agentIds.add(agentId)) {
                    duplicateIds.add(agentId);
                }
            }
            
            Assertions.assertTrue(duplicateIds.isEmpty(),
                "No duplicate agent IDs should exist in registry. Duplicates: " + duplicateIds);
            
            // Verify total count matches unique count
            Assertions.assertEquals(registeredAgents.size(), agentIds.size(),
                "Total registered agents should equal unique agent count");
            
        } catch (AgentException e) {
            Assertions.fail("Framework should maintain unique registrations: " + e.getMessage());
        } finally {
            framework.shutdown();
        }
    }

    // Test data classes

    public static class StatusScenario {
        private final String scenarioName;
        
        public StatusScenario(String scenarioName) {
            this.scenarioName = scenarioName;
        }
        
        public String getScenarioName() { return scenarioName; }
    }

    public static class RegistrationScenario {
        private final String scenarioName;
        
        public RegistrationScenario(String scenarioName) {
            this.scenarioName = scenarioName;
        }
        
        public String getScenarioName() { return scenarioName; }
    }

    // Generators for property-based testing

    @Provide
    Arbitrary<AgentType> agentTypes() {
        return Arbitraries.of(AgentType.values());
    }

    @Provide
    Arbitrary<Set<AgentCapability>> capabilitySets() {
        return Arbitraries.of(AgentCapability.values())
            .set().ofMinSize(1).ofMaxSize(5);
    }

    @Provide
    Arbitrary<AgentCapability> capabilityQueries() {
        return Arbitraries.of(AgentCapability.values());
    }

    @Provide
    Arbitrary<StatusScenario> statusScenarios() {
        return Arbitraries.of("normal", "degraded", "unavailable")
            .map(StatusScenario::new);
    }

    @Provide
    Arbitrary<RegistrationScenario> registrationScenarios() {
        return Arbitraries.of("single-registration", "multiple-registrations", "concurrent-registrations")
            .map(RegistrationScenario::new);
    }
}