// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.logging;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Logger wrapper that delegates to original logger and forwards to server via callback.
 * Maintains exact same behavior as original logger while enabling execution monitoring.
 * 
 * This is a composition-based approach rather than implementing the full Logger interface,
 * making it simpler and more maintainable for our MVP needs.
 */
public class CallbackLogger {
    
    private final Logger delegate;  // Original logger
    private final String executionId;
    private final LogCallback callback;
    
    public CallbackLogger(Class<?> clazz, String executionId, LogCallback callback) {
        this.delegate = LogManager.getLogger(clazz);
        this.executionId = executionId;
        this.callback = callback;
    }
    
    public CallbackLogger(Logger logger, String executionId, LogCallback callback) {
        this.delegate = logger;
        this.executionId = executionId;
        this.callback = callback;
    }
    
    // Helper method to forward logs to callback
    private void forwardToCallback(String level, String message) {
        if (callback != null && executionId != null) {
            try {
                callback.onLog(executionId, level, message, delegate.getName());
            } catch (Exception e) {
                // Fallback to stderr if callback fails - don't break the original logging
                System.err.println("Failed to forward log to callback: " + e.getMessage());
            }
        }
    }
    
    // Main logging methods that we need to intercept
    public void info(String message) {
        delegate.info(message);
        forwardToCallback("INFO", message);
    }
    
    public void info(String format, Object... params) {
        delegate.info(format, params);
        try {
            forwardToCallback("INFO", String.format(format.replace("{}", "%s"), params));
        } catch (Exception e) {
            forwardToCallback("INFO", format + " [params:" + java.util.Arrays.toString(params) + "]");
        }
    }
    
    public void error(String message) {
        delegate.error(message);
        forwardToCallback("ERROR", message);
    }
    
    public void error(String message, Throwable throwable) {
        delegate.error(message, throwable);
        forwardToCallback("ERROR", message + ": " + throwable.getMessage());
    }
    
    public void error(String format, Object... params) {
        delegate.error(format, params);
        try {
            forwardToCallback("ERROR", String.format(format.replace("{}", "%s"), params));
        } catch (Exception e) {
            forwardToCallback("ERROR", format + " [params:" + java.util.Arrays.toString(params) + "]");
        }
    }
    
    public void debug(String message) {
        delegate.debug(message);
        forwardToCallback("DEBUG", message);
    }
    
    public void debug(String format, Object... params) {
        delegate.debug(format, params);
        try {
            forwardToCallback("DEBUG", String.format(format.replace("{}", "%s"), params));
        } catch (Exception e) {
            forwardToCallback("DEBUG", format + " [params:" + java.util.Arrays.toString(params) + "]");
        }
    }
    
    public void warn(String message) {
        delegate.warn(message);
        forwardToCallback("WARN", message);
    }
    
    public void warn(String format, Object... params) {
        delegate.warn(format, params);
        try {
            forwardToCallback("WARN", String.format(format.replace("{}", "%s"), params));
        } catch (Exception e) {
            forwardToCallback("WARN", format + " [params:" + java.util.Arrays.toString(params) + "]");
        }
    }
    
    // Provide access to the underlying logger for any methods not wrapped
    public Logger getDelegate() {
        return delegate;
    }
    
    // Common utility methods
    public String getName() {
        return delegate.getName();
    }
    
    public boolean isDebugEnabled() {
        return delegate.isDebugEnabled();
    }
    
    public boolean isInfoEnabled() {
        return delegate.isInfoEnabled();
    }
    
    public boolean isErrorEnabled() {
        return delegate.isErrorEnabled();
    }
} 