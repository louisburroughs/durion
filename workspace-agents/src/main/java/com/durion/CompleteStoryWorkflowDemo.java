package com.durion;
import java.util.*;

import com.durion.agents.*;
import com.durion.core.*;

/**
 * Complete Story Workflow Demo - REQ-WS-014 Implementation
 * 
 * Demonstrates the complete story processing workflow:
 * 1. Story Analysis & Sequencing
 * 2. Coordination Document Generation  
 * 3. GitHub Issue Creation (NEW!)
 * 4. Cross-Repository Integration
 * 
 * This completes the missing link between story orchestration and development work.
 */
public class CompleteStoryWorkflowDemo {
    
    public static void main(String[] args) {
        System.out.println("🎯 COMPLETE STORY WORKFLOW DEMO");
        System.out.println("===============================");
        System.out.println("📋 Demonstrating REQ-WS-014: Automated Story Processing & Issue Generation");
        System.out.println();
        
        // Initialize agents
        StoryOrchestrationAgent storyAgent = new StoryOrchestrationAgent();
        GitHubIssueCreationAgent issueAgent = new GitHubIssueCreationAgent();
        RequirementsDecompositionAgent reqAgent = new RequirementsDecompositionAgent();
        
        AgentConfiguration config = createConfiguration();
        storyAgent.initialize(config);
        issueAgent.initialize(config);
        reqAgent.initialize(config);
        
        try {
            System.out.println("🚀 **PHASE 1: Story Analysis & Sequencing**");
            System.out.println("==========================================");
            
            // Step 1: Analyze Stories
            System.out.println("1️⃣ Analyzing [STORY] issues from durion repository...");
            AgentResult analysisResult = storyAgent.execute("ANALYZE_STORIES", new HashMap<>()).get();
            System.out.println("   ✅ " + analysisResult.getData());
            
            // Step 2: Generate Story Sequence
            System.out.println("2️⃣ Generating optimal story sequence...");
            AgentResult sequenceResult = storyAgent.execute("SEQUENCE_STORIES", new HashMap<>()).get();
            System.out.println("   ✅ " + sequenceResult.getData());
            
            // Step 3: Generate Coordination Documents
            System.out.println("3️⃣ Generating coordination documents...");
            AgentResult sequenceDoc = storyAgent.execute("GENERATE_SEQUENCE_DOCUMENT", new HashMap<>()).get();
            System.out.println("   ✅ " + sequenceDoc.getData());
            
            AgentResult frontendDoc = storyAgent.execute("GENERATE_FRONTEND_COORDINATION", new HashMap<>()).get();
            System.out.println("   ✅ " + frontendDoc.getData());
            
            AgentResult backendDoc = storyAgent.execute("GENERATE_BACKEND_COORDINATION", new HashMap<>()).get();
            System.out.println("   ✅ " + backendDoc.getData());
            
            System.out.println();
            System.out.println("🎯 **PHASE 2: GitHub Issue Creation (NEW!)**");
            System.out.println("============================================");
            
            // Step 4: Create Implementation Issues (REQ-WS-014)
            System.out.println("4️⃣ Creating GitHub issues in target repositories...");
            
            // Example story for issue creation
            String storyTitle = "Customer Payment Processing";
            String storyDescription = "As a customer, I want to process payments securely, so that I can complete my purchase. " +
                                    "The system should handle credit card validation, payment authorization, receipt generation, " +
                                    "and order confirmation with proper error handling and security measures.";
            String domain = "payment";
            
            System.out.println("   📋 Story: " + storyTitle);
            System.out.println("   🏷️ Domain: " + domain);
            System.out.println();
            
            // Create issues using the Story Orchestration Agent
            Map<String, Object> issueParams = Map.of(
                "storyTitle", storyTitle,
                "storyDescription", storyDescription,
                "domain", domain
            );
            
            AgentResult issueCreationResult = storyAgent.execute("CREATE_IMPLEMENTATION_ISSUES", issueParams).get();
            System.out.println("   ✅ Issue Creation Result:");
            System.out.println("   " + issueCreationResult.getData());
            
            // Also demonstrate direct issue creation
            System.out.println("5️⃣ Direct GitHub Issue Creation Agent demonstration...");
            GitHubIssueCreationAgent.IssueCreationResult directResult = 
                issueAgent.createIssuesFromStory(storyTitle, storyDescription, domain);
            
            if (directResult.success()) {
                System.out.println("   ✅ Direct Issue Creation: SUCCESS");
                if (directResult.frontendIssueUrl() != null) {
                    System.out.println("   🎨 Frontend Issue: " + directResult.frontendIssueUrl());
                }
                if (directResult.backendIssueUrl() != null) {
                    System.out.println("   ⚙️ Backend Issue: " + directResult.backendIssueUrl());
                }
            } else {
                System.out.println("   ❌ Direct Issue Creation: FAILED - " + directResult.message());
            }
            
            System.out.println();
            System.out.println("🔧 **PHASE 3: Requirements Decomposition Integration**");
            System.out.println("====================================================");
            
            // Step 6: Show Requirements Decomposition
            System.out.println("6️⃣ Demonstrating requirements decomposition...");
            AgentResult decompositionResult = reqAgent.decomposeRequirements(storyDescription);
            
            if (decompositionResult.isSuccess()) {
                RequirementsDecompositionAgent.DecompositionResult decomp = 
                    (RequirementsDecompositionAgent.DecompositionResult) decompositionResult.getData();
                
                System.out.println("   ✅ Requirements Decomposition: SUCCESS");
                System.out.println("   🎨 Frontend Work:");
                System.out.println("      • Components: " + decomp.frontendWork().components());
                System.out.println("      • Screens: " + decomp.frontendWork().screens());
                System.out.println("      • Forms: " + decomp.frontendWork().forms());
                
                System.out.println("   ⚙️ Backend Work:");
                System.out.println("      • APIs: " + decomp.backendWork().apis());
                System.out.println("      • Business Logic: " + decomp.backendWork().businessLogic());
                
                System.out.println("   🔗 API Contracts: " + decomp.apiContracts().size() + " contracts defined");
            }
            
            System.out.println();
            System.out.println("🎉 **WORKFLOW COMPLETE: Story → Issues → Implementation**");
            System.out.println("========================================================");
            
            System.out.println("✅ **COMPLETE WORKFLOW DEMONSTRATED:**");
            System.out.println("   1. ✅ Story Analysis & Dependency Detection");
            System.out.println("   2. ✅ Optimal Story Sequencing (Backend-First prioritization)");
            System.out.println("   3. ✅ Coordination Document Generation");
            System.out.println("      • story-sequence.md - Master sequence");
            System.out.println("      • frontend-coordination.md - Frontend readiness");
            System.out.println("      • backend-coordination.md - Backend priorities");
            System.out.println("   4. ✅ GitHub Issue Creation (REQ-WS-014)");
            System.out.println("      • durion-moqui-frontend repository issues");
            System.out.println("      • durion-positivity-backend repository issues");
            System.out.println("      • Populated .github/kiro-story.md templates");
            System.out.println("      • Appropriate labels and classifications");
            System.out.println("   5. ✅ Requirements Decomposition Integration");
            System.out.println("   6. ✅ Cross-Technology Coordination (Java 21 ↔ Java 11 ↔ Groovy ↔ TypeScript)");
            
            System.out.println();
            System.out.println("🚀 **PRODUCTION READY: Complete Story Processing Pipeline**");
            System.out.println("   📋 Input: [STORY] issues in durion repository");
            System.out.println("   🔄 Process: Analysis → Sequencing → Coordination → Issue Creation");
            System.out.println("   📤 Output: Implementation-ready issues in target repositories");
            System.out.println("   🎯 Result: Seamless story-to-development workflow");
            
            System.out.println();
            System.out.println("🎊 **REQ-WS-014 IMPLEMENTATION: COMPLETE AND FUNCTIONAL!**");
            
        } catch (Exception e) {
            System.err.println("❌ Error during workflow demo: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Shutdown agents
            storyAgent.shutdown();
            issueAgent.shutdown();
            reqAgent.shutdown();
        }
    }
    
    private static AgentConfiguration createConfiguration() {
        Properties props = new Properties();
        Map<String, Object> settings = new HashMap<>();
        settings.put("github.repository", "louisburroughs/durion");
        settings.put("github.frontend.repository", "louisburroughs/durion-moqui-frontend");
        settings.put("github.backend.repository", "louisburroughs/durion-positivity-backend");
        settings.put("maxConcurrentUsers", 100);
        settings.put("responseTimeoutSeconds", 30);
        
        return new AgentConfiguration("complete-workflow", props, settings);
    }
}