# GitHub Branching Strategy

## Overview

This document defines the branch naming conventions, protection rules, and workflow for all Durion projects (durion-moqui-frontend, durion-positivity-backend, durion, workspace-agents).

The strategy uses **Git Flow** adapted for continuous delivery with the following principles:
- `main` is production-ready code (released or releasing)
- `develop` is integration branch for features
- Feature, bugfix, and hotfix branches follow naming conventions
- All changes go through pull requests with required reviews
- CI/CD gates validate before merge

---

## Branch Types & Naming Conventions

### 1. Main Branch: `main`
- **Purpose**: Production-ready code, always deployable
- **Protection Rules**:
  - ✓ Require pull request reviews (min 2 approvals)
  - ✓ Require status checks to pass (CI/CD, tests, linting)
  - ✓ Require branches to be up to date before merge
  - ✓ Dismiss stale pull request approvals when new commits are pushed
  - ✓ No force pushes allowed
- **Tagging**: Release tags in format `v{major}.{minor}.{patch}` (e.g., `v1.2.3`)
- **Access**: Only release manager can merge to main

### 2. Develop Branch: `develop`
- **Purpose**: Integration branch for features, staging for next release
- **Protection Rules**:
  - ✓ Require pull request reviews (min 1 approval)
  - ✓ Require status checks to pass (CI/CD, tests, linting)
  - ✓ Require branches to be up to date before merge
  - ✓ Dismiss stale pull request approvals when new commits are pushed
- **Source**: Created once per repository (never deleted)
- **Merge to main**: When preparing a release (via release PR)

### 3. Feature Branches: `feature/{story-id}-{description}`
- **Format**: `feature/{issue-number}-{kebab-case-description}`
- **Examples**:
  - `feature/123-add-user-authentication`
  - `feature/456-inventory-ledger-atp-calculation`
  - `feature/789-fix-quasar-date-picker`
