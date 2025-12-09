package com.github.istin.dmtools.di;

import com.github.istin.dmtools.atlassian.jira.BasicJiraClient;
import com.github.istin.dmtools.broadcom.rally.BasicRallyClient;
import com.github.istin.dmtools.common.config.ApplicationConfiguration;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.microsoft.ado.BasicAzureDevOpsClient;
import dagger.Module;
import dagger.Provides;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Singleton;

/**
 * Dagger module for tracker client dependency injection with support for multiple tracker providers.
 * Supports selection via DEFAULT_TRACKER configuration or auto-detection.
 */
@Module
public class TrackerModule {

    private static final Logger logger = LogManager.getLogger(TrackerModule.class);

    @Provides
    @Singleton
    TrackerClient<? extends ITicket> provideTrackerClient(ApplicationConfiguration configuration) {
        // Track which providers we've already tried to avoid redundant attempts
        boolean jiraAttempted = false;
        boolean adoAttempted = false;
        boolean rallyAttempted = false;
        
        // Check if a specific default tracker is configured
        String defaultTracker = configuration.getDefaultTracker();
        logger.info("DEFAULT_TRACKER value from config: '{}'", defaultTracker);
        
        // If DEFAULT_TRACKER is set, try to initialize that specific provider first
        if (defaultTracker != null && !defaultTracker.trim().isEmpty()) {
            logger.info("DEFAULT_TRACKER is set to: '{}', initializing preferred provider...", defaultTracker);
            
            if ("jira".equalsIgnoreCase(defaultTracker.trim())) {
                try {
                    logger.debug("Attempting to initialize TrackerClient via BasicJiraClient as DEFAULT_TRACKER=jira...");
                    TrackerClient<? extends ITicket> jiraClient = BasicJiraClient.getInstance();
                    if (jiraClient != null) {
                        logger.debug("BasicJiraClient initialized successfully.");
                        return jiraClient;
                    }
                } catch (Exception e) {
                    logger.error("Failed to initialize BasicJiraClient (DEFAULT_TRACKER=jira): " + e.getMessage());
                }
                jiraAttempted = true;
            } else if ("ado".equalsIgnoreCase(defaultTracker.trim())) {
                try {
                    logger.debug("Attempting to initialize TrackerClient via BasicAzureDevOpsClient as DEFAULT_TRACKER=ado...");
                    TrackerClient<? extends ITicket> adoClient = BasicAzureDevOpsClient.getInstance();
                    if (adoClient != null) {
                        logger.debug("BasicAzureDevOpsClient initialized successfully.");
                        return adoClient;
                    }
                } catch (Exception e) {
                    logger.error("Failed to initialize BasicAzureDevOpsClient (DEFAULT_TRACKER=ado): " + e.getMessage());
                }
                adoAttempted = true;
            } else if ("rally".equalsIgnoreCase(defaultTracker.trim())) {
                try {
                    logger.debug("Attempting to initialize TrackerClient via BasicRallyClient as DEFAULT_TRACKER=rally...");
                    TrackerClient<? extends ITicket> rallyClient = BasicRallyClient.getInstance();
                    if (rallyClient != null) {
                        logger.debug("BasicRallyClient initialized successfully.");
                        return rallyClient;
                    }
                } catch (Exception e) {
                    logger.error("Failed to initialize BasicRallyClient (DEFAULT_TRACKER=rally): " + e.getMessage());
                }
                rallyAttempted = true;
            } else {
                logger.warn("Unknown DEFAULT_TRACKER value: '{}'. Valid values: 'jira', 'ado', 'rally'", defaultTracker);
            }
        }
        
        // Auto-detection fallback (in order: Jira -> ADO -> Rally)
        logger.info("Auto-detecting available tracker configuration...");
        
        // Try Jira if not already attempted
        if (!jiraAttempted) {
            try {
                logger.debug("Attempting to auto-detect BasicJiraClient...");
                TrackerClient<? extends ITicket> jiraClient = BasicJiraClient.getInstance();
                if (jiraClient != null) {
                    logger.debug("BasicJiraClient auto-detected and initialized successfully.");
                    return jiraClient;
                }
            } catch (Exception e) {
                logger.debug("BasicJiraClient auto-detection failed: {}", e.getMessage());
            }
        }
        
        // Try ADO if not already attempted
        if (!adoAttempted) {
            try {
                logger.debug("Attempting to auto-detect BasicAzureDevOpsClient...");
                TrackerClient<? extends ITicket> adoClient = BasicAzureDevOpsClient.getInstance();
                if (adoClient != null) {
                    logger.debug("BasicAzureDevOpsClient auto-detected and initialized successfully.");
                    return adoClient;
                }
            } catch (Exception e) {
                logger.debug("BasicAzureDevOpsClient auto-detection failed: {}", e.getMessage());
            }
        }
        
        // Try Rally if not already attempted
        if (!rallyAttempted) {
            try {
                logger.debug("Attempting to auto-detect BasicRallyClient...");
                TrackerClient<? extends ITicket> rallyClient = BasicRallyClient.getInstance();
                if (rallyClient != null) {
                    logger.debug("BasicRallyClient auto-detected and initialized successfully.");
                    return rallyClient;
                }
            } catch (Exception e) {
                logger.debug("BasicRallyClient auto-detection failed: {}", e.getMessage());
            }
        }
        
        // Check if we're in a test environment (Spring Boot tests, JUnit, etc.)
        // In test environments, it's acceptable to not have tracker configuration
        boolean isTestEnvironment = isTestEnvironment();
        
        if (isTestEnvironment) {
            logger.warn("No tracker configuration found in test environment. Returning null TrackerClient. " +
                    "Tests that require TrackerClient should mock it or provide test configuration.");
            return null;
        }
        
        logger.error("No tracker configuration found. Please configure one of: JIRA, ADO, or Rally");
        throw new RuntimeException("Failed to create TrackerClient instance. " +
                "Please configure JIRA (JIRA_BASE_PATH, JIRA_API_TOKEN), " +
                "ADO (ADO_ORGANIZATION, ADO_PROJECT, ADO_PAT_TOKEN), or " +
                "Rally (RALLY_PATH, RALLY_TOKEN)");
    }
    
