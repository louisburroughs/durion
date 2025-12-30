package com.durion.audit;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Comprehensive audit logging and tracking system for the missing issues audit.
 * 
 * Provides detailed logging for all API requests, responses, errors, and audit operations
 * with context for troubleshooting and audit history tracking.
 * 
 * Requirements: 6.1, 6.2 - Comprehensive logging for audit operations and decisions
 */
public class AuditLogger {
    
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final String auditSessionId;
    private int requestCounter = 0;
    private int errorCounter = 0;
    private LocalDateTime sessionStartTime;
    
    public AuditLogger() {
        this.auditSessionId = generateSessionId();
        this.sessionStartTime = LocalDateTime.now();
        logSessionStart();
    }
    
    /**
     * Generates a unique session ID for this audit run.
     */
    private String generateSessionId() {
        return "AUDIT-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
    }
    
    /**
     * Logs the start of an audit session.
     */
    private void logSessionStart() {
        System.out.println("🚀 ===== MISSING ISSUES AUDIT SESSION STARTED =====");
        System.out.println("📅 Session ID: " + auditSessionId);
        System.out.println("⏰ Start Time: " + sessionStartTime.format(TIMESTAMP_FORMAT));
        System.out.println("🔍 Audit Engine: Enhanced Missing Issues Detection");
        System.out.println("================================================");
    }
    
    /**
     * Logs the start of audit configuration validation.
     */
    public void logConfigurationValidation(AuditConfiguration configuration) {
        System.out.println("\n🔧 CONFIGURATION VALIDATION");
        System.out.println("├─ Audit Mode: " + configuration.getAuditMode());
        System.out.println("├─ Output Directory: " + configuration.getOutputDirectory());
        System.out.println("├─ Use Cache: " + configuration.isUseCache());
        System.out.println("├─ Create Missing Issues: " + configuration.isCreateMissingIssues());
        System.out.println("├─ Rate Limit Delay: " + configuration.getRateLimitDelayMs() + "ms");
        System.out.println("├─ Batch Size: " + configuration.getBatchSize());
        
        if (configuration.getStartDate().isPresent() || configuration.getEndDate().isPresent()) {
            System.out.println("├─ Date Range: " + 
                configuration.getStartDate().orElse(null) + " to " + 
                configuration.getEndDate().orElse(null));
        }
        
        if (configuration.getStoryRangeStart().isPresent() || configuration.getStoryRangeEnd().isPresent()) {
            System.out.println("├─ Story Range: " + 
                configuration.getStoryRangeStart().orElse(null) + " to " + 
                configuration.getStoryRangeEnd().orElse(null));
        }
        
        System.out.println("└─ ✅ Configuration validated successfully");
    }
    
    /**
     * Logs the start of the audit process.
     */
    public void logAuditStart(List<Integer> processedIssues, Map<Integer, StoryMetadata> storyMetadata) {
        System.out.println("\n📊 AUDIT PROCESS INITIALIZATION");
        System.out.println("├─ Processed Issues Count: " + processedIssues.size());
        System.out.println("├─ Story Metadata Count: " + storyMetadata.size());
        System.out.println("├─ Issue Range: #" + 
            processedIssues.stream().min(Integer::compareTo).orElse(0) + " to #" + 
            processedIssues.stream().max(Integer::compareTo).orElse(0));
        System.out.println("└─ 🔍 Starting repository scanning...");
    }
    
    /**
     * Logs API request details.
     */
    public void logApiRequest(String method, String url, String repository) {
        requestCounter++;
        System.out.println("\n📤 API REQUEST #" + requestCounter);
        System.out.println("├─ Method: " + method);
        System.out.println("├─ Repository: " + repository);
        System.out.println("├─ URL: " + url);
        System.out.println("└─ Timestamp: " + LocalDateTime.now().format(TIMESTAMP_FORMAT));
    }
    
