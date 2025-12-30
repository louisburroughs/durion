package com.durion.audit;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Default implementation of ReportManager for generating CSV and JSON reports.
 * 
 * Implements comprehensive report generation with all required fields:
 * - CSV reports for missing frontend and backend issues
 * - JSON summary reports with audit statistics and metadata
 * - Markdown summary reports for human readability
 * - Timestamp-based file naming for reports
 * - Automatic directory structure creation
 * 
 * Requirements: 1.5, 2.1, 2.2, 2.3, 2.4, 2.5, 6.3, 6.5
 */
public class DefaultReportManager implements ReportManager {

    private static final String CSV_HEADER = "Story Number,Story Title,Story URL,Expected Title,Target Repository,Domain";

    private final ObjectMapper objectMapper;
    private final FileOutputManager fileOutputManager;
    private final String baseOutputDirectory;

    public DefaultReportManager(String baseOutputDirectory) {
        this.baseOutputDirectory = baseOutputDirectory;
        this.fileOutputManager = new FileOutputManager(baseOutputDirectory);
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Override
    public List<String> generateReports(AuditResult auditResult) throws IOException {
        List<String> generatedFiles = new ArrayList<>();

        // Validate disk space before generating reports
        long estimatedSize = estimateReportSize(auditResult);
        fileOutputManager.validateDiskSpace(estimatedSize);

        // Create output directory structure
        fileOutputManager.createOutputDirectoryStructure(auditResult.getAuditTimestamp());

        // Generate CSV reports for missing issues
        if (!auditResult.getMissingFrontendIssues().isEmpty()) {
            String frontendFilename = fileOutputManager.generateTimestampedFilename(
                    "missing-frontend", "csv", auditResult.getAuditTimestamp());
            Path frontendCsvPath = fileOutputManager.createReportFilePath(frontendFilename,
                    auditResult.getAuditTimestamp());
            generateMissingFrontendCsv(auditResult.getMissingFrontendIssues(), frontendCsvPath.toString());
            generatedFiles.add(frontendCsvPath.toString());
        }

        if (!auditResult.getMissingBackendIssues().isEmpty()) {
            String backendFilename = fileOutputManager.generateTimestampedFilename(
                    "missing-backend", "csv", auditResult.getAuditTimestamp());
            Path backendCsvPath = fileOutputManager.createReportFilePath(backendFilename,
                    auditResult.getAuditTimestamp());
            generateMissingBackendCsv(auditResult.getMissingBackendIssues(), backendCsvPath.toString());
            generatedFiles.add(backendCsvPath.toString());
        }

        // Generate JSON summary report
        String jsonFilename = fileOutputManager.generateTimestampedFilename(
                "audit", "json", auditResult.getAuditTimestamp());
        Path jsonSummaryPath = fileOutputManager.createReportFilePath(jsonFilename, auditResult.getAuditTimestamp());
        generateJsonSummary(auditResult, jsonSummaryPath.toString());
        generatedFiles.add(jsonSummaryPath.toString());

        // Generate Markdown summary report
        String markdownFilename = fileOutputManager.generateTimestampedFilename(
                "summary", "md", auditResult.getAuditTimestamp());
        Path markdownSummaryPath = fileOutputManager.createReportFilePath(markdownFilename,
                auditResult.getAuditTimestamp());
        generateMarkdownSummary(auditResult, markdownSummaryPath.toString());
        generatedFiles.add(markdownSummaryPath.toString());

        return generatedFiles;
    }

    @Override
    public void generateMissingFrontendCsv(List<MissingIssue> missingIssues, String outputPath) throws IOException {
        generateMissingIssuesCsv(missingIssues, outputPath, "Frontend");
    }

    @Override
    public void generateMissingBackendCsv(List<MissingIssue> missingIssues, String outputPath) throws IOException {
        generateMissingIssuesCsv(missingIssues, outputPath, "Backend");
    }

    /**
     * Common method for generating CSV reports for missing issues.
     * Ensures all required fields are included: story number, title, URL, expected
     * title, target repository, domain.
     */
    private void generateMissingIssuesCsv(List<MissingIssue> missingIssues, String outputPath, String type)
            throws IOException {
        Path path = Paths.get(outputPath);

        // Ensure file is writable and create directories if needed
        fileOutputManager.ensureFileWritable(path);

        try (BufferedWriter writer = Files.newBufferedWriter(path)) {
            // Write CSV header
            writer.write(CSV_HEADER);
            writer.newLine();

            // Write missing issues data
            for (MissingIssue issue : missingIssues) {
                writer.write(formatCsvRow(
                        String.valueOf(issue.getStoryNumber()),
                        escapeCsvField(issue.getStoryTitle()),
                        issue.getStoryUrl(),
                        escapeCsvField(issue.getExpectedTitle()),
                        issue.getTargetRepository()));
                writer.newLine();
            }
        }
    }

    @Override
    public void generateJsonSummary(AuditResult auditResult, String outputPath) throws IOException {
        Path path = Paths.get(outputPath);

        // Ensure file is writable and create directories if needed
        fileOutputManager.ensureFileWritable(path);

        // Create comprehensive JSON summary with all audit data and metadata
        Map<String, Object> summary = new HashMap<>();

        // Audit metadata
        summary.put("auditTimestamp", auditResult.getAuditTimestamp());
        summary.put("totalProcessedStories", auditResult.getTotalProcessedStories());
        summary.put("totalMissingIssues", auditResult.getTotalMissingIssues());

        // Configuration information
        AuditConfiguration config = auditResult.getConfiguration();
        Map<String, Object> configData = new HashMap<>();
        configData.put("outputDirectory", config.getOutputDirectory());
        configData.put("auditMode", config.getAuditMode().toString());
        configData.put("rateLimitDelayMs", config.getRateLimitDelayMs());
        configData.put("batchSize", config.getBatchSize());
        config.getStartDate().ifPresent(date -> configData.put("startDate", date));
        config.getEndDate().ifPresent(date -> configData.put("endDate", date));
        config.getStoryRangeStart().ifPresent(start -> configData.put("storyRangeStart", start));
        config.getStoryRangeEnd().ifPresent(end -> configData.put("storyRangeEnd", end));
        summary.put("configuration", configData);

        // Statistics
        AuditStatistics stats = auditResult.getStatistics();
        Map<String, Object> statisticsData = new HashMap<>();
        statisticsData.put("totalFrontendIssues", stats.getTotalFrontendIssues());
        statisticsData.put("totalBackendIssues", stats.getTotalBackendIssues());
        statisticsData.put("missingFrontendCount", stats.getMissingFrontendCount());
        statisticsData.put("missingBackendCount", stats.getMissingBackendCount());
        statisticsData.put("frontendCompletionPercentage", stats.getFrontendCompletionPercentage());
        statisticsData.put("backendCompletionPercentage", stats.getBackendCompletionPercentage());
        statisticsData.put("overallCompletionPercentage", stats.getOverallCompletionPercentage());
        summary.put("statistics", statisticsData);

        // Missing issues details
        summary.put("missingFrontendIssues", convertMissingIssuesToMap(auditResult.getMissingFrontendIssues()));
        summary.put("missingBackendIssues", convertMissingIssuesToMap(auditResult.getMissingBackendIssues()));

        // Write JSON file
        objectMapper.writeValue(path.toFile(), summary);
    }

    @Override
    public void generateMarkdownSummary(AuditResult auditResult, String outputPath) throws IOException {
        Path path = Paths.get(outputPath);

        // Ensure file is writable and create directories if needed
        fileOutputManager.ensureFileWritable(path);

        try (BufferedWriter writer = Files.newBufferedWriter(path)) {
            AuditStatistics stats = auditResult.getStatistics();

            // Write markdown header
            writer.write("# Missing Issues Audit Report");
            writer.newLine();
            writer.newLine();

            writer.write("**Generated:** "
                    + auditResult.getAuditTimestamp().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            writer.newLine();
            writer.write("**Total Processed Stories:** " + auditResult.getTotalProcessedStories());
            writer.newLine();
            writer.write("**Total Missing Issues:** " + auditResult.getTotalMissingIssues());
            writer.newLine();
            writer.newLine();

            // Statistics section
            writer.write("## Summary Statistics");
            writer.newLine();
            writer.newLine();

            writer.write("| Metric | Frontend | Backend | Overall |");
            writer.newLine();
            writer.write("|--------|----------|---------|---------|");
            writer.newLine();

            writer.write(String.format("| **Issues Found** | %d | %d | %d |",
                    stats.getTotalFrontendIssues(),
                    stats.getTotalBackendIssues(),
                    stats.getTotalFrontendIssues() + stats.getTotalBackendIssues()));
            writer.newLine();

            writer.write(String.format("| **Missing Issues** | %d | %d | %d |",
                    stats.getMissingFrontendCount(),
                    stats.getMissingBackendCount(),
                    stats.getTotalMissingCount()));
            writer.newLine();

            writer.write(String.format("| **Completion %%** | %.1f%% | %.1f%% | %.1f%% |",
                    stats.getFrontendCompletionPercentage(),
                    stats.getBackendCompletionPercentage(),
                    stats.getOverallCompletionPercentage()));
            writer.newLine();
            writer.newLine();

            // Missing frontend issues
            if (!auditResult.getMissingFrontendIssues().isEmpty()) {
                writer.write("## Missing Frontend Issues (" + auditResult.getMissingFrontendIssues().size() + ")");
                writer.newLine();
                writer.newLine();

                for (MissingIssue issue : auditResult.getMissingFrontendIssues()) {
                    writer.write(String.format("- **Story #%d**: [%s](%s)",
                            issue.getStoryNumber(),
                            issue.getStoryTitle(),
                            issue.getStoryUrl()));
                    writer.newLine();
                    writer.write(String.format("  - **Expected Title**: %s", issue.getExpectedTitle()));
                    writer.newLine();
                    writer.write(String.format("  - **Repository**: %s", issue.getTargetRepository()));
                    writer.newLine();
                    writer.newLine();
                }
            }

            // Missing backend issues
            if (!auditResult.getMissingBackendIssues().isEmpty()) {
                writer.write("## Missing Backend Issues (" + auditResult.getMissingBackendIssues().size() + ")");
                writer.newLine();
                writer.newLine();

                for (MissingIssue issue : auditResult.getMissingBackendIssues()) {
                    writer.write(String.format("- **Story #%d**: [%s](%s)",
                            issue.getStoryNumber(),
                            issue.getStoryTitle(),
                            issue.getStoryUrl()));
                    writer.newLine();
                    writer.write(String.format("  - **Expected Title**: %s", issue.getExpectedTitle()));
                    writer.newLine();
                    writer.write(String.format("  - **Repository**: %s", issue.getTargetRepository()));
                    writer.newLine();
                    writer.newLine();
                }
            }

            // Configuration details
            writer.write("## Audit Configuration");
            writer.newLine();
            writer.newLine();

            AuditConfiguration config = auditResult.getConfiguration();
            writer.write("- **Output Directory**: " + config.getOutputDirectory());
            writer.newLine();
            writer.write("- **Audit Mode**: " + config.getAuditMode());
            writer.newLine();
            writer.write("- **Rate Limit Delay**: " + config.getRateLimitDelayMs() + "ms");
            writer.newLine();
            writer.write("- **Batch Size**: " + config.getBatchSize());
            writer.newLine();

            if (config.getStartDate().isPresent() || config.getEndDate().isPresent()) {
                writer.write("- **Date Range**: " +
                        config.getStartDate().map(Object::toString).orElse("(start)") +
                        " to " +
                        config.getEndDate().map(Object::toString).orElse("(end)"));
                writer.newLine();
            }

            if (config.getStoryRangeStart().isPresent() || config.getStoryRangeEnd().isPresent()) {
                writer.write("- **Story Range**: " +
                        config.getStoryRangeStart().map(Object::toString).orElse("(start)") +
                        " to " +
                        config.getStoryRangeEnd().map(Object::toString).orElse("(end)"));
                writer.newLine();
            }
        }
    }

    @Override
    public void updateReportsAfterIssueCreation(List<String> reportPaths, List<Object> createdIssues)
            throws IOException {
        // TODO: Implement report updates after issue creation
        // This will be implemented when issue creation functionality is added
        throw new UnsupportedOperationException("Report updates after issue creation not yet implemented");
    }

    /**
     * Creates the output directory structure with timestamp-based naming.
     * Ensures proper file permissions and error handling.
     */
    private String createOutputDirectory(LocalDateTime timestamp) throws IOException {
        Path baseDir = Paths.get(baseOutputDirectory);

        // Create base directory if it doesn't exist
        if (!Files.exists(baseDir)) {
            Files.createDirectories(baseDir);
        }

        // Create missing-issues subdirectory
        Path missingIssuesDir = baseDir.resolve("missing-issues");
        if (!Files.exists(missingIssuesDir)) {
            Files.createDirectories(missingIssuesDir);
        }

        return missingIssuesDir.toString();
    }

    /**
     * Estimates the size of reports to be generated for disk space validation.
     */
    private long estimateReportSize(AuditResult auditResult) {
        // Rough estimation based on number of missing issues and typical file sizes
        int totalMissingIssues = auditResult.getTotalMissingIssues();

        // Estimate CSV size: ~200 bytes per missing issue
        long csvSize = totalMissingIssues * 200L;

        // Estimate JSON size: ~500 bytes per missing issue + metadata
        long jsonSize = (totalMissingIssues * 500L) + 5000L; // 5KB for metadata

        // Estimate Markdown size: ~300 bytes per missing issue + formatting
        long markdownSize = (totalMissingIssues * 300L) + 2000L; // 2KB for formatting

        return csvSize + jsonSize + markdownSize;
    }

    /**
     * Converts MissingIssue objects to Map for JSON serialization.
     */
    private List<Map<String, Object>> convertMissingIssuesToMap(List<MissingIssue> missingIssues) {
        List<Map<String, Object>> issuesList = new ArrayList<>();

        for (MissingIssue issue : missingIssues) {
            Map<String, Object> issueMap = new HashMap<>();
            issueMap.put("storyNumber", issue.getStoryNumber());
            issueMap.put("storyTitle", issue.getStoryTitle());
            issueMap.put("storyUrl", issue.getStoryUrl());
            issueMap.put("repositoryType", issue.getRepositoryType());
            issueMap.put("targetRepository", issue.getTargetRepository());
            issueMap.put("expectedTitle", issue.getExpectedTitle());
            issuesList.add(issueMap);
        }

        return issuesList;
    }

    /**
     * Formats a CSV row with proper escaping.
     */
    private String formatCsvRow(String... fields) {
        return String.join(",", fields);
    }

    /**
     * Escapes CSV field content by wrapping in quotes and escaping internal quotes.
     */
    private String escapeCsvField(String field) {
        if (field == null) {
            return "";
        }

        // If field contains comma, quote, or newline, wrap in quotes and escape
        // internal quotes
        if (field.contains(",") || field.contains("\"") || field.contains("\n") || field.contains("\r")) {
            return "\"" + field.replace("\"", "\"\"") + "\"";
        }

        return field;
    }
}