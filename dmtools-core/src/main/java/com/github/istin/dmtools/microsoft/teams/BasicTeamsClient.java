package com.github.istin.dmtools.microsoft.teams;

import com.github.istin.dmtools.common.utils.PropertyReader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

/**
 * Basic Teams client that reads configuration from environment variables via PropertyReader.
 * Provides singleton access for MCP CLI integration.
 */
public class BasicTeamsClient extends TeamsClient {
    
    private static final Logger logger = LogManager.getLogger(BasicTeamsClient.class);
    
    private static BasicTeamsClient instance;
    private static final Object lock = new Object();
    
    /**
     * Private constructor - reads configuration from PropertyReader.
     */
    private BasicTeamsClient() throws IOException {
        super(
            getClientIdOrThrow(),
            getTenantId(),
            getScopes(),
            getAuthMethod(),
            getAuthPort(),
            getTokenCachePath(),
            getRefreshToken()
        );
        logger.info("BasicTeamsClient initialized with tenant: {}, auth method: {}", 
            getTenantId(), getAuthMethod());
    }
    
    /**
     * Gets singleton instance of BasicTeamsClient.
     * 
     * @return BasicTeamsClient instance
     * @throws IOException if configuration is missing or initialization fails
     */
    public static BasicTeamsClient getInstance() throws IOException {
        if (instance == null) {
            synchronized (lock) {
                if (instance == null) {
                    instance = new BasicTeamsClient();
                }
            }
        }
        return instance;
    }
    
    /**
     * Resets the singleton instance (useful for testing or re-authentication).
     */
    public static void resetInstance() {
        synchronized (lock) {
            instance = null;
        }
    }
    
    // Configuration helper methods
    
    private static String getClientIdOrThrow() {
        PropertyReader reader = new PropertyReader();
        String clientId = reader.getTeamsClientId();
        if (clientId == null || clientId.trim().isEmpty()) {
            throw new IllegalStateException(
                "TEAMS_CLIENT_ID environment variable is required. " +
                "Please configure your Azure App Registration client ID."
            );
        }
        return clientId;
    }
    
    private static String getTenantId() {
        PropertyReader reader = new PropertyReader();
        return reader.getTeamsTenantId();
    }
    
    private static String getScopes() {
        PropertyReader reader = new PropertyReader();
        return reader.getTeamsScopes();
    }
    
    private static String getAuthMethod() {
        PropertyReader reader = new PropertyReader();
        return reader.getTeamsAuthMethod();
    }
    
    private static int getAuthPort() {
        PropertyReader reader = new PropertyReader();
        return reader.getTeamsAuthPort();
    }
    
    private static String getTokenCachePath() {
        PropertyReader reader = new PropertyReader();
        return reader.getTeamsTokenCachePath();
    }
    
    private static String getRefreshToken() {
        PropertyReader reader = new PropertyReader();
        return reader.getTeamsRefreshToken();
    }
}



