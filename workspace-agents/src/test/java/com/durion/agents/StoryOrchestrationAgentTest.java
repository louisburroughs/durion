package com.durion.agents;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.durion.agents.StoryOrchestrationAgent;
import com.durion.core.AgentConfiguration;
import com.durion.core.AgentResult;

public class StoryOrchestrationAgentTest {

    private StoryOrchestrationAgent agent;

    @BeforeEach
    void setUp() {
        agent = new StoryOrchestrationAgent();
        agent.initialize(new AgentConfiguration("test-config", new Properties(), Map.of()));
    }

    @Test
    void testGenerateSequenceDocument() throws Exception {
        CompletableFuture<AgentResult> result = agent.execute("GENERATE_SEQUENCE_DOCUMENT", Map.of());
        AgentResult agentResult = result.get();
        assertTrue(agentResult.isSuccess(), "Operation failed: " + agentResult.getMessage());
        assertNotNull(agentResult.getData());
        assertTrue(agentResult.getData().toString().contains("Generated story-sequence.md"));
    }

    @Test
    void testAnalyzeStories() throws Exception {
        CompletableFuture<AgentResult> result = agent.execute("ANALYZE_STORIES", Map.of());
        AgentResult agentResult = result.get();
        assertTrue(agentResult.isSuccess(), "Operation failed: " + agentResult.getMessage());
        assertNotNull(agentResult.getData());
        assertTrue(agentResult.getData().toString().contains("Analyzed"));
    }

    @Test
    void testSequenceStories() throws Exception {
        agent.execute("ANALYZE_STORIES", Map.of()).get();
        CompletableFuture<AgentResult> result = agent.execute("SEQUENCE_STORIES", Map.of());
        AgentResult agentResult = result.get();
        assertTrue(agentResult.isSuccess(), "Operation failed: " + agentResult.getMessage());
        assertNotNull(agentResult.getData());
        assertTrue(agentResult.getData().toString().contains("Generated sequence"));
    }

    @Test
    void testClassifyStory() throws Exception {
        agent.execute("ANALYZE_STORIES", Map.of()).get();
        CompletableFuture<AgentResult> result = agent.execute("CLASSIFY_STORY", Map.of("storyId", "STORY-001"));
        AgentResult agentResult = result.get();
        assertTrue(agentResult.isSuccess(), "Operation failed: " + agentResult.getMessage());
        assertNotNull(agentResult.getData());
        assertTrue(agentResult.getData().toString().contains("classified as"));
    }
}
