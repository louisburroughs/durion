package agents;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import core.AgentConfiguration;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

public class StoryOrchestrationAgentTest {
    
    private StoryOrchestrationAgent agent;
    
    @BeforeEach
    void setUp() {
        agent = new StoryOrchestrationAgent();
        agent.initialize(new AgentConfiguration("test-config", new Properties(), Map.of()));
    }
    
    @Test
    void testGenerateSequenceDocument() {
        CompletableFuture<core.AgentResult> result = agent.execute("GENERATE_SEQUENCE_DOCUMENT", Map.of());
        
        assertNotNull(result);
        assertTrue(result.isDone());
        
        core.AgentResult agentResult = result.join();
        assertTrue(agentResult.isSuccess());
        assertNotNull(agentResult.getData());
        assertTrue(agentResult.getData().toString().contains("Generated story-sequence.md"));
    }
    
    @Test
    void testAnalyzeStories() {
        CompletableFuture<core.AgentResult> result = agent.execute("ANALYZE_STORIES", Map.of());
        
        assertNotNull(result);
        assertTrue(result.isDone());
        
        core.AgentResult agentResult = result.join();
        assertTrue(agentResult.isSuccess());
        assertNotNull(agentResult.getData());
        assertTrue(agentResult.getData().toString().contains("Analyzed"));
    }
    
    @Test
    void testSequenceStories() {
        // First analyze stories
        agent.execute("ANALYZE_STORIES", Map.of()).join();
        
        // Then sequence them
        CompletableFuture<core.AgentResult> result = agent.execute("SEQUENCE_STORIES", Map.of());
        
        assertNotNull(result);
        assertTrue(result.isDone());
        
        core.AgentResult agentResult = result.join();
        assertTrue(agentResult.isSuccess());
        assertNotNull(agentResult.getData());
        assertTrue(agentResult.getData().toString().contains("Generated sequence"));
    }
    
    @Test
    void testClassifyStory() {
        // First analyze stories to load them
        agent.execute("ANALYZE_STORIES", Map.of()).join();
        
        // Then classify a specific story
        CompletableFuture<core.AgentResult> result = agent.execute("CLASSIFY_STORY", 
            Map.of("storyId", "STORY-001"));
        
        assertNotNull(result);
        assertTrue(result.isDone());
        
        core.AgentResult agentResult = result.join();
        assertTrue(agentResult.isSuccess());
        assertNotNull(agentResult.getData());
        assertTrue(agentResult.getData().toString().contains("classified as"));
    }
}
