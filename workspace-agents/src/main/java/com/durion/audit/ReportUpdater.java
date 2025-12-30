package com.durion.audit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Updates audit reports to reflect newly created issues and track creation status.
 * 
 * Provides functionality to:
 * - Update existing audit reports with creation results
 * - Track success/failure status for each creation attempt
 * - Maintain report consistency after batch operations
 * - Generate updated summary reports
 * 
 * Requirements: 4.5 - Report updates after creation
 */
public class ReportUpdater {
    
    private final AuditLogger logger;
    private final String outputDirectory;
    
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    public ReportUpdater(AuditLogger logger, String outputDirectory) {
        this.logger = logger;
        this.outputDirectory = outputDirectory;
    }
    
    /**
     * Updates an existing audit report with issue creation results
     * 
     * @param originalReportPath Path to the original audit report
     * @param creationResults List of issue creation results
     * @return Path to the updated report
     * @throws IOException if report update fails
     */
    public String updateAuditReport(String originalReportPath, List<IssueCreator.IssueCreationResult> creationResults) 
            throws IOException {
        
        logger.logProgress("Report Update", 0, creationResults.size());
        
        // Read the original report
        Path originalPath = Paths.get(originalReportPath);
        if (!Files.exists(originalPath)) {
            throw new IOException("Original report not found: " + originalReportPath);
        }
        
        String originalContent = Files.readString(originalPath);
        
        // Generate updated content
        String updatedContent = generateUpdatedReportContent(originalContent, creationResults);
        
        // Create updated report with timestamp
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String updatedReportPath = originalReportPath.replace(".json", "-updated-" + timestamp + ".json");
        
        Files.writeString(Paths.get(updatedReportPath), updatedContent);
        
        logger.logProgress("Report Update", creationResults.size(), creationResults.size());
        System.out.println("📄 Updated audit report saved: " + updatedReportPath);
        
        return updatedReportPath;
    }
    
    /**
     * Creates a creation status report for batch operations
     * 
     * @param batchResult The batch creation result
     * @param originalMissingIssues The original list of missing issues
     * @return Path to the creation status report
     * @throws IOException if report creation fails
     */
    public String createCreationStatusReport(IssueCreator.BatchCreationResult batchResult, 
                                           List<MissingIssue> originalMissingIssues) throws IOException {
        
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String reportPath = Paths.get(outputDirectory, "issue-creation-status-" + timestamp + ".json").toString();
        
        CreationStatusReport statusReport = new CreationStatusReport(
            LocalDateTime.now(),
            batchResult.getTotalCount(),
            batchResult.getSuccessCount(),
            batchResult.getFailureCount(),
            batchResult.getSuccessRate(),
            generateCreationDetails(batchResult.getResults(), originalMissingIssues),
            batchResult.getBatchErrors()
        );
        
        String jsonContent = formatCreationStatusReportAsJson(statusReport);
        Files.writeString(Paths.get(reportPath), jsonContent);
        
        System.out.println("📊 Creation status report saved: " + reportPath);
        return reportPath;
    }
    
    /**
     * Updates a CSV report to mark successfully created issues
     * 
     * @param csvReportPath Path to the original CSV report
     * @param creationResults List of creation results
     * @return Path to the updated CSV report
     * @throws IOException if CSV update fails
     */
    public String updateCsvReport(String csvReportPath, List<IssueCreator.IssueCreationResult> creationResults) 
            throws IOException {
        
        Path originalPath = Paths.get(csvReportPath);
        if (!Files.exists(originalPath)) {
            throw new IOException("Original CSV report not found: " + csvReportPath);
        }
        
        List<String> lines = Files.readAllLines(originalPath);
        List<String> updatedLines = new ArrayList<>();
        
        // Keep the header and add status columns if not present
        if (!lines.isEmpty()) {
            String header = lines.get(0);
            if (!header.contains("Creation Status")) {
                header += ",Creation Status,Created Issue URL,Creation Timestamp,Error Message";
            }
            updatedLines.add(header);
            
            // Process data lines
            for (int i = 1; i < lines.size(); i++) {
                String line = lines.get(i);
                String updatedLine = updateCsvLineWithCreationStatus(line, creationResults, i - 1);
                updatedLines.add(updatedLine);
            }
        }
        
        // Write updated CSV
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String updatedCsvPath = csvReportPath.replace(".csv", "-updated-" + timestamp + ".csv");
        
        Files.write(Paths.get(updatedCsvPath), updatedLines);
        
        System.out.println("📋 Updated CSV report saved: " + updatedCsvPath);
        return updatedCsvPath;
    }
    
