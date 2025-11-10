package com.github.istin.dmtools.common.utils;

import com.github.istin.dmtools.common.networking.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class RetryUtil {
    @FunctionalInterface
    public interface CheckedSupplier<T> {
        T get() throws Exception;
    }

    private static final Logger logger = LoggerFactory.getLogger(RetryUtil.class);

    /**
     * Executes an operation with retry mechanism for handling any exception
     * @param operation The operation to execute
     * @param defaultValue The default value to return if all retries fail
     * @param <T> The return type of the operation
     * @return The result of the operation or the default value if all retries fail
     */
    public static <T> T executeWithRetry(CheckedSupplier<T> operation, T defaultValue) {
        PropertyReader propertyReader = new PropertyReader();
        int maxRetries = propertyReader.getAiRetryAmount();
        long delayStep = propertyReader.getAiRetryDelayStep();
        int attemptCount = 0;
        Exception lastException = null;

        while (attemptCount <= maxRetries) {
            try {
                return operation.get();
            } catch (Exception e) {
                if (e instanceof RestClient.RestClientException) {
                    String body = ((RestClient.RestClientException) e).getBody();
                    if (body != null) {
                        int code = ((RestClient.RestClientException) e).getCode();
                        if (body.contains("invalid message format") || code == 400 || code == 403) {
                            return (T) body;
                        }
                    }
                }
                e.printStackTrace();
                lastException = e;
                attemptCount++;

                if (attemptCount > maxRetries) {
                    logger.error("All retry attempts failed. Last error: ", e);
                    return defaultValue;
                }

                long currentDelay = delayStep * attemptCount;

                logger.warn("Attempt {} failed. Retrying after {} seconds. Error: {}",
                        attemptCount, (currentDelay/1000), e.getMessage());

                try {
                    Thread.sleep(currentDelay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    logger.error("Retry interrupted", ie);
                    return defaultValue;
                }
            }
        }

        return defaultValue;
    }

    /**
     * Executes an operation with retry mechanism for handling any exception
     * @param operation The operation to execute
     * @return The result of the operation or empty string if all retries fail
     */
    public static String executeWithRetry(CheckedSupplier<String> operation) {
        return executeWithRetry(operation, "");
    }

    /**
     * Executes an operation with retry mechanism for handling any exception
     * Throws RuntimeException if all retries fail
     * @param operation The operation to execute
     * @param <T> The return type of the operation
     * @return The result of the operation
     * @throws RuntimeException if all retries fail
     */
    public static <T> T executeWithRetryAndThrow(CheckedSupplier<T> operation) {
        PropertyReader propertyReader = new PropertyReader();
        int maxRetries = propertyReader.getAiRetryAmount();
        long delayStep = propertyReader.getAiRetryDelayStep();
        int attemptCount = 0;
        Exception lastException = null;

        while (attemptCount <= maxRetries) {
            try {
                return operation.get();
            } catch (Exception e) {
                lastException = e;
                attemptCount++;

                if (attemptCount > maxRetries) {
                    logger.error("All retry attempts failed. Last error: ", e);
                    throw new RuntimeException("Operation failed after " + maxRetries + " retries", lastException);
                }

                long currentDelay = delayStep * attemptCount;

                logger.warn("Attempt {} failed. Retrying after {} seconds. Error: {}",
                        attemptCount, (currentDelay/1000), e.getMessage());

                try {
                    Thread.sleep(currentDelay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Retry interrupted", ie);
                }
            }
        }

        throw new RuntimeException("Operation failed after " + maxRetries + " retries", lastException);
    }
}