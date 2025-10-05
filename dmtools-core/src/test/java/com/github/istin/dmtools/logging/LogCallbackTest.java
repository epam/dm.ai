package com.github.istin.dmtools.logging;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class LogCallbackTest {

    @Test
    void testLogCallbackInterface() {
        AtomicInteger callCount = new AtomicInteger(0);
        
        LogCallback callback = (executionId, level, message, component) -> {
            callCount.incrementAndGet();
            assertNotNull(executionId);
            assertNotNull(level);
            assertNotNull(message);
            assertNotNull(component);
        };

        callback.onLog("exec-123", "INFO", "Test message", "TestComponent");
        assertEquals(1, callCount.get());
    }

    @Test
    void testLogCallbackInterface_MultipleValues() {
        LogCallback callback = (executionId, level, message, component) -> {
            assertEquals("exec-456", executionId);
            assertEquals("DEBUG", level);
            assertEquals("Debug message", message);
            assertEquals("DebugComponent", component);
        };

        callback.onLog("exec-456", "DEBUG", "Debug message", "DebugComponent");
    }

    @Test
    void testLogCallbackInterface_NullValues() {
        LogCallback callback = (executionId, level, message, component) -> {
            assertNull(executionId);
            assertNull(level);
            assertNull(message);
            assertNull(component);
        };

        callback.onLog(null, null, null, null);
    }

    @Test
    void testLogCallbackInterface_EmptyValues() {
        LogCallback callback = (executionId, level, message, component) -> {
            assertEquals("", executionId);
            assertEquals("", level);
            assertEquals("", message);
            assertEquals("", component);
        };

        callback.onLog("", "", "", "");
    }

    @Test
    void testLogCallbackInterface_DifferentLevels() {
        String[] levels = {"DEBUG", "INFO", "WARN", "ERROR"};
        AtomicInteger index = new AtomicInteger(0);
        
        LogCallback callback = (executionId, level, message, component) -> {
            assertEquals(levels[index.get()], level);
            index.incrementAndGet();
        };

        for (String level : levels) {
            callback.onLog("exec-1", level, "Message", "Component");
        }
        
        assertEquals(4, index.get());
    }
}