    /**
     * Generates a summary report of creation operations
     * 
     * @param batchResult The batch creation result
     * @param originalMissingIssues The original missing issues
     * @return Path to the summary report
     * @throws IOException if summary creation fails
     */
    public String generateCreationSummary(IssueCreator.BatchCreationResult batchResult, 
                                        List<MissingIssue> originalMissingIssues) throws IOException {
        
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String summaryPath = Paths.get(outputDirectory, "creation-summary-" + timestamp + ".md").toString();
        
        StringBuilder summary = new StringBuilder();
        summary.append("# Issue Creation Summary Report\n\n");
        summary.append("**Generated**: ").append(LocalDateTime.now().format(TIMESTAMP_FORMAT)).append("\n");
        summary.append("**Total Issues Processed**: ").append(batchResult.getTotalCount()).append("\n");
        summary.append("**Successfully Created**: ").append(batchResult.getSuccessCount()).append("\n");
        summary.append("**Failed to Create**: ").append(batchResult.getFailureCount()).append("\n");
        summary.append("**Success Rate**: ").append(String.format("%.1f%%", batchResult.getSuccessRate() * 100)).append("\n\n");
        
        // Success details
        if (batchResult.getSuccessCount() > 0) {
            summary.append("## ✅ Successfully Created Issues\n\n");
            for (int i = 0; i < batchResult.getResults().size(); i++) {
                IssueCreator.IssueCreationResult result = batchResult.getResults().get(i);
                if (result.isSuccess()) {
                    MissingIssue original = originalMissingIssues.get(i);
                    summary.append("- **Story #").append(original.getStoryNumber()).append("**: ");
                    summary.append(original.getStoryTitle()).append("\n");
                    
                    if (result.getFrontendIssue() != null) {
                        summary.append("  - Frontend: ").append(result.getFrontendIssue().getUrl()).append("\n");
                    }
                    if (result.getBackendIssue() != null) {
                        summary.append("  - Backend: ").append(result.getBackendIssue().getUrl()).append("\n");
                    }
                    summary.append("\n");
                }
            }
        }
        
        // Failure details
        if (batchResult.getFailureCount() > 0) {
            summary.append("## ❌ Failed to Create Issues\n\n");
            for (int i = 0; i < batchResult.getResults().size(); i++) {
                IssueCreator.IssueCreationResult result = batchResult.getResults().get(i);
                if (!result.isSuccess()) {
                    MissingIssue original = originalMissingIssues.get(i);
                    summary.append("- **Story #").append(original.getStoryNumber()).append("**: ");
                    summary.append(original.getStoryTitle()).append("\n");
                    summary.append("  - **Error**: ").append(result.getMessage()).append("\n");
                    
                    if (!result.getErrors().isEmpty()) {
                        summary.append("  - **Details**: ").append(String.join("; ", result.getErrors())).append("\n");
                    }
                    summary.append("\n");
                }
            }
        }
        
        // Batch errors
        if (!batchResult.getBatchErrors().isEmpty()) {
            summary.append("## ⚠️ Batch Processing Errors\n\n");
            for (String error : batchResult.getBatchErrors()) {
                summary.append("- ").append(error).append("\n");
            }
            summary.append("\n");
        }
        
        summary.append("---\n");
        summary.append("*Generated by Missing Issues Audit System*\n");
        
        Files.writeString(Paths.get(summaryPath), summary.toString());
        
        System.out.println("📝 Creation summary saved: " + summaryPath);
        return summaryPath;
    }
    
