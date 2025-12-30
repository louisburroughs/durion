package com.durion.audit;

import java.util.regex.Pattern;

/**
 * Utility class for validating GitHub tokens and their format.
 */
public class GitHubTokenValidator {
    
    // GitHub token format patterns
    private static final Pattern CLASSIC_TOKEN_PATTERN = Pattern.compile("^ghp_[a-zA-Z0-9]{36}$");
    private static final Pattern FINE_GRAINED_TOKEN_PATTERN = Pattern.compile("^github_pat_[a-zA-Z0-9_]{82}$");
    
    /**
     * Validates the format of a GitHub token.
     * 
     * @param token The token to validate
     * @return TokenValidationResult containing validation status and details
     */
    public static TokenValidationResult validateTokenFormat(String token) {
        if (token == null || token.trim().isEmpty()) {
            return new TokenValidationResult(false, TokenType.UNKNOWN, 
                "Token is null or empty");
        }
        
        String trimmedToken = token.trim();
        
        if (CLASSIC_TOKEN_PATTERN.matcher(trimmedToken).matches()) {
            return new TokenValidationResult(true, TokenType.CLASSIC, 
                "Valid classic personal access token format");
        }
        
        if (FINE_GRAINED_TOKEN_PATTERN.matcher(trimmedToken).matches()) {
            return new TokenValidationResult(true, TokenType.FINE_GRAINED, 
                "Valid fine-grained personal access token format");
        }
        
        // Check for common format issues
        if (trimmedToken.startsWith("ghp_")) {
            if (trimmedToken.length() != 40) {
                return new TokenValidationResult(false, TokenType.CLASSIC, 
                    String.format("Classic token should be 40 characters, but got %d", trimmedToken.length()));
            } else {
                return new TokenValidationResult(false, TokenType.CLASSIC, 
                    "Classic token has invalid characters (should be alphanumeric)");
            }
        }
        
        if (trimmedToken.startsWith("github_pat_")) {
            return new TokenValidationResult(false, TokenType.FINE_GRAINED, 
                String.format("Fine-grained token has invalid format (expected 93 characters, got %d)", trimmedToken.length()));
        }
        
        return new TokenValidationResult(false, TokenType.UNKNOWN, 
            "Token format not recognized (expected ghp_ or github_pat_ prefix)");
    }
    
    /**
     * Gets a GitHub token from command line arguments or environment variables.
     * 
     * @param args Command line arguments
     * @param tokenArgName The argument name to look for (e.g., "--token")
     * @return The token if found, null otherwise
     */
    public static String getTokenFromArgsOrEnv(String[] args, String tokenArgName) {
        // First, check command line arguments
        String token = getTokenFromArgs(args, tokenArgName);
        if (token != null) {
            return token;
        }
        
        // Then check environment variable
        return getTokenFromEnvironment();
    }
    
    /**
     * Extracts GitHub token from command line arguments.
     * 
     * @param args Command line arguments
     * @param tokenArgName The argument name to look for (e.g., "--token")
     * @return The token if found, null otherwise
     */
    public static String getTokenFromArgs(String[] args, String tokenArgName) {
        for (int i = 0; i < args.length - 1; i++) {
            if (tokenArgName.equals(args[i])) {
                return args[i + 1];
            }
        }
        return null;
    }
    
    /**
     * Gets GitHub token from environment variable.
     * 
     * @return The token from GITHUB_TOKEN environment variable, null if not set
     */
    public static String getTokenFromEnvironment() {
        return System.getenv("GITHUB_TOKEN");
    }
    
    /**
     * Validates that a token has the required permissions for the audit system.
     * This is a placeholder for future implementation that would test actual permissions.
     * 
     * @param token The token to validate
     * @return true if the token appears to have required permissions
     */
    public static boolean validateTokenPermissions(String token) {
        // For now, just validate the format
        // In a future implementation, this would make an API call to test permissions
        TokenValidationResult formatResult = validateTokenFormat(token);
        return formatResult.isValid();
    }
    
    /**
     * Creates a safe display version of a token for logging (shows only prefix and length).
     * 
     * @param token The token to create a safe display for
     * @return A safe string representation of the token
     */
    public static String createSafeTokenDisplay(String token) {
        if (token == null || token.trim().isEmpty()) {
            return "[EMPTY TOKEN]";
        }
        
        String trimmedToken = token.trim();
        if (trimmedToken.length() <= 8) {
            return "[TOKEN TOO SHORT]";
        }
        
        return trimmedToken.substring(0, 8) + "..." + " (length: " + trimmedToken.length() + ")";
    }
    
    /**
     * Result of token validation.
     */
    public static class TokenValidationResult {
        private final boolean valid;
        private final TokenType tokenType;
        private final String message;
        
        public TokenValidationResult(boolean valid, TokenType tokenType, String message) {
            this.valid = valid;
            this.tokenType = tokenType;
            this.message = message;
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public TokenType getTokenType() {
            return tokenType;
        }
        
        public String getMessage() {
            return message;
        }
        
        @Override
        public String toString() {
            return String.format("TokenValidationResult{valid=%s, type=%s, message='%s'}", 
                               valid, tokenType, message);
        }
    }
    
    /**
     * Types of GitHub tokens.
     */
    public enum TokenType {
        CLASSIC,        // Classic personal access token (ghp_)
        FINE_GRAINED,   // Fine-grained personal access token (github_pat_)
        UNKNOWN         // Unknown or invalid format
    }
}