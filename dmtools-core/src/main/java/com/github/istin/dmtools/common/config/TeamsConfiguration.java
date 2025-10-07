package com.github.istin.dmtools.common.config;

/**
 * Configuration interface for Microsoft Teams settings.
 */
public interface TeamsConfiguration {
    /**
     * Gets the Microsoft Graph API base path
     * @return The Microsoft Graph API base path (default: https://graph.microsoft.com/v1.0)
     */
    String getTeamsBasePath();

    /**
     * Gets the Azure App Registration client ID
     * @return The client ID for OAuth 2.0 authentication
     */
    String getTeamsClientId();

    /**
     * Gets the tenant ID for Azure AD authentication
     * @return The tenant ID (default: "common" for multi-tenant)
     */
    String getTenantId();

    /**
     * Gets the port for localhost redirect during browser-based authentication
     * @return The redirect port (default: 8080)
     */
    String getTeamsAuthPort();

    /**
     * Gets the pre-configured refresh token (optional)
     * @return The refresh token, or null if not configured
     */
    String getTeamsRefreshToken();

    /**
     * Gets the authentication method to use
     * @return Authentication method: "browser", "device", or "refresh_token"
     */
    String getTeamsAuthMethod();

    /**
     * Gets the OAuth 2.0 scopes required for Teams API access
     * @return Comma-separated list of scopes
     */
    String getTeamsScopes();

    /**
     * Gets the path where token cache should be stored
     * @return The token cache path (default: ./teams.token)
     */
    String getTeamsTokenCachePath();
}