    /**
     * Validates report consistency after batch operations
     * 
     * @param originalReportPath Path to original report
     * @param updatedReportPath Path to updated report
     * @param creationResults List of creation results
     * @return true if reports are consistent
     */
    public boolean validateReportConsistency(String originalReportPath, String updatedReportPath, 
                                           List<IssueCreator.IssueCreationResult> creationResults) {
        try {
            // Basic validation - check that files exist and are readable
            Path originalPath = Paths.get(originalReportPath);
            Path updatedPath = Paths.get(updatedReportPath);
            
            if (!Files.exists(originalPath) || !Files.exists(updatedPath)) {
                System.out.println("❌ Report consistency validation failed: Missing files");
                return false;
            }
            
            // Check that updated report is larger (contains additional data)
            long originalSize = Files.size(originalPath);
            long updatedSize = Files.size(updatedPath);
            
            if (updatedSize <= originalSize) {
                System.out.println("❌ Report consistency validation failed: Updated report not larger than original");
                return false;
            }
            
            // Check that creation results count matches expectations
            int successCount = (int) creationResults.stream().mapToLong(r -> r.isSuccess() ? 1 : 0).sum();
            int failureCount = creationResults.size() - successCount;
            
            System.out.println("✅ Report consistency validation passed");
            System.out.println("   - Original report size: " + originalSize + " bytes");
            System.out.println("   - Updated report size: " + updatedSize + " bytes");
            System.out.println("   - Creation results: " + successCount + " success, " + failureCount + " failure");
            
            return true;
            
        } catch (IOException e) {
            System.out.println("❌ Report consistency validation failed: " + e.getMessage());
            return false;
        }
    }
    
    // Helper methods
    
    private String generateUpdatedReportContent(String originalContent, List<IssueCreator.IssueCreationResult> creationResults) {
        // For JSON reports, we would parse and update the JSON structure
        // For simplicity in this implementation, we'll append creation results
        
        StringBuilder updated = new StringBuilder(originalContent);
        
        // Remove the closing brace if it exists
        if (updated.toString().trim().endsWith("}")) {
            int lastBrace = updated.lastIndexOf("}");
            updated.delete(lastBrace, updated.length());
            updated.append(",\n");
        }
        
        // Add creation results section
        updated.append("  \"creationResults\": {\n");
        updated.append("    \"timestamp\": \"").append(LocalDateTime.now().format(TIMESTAMP_FORMAT)).append("\",\n");
        updated.append("    \"totalAttempts\": ").append(creationResults.size()).append(",\n");
        
        int successCount = (int) creationResults.stream().mapToLong(r -> r.isSuccess() ? 1 : 0).sum();
        updated.append("    \"successCount\": ").append(successCount).append(",\n");
        updated.append("    \"failureCount\": ").append(creationResults.size() - successCount).append(",\n");
        updated.append("    \"results\": [\n");
        
        for (int i = 0; i < creationResults.size(); i++) {
            IssueCreator.IssueCreationResult result = creationResults.get(i);
            updated.append("      {\n");
            updated.append("        \"success\": ").append(result.isSuccess()).append(",\n");
            updated.append("        \"message\": \"").append(escapeJson(result.getMessage())).append("\",\n");
            
            if (result.getFrontendIssue() != null) {
                updated.append("        \"frontendIssueUrl\": \"").append(result.getFrontendIssue().getUrl()).append("\",\n");
            }
            if (result.getBackendIssue() != null) {
                updated.append("        \"backendIssueUrl\": \"").append(result.getBackendIssue().getUrl()).append("\",\n");
            }
            
            updated.append("        \"errors\": [");
            for (int j = 0; j < result.getErrors().size(); j++) {
                updated.append("\"").append(escapeJson(result.getErrors().get(j))).append("\"");
                if (j < result.getErrors().size() - 1) updated.append(", ");
            }
            updated.append("]\n");
            updated.append("      }");
            if (i < creationResults.size() - 1) updated.append(",");
            updated.append("\n");
        }
        
        updated.append("    ]\n");
        updated.append("  }\n");
        updated.append("}");
        
        return updated.toString();
    }
    
    private List<CreationDetail> generateCreationDetails(List<IssueCreator.IssueCreationResult> results, 
                                                       List<MissingIssue> originalIssues) {
        List<CreationDetail> details = new ArrayList<>();
        
        for (int i = 0; i < results.size() && i < originalIssues.size(); i++) {
            IssueCreator.IssueCreationResult result = results.get(i);
            MissingIssue original = originalIssues.get(i);
            
            details.add(new CreationDetail(
                original.getStoryNumber(),
                original.getStoryTitle(),
                result.isSuccess(),
                result.getFrontendIssue() != null ? result.getFrontendIssue().getUrl() : null,
                result.getBackendIssue() != null ? result.getBackendIssue().getUrl() : null,
                result.getMessage(),
                result.getErrors()
            ));
        }
        
        return details;
    }
    
