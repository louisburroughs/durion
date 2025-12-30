# Design Document

## Overview

The Missing Issues Audit System is a Java-based command-line tool that identifies and reports missing implementation issues in the Durion project's frontend and backend repositories. The system compares the master processed issues list against actual GitHub implementation issues to detect gaps in the automated story processing workflow.

## Architecture

### System Architecture

The audit system follows a layered architecture with clear separation of concerns:

```
┌─────────────────────────────────────────────────────────────────┐
│                    Missing Issues Audit System                  │
├─────────────────────────────────────────────────────────────────┤
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐ │
│  │   CLI Interface │  │  Report Manager │  │  Issue Creator  │ │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘ │
├─────────────────────────────────────────────────────────────────┤
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐ │
│  │  Audit Engine   │  │  Cache Manager  │  │  Progress       │ │
│  │                 │  │                 │  │  Tracker        │ │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘ │
├─────────────────────────────────────────────────────────────────┤
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐ │
│  │ GitHub API      │  │ File System     │  │ Rate Limiter    │ │
│  │ Client (SSL)    │  │ Manager         │  │                 │ │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

## Components and Interfaces

### Core Components

#### 1. MissingIssuesAuditSystem (Main Class)
```java
public class MissingIssuesAuditSystem {
    private final GitHubApiClientSSLBypass githubClient;
    private final AuditEngine auditEngine;
    private final ReportManager reportManager;
    
    public void runAudit(AuditConfiguration config);
    public void createMissingIssues(String reportPath);
}
```

#### 2. AuditEngine
```java
public class AuditEngine {
    public AuditResult performAudit(List<Integer> processedIssues, 
                                   List<GitHubIssue> frontendIssues,
                                   List<GitHubIssue> backendIssues,
                                   Map<Integer, StoryMetadata> storyMetadata);
}
```

#### 3. StoryMetadataParser
```java
public class StoryMetadataParser {
    public Map<Integer, StoryMetadata> parseCoordinationFiles();
    public Map<Integer, StoryMetadata> parseFrontendCoordination();
    public Map<Integer, StoryMetadata> parseBackendCoordination();
    // NOTE: story-sequence.md is NOT used - different format, limited scope
}
```

## Data Models

### Repository Structure

Based on the existing orchestration structure:

```
.github/orchestration/
├── processed-issues.txt              # Master list of processed story numbers (one per line)
├── frontend-coordination.md          # Comprehensive list of stories ready for frontend development
├── backend-coordination.md           # Comprehensive list of stories ready for backend development  
├── story-sequence.md                 # NOT USED - Different format, limited scope, not relevant for audit
├── missing-issues/                   # Audit reports directory (created by audit system)
│   ├── audit-{timestamp}.json       # Detailed audit results
│   ├── missing-frontend-{timestamp}.csv  # Missing frontend issues
│   ├── missing-backend-{timestamp}.csv   # Missing backend issues
│   └── summary-{timestamp}.md        # Human-readable summary
└── audit-cache/                      # Query result cache (created by audit system)
    ├── frontend-issues-{date}.json   # Cached frontend issues
    └── backend-issues-{date}.json    # Cached backend issues
