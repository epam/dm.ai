package com.github.istin.dmtools.networking;

import org.apache.logging.log4j.Logger;

/**
 * Configuration helper for creating RetryPolicy instances from environment variables or properties.
 * Allows easy tuning of retry behavior without code changes.
 *
 * Environment variables:
 * - JIRA_RETRY_MAX_ATTEMPTS: Maximum number of retry attempts (default: 5)
 * - JIRA_RETRY_BASE_DELAY_MS: Base delay in milliseconds (default: 1000)
 * - JIRA_RETRY_MAX_DELAY_MS: Maximum delay in milliseconds (default: 60000)
 * - JIRA_RETRY_BACKOFF_MULTIPLIER: Backoff multiplier (default: 2.0)
 * - JIRA_RETRY_JITTER_FACTOR: Jitter factor 0.0-1.0 (default: 0.3)
 * - JIRA_RETRY_ENABLED: Enable/disable retry logic (default: true)
 */
public class RetryPolicyConfig {

    private static final String ENV_PREFIX = "JIRA_RETRY_";

    private RetryPolicyConfig() {
        // Utility class, prevent instantiation
    }

    /**
     * Creates a RetryPolicy configured from environment variables.
     * Falls back to defaults if environment variables are not set.
     */
    public static RetryPolicy fromEnvironment(Logger logger) {
        // Check if retry is enabled
        boolean retryEnabled = getBooleanEnv(ENV_PREFIX + "ENABLED", true);
        if (!retryEnabled) {
            // Return a policy with 0 retries (effectively disabled)
            return new RetryPolicy(0, 0, 0, 1.0, 0.0, logger);
        }

        int maxRetries = getIntEnv(ENV_PREFIX + "MAX_ATTEMPTS", RetryPolicy.DEFAULT_MAX_RETRIES);
        long baseDelayMs = getLongEnv(ENV_PREFIX + "BASE_DELAY_MS", RetryPolicy.DEFAULT_BASE_DELAY_MS);
        long maxDelayMs = getLongEnv(ENV_PREFIX + "MAX_DELAY_MS", RetryPolicy.DEFAULT_MAX_DELAY_MS);
        double backoffMultiplier = getDoubleEnv(ENV_PREFIX + "BACKOFF_MULTIPLIER", RetryPolicy.DEFAULT_BACKOFF_MULTIPLIER);
        double jitterFactor = getDoubleEnv(ENV_PREFIX + "JITTER_FACTOR", RetryPolicy.DEFAULT_JITTER_FACTOR);

        // Log configuration if logger is available
        if (logger != null) {
            logger.info("RetryPolicy configured from environment: maxRetries={}, baseDelayMs={}, maxDelayMs={}, " +
                       "backoffMultiplier={}, jitterFactor={}",
                       maxRetries, baseDelayMs, maxDelayMs, backoffMultiplier, jitterFactor);
        }

        return new RetryPolicy(maxRetries, baseDelayMs, maxDelayMs, backoffMultiplier, jitterFactor, logger);
    }

    /**
     * Creates a RetryPolicy optimized for Jira Cloud.
     * These settings are based on Atlassian's recommendations for handling rate limits.
     */
    public static RetryPolicy forJiraCloud(Logger logger) {
        // Jira Cloud specific settings
        // - More aggressive retries as rate limits are common
        // - Longer max delay to handle hourly rate limit resets
        // - Higher jitter to prevent synchronized retries
        return new RetryPolicy(
            7,          // maxRetries - up to 7 attempts
            2000,       // baseDelayMs - start with 2 seconds
            120000,     // maxDelayMs - up to 2 minutes
            2.0,        // backoffMultiplier - double each time
            0.4,        // jitterFactor - 40% jitter for better distribution
            logger
        );
    }

    /**
     * Creates a RetryPolicy optimized for Jira Server/Data Center.
     * These settings are more conservative as on-premise installations typically have fewer rate limits.
     */
    public static RetryPolicy forJiraServer(Logger logger) {
        // Jira Server/DC specific settings
        // - Fewer retries as rate limits are less common
        // - Shorter delays as recovery is typically faster
        // - Lower jitter as there's less concurrency concern
        return new RetryPolicy(
            3,          // maxRetries - up to 3 attempts
            500,        // baseDelayMs - start with 500ms
            30000,      // maxDelayMs - up to 30 seconds
            2.0,        // backoffMultiplier - double each time
            0.2,        // jitterFactor - 20% jitter
            logger
        );
    }

    /**
     * Creates a RetryPolicy for testing with minimal delays.
     */
    public static RetryPolicy forTesting(Logger logger) {
        return new RetryPolicy(
            3,          // maxRetries
            10,         // baseDelayMs - very short for fast tests
            100,        // maxDelayMs - cap at 100ms
            2.0,        // backoffMultiplier
            0.1,        // jitterFactor - minimal jitter for predictable tests
            logger
        );
    }

    /**
     * Creates an aggressive RetryPolicy for critical operations.
     * Use this for operations that must succeed if at all possible.
     */
    public static RetryPolicy aggressive(Logger logger) {
        return new RetryPolicy(
            10,         // maxRetries - many attempts
            3000,       // baseDelayMs - start with 3 seconds
            300000,     // maxDelayMs - up to 5 minutes
            1.5,        // backoffMultiplier - slower growth
            0.5,        // jitterFactor - high jitter
            logger
        );
    }

    /**
     * Creates a conservative RetryPolicy for non-critical operations.
     * Use this when failures are acceptable and you want to fail fast.
     */
    public static RetryPolicy conservative(Logger logger) {
        return new RetryPolicy(
            2,          // maxRetries - only 2 attempts
            1000,       // baseDelayMs - 1 second
            5000,       // maxDelayMs - max 5 seconds
            2.0,        // backoffMultiplier
            0.2,        // jitterFactor
            logger
        );
    }

    // Helper methods for reading environment variables

    private static int getIntEnv(String name, int defaultValue) {
        String value = System.getenv(name);
        if (value != null && !value.isEmpty()) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                // Fall through to default
            }
        }
        return defaultValue;
    }

    private static long getLongEnv(String name, long defaultValue) {
        String value = System.getenv(name);
        if (value != null && !value.isEmpty()) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException e) {
                // Fall through to default
            }
        }
        return defaultValue;
    }

    private static double getDoubleEnv(String name, double defaultValue) {
        String value = System.getenv(name);
        if (value != null && !value.isEmpty()) {
            try {
                return Double.parseDouble(value);
            } catch (NumberFormatException e) {
                // Fall through to default
            }
        }
        return defaultValue;
    }

    private static boolean getBooleanEnv(String name, boolean defaultValue) {
        String value = System.getenv(name);
        if (value != null && !value.isEmpty()) {
            return "true".equalsIgnoreCase(value) || "1".equals(value) || "yes".equalsIgnoreCase(value);
        }
        return defaultValue;
    }
}