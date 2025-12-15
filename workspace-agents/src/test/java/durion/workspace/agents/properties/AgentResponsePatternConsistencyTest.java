package durion.workspace.agents.properties;

import durion.workspace.agents.core.*;
import durion.workspace.agents.coordination.*;
import net.jqwik.api.*;
import net.jqwik.api.constraints.NotEmpty;
import org.junit.jupiter.api.Tag;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * **Feature: workspace-agents-structure-fix, Property 9: Response pattern
 * consistency**
 * **Validates: Requirements 4.5**
 * 
 * Property-based test to verify that all agent operations return responses that
 * follow
 * the AgentResponse pattern consistently.
 */
@Tag("property-test")
public class AgentResponsePatternConsistencyTest {

    /**
     * Property 9: Response pattern consistency
     * For any agent operation that returns a response, the response should follow
     * the AgentResponse pattern
     */
    @Property(tries = 100)
    @Tag("property-test")
    void agentResponsePatternConsistency(@ForAll("agentInstances") AbstractWorkspaceAgent agent,
            @ForAll("validRequests") AgentRequest request) {

        try {
            // Process a valid request
            AgentResponse response = agent.processRequest(request);

            // Verify response follows the pattern
            verifyResponseStructure(agent, request, response);

        } catch (AgentException e) {
            // AgentException is acceptable - it means the agent couldn't process the
            // request
            // but this is still following the error handling pattern
            assert e.getAgentId().equals(agent.getAgentId()) : "AgentException should have correct agent ID";
        }
    }

    /**
     * Property to verify response consistency for successful operations
     */
    @Property(tries = 100)
    @Tag("property-test")
    void successfulResponseConsistency(@ForAll("agentInstances") AbstractWorkspaceAgent agent,
            @ForAll("supportedRequests") AgentRequest request) {

        // Only test requests that the agent can handle
        if (!agent.canHandleRequest(request)) {
            return; // Skip this test case
        }

        try {
            AgentResponse response = agent.processRequest(request);

            if (response != null && response.isSuccess()) {
                // Verify successful response structure
                verifySuccessfulResponseStructure(agent, request, response);
            }

        } catch (AgentException e) {
            // Even if an exception is thrown, it should be properly structured
            assert e.getAgentId().equals(agent.getAgentId()) : "AgentException should have correct agent ID";
        }
    }

    /**
     * Property to verify response metadata consistency
     */
    @Property(tries = 100)
    @Tag("property-test")
    void responseMetadataConsistency(@ForAll("agentInstances") AbstractWorkspaceAgent agent,
            @ForAll("validRequests") AgentRequest request) {

        try {
            AgentResponse response = agent.processRequest(request);

            if (response != null) {
                // Verify metadata structure
                verifyResponseMetadata(response);
            }

        } catch (AgentException e) {
            // Exception is acceptable
        }
    }

    /**
     * Property to verify response timing consistency
     */
    @Property(tries = 50) // Fewer tries since timing tests can be slower
    @Tag("property-test")
    void responseTimingConsistency(@ForAll("agentInstances") AbstractWorkspaceAgent agent,
            @ForAll("validRequests") AgentRequest request) {

        try {
            Instant beforeRequest = Instant.now();
            AgentResponse response = agent.processRequest(request);
            Instant afterRequest = Instant.now();

            if (response != null) {
                // Response timestamp should be within the request processing window
                assert !response.getTimestamp().isBefore(beforeRequest)
                        : "Response timestamp should not be before request start time";
                assert !response.getTimestamp().isAfter(afterRequest)
                        : "Response timestamp should not be after request completion time";
            }

        } catch (AgentException e) {
            // Exception is acceptable
        }
    }

