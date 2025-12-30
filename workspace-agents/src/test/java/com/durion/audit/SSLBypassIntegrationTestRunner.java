package com.durion.audit;

import com.durion.audit.EnhancedGitHubRepositoryScanner;
import com.durion.audit.GitHubApiClientWrapper;
import com.durion.audit.GitHubRateLimiter;

/**
 * Simple test runner to verify SSL bypass integration in EnhancedGitHubRepositoryScanner.
 * This test verifies that the scanner properly integrates with GitHubApiClientSSLBypass.
 */
public class SSLBypassIntegrationTestRunner {
    
    public static void main(String[] args) throws Exception {
        System.out.println("🧪 Testing SSL Bypass Integration in EnhancedGitHubRepositoryScanner");
        System.out.println("================================================================");
        
        // Test 1: Scanner initialization with SSL bypass
        testScannerInitialization();
        
        // Test 2: Rate limiter integration
        testRateLimiterIntegration();
        
        // Test 3: SSL bypass client access
        testSSLBypassClientAccess();
        
        System.out.println("✅ All SSL bypass integration tests passed!");
    }
    
    private static void testScannerInitialization() throws Exception {
        System.out.println("Test 1: Scanner initialization with SSL bypass");
        
        try {
            String testToken = "ghp_test_token_for_initialization_test_only";
            EnhancedGitHubRepositoryScanner scanner = new EnhancedGitHubRepositoryScanner(testToken);
            
            // Verify scanner was created successfully
            if (scanner == null) {
                throw new AssertionError("Scanner should not be null");
            }
            
            // Verify rate limiter is available
            GitHubRateLimiter rateLimiter = scanner.getRateLimiter();
            if (rateLimiter == null) {
                throw new AssertionError("Rate limiter should not be null");
            }
            
            // Verify SSL bypass client is available
            GitHubApiClientWrapper sslClient = scanner.getSslBypassClient();
            if (sslClient == null) {
                throw new AssertionError("SSL bypass client should not be null");
            }
            
            System.out.println("  ✓ Scanner initialization with SSL bypass works correctly");
            
        } catch (Exception e) {
            System.out.println("  ❌ Scanner initialization failed: " + e.getMessage());
            throw e;
        }
    }
    
    private static void testRateLimiterIntegration() throws Exception {
        System.out.println("Test 2: Rate limiter integration");
        
        try {
            String testToken = "ghp_test_token_for_rate_limiter_test_only";
            EnhancedGitHubRepositoryScanner scanner = new EnhancedGitHubRepositoryScanner(testToken);
            
            GitHubRateLimiter rateLimiter = scanner.getRateLimiter();
            
            // Verify initial operation count
            int initialCount = rateLimiter.getOperationCount();
            if (initialCount != 0) {
                throw new AssertionError("Initial operation count should be 0, but was " + initialCount);
            }
            
            // Test rate limiter reset functionality
            rateLimiter.resetOperationCount();
            int resetCount = rateLimiter.getOperationCount();
            if (resetCount != 0) {
                throw new AssertionError("Reset operation count should be 0, but was " + resetCount);
            }
            
            System.out.println("  ✓ Rate limiter integration works correctly");
            
        } catch (Exception e) {
            System.out.println("  ❌ Rate limiter integration failed: " + e.getMessage());
            throw e;
        }
    }
    
    private static void testSSLBypassClientAccess() throws Exception {
        System.out.println("Test 3: SSL bypass client access");
        
        try {
            String testToken = "ghp_test_token_for_ssl_client_test_only";
            EnhancedGitHubRepositoryScanner scanner = new EnhancedGitHubRepositoryScanner(testToken);
            
            GitHubApiClientWrapper sslClient = scanner.getSslBypassClient();
            
            // Verify SSL client is properly configured
            if (sslClient == null) {
                throw new AssertionError("SSL bypass client should not be null");
            }
            
            // Test scanner summary logging (should not throw exceptions)
            scanner.logSummary();
            
            System.out.println("  ✓ SSL bypass client access works correctly");
            
        } catch (Exception e) {
            System.out.println("  ❌ SSL bypass client access failed: " + e.getMessage());
            throw e;
        }
    }
}