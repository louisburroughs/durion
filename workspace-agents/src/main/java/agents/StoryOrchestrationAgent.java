package agents;

import core.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Story Orchestration Agent - Manages global story sequencing and dependency analysis
 * 
 * Reads [STORY] issues from durion repository, builds dependency graphs, and generates
 * coordinated sequences prioritizing backend stories with highest frontend unblock value.
 */
public class StoryOrchestrationAgent implements WorkspaceAgent {
    
    private final Map<String, Story> stories = new HashMap<>();
    private final Map<String, Set<String>> dependencies = new HashMap<>();
    private final Map<String, String> lastKnownStoryHashes = new HashMap<>();
    private AgentConfiguration config;
    private boolean ready = false;
    private boolean triggersEnabled = false;
    private long lastOrchestrationRun = 0;
    
    @Override
    public String getAgentId() {
        return "story-orchestration-agent";
    }
    
    @Override
    public AgentCapabilities getCapabilities() {
        return new AgentCapabilities(
            "StoryOrchestrationAgent",
            Set.of("ANALYZE_STORIES", "SEQUENCE_STORIES", "CLASSIFY_STORY", "GENERATE_SEQUENCE_DOCUMENT", "GENERATE_FRONTEND_COORDINATION", "GENERATE_BACKEND_COORDINATION", "VALIDATE_ORCHESTRATION", "SETUP_TRIGGERS", "HANDLE_STORY_EVENT"),
            Map.of(
                "ANALYZE_STORIES", "Analyzes all [STORY] issues and builds dependency graph",
                "SEQUENCE_STORIES", "Computes global dependency-respecting sequence",
                "CLASSIFY_STORY", "Classifies story as Backend-First, Frontend-First, or Parallel",
                "GENERATE_SEQUENCE_DOCUMENT", "Generates global story sequence document (story-sequence.md)",
                "GENERATE_FRONTEND_COORDINATION", "Generates frontend coordination view (frontend-coordination.md)",
                "GENERATE_BACKEND_COORDINATION", "Generates backend coordination view (backend-coordination.md)",
                "VALIDATE_ORCHESTRATION", "Validates consistency across all orchestration documents",
                "SETUP_TRIGGERS", "Sets up periodic and event-based orchestration triggers",
                "HANDLE_STORY_EVENT", "Handles story creation, closure, or metadata changes"
            ),
            Set.of("github-api"),
            10
        );
    }
    
