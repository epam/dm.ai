package com.github.istin.dmtools.microsoft.common.auth;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import okhttp3.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import java.awt.Desktop;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * OAuth 2.0 authentication flow implementation for Microsoft Graph API.
 * Supports browser-based flow, device code flow, and token refresh.
 */
public class OAuth2AuthenticationFlow {
    private static final Logger logger = LogManager.getLogger(OAuth2AuthenticationFlow.class);
    
    private static final String AUTHORIZATION_ENDPOINT = "https://login.microsoftonline.com/%s/oauth2/v2.0/authorize";
    private static final String TOKEN_ENDPOINT = "https://login.microsoftonline.com/%s/oauth2/v2.0/token";
    private static final String DEVICE_CODE_ENDPOINT = "https://login.microsoftonline.com/%s/oauth2/v2.0/devicecode";
    
    private final OkHttpClient httpClient;
    
    public OAuth2AuthenticationFlow() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
    }
    
    /**
     * Authenticates using browser-based flow with localhost redirect.
     * 
     * @param clientId Azure App Registration client ID
     * @param tenantId Tenant ID (use "common" for multi-tenant)
     * @param scopes Space-separated list of OAuth scopes
     * @param redirectPort Port for localhost redirect (default: 8080)
     * @return TokenResponse containing access and refresh tokens
     * @throws IOException if authentication fails
     */
    public TokenResponse authenticateViaBrowser(String clientId, String tenantId, String scopes, int redirectPort) throws IOException {
        String redirectUri = "http://localhost:" + redirectPort;
        String state = generateRandomState();
        
        // Build authorization URL
        String authUrl = String.format(AUTHORIZATION_ENDPOINT, tenantId) +
                "?client_id=" + URLEncoder.encode(clientId, "UTF-8") +
                "&response_type=code" +
                "&redirect_uri=" + URLEncoder.encode(redirectUri, "UTF-8") +
                "&scope=" + URLEncoder.encode(scopes, "UTF-8") +
                "&state=" + state +
                "&response_mode=query";
        
        logger.info("Starting browser authentication flow...");
        logger.info("Opening browser for authentication: {}", authUrl);
        
        // Start local HTTP server to receive callback
        CompletableFuture<String> authCodeFuture = new CompletableFuture<>();
        HttpServer server;
        try {
            server = HttpServer.create(new InetSocketAddress(redirectPort), 0);
        } catch (java.net.BindException e) {
            throw new IOException("Failed to start local server on port " + redirectPort + 
                    ". Port is already in use. Please close the application using this port or configure a different port using TEAMS_AUTH_PORT environment variable.", e);
        }
        
        server.createContext("/", exchange -> {
            handleAuthCallback(exchange, authCodeFuture, state);
        });
        
        server.setExecutor(null);
        server.start();
        
        try {
            // Open browser for authentication
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(authUrl));
            } else {
                logger.info("Please open this URL in your browser:");
                logger.info(authUrl);
            }
            
            // Wait for auth code (timeout after 5 minutes)
            String authCode = authCodeFuture.get(5, TimeUnit.MINUTES);
            
            // Exchange auth code for tokens
            return exchangeAuthCodeForTokens(clientId, tenantId, authCode, redirectUri);
            
        } catch (Exception e) {
            throw new IOException("Browser authentication failed: " + e.getMessage(), e);
        } finally {
            server.stop(0);
        }
    }
    
    /**
     * Handles the OAuth callback from the browser.
     */
    private void handleAuthCallback(HttpExchange exchange, CompletableFuture<String> authCodeFuture, String expectedState) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        String response;
        int statusCode;
        
        if (query != null && query.contains("code=")) {
            // Parse query parameters
            String[] params = query.split("&");
            String code = null;
            String state = null;
            
            for (String param : params) {
                String[] kv = param.split("=", 2);
                if (kv.length == 2) {
                    if (kv[0].equals("code")) {
                        code = kv[1];
                    } else if (kv[0].equals("state")) {
                        state = kv[1];
                    }
                }
            }
            
            if (code != null && expectedState.equals(state)) {
                authCodeFuture.complete(code);
                response = "<html><body><h1>Authentication Successful!</h1><p>You can close this window and return to the application.</p></body></html>";
                statusCode = 200;
            } else {
                authCodeFuture.completeExceptionally(new IOException("Invalid state parameter"));
                response = "<html><body><h1>Authentication Failed</h1><p>Invalid state parameter.</p></body></html>";
                statusCode = 400;
            }
        } else if (query != null && query.contains("error=")) {
            authCodeFuture.completeExceptionally(new IOException("Authentication error: " + query));
            response = "<html><body><h1>Authentication Failed</h1><p>Error: " + query + "</p></body></html>";
            statusCode = 400;
        } else {
            response = "<html><body><h1>Invalid Request</h1></body></html>";
            statusCode = 400;
        }
        
        exchange.sendResponseHeaders(statusCode, response.length());
        OutputStream os = exchange.getResponseBody();
        os.write(response.getBytes());
        os.close();
    }
    
    /**
     * Authenticates using device code flow (for headless environments).
     * 
     * @param clientId Azure App Registration client ID
     * @param tenantId Tenant ID (use "common" for multi-tenant)
     * @param scopes Space-separated list of OAuth scopes
     * @return TokenResponse containing access and refresh tokens
     * @throws IOException if authentication fails
     */
    public TokenResponse authenticateViaDeviceCode(String clientId, String tenantId, String scopes) throws IOException {
        // Request device code
        RequestBody deviceCodeBody = new FormBody.Builder()
                .add("client_id", clientId)
                .add("scope", scopes)
                .build();
        
        Request deviceCodeRequest = new Request.Builder()
                .url(String.format(DEVICE_CODE_ENDPOINT, tenantId))
                .post(deviceCodeBody)
                .build();
        
        try (Response response = httpClient.newCall(deviceCodeRequest).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Device code request failed: " + response.code() + " " + response.message());
            }
            
            String responseBody = response.body().string();
            JSONObject json = new JSONObject(responseBody);
            
            String deviceCode = json.getString("device_code");
            String userCode = json.getString("user_code");
            String verificationUri = json.getString("verification_uri");
            int expiresIn = json.getInt("expires_in");
            int interval = json.optInt("interval", 5);
            
            String separator = "============================================================";
            logger.info(separator);
            logger.info("Device Code Authentication");
            logger.info(separator);
            logger.info("Please visit: {}", verificationUri);
            logger.info("Enter code: {}", userCode);
            logger.info(separator);
            
            // Poll for token
            return pollForDeviceToken(clientId, tenantId, deviceCode, interval, expiresIn);
        }
    }
    
    /**
     * Polls the token endpoint until the user completes device code authentication.
     */
    private TokenResponse pollForDeviceToken(String clientId, String tenantId, String deviceCode, int interval, int expiresIn) throws IOException {
        long startTime = System.currentTimeMillis();
        long timeoutMs = expiresIn * 1000L;
        
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            try {
                Thread.sleep(interval * 1000L);
            } catch (InterruptedException e) {
                throw new IOException("Device code polling interrupted", e);
            }
            
            RequestBody tokenBody = new FormBody.Builder()
                    .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                    .add("client_id", clientId)
                    .add("device_code", deviceCode)
                    .build();
            
            Request tokenRequest = new Request.Builder()
                    .url(String.format(TOKEN_ENDPOINT, tenantId))
                    .post(tokenBody)
                    .build();
            
            try (Response response = httpClient.newCall(tokenRequest).execute()) {
                String responseBody = response.body().string();
                JSONObject json = new JSONObject(responseBody);
                
                if (response.isSuccessful()) {
                    // Authentication successful
                    return parseTokenResponse(json);
                } else if (json.optString("error").equals("authorization_pending")) {
                    // User hasn't completed auth yet, continue polling
                    logger.debug("Waiting for user to complete authentication...");
                    continue;
                } else if (json.optString("error").equals("slow_down")) {
                    // Increase polling interval
                    interval += 5;
                    continue;
                } else {
                    // Other error
                    throw new IOException("Device code authentication failed: " + responseBody);
                }
            }
        }
        
        throw new IOException("Device code authentication timeout");
    }
    
    /**
     * Refreshes the access token using a refresh token.
     * 
     * @param refreshToken The refresh token
     * @param clientId Azure App Registration client ID
     * @param tenantId Tenant ID
     * @return TokenResponse containing new access token
     * @throws IOException if token refresh fails
     */
    public TokenResponse refreshAccessToken(String refreshToken, String clientId, String tenantId) throws IOException {
        RequestBody tokenBody = new FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("client_id", clientId)
                .add("refresh_token", refreshToken)
                .build();
        
        Request tokenRequest = new Request.Builder()
                .url(String.format(TOKEN_ENDPOINT, tenantId))
                .post(tokenBody)
                .build();
        
        try (Response response = httpClient.newCall(tokenRequest).execute()) {
            String responseBody = response.body().string();
            
            if (!response.isSuccessful()) {
                throw new IOException("Token refresh failed: " + response.code() + " " + responseBody);
            }
            
            JSONObject json = new JSONObject(responseBody);
            return parseTokenResponse(json);
        }
    }
    
    /**
     * Exchanges authorization code for access and refresh tokens.
     */
    private TokenResponse exchangeAuthCodeForTokens(String clientId, String tenantId, String authCode, String redirectUri) throws IOException {
        RequestBody tokenBody = new FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("client_id", clientId)
                .add("code", authCode)
                .add("redirect_uri", redirectUri)
                .build();
        
        Request tokenRequest = new Request.Builder()
                .url(String.format(TOKEN_ENDPOINT, tenantId))
                .post(tokenBody)
                .build();
        
        try (Response response = httpClient.newCall(tokenRequest).execute()) {
            String responseBody = response.body().string();
            
            if (!response.isSuccessful()) {
                throw new IOException("Token exchange failed: " + response.code() + " " + responseBody);
            }
            
            JSONObject json = new JSONObject(responseBody);
            return parseTokenResponse(json);
        }
    }
    
    /**
     * Parses token response JSON into TokenResponse object.
     */
    private TokenResponse parseTokenResponse(JSONObject json) {
        String accessToken = json.getString("access_token");
        String refreshToken = json.optString("refresh_token", null);
        int expiresIn = json.getInt("expires_in");
        
        return new TokenResponse(accessToken, refreshToken, expiresIn);
    }
    
    /**
     * Generates a random state parameter for CSRF protection.
     */
    private String generateRandomState() {
        return Long.toHexString(System.currentTimeMillis()) + Long.toHexString(Double.doubleToLongBits(Math.random()));
    }
    
    /**
     * Response object containing OAuth tokens.
     */
    public static class TokenResponse {
        private final String accessToken;
        private final String refreshToken;
        private final int expiresIn;
        
        public TokenResponse(String accessToken, String refreshToken, int expiresIn) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.expiresIn = expiresIn;
        }
        
        public String getAccessToken() {
            return accessToken;
        }
        
        public String getRefreshToken() {
            return refreshToken;
        }
        
        public int getExpiresIn() {
            return expiresIn;
        }
    }
}