    /**
     * Logs API response details.
     */
    public void logApiResponse(int statusCode, long responseTimeMs, int resultCount) {
        System.out.println("📥 API RESPONSE #" + requestCounter);
        System.out.println("├─ Status Code: " + statusCode);
        System.out.println("├─ Response Time: " + responseTimeMs + "ms");
        System.out.println("├─ Result Count: " + resultCount);
        
        if (statusCode == 200) {
            System.out.println("└─ ✅ Request successful");
        } else if (statusCode == 403) {
            System.out.println("└─ ⚠️ Rate limit encountered");
        } else if (statusCode >= 400) {
            System.out.println("└─ ❌ Request failed");
        } else {
            System.out.println("└─ ℹ️ Unexpected status code");
        }
    }
    
    /**
     * Logs rate limiting information.
     */
    public void logRateLimit(RateLimitInfo rateLimitInfo) {
        if (rateLimitInfo != null) {
            System.out.println("\n⏱️ RATE LIMIT STATUS");
            System.out.println("├─ Remaining: " + rateLimitInfo.getRemaining() + "/" + rateLimitInfo.getLimit());
            System.out.println("├─ Used: " + rateLimitInfo.getUsed());
            System.out.println("├─ Reset Time: " + rateLimitInfo.getResetTime().format(TIMESTAMP_FORMAT));
            System.out.println("└─ Resource: " + rateLimitInfo.getResource());
            
            if (rateLimitInfo.getRemaining() < 10) {
                System.out.println("🚨 WARNING: Rate limit critically low!");
            } else if (rateLimitInfo.getRemaining() < 100) {
                System.out.println("⚠️ CAUTION: Rate limit getting low");
            }
        }
    }
    
    /**
     * Logs error details with context.
     */
    public void logError(String operation, Exception error, String context) {
        errorCounter++;
        System.out.println("\n❌ ERROR #" + errorCounter);
        System.out.println("├─ Operation: " + operation);
        System.out.println("├─ Error Type: " + error.getClass().getSimpleName());
        System.out.println("├─ Error Message: " + error.getMessage());
        System.out.println("├─ Context: " + context);
        System.out.println("├─ Timestamp: " + LocalDateTime.now().format(TIMESTAMP_FORMAT));
        System.out.println("└─ Session: " + auditSessionId);
        
        // Log stack trace for debugging if needed
        if (error.getCause() != null) {
            System.out.println("   Caused by: " + error.getCause().getClass().getSimpleName() + 
                             ": " + error.getCause().getMessage());
        }
    }
    
    /**
     * Logs audit progress updates.
     */
    public void logProgress(String phase, int completed, int total) {
        double percentage = total > 0 ? (completed * 100.0) / total : 0;
        System.out.println("\n📈 PROGRESS UPDATE");
        System.out.println("├─ Phase: " + phase);
        System.out.println("├─ Completed: " + completed + "/" + total);
        System.out.println("├─ Progress: " + String.format("%.1f%%", percentage));
        System.out.println("└─ Timestamp: " + LocalDateTime.now().format(TIMESTAMP_FORMAT));
    }
    
    /**
     * Logs missing issue detection results.
     */
    public void logMissingIssueDetection(List<MissingIssue> frontendMissing, List<MissingIssue> backendMissing) {
        System.out.println("\n🔍 MISSING ISSUE DETECTION RESULTS");
        System.out.println("├─ Frontend Missing: " + frontendMissing.size() + " issues");
        System.out.println("├─ Backend Missing: " + backendMissing.size() + " issues");
        System.out.println("├─ Total Missing: " + (frontendMissing.size() + backendMissing.size()) + " issues");
        
        if (!frontendMissing.isEmpty()) {
            System.out.println("├─ Frontend Missing Issues:");
            frontendMissing.stream().limit(5).forEach(issue -> 
                System.out.println("│  • #" + issue.getStoryNumber() + ": " + issue.getStoryTitle()));
            if (frontendMissing.size() > 5) {
                System.out.println("│  ... and " + (frontendMissing.size() - 5) + " more");
            }
        }
        
        if (!backendMissing.isEmpty()) {
            System.out.println("├─ Backend Missing Issues:");
            backendMissing.stream().limit(5).forEach(issue -> 
                System.out.println("│  • #" + issue.getStoryNumber() + ": " + issue.getStoryTitle()));
            if (backendMissing.size() > 5) {
                System.out.println("│  ... and " + (backendMissing.size() - 5) + " more");
            }
        }
        
        System.out.println("└─ ✅ Detection completed");
    }
    
