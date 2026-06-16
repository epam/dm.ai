// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.figma;

import okhttp3.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Manages Figma OAuth2 token lifecycle: refresh, caching, and expiry.
 *
 * <p>Supports:
 * <ul>
 *   <li>Exchanging an authorization code for access + refresh tokens</li>
 *   <li>Refreshing an access token using a stored refresh token</li>
 * </ul>
 *
 * <p>Token endpoint: {@code https://api.figma.com/v1/oauth/token}
 */
public class FigmaOAuth2TokenManager {

    private static final Logger logger = LogManager.getLogger(FigmaOAuth2TokenManager.class);

    public static final String FIGMA_OAUTH_AUTHORIZE_URL = "https://www.figma.com/oauth";
    public static final String FIGMA_OAUTH_TOKEN_URL = "https://api.figma.com/v1/oauth/token";
    public static final String DEFAULT_FIGMA_OAUTH_SCOPE = "file_content:read file_metadata:read";

    private final String clientId;
    private final String clientSecret;
    private final String oauthScope;
    private final OkHttpClient httpClient;

    private String cachedAccessToken;
    private long tokenExpiryTimeMs = 0;

    public FigmaOAuth2TokenManager(String clientId, String clientSecret) {
        this(clientId, clientSecret, null);
    }

    public FigmaOAuth2TokenManager(String clientId, String clientSecret, String oauthScope) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.oauthScope = normalizeScope(oauthScope);
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Returns the authorization URL to redirect the user to for OAuth2 authorization code flow.
     *
     * @param redirectUri the redirect URI registered in your Figma OAuth app
     * @param state       random state value for CSRF protection
     * @return full authorization URL
     */
    public String buildAuthorizationUrl(String redirectUri, String state) {
        try {
            return FIGMA_OAUTH_AUTHORIZE_URL
                    + "?client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8.name())
                    + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8.name())
                    + "&scope=" + URLEncoder.encode(oauthScope, StandardCharsets.UTF_8.name())
                    + "&state=" + URLEncoder.encode(state, StandardCharsets.UTF_8.name())
                    + "&response_type=code";
        } catch (Exception e) {
            throw new RuntimeException("Failed to build authorization URL", e);
        }
    }

    /**
     * Exchanges an authorization code for access + refresh tokens.
     *
     * @param code        authorization code received from the OAuth2 callback
     * @param redirectUri redirect URI used in the authorization request
     * @return token response containing access_token, refresh_token, and expires_in
     * @throws IOException if the token exchange fails
     */
    public TokenResponse exchangeCodeForTokens(String code, String redirectUri) throws IOException {
        String body = "client_id=" + encode(clientId)
                + "&client_secret=" + encode(clientSecret)
                + "&code=" + encode(code)
                + "&redirect_uri=" + encode(redirectUri)
                + "&grant_type=authorization_code";

        return postToTokenEndpoint(body);
    }

    /**
     * Refreshes the access token using a stored refresh token.
     *
     * @param refreshToken the refresh token to use
     * @return token response containing new access_token (and possibly new refresh_token)
     * @throws IOException if the refresh fails
     */
    public TokenResponse refreshAccessToken(String refreshToken) throws IOException {
        String body = "client_id=" + encode(clientId)
                + "&client_secret=" + encode(clientSecret)
                + "&refresh_token=" + encode(refreshToken)
                + "&grant_type=refresh_token";

        return postToTokenEndpoint(body);
    }

    /**
     * Returns a valid access token, refreshing it if expired.
     * Caches the token in memory to avoid unnecessary refreshes.
     *
     * @param refreshToken the refresh token to use if access token is expired
     * @return valid access token
     * @throws IOException if the refresh fails
     */
    public synchronized String getValidAccessToken(String refreshToken) throws IOException {
        if (cachedAccessToken != null && System.currentTimeMillis() < tokenExpiryTimeMs) {
            return cachedAccessToken;
        }
        logger.info("Figma OAuth2: refreshing access token");
        TokenResponse response = refreshAccessToken(refreshToken);
        cachedAccessToken = response.getAccessToken();
        // Subtract 60 seconds as a safety margin
        long expiresIn = response.getExpiresIn() > 0 ? response.getExpiresIn() : 3600;
        tokenExpiryTimeMs = System.currentTimeMillis() + (expiresIn - 60) * 1000L;
        return cachedAccessToken;
    }

    private TokenResponse postToTokenEndpoint(String formBody) throws IOException {
        RequestBody requestBody = RequestBody.create(
                formBody, MediaType.get("application/x-www-form-urlencoded"));

        Request request = new Request.Builder()
                .url(FIGMA_OAUTH_TOKEN_URL)
                .post(requestBody)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new IOException("Figma OAuth2 token request failed [" + response.code() + "]: " + responseBody);
            }
            JSONObject json = new JSONObject(responseBody);
            return new TokenResponse(
                    json.optString("access_token"),
                    json.optString("refresh_token"),
                    json.optLong("expires_in", 3600)
            );
        }
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String normalizeScope(String scope) {
        if (scope == null || scope.trim().isEmpty()) {
            return DEFAULT_FIGMA_OAUTH_SCOPE;
        }
        return scope.trim();
    }

    public static class TokenResponse {
        private final String accessToken;
        private final String refreshToken;
        private final long expiresIn;

        public TokenResponse(String accessToken, String refreshToken, long expiresIn) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.expiresIn = expiresIn;
        }

        public String getAccessToken() { return accessToken; }
        public String getRefreshToken() { return refreshToken; }
        public long getExpiresIn() { return expiresIn; }

        @Override
        public String toString() {
            return "TokenResponse{accessToken='" + (accessToken != null ? "[SET]" : "null")
                    + "', refreshToken='" + (refreshToken != null ? "[SET]" : "null")
                    + "', expiresIn=" + expiresIn + "}";
        }
    }
}
