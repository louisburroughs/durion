package durion.workspace.agents.properties;

import durion.workspace.agents.core.*;
import durion.workspace.agents.coordination.*;
import net.jqwik.api.*;
import net.jqwik.api.constraints.NotEmpty;
import org.junit.jupiter.api.Tag;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * **Feature: workspace-agents-structure-fix, Property 8: Error handling pattern
 * compliance**
 * **Validates: Requirements 4.4**
 * 
 * Property-based test to verify that all agents use AgentException with proper
 * error types
 * for error handling and follow consistent error handling patterns.
 */
@Tag("property-test")
public class AgentErrorHandlingPatternComplianceTest {

    /**
     * Property 8: Error handling pattern compliance
     * For any agent that throws exceptions, those exceptions should be
     * AgentException instances with proper error types
     */
    @Property(tries = 100)
    @Tag("property-test")
    void agentErrorHandlingPatternCompliance(@ForAll("agentInstances") AbstractWorkspaceAgent agent,
            @ForAll("invalidRequests") AgentRequest invalidRequest) {

        try {
            // Try to process an invalid request that should cause an exception
            AgentResponse response = agent.processRequest(invalidRequest);

            // If no exception was thrown, the response should indicate an error
            if (response != null && !response.isSuccess()) {
                // This is acceptable - agent handled error gracefully with error response
                assert response.getErrorMessage() != null && !response.getErrorMessage().trim().isEmpty()
                        : "Agent " + agent.getAgentId() + " error response should have a non-empty error message";
            }

        } catch (AgentException e) {
            // This is the expected pattern - verify the exception is properly structured
            verifyAgentExceptionStructure(agent, e);

        } catch (Exception e) {
            // Any other exception type is a violation of the error handling pattern
            assert false : "Agent " + agent.getAgentId() + " threw non-AgentException: " +
                    e.getClass().getSimpleName() + " - " + e.getMessage() +
                    ". Should throw AgentException instead.";
        }
    }

    /**
     * Property to verify error types are valid and meaningful
     */
    @Property(tries = 100)
    @Tag("property-test")
    void agentErrorTypeValidity(@ForAll("agentInstances") AbstractWorkspaceAgent agent,
            @ForAll("errorScenarios") ErrorScenario scenario) {

        try {
            // Create a request that should trigger the specific error scenario
            AgentRequest request = createRequestForErrorScenario(scenario);
            agent.processRequest(request);

        } catch (AgentException e) {
            // Verify the error type is appropriate for the scenario
            verifyErrorTypeAppropriate(scenario, e.getErrorType());

            // Verify error message is meaningful
            assert e.getMessage() != null && !e.getMessage().trim().isEmpty()
                    : "AgentException should have a meaningful error message";

            // Verify agent ID is set correctly
            assert e.getAgentId() != null && e.getAgentId().equals(agent.getAgentId())
                    : "AgentException should have correct agent ID";

        } catch (Exception e) {
            // Non-AgentException is a pattern violation
            assert false : "Agent should throw AgentException for error scenario " + scenario +
                    ", but threw " + e.getClass().getSimpleName();
        }
    }

    /**
     * Property to verify error handling doesn't break agent state
     */
    @Property(tries = 100)
    @Tag("property-test")
    void agentErrorHandlingStateConsistency(@ForAll("agentInstances") AbstractWorkspaceAgent agent) {
        // Get initial agent state
        AgentStatus initialStatus = agent.getStatus();
        Set<AgentCapability> initialCapabilities = agent.getCapabilities();

        try {
            // Try an operation that should fail
            AgentRequest invalidRequest = new AgentRequest("test-request-" + System.currentTimeMillis(),
                    "invalid-request-type", "Test invalid request", AgentCapability.TESTING_COORDINATION, "test");
            agent.processRequest(invalidRequest);

        } catch (AgentException e) {
            // Expected - verify agent state is still consistent after error
            AgentStatus statusAfterError = agent.getStatus();
            Set<AgentCapability> capabilitiesAfterError = agent.getCapabilities();

            // Agent should still be available (error handling shouldn't break the agent)
            assert statusAfterError.available()
                    : "Agent " + agent.getAgentId() + " should remain available after handling error";

            // Capabilities should remain unchanged
            assert initialCapabilities.equals(capabilitiesAfterError)
                    : "Agent " + agent.getAgentId() + " capabilities should not change after error handling";

        } catch (Exception e) {
            assert false : "Agent should throw AgentException, not " + e.getClass().getSimpleName();
        }
    }

