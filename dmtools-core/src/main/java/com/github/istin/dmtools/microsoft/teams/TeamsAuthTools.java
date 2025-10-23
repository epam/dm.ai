package com.github.istin.dmtools.microsoft.teams;

import com.github.istin.dmtools.common.utils.PropertyReader;
import com.github.istin.dmtools.mcp.MCPTool;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

/**
 * Microsoft Teams authentication tools for MCP.
 * Provides separate authentication flow management independent of the main Teams client.
 * Persists authentication state to a file to work across multiple command invocations.
 */
public class TeamsAuthTools {
    
    private static final Logger logger = LogManager.getLogger(TeamsAuthTools.class);
    
    // File to persist device code auth state between command invocations
    private static final String PENDING_AUTH_FILE = System.getProperty("user.home") + "/.dmtools/.teams-pending-auth.json";
    
    /**
     * Saves pending authentication state to a file for persistence across command invocations.
     */
    private void savePendingAuth(String deviceCode, String clientId, String tenantId) throws IOException {
        JSONObject state = new JSONObject();
        state.put("device_code", deviceCode);
        state.put("client_id", clientId);
        state.put("tenant_id", tenantId);
        state.put("timestamp", System.currentTimeMillis());
        
        File file = new File(PENDING_AUTH_FILE);
        file.getParentFile().mkdirs(); // Ensure directory exists
        Files.write(file.toPath(), state.toString(2).getBytes(), 
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        logger.debug("Saved pending auth state to: {}", PENDING_AUTH_FILE);
    }
    
    /**
     * Loads pending authentication state from file.
     * Returns null if no pending auth or if expired (> 15 minutes old).
     */
    private JSONObject loadPendingAuth() {
        File file = new File(PENDING_AUTH_FILE);
        if (!file.exists()) {
            return null;
        }
        
        try {
            String content = new String(Files.readAllBytes(file.toPath()));
            JSONObject state = new JSONObject(content);
            
            // Check if expired (15 minutes = 900 seconds)
            long timestamp = state.optLong("timestamp", 0);
            long age = (System.currentTimeMillis() - timestamp) / 1000;
            if (age > 900) {
                logger.warn("Pending auth state expired (age: {} seconds)", age);
                file.delete();
                return null;
            }
            
            logger.debug("Loaded pending auth state from: {}", PENDING_AUTH_FILE);
            return state;
        } catch (Exception e) {
            logger.warn("Failed to load pending auth state: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Clears pending authentication state file.
     */
    private void clearPendingAuth() {
        File file = new File(PENDING_AUTH_FILE);
        if (file.exists()) {
            file.delete();
            logger.debug("Cleared pending auth state");
        }
    }
    
    /**
     * Initiates device code authentication flow for Microsoft Teams.
     * Returns the device code and verification URL that the user needs to visit.
     * 
     * @return JSON string with verification_url, user_code, and expires_in
     * @throws IOException if authentication initiation fails
     */
    @MCPTool(
        name = "teams_auth_start",
        description = "Start device code authentication for Microsoft Teams. Returns URL and code for user to authenticate.",
        integration = "teams_auth",
        category = "authentication"
    )
    public String startDeviceCodeAuth() throws IOException {
        PropertyReader reader = new PropertyReader();
        String clientId = reader.getTeamsClientId();
        String tenantId = reader.getTeamsTenantId();
        String scopes = reader.getTeamsScopes();
        
        if (clientId == null || clientId.trim().isEmpty()) {
            throw new IOException("TEAMS_CLIENT_ID not configured. Please set the environment variable.");
        }
        
        // Request device code
        String deviceCodeEndpoint = String.format(
            "https://login.microsoftonline.com/%s/oauth2/v2.0/devicecode", 
            tenantId
        );
        
        okhttp3.OkHttpClient httpClient = new okhttp3.OkHttpClient.Builder()
            .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build();
        
        okhttp3.RequestBody deviceCodeBody = new okhttp3.FormBody.Builder()
            .add("client_id", clientId)
            .add("scope", scopes)
            .build();
        
        okhttp3.Request deviceCodeRequest = new okhttp3.Request.Builder()
            .url(deviceCodeEndpoint)
            .post(deviceCodeBody)
            .build();
        
        try (okhttp3.Response response = httpClient.newCall(deviceCodeRequest).execute()) {
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
            
            // Save state to file for persistence across command invocations
            savePendingAuth(deviceCode, clientId, tenantId);
            
            // Return user-friendly response
            JSONObject result = new JSONObject();
            result.put("verification_url", verificationUri);
            result.put("user_code", userCode);
            result.put("expires_in", expiresIn);
            result.put("interval", interval);
            result.put("message", String.format(
                "Please visit %s and enter code: %s\n" +
                "After approving, call teams_auth_complete to finish authentication.",
                verificationUri, userCode
            ));
            
            logger.info("Device code authentication started. User code: {}", userCode);
            return result.toString(2);
        }
    }
    
    /**
     * Completes device code authentication by polling for the access token.
     * Should be called after the user has approved the authentication in their browser.
     * 
     * @return JSON string with authentication status and refresh token
     * @throws IOException if authentication completion fails
     */
    @MCPTool(
        name = "teams_auth_complete",
        description = "Complete device code authentication after user approval. Polls for tokens and saves refresh token.",
        integration = "teams_auth",
        category = "authentication"
    )
    public String completeDeviceCodeAuth() throws IOException {
        // Load pending auth state from file
        JSONObject pendingAuth = loadPendingAuth();
        if (pendingAuth == null) {
            throw new IOException(
                "No pending authentication. Please call teams_auth_start first."
            );
        }
        
        String pendingDeviceCode = pendingAuth.getString("device_code");
        String pendingClientId = pendingAuth.getString("client_id");
        String pendingTenantId = pendingAuth.getString("tenant_id");
        
        String tokenEndpoint = String.format(
            "https://login.microsoftonline.com/%s/oauth2/v2.0/token",
            pendingTenantId
        );
        
        okhttp3.OkHttpClient httpClient = new okhttp3.OkHttpClient.Builder()
            .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build();
        
        // Poll for token (try a few times)
        int maxAttempts = 3;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            okhttp3.RequestBody tokenBody = new okhttp3.FormBody.Builder()
                .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                .add("client_id", pendingClientId)
                .add("device_code", pendingDeviceCode)
                .build();
            
            okhttp3.Request tokenRequest = new okhttp3.Request.Builder()
                .url(tokenEndpoint)
                .post(tokenBody)
                .build();
            
            try (okhttp3.Response response = httpClient.newCall(tokenRequest).execute()) {
                String responseBody = response.body().string();
                JSONObject json = new JSONObject(responseBody);
                
                if (response.isSuccessful()) {
                    // Authentication successful!
                    String refreshToken = json.optString("refresh_token", null);
                    int expiresIn = json.getInt("expires_in");
                    
                    // Clear pending state file
                    clearPendingAuth();
                    
                    // Reset BasicTeamsClient instance to force re-initialization with new token
                    BasicTeamsClient.resetInstance();
                    
                    JSONObject result = new JSONObject();
                    result.put("status", "success");
                    result.put("message", "Authentication successful!");
                    result.put("expires_in", expiresIn);
                    if (refreshToken != null) {
                        result.put("refresh_token", refreshToken);
                        result.put("instruction", 
                            "Save this refresh token as TEAMS_REFRESH_TOKEN environment variable for future use:\n" +
                            "export TEAMS_REFRESH_TOKEN=\"" + refreshToken + "\"");
                    }
                    
                    logger.info("Device code authentication completed successfully");
                    return result.toString(2);
                    
                } else if (json.optString("error").equals("authorization_pending")) {
                    // User hasn't completed auth yet
                    if (attempt < maxAttempts - 1) {
                        logger.debug("Waiting for user to complete authentication... (attempt {})", attempt + 1);
                        Thread.sleep(5000); // Wait 5 seconds before next attempt
                        continue;
                    } else {
                        JSONObject result = new JSONObject();
                        result.put("status", "pending");
                        result.put("message", 
                            "User has not completed authentication yet. " +
                            "Please approve the authentication in your browser and call this again.");
                        return result.toString(2);
                    }
                } else {
                    // Other error
                    throw new IOException("Authentication failed: " + responseBody);
                }
            } catch (InterruptedException e) {
                throw new IOException("Authentication interrupted", e);
            }
        }
        
        // If we get here, user hasn't approved yet
        JSONObject result = new JSONObject();
        result.put("status", "pending");
        result.put("message", 
            "User has not completed authentication yet. " +
            "Please approve the authentication in your browser and call this again.");
        return result.toString(2);
    }
    
    /**
     * Checks the current authentication status for Microsoft Teams.
     * 
     * @return JSON string with authentication status
     */
    @MCPTool(
        name = "teams_auth_status",
        description = "Check current Microsoft Teams authentication status and configuration.",
        integration = "teams_auth",
        category = "authentication"
    )
    public String checkAuthStatus() {
        PropertyReader reader = new PropertyReader();
        String clientId = reader.getTeamsClientId();
        String refreshToken = reader.getTeamsRefreshToken();
        String tokenCachePath = reader.getTeamsTokenCachePath();
        
        // Check for pending auth from file
        JSONObject pendingAuth = loadPendingAuth();
        
        JSONObject result = new JSONObject();
        result.put("client_id_configured", clientId != null && !clientId.trim().isEmpty());
        result.put("refresh_token_configured", refreshToken != null && !refreshToken.trim().isEmpty());
        result.put("token_cache_path", tokenCachePath);
        result.put("pending_device_auth", pendingAuth != null);
        
        if (clientId == null || clientId.trim().isEmpty()) {
            result.put("message", "TEAMS_CLIENT_ID not configured. Set environment variable to proceed.");
        } else if (refreshToken == null || refreshToken.trim().isEmpty()) {
            result.put("message", 
                "No refresh token configured. Use teams_auth_start to begin authentication.");
        } else {
            result.put("message", "Authentication configured. Teams client should be ready to use.");
        }
        
        return result.toString(2);
    }
}