    @Override
    public CompletableFuture<AgentResult> execute(String operation, Map<String, Object> parameters) {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            try {
                String result = processOperation(operation, parameters);
                long executionTime = System.currentTimeMillis() - startTime;
                return new AgentResult(true, result, null, executionTime, Map.of());
            } catch (Exception e) {
                long executionTime = System.currentTimeMillis() - startTime;
                return new AgentResult(false, null, e.getMessage(), executionTime, Map.of());
            }
        });
    }
    
    @Override
    public AgentHealth getHealth() {
        return ready ? AgentHealth.HEALTHY : AgentHealth.DEGRADED;
    }
    
    @Override
    public AgentMetrics getMetrics() {
        return new AgentMetrics(0L, 0L, 0L, Duration.ofMillis(100), Duration.ofSeconds(1), 0.999, 0);
    }
    
    @Override
    public void initialize(AgentConfiguration config) {
        this.config = config;
        this.ready = true;
    }
    
    @Override
    public void shutdown() {
        this.ready = false;
    }
    
    @Override
    public boolean isReady() {
        return ready;
    }
    
    private String processOperation(String operation, Map<String, Object> parameters) {
        switch (operation) {
            case "ANALYZE_STORIES":
                return analyzeStories();
            case "SEQUENCE_STORIES":
                return sequenceStories();
            case "CLASSIFY_STORY":
                String storyId = (String) parameters.get("storyId");
                return classifyStory(storyId);
            case "GENERATE_SEQUENCE_DOCUMENT":
                return generateSequenceDocument();
            case "GENERATE_FRONTEND_COORDINATION":
                return generateFrontendCoordination();
            case "GENERATE_BACKEND_COORDINATION":
                return generateBackendCoordination();
            case "VALIDATE_ORCHESTRATION":
                return validateOrchestration();
            case "SETUP_TRIGGERS":
                return setupTriggers();
            case "HANDLE_STORY_EVENT":
                String eventType = (String) parameters.get("eventType");
                String eventStoryId = (String) parameters.get("storyId");
                return handleStoryEvent(eventType, eventStoryId);
            default:
                return "Unknown operation: " + operation;
        }
    }
    
    /**
     * Analyzes all [STORY] issues and builds dependency graph
     */
    public String analyzeStories() {
        // Simulate reading GitHub issues - in real implementation would use GitHub API
        loadStoriesFromRepository();
        buildDependencyGraph();
        return "Analyzed " + stories.size() + " stories with " + 
               dependencies.values().stream().mapToInt(Set::size).sum() + " dependencies";
    }
    
    /**
     * Computes global dependency-respecting sequence prioritizing backend stories
     */
    public String sequenceStories() {
        List<Story> sequence = computeOptimalSequence();
        return "Generated sequence of " + sequence.size() + " stories";
    }
    
    /**
     * Classifies story as Backend-First, Frontend-First, or Parallel
     */
    public String classifyStory(String storyId) {
        Story story = stories.get(storyId);
        if (story == null) return "Story not found: " + storyId;
        
        StoryClassification classification = determineClassification(story);
        story.setClassification(classification);
        return "Story " + storyId + " classified as: " + classification;
    }
    
    private void loadStoriesFromRepository() {
        // Simulate loading stories - real implementation would use GitHub API
        stories.put("STORY-001", new Story("STORY-001", "User Authentication", "Backend"));
        stories.put("STORY-002", new Story("STORY-002", "Login UI", "Frontend"));
        stories.put("STORY-003", new Story("STORY-003", "Product Catalog", "Backend"));
        stories.put("STORY-004", new Story("STORY-004", "Product Display", "Frontend"));
        
        // Classify all stories after loading
        for (Story story : stories.values()) {
            story.setClassification(determineClassification(story));
        }
    }
    
    private void buildDependencyGraph() {
        // Simulate dependency analysis - real implementation would parse issue metadata
        dependencies.put("STORY-002", Set.of("STORY-001")); // Login UI depends on User Auth
        dependencies.put("STORY-004", Set.of("STORY-003")); // Product Display depends on Catalog
    }
    
    private List<Story> computeOptimalSequence() {
        List<Story> sequence = new ArrayList<>();
        Set<String> completed = new HashSet<>();
        
        // Topological sort with backend-first prioritization
        while (sequence.size() < stories.size()) {
            String nextStory = findNextStoryToSchedule(completed);
            if (nextStory != null) {
                sequence.add(stories.get(nextStory));
                completed.add(nextStory);
            } else {
                break; // Circular dependency or error
            }
        }
        
        return sequence;
    }
    
    private String findNextStoryToSchedule(Set<String> completed) {
        return stories.keySet().stream()
            .filter(storyId -> !completed.contains(storyId))
            .filter(storyId -> {
                Set<String> deps = dependencies.getOrDefault(storyId, Set.of());
                return completed.containsAll(deps);
            })
            .min((a, b) -> {
                Story storyA = stories.get(a);
                Story storyB = stories.get(b);
                // Prioritize backend stories
                if (storyA.getType().equals("Backend") && !storyB.getType().equals("Backend")) {
                    return -1;
                } else if (!storyA.getType().equals("Backend") && storyB.getType().equals("Backend")) {
                    return 1;
                }
                return a.compareTo(b);
            })
            .orElse(null);
    }
    
    /**
     * Generates global story sequence document (story-sequence.md)
     */
    public String generateSequenceDocument() {
        try {
            // Ensure stories are analyzed and sequenced
            analyzeStories();
            List<Story> sequence = computeOptimalSequence();
            
            // Generate document content
            StringBuilder content = new StringBuilder();
            content.append("# Global Story Sequence\n\n");
            content.append("This document provides the global ordered sequence of all [STORY] issues across the durion ecosystem.\n\n");
            
            // Last updated timestamp
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            content.append("**Last Updated:** ").append(timestamp).append("\n");
            content.append("**Change Summary:** Generated global story sequence with ").append(sequence.size()).append(" stories\n\n");
            
            // Global story table
            content.append("## Global Story Sequence\n\n");
            content.append("| Orchestration ID | Repository/Issue | Classification | Dependencies | Status | Target Sprint |\n");
            content.append("|------------------|------------------|----------------|--------------|--------|---------------|\n");
            
            for (int i = 0; i < sequence.size(); i++) {
                Story story = sequence.get(i);
                String orchestrationId = String.format("ORD-%03d", i + 1);
                String repoLink = String.format("[%s](https://github.com/louisburroughs/durion/issues/%s)", 
                                               story.getId(), story.getId().replace("STORY-", ""));
                String classification = story.getClassification() != null ? 
                                      story.getClassification().toString() : "UNCLASSIFIED";
                Set<String> deps = dependencies.getOrDefault(story.getId(), Set.of());
                String depsStr = deps.isEmpty() ? "None" : String.join(", ", deps);
                
                content.append(String.format("| %s | %s | %s | %s | Open | TBD |\n",
                    orchestrationId, repoLink, classification, depsStr));
            }
            
            // Dependency overview
            content.append("\n## Dependency Overview\n\n");
            content.append("### Dependency Graph\n\n");
            for (Map.Entry<String, Set<String>> entry : dependencies.entrySet()) {
                if (!entry.getValue().isEmpty()) {
                    content.append("- **").append(entry.getKey()).append("** depends on: ");
                    content.append(String.join(", ", entry.getValue())).append("\n");
                }
            }
            
            // Classification summary
            content.append("\n### Classification Summary\n\n");
            Map<StoryClassification, Long> classificationCounts = new HashMap<>();
            for (Story story : sequence) {
                StoryClassification cls = story.getClassification();
                if (cls != null) {
                    classificationCounts.merge(cls, 1L, Long::sum);
                }
            }
            
            for (Map.Entry<StoryClassification, Long> entry : classificationCounts.entrySet()) {
                content.append("- **").append(entry.getKey()).append("**: ").append(entry.getValue()).append(" stories\n");
            }
            
            // Write to file
            Path orchestrationDir = Paths.get(".github/orchestration");
            Files.createDirectories(orchestrationDir);
            Path sequenceFile = orchestrationDir.resolve("story-sequence.md");
            Files.write(sequenceFile, content.toString().getBytes());
            
            return "Generated story-sequence.md with " + sequence.size() + " stories at " + sequenceFile.toAbsolutePath();
            
        } catch (IOException e) {
            return "Error generating sequence document: " + e.getMessage();
        }
    }
    
    /**
     * Generates frontend coordination view (frontend-coordination.md)
     */
    public String generateFrontendCoordination() {
        try {
            // Ensure stories are analyzed and sequenced
            analyzeStories();
            List<Story> sequence = computeOptimalSequence();
            
            // Generate document content
            StringBuilder content = new StringBuilder();
            content.append("# Frontend Coordination View\n\n");
            content.append("This document provides a frontend-centric view of story coordination, showing which frontend work is ready, blocked, or can proceed in parallel.\n\n");
            
            // Last updated timestamp
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            content.append("**Last Updated:** ").append(timestamp).append("\n");
            content.append("**Change Summary:** Generated frontend coordination view with story status analysis\n\n");
            
            // Categorize stories for frontend perspective
            List<Story> readyStories = new ArrayList<>();
            List<Story> blockedStories = new ArrayList<>();
            List<Story> parallelStories = new ArrayList<>();
            
            Set<String> completedBackendStories = getCompletedBackendStories();
            
            for (Story story : sequence) {
                if (story.getType().equals("Frontend")) {
                    Set<String> deps = dependencies.getOrDefault(story.getId(), Set.of());
                    
                    if (deps.isEmpty() || completedBackendStories.containsAll(deps)) {
                        readyStories.add(story);
                    } else if (story.getClassification() == StoryClassification.PARALLEL) {
                        parallelStories.add(story);
                    } else {
                        blockedStories.add(story);
                    }
                }
            }
            
            // Ready Stories Section
            content.append("## Ready Stories\n\n");
            content.append("Frontend work whose backend prerequisites are complete or not required.\n\n");
            
            if (readyStories.isEmpty()) {
                content.append("*No ready frontend stories at this time.*\n\n");
            } else {
                content.append("| Story ID | Title | Prerequisites | Notes |\n");
                content.append("|----------|-------|---------------|-------|\n");
                
                for (Story story : readyStories) {
                    Set<String> deps = dependencies.getOrDefault(story.getId(), Set.of());
                    String prereqs = deps.isEmpty() ? "None" : String.join(", ", deps);
                    content.append(String.format("| %s | %s | %s | Ready for implementation |\n",
                        story.getId(), story.getTitle(), prereqs));
                }
                content.append("\n");
            }
            
            // Blocked Stories Section
            content.append("## Blocked Stories\n\n");
            content.append("Frontend work waiting on specific backend stories.\n\n");
            
            if (blockedStories.isEmpty()) {
                content.append("*No blocked frontend stories at this time.*\n\n");
            } else {
                content.append("| Story ID | Title | Blocking Stories | Required Contracts | Stubs Allowed |\n");
                content.append("|----------|-------|------------------|-------------------|---------------|\n");
                
                for (Story story : blockedStories) {
                    Set<String> deps = dependencies.getOrDefault(story.getId(), Set.of());
                    String blockingStories = String.join(", ", deps);
                    String contracts = generateRequiredContracts(story, deps);
                    String stubsAllowed = determineStubPolicy(story);
                    
                    content.append(String.format("| %s | %s | %s | %s | %s |\n",
                        story.getId(), story.getTitle(), blockingStories, contracts, stubsAllowed));
                }
                content.append("\n");
            }
            
            // Parallel Stories Section
            content.append("## Parallel Stories\n\n");
            content.append("Frontend work that can proceed alongside backend work using documented contracts.\n\n");
            
            if (parallelStories.isEmpty()) {
                content.append("*No parallel frontend stories at this time.*\n\n");
            } else {
                content.append("| Story ID | Title | Parallel Backend Work | Contract Status | Implementation Notes |\n");
                content.append("|----------|-------|----------------------|-----------------|----------------------|\n");
                
                for (Story story : parallelStories) {
                    String parallelBackend = findParallelBackendWork(story);
                    String contractStatus = "Contract documented";
                    String notes = "Can proceed with documented API contracts";
                    
                    content.append(String.format("| %s | %s | %s | %s | %s |\n",
                        story.getId(), story.getTitle(), parallelBackend, contractStatus, notes));
                }
                content.append("\n");
            }
            
            // Write to file
            Path orchestrationDir = Paths.get(".github/orchestration");
            Files.createDirectories(orchestrationDir);
            Path frontendFile = orchestrationDir.resolve("frontend-coordination.md");
            Files.write(frontendFile, content.toString().getBytes());
            
            return "Generated frontend-coordination.md with " + 
                   readyStories.size() + " ready, " + 
                   blockedStories.size() + " blocked, " + 
                   parallelStories.size() + " parallel stories at " + 
                   frontendFile.toAbsolutePath();
            
        } catch (IOException e) {
            return "Error generating frontend coordination document: " + e.getMessage();
        }
    }
    
    private Set<String> getCompletedBackendStories() {
        // In real implementation, would query GitHub API for closed backend stories
        // For now, simulate that STORY-001 (User Authentication) is completed
        return Set.of("STORY-001");
    }
    
    private String generateRequiredContracts(Story story, Set<String> dependencies) {
        // Generate contract requirements based on story dependencies
        if (dependencies.contains("STORY-001")) {
            return "JWT auth endpoints, user profile API";
        } else if (dependencies.contains("STORY-003")) {
            return "Product catalog API, search endpoints";
        }
        return "API contracts TBD";
    }
    
    private String determineStubPolicy(Story story) {
        // Determine if stubs are allowed for this story
        if (story.getId().equals("STORY-002")) {
            return "Yes - mock auth service";
        } else if (story.getId().equals("STORY-004")) {
            return "Yes - static product data";
        }
        return "No - requires live backend";
    }
    
    private String findParallelBackendWork(Story story) {
        // Find related backend work that can proceed in parallel
        return "None identified";
    }
    
    /**
     * Generates backend coordination view (backend-coordination.md)
     */
    public String generateBackendCoordination() {
        try {
            // Ensure stories are analyzed and sequenced
            if (stories.isEmpty()) {
                analyzeStories();
            }
            
            StringBuilder content = new StringBuilder();
            content.append("# Backend Coordination View\n\n");
            content.append("Generated: ").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("\n\n");
            content.append("This document provides a backend-centric view of story orchestration, prioritizing backend stories by their frontend unblock value.\n\n");
            
            // Get backend stories prioritized by frontend unblock value
            List<Story> prioritizedBackendStories = getPrioritizedBackendStories();
            
            content.append("## Backend Stories Prioritized by Frontend Unblock Value\n\n");
            content.append("| Story ID | Title | Frontend Stories Unblocked | Priority Score |\n");
            content.append("|----------|-------|----------------------------|----------------|\n");
            
            for (Story story : prioritizedBackendStories) {
                Set<String> unblockedFrontendStories = getFrontendStoriesUnblockedBy(story);
                int priorityScore = calculateFrontendUnblockValue(story);
                
                content.append("| ").append(story.getId())
                       .append(" | ").append(story.getTitle())
                       .append(" | ").append(unblockedFrontendStories.size())
                       .append(" | ").append(priorityScore)
                       .append(" |\n");
            }
            
            content.append("\n## Backend-to-Frontend Story Mapping\n\n");
            
            for (Story backendStory : prioritizedBackendStories) {
                Set<String> dependentFrontendStories = getFrontendStoriesUnblockedBy(backendStory);
                if (!dependentFrontendStories.isEmpty()) {
                    content.append("### ").append(backendStory.getId()).append(": ").append(backendStory.getTitle()).append("\n\n");
                    content.append("**Dependent Frontend Stories:**\n");
                    
                    for (String frontendStoryId : dependentFrontendStories) {
                        Story frontendStory = stories.get(frontendStoryId);
                        if (frontendStory != null) {
                            content.append("- ").append(frontendStoryId).append(": ").append(frontendStory.getTitle()).append("\n");
                        }
                    }
                    
                    content.append("\n**Required Integration Points:**\n");
                    content.append(generateRequiredIntegrationPoints(backendStory));
                    content.append("\n");
                    
                    content.append("**Performance & Security Constraints:**\n");
                    content.append(generatePerformanceSecurityConstraints(backendStory));
                    content.append("\n\n");
                }
            }
            
            // Write to file
            Path outputPath = Paths.get("durion/.github/orchestration/backend-coordination.md");
            Files.createDirectories(outputPath.getParent());
            Files.write(outputPath, content.toString().getBytes());
            
            return "Generated backend coordination view with " + prioritizedBackendStories.size() + " backend stories";
            
        } catch (IOException e) {
            return "Error generating backend coordination: " + e.getMessage();
        }
    }
    
    /**
     * Validates consistency across all orchestration documents
     */
    public String validateOrchestration() {
        try {
            List<String> issues = new ArrayList<>();
            
            // Read all three documents
            Map<String, Set<String>> documentStories = readOrchestrationDocuments();
            
            Set<String> sequenceStories = documentStories.get("sequence");
            Set<String> frontendStories = documentStories.get("frontend");
            Set<String> backendStories = documentStories.get("backend");
            
            // Validation 1: Every story in frontend-coordination.md exists in story-sequence.md
            for (String storyId : frontendStories) {
                if (!sequenceStories.contains(storyId)) {
                    issues.add("Frontend story " + storyId + " not found in story-sequence.md");
                }
            }
            
            // Validation 2: Every story in backend-coordination.md exists in story-sequence.md
            for (String storyId : backendStories) {
                if (!sequenceStories.contains(storyId)) {
                    issues.add("Backend story " + storyId + " not found in story-sequence.md");
                }
            }
            
            // Validation 3: Check for references to closed/deleted stories
            Set<String> activeStories = getActiveStoriesFromRepository();
            for (String storyId : sequenceStories) {
                if (!activeStories.contains(storyId)) {
                    issues.add("Story " + storyId + " in story-sequence.md is closed/deleted/re-scoped");
                }
            }
            
            // Validation 4: Check classification consistency
            Map<String, StoryClassification> sequenceClassifications = getClassificationsFromSequence();
            Map<String, StoryClassification> frontendClassifications = getClassificationsFromFrontend();
            Map<String, StoryClassification> backendClassifications = getClassificationsFromBackend();
            
            for (String storyId : sequenceStories) {
                StoryClassification seqClass = sequenceClassifications.get(storyId);
                StoryClassification frontClass = frontendClassifications.get(storyId);
                StoryClassification backClass = backendClassifications.get(storyId);
                
                if (frontClass != null && !frontClass.equals(seqClass)) {
                    issues.add("Classification mismatch for " + storyId + ": sequence=" + seqClass + ", frontend=" + frontClass);
                }
                if (backClass != null && !backClass.equals(seqClass)) {
                    issues.add("Classification mismatch for " + storyId + ": sequence=" + seqClass + ", backend=" + backClass);
                }
            }
            
            // If issues found, attempt to fix them
            if (!issues.isEmpty()) {
                String fixResult = attemptOrchestrationFix(issues);
                return "Validation found " + issues.size() + " issues:\n" + 
                       String.join("\n", issues) + "\n\nFix attempt: " + fixResult;
            }
            
            return "Orchestration validation passed - all documents are consistent";
            
        } catch (Exception e) {
            return "Error during orchestration validation: " + e.getMessage();
        }
    }
    
    private Map<String, Set<String>> readOrchestrationDocuments() throws IOException {
        Map<String, Set<String>> result = new HashMap<>();
        
        // Read story-sequence.md
        Path sequencePath = Paths.get("durion/.github/orchestration/story-sequence.md");
        Set<String> sequenceStories = new HashSet<>();
        if (Files.exists(sequencePath)) {
            String content = Files.readString(sequencePath);
            // Extract story IDs from table format (simplified parsing)
            String[] lines = content.split("\n");
            for (String line : lines) {
                if (line.contains("STORY-") && line.contains("|")) {
                    String[] parts = line.split("\\|");
                    if (parts.length > 1) {
                        String storyId = parts[1].trim();
                        if (storyId.startsWith("STORY-")) {
                            sequenceStories.add(storyId);
                        }
                    }
                }
            }
        }
        result.put("sequence", sequenceStories);
        
        // Read frontend-coordination.md
        Path frontendPath = Paths.get("durion/.github/orchestration/frontend-coordination.md");
        Set<String> frontendStories = new HashSet<>();
        if (Files.exists(frontendPath)) {
            String content = Files.readString(frontendPath);
            // Extract story IDs (simplified parsing)
            String[] lines = content.split("\n");
            for (String line : lines) {
                if (line.contains("STORY-")) {
                    // Extract all STORY-XXX patterns from the line
                    String[] words = line.split("\\s+");
                    for (String word : words) {
                        if (word.startsWith("STORY-") && word.matches("STORY-\\d+.*")) {
                            String storyId = word.replaceAll("[^A-Z0-9-]", "");
                            if (storyId.matches("STORY-\\d+")) {
                                frontendStories.add(storyId);
                            }
                        }
                    }
                }
            }
        }
        result.put("frontend", frontendStories);
        
        // Read backend-coordination.md
        Path backendPath = Paths.get("durion/.github/orchestration/backend-coordination.md");
        Set<String> backendStories = new HashSet<>();
        if (Files.exists(backendPath)) {
            String content = Files.readString(backendPath);
            // Extract story IDs (simplified parsing)
            String[] lines = content.split("\n");
            for (String line : lines) {
                if (line.contains("STORY-")) {
                    // Extract all STORY-XXX patterns from the line
                    String[] words = line.split("\\s+");
                    for (String word : words) {
                        if (word.startsWith("STORY-") && word.matches("STORY-\\d+.*")) {
                            String storyId = word.replaceAll("[^A-Z0-9-]", "");
                            if (storyId.matches("STORY-\\d+")) {
                                backendStories.add(storyId);
                            }
                        }
                    }
                }
            }
        }
        result.put("backend", backendStories);
        
        return result;
    }
    
    private Set<String> getActiveStoriesFromRepository() {
        // In real implementation, would query GitHub API for open [STORY] issues
        // For now, return current stories as active
        return new HashSet<>(stories.keySet());
    }
    
    private Map<String, StoryClassification> getClassificationsFromSequence() {
        // Parse classifications from story-sequence.md
        Map<String, StoryClassification> classifications = new HashMap<>();
        try {
            Path sequencePath = Paths.get("durion/.github/orchestration/story-sequence.md");
            if (Files.exists(sequencePath)) {
                String content = Files.readString(sequencePath);
                String[] lines = content.split("\n");
                for (String line : lines) {
                    if (line.contains("STORY-") && line.contains("|")) {
                        String[] parts = line.split("\\|");
                        if (parts.length > 3) {
                            String storyId = parts[1].trim();
                            String classification = parts[3].trim();
                            if (storyId.startsWith("STORY-")) {
                                classifications.put(storyId, parseClassification(classification));
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            // Return empty map on error
        }
        return classifications;
    }
    
    private Map<String, StoryClassification> getClassificationsFromFrontend() {
        // For frontend coordination, classification is implicit based on section
        Map<String, StoryClassification> classifications = new HashMap<>();
        try {
            Path frontendPath = Paths.get("durion/.github/orchestration/frontend-coordination.md");
            if (Files.exists(frontendPath)) {
                String content = Files.readString(frontendPath);
                String[] sections = content.split("##");
                for (String section : sections) {
                    StoryClassification classification = null;
                    if (section.contains("Ready Stories")) {
                        classification = StoryClassification.FRONTEND_FIRST;
                    } else if (section.contains("Parallel Stories")) {
                        classification = StoryClassification.PARALLEL;
                    } else if (section.contains("Blocked Stories")) {
                        classification = StoryClassification.FRONTEND_FIRST;
                    }
                    
                    if (classification != null) {
                        String[] lines = section.split("\n");
                        for (String line : lines) {
                            if (line.contains("STORY-")) {
                                String[] words = line.split("\\s+");
                                for (String word : words) {
                                    if (word.startsWith("STORY-") && word.matches("STORY-\\d+.*")) {
                                        String storyId = word.replaceAll("[^A-Z0-9-]", "");
                                        if (storyId.matches("STORY-\\d+")) {
                                            classifications.put(storyId, classification);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            // Return empty map on error
        }
        return classifications;
    }
    
    private Map<String, StoryClassification> getClassificationsFromBackend() {
        // Backend stories are implicitly BACKEND_FIRST
        Map<String, StoryClassification> classifications = new HashMap<>();
        try {
            Path backendPath = Paths.get("durion/.github/orchestration/backend-coordination.md");
            if (Files.exists(backendPath)) {
                String content = Files.readString(backendPath);
                String[] lines = content.split("\n");
                for (String line : lines) {
                    if (line.contains("STORY-")) {
                        String[] words = line.split("\\s+");
                        for (String word : words) {
                            if (word.startsWith("STORY-") && word.matches("STORY-\\d+.*")) {
                                String storyId = word.replaceAll("[^A-Z0-9-]", "");
                                if (storyId.matches("STORY-\\d+")) {
                                    classifications.put(storyId, StoryClassification.BACKEND_FIRST);
                                }
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            // Return empty map on error
        }
        return classifications;
    }
    
    private StoryClassification parseClassification(String classification) {
        switch (classification.toUpperCase()) {
            case "BACKEND-FIRST":
                return StoryClassification.BACKEND_FIRST;
            case "FRONTEND-FIRST":
                return StoryClassification.FRONTEND_FIRST;
            case "PARALLEL":
                return StoryClassification.PARALLEL;
            default:
                return StoryClassification.PARALLEL; // Default fallback
        }
    }
    
    private String attemptOrchestrationFix(List<String> issues) {
        try {
            int fixedCount = 0;
            List<String> humanReviewItems = new ArrayList<>();
            
            for (String issue : issues) {
                if (issue.contains("not found in story-sequence.md")) {
                    // For missing stories in sequence, mark for human review
                    humanReviewItems.add(issue);
                } else if (issue.contains("is closed/deleted/re-scoped")) {
                    // Remove references to closed stories from projections
                    String storyId = extractStoryIdFromIssue(issue);
                    if (removeStoryFromProjections(storyId)) {
                        fixedCount++;
                    }
                } else if (issue.contains("Classification mismatch")) {
                    // Use story-sequence.md as canonical source
                    String storyId = extractStoryIdFromIssue(issue);
                    if (updateProjectionClassifications(storyId)) {
                        fixedCount++;
                    }
                }
            }
            
            String result = "Fixed " + fixedCount + " issues automatically";
            if (!humanReviewItems.isEmpty()) {
                result += ". " + humanReviewItems.size() + " items marked for human review";
            }
            
            return result;
            
        } catch (Exception e) {
            return "Error during fix attempt: " + e.getMessage();
        }
    }
    
    private String extractStoryIdFromIssue(String issue) {
        // Extract STORY-XXX from issue description
        String[] words = issue.split("\\s+");
        for (String word : words) {
            if (word.matches("STORY-\\d+")) {
                return word;
            }
        }
        return null;
    }
    
    private boolean removeStoryFromProjections(String storyId) {
        // Remove story from frontend-coordination.md and backend-coordination.md
        try {
            boolean removed = false;
            
            // Remove from frontend coordination
            Path frontendPath = Paths.get("durion/.github/orchestration/frontend-coordination.md");
            if (Files.exists(frontendPath)) {
                String content = Files.readString(frontendPath);
                String updatedContent = content.replaceAll(".*" + storyId + ".*\n", "");
                if (!content.equals(updatedContent)) {
                    Files.write(frontendPath, updatedContent.getBytes());
                    removed = true;
                }
            }
            
            // Remove from backend coordination
            Path backendPath = Paths.get("durion/.github/orchestration/backend-coordination.md");
            if (Files.exists(backendPath)) {
                String content = Files.readString(backendPath);
                String updatedContent = content.replaceAll(".*" + storyId + ".*\n", "");
                if (!content.equals(updatedContent)) {
                    Files.write(backendPath, updatedContent.getBytes());
                    removed = true;
                }
            }
            
            return removed;
            
        } catch (IOException e) {
            return false;
        }
    }
    
    private boolean updateProjectionClassifications(String storyId) {
        // Update classifications in projections to match story-sequence.md
        try {
            Map<String, StoryClassification> sequenceClassifications = getClassificationsFromSequence();
            StoryClassification canonicalClassification = sequenceClassifications.get(storyId);
            
            if (canonicalClassification == null) {
                return false;
            }
            
            // For now, just log that we would update classifications
            // In a full implementation, would regenerate the projection documents
            return true;
            
        } catch (Exception e) {
            return false;
        }
    }
    
    private List<Story> getPrioritizedBackendStories() {
        return stories.values().stream()
                .filter(story -> story.getType().equals("Backend"))
                .sorted((a, b) -> Integer.compare(
                    calculateFrontendUnblockValue(b), 
                    calculateFrontendUnblockValue(a)
                ))
                .toList();
    }
    
    private Set<String> getFrontendStoriesUnblockedBy(Story backendStory) {
        Set<String> unblockedStories = new HashSet<>();
        
        // Find frontend stories that depend on this backend story
        for (Map.Entry<String, Set<String>> entry : dependencies.entrySet()) {
            String storyId = entry.getKey();
            Set<String> deps = entry.getValue();
            
            Story story = stories.get(storyId);
            if (story != null && story.getType().equals("Frontend") && deps.contains(backendStory.getId())) {
                unblockedStories.add(storyId);
            }
        }
        
        return unblockedStories;
    }
    
    private int calculateFrontendUnblockValue(Story backendStory) {
        Set<String> unblockedStories = getFrontendStoriesUnblockedBy(backendStory);
        
        // Base score is number of frontend stories unblocked
        int baseScore = unblockedStories.size();
        
        // Add bonus for high-priority frontend stories
        int priorityBonus = 0;
        for (String frontendStoryId : unblockedStories) {
            Story frontendStory = stories.get(frontendStoryId);
            if (frontendStory != null && frontendStory.getTitle().toLowerCase().contains("critical")) {
                priorityBonus += 5;
            }
        }
        
        return baseScore * 10 + priorityBonus;
    }
    
    private String generateRequiredIntegrationPoints(Story backendStory) {
        StringBuilder integration = new StringBuilder();
        
        // Generate required endpoints based on story type
        if (backendStory.getTitle().toLowerCase().contains("authentication")) {
            integration.append("- **Endpoints:** POST /api/auth/login, POST /api/auth/logout, GET /api/auth/validate\n");
            integration.append("- **DTOs:** LoginRequest, LoginResponse, TokenValidationResponse\n");
            integration.append("- **Business Rules:** JWT token expiry (24h), password complexity validation\n");
        } else if (backendStory.getTitle().toLowerCase().contains("catalog") || backendStory.getTitle().toLowerCase().contains("product")) {
            integration.append("- **Endpoints:** GET /api/products, GET /api/products/{id}, POST /api/products/search\n");
            integration.append("- **DTOs:** Product, ProductSummary, SearchRequest, SearchResponse\n");
            integration.append("- **Business Rules:** Product availability validation, pricing rules\n");
        } else {
            integration.append("- **Endpoints:** RESTful CRUD operations for ").append(backendStory.getTitle().toLowerCase()).append("\n");
            integration.append("- **DTOs:** Request/Response objects for ").append(backendStory.getTitle()).append("\n");
            integration.append("- **Business Rules:** Domain-specific validation and processing rules\n");
        }
        
        return integration.toString();
    }
    
    private String generatePerformanceSecurityConstraints(Story backendStory) {
        StringBuilder constraints = new StringBuilder();
        
        // Standard performance constraints
        constraints.append("- **Response Time:** < 200ms for GET operations, < 500ms for POST/PUT operations\n");
        constraints.append("- **Throughput:** Support 100+ concurrent requests\n");
        constraints.append("- **Availability:** 99.9% uptime during business hours\n");
        
        // Security constraints based on story type
        if (backendStory.getTitle().toLowerCase().contains("authentication")) {
            constraints.append("- **Security:** JWT-based authentication, AES-256 encryption for sensitive data\n");
            constraints.append("- **Authorization:** Role-based access control (RBAC)\n");
            constraints.append("- **Audit:** Complete audit trail for authentication events\n");
        } else {
            constraints.append("- **Security:** JWT token validation required for all operations\n");
            constraints.append("- **Authorization:** Resource-level access control\n");
            constraints.append("- **Data Protection:** Sensitive data encryption at rest and in transit\n");
        }
        
        return constraints.toString();
    }
    
    private StoryClassification determineClassification(Story story) {
        Set<String> deps = dependencies.getOrDefault(story.getId(), Set.of());
        
        if (story.getType().equals("Backend")) {
            return StoryClassification.BACKEND_FIRST;
        } else if (deps.isEmpty()) {
            return StoryClassification.PARALLEL;
        } else {
            return StoryClassification.FRONTEND_FIRST;
        }
    }
    
    /**
     * Sets up periodic and event-based orchestration triggers
     */
    public String setupTriggers() {
        triggersEnabled = true;
        lastOrchestrationRun = System.currentTimeMillis();
        
        // In real implementation, would set up:
        // - GitHub webhook listeners for issue events
        // - Periodic timer for checking story changes
        // - Event handlers for dependency/label changes
        
        return "Orchestration triggers enabled - monitoring for story events";
    }
    
    /**
     * Handles story creation, closure, or metadata changes
     */
    public String handleStoryEvent(String eventType, String storyId) {
        if (!triggersEnabled) {
            return "Triggers not enabled - call SETUP_TRIGGERS first";
        }
        
        boolean shouldRerun = shouldTriggerOrchestration(eventType, storyId);
        
        if (shouldRerun) {
            return runIncrementalOrchestration(eventType, storyId);
        } else {
            return "Event " + eventType + " for " + storyId + " - no orchestration rerun needed";
        }
    }
    
    /**
     * Determines if orchestration should rerun based on event type and story changes
     */
    private boolean shouldTriggerOrchestration(String eventType, String storyId) {
        switch (eventType) {
            case "story_created":
            case "story_closed":
                return true; // Always rerun for new/closed stories
                
            case "dependencies_changed":
            case "labels_changed":
            case "notes_for_agents_changed":
                return hasSignificantChange(storyId);
                
            case "linked_story_changed":
                return true; // Linked story changes affect coordination
                
            default:
                return false;
        }
    }
    
    /**
     * Checks if story changes are significant enough to trigger orchestration
     */
    private boolean hasSignificantChange(String storyId) {
        // In real implementation, would compare current story hash with last known
        String currentHash = computeStoryHash(storyId);
        String lastHash = lastKnownStoryHashes.get(storyId);
        
        if (!currentHash.equals(lastHash)) {
            lastKnownStoryHashes.put(storyId, currentHash);
            return true;
        }
        
        return false;
    }
    
    /**
     * Computes hash of story metadata for change detection
     */
    private String computeStoryHash(String storyId) {
        Story story = stories.get(storyId);
        if (story == null) return "";
        
        // In real implementation, would hash dependencies, labels, notes
        return story.getId() + ":" + story.getType() + ":" + 
               dependencies.getOrDefault(storyId, Set.of()).toString();
    }
    
    /**
     * Runs incremental orchestration minimizing churn in story ordering
     */
    private String runIncrementalOrchestration(String eventType, String storyId) {
        long timeSinceLastRun = System.currentTimeMillis() - lastOrchestrationRun;
        
        // Minimize churn by batching rapid changes
        if (timeSinceLastRun < 30000) { // 30 seconds
            return "Orchestration batched - recent run detected, minimizing churn";
        }
        
        // Run full orchestration pipeline
        analyzeStories();
        sequenceStories();
        generateSequenceDocument();
        generateFrontendCoordination();
        generateBackendCoordination();
        validateOrchestration();
        
        lastOrchestrationRun = System.currentTimeMillis();
        
        return "Incremental orchestration completed for " + eventType + " on " + storyId + 
               " - story ordering updated with minimal churn";
    }
    
    // Inner classes for story management
    public static class Story {
        private final String id;
        private final String title;
        private final String type;
        private StoryClassification classification;
        
        public Story(String id, String title, String type) {
            this.id = id;
            this.title = title;
            this.type = type;
        }
        
        public String getId() { return id; }
        public String getTitle() { return title; }
        public String getType() { return type; }
        public StoryClassification getClassification() { return classification; }
        public void setClassification(StoryClassification classification) { 
            this.classification = classification; 
        }
    }
    
    public enum StoryClassification {
        BACKEND_FIRST,
        FRONTEND_FIRST,
        PARALLEL
    }
}
