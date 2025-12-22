# Durion Workspace

A comprehensive workspace-level agent framework that provides unified coordination across the durion ecosystem, enabling seamless integration between backend and frontend systems.

## Overview

Durion is a sophisticated agent-based coordination system designed to manage and optimize development workflows across multiple projects in the durion ecosystem. It serves as the central nervous system for coordinating activities between:

- **Positivity**: Spring Boot-based POS backend microservices
- **Moqui Example**: Moqui Framework-based frontend application

## Key Features

- **Cross-Project Coordination**: Unified agent framework for backend-frontend integration
- **Performance Monitoring**: Real-time tracking with 99.9% availability targets
- **Security-First Design**: JWT authentication with AES-256 encryption
- **Scalable Architecture**: Supports 100+ concurrent users and 50% workspace growth
- **Property-Based Testing**: Automated validation of architectural consistency
- **Disaster Recovery**: 4-hour RTO and 1-hour RPO capabilities

## Architecture

The framework is organized into four coordination layers:

### 1. Workspace Coordination Layer

- Full-Stack Integration Agent
- Workspace Architecture Agent
- Unified Security Agent
- Performance Coordination Agent

### 2. Technology Bridge Layer

- API Contract Agent
- Data Integration Agent
- Frontend-Backend Bridge Agent

### 3. Operational Coordination Layer

- Multi-Project DevOps Agent
- Workspace SRE Agent
- Cross-Project Testing Agent
- Disaster Recovery Agent

### 4. Governance and Compliance Layer

- Data Governance Agent
- Documentation Coordination Agent
- Workflow Coordination Agent

## Quick Start

### Prerequisites

- Java 21+
- Gradle 7.0+
- Git

### Setup

1. Clone the repository:

```bash
git clone https://github.com/louisburroughs/durion.git
cd durion
```

2. Build the workspace agents:

```bash
cd .kiro/workspace-agents
./gradlew build
```

3. Run the demo:

```bash
java -cp build/libs/workspace-agents.jar durion.workspace.agents.WorkspaceAgentDemo
```

## Project Structure

```
durion/
├── .kiro/
│   ├── specs/
│   │   └── workspace-agent-structure/
│   │       ├── design.md
│   │       ├── requirements.md
│   │       └── tasks.md
│   └── workspace-agents/
│       ├── src/main/java/durion/workspace/agents/
│       ├── build.gradle
│       ├── README.md
│       └── ...
├── durion.code-workspace
└── README.md (this file)
```

## Performance Requirements

- **Response Time**: 5-second target for 95% of requests
- **Availability**: 99.9% uptime during business hours
- **Concurrency**: 100 concurrent users supported
- **Scalability**: 50% workspace growth tolerance

## Security

- JWT-based authentication across all agent communications
- AES-256 encryption for sensitive data
- Role-based access control (RBAC)
- Comprehensive audit logging

## Testing

The framework includes property-based testing to ensure architectural consistency:

```bash
# Run property tests
cd .kiro/workspace-agents
node test/simple-property-test.js
```

## Integration Points

### Positivity Backend (Spring Boot)

- REST API coordination
- Microservices communication
- AWS Fargate deployment integration

### Moqui Example Frontend

- XML configuration management
- Groovy service integration
- Frontend-backend bridge patterns

## Development

### Building

```bash
cd .kiro/workspace-agents
./gradlew clean build
```

### Testing

```bash
./gradlew test
```

### Code Style

The project follows standard Java conventions with Gradle build tooling.

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests for new functionality
5. Ensure all tests pass
6. Submit a pull request

## License

This project is part of the durion ecosystem. See individual project licenses for details.

## Contact

For questions or support, please refer to the individual project documentation or create an issue in the appropriate repository.

## Related Projects

- [Positivity](https://github.com/louisburroughs/positivity) - POS backend microservices
- [Moqui Example](https://github.com/louisburroughs/moqui_example) - Frontend application
- [Moqui Example Runtime](https://github.com/louisburroughs/moqui_example_runtime) - Runtime environment</content>
<parameter name="filePath">/home/n541342/IdeaProjects/durion/README.md