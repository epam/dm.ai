package com.github.istin.dmtools.microsoft.common.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TokenCache functionality.
 */
class TokenCacheTest {
    
    private TokenCache tokenCache;
    private File cacheFile;
    
    @BeforeEach
    void setUp(@TempDir Path tempDir) throws IOException {
        cacheFile = tempDir.resolve("test-token-cache.json").toFile();
        tokenCache = new TokenCache(cacheFile.getAbsolutePath());
    }
    
    @AfterEach
    void tearDown() throws IOException {
        if (cacheFile.exists()) {
            cacheFile.delete();
        }
    }
    
    @Test
    void testUpdateAndRetrieveTokens() throws IOException {
        // Update tokens
        String accessToken = "test-access-token";
        String refreshToken = "test-refresh-token";
        int expiresIn = 3600; // 1 hour
        
        tokenCache.updateTokens(accessToken, refreshToken, expiresIn);
        
        // Verify tokens are stored
        assertEquals(accessToken, tokenCache.getAccessToken());
        assertEquals(refreshToken, tokenCache.getRefreshToken());
        assertFalse(tokenCache.isAccessTokenExpired());
        assertTrue(tokenCache.hasValidAccessToken());
        assertTrue(tokenCache.hasRefreshToken());
    }
    
    @Test
    void testTokenExpiration() throws IOException, InterruptedException {
        // Create token that expires in 1 second (accounting for 5 minute buffer)
        String accessToken = "test-access-token";
        String refreshToken = "test-refresh-token";
        int expiresIn = 1; // 1 second (buffer makes it already expired)
        
        tokenCache.updateTokens(accessToken, refreshToken, expiresIn);
        
        // Token should be expired due to 5-minute buffer
        assertTrue(tokenCache.isAccessTokenExpired());
        assertFalse(tokenCache.hasValidAccessToken());
    }
    
    @Test
    void testTokenPersistence() throws IOException {
        // Update tokens and save
        String accessToken = "test-access-token";
        String refreshToken = "test-refresh-token";
        int expiresIn = 3600;
        
        tokenCache.updateTokens(accessToken, refreshToken, expiresIn);
        
        // Create new cache instance with same file
        TokenCache newCache = new TokenCache(cacheFile.getAbsolutePath());
        
        // Verify tokens are loaded from file
        assertEquals(accessToken, newCache.getAccessToken());
        assertEquals(refreshToken, newCache.getRefreshToken());
        assertTrue(newCache.hasValidAccessToken());
    }
    
    @Test
    void testClearCache() throws IOException {
        // Update tokens
        String accessToken = "test-access-token";
        String refreshToken = "test-refresh-token";
        int expiresIn = 3600;
        
        tokenCache.updateTokens(accessToken, refreshToken, expiresIn);
        
        // Clear cache
        tokenCache.clear();
        
        // Verify tokens are cleared
        assertNull(tokenCache.getAccessToken());
        assertNull(tokenCache.getRefreshToken());
        assertFalse(tokenCache.hasValidAccessToken());
        assertFalse(cacheFile.exists());
    }
    
    @Test
    void testEmptyCache() throws IOException {
        // New cache should have no tokens
        assertNull(tokenCache.getAccessToken());
        assertNull(tokenCache.getRefreshToken());
        assertTrue(tokenCache.isAccessTokenExpired());
        assertFalse(tokenCache.hasValidAccessToken());
        assertFalse(tokenCache.hasRefreshToken());
    }
    
    @Test
    void testRefreshTokenUpdate() throws IOException {
        // Initial tokens
        tokenCache.updateTokens("access1", "refresh1", 3600);
        assertEquals("refresh1", tokenCache.getRefreshToken());
        
        // Update with new access token but no refresh token
        tokenCache.updateTokens("access2", null, 3600);
        
        // Refresh token should remain unchanged
        assertEquals("refresh1", tokenCache.getRefreshToken());
        assertEquals("access2", tokenCache.getAccessToken());
    }
}
