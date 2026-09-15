// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.microsoft.sharepoint;

import com.github.istin.dmtools.common.utils.PropertyReader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

/**
 * Provides a singleton instance of SharePointClient configured from environment variables/properties.
 * Uses the same Teams authentication configuration by default.
 */
public class BasicSharePointClient {
    private static final Logger logger = LogManager.getLogger(BasicSharePointClient.class);
    
    private static SharePointClient instance;
    private static final PropertyReader propertyReader;
    
    static {
        propertyReader = new PropertyReader();
    }
    
    /**
     * Gets or creates the singleton SharePointClient instance.
     * Configuration is read from PropertyReader (environment variables or properties file).
     * 
     * Note: SharePoint uses the same authentication as Teams/OneDrive,
     * so it reuses TEAMS_* environment variables by default.
     * 
     * @return Configured SharePointClient instance
     * @throws IOException if client creation fails
     */
    public static synchronized SharePointClient getInstance() throws IOException {
        if (instance == null) {
            String clientId = propertyReader.getTeamsClientId();
            validateClientId(clientId);
            String tenantId = propertyReader.getTeamsTenantId();
            String scopes = propertyReader.getSharePointScopes(); // Uses Teams scopes + Files.Read
            String authMethod = propertyReader.getTeamsAuthMethod();
            int authPort = propertyReader.getTeamsAuthPort();
            String tokenCachePath = propertyReader.getTeamsTokenCachePath();
            String refreshToken = propertyReader.getTeamsRefreshToken();
            
            logger.info("Initializing SharePointClient with clientId: {}, tenantId: {}, authMethod: {}", 
                clientId, tenantId, authMethod);
            
            instance = new SharePointClient(
                clientId,
                tenantId,
                scopes,
                authMethod,
                authPort,
                tokenCachePath,
                refreshToken
            );
        }
        
        return instance;
    }
    
    /**
     * Validates that the Teams/SharePoint client id is configured. SharePoint
     * reuses the Teams (TEAMS_*) auth configuration; without a client id the
     * SharePointClient constructor would still start an OAuth token flow with
     * a null client id — in browser auth mode that even opens a browser window
     * for a login that can never succeed. BasicTeamsClient fails fast in the
     * same situation; mirror that here.
     */
    static void validateClientId(String clientId) {
        if (clientId == null || clientId.trim().isEmpty()) {
            throw new IllegalStateException(
                "TEAMS_CLIENT_ID environment variable is required for the SharePoint integration " +
                "(SharePoint reuses the Teams authentication configuration)."
            );
        }
    }

    /**
     * Resets the singleton instance (useful for testing or reconfiguration).
     */
    public static synchronized void reset() {
        instance = null;
    }
}



