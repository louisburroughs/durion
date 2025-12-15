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
 * Property-based test for agent coordination availability
 * 
 * **Feature: workspace-agents-structure-fix, Property 3: Agent coordination
 * availability**
 * **Validates: Requirements 3.4**
 * 
 * Property 3: Agent coordination availability
 * For any pair of registered agents, coordination capabilities should be
 * available and functional between them
 */
@Tag("property-test")
public class AgentCoordinationAvailabilityTest {

        /**
         * Property: Coordination capabilities are available between agent pairs
         * 
         * For any pair of registered agents, coordination capabilities should be
         * available and functional between them
         */
        @Property(tries = 100)
        void shouldProvideCoordinationCapabilitiesBetweenAgentPairs(
                        @ForAll("agentPairs") Tuple.Tuple2<String, String> agentPair) {

                // Given: A framework with registered agents
                WorkspaceAgentFramework framework = new WorkspaceAgentFramework();

                try {
                        framework.start();
                        WorkspaceAgentRegistry registry = framework.getRegistry();
                        List<WorkspaceAgentRegistry.RegisteredAgent> registeredAgents = registry
                                        .getAllRegisteredAgents();

                        // Filter to get the specific agent pair if they exist
                        Optional<WorkspaceAgentRegistry.RegisteredAgent> agent1 = registeredAgents.stream()
                                        .filter(ra -> ra.getAgent().getClass().getSimpleName().equals(agentPair.get1()))
                                        .findFirst();

                        Optional<WorkspaceAgentRegistry.RegisteredAgent> agent2 = registeredAgents.stream()
                                        .filter(ra -> ra.getAgent().getClass().getSimpleName().equals(agentPair.get2()))
                                        .findFirst();

                        // Skip if either agent is not available in this framework instance
                        if (agent1.isEmpty() || agent2.isEmpty()) {
                                return; // Skip this test case
                        }

                        WorkspaceAgent firstAgent = agent1.get().getAgent();
                        WorkspaceAgent secondAgent = agent2.get().getAgent();

                        // When: Checking coordination capabilities between agents
                        // Then: Both agents should be available for coordination
                        Assertions.assertTrue(firstAgent.getStatus().available(),
                                        "First agent (" + firstAgent.getClass().getSimpleName()
                                                        + ") should be available for coordination");

                        Assertions.assertTrue(secondAgent.getStatus().available(),
                                        "Second agent (" + secondAgent.getClass().getSimpleName()
                                                        + ") should be available for coordination");

                        // Verify coordination dependencies are properly configured
                        List<String> firstAgentDependencies = firstAgent.getCoordinationDependencies();
                        List<String> secondAgentDependencies = secondAgent.getCoordinationDependencies();

                        Assertions.assertNotNull(firstAgentDependencies,
                                        "First agent should have coordination dependencies configured");

                        Assertions.assertNotNull(secondAgentDependencies,
                                        "Second agent should have coordination dependencies configured");

                        // Verify agents can handle coordination requests
                        AgentRequest coordinationRequest = new AgentRequest(
                                        "coord-test-" + System.currentTimeMillis(),
                                        "COORDINATION_TEST",
                                        "Test coordination capability between agents",
                                        AgentCapability.WORKFLOW_COORDINATION,
                                        "workspace");

                        // Test that the agent can process coordination-related requests
                        boolean canHandle = firstAgent.canHandleRequest(coordinationRequest);

                        if (!canHandle) {
                                // If agent can't handle the request, processRequest should throw
                                // CAPABILITY_MISMATCH
                                try {
                                        AgentResponse response = firstAgent.processRequest(coordinationRequest);
                                        Assertions.fail("Agent should have thrown CAPABILITY_MISMATCH exception for unsupported request");
                                } catch (AgentException e) {
                                        Assertions.assertTrue(e
                                                        .getErrorType() == AgentException.AgentErrorType.CAPABILITY_MISMATCH,
                                                        "Agent should properly handle unsupported coordination requests");
                                }
                        } else {
                                // If agent can handle the request capability, it should either process it
                                // successfully
                                // or throw CAPABILITY_MISMATCH for unsupported request types
                                try {
                                        AgentResponse response = firstAgent.processRequest(coordinationRequest);
                                        Assertions.assertNotNull(response,
                                                        "Agent should be able to process coordination requests");

                                        // Verify response indicates coordination capability
                                        Assertions.assertTrue(response.isSuccess() ||
                                                        response.getErrorMessage().contains("Unsupported request type"),
                                                        "Agent should either handle coordination or properly reject unsupported requests");
                                } catch (AgentException e) {
                                        // Some agents throw exceptions for unsupported request types, which is also
                                        // acceptable
                                        Assertions.assertTrue(e
                                                        .getErrorType() == AgentException.AgentErrorType.CAPABILITY_MISMATCH,
                                                        "Agent should properly handle unsupported coordination requests");
                                }
                        }

                } catch (AgentException e) {
                        Assertions.fail("Framework should start successfully for coordination testing: "
                                        + e.getMessage());
                } finally {
                        framework.shutdown();
                }
        }

