package com.durion;

import java.util.Map;
import java.util.Properties;

import com.durion.agents.StoryOrchestrationAgent;
import com.durion.core.AgentConfiguration;
import com.durion.core.AgentResult;

public class TestFrontendCoordinationGeneration {
    public static void main(String[] args) {
        try {
            StoryOrchestrationAgent agent = new StoryOrchestrationAgent();
            agent.initialize(new AgentConfiguration("test", new Properties(), Map.of()));
            
            // Test frontend coordination generation
            AgentResult result = agent.execute("GENERATE_FRONTEND_COORDINATION", Map.of()).join();
            System.out.println("Frontend Coordination Generation Result:");
            System.out.println(result.getData());
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
