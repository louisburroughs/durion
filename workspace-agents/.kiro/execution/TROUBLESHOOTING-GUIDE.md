# 🔧 GitHub API Connection Troubleshooting Guide

## 🎯 **Enhanced Logging Added**

The GitHubApiClient now includes comprehensive logging to help diagnose connection issues. Here's how to use it:

## 🧪 **Step 1: Test Your GitHub Token**

### Quick Test:
```bash
cd workspace-agents

# Linux/Mac
export GITHUB_TOKEN=your_actual_token_here
./test-github-connection.sh

# Windows
set GITHUB_TOKEN=your_actual_token_here
test-github-connection.bat

# Or with token as argument
java -cp "target/classes" GitHubApiClient your_actual_token_here
```

### What You'll See:

#### ✅ **Successful Connection:**
```
🧪 **GITHUB API CLIENT TEST**
=============================
🔍 **GITHUB API CONNECTION TEST**
================================
🔑 Token prefix: ghp_1234...
📏 Token length: 40 characters
✅ Token format: Personal Access Token (classic)
🌐 Testing URL: https://api.github.com/user
📤 Sending request to GitHub API...
📥 Response received in 245ms
📊 HTTP Status: 200
📋 Response Headers:
   server: GitHub.com
   content-type: application/json; charset=utf-8
✅ GitHub API connection successful!
👤 Authenticated as: your_username
```

#### ❌ **Failed Connection Examples:**

##### Invalid Token:
```
🔑 Token prefix: fake_tok...
📏 Token length: 22 characters
⚠️ Token format: Unknown (expected ghp_ or github_pat_ prefix)
📊 HTTP Status: 401
❌ GitHub API connection failed!
🔐 Error 401: Unauthorized - Token is invalid or expired
   • Check if your token is correct
   • Verify token hasn't expired
   • Ensure token has proper permissions
```

##### Network/SSL Issues:
```
❌ Network error during GitHub API test:
   Error: SSLHandshakeException: PKIX path building failed
   • Check your internet connection
   • Verify GitHub.com is accessible
   • Check if you're behind a firewall/proxy
```

## 🌐 **Step 2: Test Network Connectivity**

If you see SSL or network errors:

```bash
# Linux/Mac
./test-network-connectivity.sh
```

This will test:
- Basic connectivity to GitHub
- HTTPS access to GitHub API
- SSL certificate validation
- Environment information

## 🔍 **Common Issues and Solutions**

### 1. **SSL Certificate Errors**

**Symptoms:**
```
SSLHandshakeException: PKIX path building failed
```

**Causes:**
- Corporate firewall/proxy
- Outdated Java certificates
- Network security policies

**Solutions:**
```bash
# Option 1: Update Java certificates
sudo apt-get update && sudo apt-get install ca-certificates-java

# Option 2: Add Java SSL debugging
java -Djavax.net.debug=ssl -cp "target/classes" GitHubApiClient your_token

# Option 3: Configure proxy (if behind corporate firewall)
export JAVA_OPTS="-Dhttps.proxyHost=your.proxy.com -Dhttps.proxyPort=8080"
```

### 2. **Token Format Issues**

**Valid Token Formats:**
- `ghp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx` (Classic Personal Access Token)
- `github_pat_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx` (Fine-grained Personal Access Token)

**Invalid Examples:**
- `gho_xxxxxxxx` (OAuth token - won't work)
- `ghs_xxxxxxxx` (Server token - won't work)
- Random strings without proper prefix

### 3. **Permission Issues**

**Required Scopes:**
- `repo` (for private repositories)
- `public_repo` (for public repositories)

**Test Repository Access:**
```bash
# Test if token can access the durion repository
curl -H "Authorization: Bearer your_token_here" \
     https://api.github.com/repos/louisburroughs/durion
```

### 4. **Rate Limiting**

**Symptoms:**
```
📊 HTTP Status: 429
⏱️ Error 429: Rate Limited - Too many requests
```

**Solution:**
- Wait for rate limit to reset (usually 1 hour)
- Check rate limit status:
```bash
curl -H "Authorization: Bearer your_token_here" \
     https://api.github.com/rate_limit
```

## 🛠️ **Advanced Debugging**

### Enable Detailed Java Networking Logs:
```bash
java -Djava.net.useSystemProxies=true \
     -Djavax.net.debug=all \
     -cp "target/classes" GitHubApiClient your_token
```

### Test with curl (bypass Java):
```bash
# Test authentication
curl -H "Authorization: Bearer your_token_here" \
     https://api.github.com/user

# Test repository access
curl -H "Authorization: Bearer your_token_here" \
     https://api.github.com/repos/louisburroughs/durion/issues?labels=STORY
```

### Check Java Version:
```bash
java -version
# Ensure you're using Java 11 or later
```

## 📋 **Checklist Before Reporting Issues**

- [ ] Token is valid and not expired
- [ ] Token has correct format (ghp_ or github_pat_ prefix)
- [ ] Token has required permissions (repo scope)
- [ ] Network connectivity to GitHub works
- [ ] SSL certificates are valid
- [ ] Not behind restrictive firewall/proxy
- [ ] Java version is 11 or later
- [ ] Can access GitHub.com in browser

## 🆘 **Getting Help**

If you're still having issues after following this guide:

1. **Run the diagnostic tests:**
   ```bash
   ./test-github-connection.sh your_token > debug_output.txt 2>&1
   ./test-network-connectivity.sh >> debug_output.txt 2>&1
   ```

2. **Share the debug output** (remove your actual token first!)

3. **Include environment information:**
   - Operating system
   - Java version
   - Network setup (corporate/home)
   - Any proxy/firewall information

## 🎯 **Quick Fix Summary**

Most common issue is **SSL certificate problems** in corporate environments:

```bash
# Try this if you get SSL errors:
export JAVA_OPTS="-Dtrust_all_cert=true"
java $JAVA_OPTS -cp "target/classes" ProductionStoryMonitor your_token
```

**⚠️ Warning:** Only use the above in development environments, not production!