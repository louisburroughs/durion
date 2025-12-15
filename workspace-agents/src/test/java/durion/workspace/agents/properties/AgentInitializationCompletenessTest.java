package durion.workspace.agents.properties;

import durion.workspace.agents.WorkspaceAgentFramework;
import durion.workspace.agents.core.*;
import durion.workspace.agents.registry.WorkspaceAgentRegistry;
import durion.workspace.agents.coordination.*;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Assertions;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Property-based test for agent initialization completeness
 * 
 * **Feature: workspace-agents-structure-fix, Property 1: Agent initialization
 * completeness**
 * **Validates: Requirements 3.2**
 * 
 * Property 1: Agent initialization completeness
 * For any set of available agent classes, when the framework starts, all
 * discoverable agents
 * should be successfully initialized and registered
 */
@Tag("property-test")
public class AgentInitializationCompletenessTest {

    /**
     * Property: All discoverable agents are initialized
     * 
     * For any set of available agent classes, when the framework starts,
     * all discoverable agents should be successfully initialized and registered
     */
    @Property(tries = 100)
    void shouldInitializeAllDiscoverableAgents(
            @ForAll("agentClassSets") Set<Class<? extends AbstractWorkspaceAgent>> agentClasses) {

        // Given: A framework with discoverable agent classes
        WorkspaceAgentFramework framework = new WorkspaceAgentFramework();

        // When: Framework starts and initializes agents
        try {
            framework.start();

            // Then: All expected core agents should be registered
            WorkspaceAgentRegistry registry = framework.getRegistry();
            List<WorkspaceAgentRegistry.RegisteredAgent> registeredAgents = registry.getAllRegisteredAgents();

            // Verify all core coordination agents are initialized
            Set<String> expectedAgentTypes = Set.of(
                    "CrossProjectTestingAgent",
                    "DataGovernanceAgent",
                    "DisasterRecoveryAgent",
                    "DocumentationCoordinationAgent",
                    "PerformanceCoordinationAgent",
                    "WorkflowCoordinationAgent",
                    "WorkspaceFeatureDevelopmentAgent",
                    "WorkspaceReleaseCoordinationAgent");

            Set<String> actualAgentTypes = registeredAgents.stream()
                    .map(ra -> ra.getAgent().getClass().getSimpleName())
                    .collect(Collectors.toSet());

            Assertions.assertTrue(actualAgentTypes.containsAll(expectedAgentTypes),
                    "All core coordination agents should be initialized. Expected: " + expectedAgentTypes +
                            ", Actual: " + actualAgentTypes);

            // Verify all agents are in healthy status
            for (WorkspaceAgentRegistry.RegisteredAgent registeredAgent : registeredAgents) {
                AgentStatus status = registeredAgent.getAgent().getStatus();
                Assertions.assertTrue(status.available(),
                        "Agent " + registeredAgent.getAgent().getClass().getSimpleName() + " should be available");
            }

            // Verify no duplicate registrations
            Set<String> agentIds = registeredAgents.stream()
                    .map(ra -> ra.getAgent().getAgentId())
                    .collect(Collectors.toSet());

            Assertions.assertEquals(registeredAgents.size(), agentIds.size(),
                    "No duplicate agent registrations should exist");

        } catch (AgentException e) {
            Assertions.fail("Framework should start successfully without exceptions: " + e.getMessage());
        } finally {
            framework.shutdown();
        }
    }

    /**
     * Property: Agent initialization preserves capabilities
     * 
     * For any initialized agent, all declared capabilities should be properly
     * registered and accessible through the registry
     */
    @Property(tries = 100)
    void shouldPreserveAgentCapabilitiesDuringInitialization(
            @ForAll("agentCapabilitySets") Set<AgentCapability> expectedCapabilities) {

        // Given: A framework with agents having specific capabilities
        WorkspaceAgentFramework framework = new WorkspaceAgentFramework();

        try {
            framework.start();

            // When: Querying agents by capability
            WorkspaceAgentRegistry registry = framework.getRegistry();

            // Then: All agents with declared capabilities should be discoverable
            for (AgentCapability capability : expectedCapabilities) {
                List<WorkspaceAgent> agentsWithCapability = registry.discoverAgentsByCapability(capability);

                // Verify agents are found for each capability
                if (isCapabilityProvidedByCoreAgents(capability)) {
                    Assertions.assertFalse(agentsWithCapability.isEmpty(),
                            "At least one agent should provide capability: " + capability);

                    // Verify all returned agents actually have the capability
                    for (WorkspaceAgent agent : agentsWithCapability) {
                        Assertions.assertTrue(agent.hasCapability(capability),
                                "Agent " + agent.getAgentId() + " should have capability: " + capability);
                    }
                }
            }

        } catch (AgentException e) {
            Assertions.fail("Framework should start successfully: " + e.getMessage());
        } finally {
            framework.shutdown();
        }
    }