    /**
     * Logs audit statistics and summary.
     */
    public void logAuditSummary(AuditResult auditResult) {
        LocalDateTime endTime = LocalDateTime.now();
        long durationMinutes = java.time.Duration.between(sessionStartTime, endTime).toMinutes();
        
        System.out.println("\n📊 AUDIT SUMMARY");
        System.out.println("├─ Session ID: " + auditSessionId);
        System.out.println("├─ Duration: " + durationMinutes + " minutes");
        System.out.println("├─ Total API Requests: " + requestCounter);
        System.out.println("├─ Total Errors: " + errorCounter);
        System.out.println("├─ Processed Stories: " + auditResult.getTotalProcessedStories());
        System.out.println("├─ Missing Frontend Issues: " + auditResult.getMissingFrontendIssues().size());
        System.out.println("├─ Missing Backend Issues: " + auditResult.getMissingBackendIssues().size());
        System.out.println("├─ Total Missing Issues: " + auditResult.getTotalMissingIssues());
        
        AuditStatistics stats = auditResult.getStatistics();
        if (stats != null) {
            System.out.println("├─ Frontend Issues Found: " + stats.getFrontendIssuesFound());
            System.out.println("├─ Backend Issues Found: " + stats.getBackendIssuesFound());
        }
        
        System.out.println("└─ Audit Timestamp: " + auditResult.getAuditTimestamp().format(TIMESTAMP_FORMAT));
    }
    
    /**
     * Logs issue creation attempts and results.
     */
    public void logIssueCreation(MissingIssue missingIssue, boolean success, String errorMessage) {
        System.out.println("\n🔨 ISSUE CREATION");
        System.out.println("├─ Story: #" + missingIssue.getStoryNumber());
        System.out.println("├─ Title: " + missingIssue.getExpectedTitle());
        System.out.println("├─ Repository: " + missingIssue.getTargetRepository());
        System.out.println("├─ Type: " + missingIssue.getRepositoryType());
        
        if (success) {
            System.out.println("└─ ✅ Issue created successfully");
        } else {
            System.out.println("├─ ❌ Issue creation failed");
            System.out.println("└─ Error: " + errorMessage);
        }
    }
    
    /**
     * Logs the end of the audit session.
     */
    public void logSessionEnd() {
        LocalDateTime endTime = LocalDateTime.now();
        long durationMinutes = java.time.Duration.between(sessionStartTime, endTime).toMinutes();
        
        System.out.println("\n🏁 ===== MISSING ISSUES AUDIT SESSION COMPLETED =====");
        System.out.println("📅 Session ID: " + auditSessionId);
        System.out.println("⏰ End Time: " + endTime.format(TIMESTAMP_FORMAT));
        System.out.println("⏱️ Total Duration: " + durationMinutes + " minutes");
        System.out.println("📊 Total API Requests: " + requestCounter);
        System.out.println("❌ Total Errors: " + errorCounter);
        System.out.println("====================================================");
    }
    
    /**
     * Gets the current session ID for external reference.
     */
    public String getSessionId() {
        return auditSessionId;
    }
    
    /**
     * Gets the current request counter.
     */
    public int getRequestCounter() {
        return requestCounter;
    }
    
    /**
     * Gets the current error counter.
     */
    public int getErrorCounter() {
        return errorCounter;
    }
    
    /**
     * Gets the session start time.
     */
    public LocalDateTime getSessionStartTime() {
        return sessionStartTime;
    }
}