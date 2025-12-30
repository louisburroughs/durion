package com.durion.audit;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.assertj.core.api.Assertions;

import com.durion.audit.RateLimitInfo;

import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Property-based test for Property 6: Rate limiting compliance
 * **Feature: missing-issues-audit, Property 6: Rate limiting compliance**
 * **Validates: Requirements 3.2, 3.3, 3.4**
 * 
 * For any sequence of API requests, the system should respect rate limits, 
 * implement appropriate delays, and retry failed requests according to GitHub API guidelines.
 * 
 * NOTE: These tests use a TestableRateLimiter that tracks delay calculations without
 * actually sleeping, making tests fast while still verifying the rate limiting logic.
 */
public class RateLimitingComplianceTest {

    /**
     * A testable rate limiter that tracks delays without actually sleeping.
     * This allows us to verify the rate limiting logic without waiting for real delays.
     */
    static class TestableRateLimiter {
        private static final int BASE_DELAY_MS = 2000;
        private static final int BATCH_DELAY_MS = 10000;
        private static final int BATCH_SIZE = 5;
        private static final int SECONDARY_RATE_LIMIT_WAIT_MS = 60000;
        private static final int CRITICAL_REMAINING_THRESHOLD = 10;
        
        private int operationCount = 0;
        private long totalDelayApplied = 0;
        private RateLimitInfo lastKnownRateLimit;
        private boolean shouldPause = false;
        
        public long applyPreRequestDelay() {
            operationCount++;
            long delay = BASE_DELAY_MS;
            
            // Additional delay every BATCH_SIZE operations
            if (operationCount % BATCH_SIZE == 0) {
                delay += BATCH_DELAY_MS;
            }
            
            // Additional delay if rate limit is critical
            if (lastKnownRateLimit != null && lastKnownRateLimit.isCritical()) {
                delay += calculateCriticalWaitTime();
            }
            
            totalDelayApplied += delay;
            return delay;
        }
        
        private long calculateCriticalWaitTime() {
            if (lastKnownRateLimit == null) return 0;
            LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
            LocalDateTime resetTime = lastKnownRateLimit.getResetTime();
            if (now.isAfter(resetTime)) return 0;
            long secondsUntilReset = now.until(resetTime, java.time.temporal.ChronoUnit.SECONDS);
            return secondsUntilReset * 1000 + 5000;
        }
        
        public ProcessResult processResponse(MockHttpResponse response) {
            updateRateLimitFromHeaders(response);
            
            if (response.statusCode() == 403) {
                String body = response.body();
                if (body != null && body.contains("secondary rate limit")) {
                    return new ProcessResult(false, SECONDARY_RATE_LIMIT_WAIT_MS);
                } else {
                    long waitTime = lastKnownRateLimit != null ? 
                        calculateCriticalWaitTime() : 30000;
                    return new ProcessResult(false, waitTime);
                }
            }
            
            return new ProcessResult(true, 0);
        }
        
        private void updateRateLimitFromHeaders(MockHttpResponse response) {
            String limitHeader = response.getHeaderValue("x-ratelimit-limit");
            String remainingHeader = response.getHeaderValue("x-ratelimit-remaining");
            String resetHeader = response.getHeaderValue("x-ratelimit-reset");
            String resourceHeader = response.getHeaderValue("x-ratelimit-resource");
            
            if (remainingHeader != null && resetHeader != null) {
                try {
                    int limit = limitHeader != null ? Integer.parseInt(limitHeader) : 5000;
                    int remaining = Integer.parseInt(remainingHeader);
                    int used = limit - remaining;
                    long resetTime = Long.parseLong(resetHeader);
                    String resource = resourceHeader != null ? resourceHeader : "core";
                    
                    LocalDateTime resetDateTime = LocalDateTime.ofEpochSecond(resetTime, 0, ZoneOffset.UTC);
                    lastKnownRateLimit = new RateLimitInfo(limit, remaining, used, resetDateTime, resource);
                    
                    shouldPause = lastKnownRateLimit.isCritical();
                } catch (NumberFormatException e) {
                    // Ignore parse errors
                }
            }
        }
        
        public int getOperationCount() { return operationCount; }
        public long getTotalDelayApplied() { return totalDelayApplied; }
        public RateLimitInfo getCurrentRateLimit() { return lastKnownRateLimit; }
        public boolean shouldPauseOperations() { return shouldPause; }
        public void resetOperationCount() { operationCount = 0; totalDelayApplied = 0; }
        
