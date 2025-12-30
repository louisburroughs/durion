#!/bin/bash

echo "🧪 **GITHUB RATE LIMIT TEST**"
echo "============================="
echo ""

# Check if GITHUB_TOKEN is set
if [ -z "$GITHUB_TOKEN" ]; then
    echo "❌ GITHUB_TOKEN environment variable is not set"
    echo "   Please set it with: export GITHUB_TOKEN=your_token_here"
    exit 1
fi

echo "🔍 **Testing Rate Limit Check Functionality**"
echo "============================================="

# Create a simple test class to check rate limits
cat > src/main/java/RateLimitTest.java << 'EOF'
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
EOF

echo "📝 Created rate limit test class"
echo "🔧 Compiling..."

mvn compile -q

if [ $? -eq 0 ]; then
    echo "✅ Compilation successful"
    echo "🧪 Running rate limit test..."
    java -cp target/classes RateLimitTest $GITHUB_TOKEN
else
    echo "❌ Compilation failed"
    exit 1
fi

echo ""
echo "🏁 Rate limit test complete!"