    /**
     * Property to verify timeout handling follows the pattern
     */
    @Property(tries = 50) // Fewer tries since this might be slower
    @Tag("property-test")
    void agentTimeoutHandlingCompliance(@ForAll("agentInstances") AbstractWorkspaceAgent agent) {
        // Create a request that might timeout (this is simulated)
        AgentRequest timeoutRequest = new AgentRequest("timeout-request-" + System.currentTimeMillis(),
                "timeout-test", "Test timeout request", AgentCapability.TESTING_COORDINATION, "test");

        try {
            long startTime = System.currentTimeMillis();
            agent.processRequest(timeoutRequest);
            long endTime = System.currentTimeMillis();

            // If the request completed, it should be within reasonable time
            long duration = endTime - startTime;
            assert duration < 10000 : // 10 seconds max for test
                    "Agent " + agent.getAgentId() + " took too long: " + duration + "ms";

        } catch (AgentException e) {
            // If timeout occurred, verify it's the right error type
            if (e.getErrorType() == AgentException.AgentErrorType.TIMEOUT) {
                // This is correct timeout handling
                assert e.getMessage().toLowerCase().contains("timeout")
                        : "Timeout AgentException should mention timeout in message";
            }
            // Other error types are also acceptable for this test

        } catch (Exception e) {
            assert false : "Agent should throw AgentException for timeout, not " + e.getClass().getSimpleName();
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
                new WorkspaceReleaseCoordinationAgent());

        return Arbitraries.of(agents);
    }

    /**
     * Provides invalid requests that should trigger error handling
     */
    @Provide
    Arbitrary<AgentRequest> invalidRequests() {
        List<String> invalidRequestTypes = Arrays.asList(
                "INVALID_REQUEST_TYPE",
                "UNSUPPORTED_OPERATION",
                "MALFORMED_REQUEST",
                "",
                null);

        return Arbitraries.of(invalidRequestTypes)
                .map(requestType -> new AgentRequest("test-request-" + System.currentTimeMillis(),
                        requestType, "Test invalid request", AgentCapability.TESTING_COORDINATION, "test"));
    }

    /**
     * Provides error scenarios for testing
     */
    @Provide
    Arbitrary<ErrorScenario> errorScenarios() {
        return Arbitraries.of(ErrorScenario.values());
    }

    /**
     * Helper method to verify AgentException structure
     */
    private void verifyAgentExceptionStructure(AbstractWorkspaceAgent agent, AgentException e) {
        // Verify agent ID is set
        assert e.getAgentId() != null : "AgentException should have agent ID";
        assert e.getAgentId().equals(agent.getAgentId())
                : "AgentException agent ID should match the agent that threw it";

        // Verify error type is set
        assert e.getErrorType() != null : "AgentException should have error type";

        // Verify error type is valid enum value
        boolean isValidErrorType = Arrays.asList(AgentException.AgentErrorType.values())
                .contains(e.getErrorType());
        assert isValidErrorType : "AgentException should have valid error type: " + e.getErrorType();

        // Verify message is meaningful
        assert e.getMessage() != null && !e.getMessage().trim().isEmpty()
                : "AgentException should have meaningful error message";
    }

