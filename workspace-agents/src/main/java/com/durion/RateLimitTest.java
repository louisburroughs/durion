package com.durion;
public class RateLimitTest {
    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("❌ Usage: java RateLimitTest <github_token>");
            return;
        }
        
        String token = args[0];
        System.out.println("🧪 **RATE LIMIT TEST**");
        System.out.println("=====================");
        
        try {
            GitHubApiClientSSLBypass client = new GitHubApiClientSSLBypass(token);
            
            System.out.println("🔍 Testing rate limit check...");
            client.checkRateLimitAndWait();
            
            System.out.println("✅ Rate limit check completed successfully!");
            
        } catch (Exception e) {
            System.out.println("❌ Error during rate limit test: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