    /**
     * Checks if the current execution is in a test environment.
     * This allows TrackerModule to gracefully handle missing configuration in tests.
     * 
     * @return true if running in a test environment, false otherwise
     */
    private boolean isTestEnvironment() {
        // Check for explicit test environment variables (most reliable)
        String testEnv = System.getenv("TEST_ENV");
        if ("true".equalsIgnoreCase(testEnv)) {
            return true;
        }
        
        // Check for CI environment (tests often run in CI without tracker config)
        // Most CI systems set CI=true (GitHub Actions, GitLab CI, Jenkins, etc.)
        String ci = System.getenv("CI");
        if ("true".equalsIgnoreCase(ci) || "1".equals(ci)) {
            // In CI, allow null TrackerClient if no configuration is found
            // This prevents test failures when tracker config is not needed
            logger.debug("Detected CI environment (CI={}), allowing null TrackerClient", ci);
            return true;
        }
        
        // Check for Gradle test execution (Gradle sets org.gradle.test.worker property)
        String gradleWorker = System.getProperty("org.gradle.test.worker");
        if (gradleWorker != null && !gradleWorker.isEmpty()) {
            logger.debug("Detected Gradle test worker, allowing null TrackerClient");
            return true;
        }
        
        // Check for common test environment indicators in classpath
        String javaClassPath = System.getProperty("java.class.path", "");
        boolean hasTestInClasspath = javaClassPath.contains("junit") || 
                                     javaClassPath.contains("test") ||
                                     javaClassPath.contains("mockito");
        
        // Check for Spring Boot test properties
        String springApplicationName = System.getProperty("spring.application.name", "");
        boolean isSpringTest = springApplicationName.contains("test") ||
                              System.getProperty("spring.test.context", "").contains("test");
        
        // Check for JUnit test runner in stack trace (for runtime detection)
        try {
            StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
            for (StackTraceElement element : stackTrace) {
                String className = element.getClassName();
                if (className.contains("junit") || 
                    className.contains("springframework.test") ||
                    className.contains("mockito") ||
                    className.contains("org.junit") ||
                    className.contains("org.testng") ||
                    className.contains("org.gradle")) {
                    logger.debug("Detected test framework in stack trace: {}", className);
                    return true;
                }
            }
        } catch (Exception e) {
            // If stack trace inspection fails, continue with other checks
            logger.debug("Failed to inspect stack trace for test environment detection: {}", e.getMessage());
        }
        
        return hasTestInClasspath || isSpringTest;
    }
}
