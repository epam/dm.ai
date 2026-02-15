package com.github.istin.dmtools.ai.google.auth;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import okhttp3.Request;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

/**
 * Service Account OAuth2 authentication strategy for Vertex AI Gemini.
 * Uses Google Cloud service account credentials to obtain OAuth2 access tokens.
 * Implements token caching with automatic refresh (tokens expire after 1 hour).
 */
public class ServiceAccountAuthenticationStrategy implements GeminiAuthenticationStrategy {

    private static final Logger logger = LogManager.getLogger(ServiceAccountAuthenticationStrategy.class);

    // OAuth2 scope required for Vertex AI API access
    private static final String VERTEX_AI_SCOPE = "https://www.googleapis.com/auth/cloud-platform";

    private final GoogleCredentials credentials;
    private String cachedAccessToken;
    private long tokenExpirationTime;

    /**
     * Creates a service account authentication strategy from a credentials file.
     *
     * @param credentialsPath Path to the service account JSON file
     * @throws IOException if the credentials file cannot be loaded
     */
    public ServiceAccountAuthenticationStrategy(String credentialsPath) throws IOException {
        if (credentialsPath == null || credentialsPath.trim().isEmpty()) {
            throw new IllegalArgumentException("Credentials path cannot be null or empty");
        }

        try (InputStream credentialsStream = new FileInputStream(credentialsPath)) {
            // Load credentials and set the required scope for Vertex AI
            this.credentials = GoogleCredentials.fromStream(credentialsStream)
                    .createScoped(Collections.singleton(VERTEX_AI_SCOPE));
            logger.info("Service account credentials loaded from file: {}", credentialsPath);
            logger.debug("OAuth2 scope set for Vertex AI: {}", VERTEX_AI_SCOPE);
        }
    }

    /**
     * Creates a service account authentication strategy from JSON credentials string.
     *
     * @param credentialsJson JSON string containing service account credentials
     * @param isJsonContent Flag to distinguish from file path (must be true)
     * @throws IOException if the credentials JSON cannot be parsed
     */
    public ServiceAccountAuthenticationStrategy(String credentialsJson, boolean isJsonContent) throws IOException {
        if (!isJsonContent) {
            throw new IllegalArgumentException("Second parameter must be true to indicate JSON content");
        }
        if (credentialsJson == null || credentialsJson.trim().isEmpty()) {
            throw new IllegalArgumentException("Credentials JSON cannot be null or empty");
        }

        try (InputStream credentialsStream = new ByteArrayInputStream(credentialsJson.getBytes(StandardCharsets.UTF_8))) {
            // Load credentials and set the required scope for Vertex AI
            this.credentials = GoogleCredentials.fromStream(credentialsStream)
                    .createScoped(Collections.singleton(VERTEX_AI_SCOPE));
            logger.info("Service account credentials loaded from JSON string");
            logger.debug("OAuth2 scope set for Vertex AI: {}", VERTEX_AI_SCOPE);
        }
    }

    @Override
    public Request.Builder sign(Request.Builder builder, Map<String, String> customHeaders) {
        try {
            // Get access token (from cache or refresh if needed)
            String accessToken = getAccessToken();

            // Add OAuth2 Bearer token to Authorization header
            builder.addHeader("Authorization", "Bearer " + accessToken);

            // Add custom headers if any
            if (customHeaders != null) {
                customHeaders.forEach(builder::addHeader);
            }

            return builder;
        } catch (IOException e) {
            logger.error("Failed to obtain access token for service account authentication", e);
            throw new RuntimeException("Failed to obtain access token", e);
        }
    }

    @Override
    public Request signRequest(Request.Builder requestBuilder, String url, String body, Map<String, String> customHeaders) {
        try {
            // Get access token (from cache or refresh if needed)
            String accessToken = getAccessToken();

            // Add OAuth2 Bearer token to Authorization header
            requestBuilder.addHeader("Authorization", "Bearer " + accessToken);

            // Add custom headers if any
            if (customHeaders != null) {
                customHeaders.forEach(requestBuilder::addHeader);
            }

            return requestBuilder.build();
        } catch (IOException e) {
            logger.error("Failed to obtain access token for service account authentication", e);
            throw new RuntimeException("Failed to obtain access token", e);
        }
    }

    @Override
    public String getAuthenticationType() {
        return "SERVICE_ACCOUNT";
    }

    /**
     * Gets the access token, using cached token or refreshing if expired.
     * Thread-safe implementation with synchronized block.
     *
     * @return The access token
     * @throws IOException if token refresh fails
     */
    private synchronized String getAccessToken() throws IOException {
        long now = System.currentTimeMillis();

        // Refresh if token is null or expires in less than 5 minutes (300000ms)
        if (cachedAccessToken == null || now >= tokenExpirationTime - 300000) {
            refreshAccessToken();
            logger.debug("Access token refreshed, expires at: {}", new java.util.Date(tokenExpirationTime));
        } else {
            logger.debug("Using cached access token, expires at: {}", new java.util.Date(tokenExpirationTime));
        }

        return cachedAccessToken;
    }

    /**
     * Refreshes the access token from Google Cloud.
     *
     * @throws IOException if token refresh fails
     */
    private void refreshAccessToken() throws IOException {
        credentials.refreshIfExpired();
        AccessToken token = credentials.getAccessToken();

        if (token == null) {
            throw new IOException("Failed to obtain access token from Google credentials");
        }

        cachedAccessToken = token.getTokenValue();

        // Check if expiration time is available (can be null for some token types)
        if (token.getExpirationTime() != null) {
            tokenExpirationTime = token.getExpirationTime().getTime();
            logger.info("Access token obtained, expires at: {}", token.getExpirationTime());
        } else {
            // Default to 1 hour expiration if not provided (standard OAuth2 token lifetime)
            tokenExpirationTime = System.currentTimeMillis() + 3600000; // 1 hour
            logger.warn("Access token expiration time not provided, defaulting to 1 hour from now");
        }
    }
}
