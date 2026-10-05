// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.networking;

import com.github.istin.dmtools.common.utils.PropertyReader;
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

    // Maximum time we will ever wait for a Retry-After response (5 minutes)
    public static final long MAX_RETRY_AFTER_SECONDS = 300L;

    // Property key (config.properties / dmtools.env / environment) for the rate-limit wait cap
    public static final String RATE_LIMIT_MAX_WAIT_SECONDS_PROPERTY = "RATE_LIMIT_MAX_WAIT_SECONDS";
    // Maximum time we will wait for a genuine rate limit to reset (default: 60 minutes)
    public static final long DEFAULT_RATE_LIMIT_MAX_WAIT_SECONDS = 3600L;

    private final int maxRetries;
    private final long baseDelayMs;
    private final long maxDelayMs;
    private final double backoffMultiplier;
    private final double jitterFactor;
    private final long rateLimitMaxWaitSeconds;
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
     * The rate-limit wait cap is resolved from the RATE_LIMIT_MAX_WAIT_SECONDS property
     * (default: 3600 seconds).
     */
    public RetryPolicy(int maxRetries, long baseDelayMs, long maxDelayMs,
                      double backoffMultiplier, double jitterFactor, Logger logger) {
        this(maxRetries, baseDelayMs, maxDelayMs, backoffMultiplier, jitterFactor,
             resolveRateLimitMaxWaitSeconds(logger), logger);
    }

    /**
     * Creates a retry policy with custom settings including an explicit rate-limit wait cap.
     */
    public RetryPolicy(int maxRetries, long baseDelayMs, long maxDelayMs,
                      double backoffMultiplier, double jitterFactor,
                      long rateLimitMaxWaitSeconds, Logger logger) {
        this.maxRetries = maxRetries;
        this.baseDelayMs = baseDelayMs;
        this.maxDelayMs = maxDelayMs;
        this.backoffMultiplier = backoffMultiplier;
        this.jitterFactor = jitterFactor;
        this.rateLimitMaxWaitSeconds = rateLimitMaxWaitSeconds;
        this.random = new Random();
        this.logger = logger;
    }

    /**
     * Resolves the rate-limit wait cap (in seconds) from the RATE_LIMIT_MAX_WAIT_SECONDS
     * property, falling back to DEFAULT_RATE_LIMIT_MAX_WAIT_SECONDS when unset or invalid.
     */
    private static long resolveRateLimitMaxWaitSeconds(Logger logger) {
        String value = new PropertyReader().getValue(RATE_LIMIT_MAX_WAIT_SECONDS_PROPERTY,
                String.valueOf(DEFAULT_RATE_LIMIT_MAX_WAIT_SECONDS));
        try {
            long parsed = Long.parseLong(value.trim());
            if (parsed > 0) {
                return parsed;
            }
        } catch (NumberFormatException e) {
            // Fall through to default
        }
        if (logger != null) {
            logger.warn("Invalid {} value '{}', falling back to {}s",
                    RATE_LIMIT_MAX_WAIT_SECONDS_PROPERTY, value, DEFAULT_RATE_LIMIT_MAX_WAIT_SECONDS);
        }
        return DEFAULT_RATE_LIMIT_MAX_WAIT_SECONDS;
    }

    /**
     * Determines if an exception is retryable.
     */
    public boolean isRetryable(IOException e) {
        if (e instanceof com.github.istin.dmtools.common.networking.RestClient.RateLimitException) {
            return true;
        }

        if (e instanceof com.github.istin.dmtools.common.networking.RestClient.RestClientException) {
            // The message embeds the request URL (host:port, ids), so substring checks on it
            // misfire (a port like 50312 contains "503"). Decide on the status code and body.
            return isRetryableStatus((com.github.istin.dmtools.common.networking.RestClient.RestClientException) e);
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

    private static boolean isRetryableStatus(com.github.istin.dmtools.common.networking.RestClient.RestClientException e) {
        int code = e.getCode();
        if (code == 429 || code == 502 || code == 503 || code == 504) {
            return true;
        }
        String body = e.getBody() == null ? "" : e.getBody().toLowerCase();
        return body.contains("rate limit") || body.contains("too many requests") || body.contains("throttl");
    }

    /**
     * Returns true if the exception represents a client error (4xx) that should never be retried.
     * Note: 429 (RateLimitException) is explicitly excluded — it IS retryable with Retry-After delay.
     */
    public static boolean isClientError(IOException e) {
        if (e instanceof com.github.istin.dmtools.common.networking.RestClient.RateLimitException) {
            return false;
        }
        if (e instanceof com.github.istin.dmtools.common.networking.RestClient.RestClientException) {
            int code = ((com.github.istin.dmtools.common.networking.RestClient.RestClientException) e).getCode();
            return code >= 400 && code < 500;
        }
        return false;
    }

    /**
     * Calculates the delay before the next retry attempt.
     * Uses exponential backoff with jitter to avoid thundering herd.
     * Rate-limit responses (HTTP 429, or X-RateLimit-Reset) are honored up to
     * rateLimitMaxWaitSeconds and capped (never aborted) beyond it.
     * Throws IOException if a non-rate-limit Retry-After exceeds MAX_RETRY_AFTER_SECONDS (5 minutes).
     */
    public long calculateDelayMs(int attemptNumber, Response response) throws IOException {
        // First check if server provided Retry-After header
        if (response != null) {
            String retryAfter = response.header("Retry-After");
            if (retryAfter != null) {
                try {
                    // Retry-After can be in seconds or HTTP-date format
                    // For simplicity, assume it's in seconds
                    long retryAfterSeconds = Long.parseLong(retryAfter);
                    boolean rateLimited = response.code() == 429;
                    if (!rateLimited && retryAfterSeconds > MAX_RETRY_AFTER_SECONDS) {
                        throw new IOException(
                            "Retry-After header value (" + retryAfterSeconds + "s) exceeds configured maximum of "
                            + MAX_RETRY_AFTER_SECONDS + "s. Aborting to avoid excessive wait."
                        );
                    }
                    long serverDelay = retryAfterSeconds * 1000L;
                    if (rateLimited) {
                        // A genuine rate limit may legitimately require a long wait (e.g. GitHub resets
                        // hourly): honor it up to the configured rate-limit cap instead of aborting.
                        long maxWaitMs = rateLimitMaxWaitSeconds * 1000L;
                        if (serverDelay > maxWaitMs) {
                            logger.warn("Rate-limit Retry-After of {}s exceeds {} ({}s), capping wait to {} ms",
                                    retryAfterSeconds, RATE_LIMIT_MAX_WAIT_SECONDS_PROPERTY,
                                    rateLimitMaxWaitSeconds, maxWaitMs);
                            serverDelay = maxWaitMs;
                        }
                    }
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
                        // Honor the server's reset time (GitHub resets up to ~60 min out), capped by the
                        // configurable rate-limit wait cap — never by maxDelayMs and never aborted.
                        long maxWaitMs = rateLimitMaxWaitSeconds * 1000L;
                        long waitMs = delay + 1000L; // Add 1 second buffer
                        if (waitMs > maxWaitMs) {
                            logger.warn("Rate limit reset wait {} ms exceeds {} ({}s), capping wait to {} ms",
                                    waitMs, RATE_LIMIT_MAX_WAIT_SECONDS_PROPERTY, rateLimitMaxWaitSeconds, maxWaitMs);
                            waitMs = maxWaitMs;
                        }
                        logger.info("Rate limit resets at: {}, waiting {} ms", resetTime, waitMs);
                        return waitMs;
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
    public long getRateLimitMaxWaitSeconds() { return rateLimitMaxWaitSeconds; }
}
