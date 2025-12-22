# Durion Workspace Agent System - Quick Start Guide

## Overview

The Durion Workspace Agent System provides unified coordination across the durion ecosystem, managing development workflows between positivity (Spring Boot backend) and moqui (frontend) systems through 13 specialized workspace agents.

## Prerequisites

- **Java 21+** (Java 21 recommended)
- **2GB RAM** minimum (4GB recommended)
- **1GB disk space**
- **Root access** for installation
- **Network access** to positivity and moqui systems

## Quick Installation

### 1. Extract and Install

```bash
# Extract the distribution package
tar -xzf workspace-agents-dist.tar.gz
cd workspace-agents-dist

# Run installation (requires root)
sudo ./install.sh
```

### 2. Configure the System

```bash
# Run interactive setup
sudo ./setup.sh
```

The setup will prompt for:
- Workspace name
- Positivity backend URL
- Moqui frontend URL
- Performance settings
- Security configuration

### 3. Start the Service

```bash
# Start the workspace agents
sudo systemctl start durion-workspace-agents

# Enable auto-start on boot
sudo systemctl enable durion-workspace-agents

# Check status
./scripts/status.sh
```

### 4. Verify Installation

```bash
# Run comprehensive health check
./health-check.sh

# Check service status
systemctl status durion-workspace-agents

# Test health endpoint
curl http://localhost:8090/workspace-agents/actuator/health
```

## Basic Usage

### Service Management

```bash
# Start service
sudo systemctl start durion-workspace-agents

# Stop service
sudo systemctl stop durion-workspace-agents

# Restart service
sudo systemctl restart durion-workspace-agents

# Check status
systemctl status durion-workspace-agents
```

### Using Utility Scripts

```bash
# Start with script
./scripts/start.sh

# Stop with script
./scripts/stop.sh

# Restart with script
./scripts/restart.sh

# Check status with script
./scripts/status.sh
```

### Health Monitoring

```bash
# Full health check
./health-check.sh

# Configuration check only
./health-check.sh --config

# Performance check only
./health-check.sh --performance

# Security check only
./health-check.sh --security

# Connectivity check only
./health-check.sh --connectivity
```

### Log Monitoring

```bash
# View live logs
journalctl -u durion-workspace-agents -f

# View recent logs
journalctl -u durion-workspace-agents -n 50

# View logs since specific time
journalctl -u durion-workspace-agents --since "1 hour ago"

# View error logs only
journalctl -u durion-workspace-agents -p err
```

## Configuration

### Main Configuration

Edit `/etc/durion/workspace-agents.yml`:

```yaml
workspace:
  name: "my-workspace"
  projects:
    - name: "positivity"
      url: "http://localhost:8080"
    - name: "moqui"
      url: "http://localhost:8081"

agents:
  response-timeout: 5000ms
  max-concurrent-users: 100
```

### Security Configuration

Edit `/etc/durion/security.yml`:

```yaml
security:
  jwt:
    secret: "your-secure-jwt-secret"
    expiration: 3600
  encryption:
    key: "your-secure-aes-key"
```

**Important**: Set proper file permissions:
```bash
sudo chmod 600 /etc/durion/security.yml
```

### Performance Tuning

Edit `/etc/durion/performance.yml`:

```yaml
performance:
  response-time-target: 5000ms
  availability-target: 99.9
  concurrent-users: 100
  cache:
    enabled: true
    ttl: 300
```

## Workspace Agents

The system includes 13 specialized agents:

### Workspace Coordination Layer
1. **Requirements Decomposition Agent** - Splits business requirements
2. **Full-Stack Integration Agent** - Coordinates across layers
3. **Workspace Architecture Agent** - Enforces consistency
4. **Unified Security Agent** - Manages security
5. **Performance Coordination Agent** - Optimizes performance

### Technology Bridge Layer
6. **API Contract Agent** - Manages API contracts
7. **Data Integration Agent** - Coordinates data flow
8. **Frontend-Backend Bridge Agent** - Three-tier integration

### Operational Coordination Layer
9. **Multi-Project DevOps Agent** - Coordinates deployments
10. **Workspace SRE Agent** - Unified observability
11. **Cross-Project Testing Agent** - Coordinates testing

### Governance and Support Layer
12. **Workflow Coordination Agent** - Development planning
13. **Documentation Coordination Agent** - Documentation sync

## API Endpoints

### Health and Monitoring

```bash
# Health check
curl http://localhost:8090/workspace-agents/actuator/health

# Metrics
curl http://localhost:8090/workspace-agents/actuator/metrics

# Info
curl http://localhost:8090/workspace-agents/actuator/info
```

### Agent Endpoints

```bash
# Requirements decomposition
curl -X POST http://localhost:8090/workspace-agents/api/requirements/decompose \
  -H "Content-Type: application/json" \
  -d '{"requirement": "Create user management system"}'

# Full-stack integration guidance
curl http://localhost:8090/workspace-agents/api/integration/guidance

# Architecture validation
curl -X POST http://localhost:8090/workspace-agents/api/architecture/validate \
  -H "Content-Type: application/json" \
  -d '{"project": "positivity", "changes": [...]}'
```

## Performance Targets

- **Response Time**: <5 seconds for 95% of requests
- **Availability**: 99.9% uptime during business hours
- **Concurrency**: 100 concurrent users supported
- **Scalability**: 50% workspace growth tolerance

## Troubleshooting

### Common Issues

1. **Service won't start**:
   ```bash
   # Check Java version
   java -version
   
   # Check configuration
   ./health-check.sh --config
   
   # View logs
   journalctl -u durion-workspace-agents -n 20
   ```

2. **Health endpoint not responding**:
   ```bash
   # Check if service is running
   systemctl status durion-workspace-agents
   
   # Check port availability
   netstat -tlnp | grep 8090
   
   # Check firewall
   sudo ufw status
   ```

3. **Performance issues**:
   ```bash
   # Check system resources
   free -h
   df -h
   
   # Check performance metrics
   ./health-check.sh --performance
   
   # Tune JVM settings in /etc/durion/performance.yml
   ```

4. **Integration failures**:
   ```bash
   # Test connectivity
   ./health-check.sh --connectivity
   
   # Check project URLs in configuration
   cat /etc/durion/workspace-agents.yml
   ```

### Log Locations

- **Main logs**: `/var/log/durion/workspace-agents.log`
- **Performance logs**: `/var/log/durion/performance.log`
- **Security logs**: `/var/log/durion/security.log`
- **Audit logs**: `/var/log/durion/audit.log`
- **Integration logs**: `/var/log/durion/integration.log`
- **Error logs**: `/var/log/durion/error.log`

### Getting Help

1. **Check documentation**: See `docs/` directory
2. **Run health check**: `./health-check.sh`
3. **View logs**: `journalctl -u durion-workspace-agents -f`
4. **Check configuration**: Files in `/etc/durion/`

## Next Steps

1. **Configure Projects**: Update URLs in `/etc/durion/workspace-agents.yml`
2. **Security Setup**: Generate secure keys in `/etc/durion/security.yml`
3. **Performance Tuning**: Adjust settings in `/etc/durion/performance.yml`
4. **Integration Testing**: Test with your positivity and moqui systems
5. **Monitoring Setup**: Configure alerts and dashboards

For detailed configuration and advanced usage, see:
- `Configuration.md` - Detailed configuration reference
- `Troubleshooting.md` - Comprehensive troubleshooting guide
