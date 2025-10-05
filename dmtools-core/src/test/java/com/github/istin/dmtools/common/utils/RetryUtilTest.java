package com.github.istin.dmtools.common.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RetryUtilTest {

    private MockedConstruction<PropertyReader> mockedPropertyReader;

    @BeforeEach
    void setUp() {
        // Reset static property to ensure consistent test behavior
        PropertyReader.prop = null;
        
        // Mock PropertyReader to use very short delays for testing
        mockedPropertyReader = Mockito.mockConstruction(PropertyReader.class, (mock, context) -> {
            when(mock.getAiRetryAmount()).thenReturn(3);
            when(mock.getAiRetryDelayStep()).thenReturn(1L); // 1ms instead of 20000ms
        });
    }

    @AfterEach
    void tearDown() {
        if (mockedPropertyReader != null) {
            mockedPropertyReader.close();
        }
        PropertyReader.prop = null;
    }

    @Test
    void testExecuteWithRetry_SuccessOnFirstAttempt() {
        String result = RetryUtil.executeWithRetry(() -> "success", "default");
        assertEquals("success", result);
    }

    @Test
    void testExecuteWithRetry_ReturnsDefaultOnFailure() {
        String result = RetryUtil.executeWithRetry(() -> {
            throw new RuntimeException("Test exception");
        }, "default");
        assertEquals("default", result);
    }

    @Test
    void testExecuteWithRetry_SuccessAfterRetries() {
        AtomicInteger attempts = new AtomicInteger(0);
        String result = RetryUtil.executeWithRetry(() -> {
            if (attempts.incrementAndGet() < 2) {
                throw new RuntimeException("Temporary failure");
            }
            return "success after retry";
        }, "default");
        assertEquals("success after retry", result);
        assertTrue(attempts.get() >= 2);
    }

    @Test
    void testExecuteWithRetry_StringVersion() {
        String result = RetryUtil.executeWithRetry(() -> "test string");
        assertEquals("test string", result);
    }

    @Test
    void testExecuteWithRetry_StringVersion_ReturnsEmptyOnFailure() {
        String result = RetryUtil.executeWithRetry(() -> {
            throw new RuntimeException("Test exception");
        });
        assertEquals("", result);
    }

    @Test
    void testExecuteWithRetryAndThrow_Success() {
        String result = RetryUtil.executeWithRetryAndThrow(() -> "success");
        assertEquals("success", result);
    }

    @Test
    void testExecuteWithRetryAndThrow_ThrowsExceptionAfterRetries() {
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            RetryUtil.executeWithRetryAndThrow(() -> {
                throw new RuntimeException("Persistent failure");
            });
        });
        assertTrue(exception.getMessage().contains("retries"));
    }

    @Test
    void testExecuteWithRetryAndThrow_SuccessAfterRetry() {
        AtomicInteger attempts = new AtomicInteger(0);
        String result = RetryUtil.executeWithRetryAndThrow(() -> {
            if (attempts.incrementAndGet() < 2) {
                throw new RuntimeException("Temporary failure");
            }
            return "success";
        });
        assertEquals("success", result);
        assertTrue(attempts.get() >= 2);
    }

    @Test
    void testExecuteWithRetry_MultipleAttempts() {
        AtomicInteger attempts = new AtomicInteger(0);
        
        String result = RetryUtil.executeWithRetry(() -> {
            int currentAttempt = attempts.incrementAndGet();
            if (currentAttempt < 3) {
                throw new RuntimeException("Attempt " + currentAttempt + " fails");
            }
            return "success after retries";
        }, "default");
        
        assertEquals("success after retries", result);
        assertTrue(attempts.get() >= 3);
    }

    @Test
    void testExecuteWithRetryAndThrow_MultipleAttempts() {
        AtomicInteger attempts = new AtomicInteger(0);
        
        String result = RetryUtil.executeWithRetryAndThrow(() -> {
            int currentAttempt = attempts.incrementAndGet();
            if (currentAttempt < 2) {
                throw new RuntimeException("Attempt " + currentAttempt + " fails");
            }
            return "success";
        });
        
        assertEquals("success", result);
        assertTrue(attempts.get() >= 2);
    }
}
