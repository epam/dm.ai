package com.github.istin.dmtools.job;

/**
 * Enum defining the execution modes for job execution.
 * Used to determine how jobs are executed and how dependencies are injected.
 */
public enum ExecutionMode {
    /**
     * Standalone mode - uses default Dagger components with system property configuration
     * This is the current behavior and maintains backward compatibility
     */
    STANDALONE,
    
    /**
     * Server-managed mode - uses dynamic Dagger modules with pre-resolved credentials
     * Credentials and integrations are resolved by the server before execution
     */
    SERVER_MANAGED
} 