// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.networking;

import com.github.istin.dmtools.common.networking.RestClient;
import okhttp3.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.*;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for AbstractRestClient retry logic with the new RetryPolicy implementation.
 * These tests verify the RetryPolicy behavior directly.
 */
class AbstractRestClientRetryTest {

    private static final Logger logger = LogManager.getLogger(AbstractRestClientRetryTest.class);
    private RetryPolicy retryPolicy;

    @BeforeEach
    void setUp() {
        // Create policy with short delays for testing
        retryPolicy = new RetryPolicy(
            3,      // maxRetries
            100,    // baseDelayMs (short for testing)
            1000,   // maxDelayMs
            2.0,    // backoffMultiplier
            0.1,    // jitterFactor (low for predictable testing)
            logger
        );
    }

    @Test
    @DisplayName("RetryPolicy should identify RateLimitException as retryable")
    void testRateLimitExceptionIsRetryable() {
        RestClient.RateLimitException rateLimitEx =
            new RestClient.RateLimitException("Rate limit", "body", null, 429);

        assertTrue(retryPolicy.isRetryable(rateLimitEx));
    }

    @Test
    @DisplayName("RetryPolicy should identify rate limit messages as retryable")
    void testRateLimitMessageIsRetryable() {
        IOException rateLimit1 = new IOException("Rate limit exceeded");
        IOException rateLimit2 = new IOException("Too many requests (429)");
        IOException rateLimit3 = new IOException("throttled");

        assertTrue(retryPolicy.isRetryable(rateLimit1));
        assertTrue(retryPolicy.isRetryable(rateLimit2));
        assertTrue(retryPolicy.isRetryable(rateLimit3));
    }

    @Test
    @DisplayName("RetryPolicy should identify service unavailable errors as retryable")
    void testServiceUnavailableIsRetryable() {
        IOException serviceUnavailable = new IOException("Service unavailable (503)");
        IOException gatewayTimeout = new IOException("Gateway timeout (504)");

        assertTrue(retryPolicy.isRetryable(serviceUnavailable));
        assertTrue(retryPolicy.isRetryable(gatewayTimeout));
    }

    @Test
    @DisplayName("RetryPolicy should not retry non-retryable errors")
    void testNonRetryableErrors() {
        IOException notFound = new IOException("Not found (404)");
        IOException unauthorized = new IOException("Unauthorized (401)");
        IOException badRequest = new IOException("Bad request (400)");

        assertFalse(retryPolicy.isRetryable(notFound));
        assertFalse(retryPolicy.isRetryable(unauthorized));
        assertFalse(retryPolicy.isRetryable(badRequest));
    }

    @Test
    @DisplayName("RetryPolicy should calculate exponential backoff delay")
    void testCalculateDelayExponentialBackoff() throws Exception {
        long delay1 = retryPolicy.calculateDelayMs(1, null);
        long delay2 = retryPolicy.calculateDelayMs(2, null);
        long delay3 = retryPolicy.calculateDelayMs(3, null);

        // Delays should increase (with some tolerance for jitter)
        assertTrue(delay1 >= 80 && delay1 <= 150, "First delay should be ~100ms: " + delay1);
        assertTrue(delay2 >= 160 && delay2 <= 300, "Second delay should be ~200ms: " + delay2);
        assertTrue(delay3 >= 320 && delay3 <= 600, "Third delay should be ~400ms: " + delay3);

        // Delays should respect max delay
        long delayHigh = retryPolicy.calculateDelayMs(10, null);
        assertTrue(delayHigh <= 1100, "Delay should not exceed maxDelayMs + jitter: " + delayHigh);
    }

    @Test
    @DisplayName("RetryPolicy should respect Retry-After header")
    void testRetryAfterHeader() throws Exception {
        Response mockResponse = mock(Response.class);
        when(mockResponse.header("Retry-After")).thenReturn("5");

        long delay = retryPolicy.calculateDelayMs(1, mockResponse);

        // Should use server-provided delay (5 seconds = 5000ms) with some jitter
        assertTrue(delay >= 4500 && delay <= 5500, "Delay should be ~5000ms: " + delay);
    }

    @Test
    @DisplayName("RetryPolicy should throw when Retry-After exceeds 5 minutes")
    void testRetryAfterExceedsMaxThrows() {
        Response mockResponse = mock(Response.class);
        when(mockResponse.header("Retry-After")).thenReturn("390883");

        assertThrows(IOException.class, () -> retryPolicy.calculateDelayMs(1, mockResponse),
            "Should throw when Retry-After exceeds MAX_RETRY_AFTER_SECONDS");
    }

    @Test
    @DisplayName("RetryPolicy should return correct max retries")
    void testGetMaxRetries() {
        assertEquals(3, retryPolicy.getMaxRetries());
    }

    @Test
    @DisplayName("Default RetryPolicy should have sensible defaults")
    void testDefaultRetryPolicy() {
        RetryPolicy defaultPolicy = new RetryPolicy(logger);

        assertEquals(RetryPolicy.DEFAULT_MAX_RETRIES, defaultPolicy.getMaxRetries());

        // Verify it can identify retryable errors
        assertTrue(defaultPolicy.isRetryable(new IOException("Rate limit")));
        assertFalse(defaultPolicy.isRetryable(new IOException("Not found")));
    }
}
