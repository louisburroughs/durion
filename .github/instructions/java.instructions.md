---
name: 'Java Development Guidelines'
description: 'Guidelines for building Java base applications'
applyTo: '**/*.java'
---

# Java Development

## General Instructions

- Follow standard Java conventions as outlined in the [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html).

## Best practices

- **Records**: For classes primarily intended to store data (e.g., DTOs, immutable data structures), **Java Records should be used instead of traditional classes**.
- **Pattern Matching**: Utilize pattern matching for `instanceof` and `switch` expression to simplify conditional logic and type casting.
- **Type Inference**: Use `var` for local variable declarations to improve readability, but only when the type is explicitly clear from the right-hand side of the expression.
- **Immutability**: Favor immutable objects. Make classes and fields `final` where possible. Use collections from `List.of()`/`Map.of()` for fixed data. Use `Stream.toList()` to create immutable lists.
- **Streams and Lambdas**: Use the Streams API and lambda expressions for collection processing. Employ method references (e.g., `stream.map(Foo::toBar)`).
- **Null Handling**: Avoid returning or accepting `null`. Use `Optional<T>` for possibly-absent values and `Objects` utility methods like `equals()` and `requireNonNull()`.
- **Exception Handling**: Use checked exceptions for recoverable conditions and runtime exceptions for programming errors. Always provide meaningful messages when throwing exceptions.
- **Dependency Management**: Use Maven or Gradle for managing project dependencies. Keep dependencies up-to-date and avoid unnecessary dependencies.
- **Logging**: Use a logging framework like SLF4J with Logback or Log4j2. Log at appropriate levels (DEBUG, INFO, WARN, ERROR) and avoid logging sensitive information.
- **Enums**: Use enums for fixed sets of constants instead of `int` or `String` constants. Enums provide type safety and can have methods and fields.


# Java Date/Time Agent Policy (Java 21)

## ✅ Required API
- **MUST** use `java.time` only (`Instant`, `ZonedDateTime`, `OffsetDateTime`, `LocalDate`, `Duration`, `Period`, `ZoneId`, `Clock`).
- **MUST NOT** use legacy date/time APIs (`java.util.Date`, `java.util.Calendar`, `java.sql.*`) except at strict integration boundaries (e.g., JDBC).

## 🕒 “Now” and time control
- **MUST NOT** call `Instant.now()` / `ZonedDateTime.now()` directly in business logic.
- **MUST** inject `Clock` (or `TimeProvider`) and obtain time via `Instant.now(clock)`.

## 🌍 Time zone rules
- **MUST** make zone explicit for any local-time computation (`ZoneId` required).
- **MUST NOT** assume `ZoneId.systemDefault()` unless explicitly documented and intended.
- **MUST** store / compute canonical timestamps in **UTC** using `Instant`.

## 🧱 Type selection
- Use **`Instant`** for audit/event timestamps and ordering.
- Use **`ZonedDateTime`** for user-facing times where DST/zone rules matter.
- Use **`LocalDate`** for business dates (due date, billing date).
- **Avoid `LocalDateTime`** unless value is explicitly “floating” and is paired with a `ZoneId` at the boundary.

## 💾 Persistence & serialization
- **MUST** store timestamps as `Instant` in UTC (or DB `TIMESTAMP WITH TIME ZONE`).
- **MUST** serialize API timestamps as **RFC3339/ISO-8601** (e.g., `2026-01-26T14:35:12Z`).
- **MUST** keep timestamp precision consistent (seconds/millis/nanos) across the system.

## ➕ Arithmetic & comparisons
- **MUST** compare timestamps using `Instant`.
- **MUST** use `Duration` for elapsed time; `Period` for calendar amounts.
- **MUST NOT** model calendar days as `Duration.ofHours(24)`; use `LocalDate` + `ZoneId`.
- Prefer interval semantics **[start, end)** for time ranges.

## 🧾 Formatting/parsing
- **MUST** use `DateTimeFormatter` (never manual formatting).
- **MUST** include explicit locale and zone for UI formatting.
- **MUST** reject ambiguous timestamps during parsing.

## 🧪 Testing
- **MUST** use `Clock.fixed(...)` or `Clock.offset(...)` in tests.
- **MUST** include DST boundary tests when logic depends on local time.

## Java 21 Switch Policy

