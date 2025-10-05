package com.github.istin.dmtools.logging;

import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LoggerFactoryTest {

    @Test
    void testCreateStandardLogger() {
        Logger logger = LoggerFactory.createStandardLogger(LoggerFactoryTest.class);
        assertNotNull(logger);
        assertTrue(logger instanceof Logger);
    }

    @Test
    void testCreateLogger_WithoutCallback() {
        Object logger = LoggerFactory.createLogger(LoggerFactoryTest.class, null, null);
        assertNotNull(logger);
        assertTrue(logger instanceof Logger);
    }

    @Test
    void testCreateLogger_WithNullExecutionId() {
        LogCallback mockCallback = (executionId, level, message, component) -> {};
        Object logger = LoggerFactory.createLogger(LoggerFactoryTest.class, null, mockCallback);
        assertNotNull(logger);
        assertTrue(logger instanceof Logger);
    }

    @Test
    void testCreateLogger_WithNullCallback() {
        Object logger = LoggerFactory.createLogger(LoggerFactoryTest.class, "exec-123", null);
        assertNotNull(logger);
        assertTrue(logger instanceof Logger);
    }

    @Test
    void testCreateLogger_WithCallbackAndExecutionId() {
        LogCallback mockCallback = (executionId, level, message, component) -> {};
        Object logger = LoggerFactory.createLogger(LoggerFactoryTest.class, "exec-123", mockCallback);
        assertNotNull(logger);
        assertTrue(logger instanceof CallbackLogger);
    }

    @Test
    void testCreateLogger_DifferentClasses() {
        Logger logger1 = LoggerFactory.createStandardLogger(LoggerFactoryTest.class);
        Logger logger2 = LoggerFactory.createStandardLogger(String.class);
        
        assertNotNull(logger1);
        assertNotNull(logger2);
        assertNotEquals(logger1.getName(), logger2.getName());
    }

    @Test
    void testCreateStandardLogger_VerifyLoggerName() {
        Logger logger = LoggerFactory.createStandardLogger(LoggerFactoryTest.class);
        assertEquals(LoggerFactoryTest.class.getName(), logger.getName());
    }
}
