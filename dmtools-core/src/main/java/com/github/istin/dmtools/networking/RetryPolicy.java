package com.github.istin.dmtools.networking;

import org.apache.logging.log4j.Logger;
import okhttp3.Response;

import java.io.IOException;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Retry policy for handling rate limits and transient failures in API calls.
 * Implements exponential backoff with jitter for optimal retry behavior.
 */
public class RetryPolicy {

    // Default configuration values
    public static final int DEFAULT_MAX_RETRIES = 5;
    public static final long DEFAULT_BASE_DELAY_MS = 1000L; // 1 second
    public static final long DEFAULT_MAX_DELAY_MS = 60000L; // 60 seconds
    public static final double DEFAULT_BACKOFF_MULTIPLIER = 2.0;
    public static final double DEFAULT_JITTER_FACTOR = 0.3; // 30% jitter

    private final int maxRetries;
    private final long baseDelayMs;
    private final long maxDelayMs;
    private final double backoffMultiplier;
    private final double jitterFactor;
    private final Random random;
    private final Logger logger;

    /**
     * Creates a retry policy with default settings optimized for Jira Cloud.
     */
    public RetryPolicy(Logger logger) {
        this(DEFAULT_MAX_RETRIES, DEFAULT_BASE_DELAY_MS, DEFAULT_MAX_DELAY_MS,
             DEFAULT_BACKOFF_MULTIPLIER, DEFAULT_JITTER_FACTOR, logger);
    }

    /**
     * Creates a retry policy with custom settings.
     */
    public RetryPolicy(int maxRetries, long baseDelayMs, long maxDelayMs,
                      double backoffMultiplier, double jitterFactor, Logger logger) {
        this.maxRetries = maxRetries;
        this.baseDelayMs = baseDelayMs;
        this.maxDelayMs = maxDelayMs;
        this.backoffMultiplier = backoffMultiplier;
        this.jitterFactor = jitterFactor;
        this.random = new Random();
        this.logger = logger;
    }

    /**
     * Determines if an exception is retryable.
     */
    public boolean isRetryable(IOException e) {
        if (e instanceof com.github.istin.dmtools.common.networking.RestClient.RateLimitException) {
            return true;
        }

        String message = e.getMessage();
        if (message == null) {
            return false;
        }

        String lowerMessage = message.toLowerCase();
        return lowerMessage.contains("rate limit") ||
               lowerMessage.contains("429") ||
               lowerMessage.contains("too many requests") ||
               lowerMessage.contains("throttl") ||
               lowerMessage.contains("503") ||
               lowerMessage.contains("service unavailable") ||
               lowerMessage.contains("gateway timeout") ||
               lowerMessage.contains("502") ||
               lowerMessage.contains("504");
    }

    /**
     * Calculates the delay before the next retry attempt.
     * Uses exponential backoff with jitter to avoid thundering herd.
     */
    public long calculateDelayMs(int attemptNumber, Response response) {
        // First check if server provided Retry-After header
        if (response != null) {
            String retryAfter = response.header("Retry-After");
            if (retryAfter != null) {
                try {
                    // Retry-After can be in seconds or HTTP-date format
                    // For simplicity, assume it's in seconds
                    long serverDelay = Long.parseLong(retryAfter) * 1000L;
                    logger.info("Server provided Retry-After header: {} seconds", retryAfter);
                    // Add small jitter even to server-provided delay
                    return addJitter(serverDelay);
                } catch (NumberFormatException e) {
                    logger.debug("Could not parse Retry-After header: {}", retryAfter);
                }
            }

            // Check for X-RateLimit-Reset header (Unix timestamp)
            String rateLimitReset = response.header("X-RateLimit-Reset");
            if (rateLimitReset != null) {
                try {
                    long resetTime = Long.parseLong(rateLimitReset) * 1000L; // Convert to milliseconds
                    long currentTime = System.currentTimeMillis();
                    if (resetTime > currentTime) {
                        long delay = resetTime - currentTime;
                        logger.info("Rate limit resets at: {}, waiting {} ms", resetTime, delay);
                        return Math.min(delay + 1000L, maxDelayMs); // Add 1 second buffer
                    }
                } catch (NumberFormatException e) {
                    logger.debug("Could not parse X-RateLimit-Reset header: {}", rateLimitReset);
                }
            }
        }

        // Calculate exponential backoff
        double exponentialDelay = baseDelayMs * Math.pow(backoffMultiplier, attemptNumber - 1);
        long delay = Math.min((long) exponentialDelay, maxDelayMs);

        // Add jitter to prevent thundering herd
        return addJitter(delay);
    }

    /**
     * Adds random jitter to the delay to prevent synchronized retries.
     */
    private long addJitter(long delay) {
        double jitter = delay * jitterFactor * (random.nextDouble() - 0.5);
        return Math.max(0, delay + (long) jitter);
    }

    /**
     * Executes the retry delay.
     */
    public void executeDelay(long delayMs) throws InterruptedException {
        if (delayMs > 0) {
            logger.info("Waiting {} ms before retry ({}s)", delayMs, delayMs / 1000.0);
            Thread.sleep(delayMs);
        }
    }

    /**
     * Checks if retry should be attempted based on attempt number.
     */
    public boolean shouldRetry(int attemptNumber) {
        return attemptNumber <= maxRetries;
    }

    /**
     * Logs retry attempt information.
     */
    public void logRetryAttempt(int attemptNumber, String url, IOException error) {
        if (error instanceof com.github.istin.dmtools.common.networking.RestClient.RateLimitException) {
            logger.warn("Rate limit hit for URL: {} (Attempt {}/{}). Error: {}",
                       sanitizeUrl(url), attemptNumber, maxRetries, error.getMessage());
        } else {
            logger.warn("Transient error for URL: {} (Attempt {}/{}). Error: {}",
                       sanitizeUrl(url), attemptNumber, maxRetries, error.getMessage());
        }
    }

    /**
     * Logs when max retries are exceeded.
     */
    public void logMaxRetriesExceeded(String url, IOException lastError) {
        logger.error("Max retries ({}) exceeded for URL: {}. Final error: {}",
                    maxRetries, sanitizeUrl(url), lastError.getMessage());
    }

    /**
     * Sanitizes URL to remove sensitive information.
     */
    private String sanitizeUrl(String url) {
        if (url == null) return null;
        // Remove any API keys or tokens from URL
        return url.replaceAll("([?&])(api_key|token|key|auth)=([^&]*)", "$1$2=***");
    }

    // Getters for configuration
    public int getMaxRetries() { return maxRetries; }
    public long getBaseDelayMs() { return baseDelayMs; }
    public long getMaxDelayMs() { return maxDelayMs; }
    public double getBackoffMultiplier() { return backoffMultiplier; }
    public double getJitterFactor() { return jitterFactor; }
}