- **Source**: Branch from `develop`
- **Target**: Merge back to `develop` via pull request
- **Lifecycle**: Delete after merge
- **Naming Rules**:
  - Use issue number from GitHub (e.g., #123)
  - Use kebab-case (hyphens, no spaces or underscores)
  - Keep description concise (< 50 chars recommended)
  - Avoid numbers-only descriptions

### 4. Bugfix Branches: `bugfix/{story-id}-{description}`
- **Format**: `bugfix/{issue-number}-{kebab-case-description}`
- **Examples**:
  - `bugfix/234-fix-inventory-calculation-edge-case`
  - `bugfix/567-resolve-authentication-token-expiry`
- **Source**: Branch from `develop` for bugs targeting next release
- **Target**: Merge back to `develop` via pull request
- **Lifecycle**: Delete after merge
- **Distinction**: Use `bugfix/` for bugs scheduled for next release; use `hotfix/` for production bugs (see below)

### 5. Hotfix Branches: `hotfix/{issue-id}-{description}`
- **Format**: `hotfix/{issue-number}-{kebab-case-description}`
- **Examples**:
  - `hotfix/999-critical-payment-processing-bug`
  - `hotfix/1001-security-vulnerability-patch`
- **Source**: Branch from `main` (production bug)
- **Target**: Merge to both `main` AND `develop`
  - Merge to `main` → release immediately
  - Merge to `develop` → keep develop in sync
- **Lifecycle**: Delete after merge
- **Triggering**: Only for critical bugs in production that require immediate fix
- **Release**: Tag a patch version on `main` (e.g., `v1.2.1`)

### 6. Release Branches: `release/{version}`
- **Format**: `release/v{major}.{minor}.{patch}-rc{n}` or `release/{version}`
- **Examples**:
  - `release/v1.2.0-rc1`
  - `release/v2.0.0`
- **Source**: Branch from `develop` when preparing a release
- **Purpose**: Stabilization, bug fixes, version bumps, release notes
- **Target**: Merge to `main` and back to `develop` after release
- **Lifecycle**: Delete after merge to main
- **Process**:
  1. Create release branch from `develop`
  2. Bump version numbers (package.json, build.gradle, pom.xml)
  3. Generate release notes
  4. Fix critical bugs only (no new features)
  5. Merge to `main` when ready
  6. Tag `main` with version (e.g., `v1.2.0`)
  7. Merge back to `develop` to sync version bumps

### 7. Experimental/Spike Branches: `spike/{description}` or `experiment/{description}`
- **Format**: `spike/{kebab-case-description}` or `experiment/{description}`
- **Examples**:
  - `spike/evaluate-nextjs-migration`
  - `experiment/new-caching-strategy`
- **Source**: Branch from `develop`
- **Target**: Do NOT merge to develop (or merge with caution after review)
- **Purpose**: Exploration, proof-of-concept, research
- **Lifecycle**: Delete or archive (do not keep long-term)
- **Note**: Require explicit approval from architect before merging to develop

---

## Commit Message Standards

Follow **Conventional Commits** format:

```
<type>(<scope>): <subject>

<body>

<footer>
```

### Type
- `feat`: A new feature
- `fix`: A bug fix
- `docs`: Documentation only changes
- `style`: Changes that do not affect the meaning of the code (formatting, semicolons, etc.)
- `refactor`: Code change that neither fixes a bug nor adds a feature
- `perf`: Code change that improves performance
- `test`: Adding or updating tests
- `ci`: CI/CD configuration changes
- `chore`: Build process, dependency updates, tooling

### Scope (Optional but Recommended)
- Component or module being changed (e.g., `inventory`, `auth`, `quasar-dialog`)

### Subject
- Use imperative mood ("add" not "added" or "adds")
- Don't capitalize first letter
- No period at the end
- Limit to 50 characters

### Body (Optional)
- Explain what and why, not how
- Wrap at 72 characters
- Separate from subject with blank line

### Footer (Optional)
- Reference GitHub issues: `Closes #123`, `Fixes #456`
- Note breaking changes: `BREAKING CHANGE: description`

### Examples

```
feat(inventory): add ATP calculation for allocation orders

Implement Available-to-Promise calculation following ADR-0001
specifications. Uses ledger-based approach with daily summation.

Closes #123
```

```
fix(auth): resolve JWT token expiry during long operations

Extend token refresh window from 5 min to 15 min and add
automatic refresh trigger on 401 responses.

Fixes #234
```

```
docs: update branching strategy for hotfix process
```

```
ci: add property-based testing to Maven test phase
```

---

## Pull Request Workflow

### Creating a Pull Request

1. **Branch Creation**: Create feature/bugfix/hotfix branch from appropriate source
2. **Local Work**: Make atomic commits following Conventional Commits format
3. **Push to Remote**: `git push origin feature/123-description`
4. **Open PR**: 
   - Use PR template (if available in `.github/pull_request_template.md`)
   - Link related issues: "Closes #123"
   - Add labels: `domain:*`, `type:*`, `priority:*`
   - Request reviewers (min 2 for main, min 1 for develop)

### PR Title Format
- Follow same format as commit messages where possible
- Link issue in title or description: `#123`
- Examples:
  - `feat(inventory): add ATP calculation for orders (closes #123)`
  - `fix(auth): resolve JWT token expiry bug (fixes #456)`

### PR Description Template
```markdown
## Description
Brief summary of changes

## Related Issues
Closes #123
Fixes #456
Related to #789

## Type of Change
- [ ] Feature
- [ ] Bugfix
- [ ] Hotfix
- [ ] Documentation
- [ ] Breaking Change

## Testing Performed
- [ ] Unit tests pass
- [ ] Integration tests pass
- [ ] Manual testing completed

## Checklist
- [ ] Code follows project style guidelines
- [ ] No hardcoded secrets or credentials
- [ ] Documentation updated
- [ ] Tests added/updated
- [ ] Commits follow Conventional Commits format
```

### Review Requirements

#### For `develop` branch (min 1 approval)
- Code follows project conventions
- Tests are adequate
- No obvious security/performance issues
- Documentation is updated

#### For `main` branch (min 2 approvals)
- All code quality checks pass
- Code review from domain expert
- Release manager approval
- No unresolved comments

### Merge Strategies

- **Default: Squash and Merge**
  - For feature branches: Squash commits into single logical commit
  - Preserves clean commit history on develop/main
  - Use when: Multiple small commits on feature branch

- **Create a Merge Commit**
  - For release branches: Keep full commit history
  - Use when: Want to preserve feature branch history (rare)

- **Rebase and Merge**
  - Use when: Linear history preferred and no conflicts
  - Use when: Feature branch has several clean commits worth preserving

---

## Workflow Examples

### Typical Feature Development

```bash
# 1. Create feature branch from develop
git checkout develop
git pull origin develop
git checkout -b feature/123-add-new-feature

# 2. Make commits (follow Conventional Commits)
git commit -m "feat(inventory): add new item type support"
git commit -m "test(inventory): add tests for new item type"
git commit -m "docs(inventory): update README with new item type"

# 3. Push and create PR
git push origin feature/123-add-new-feature
# Open PR in GitHub, request reviewers

# 4. Address review feedback
git commit -m "refactor(inventory): address PR feedback on item type validation"
git push origin feature/123-add-new-feature

# 5. Merge after approval (squash merge recommended)
# Merge via GitHub UI: Squash and Merge
# Delete branch after merge
git checkout develop
git pull origin develop
```

### Hotfix for Production Bug

```bash
# 1. Create hotfix branch from main
git checkout main
git pull origin main
git checkout -b hotfix/999-critical-bug-fix

# 2. Make targeted fix
git commit -m "fix(payment): critical payment processing bug"

# 3. Push and create PR to main
git push origin hotfix/999-critical-bug-fix
# Open PR to main, request 2 approvals

# 4. Merge to main after approval
# GitHub UI: Create merge commit (preserve history)
# Tag the release
git checkout main
git pull origin main
git tag v1.2.1
git push origin v1.2.1

# 5. Also merge to develop to stay in sync
git checkout develop
git pull origin develop
git merge --no-ff main  # Keep merge commit for history
git push origin develop

# 6. Delete hotfix branch
git push origin --delete hotfix/999-critical-bug-fix
```

### Release Process

```bash
# 1. Create release branch from develop
git checkout develop
git pull origin develop
git checkout -b release/v1.2.0

# 2. Bump versions
# Update package.json, build.gradle, pom.xml, etc.
git commit -m "chore(release): bump to v1.2.0"

# 3. Generate release notes and commit
git commit -m "docs(release): add release notes for v1.2.0"

# 4. Create PR to main for final review
git push origin release/v1.2.0
# Open PR to main, request release manager approval

# 5. Merge to main (create merge commit)
# GitHub UI: Create merge commit
git checkout main
git pull origin main

# 6. Tag the release
git tag v1.2.0
git push origin v1.2.0

# 7. Merge back to develop (keep version bumps in sync)
git checkout develop
git pull origin develop
git merge --no-ff main
git push origin develop

# 8. Delete release branch
git push origin --delete release/v1.2.0
```

---

## Branch Protection Rules Configuration

### For `main` Branch

```yaml
required_status_checks:
  - context: "Build & Tests"
    strict: true
  - context: "Linting"
    strict: true
  - context: "Security Scan"
    strict: true

required_pull_request_reviews:
  required_approving_review_count: 2
  dismiss_stale_reviews: true
  require_code_owner_reviews: false

required_signatures: false
enforce_admins: false
allow_force_pushes: false
allow_deletions: false
```

### For `develop` Branch

```yaml
required_status_checks:
  - context: "Build & Tests"
    strict: true
  - context: "Linting"
    strict: true

required_pull_request_reviews:
  required_approving_review_count: 1
  dismiss_stale_reviews: true
  require_code_owner_reviews: false

required_signatures: false
enforce_admins: false
allow_force_pushes: false
allow_deletions: false
```

---

## Tags & Versioning

### Version Tag Format
- **Production Release**: `v{major}.{minor}.{patch}` (e.g., `v1.2.3`)
- **Release Candidate**: `v{major}.{minor}.{patch}-rc{n}` (e.g., `v1.2.0-rc1`)
- **Development Release**: `v{major}.{minor}.{patch}-dev{n}` (e.g., `v1.3.0-dev5`)

### Semantic Versioning
- **MAJOR**: Breaking changes to APIs or data contracts
- **MINOR**: New feature or functionality, backward-compatible
- **PATCH**: Bug fixes, backward-compatible

### Tag Creation
```bash
# Create and push tag
git tag v1.2.3
git push origin v1.2.3

# Release candidate
git tag v1.2.0-rc1
git push origin v1.2.0-rc1
```

---

## Common Issues & Resolution

### Issue: "Your branch has diverged"
**Cause**: Remote branch changed since you last pulled
**Solution**:
```bash
git fetch origin
git rebase origin/develop  # or main
git push origin feature/123-description -f  # Force push only on feature branches
```

### Issue: "Cannot merge, conflicts exist"
**Solution**:
```bash
git fetch origin
git merge origin/develop
# Resolve conflicts in editor
git add .
git commit -m "merge: resolve conflicts with develop"
git push origin feature/123-description
```

### Issue: "PR title doesn't match Conventional Commits"
**Solution**: Edit PR title in GitHub to match format

### Issue: "Need to undo a commit already pushed"
**Solution** (for feature branches only):
```bash
git revert <commit-hash>  # Safe: creates inverse commit
git push origin feature/123-description
```

---

## References

- [Git Flow Model](https://nvie.com/posts/a-successful-git-branching-model/)
- [Conventional Commits](https://www.conventionalcommits.org/)
- [Semantic Versioning](https://semver.org/)
- [GitHub Branch Protection Rules](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/managing-a-branch-protection-rule)
- [Durion Development Governance](./README.md)