    /**
     * Helper method to create request for specific error scenario
     */
    private AgentRequest createRequestForErrorScenario(ErrorScenario scenario) {
        String requestId = "test-request-" + System.currentTimeMillis();
        switch (scenario) {
            case CAPABILITY_MISMATCH:
                return new AgentRequest(requestId, "UNSUPPORTED_CAPABILITY", "Test capability mismatch",
                        AgentCapability.API_CONTRACT_MANAGEMENT, "test");
            case TIMEOUT:
                return new AgentRequest(requestId, "TIMEOUT_TEST", "Test timeout scenario",
                        AgentCapability.TESTING_COORDINATION, "test");
            case UNAVAILABLE:
                return new AgentRequest(requestId, "UNAVAILABLE_TEST", "Test unavailable scenario",
                        AgentCapability.TESTING_COORDINATION, "test");
            case CONFIGURATION_ERROR:
                return new AgentRequest(requestId, "CONFIG_ERROR_TEST", "Test configuration error",
                        AgentCapability.TESTING_COORDINATION, "test");
            case COORDINATION_FAILURE:
                return new AgentRequest(requestId, "COORDINATION_FAILURE_TEST", "Test coordination failure",
                        AgentCapability.TESTING_COORDINATION, "test");
            case PERFORMANCE_DEGRADATION:
                return new AgentRequest(requestId, "PERFORMANCE_TEST", "Test performance degradation",
                        AgentCapability.PERFORMANCE_OPTIMIZATION, "test");
            case RESOURCE_EXHAUSTION:
                return new AgentRequest(requestId, "RESOURCE_TEST", "Test resource exhaustion",
                        AgentCapability.TESTING_COORDINATION, "test");
            default:
                return new AgentRequest(requestId, "UNKNOWN_ERROR_TEST", "Test unknown error",
                        AgentCapability.TESTING_COORDINATION, "test");
        }
    }

    /**
     * Helper method to verify error type is appropriate for scenario
     */
    private void verifyErrorTypeAppropriate(ErrorScenario scenario, AgentException.AgentErrorType errorType) {
        switch (scenario) {
            case CAPABILITY_MISMATCH:
                assert errorType == AgentException.AgentErrorType.CAPABILITY_MISMATCH
                        : "Capability mismatch scenario should produce CAPABILITY_MISMATCH error type";
                break;
            case TIMEOUT:
                assert errorType == AgentException.AgentErrorType.TIMEOUT
                        : "Timeout scenario should produce TIMEOUT error type";
                break;
            case UNAVAILABLE:
                assert errorType == AgentException.AgentErrorType.UNAVAILABLE
                        : "Unavailable scenario should produce UNAVAILABLE error type";
                break;
            case CONFIGURATION_ERROR:
                assert errorType == AgentException.AgentErrorType.CONFIGURATION_ERROR
                        : "Configuration error scenario should produce CONFIGURATION_ERROR error type";
                break;
            case COORDINATION_FAILURE:
                assert errorType == AgentException.AgentErrorType.COORDINATION_FAILURE
                        : "Coordination failure scenario should produce COORDINATION_FAILURE error type";
                break;
            case PERFORMANCE_DEGRADATION:
                assert errorType == AgentException.AgentErrorType.PERFORMANCE_DEGRADATION
                        : "Performance degradation scenario should produce PERFORMANCE_DEGRADATION error type";
                break;
            case RESOURCE_EXHAUSTION:
                assert errorType == AgentException.AgentErrorType.RESOURCE_EXHAUSTION
                        : "Resource exhaustion scenario should produce RESOURCE_EXHAUSTION error type";
                break;
            default:
                // For unknown scenarios, any error type is acceptable as long as it's valid
                assert errorType != null : "Error type should not be null";
        }
    }

    /**
     * Error scenarios for testing
     */
    public enum ErrorScenario {
        CAPABILITY_MISMATCH,
        TIMEOUT,
        UNAVAILABLE,
        CONFIGURATION_ERROR,
        COORDINATION_FAILURE,
        PERFORMANCE_DEGRADATION,
        RESOURCE_EXHAUSTION
    }
}