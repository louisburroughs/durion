#!/bin/bash

# Network Connectivity Test for GitHub API
# This script helps diagnose network issues

echo "🌐 GitHub API Network Connectivity Test"
echo "======================================"

echo "1️⃣ Testing basic connectivity to GitHub..."
if ping -c 3 github.com > /dev/null 2>&1; then
    echo "✅ Can reach github.com"
else
    echo "❌ Cannot reach github.com"
    echo "   • Check your internet connection"
    echo "   • Check if GitHub is blocked by firewall"
fi

echo ""
echo "2️⃣ Testing HTTPS connectivity to GitHub API..."
if curl -s --connect-timeout 10 https://api.github.com/zen > /dev/null; then
    echo "✅ Can reach GitHub API via HTTPS"
    echo "📄 GitHub Zen: $(curl -s https://api.github.com/zen)"
else
    echo "❌ Cannot reach GitHub API via HTTPS"
    echo "   • Check if HTTPS is blocked"
    echo "   • Check proxy settings"
    echo "   • Check SSL certificate issues"
fi

echo ""
echo "3️⃣ Testing SSL certificate..."
echo | openssl s_client -connect api.github.com:443 -servername api.github.com 2>/dev/null | openssl x509 -noout -dates 2>/dev/null
if [ $? -eq 0 ]; then
    echo "✅ SSL certificate is valid"
else
    echo "❌ SSL certificate issues detected"
    echo "   • This might be a corporate firewall/proxy issue"
    echo "   • Check if you need to configure SSL certificates"
fi

echo ""
echo "4️⃣ Environment information:"
echo "   • OS: $(uname -s)"
echo "   • Java version: $(java -version 2>&1 | head -n 1)"
echo "   • Current directory: $(pwd)"

if [ ! -z "$HTTP_PROXY" ]; then
    echo "   • HTTP_PROXY: $HTTP_PROXY"
fi

if [ ! -z "$HTTPS_PROXY" ]; then
    echo "   • HTTPS_PROXY: $HTTPS_PROXY"
fi

echo ""
echo "🏁 Network test complete!"
echo ""
echo "💡 If you see SSL certificate errors:"
echo "   • You might be behind a corporate firewall"
echo "   • Try setting Java system properties for SSL"
echo "   • Contact your IT department about GitHub API access"