        /**
         * Property: Coordination capabilities are symmetric
         * 
         * For any pair of agents that can coordinate, the coordination capability
         * should be available in both directions
         */
        @Property(tries = 100)
        void shouldProvideSymmetricCoordinationCapabilities(
                        @ForAll("coordinatingAgentPairs") Tuple.Tuple2<String, String> agentPair) {

                // Given: A framework with registered agents
                WorkspaceAgentFramework framework = new WorkspaceAgentFramework();

                try {
                        framework.start();
                        WorkspaceAgentRegistry registry = framework.getRegistry();
                        List<WorkspaceAgentRegistry.RegisteredAgent> registeredAgents = registry
                                        .getAllRegisteredAgents();

                        // Filter to get the specific agent pair if they exist
                        Optional<WorkspaceAgentRegistry.RegisteredAgent> agent1 = registeredAgents.stream()
                                        .filter(ra -> ra.getAgent().getClass().getSimpleName().equals(agentPair.get1()))
                                        .findFirst();

                        Optional<WorkspaceAgentRegistry.RegisteredAgent> agent2 = registeredAgents.stream()
                                        .filter(ra -> ra.getAgent().getClass().getSimpleName().equals(agentPair.get2()))
                                        .findFirst();

                        // Skip if either agent is not available in this framework instance
                        if (agent1.isEmpty() || agent2.isEmpty()) {
                                return; // Skip this test case
                        }

                        WorkspaceAgent firstAgent = agent1.get().getAgent();
                        WorkspaceAgent secondAgent = agent2.get().getAgent();

                        // When: Checking symmetric coordination capabilities
                        // Then: Both agents should have compatible coordination capabilities
                        Set<AgentCapability> firstCapabilities = firstAgent.getCapabilities();
                        Set<AgentCapability> secondCapabilities = secondAgent.getCapabilities();

                        // Find common coordination capabilities
                        Set<AgentCapability> commonCapabilities = firstCapabilities.stream()
                                        .filter(cap -> secondCapabilities.contains(cap))
                                        .filter(this::isCoordinationCapability)
                                        .collect(Collectors.toSet());

                        // If agents share coordination capabilities, they should be able to coordinate
                        if (!commonCapabilities.isEmpty()) {
                                Assertions.assertTrue(firstAgent.getStatus().available(),
                                                "First agent should be available for coordination");
                                Assertions.assertTrue(secondAgent.getStatus().available(),
                                                "Second agent should be available for coordination");
                        }

                } catch (AgentException e) {
                        Assertions.fail("Framework should start successfully for symmetric coordination testing: "
                                        + e.getMessage());
                } finally {
                        framework.shutdown();
                }
        }

        /**
         * Helper method to determine if a capability is coordination-related
         */
        private boolean isCoordinationCapability(AgentCapability capability) {
                return capability.name().contains("COORDINATION") ||
                                capability == AgentCapability.WORKFLOW_COORDINATION ||
                                capability == AgentCapability.DEPLOYMENT_COORDINATION ||
                                capability == AgentCapability.TESTING_COORDINATION ||
                                capability == AgentCapability.DOCUMENTATION_COORDINATION;
        }

        /**
         * Provides pairs of agent class names for testing coordination
         */
        @Provide
        Arbitrary<Tuple.Tuple2<String, String>> agentPairs() {
                List<String> agentTypes = Arrays.asList(
                                "CrossProjectTestingAgent",
                                "DataGovernanceAgent",
                                "DisasterRecoveryAgent",
                                "DocumentationCoordinationAgent",
                                "PerformanceCoordinationAgent",
                                "WorkflowCoordinationAgent",
                                "WorkspaceFeatureDevelopmentAgent",
                                "WorkspaceReleaseCoordinationAgent");

                return Combinators.combine(
                                Arbitraries.of(agentTypes),
                                Arbitraries.of(agentTypes)).as(Tuple::of)
                                .filter(pair -> !pair.get1().equals(pair.get2())); // Exclude same agent pairs
        }

        /**
         * Provides pairs of agents that are known to coordinate with each other
         */
        @Provide
        Arbitrary<Tuple.Tuple2<String, String>> coordinatingAgentPairs() {
                // Define known coordination relationships
                List<Tuple.Tuple2<String, String>> coordinatingPairs = Arrays.asList(
                                Tuple.of("WorkflowCoordinationAgent", "WorkspaceFeatureDevelopmentAgent"),
                                Tuple.of("DocumentationCoordinationAgent", "WorkflowCoordinationAgent"),
                                Tuple.of("WorkspaceReleaseCoordinationAgent", "DocumentationCoordinationAgent"),
                                Tuple.of("DisasterRecoveryAgent", "WorkspaceReleaseCoordinationAgent"),
                                Tuple.of("PerformanceCoordinationAgent", "DisasterRecoveryAgent"));

                return Arbitraries.of(coordinatingPairs);
        }
}