---
name: "Code Exemplars Blueprint Generator"
description: "Technology-agnostic prompt generator that creates customizable AI prompts for scanning codebases and identifying high-quality code exemplars. Supports multiple programming languages with configurable analysis depth, categorization methods, and documentation formats to establish coding standards and maintain consistency."
agent: agent
model: "GPT-5 mini (copilot)"
---

# Code Exemplars Blueprint Generator

## Configuration Variables

Provide values for these variables when invoking the generator:

```text
PROJECT_TYPE="Auto-detect|Java|JavaScript|TypeScript|React|Angular|Vue|Quasar|Python|Other"  # Primary technology
SCAN_DEPTH="Basic|Standard|Comprehensive"                                                    # Analysis depth
INCLUDE_CODE_SNIPPETS=true|false                                                              # Include code snippets in exemplars
CATEGORIZATION="Pattern Type|Architecture Layer|File Type"                                  # Organize exemplars by this scheme
MAX_EXAMPLES_PER_CATEGORY=3                                                                   # Max examples per category
INCLUDE_COMMENTS=true|false                                                                   # Include explanatory comments for each exemplar
```

---

## Generated Prompt (template)

Below is the prompt text the generator will use. Replace the ${VARIABLE} placeholders with real values before running.

```text
Scan this codebase and generate an exemplars.md file that identifies high-quality, representative code examples. The exemplars should demonstrate our coding standards and patterns to help maintain consistency. Use the following approach:

1) Codebase analysis
- If PROJECT_TYPE is "Auto-detect": automatically detect primary languages and frameworks by scanning file extensions and common config files (pom.xml, package.json, pyproject.toml, etc.).
- Otherwise: focus the scan on the specified PROJECT_TYPE (e.g., Java).
- Identify files with high-quality implementation, clear structure, and good documentation.
- Detect commonly used patterns, architecture components, and well-structured implementations.
- Prioritize files that demonstrate best practices for the chosen technology stack.
- Only reference actual files that exist in the codebase — do not invent examples.

2) Exemplar identification criteria
- Readable code with clear naming conventions
- Comprehensive comments and documentation where applicable
- Proper input validation and error handling
- Adherence to architecture and design patterns
- Separation of concerns and single-responsibility
- Efficient implementation without obvious code smells
- Representative of the project's standard approaches

3) Core pattern categories (select by PROJECT_TYPE)
- If .NET detected or requested:
  - Domain models, repository implementations, service layer components, controller patterns, DI examples, middleware, unit-test patterns
- If frontend (JavaScript/TypeScript/React/Angular/Vue):
  - Component structure, state management, API integration, form handling, routing, reusable UI components, unit tests
- If Java or backend services:
  - Entity classes (JPA), service implementations, repository patterns, controller/resource classes, configuration classes, unit tests
- If Python:
  - Class definitions, API routes/views, ORM models, service functions, utility modules, tests

4) Architecture layer exemplars
- Presentation layer: controllers / API endpoints, UI components, DTOs
- Business logic layer: service implementations, orchestration, domain rules
- Data access layer: repositories, data models, query patterns
- Cross-cutting concerns: logging, error handling, auth, validation, metrics

5) Exemplar documentation format
For each exemplar include:
- File path (relative to repository root)
- Brief description of why it is exemplary
- Pattern/component type it represents
- (optional) Key implementation details and coding principles demonstrated
- (optional) A small representative code snippet (if INCLUDE_CODE_SNIPPETS=true)

6) (Optional) When SCAN_DEPTH=="Comprehensive", also produce:
- Consistency patterns across the codebase
- Architecture observations
- Implementation conventions and naming patterns
- Noted anti-patterns to avoid

Output requirements:
- Create `exemplars.md` with:
  1. Introduction explaining purpose
  2. Table of contents linking to categories
  3. Organized sections according to CATEGORIZATION
  4. Up to MAX_EXAMPLES_PER_CATEGORY examples per category
  5. Conclusion with actionable recommendations for maintainers

Important: verify all referenced file paths exist in the repository before including them. Do not include placeholders or hypothetical files.
```

---

## Expected Output

When executed with concrete configuration values, the generator should produce a ready-to-use `exemplars.md` containing verified references to representative source files, concise rationale for each exemplar, and optional code snippets or notes depending on configuration.