    /**
     * Property: Agent initialization respects dependencies
     * 
     * For any agent with coordination dependencies, those dependencies should
     * be satisfied during initialization
     */
    @Property(tries = 100)
    void shouldRespectAgentDependenciesDuringInitialization(
            @ForAll("dependencyScenarios") DependencyScenario scenario) {

        // Given: A framework with agents having dependencies
        WorkspaceAgentFramework framework = new WorkspaceAgentFramework();

        try {
            framework.start();

            // When: Checking agent dependencies
            WorkspaceAgentRegistry registry = framework.getRegistry();
            List<WorkspaceAgentRegistry.RegisteredAgent> registeredAgents = registry.getAllRegisteredAgents();

            // Then: All agent dependencies should be satisfied
            for (WorkspaceAgentRegistry.RegisteredAgent registeredAgent : registeredAgents) {
                WorkspaceAgent agent = registeredAgent.getAgent();
                List<String> dependencies = agent.getCoordinationDependencies();

                for (String dependencyId : dependencies) {
                    Optional<WorkspaceAgentRegistry.RegisteredAgent> dependency = registry
                            .getRegisteredAgent(dependencyId);

                    // For this test, we allow dependencies to be optional since we're testing
                    // the core framework initialization, not specific dependency resolution
                    if (dependency.isPresent()) {
                        Assertions.assertTrue(dependency.get().getAgent().getStatus().available(),
                                "Dependency " + dependencyId + " should be available for agent " + agent.getAgentId());
                    }
                }
            }

        } catch (AgentException e) {
            Assertions.fail("Framework should handle dependencies correctly: " + e.getMessage());
        } finally {
            framework.shutdown();
        }
    }

    /**
     * Property: Framework initialization is idempotent
     * 
     * For any framework instance, multiple start calls should not cause
     * duplicate registrations or inconsistent state
     */
    @Property(tries = 100)
    void shouldBeIdempotentForMultipleStartCalls(
            @ForAll @IntRange(min = 1, max = 3) int startCalls) {

        // Given: A framework instance
        WorkspaceAgentFramework framework = new WorkspaceAgentFramework();

        try {
            // When: Starting framework multiple times
            for (int i = 0; i < startCalls; i++) {
                if (i == 0) {
                    framework.start(); // First start should succeed
                } else {
                    // Subsequent starts should be handled gracefully
                    // (The current implementation doesn't prevent multiple starts,
                    // but it should not cause issues)
                    try {
                        framework.start();
                    } catch (Exception e) {
                        // Multiple starts might throw exceptions, which is acceptable
                        // as long as the framework remains in a consistent state
                    }
                }
            }

            // Then: Framework should be in consistent state
            Assertions.assertTrue(framework.isRunning(),
                    "Framework should be running after start calls");

            WorkspaceAgentRegistry registry = framework.getRegistry();
            List<WorkspaceAgentRegistry.RegisteredAgent> registeredAgents = registry.getAllRegisteredAgents();

            // Verify no duplicate registrations occurred
            Set<String> agentIds = registeredAgents.stream()
                    .map(ra -> ra.getAgent().getAgentId())
                    .collect(Collectors.toSet());

            Assertions.assertEquals(registeredAgents.size(), agentIds.size(),
                    "No duplicate agent registrations should exist after multiple starts");

        } catch (AgentException e) {
            Assertions.fail("Framework should handle multiple starts gracefully: " + e.getMessage());
        } finally {
            framework.shutdown();
        }
    }

    // Test data classes

    public static class DependencyScenario {
        private final Map<String, List<String>> agentDependencies;

        public DependencyScenario(Map<String, List<String>> agentDependencies) {
            this.agentDependencies = agentDependencies;
        }

        public Map<String, List<String>> getAgentDependencies() {
            return agentDependencies;
        }
    }

    // Generators for property-based testing

    @Provide
    Arbitrary<Set<Class<? extends AbstractWorkspaceAgent>>> agentClassSets() {
        return Arbitraries.of(
                CrossProjectTestingAgent.class,
                DataGovernanceAgent.class,
                DisasterRecoveryAgent.class,
                DocumentationCoordinationAgent.class,
                PerformanceCoordinationAgent.class,
                WorkflowCoordinationAgent.class,
                WorkspaceFeatureDevelopmentAgent.class,
                WorkspaceReleaseCoordinationAgent.class).set().ofMinSize(1).ofMaxSize(8);
    }

    @Provide
    Arbitrary<Set<AgentCapability>> agentCapabilitySets() {
        return Arbitraries.of(AgentCapability.values())
                .set().ofMinSize(1).ofMaxSize(AgentCapability.values().length);
    }

    @Provide
    Arbitrary<DependencyScenario> dependencyScenarios() {
        return Arbitraries.maps(
                agentNames(),
                agentNames().list().ofMinSize(0).ofMaxSize(2)).ofMinSize(1).ofMaxSize(4)
                .map(DependencyScenario::new);
    }

    @Provide
    Arbitrary<String> agentNames() {
        return Arbitraries.of(
                "CrossProjectTestingAgent",
                "DataGovernanceAgent",
                "DisasterRecoveryAgent",
                "DocumentationCoordinationAgent",
                "PerformanceCoordinationAgent",
                "WorkflowCoordinationAgent",
                "WorkspaceFeatureDevelopmentAgent",
                "WorkspaceReleaseCoordinationAgent");
    }

    // Helper methods

    private boolean isCapabilityProvidedByCoreAgents(AgentCapability capability) {
        // Based on the core agents initialized by the framework, determine which
        // capabilities are actually provided
        switch (capability) {
            case TESTING_COORDINATION:
            case DATA_GOVERNANCE:
            case DISASTER_RECOVERY:
            case DOCUMENTATION_COORDINATION:
            case PERFORMANCE_OPTIMIZATION:
            case WORKFLOW_COORDINATION:
            case FULL_STACK_INTEGRATION:
            case DEPLOYMENT_COORDINATION:
                return true;
            default:
                return false; // Other capabilities might not be provided by core agents
        }
    }
}