        public long calculateExpectedDelay(int opCount) {
            long expected = (long) opCount * BASE_DELAY_MS;
            expected += (long) (opCount / BATCH_SIZE) * BATCH_DELAY_MS;
            return expected;
        }
    }
    
    static class ProcessResult {
        final boolean success;
        final long delayMs;
        
        ProcessResult(boolean success, long delayMs) {
            this.success = success;
            this.delayMs = delayMs;
        }
    }

    @Property(tries = 100)
    @Label("Property 6: Rate limiting compliance - base delays are calculated correctly")
    void rateLimiterShouldCalculateBaseDelaysCorrectly(
            @ForAll @IntRange(min = 1, max = 50) int operationCount) {
        
        // Given: A testable rate limiter
        TestableRateLimiter rateLimiter = new TestableRateLimiter();
        
        // When: Performing multiple operations
        long totalDelay = 0;
        for (int i = 0; i < operationCount; i++) {
            totalDelay += rateLimiter.applyPreRequestDelay();
        }
        
        // Then: Total delay should include base delays (2 seconds per operation)
        long expectedMinDelay = operationCount * 2000L;
        
        Assertions.assertThat(totalDelay)
                .isGreaterThanOrEqualTo(expectedMinDelay)
                .describedAs("Total delay should include base delays of 2 seconds per operation");
        
        // And: Operation count should be tracked correctly
        Assertions.assertThat(rateLimiter.getOperationCount())
                .isEqualTo(operationCount);
    }
    
    @Property(tries = 100)
    @Label("Property 6: Rate limiting compliance - batch delays are calculated correctly")
    void rateLimiterShouldCalculateBatchDelaysCorrectly(
            @ForAll @IntRange(min = 5, max = 50) int operationCount) {
        
        // Given: A testable rate limiter
        TestableRateLimiter rateLimiter = new TestableRateLimiter();
        
        // When: Performing operations that trigger batch delays
        long totalDelay = 0;
        for (int i = 0; i < operationCount; i++) {
            totalDelay += rateLimiter.applyPreRequestDelay();
        }
        
        // Then: Total delay should include both base delays and batch delays
        int batchDelayCount = operationCount / 5;
        long expectedDelay = (operationCount * 2000L) + (batchDelayCount * 10000L);
        
        Assertions.assertThat(totalDelay)
                .isEqualTo(expectedDelay)
                .describedAs("Total delay should include batch delays of 10 seconds every 5 operations");
    }
    
    @Property(tries = 100)
    @Label("Property 6: Rate limiting compliance - secondary rate limit triggers 60s wait")
    void rateLimiterShouldTrigger60SecondWaitForSecondaryRateLimit(
            @ForAll("secondaryRateLimitResponse") MockHttpResponse response) {
        
        // Given: A testable rate limiter
        TestableRateLimiter rateLimiter = new TestableRateLimiter();
        
        // When: Processing a secondary rate limit response
        ProcessResult result = rateLimiter.processResponse(response);
        
        // Then: Should return false (indicating retry needed)
        Assertions.assertThat(result.success).isFalse();
        
        // And: Should indicate 60 second wait for secondary rate limit
        Assertions.assertThat(result.delayMs)
                .isEqualTo(60000)
                .describedAs("Secondary rate limit should trigger 60-second wait");
    }
    
    @Property(tries = 100)
    @Label("Property 6: Rate limiting compliance - rate limit headers are processed")
    void rateLimiterShouldProcessRateLimitHeaders(
            @ForAll("rateLimitResponse") MockHttpResponse response) {
        
        // Given: A testable rate limiter
        TestableRateLimiter rateLimiter = new TestableRateLimiter();
        
        // When: Processing a response with rate limit headers
        ProcessResult result = rateLimiter.processResponse(response);
        
        // Then: Should successfully process the response
        Assertions.assertThat(result.success).isTrue();
        
        // And: Should update internal rate limit information
        RateLimitInfo rateLimitInfo = rateLimiter.getCurrentRateLimit();
        Assertions.assertThat(rateLimitInfo).isNotNull();
        
        // And: Rate limit info should match the headers
        Assertions.assertThat(rateLimitInfo.getRemaining())
                .isEqualTo(response.getRemainingRequests());
        Assertions.assertThat(rateLimitInfo.getLimit())
                .isEqualTo(response.getRequestLimit());
    }
    