    /**
     * Property to verify response quality scoring consistency
     */
    @Property(tries = 100)
    @Tag("property-test")
    void responseQualityConsistency(@ForAll("agentInstances") AbstractWorkspaceAgent agent,
            @ForAll("validRequests") AgentRequest request) {

        try {
            AgentResponse response = agent.processRequest(request);

            if (response != null) {
                // Quality score should be valid
                int qualityScore = response.getQualityScore();
                assert qualityScore >= 0 && qualityScore <= 100
                        : "Response quality score should be 0-100, got: " + qualityScore;

                // Successful responses should have higher quality scores
                if (response.isSuccess()) {
                    assert qualityScore >= 40
                            : "Successful response should have quality score >= 40, got: " + qualityScore;
                }
            }

        } catch (AgentException e) {
            // Exception is acceptable
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
     * Provides valid requests for testing
     */
    @Provide
    Arbitrary<AgentRequest> validRequests() {
        List<String> requestTypes = Arrays.asList(
                "GET_STATUS",
                "GET_CAPABILITIES",
                "HEALTH_CHECK",
                "COORDINATION_REQUEST");

        return Arbitraries.of(requestTypes)
                .map(requestType -> new AgentRequest("test-request-" + System.currentTimeMillis(),
                        requestType, "Test request", AgentCapability.TESTING_COORDINATION, "test"));
    }

    /**
     * Provides requests that agents are likely to support
     */
    @Provide
    Arbitrary<AgentRequest> supportedRequests() {
        // These are generic request types that most agents should be able to handle
        List<String> supportedTypes = Arrays.asList(
                "GET_STATUS",
                "HEALTH_CHECK");

        return Arbitraries.of(supportedTypes)
                .map(requestType -> new AgentRequest("test-request-" + System.currentTimeMillis(),
                        requestType, "Test supported request", AgentCapability.TESTING_COORDINATION, "test"));
    }

    /**
     * Helper method to verify basic response structure
     */
    private void verifyResponseStructure(AbstractWorkspaceAgent agent, AgentRequest request, AgentResponse response) {
        assert response != null : "Agent " + agent.getAgentId() + " should return non-null response";

        // Verify request ID correlation
        assert response.getRequestId() != null : "Response should have request ID";
        assert response.getRequestId().equals(request.getRequestId())
                : "Response request ID should match original request ID";

        // Verify timestamp is set
        assert response.getTimestamp() != null : "Response should have timestamp";

        // Verify success flag consistency
        if (response.isSuccess()) {
            assert response.getErrorMessage() == null || response.getErrorMessage().isEmpty()
                    : "Successful response should not have error message";
        } else {
            assert response.getErrorMessage() != null && !response.getErrorMessage().trim().isEmpty()
                    : "Failed response should have meaningful error message";
        }
    }

    /**
     * Helper method to verify successful response structure
     */
    private void verifySuccessfulResponseStructure(AbstractWorkspaceAgent agent, AgentRequest request,
            AgentResponse response) {
        assert response.isSuccess() : "Response should be marked as successful";

        // Verify guidance is provided
        assert response.getGuidance() != null : "Successful response should have guidance";

        // Verify recommendations are provided
        assert response.getRecommendations() != null : "Successful response should have recommendations list";

        // Verify metadata is accessible
        Map<String, Object> metadata = response.getMetadata();
        assert metadata != null : "Response should have metadata map";

        // Quality score should be reasonable for successful responses
        int qualityScore = response.getQualityScore();
        assert qualityScore >= 40 : "Successful response should have quality score >= 40";
    }

    /**
     * Helper method to verify response metadata
     */
    private void verifyResponseMetadata(AgentResponse response) {
        Map<String, Object> metadata = response.getMetadata();
        assert metadata != null : "Response should have metadata map";

        // Metadata should be modifiable through the response
        int initialSize = metadata.size();
        response.addMetadata("test-key", "test-value");

        Object retrievedValue = response.getMetadata("test-key");
        assert "test-value".equals(retrievedValue) : "Should be able to add and retrieve metadata";

        // Verify metadata map is properly encapsulated
        Map<String, Object> metadataCopy = response.getMetadata();
        assert metadataCopy != null : "Should be able to get metadata copy";
        assert metadataCopy.size() >= initialSize : "Metadata copy should include added items";
    }
}