```

### Existing File Analysis

The audit system will work with these existing files:

- **processed-issues.txt**: Contains 206 processed story numbers (273 down to 12) - the authoritative list
- **frontend-coordination.md**: Contains detailed story information with titles for all 206 processed stories ready for frontend development
- **backend-coordination.md**: Contains detailed story information with titles for all 206 processed stories ready for backend development
- **story-sequence.md**: NOT USED - Contains different format with limited scope (only 4 stories), not relevant for audit processing

### Expected Implementation Issue Format

Based on the existing system, implementation issues should follow this pattern:
- **Frontend**: `[FRONTEND] [STORY] {Original Story Title}`
- **Backend**: `[BACKEND] [STORY] {Original Story Title}`
- **Repository**: `louisburroughs/durion-moqui-frontend` and `louisburroughs/durion-positivity-backend`
### Data Models

#### MissingIssue
```java
public class StoryMetadata {
    private final int storyNumber;
    private final String storyTitle;
    private final String storyUrl; // Constructed from story number
    private final boolean readyForFrontend;
    private final boolean readyForBackend;
}
```
```java
public class MissingIssue {
    private final int storyNumber;
    private final String storyTitle;
    private final String storyUrl;
    private final String repositoryType; // "frontend" or "backend"
    private final String targetRepository;
    private final String expectedTitle;
}
```

#### AuditResult
```java
public class AuditResult {
    private final List<MissingIssue> missingFrontendIssues;
    private final List<MissingIssue> missingBackendIssues;
    private final int totalProcessedStories;
    private final LocalDateTime auditTimestamp;
}
```

### File Formats

#### processed-issues.txt Format
```
273
272
271
270
...
12
```
(One issue number per line, currently contains 206 processed stories)

#### Story Information Source
The system extracts story details from the coordination files which contain comprehensive story information:

**frontend-coordination.md and backend-coordination.md format:**
```markdown
- ✅ **Story #273**: [STORY] Security: Audit Trail for Price Overrides, Refunds, and Cancellations
- ✅ **Story #272**: [STORY] Security: Define POS Roles and Permission Matrix
- ✅ **Story #271**: [STORY] Customer: Enforce PO Requirement and Billing Rules During Checkout
...
```

The coordination files provide:
- Story number and title for all 206 processed stories
- Consistent formatting with checkmarks indicating processing status
- Complete coverage of all stories listed in processed-issues.txt

#### Missing Issues Report Format (CSV)
```csv
Story Number,Story Title,Story URL,Expected Title,Target Repository
273,"Security: Audit Trail for Price Overrides, Refunds, and Cancellations","https://github.com/louisburroughs/durion/issues/273","[FRONTEND] Security: Audit Trail for Price Overrides, Refunds, and Cancellations","louisburroughs/durion-moqui-frontend"
```

## Error Handling

### Rate Limiting Strategy

Based on the existing secondary rate limit solution, the audit system implements:

#### Primary Rate Limit Management
- Monitor GitHub API rate limit headers in responses
- Track remaining requests and reset times
- Implement delays when approaching limits (< 100 requests remaining)

#### Secondary Rate Limit Handling
- Detect secondary rate limit errors (HTTP 403 with "secondary rate limit" message)
- Implement 60-second wait period when secondary rate limits are hit
- Retry failed requests once after waiting
- Continue processing remaining items even if some operations fail

#### Processing Delays
- **2 seconds** between individual repository queries
- **10 seconds** additional delay every 5 operations
- **60 seconds** wait when secondary rate limit is encountered
- Configurable batch sizes for rate limit checks (default: every 5 operations)

#### Error Handling Patterns
```java
if (response.statusCode() == 403 && response.body().contains("secondary rate limit")) {
    System.out.println("⚠️ Hit secondary rate limit - waiting 60 seconds before retry...");
    Thread.sleep(60000);
    // Retry once after waiting
}
```

### SSL Bypass Integration

The audit system uses the existing SSL bypass infrastructure for corporate environments:

#### SSL Certificate Handling
- Bypasses SSL certificate validation for development/corporate environments
- Uses `GitHubApiClientSSLBypass` class from existing infrastructure
- Maintains full GitHub API functionality while working around certificate issues
- Provides clear warnings about SSL bypass usage

#### Corporate Environment Compatibility
- Works in environments with proxy servers or custom certificates
- Handles common corporate SSL certificate validation issues
- Maintains security through token-based authentication while bypassing certificate validation
- Follows established patterns from production story monitor

#### Implementation Pattern
```java
// Use existing SSL bypass client
private final GitHubApiClientSSLBypass githubClient;

