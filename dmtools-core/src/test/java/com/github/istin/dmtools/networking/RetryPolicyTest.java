package com.github.istin.dmtools.networking;

import com.github.istin.dmtools.common.networking.RestClient;
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
    void testCalculateDelayWithExponentialBackoff() {
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
    void testCalculateDelayWithRetryAfterHeader() {
        when(mockResponse.header("Retry-After")).thenReturn("5");

        long delay = retryPolicy.calculateDelayMs(1, mockResponse);

        // Should be around 5000ms with some jitter
        assertTrue(delay >= 3500 && delay <= 6500);
    }

    @Test
    @DisplayName("Should respect X-RateLimit-Reset header")
    void testCalculateDelayWithRateLimitResetHeader() {
        long futureTime = (System.currentTimeMillis() / 1000L) + 10; // 10 seconds in future
        when(mockResponse.header("X-RateLimit-Reset")).thenReturn(String.valueOf(futureTime));

        long delay = retryPolicy.calculateDelayMs(1, mockResponse);

        // Should be around 10000ms + 1000ms buffer
        assertTrue(delay >= 10000 && delay <= 12000);
    }

    @Test
    @DisplayName("Should respect max delay limit")
    void testMaxDelayLimit() {
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
    void testCalculateDelayWithInvalidRetryAfterHeader() {
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
    @DisplayName("Should add jitter to prevent thundering herd")
    void testJitterVariation() {
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