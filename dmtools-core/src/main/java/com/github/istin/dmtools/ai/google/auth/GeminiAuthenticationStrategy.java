package com.github.istin.dmtools.ai.google.auth;

import okhttp3.Request;

import java.util.Map;

/**
 * Interface for Gemini authentication strategies.
 * Different implementations handle API Key or Service Account (OAuth2) authentication.
 */
public interface GeminiAuthenticationStrategy {

    /**
     * Signs a request builder with authentication headers or query parameters.
     * For API Key, this adds the key as a query parameter.
     * For Service Account (OAuth2), this adds the Authorization header with Bearer token.
     *
     * @param builder The request builder to sign
     * @param customHeaders Custom headers to add
     * @return The signed request builder
     */
    Request.Builder sign(Request.Builder builder, Map<String, String> customHeaders);

    /**
     * Signs a complete request.
     * This is called from the post() method for authentication.
     *
     * @param requestBuilder The request builder
     * @param url The request URL
     * @param body The request body
     * @param customHeaders Custom headers to add
     * @return The signed request
     */
    Request signRequest(Request.Builder requestBuilder, String url, String body, Map<String, String> customHeaders);

    /**
     * Returns the authentication type name for logging/debugging.
     *
     * @return The authentication type name (e.g., "API_KEY", "SERVICE_ACCOUNT")
     */
    String getAuthenticationType();
}