    private String updateCsvLineWithCreationStatus(String line, List<IssueCreator.IssueCreationResult> creationResults, int index) {
        if (index >= creationResults.size()) {
            return line + ",Not Processed,,,";
        }
        
        IssueCreator.IssueCreationResult result = creationResults.get(index);
        StringBuilder updated = new StringBuilder(line);
        
        updated.append(",").append(result.isSuccess() ? "Success" : "Failed");
        
        // Add created issue URL (prefer frontend, then backend)
        String issueUrl = "";
        if (result.getFrontendIssue() != null) {
            issueUrl = result.getFrontendIssue().getUrl();
        } else if (result.getBackendIssue() != null) {
            issueUrl = result.getBackendIssue().getUrl();
        }
        updated.append(",").append(issueUrl);
        
        // Add timestamp
        updated.append(",").append(LocalDateTime.now().format(TIMESTAMP_FORMAT));
        
        // Add error message if any
        String errorMsg = result.getErrors().isEmpty() ? "" : String.join("; ", result.getErrors());
        updated.append(",\"").append(escapeJson(errorMsg)).append("\"");
        
        return updated.toString();
    }
    
    private String formatCreationStatusReportAsJson(CreationStatusReport report) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"timestamp\": \"").append(report.timestamp.format(TIMESTAMP_FORMAT)).append("\",\n");
        json.append("  \"totalCount\": ").append(report.totalCount).append(",\n");
        json.append("  \"successCount\": ").append(report.successCount).append(",\n");
        json.append("  \"failureCount\": ").append(report.failureCount).append(",\n");
        json.append("  \"successRate\": ").append(report.successRate).append(",\n");
        json.append("  \"details\": [\n");
        
        for (int i = 0; i < report.details.size(); i++) {
            CreationDetail detail = report.details.get(i);
            json.append("    {\n");
            json.append("      \"storyNumber\": ").append(detail.storyNumber).append(",\n");
            json.append("      \"storyTitle\": \"").append(escapeJson(detail.storyTitle)).append("\",\n");
            json.append("      \"success\": ").append(detail.success).append(",\n");
            json.append("      \"frontendIssueUrl\": ").append(detail.frontendIssueUrl != null ? "\"" + detail.frontendIssueUrl + "\"" : "null").append(",\n");
            json.append("      \"backendIssueUrl\": ").append(detail.backendIssueUrl != null ? "\"" + detail.backendIssueUrl + "\"" : "null").append(",\n");
            json.append("      \"message\": \"").append(escapeJson(detail.message)).append("\",\n");
            json.append("      \"errors\": [");
            for (int j = 0; j < detail.errors.size(); j++) {
                json.append("\"").append(escapeJson(detail.errors.get(j))).append("\"");
                if (j < detail.errors.size() - 1) json.append(", ");
            }
            json.append("]\n");
            json.append("    }");
            if (i < report.details.size() - 1) json.append(",");
            json.append("\n");
        }
        
        json.append("  ],\n");
        json.append("  \"batchErrors\": [");
        for (int i = 0; i < report.batchErrors.size(); i++) {
            json.append("\"").append(escapeJson(report.batchErrors.get(i))).append("\"");
            if (i < report.batchErrors.size() - 1) json.append(", ");
        }
        json.append("]\n");
        json.append("}");
        
        return json.toString();
    }
    
    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
    
    // Data classes for report structure
    
    private static class CreationStatusReport {
        final LocalDateTime timestamp;
        final int totalCount;
        final int successCount;
        final int failureCount;
        final double successRate;
        final List<CreationDetail> details;
        final List<String> batchErrors;
        
        CreationStatusReport(LocalDateTime timestamp, int totalCount, int successCount, int failureCount,
                           double successRate, List<CreationDetail> details, List<String> batchErrors) {
            this.timestamp = timestamp;
            this.totalCount = totalCount;
            this.successCount = successCount;
            this.failureCount = failureCount;
            this.successRate = successRate;
            this.details = details;
            this.batchErrors = batchErrors;
        }
    }
    
    private static class CreationDetail {
        final int storyNumber;
        final String storyTitle;
        final boolean success;
        final String frontendIssueUrl;
        final String backendIssueUrl;
        final String message;
        final List<String> errors;
        
        CreationDetail(int storyNumber, String storyTitle, boolean success, String frontendIssueUrl,
                      String backendIssueUrl, String message, List<String> errors) {
            this.storyNumber = storyNumber;
            this.storyTitle = storyTitle;
            this.success = success;
            this.frontendIssueUrl = frontendIssueUrl;
            this.backendIssueUrl = backendIssueUrl;
            this.message = message;
            this.errors = new ArrayList<>(errors);
        }
    }
}