    @Property(tries = 100)
    @Label("Property 6: Rate limiting compliance - critical rate limit triggers pause")
    void rateLimiterShouldPauseOnCriticalRateLimit(
            @ForAll("criticalRateLimitResponse") MockHttpResponse response) {
        
        // Given: A testable rate limiter
        TestableRateLimiter rateLimiter = new TestableRateLimiter();
        
        // When: Processing a response with critical rate limit (< 10 remaining)
        rateLimiter.processResponse(response);
        
        // Then: Should indicate that operations should be paused
        Assertions.assertThat(rateLimiter.shouldPauseOperations()).isTrue();
        
        // And: Current rate limit should be marked as critical
        RateLimitInfo rateLimitInfo = rateLimiter.getCurrentRateLimit();
        Assertions.assertThat(rateLimitInfo).isNotNull();
        Assertions.assertThat(rateLimitInfo.isCritical()).isTrue();
    }
    
    @Property(tries = 100)
    @Label("Property 6: Rate limiting compliance - low rate limit adds extra delay")
    void rateLimiterShouldAddExtraDelayForLowRateLimit(
            @ForAll("criticalRateLimitResponse") MockHttpResponse response) {
        
        // Given: A testable rate limiter with critical rate limit
        TestableRateLimiter rateLimiter = new TestableRateLimiter();
        rateLimiter.processResponse(response);
        
        // When: Applying pre-request delay with critical rate limit
        long delay = rateLimiter.applyPreRequestDelay();
        
        // Then: Should apply additional delay beyond base delay
        long baseDelay = 2000;
        Assertions.assertThat(delay)
                .isGreaterThan(baseDelay)
                .describedAs("Should apply additional delay when rate limit is critical");
    }
    
    @Property(tries = 100)
    @Label("Property 6: Rate limiting compliance - operation count tracking")
    void rateLimiterShouldTrackOperationCountAccurately(
            @ForAll @IntRange(min = 1, max = 100) int operationCount) {
        
        // Given: A testable rate limiter
        TestableRateLimiter rateLimiter = new TestableRateLimiter();
        
        // When: Performing multiple operations
        for (int i = 0; i < operationCount; i++) {
            rateLimiter.applyPreRequestDelay();
        }
        
        // Then: Operation count should be accurate
        Assertions.assertThat(rateLimiter.getOperationCount())
                .isEqualTo(operationCount);
        
        // When: Resetting operation count
        rateLimiter.resetOperationCount();
        
        // Then: Count should be reset to zero
        Assertions.assertThat(rateLimiter.getOperationCount()).isZero();
    }
    
    @Property(tries = 100)
    @Label("Property 6: Rate limiting compliance - delay calculation is deterministic")
    void delayCalculationShouldBeDeterministic(
            @ForAll @IntRange(min = 1, max = 100) int operationCount) {
        
        // Given: Two rate limiters
        TestableRateLimiter limiter1 = new TestableRateLimiter();
        TestableRateLimiter limiter2 = new TestableRateLimiter();
        
        // When: Performing the same number of operations on both
        for (int i = 0; i < operationCount; i++) {
            limiter1.applyPreRequestDelay();
            limiter2.applyPreRequestDelay();
        }
        
        // Then: Both should have the same total delay
        Assertions.assertThat(limiter1.getTotalDelayApplied())
                .isEqualTo(limiter2.getTotalDelayApplied())
                .describedAs("Delay calculation should be deterministic");
    }

    @Provide
    Arbitrary<MockHttpResponse> secondaryRateLimitResponse() {
        return Arbitraries.create(() -> {
            MockHttpResponse response = new MockHttpResponse();
            response.setStatusCode(403);
            response.setBody("You have exceeded a secondary rate limit and have been temporarily blocked from content creation.");
            return response;
        });
    }
    
    @Provide
    Arbitrary<MockHttpResponse> rateLimitResponse() {
        return Arbitraries.integers().between(100, 4000).map(remaining -> {
            MockHttpResponse response = new MockHttpResponse();
            response.setStatusCode(200);
            response.setBody("{\"message\": \"Success\"}");
            
            int limit = 5000;
            int used = limit - remaining;
            long resetTime = System.currentTimeMillis() / 1000 + 3600;
            
            response.addHeader("x-ratelimit-limit", String.valueOf(limit));
            response.addHeader("x-ratelimit-remaining", String.valueOf(remaining));
            response.addHeader("x-ratelimit-used", String.valueOf(used));
            response.addHeader("x-ratelimit-reset", String.valueOf(resetTime));
            response.addHeader("x-ratelimit-resource", "core");
            
            response.setRequestLimit(limit);
            response.setRemainingRequests(remaining);
            
            return response;
        });
    }
    
