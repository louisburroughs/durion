package com.durion.audit;

import java.io.IOException;
import java.util.List;

/**
 * Interface for managing audit report generation and file output.
 */
public interface ReportManager {
    
    /**
     * Generates all audit reports (CSV, JSON, summary) from the audit result.
     * 
     * @param auditResult The result of the audit operation
     * @return List of file paths where reports were saved
     * @throws IOException if report generation fails
     */
    List<String> generateReports(AuditResult auditResult) throws IOException;
    
    /**
     * Generates a CSV report of missing frontend issues.
     * 
     * @param missingIssues List of missing frontend issues
     * @param outputPath Path where the CSV file should be saved
     * @throws IOException if CSV generation fails
     */
    void generateMissingFrontendCsv(List<MissingIssue> missingIssues, String outputPath) throws IOException;
    
    /**
     * Generates a CSV report of missing backend issues.
     * 
     * @param missingIssues List of missing backend issues
     * @param outputPath Path where the CSV file should be saved
     * @throws IOException if CSV generation fails
     */
    void generateMissingBackendCsv(List<MissingIssue> missingIssues, String outputPath) throws IOException;
    
    /**
     * Generates a JSON summary report with audit statistics and metadata.
     * 
     * @param auditResult The complete audit result
     * @param outputPath Path where the JSON file should be saved
     * @throws IOException if JSON generation fails
     */
    void generateJsonSummary(AuditResult auditResult, String outputPath) throws IOException;
    
    /**
     * Generates a human-readable markdown summary report.
     * 
     * @param auditResult The complete audit result
     * @param outputPath Path where the markdown file should be saved
     * @throws IOException if markdown generation fails
     */
    void generateMarkdownSummary(AuditResult auditResult, String outputPath) throws IOException;
    
    /**
     * Updates existing reports to reflect newly created issues.
     * 
     * @param reportPaths List of report file paths to update
     * @param createdIssues List of issues that were successfully created
     * @throws IOException if report update fails
     */
    void updateReportsAfterIssueCreation(List<String> reportPaths, 
                                       List<Object> createdIssues) throws IOException;  // Temporarily using Object
}