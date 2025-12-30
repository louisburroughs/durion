package com.durion;
import java.util.HashMap;

import com.durion.agents.StoryOrchestrationAgent;
import com.durion.core.AgentConfiguration;
import com.durion.core.AgentResult;

/**
 * Test backend coordination generation specifically
 */
public class TestBackendGeneration {
    
    public static void main(String[] args) {
        System.out.println("🔧 Testing Backend Coordination Generation\n");
        
        // Initialize the agent
        StoryOrchestrationAgent agent = new StoryOrchestrationAgent();
        AgentConfiguration config = new AgentConfiguration("story-orchestration-agent", 
            new java.util.Properties(), new HashMap<>());
        agent.initialize(config);
        
        try {
            // First analyze stories
            System.out.println("1️⃣ Analyzing Stories...");
            AgentResult result1 = agent.execute("ANALYZE_STORIES", new HashMap<>()).get();
            System.out.println("   ✅ " + result1.getData());
            
            // Then generate backend coordination
            System.out.println("\n2️⃣ Generating Backend Coordination...");
            AgentResult result2 = agent.execute("GENERATE_BACKEND_COORDINATION", new HashMap<>()).get();
            System.out.println("   Success: " + result2.isSuccess());
            System.out.println("   Data: " + result2.getData());
            System.out.println("   Message: " + result2.getMessage());
            
            if (result2.isSuccess()) {
                System.out.println("\n✅ Backend coordination generated successfully!");
            } else {
                System.out.println("\n❌ Backend coordination generation failed!");
            }
            
        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            agent.shutdown();
        }
    }
}