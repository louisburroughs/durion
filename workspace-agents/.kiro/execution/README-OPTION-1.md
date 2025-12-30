# 🚀 Option 1: Automatic Story Processing - Choose Your Mode

## 🎯 **Two Modes Available**

### 🧪 **Demo Mode (Simulation)**
- **Purpose**: Testing and demonstration
- **GitHub**: Simulated (no real API calls)
- **Files**: Demo files only
- **Issues**: Simulated URLs
- **Use**: Learning and testing

### 🚀 **Production Mode (Real Integration)**
- **Purpose**: Actual story processing
- **GitHub**: Real API integration
- **Files**: Real coordination documents
- **Issues**: Real GitHub issues created
- **Use**: Production development workflow

---

## 🧪 **Demo Mode - Quick Start**

**Linux/Mac:**
```bash
cd workspace-agents
./start-story-monitoring.sh
```

**Windows:**
```cmd
cd workspace-agents
start-story-monitoring.bat
```

**What it does:**
- ✅ Demonstrates the workflow
- ✅ Shows console output
- ✅ Creates demo coordination files
- ❌ No real GitHub integration
- ❌ No real issues created

---

## 🚀 **Production Mode - Real Integration**

### **Setup Required:**
1. **Get GitHub Token** (with `repo` permissions)
2. **Set Environment Variable**: `export GITHUB_TOKEN=your_token`
3. **Run Production Monitor**

**Linux/Mac:**
```bash
export GITHUB_TOKEN=your_github_token_here
cd workspace-agents
./start-production-monitoring.sh
```

**Windows:**
```cmd
set GITHUB_TOKEN=your_github_token_here
cd workspace-agents
start-production-monitoring.bat
```

**What it does:**
- ✅ Real GitHub API integration
- ✅ Monitors actual repositories
- ✅ Creates real coordination files
- ✅ Creates real implementation issues
- ✅ Full production workflow

---

## 📖 **Complete Documentation**

- **Demo Mode**: [README-OPTION-1.md](README-OPTION-1.md)
- **Production Mode**: [PRODUCTION-SETUP-GUIDE.md](PRODUCTION-SETUP-GUIDE.md)

## 🎯 **Which Mode Should You Use?**

- **Learning/Testing**: Use Demo Mode
- **Actual Development**: Use Production Mode with GitHub token

---

**🎊 Both modes are ready to use - choose based on your needs!**