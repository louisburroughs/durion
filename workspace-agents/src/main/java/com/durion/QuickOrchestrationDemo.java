package com.durion;
import java.util.HashMap;
import java.util.Map;

import com.durion.agents.StoryOrchestrationAgent;
import com.durion.core.AgentConfiguration;
import com.durion.core.AgentResult;

/**
 * Quick demo of Story Orchestration Agent capabilities
 */
public class QuickOrchestrationDemo {
    
    public static void main(String[] args) {
        System.out.println("🚀 Story Orchestration Agent - Full Demo\n");
        
        // Initialize the agent
        StoryOrchestrationAgent agent = new StoryOrchestrationAgent();
        AgentConfiguration config = new AgentConfiguration("story-orchestration-agent", 
            new java.util.Properties(), new HashMap<>());
        agent.initialize(config);
        
        try {
            // Run the complete orchestration pipeline
            System.out.println("📊 Running Complete Story Orchestration Pipeline...\n");
            
            // Step 1: Analyze Stories
            System.out.println("1️⃣ Analyzing Stories...");
            AgentResult result1 = agent.execute("ANALYZE_STORIES", new HashMap<>()).get();
            System.out.println("   ✅ " + result1.getData() + "\n");
            
            // Step 2: Generate Global Story Sequence
            System.out.println("2️⃣ Generating Global Story Sequence...");
            AgentResult result2 = agent.execute("GENERATE_SEQUENCE_DOCUMENT", new HashMap<>()).get();
            System.out.println("   ✅ " + result2.getData() + "\n");
            
            // Step 3: Generate Frontend Coordination
            System.out.println("3️⃣ Generating Frontend Coordination View...");
            AgentResult result3 = agent.execute("GENERATE_FRONTEND_COORDINATION", new HashMap<>()).get();
            System.out.println("   ✅ " + result3.getData() + "\n");
            
            // Step 4: Generate Backend Coordination
            System.out.println("4️⃣ Generating Backend Coordination View...");
            AgentResult result4 = agent.execute("GENERATE_BACKEND_COORDINATION", new HashMap<>()).get();
            System.out.println("   ✅ " + result4.getData() + "\n");
            
            // Step 5: Validate Orchestration
            System.out.println("5️⃣ Validating Orchestration Consistency...");
            AgentResult result5 = agent.execute("VALIDATE_ORCHESTRATION", new HashMap<>()).get();
            System.out.println("   ✅ " + result5.getData() + "\n");
            
            // Step 6: Setup Triggers
            System.out.println("6️⃣ Setting up Orchestration Triggers...");
            AgentResult result6 = agent.execute("SETUP_TRIGGERS", new HashMap<>()).get();
            System.out.println("   ✅ " + result6.getData() + "\n");
            
            // Step 7: Test Story Classification
            System.out.println("7️⃣ Testing Story Classification...");
            Map<String, Object> classifyParams = new HashMap<>();
            classifyParams.put("storyId", "STORY-001");
            AgentResult result7 = agent.execute("CLASSIFY_STORY", classifyParams).get();
            System.out.println("   ✅ " + result7.getData() + "\n");
            
            // Step 8: Test Event Handling
            System.out.println("8️⃣ Testing Story Event Handling...");
            Map<String, Object> eventParams = new HashMap<>();
            eventParams.put("eventType", "story_created");
            eventParams.put("storyId", "STORY-005");
            AgentResult result8 = agent.execute("HANDLE_STORY_EVENT", eventParams).get();
            System.out.println("   ✅ " + result8.getData() + "\n");
            
            System.out.println("🎉 Story Orchestration Complete!");
            System.out.println("\n📁 Generated Files:");
            System.out.println("   • .github/orchestration/story-sequence.md");
            System.out.println("   • .github/orchestration/frontend-coordination.md");
            System.out.println("   • .github/orchestration/backend-coordination.md");
            
            System.out.println("\n🔧 Agent Capabilities Demonstrated:");
            System.out.println("   ✓ Story Analysis & Dependency Graph Building");
            System.out.println("   ✓ Requirements Decomposition (Backend-First vs Frontend-First)");
            System.out.println("   ✓ Global Story Sequencing with Dependency Resolution");
            System.out.println("   ✓ Frontend Coordination (Ready/Blocked/Parallel Stories)");
            System.out.println("   ✓ Backend Coordination (Prioritized by Frontend Unblock Value)");
            System.out.println("   ✓ Cross-Document Validation & Consistency Checking");
            System.out.println("   ✓ Event-Driven Orchestration Triggers");
            System.out.println("   ✓ Story Classification & Metadata Management");
            
        } catch (Exception e) {
            System.err.println("❌ Error during orchestration: " + e.getMessage());
            e.printStackTrace();
        } finally {
            agent.shutdown();
        }
    }
}