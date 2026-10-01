// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.networking;

import com.github.istin.dmtools.common.networking.RestClient;
import com.github.istin.dmtools.common.utils.PropertyReader;
import okhttp3.Headers;
import okhttp3.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RetryPolicyTest {

    private static final Logger logger = LogManager.getLogger(RetryPolicyTest.class);
    private RetryPolicy retryPolicy;

    @Mock
    private Response mockResponse;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        retryPolicy = new RetryPolicy(logger);
    }

    @Test
    @DisplayName("Should identify rate limit exceptions as retryable")
    void testIsRetryableForRateLimitException() {
        RestClient.RateLimitException rateLimitException =
            new RestClient.RateLimitException("rate limit", "429 Too Many Requests", mockResponse, 429);

        assertTrue(retryPolicy.isRetryable(rateLimitException));
    }

    @Test
    @DisplayName("Should identify rate limit error messages as retryable")
    void testIsRetryableForRateLimitMessages() {
        IOException e1 = new IOException("Error 429: Too Many Requests");
        IOException e2 = new IOException("You have exceeded the rate limit");
        IOException e3 = new IOException("Request was throttled");
        IOException e4 = new IOException("503 Service Unavailable");

        assertTrue(retryPolicy.isRetryable(e1));
        assertTrue(retryPolicy.isRetryable(e2));
        assertTrue(retryPolicy.isRetryable(e3));
        assertTrue(retryPolicy.isRetryable(e4));
    }

    @Test
    @DisplayName("Should not retry non-retryable exceptions")
    void testIsNotRetryableForOtherExceptions() {
        IOException e1 = new IOException("404 Not Found");
        IOException e2 = new IOException("401 Unauthorized");
        IOException e3 = new IOException("400 Bad Request");

        assertFalse(retryPolicy.isRetryable(e1));
        assertFalse(retryPolicy.isRetryable(e2));
        assertFalse(retryPolicy.isRetryable(e3));
    }

    @Test
    @DisplayName("Should calculate exponential backoff delay")
    void testCalculateDelayWithExponentialBackoff() throws Exception {
        // Test exponential backoff without server headers
        long delay1 = retryPolicy.calculateDelayMs(1, null);
        long delay2 = retryPolicy.calculateDelayMs(2, null);
        long delay3 = retryPolicy.calculateDelayMs(3, null);

        // Delays should increase exponentially (with jitter, so we check ranges)
        assertTrue(delay1 >= 700 && delay1 <= 1300); // 1000ms ± 30% jitter
        assertTrue(delay2 >= 1400 && delay2 <= 2600); // 2000ms ± 30% jitter
        assertTrue(delay3 >= 2800 && delay3 <= 5200); // 4000ms ± 30% jitter
    }

    @Test
    @DisplayName("Should respect Retry-After header")
    void testCalculateDelayWithRetryAfterHeader() throws Exception {
        when(mockResponse.header("Retry-After")).thenReturn("5");

        long delay = retryPolicy.calculateDelayMs(1, mockResponse);

        // Should be around 5000ms with some jitter
        assertTrue(delay >= 3500 && delay <= 6500);
    }

    @Test
    @DisplayName("Should respect X-RateLimit-Reset header")
    void testCalculateDelayWithRateLimitResetHeader() throws Exception {
        long futureTime = (System.currentTimeMillis() / 1000L) + 10; // 10 seconds in future
        when(mockResponse.header("X-RateLimit-Reset")).thenReturn(String.valueOf(futureTime));

        long delay = retryPolicy.calculateDelayMs(1, mockResponse);

        // Should be around 10000ms + 1000ms buffer
        assertTrue(delay >= 10000 && delay <= 12000);
    }

    @Test
    @DisplayName("Should respect max delay limit")
    void testMaxDelayLimit() throws Exception {
        // Test with very large attempt number
        long delay = retryPolicy.calculateDelayMs(10, null);

        // Should not exceed max delay (60000ms + jitter)
        assertTrue(delay <= RetryPolicy.DEFAULT_MAX_DELAY_MS * 1.3);
    }

    @Test
    @DisplayName("Should determine when to stop retrying")
    void testShouldRetry() {
        RetryPolicy policyWith3Retries = new RetryPolicy(3, 1000, 60000, 2.0, 0.3, logger);

        assertTrue(policyWith3Retries.shouldRetry(1));
        assertTrue(policyWith3Retries.shouldRetry(2));
        assertTrue(policyWith3Retries.shouldRetry(3));
        assertFalse(policyWith3Retries.shouldRetry(4));
    }

    @Test
    @DisplayName("Should handle null response gracefully")
    void testCalculateDelayWithNullResponse() {
        // Should not throw exception and use exponential backoff
        assertDoesNotThrow(() -> {
            long delay = retryPolicy.calculateDelayMs(1, null);
            assertTrue(delay > 0);
        });
    }

    @Test
    @DisplayName("Should handle invalid Retry-After header")
    void testCalculateDelayWithInvalidRetryAfterHeader() throws Exception {
        when(mockResponse.header("Retry-After")).thenReturn("invalid");

        // Should fall back to exponential backoff
        long delay = retryPolicy.calculateDelayMs(1, mockResponse);
        assertTrue(delay >= 700 && delay <= 1300); // Default backoff with jitter
    }

    @Test
    @DisplayName("Should properly configure custom retry policy")
    void testCustomRetryPolicyConfiguration() {
        RetryPolicy customPolicy = new RetryPolicy(
            10,     // maxRetries
            2000,   // baseDelayMs
            120000, // maxDelayMs
            3.0,    // backoffMultiplier
            0.5,    // jitterFactor
            logger
        );

        assertEquals(10, customPolicy.getMaxRetries());
        assertEquals(2000, customPolicy.getBaseDelayMs());
        assertEquals(120000, customPolicy.getMaxDelayMs());
        assertEquals(3.0, customPolicy.getBackoffMultiplier());
        assertEquals(0.5, customPolicy.getJitterFactor());
    }

    @Test
    @DisplayName("Should honor X-RateLimit-Reset far in the future instead of capping at maxDelayMs")
    void testRateLimitResetFarFutureNotCappedAtMaxDelay() throws Exception {
        long futureTime = (System.currentTimeMillis() / 1000L) + 600; // 10 minutes in future
        when(mockResponse.header("X-RateLimit-Reset")).thenReturn(String.valueOf(futureTime));

        long delay = retryPolicy.calculateDelayMs(1, mockResponse);

        // Must wait ~10 minutes (+1s buffer), NOT be capped at maxDelayMs (60s)
        assertTrue(delay > RetryPolicy.DEFAULT_MAX_DELAY_MS,
                "Rate-limit reset wait must not be capped at maxDelayMs (60s), was: " + delay);
        assertTrue(delay >= 595000 && delay <= 602000,
                "Delay should be ~600s + 1s buffer, was: " + delay);
    }

    @Test
    @DisplayName("Should honor X-RateLimit-Reset a few seconds in the future")
    void testRateLimitResetFewSecondsHonored() throws Exception {
        long futureTime = (System.currentTimeMillis() / 1000L) + 5; // 5 seconds in future
        when(mockResponse.header("X-RateLimit-Reset")).thenReturn(String.valueOf(futureTime));

        long delay = retryPolicy.calculateDelayMs(1, mockResponse);

        // ~5s + 1s buffer
        assertTrue(delay >= 5000 && delay <= 7000,
                "Delay should be ~5s + 1s buffer, was: " + delay);
    }

    @Test
    @DisplayName("Should cap X-RateLimit-Reset wait at default rate-limit max wait (60 min), not throw")
    void testRateLimitResetCappedAtDefaultRateLimitMaxWait() {
        long futureTime = (System.currentTimeMillis() / 1000L) + 7200; // 2 hours in future
        when(mockResponse.header("X-RateLimit-Reset")).thenReturn(String.valueOf(futureTime));

        long delay = assertDoesNotThrow(() -> retryPolicy.calculateDelayMs(1, mockResponse),
                "Long rate-limit reset wait must be capped, not aborted");

        // Capped at the default RATE_LIMIT_MAX_WAIT_SECONDS (3600s)
        assertEquals(RetryPolicy.DEFAULT_RATE_LIMIT_MAX_WAIT_SECONDS * 1000L, delay,
                "Delay should be capped at the default 3600s rate-limit max wait");
    }

    @Test
    @DisplayName("Should honor rate-limit Retry-After beyond 5 minutes")
    void testRateLimitedRetryAfterBeyondFiveMinutesHonored() {
        when(mockResponse.code()).thenReturn(429);
        when(mockResponse.header("Retry-After")).thenReturn("600"); // 10 minutes

        long delay = assertDoesNotThrow(() -> retryPolicy.calculateDelayMs(1, mockResponse),
                "Rate-limit Retry-After of 600s must be honored, not aborted");

        // 600s with up to 30% jitter, and definitely beyond the old 300s abort threshold
        assertTrue(delay > RetryPolicy.MAX_RETRY_AFTER_SECONDS * 1000L,
                "Delay should exceed the old 300s abort threshold, was: " + delay);
        assertTrue(delay >= 510000 && delay <= 690000,
                "Delay should be ~600s with jitter, was: " + delay);
    }

    @Test
    @DisplayName("Should still abort non-rate-limit Retry-After beyond 5 minutes")
    void testNonRateLimitedRetryAfterBeyondFiveMinutesAborts() {
        when(mockResponse.code()).thenReturn(503);
        when(mockResponse.header("Retry-After")).thenReturn("600");

        assertThrows(IOException.class, () -> retryPolicy.calculateDelayMs(1, mockResponse),
                "Non-rate-limit Retry-After beyond 300s should still abort");
    }

    @Test
    @DisplayName("Should cap rate-limit Retry-After at the rate-limit max wait instead of throwing")
    void testRateLimitedRetryAfterAboveCapIsCappedNotThrown() {
        when(mockResponse.code()).thenReturn(429);
        when(mockResponse.header("Retry-After")).thenReturn("7200"); // 2 hours, above default cap

        long delay = assertDoesNotThrow(() -> retryPolicy.calculateDelayMs(1, mockResponse),
                "Rate-limit Retry-After above the cap must be capped, not aborted");

        // Capped at 3600s, then jittered by up to 15% (0.3 factor * +/-0.5)
        long capMs = RetryPolicy.DEFAULT_RATE_LIMIT_MAX_WAIT_SECONDS * 1000L;
        assertTrue(delay >= capMs * 0.84 && delay <= capMs * 1.16,
                "Delay should be ~3600s cap with jitter, was: " + delay);
    }

    @Test
    @DisplayName("Should respect configurable RATE_LIMIT_MAX_WAIT_SECONDS cap")
    void testRateLimitMaxWaitSecondsConfigurable() throws Exception {
        PropertyReader.setOverrides(Map.of("RATE_LIMIT_MAX_WAIT_SECONDS", "300"));
        try {
            RetryPolicy cappedPolicy = new RetryPolicy(logger);
            assertEquals(300L, cappedPolicy.getRateLimitMaxWaitSeconds(),
                    "Policy should pick up the configured RATE_LIMIT_MAX_WAIT_SECONDS");

            long futureTime = (System.currentTimeMillis() / 1000L) + 3600; // 60 min in future
            when(mockResponse.header("X-RateLimit-Reset")).thenReturn(String.valueOf(futureTime));

            long delay = cappedPolicy.calculateDelayMs(1, mockResponse);

            assertEquals(300_000L, delay,
                    "Wait should be capped at the configured 300s, was: " + delay);
        } finally {
            PropertyReader.clearOverrides();
        }
    }

    @Test
    @DisplayName("Should default rate-limit max wait to 3600 seconds")
    void testDefaultRateLimitMaxWaitSeconds() {
        assertEquals(3600L, RetryPolicy.DEFAULT_RATE_LIMIT_MAX_WAIT_SECONDS);
        assertEquals(RetryPolicy.DEFAULT_RATE_LIMIT_MAX_WAIT_SECONDS,
                retryPolicy.getRateLimitMaxWaitSeconds());
    }

    @Test
    @DisplayName("Should add jitter to prevent thundering herd")
    void testJitterVariation() throws Exception {
        // Run multiple calculations to ensure jitter creates variation
        long[] delays = new long[10];
        for (int i = 0; i < 10; i++) {
            delays[i] = retryPolicy.calculateDelayMs(1, null);
        }

        // Check that not all delays are identical (jitter is working)
        boolean hasVariation = false;
        for (int i = 1; i < delays.length; i++) {
            if (delays[i] != delays[0]) {
                hasVariation = true;
                break;
            }
        }
        assertTrue(hasVariation, "Jitter should create variation in delays");
    }
}