- **MUST** prefer **switch expressions** over switch statements when returning a value:
  - Use `var result = switch (x) { ... };`
- **MUST** use **arrow labels (`->`)** (no fall-through) unless fall-through is explicitly required and commented.
- **MUST** include a `default` branch for non-exhaustive inputs; omit `default` only when switching over a **sealed type** or **complete enum** and all cases are covered.
- **MUST** use `yield` only inside `{ ... }` blocks of switch expressions when multiple statements are required.
- **SHOULD** use pattern matching in switch (Java 21) to simplify type checks:
  - `case Foo f -> ...`
  - `case null -> ...` when null is a valid input; otherwise fail fast before the switch.
- **MUST NOT** nest complex logic inside switch arms; delegate to helper methods.

## Java 21 Annotation-First Policy (Reduce Boilerplate)

- **MUST** prefer proven annotation-based libraries/framework features over hand-written boilerplate (getters/setters, builders, mappers, validators), provided they are already approved in the project stack (Lombok is a common example).
- **MUST NOT** introduce new annotation processors/frameworks without explicit approval (e.g., Lombok/MapStruct) — use only what the repo already uses.
- **MUST** use annotations for cross-cutting concerns (validation, serialization, DI, transactions, auditing) instead of duplicated code.
- **MUST** keep behavior explicit: annotations may replace boilerplate, but must not hide core business logic.
- **MUST** ensure annotations are testable and observable (e.g., validation errors surfaced, JSON fields deterministic).
- **SHOULD** use `@Override`, `@Nullable/@NotNull`, and validation annotations (`@NotBlank`, `@Size`, etc.) to document intent instead of defensive boilerplate.
- **MUST** avoid annotation overuse: do not stack annotations that make control flow unclear or debugging difficult.


### Naming Conventions

- Follow Google's Java style guide:
  - `UpperCamelCase` for class and interface names.
  - `lowerCamelCase` for method and variable names.
  - `UPPER_SNAKE_CASE` for constants.
  - `lowercase` for package names.
- Use nouns for classes (`UserService`) and verbs for methods (`getUserById`).
- Avoid abbreviations and Hungarian notation.
- Variable and method names should be descriptive and convey intent.

### Common Bug Patterns

Below are concise, human-readable rules you can apply regardless of which static analysis tool you use. If you run Sonar/SonarLint, the IDE will show the matching rule and location — direct Sonar connections are preferred and should override this ruleset.

- Resource management — Always close resources (files, sockets, streams). Use try-with-resources where possible so resources are closed automatically.
- Equality checks — Compare object equality with `.equals()` or `Objects.equals(...)` rather than `==` for non-primitives; this avoids reference-equality bugs.
- Redundant casts — Remove unnecessary casts; prefer correct generic typing and let the compiler infer types where possible.
- Reachable conditions — Avoid conditional expressions that are always true or false; they indicate bugs or dead code and should be corrected.

For contributors who *do* use Sonar or SonarLint: the IDE/scan will show the specific rule key (for example, S2095 for resource leaks) and the affected file/line. Use that information to navigate to the exact location, then apply the recommended remediation.

### Common Code Smells

These patterns are phrased for humans; they map cleanly to checks in Sonar, SpotBugs, PMD, or Checkstyle but do not require those tools to be useful.

- Parameter count — Keep method parameter lists short. If a method needs many params, consider grouping into a value object or using the builder pattern.
- Method size — Keep methods focused and small. Extract helper methods to improve readability and testability.
- Cognitive complexity — Reduce nested conditionals and heavy branching by extracting methods, using polymorphism, or applying the Strategy pattern.
- Duplicated literals — Extract repeated strings and numbers into named constants or enums to reduce errors and ease changes.
- Dead code — Remove unused variables and assignments. They confuse readers and can hide bugs.
- Magic numbers — Replace numeric literals with named constants that explain intent (e.g., MAX_RETRIES).

If you run a static analyzer like Sonar or SonarLint — direct Sonar connections are preferred and should override this ruleset. Sonar rule keys are useful for automation and suppression, but they are not required in day-to-day developer guidance.

## Build and Verification

- After adding or modifying code, verify the project continues to build successfully.
- If the project uses Maven, run `mvnw clean install`.
- If the project uses Gradle, run `./gradlew build` (or `gradlew.bat build` on Windows).
- Ensure all tests pass as part of the build.