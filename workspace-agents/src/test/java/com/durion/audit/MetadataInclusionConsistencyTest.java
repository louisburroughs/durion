package com.durion.audit;

import java.time.LocalDateTime;
import java.util.List;

import org.assertj.core.api.Assertions;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.From;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;

/**
 * Property-based test for Property 16: Metadata inclusion consistency
 * **Feature: missing-issues-audit, Property 16: Metadata inclusion
 * consistency**
 * **Validates: Requirements 6.5**
 * 
 * For any saved report, the file should include accurate timestamps and audit
 * metadata
 * reflecting the execution context.
 */
public class MetadataInclusionConsistencyTest {

        @Property(tries = 100)
        @Label("Property 16: Metadata inclusion consistency")
        void auditResultShouldIncludeConsistentMetadata(
                        @ForAll @IntRange(min = 1, max = 1000) int totalProcessedStories,
                        @ForAll @StringLength(min = 10, max = 50) String githubToken,
                        @ForAll List<@From("missingIssueGenerator") MissingIssue> missingFrontendIssues,
                        @ForAll List<@From("missingIssueGenerator") MissingIssue> missingBackendIssues) {

                // Given: An audit configuration and statistics
                LocalDateTime auditTime = LocalDateTime.now();

                AuditConfiguration config = AuditConfiguration.builder()
                                .githubToken(githubToken)
                                .auditMode(AuditMode.FULL_AUDIT)
                                .build();

                AuditStatistics statistics = new AuditStatistics(
                                totalProcessedStories,
                                25, // total frontend issues
                                20, // total backend issues
                                5, // missing frontend count
                                10, // missing backend count
                                auditTime);

                // When: Creating an audit result
                AuditResult auditResult = new AuditResult(
                                missingFrontendIssues,
                                missingBackendIssues,
                                totalProcessedStories,
                                auditTime,
                                config,
                                statistics);

                // Then: The audit result should contain consistent metadata
                Assertions.assertThat(auditResult.getAuditTimestamp()).isEqualTo(auditTime);
                Assertions.assertThat(auditResult.getConfiguration()).isEqualTo(config);
                Assertions.assertThat(auditResult.getStatistics()).isEqualTo(statistics);
                Assertions.assertThat(auditResult.getTotalProcessedStories()).isEqualTo(totalProcessedStories);

                // And: The metadata should be internally consistent
                Assertions.assertThat(auditResult.getTotalMissingIssues())
                                .isEqualTo(missingFrontendIssues.size() + missingBackendIssues.size());

                // And: The audit timestamp should be reasonable (not in the future, not too
                // old)
                LocalDateTime now = LocalDateTime.now();
                Assertions.assertThat(auditResult.getAuditTimestamp())
                                .isBefore(now.plusSeconds(1))
                                .isAfter(now.minusHours(1));

                // And: The configuration should be preserved exactly
                Assertions.assertThat(auditResult.getConfiguration().getGithubToken()).isEqualTo(githubToken);
                Assertions.assertThat(auditResult.getConfiguration().getAuditMode()).isEqualTo(AuditMode.FULL_AUDIT);
        }

        @Provide
        Arbitrary<MissingIssue> missingIssueGenerator() {
                return Combinators.combine(
                                Arbitraries.integers().between(1, 1000),
                                Arbitraries.strings().withCharRange('a', 'z').ofMinLength(5).ofMaxLength(100),
                                Arbitraries.strings().filter(s -> s.startsWith("https://github.com/")),
                                Arbitraries.of("frontend", "backend"),
                                Arbitraries.strings().filter(s -> s.contains("/")),
                                Arbitraries.strings().withCharRange('a', 'z').ofMinLength(5).ofMaxLength(120))
                                .as((storyNumber, storyTitle, storyUrl, repositoryType, targetRepository,
                                                expectedTitle) -> new MissingIssue(storyNumber, storyTitle, storyUrl,
                                                                repositoryType, targetRepository, expectedTitle));
        }
}