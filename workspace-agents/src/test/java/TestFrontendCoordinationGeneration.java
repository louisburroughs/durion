package test;

import agents.StoryOrchestrationAgent;
import core.AgentConfiguration;
import java.util.Map;

public class TestFrontendCoordinationGeneration {
    public static void main(String[] args) {
        try {
            StoryOrchestrationAgent agent = new StoryOrchestrationAgent();
            agent.initialize(new AgentConfiguration("test", Map.of()));
            
            // Test frontend coordination generation
            String result = agent.execute("GENERATE_FRONTEND_COORDINATION", Map.of()).join().getResult();
            System.out.println("Frontend Coordination Generation Result:");
            System.out.println(result);
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
