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
    private static final PropertyReader propertyReader;
    
    static {
        propertyReader = new PropertyReader();
    }
    
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
        String clientId = propertyReader.getTeamsClientId();
        if (clientId == null || clientId.trim().isEmpty()) {
            throw new IllegalStateException(
                "TEAMS_CLIENT_ID environment variable is required. " +
                "Please configure your Azure App Registration client ID."
            );
        }
        return clientId;
    }
    
    private static String getTenantId() {
        return propertyReader.getTeamsTenantId();
    }
    
    private static String getScopes() {
        return propertyReader.getTeamsScopes();
    }
    
    private static String getAuthMethod() {
        return propertyReader.getTeamsAuthMethod();
    }
    
    private static int getAuthPort() {
        return propertyReader.getTeamsAuthPort();
    }
    
    private static String getTokenCachePath() {
        return propertyReader.getTeamsTokenCachePath();
    }
    
    private static String getRefreshToken() {
        return propertyReader.getTeamsRefreshToken();
    }
}



