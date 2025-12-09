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
        
        logger.error("No tracker configuration found. Please configure one of: JIRA, ADO, or Rally");
        throw new RuntimeException("Failed to create TrackerClient instance. " +
                "Please configure JIRA (JIRA_BASE_PATH, JIRA_API_TOKEN), " +
                "ADO (ADO_ORGANIZATION, ADO_PROJECT, ADO_PAT_TOKEN), or " +
                "Rally (RALLY_PATH, RALLY_TOKEN)");
    }
}