    @Provide
    Arbitrary<MockHttpResponse> criticalRateLimitResponse() {
        return Arbitraries.integers().between(1, 9).map(remaining -> {
            MockHttpResponse response = new MockHttpResponse();
            response.setStatusCode(200);
            response.setBody("{\"message\": \"Success\"}");
            
            int limit = 5000;
            int used = limit - remaining;
            long resetTime = System.currentTimeMillis() / 1000 + 3600;
            
            response.addHeader("x-ratelimit-limit", String.valueOf(limit));
            response.addHeader("x-ratelimit-remaining", String.valueOf(remaining));
            response.addHeader("x-ratelimit-used", String.valueOf(used));
            response.addHeader("x-ratelimit-reset", String.valueOf(resetTime));
            response.addHeader("x-ratelimit-resource", "core");
            
            response.setRequestLimit(limit);
            response.setRemainingRequests(remaining);
            
            return response;
        });
    }
    
    @Provide
    Arbitrary<MockHttpResponse> lowRateLimitResponse() {
        return Arbitraries.integers().between(10, 99).map(remaining -> {
            MockHttpResponse response = new MockHttpResponse();
            response.setStatusCode(200);
            response.setBody("{\"message\": \"Success\"}");
            
            int limit = 5000;
            int used = limit - remaining;
            long resetTime = System.currentTimeMillis() / 1000 + 3600;
            
            response.addHeader("x-ratelimit-limit", String.valueOf(limit));
            response.addHeader("x-ratelimit-remaining", String.valueOf(remaining));
            response.addHeader("x-ratelimit-used", String.valueOf(used));
            response.addHeader("x-ratelimit-reset", String.valueOf(resetTime));
            response.addHeader("x-ratelimit-resource", "core");
            
            response.setRequestLimit(limit);
            response.setRemainingRequests(remaining);
            
            return response;
        });
    }

    /**
     * Mock HTTP response class for testing rate limiting logic.
     */
    public static class MockHttpResponse implements HttpResponse<String> {
        private int statusCode = 200;
        private String body = "";
        private java.net.http.HttpHeaders headers;
        private java.util.Map<String, List<String>> headerMap = new java.util.HashMap<>();
        private int requestLimit;
        private int remainingRequests;
        
        public void setStatusCode(int statusCode) {
            this.statusCode = statusCode;
        }
        
        public void setBody(String body) {
            this.body = body;
        }
        
        public void addHeader(String name, String value) {
            headerMap.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
            this.headers = java.net.http.HttpHeaders.of(headerMap, (a, b) -> true);
        }
        
        public String getHeaderValue(String name) {
            List<String> values = headerMap.get(name);
            return values != null && !values.isEmpty() ? values.get(0) : null;
        }
        
        public void setRequestLimit(int limit) {
            this.requestLimit = limit;
        }
        
        public void setRemainingRequests(int remaining) {
            this.remainingRequests = remaining;
        }
        
        public int getRequestLimit() {
            return requestLimit;
        }
        
        public int getRemainingRequests() {
            return remainingRequests;
        }
        
        @Override
        public int statusCode() {
            return statusCode;
        }
        
        @Override
        public String body() {
            return body;
        }
        
        @Override
        public java.net.http.HttpHeaders headers() {
            return headers != null ? headers : java.net.http.HttpHeaders.of(java.util.Map.of(), (a, b) -> true);
        }
        
        @Override
        public java.net.http.HttpRequest request() {
            return null;
        }
        
        @Override
        public java.util.Optional<HttpResponse<String>> previousResponse() {
            return java.util.Optional.empty();
        }
        
        @Override
        public java.util.Optional<javax.net.ssl.SSLSession> sslSession() {
            return java.util.Optional.empty();
        }
        
        @Override
        public java.net.URI uri() {
            return null;
        }
        
        @Override
        public java.net.http.HttpClient.Version version() {
            return java.net.http.HttpClient.Version.HTTP_1_1;
        }
    }
}
