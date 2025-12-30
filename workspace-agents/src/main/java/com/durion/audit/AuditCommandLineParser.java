package com.durion.audit;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;

/**
 * Command-line argument parser for the Missing Issues Audit System.
 */
public class AuditCommandLineParser {
    
    private static final String HELP_TEXT = """
        Missing Issues Audit System
        
        Usage: java -jar missing-issues-audit.jar [OPTIONS]
        
        Authentication:
          --token <token>           GitHub personal access token
                                   (or set GITHUB_TOKEN environment variable)
        
        Audit Modes:
          --audit                  Run full audit (default)
          --incremental-days <N>   Audit stories from last N days
          --incremental-range <start>-<end>  Audit story number range
          --resume                 Resume interrupted audit
        
        Actions:
          --create-issues          Create missing issues after audit
          --create-from-report <path>  Create issues from existing report
        
        Options:
          --output-dir <path>      Output directory (default: .github/orchestration/missing-issues/)
          --no-cache              Disable caching
          --rate-limit-delay <ms>  Delay between API calls (default: 2000ms)
          --batch-size <N>        Batch size for rate limiting (default: 5)
          --help                  Show this help message
        
        Examples:
          # Basic audit with environment token
          export GITHUB_TOKEN=ghp_your_token_here
          java -jar missing-issues-audit.jar --audit
          
          # Audit with command line token
          java -jar missing-issues-audit.jar --token ghp_your_token_here --audit
          
          # Incremental audit (last 7 days)
          java -jar missing-issues-audit.jar --token ghp_your_token_here --incremental-days 7
          
          # Range audit
          java -jar missing-issues-audit.jar --token ghp_your_token_here --incremental-range 200-273
          
          # Audit and create missing issues
          java -jar missing-issues-audit.jar --token ghp_your_token_here --audit --create-issues
        """;
    
    /**
     * Parses command line arguments and creates an AuditConfiguration.
     * 
     * @param args Command line arguments
     * @return Parsed AuditConfiguration
     * @throws IllegalArgumentException if arguments are invalid
     */
    public static AuditConfiguration parseArguments(String[] args) {
        if (args.length == 0 || containsArg(args, "--help")) {
            System.out.println(HELP_TEXT);
            System.exit(0);
        }
        
        // Get GitHub token
        String token = GitHubTokenValidator.getTokenFromArgsOrEnv(args, "--token");
        if (token == null) {
            throw new IllegalArgumentException(
                "GitHub token is required. Use --token <token> or set GITHUB_TOKEN environment variable.");
        }
        
        // Validate token format
        GitHubTokenValidator.TokenValidationResult tokenValidation = 
            GitHubTokenValidator.validateTokenFormat(token);
        if (!tokenValidation.isValid()) {
            throw new IllegalArgumentException("Invalid GitHub token: " + tokenValidation.getMessage());
        }
        
        System.out.println("✅ GitHub token validated: " + tokenValidation.getMessage());
        System.out.println("🔑 Token: " + GitHubTokenValidator.createSafeTokenDisplay(token));
        
        // Parse audit mode
        AuditMode auditMode = parseAuditMode(args);
        
        // Build configuration
        AuditConfiguration.Builder configBuilder = AuditConfiguration.builder()
            .githubToken(token)
            .auditMode(auditMode);
        
        // Parse date range for incremental mode
        if (auditMode == AuditMode.INCREMENTAL_DATE) {
            int days = getIntArg(args, "--incremental-days", 7);
            LocalDate endDate = LocalDate.now();
            LocalDate startDate = endDate.minusDays(days);
            configBuilder.dateRange(startDate, endDate);
        }
        
        // Parse story range for range mode
        if (auditMode == AuditMode.INCREMENTAL_RANGE) {
            String rangeStr = getStringArg(args, "--incremental-range", null);
            if (rangeStr == null) {
                throw new IllegalArgumentException("--incremental-range requires a range like 200-273");
            }
            
            String[] rangeParts = rangeStr.split("-");
            if (rangeParts.length != 2) {
                throw new IllegalArgumentException("Range format should be start-end, e.g., 200-273");
            }
            
            try {
                int start = Integer.parseInt(rangeParts[0].trim());
                int end = Integer.parseInt(rangeParts[1].trim());
                configBuilder.storyRange(start, end);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid range numbers: " + rangeStr);
            }
        }
        
        // Parse other options
        configBuilder
            .createMissingIssues(containsArg(args, "--create-issues"))
            .useCache(!containsArg(args, "--no-cache"))
            .outputDirectory(getStringArg(args, "--output-dir", ".github/orchestration/missing-issues/"))
            .rateLimitDelayMs(getIntArg(args, "--rate-limit-delay", 2000))
            .batchSize(getIntArg(args, "--batch-size", 5));
        
        return configBuilder.build();
    }
    
    /**
     * Determines the audit mode from command line arguments.
     */
    private static AuditMode parseAuditMode(String[] args) {
        if (containsArg(args, "--incremental-days")) {
            return AuditMode.INCREMENTAL_DATE;
        }
        if (containsArg(args, "--incremental-range")) {
            return AuditMode.INCREMENTAL_RANGE;
        }
        if (containsArg(args, "--resume")) {
            return AuditMode.RESUME_AUDIT;
        }
        return AuditMode.FULL_AUDIT; // Default
    }
    
    /**
     * Checks if an argument is present in the args array.
     */
    private static boolean containsArg(String[] args, String arg) {
        return Arrays.asList(args).contains(arg);
    }
    
    /**
     * Gets a string argument value.
     */
    private static String getStringArg(String[] args, String argName, String defaultValue) {
        for (int i = 0; i < args.length - 1; i++) {
            if (argName.equals(args[i])) {
                return args[i + 1];
            }
        }
        return defaultValue;
    }
    
    /**
     * Gets an integer argument value.
     */
    private static int getIntArg(String[] args, String argName, int defaultValue) {
        String stringValue = getStringArg(args, argName, null);
        if (stringValue == null) {
            return defaultValue;
        }
        
        try {
            return Integer.parseInt(stringValue);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid integer value for " + argName + ": " + stringValue);
        }
    }
    
    /**
     * Validates the parsed configuration for consistency.
     */
    public static void validateConfiguration(AuditConfiguration config) {
        // Validate GitHub token permissions (placeholder for now)
        if (!GitHubTokenValidator.validateTokenPermissions(config.getGithubToken())) {
            throw new IllegalArgumentException("GitHub token does not have required permissions");
        }
        
        // Validate date ranges
        if (config.getStartDate().isPresent() && config.getEndDate().isPresent()) {
            LocalDate start = config.getStartDate().get();
            LocalDate end = config.getEndDate().get();
            if (start.isAfter(end)) {
                throw new IllegalArgumentException("Start date cannot be after end date");
            }
        }
        
        // Validate story ranges
        if (config.getStoryRangeStart().isPresent() && config.getStoryRangeEnd().isPresent()) {
            int start = config.getStoryRangeStart().get();
            int end = config.getStoryRangeEnd().get();
            if (start > end) {
                throw new IllegalArgumentException("Story range start cannot be greater than end");
            }
            if (start < 1) {
                throw new IllegalArgumentException("Story range start must be positive");
            }
        }
        
        // Validate rate limiting parameters
        if (config.getRateLimitDelayMs() < 0) {
            throw new IllegalArgumentException("Rate limit delay cannot be negative");
        }
        
        if (config.getBatchSize() < 1) {
            throw new IllegalArgumentException("Batch size must be at least 1");
        }
        
        System.out.println("✅ Configuration validated successfully");
    }
}