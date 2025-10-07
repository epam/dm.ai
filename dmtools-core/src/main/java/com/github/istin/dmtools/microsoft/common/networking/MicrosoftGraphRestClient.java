package com.github.istin.dmtools.microsoft.common.networking;

import com.github.istin.dmtools.microsoft.common.auth.OAuth2AuthenticationFlow;
import com.github.istin.dmtools.microsoft.common.auth.TokenCache;
import com.github.istin.dmtools.networking.AbstractRestClient;
import okhttp3.Request;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

/**
 * Base REST client for Microsoft Graph API integrations.
 * Implements OAuth 2.0 Bearer token authentication with automatic token refresh.
 * This class can be extended for Teams, Outlook, OneDrive, and other Microsoft Graph integrations.
 */
public abstract class MicrosoftGraphRestClient extends AbstractRestClient {
    private static final Logger logger = LogManager.getLogger(MicrosoftGraphRestClient.class);
    
    protected final TokenCache tokenCache;
    protected final OAuth2AuthenticationFlow authFlow;
    protected final String clientId;
    protected final String tenantId;
    protected final String scopes;
    protected final String authMethod;
    protected final int authPort;
    protected final String preConfiguredRefreshToken;
    
    /**
     * Creates a Microsoft Graph REST client with OAuth 2.0 authentication.
     * 
     * @param basePath Microsoft Graph API base path (e.g., https://graph.microsoft.com/v1.0)
     * @param clientId Azure App Registration client ID
     * @param tenantId Tenant ID (use "common" for multi-tenant)
     * @param scopes OAuth 2.0 scopes (space-separated)
     * @param authMethod Authentication method: "browser", "device", or "refresh_token"
     * @param authPort Port for localhost redirect (browser flow)
     * @param tokenCachePath Path to token cache file
     * @param preConfiguredRefreshToken Optional pre-configured refresh token
     * @throws IOException if initialization fails
     */
    public MicrosoftGraphRestClient(
            String basePath,
            String clientId,
            String tenantId,
            String scopes,
            String authMethod,
            int authPort,
            String tokenCachePath,
            String preConfiguredRefreshToken) throws IOException {
        super(basePath, null); // authorization will be set via token
        
        this.clientId = clientId;
        this.tenantId = tenantId;
        this.scopes = scopes;
        this.authMethod = authMethod;
        this.authPort = authPort;
        this.preConfiguredRefreshToken = preConfiguredRefreshToken;
        this.tokenCache = new TokenCache(tokenCachePath);
        this.authFlow = new OAuth2AuthenticationFlow();
        
        // Ensure we have a valid access token
        ensureValidAccessToken();
    }
    
    /**
     * Ensures a valid access token is available, refreshing or acquiring as needed.
     * 
     * @throws IOException if token acquisition fails
     */
    protected void ensureValidAccessToken() throws IOException {
        // Check if we have a valid cached token
        if (tokenCache.hasValidAccessToken()) {
            logger.debug("Using cached access token");
            return;
        }
        
        // Try to refresh using cached refresh token
        if (tokenCache.hasRefreshToken()) {
            logger.info("Access token expired, refreshing...");
            try {
                refreshToken();
                return;
            } catch (IOException e) {
                logger.warn("Token refresh failed, will try alternative authentication: {}", e.getMessage());
            }
        }
        
        // Acquire new token based on authentication method
        acquireNewToken();
    }
    
    /**
     * Acquires a new token using the configured authentication method.
     * 
     * @throws IOException if token acquisition fails
     */
    private void acquireNewToken() throws IOException {
        OAuth2AuthenticationFlow.TokenResponse tokenResponse;
        
        if ("refresh_token".equals(authMethod) && preConfiguredRefreshToken != null) {
            // Use pre-configured refresh token
            logger.info("Acquiring token using pre-configured refresh token");
            tokenResponse = authFlow.refreshAccessToken(preConfiguredRefreshToken, clientId, tenantId);
            
        } else if ("device".equals(authMethod)) {
            // Use device code flow
            logger.info("Acquiring token using device code flow");
            tokenResponse = authFlow.authenticateViaDeviceCode(clientId, tenantId, scopes);
            
        } else {
            // Default to browser flow
            logger.info("Acquiring token using browser flow");
            tokenResponse = authFlow.authenticateViaBrowser(clientId, tenantId, scopes, authPort);
        }
        
        // Update cache with new tokens
        tokenCache.updateTokens(
                tokenResponse.getAccessToken(),
                tokenResponse.getRefreshToken(),
                tokenResponse.getExpiresIn()
        );
        
        logger.info("Access token acquired successfully");
    }
    
    /**
     * Refreshes the access token using the cached refresh token.
     * 
     * @throws IOException if token refresh fails
     */
    protected void refreshToken() throws IOException {
        String refreshToken = tokenCache.getRefreshToken();
        if (refreshToken == null) {
            throw new IOException("No refresh token available");
        }
        
        OAuth2AuthenticationFlow.TokenResponse tokenResponse = 
                authFlow.refreshAccessToken(refreshToken, clientId, tenantId);
        
        tokenCache.updateTokens(
                tokenResponse.getAccessToken(),
                tokenResponse.getRefreshToken(),
                tokenResponse.getExpiresIn()
        );
        
        logger.debug("Access token refreshed successfully");
    }
    
    /**
     * Signs the HTTP request with OAuth 2.0 Bearer token.
     * Proactively checks token expiration and refreshes if needed.
     */
    @Override
    public Request.Builder sign(Request.Builder builder) {
        try {
            // Proactive token refresh (checks expiration before request)
            if (tokenCache.isAccessTokenExpired()) {
                logger.debug("Token expired, refreshing before request");
                ensureValidAccessToken();
            }
            
            String accessToken = tokenCache.getAccessToken();
            if (accessToken == null) {
                logger.error("No access token available for signing request");
                throw new RuntimeException("No access token available");
            }
            
            return builder
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json");
                    
        } catch (IOException e) {
            logger.error("Failed to refresh token for signing request", e);
            throw new RuntimeException("Failed to refresh access token", e);
        }
    }
    
    /**
     * Gets the Microsoft Graph API base path.
     */
    @Override
    public String getBasePath() {
        return basePath;
    }
    
    /**
     * Builds the full path for a Microsoft Graph API endpoint.
     * 
     * @param path The API path (e.g., "/me/chats")
     * @return Full URL
     */
    @Override
    public String path(String path) {
        String base = getBasePath();
        if (path.startsWith("/") || base.endsWith("/")) {
            return base + path;
        }
        return base + "/" + path;
    }
    
    /**
     * Clears the token cache (forces re-authentication on next request).
     * 
     * @throws IOException if cache cannot be cleared
     */
    public void clearTokenCache() throws IOException {
        tokenCache.clear();
        logger.info("Token cache cleared");
    }
}
