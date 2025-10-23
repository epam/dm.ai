package com.github.istin.dmtools.microsoft.common.auth;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashSet;
import java.util.Set;

/**
 * Token cache manager for OAuth 2.0 tokens.
 * Handles serialization, deserialization, and expiration checking.
 */
public class TokenCache {
    private static final Logger logger = LogManager.getLogger(TokenCache.class);
    private static final int EXPIRATION_BUFFER_SECONDS = 300; // 5 minutes buffer before token expiration
    
    private final File cacheFile;
    private String accessToken;
    private String refreshToken;
    private long expiresAt; // timestamp in milliseconds
    
    /**
     * Creates a token cache with the specified file path.
     * 
     * @param cachePath Path to the token cache file
     * @throws IOException if cache directory cannot be created
     */
    public TokenCache(String cachePath) throws IOException {
        this.cacheFile = new File(cachePath);
        
        // Create parent directories if they don't exist
        File parentDir = cacheFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }
        
        // Load existing cache if available
        if (cacheFile.exists()) {
            load();
        }
    }
    
    /**
     * Loads tokens from cache file.
     * 
     * @throws IOException if file cannot be read
     */
    private void load() throws IOException {
        try {
            String content = FileUtils.readFileToString(cacheFile, "UTF-8");
            JSONObject json = new JSONObject(content);
            
            this.accessToken = json.optString("access_token", null);
            this.refreshToken = json.optString("refresh_token", null);
            this.expiresAt = json.optLong("expires_at", 0);
            
            logger.debug("Token cache loaded from: {}", cacheFile.getAbsolutePath());
        } catch (Exception e) {
            logger.warn("Failed to load token cache: {}", e.getMessage());
            // Clear invalid cache
            this.accessToken = null;
            this.refreshToken = null;
            this.expiresAt = 0;
        }
    }
    
    /**
     * Saves tokens to cache file with secure permissions.
     * 
     * @throws IOException if file cannot be written
     */
    public void save() throws IOException {
        JSONObject json = new JSONObject();
        if (accessToken != null) {
            json.put("access_token", accessToken);
        }
        if (refreshToken != null) {
            json.put("refresh_token", refreshToken);
        }
        json.put("expires_at", expiresAt);
        
        FileUtils.writeStringToFile(cacheFile, json.toString(2), "UTF-8");
        
        // Set secure file permissions on Unix systems (0600 - read/write owner only)
        try {
            Set<PosixFilePermission> perms = new HashSet<>();
            perms.add(PosixFilePermission.OWNER_READ);
            perms.add(PosixFilePermission.OWNER_WRITE);
            Files.setPosixFilePermissions(cacheFile.toPath(), perms);
            logger.debug("Token cache saved with secure permissions: {}", cacheFile.getAbsolutePath());
        } catch (UnsupportedOperationException e) {
            // Windows doesn't support POSIX permissions
            logger.debug("Token cache saved (POSIX permissions not supported): {}", cacheFile.getAbsolutePath());
        }
    }
    
    /**
     * Updates the cache with new tokens.
     * 
     * @param accessToken New access token
     * @param refreshToken New refresh token (can be null)
     * @param expiresInSeconds Token expiration time in seconds
     * @throws IOException if cache cannot be saved
     */
    public void updateTokens(String accessToken, String refreshToken, int expiresInSeconds) throws IOException {
        this.accessToken = accessToken;
        if (refreshToken != null) {
            this.refreshToken = refreshToken;
        }
        // Subtract buffer to refresh before actual expiration
        this.expiresAt = System.currentTimeMillis() + ((expiresInSeconds - EXPIRATION_BUFFER_SECONDS) * 1000L);
        save();
    }
    
    /**
     * Checks if the access token is expired or about to expire.
     * 
     * @return true if token is expired or will expire within 5 minutes
     */
    public boolean isAccessTokenExpired() {
        if (accessToken == null || expiresAt == 0) {
            return true;
        }
        return System.currentTimeMillis() >= expiresAt;
    }
    
    /**
     * Gets the cached access token.
     * 
     * @return The access token, or null if not available
     */
    public String getAccessToken() {
        return accessToken;
    }
    
    /**
     * Gets the cached refresh token.
     * 
     * @return The refresh token, or null if not available
     */
    public String getRefreshToken() {
        return refreshToken;
    }
    
    /**
     * Checks if the cache has a valid (non-expired) access token.
     * 
     * @return true if valid access token is available
     */
    public boolean hasValidAccessToken() {
        return accessToken != null && !isAccessTokenExpired();
    }
    
    /**
     * Checks if the cache has a refresh token.
     * 
     * @return true if refresh token is available
     */
    public boolean hasRefreshToken() {
        return refreshToken != null && !refreshToken.isEmpty();
    }
    
    /**
     * Clears all cached tokens.
     * 
     * @throws IOException if cache file cannot be deleted
     */
    public void clear() throws IOException {
        this.accessToken = null;
        this.refreshToken = null;
        this.expiresAt = 0;
        
        if (cacheFile.exists()) {
            cacheFile.delete();
        }
        
        logger.info("Token cache cleared");
    }
}
