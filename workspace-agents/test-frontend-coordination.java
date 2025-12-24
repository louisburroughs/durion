import agents.StoryOrchestrationAgent;
import core.AgentConfiguration;
import java.util.Map;

public class TestFrontendCoordination {
    public static void main(String[] args) {
        StoryOrchestrationAgent agent = new StoryOrchestrationAgent();
        agent.initialize(new AgentConfiguration("test", Map.of()));
        
        // Test frontend coordination generation
        String result = agent.execute("GENERATE_FRONTEND_COORDINATION", Map.of()).join().getResult();
        System.out.println("Frontend Coordination Result: " + result);
    }
}