public MissingIssuesAuditSystem(String githubToken) {
    this.githubClient = new GitHubApiClientSSLBypass(githubToken);
}
```

## Testing Strategy

### Unit Testing Approach

The system will use JUnit 5 for comprehensive unit testing with the following focus areas:

1. **Core Logic Testing**: Audit engine comparison algorithms
2. **Data Model Testing**: Serialization/deserialization of audit results
3. **Error Handling Testing**: Exception scenarios and recovery mechanisms
4. **Configuration Testing**: Command-line argument parsing and validation

### Integration Testing

1. **GitHub API Integration**: Mock GitHub API responses for consistent testing
2. **File System Integration**: Temporary directories for file operations testing
3. **End-to-End Workflows**: Complete audit cycles with test data

### Property-Based Testing

Property-based tests will be implemented using jqwik framework to verify:

1. **Audit Correctness**: Universal properties about missing issue detection
2. **Data Consistency**: Round-trip properties for serialization
3. **Rate Limiting**: Properties about API call timing and limits
## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system-essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property Reflection

After analyzing all acceptance criteria, several properties can be consolidated to eliminate redundancy:

- Properties 1.3 and 1.4 (missing frontend/backend detection) can be combined into a single comprehensive missing issue detection property
- Properties 2.1-2.5 (report content requirements) can be consolidated into a comprehensive report completeness property  
- Properties 3.2-3.4 (rate limiting behaviors) can be combined into a single rate limiting compliance property
- Properties 4.2-4.3 (issue creation format) can be merged into a single issue creation consistency property
- Properties 6.1, 6.2, 6.4 (logging requirements) can be consolidated into a comprehensive logging property

### Core Properties

**Property 1: File parsing consistency**
*For any* valid processed-issues.txt file format, the system should successfully parse and return the correct list of issue numbers
**Validates: Requirements 1.1**

**Property 2: Repository search accuracy**
*For any* repository with implementation issues, searching with [FRONTEND] or [BACKEND] patterns should return only issues matching those exact patterns
**Validates: Requirements 1.2**

**Property 3: Missing issue detection completeness**
*For any* set of processed issues and implementation issues, the system should identify all and only those processed issues that lack corresponding implementation issues in both repositories
**Validates: Requirements 1.3, 1.4**

**Property 4: Report content completeness**
*For any* set of missing issues, generated reports should contain all required fields: story number, title, URL, expected title format, and target repository
**Validates: Requirements 1.5, 2.1, 2.2, 2.3, 2.4**

**Property 5: Report output location consistency**
*For any* audit execution, all generated reports should be saved to the .github/orchestration/missing-issues/ directory with proper timestamps
**Validates: Requirements 2.5**

**Property 6: Rate limiting compliance**
*For any* sequence of API requests, the system should respect rate limits, implement appropriate delays, and retry failed requests according to GitHub API guidelines
**Validates: Requirements 3.2, 3.3, 3.4**

**Property 7: Issue creation format consistency**
*For any* missing issue, when automatically created, the new issue should use the same title and body format as the original processing system and include appropriate labels
**Validates: Requirements 4.2, 4.3**

**Property 8: Issue creation resilience**
*For any* issue creation operation, the system should handle rate limits and failures gracefully, continuing with remaining operations
**Validates: Requirements 4.4**

**Property 9: Report update consistency**
*For any* completed issue creation batch, audit reports should be updated to reflect the newly created issues with accurate status changes
**Validates: Requirements 4.5**

**Property 10: Date filtering accuracy**
*For any* specified date range (last N days), the audit should process only stories that were processed within that timeframe
**Validates: Requirements 5.1**

**Property 11: Range filtering accuracy**
*For any* specified story number range, the audit should process only stories with numbers within that range (inclusive)
**Validates: Requirements 5.2**

**Property 12: Caching effectiveness**
*For any* repeated repository query within the same audit session, the system should use cached results instead of making duplicate API calls
**Validates: Requirements 5.3**

**Property 13: Audit resumption consistency**
*For any* interrupted audit operation, the system should be able to resume from the last completed checkpoint without duplicating work
**Validates: Requirements 5.5**

**Property 14: Comprehensive logging**
*For any* audit execution, the system should log all API requests, errors with context, and issue creation attempts with sufficient detail for debugging
**Validates: Requirements 6.1, 6.2, 6.4**

**Property 15: Summary report accuracy**
*For any* completed audit, generated summary reports should contain accurate statistics matching the actual audit results
**Validates: Requirements 6.3**

**Property 16: Metadata inclusion consistency**
*For any* saved report, the file should include accurate timestamps and audit metadata reflecting the execution context
**Validates: Requirements 6.5**

### Integration with Existing Infrastructure

The audit system leverages the existing GitHub API infrastructure:

#### SSL Bypass Client Integration
- Uses `GitHubApiClientSSLBypass` for corporate environment compatibility
- Inherits existing rate limiting and retry logic from production story monitor
- Follows established patterns for secondary rate limit handling

#### Repository Configuration
- **Story Repository**: `louisburroughs/durion` (source of truth for stories)
- **Frontend Repository**: `louisburroughs/durion-moqui-frontend` 
- **Backend Repository**: `louisburroughs/durion-positivity-backend`

#### Story Classification
Stories are classified by their processing status (extracted from coordination files):
- **Frontend Ready**: Stories listed in frontend-coordination.md (206 stories)
- **Backend Ready**: Stories listed in backend-coordination.md (206 stories)
- **Processed**: Stories listed in processed-issues.txt (206 stories)

All current processed stories are marked as ready for both frontend and backend development.

#### Issue Creation Template Integration

Based on the existing issue creation patterns, the audit system should use the same template and labeling approach:

##### Issue Template Format
The system uses the existing `.github/kiro-story.md` template structure:
```markdown
## 🤖 Implementation Issue - Created by Durion Workspace Agent

