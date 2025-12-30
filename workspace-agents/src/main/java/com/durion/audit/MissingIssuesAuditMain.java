package com.durion.audit;

/**
 * Main entry point for the Missing Issues Audit System.
 * Handles command-line argument parsing, token validation, and system initialization.
 */
public class MissingIssuesAuditMain {
    
    public static void main(String[] args) {
        try {
            System.out.println("🔍 Missing Issues Audit System");
            System.out.println("==============================");
            
            // Parse and validate command line arguments
            AuditConfiguration config = AuditCommandLineParser.parseArguments(args);
            AuditCommandLineParser.validateConfiguration(config);
            
            System.out.println("📋 Configuration Summary:");
            System.out.println("   • Audit Mode: " + config.getAuditMode());
            System.out.println("   • Create Issues: " + config.isCreateMissingIssues());
            System.out.println("   • Use Cache: " + config.isUseCache());
            System.out.println("   • Output Directory: " + config.getOutputDirectory());
            System.out.println("   • Rate Limit Delay: " + config.getRateLimitDelayMs() + "ms");
            System.out.println("   • Batch Size: " + config.getBatchSize());
            
            if (config.getStartDate().isPresent() && config.getEndDate().isPresent()) {
                System.out.println("   • Date Range: " + config.getStartDate().get() + " to " + config.getEndDate().get());
            }
            
            if (config.getStoryRangeStart().isPresent() && config.getStoryRangeEnd().isPresent()) {
                System.out.println("   • Story Range: #" + config.getStoryRangeStart().get() + " to #" + config.getStoryRangeEnd().get());
            }
            
            System.out.println();
            
            // Initialize and run the audit system
            MissingIssuesAuditSystem auditSystem = new MissingIssuesAuditSystem(config);
            
            // Run the audit
            AuditResult result = auditSystem.runAudit();
            
            // Display final summary
            System.out.println();
            System.out.println("🎉 Audit System Execution Complete!");
            System.out.println("===================================");
            System.out.println("📊 Final Results:");
            System.out.println("   • Total Processed Stories: " + result.getTotalProcessedStories());
            System.out.println("   • Missing Frontend Issues: " + result.getMissingFrontendIssues().size());
            System.out.println("   • Missing Backend Issues: " + result.getMissingBackendIssues().size());
            System.out.println("   • Total Missing Issues: " + result.getTotalMissingIssues());
            
            if (result.getTotalMissingIssues() == 0) {
                System.out.println("✅ All processed stories have implementation issues!");
            } else {
                System.out.println("📋 Check the generated reports for details on missing issues");
            }
            
        } catch (IllegalArgumentException e) {
            System.err.println("❌ Configuration Error: " + e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            System.err.println("❌ Audit Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Demonstrates token validation functionality.
     */
    public static void demonstrateTokenValidation(String[] testTokens) {
        System.out.println("🧪 Token Validation Demonstration");
        System.out.println("=================================");
        
        for (String token : testTokens) {
            System.out.println();
            System.out.println("Testing token: " + GitHubTokenValidator.createSafeTokenDisplay(token));
            
            GitHubTokenValidator.TokenValidationResult result = 
                GitHubTokenValidator.validateTokenFormat(token);
            
            System.out.println("  Result: " + result);
            
            if (result.isValid()) {
                System.out.println("  ✅ Token format is valid");
            } else {
                System.out.println("  ❌ Token format is invalid");
            }
        }
    }
}