### Original Story
**Story**: #{storyNumber} - {storyTitle}
**URL**: {storyUrl}

### Implementation Requirements
{Requirements decomposition from original story}

### Notes for Agents
- This issue was created automatically by the Missing Issues Audit System
- Original story processing may have failed due to rate limits or network issues
- Ensure this implementation aligns with the original story requirements

### Labels Applied
- `type:story` - Indicates this is a story implementation
- `layer:functional` - Functional layer implementation
- `kiro` - Created by Kiro automation
- `story-implementation` - Implementation of a story issue
```

##### Label Application Strategy
- **Standard Labels**: `type:story`, `layer:functional`, `kiro`
- **Implementation Labels**: `story-implementation`, `frontend`/`backend`
- **Priority Labels**: Based on story dependencies and processing order
### File System Error Handling

1. **Missing Files**: Graceful handling when processed-issues.txt is not found
2. **Permission Errors**: Clear error messages for file access issues  
3. **Disk Space**: Validation before writing large report files
4. **Concurrent Access**: File locking to prevent corruption during writes

### Operational Considerations

#### Performance Expectations
- **206 processed stories** (current volume from processed-issues.txt)
- **Estimated audit time**: 15-30 minutes with proper rate limiting
- **API calls**: ~412 repository queries (2 per story) plus pagination
- **Report generation**: < 1 minute for file operations
- **Story metadata parsing**: Coordination files contain all 206 stories with titles

#### Scalability
- Caching reduces API calls for repeated audits
- Incremental mode supports efficient re-audits
- Batch processing with configurable sizes
- Progress tracking for long-running operations

## Usage Examples

### Command Line Interface

#### Basic Audit
```bash
# Using environment variable
export GITHUB_TOKEN=ghp_your_token_here
java -jar missing-issues-audit.jar --audit

# Using command line argument
java -jar missing-issues-audit.jar --token ghp_your_token_here --audit
```

#### Incremental Audit (Last 7 Days)
```bash
java -jar missing-issues-audit.jar --token ghp_your_token_here --audit --days 7
```

#### Range-Based Audit
```bash
java -jar missing-issues-audit.jar --token ghp_your_token_here --audit --range 200-273
```

#### Create Missing Issues
```bash
# Audit and create missing issues
java -jar missing-issues-audit.jar --token ghp_your_token_here --audit --create-issues

# Create issues from existing report
java -jar missing-issues-audit.jar --token ghp_your_token_here --create-from-report .github/orchestration/missing-issues/audit-2024-12-25.json
```

### GitHub Token Requirements

#### Token Permissions
The GitHub token must have the following scopes:
- `repo` - Full repository access (required for reading issues and creating new ones)
- `read:org` - Read organization membership (if repositories are in an organization)

#### Token Format
- **Classic Personal Access Token**: `ghp_` prefix, 40 characters total
- **Fine-grained Personal Access Token**: `github_pat_` prefix, variable length

#### Token Validation
The system validates:
1. Token format matches expected patterns
2. Token has required repository permissions
3. Token can access the specified repositories
4. Token is not expired or revoked