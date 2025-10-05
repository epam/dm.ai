package com.github.istin.dmtools.networking;

import com.github.istin.dmtools.common.networking.GenericRequest;
import okhttp3.Request;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AbstractRestClientTest {

    private AbstractRestClient restClient;

    @Before
    public void setUp() throws IOException {
        restClient = new AbstractRestClient("http://example.com", "auth") {
            @Override
            public String path(String path) {
                return "";
            }

            @Override
            public Request.Builder sign(Request.Builder builder) {
                return builder;
            }

        };
    }

    @Test
    public void testGetTimeout() {
        assertEquals(60, restClient.getTimeout());
    }

    @Test
    public void testSetCachePostRequestsEnabled() {
        restClient.setCachePostRequestsEnabled(true);
        assertTrue(restClient.isCachePostRequestsEnabled());
    }

    @Test
    public void testSetClearCache() throws IOException {
        restClient.setClearCache(true);
        assertTrue(restClient.isClearCache);
    }


    @Test
    public void testGetBasePath() {
        assertEquals("http://example.com", restClient.getBasePath());
    }

    @Test
    public void testGetCachedFile() {
        GenericRequest mockRequest = mock(GenericRequest.class);
        when(mockRequest.url()).thenReturn("http://example.com");
        File cachedFile = restClient.getCachedFile(mockRequest);
        assertNotNull(cachedFile);
    }

    // URL Sanitization Tests

    @Test
    public void testSanitizeUrl_WithApiKey() {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent?key=real_geminiKeysj4";
        String sanitized = AbstractRestClient.sanitizeUrl(url);
        assertEquals("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent?key=***REDACTED***", sanitized);
    }

    @Test
    public void testSanitizeUrl_WithToken() {
        String url = "https://api.example.com/data?token=secret123&param=value";
        String sanitized = AbstractRestClient.sanitizeUrl(url);
        assertEquals("https://api.example.com/data?token=***REDACTED***&param=value", sanitized);
    }

    @Test
    public void testSanitizeUrl_WithMultipleSensitiveParams() {
        String url = "https://api.example.com/data?key=apikey123&token=token456&param=value&secret=mysecret";
        String sanitized = AbstractRestClient.sanitizeUrl(url);
        assertEquals("https://api.example.com/data?key=***REDACTED***&token=***REDACTED***&param=value&secret=***REDACTED***", sanitized);
    }

    @Test
    public void testSanitizeUrl_WithPassword() {
        String url = "https://api.example.com/login?username=user&password=secret123";
        String sanitized = AbstractRestClient.sanitizeUrl(url);
        assertEquals("https://api.example.com/login?username=user&password=***REDACTED***", sanitized);
    }

    @Test
    public void testSanitizeUrl_WithAccessToken() {
        String url = "https://api.example.com/data?access_token=bearer123&data=info";
        String sanitized = AbstractRestClient.sanitizeUrl(url);
        assertEquals("https://api.example.com/data?access_token=***REDACTED***&data=info", sanitized);
    }

    @Test
    public void testSanitizeUrl_WithApiKeyVariation() {
        String url = "https://api.example.com/data?api_key=mykey&apikey=anotherkey&normal=param";
        String sanitized = AbstractRestClient.sanitizeUrl(url);
        assertEquals("https://api.example.com/data?api_key=***REDACTED***&apikey=***REDACTED***&normal=param", sanitized);
    }

    @Test
    public void testSanitizeUrl_WithAuthorization() {
        String url = "https://api.example.com/data?authorization=Bearer_token123&param=value";
        String sanitized = AbstractRestClient.sanitizeUrl(url);
        assertEquals("https://api.example.com/data?authorization=***REDACTED***&param=value", sanitized);
    }

    @Test
    public void testSanitizeUrl_NoQueryParams() {
        String url = "https://api.example.com/data";
        String sanitized = AbstractRestClient.sanitizeUrl(url);
        assertEquals("https://api.example.com/data", sanitized);
    }

    @Test
    public void testSanitizeUrl_NoSensitiveParams() {
        String url = "https://api.example.com/data?param1=value1&param2=value2";
        String sanitized = AbstractRestClient.sanitizeUrl(url);
        assertEquals("https://api.example.com/data?param1=value1&param2=value2", sanitized);
    }

    @Test
    public void testSanitizeUrl_EmptyQueryParam() {
        String url = "https://api.example.com/data?key=&param=value";
        String sanitized = AbstractRestClient.sanitizeUrl(url);
        assertEquals("https://api.example.com/data?key=***REDACTED***&param=value", sanitized);
    }

    @Test
    public void testSanitizeUrl_ParamWithoutValue() {
        String url = "https://api.example.com/data?key&param=value";
        String sanitized = AbstractRestClient.sanitizeUrl(url);
        assertEquals("https://api.example.com/data?key&param=value", sanitized);
    }

    @Test
    public void testSanitizeUrl_NullUrl() {
        String sanitized = AbstractRestClient.sanitizeUrl(null);
        assertNull(sanitized);
    }

    @Test
    public void testSanitizeUrl_EmptyUrl() {
        String url = "";
        String sanitized = AbstractRestClient.sanitizeUrl(url);
        assertEquals("", sanitized);
    }

    @Test
    public void testSanitizeUrl_CaseInsensitive() {
        String url = "https://api.example.com/data?KEY=secret123&Token=token456&APIKEY=key789";
        String sanitized = AbstractRestClient.sanitizeUrl(url);
        assertEquals("https://api.example.com/data?KEY=***REDACTED***&Token=***REDACTED***&APIKEY=***REDACTED***", sanitized);
    }

    @Test
    public void testSanitizeUrl_GeminiSpecificCase() {
        // This is the exact URL pattern from the bug report
        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent?key=real_geminiKeysj4";
        String sanitized = AbstractRestClient.sanitizeUrl(url);
        
        // Verify the key is redacted
        assertFalse("Sanitized URL should not contain the real API key", sanitized.contains("real_geminiKeysj4"));
        assertTrue("Sanitized URL should contain redacted placeholder", sanitized.contains("***REDACTED***"));
        assertTrue("Sanitized URL should preserve the base URL", sanitized.startsWith("https://generativelanguage.googleapis.com"));
    }

    @Test
    public void testSanitizeUrl_SubstringMatching() {
        // Test that parameters containing sensitive words are also redacted
        String url = "https://api.example.com/data?my_api_key=secret&user_token=token123&normal=value";
        String sanitized = AbstractRestClient.sanitizeUrl(url);
        assertEquals("https://api.example.com/data?my_api_key=***REDACTED***&user_token=***REDACTED***&normal=value", sanitized);
    }

    @Test
    public void testSanitizeUrl_UrlEncoded() {
        String url = "https://api.example.com/data?key=encoded%20value&param=normal";
        String sanitized = AbstractRestClient.sanitizeUrl(url);
        assertEquals("https://api.example.com/data?key=***REDACTED***&param=normal", sanitized);
    }

    // isRecoverableConnectionError Tests

    @Test
    public void testIsRecoverableConnectionError_BrokenPipe() {
        Exception ex = new IOException("Broken pipe");
        assertTrue(restClient.isRecoverableConnectionError(ex));
    }

    @Test
    public void testIsRecoverableConnectionError_ConnectionReset() {
        Exception ex = new IOException("Connection reset by peer");
        assertTrue(restClient.isRecoverableConnectionError(ex));
    }

    @Test
    public void testIsRecoverableConnectionError_ConnectionRefused() {
        Exception ex = new IOException("Connection refused");
        assertTrue(restClient.isRecoverableConnectionError(ex));
    }

    @Test
    public void testIsRecoverableConnectionError_Timeout() {
        Exception ex = new IOException("timeout occurred");
        assertTrue(restClient.isRecoverableConnectionError(ex));
    }

    @Test
    public void testIsRecoverableConnectionError_SocketTimeout() {
        Exception ex = new java.net.SocketTimeoutException("Read timed out");
        assertTrue(restClient.isRecoverableConnectionError(ex));
    }

    @Test
    public void testIsRecoverableConnectionError_ConnectException() {
        Exception ex = new java.net.ConnectException("Connection timed out");
        assertTrue(restClient.isRecoverableConnectionError(ex));
    }

    @Test
    public void testIsRecoverableConnectionError_NetworkUnreachable() {
        Exception ex = new IOException("Network is unreachable");
        assertTrue(restClient.isRecoverableConnectionError(ex));
    }

    @Test
    public void testIsRecoverableConnectionError_HostUnreachable() {
        Exception ex = new IOException("Host is unreachable");
        assertTrue(restClient.isRecoverableConnectionError(ex));
    }

    @Test
    public void testIsRecoverableConnectionError_ConnectionLost() {
        Exception ex = new IOException("Connection lost");
        assertTrue(restClient.isRecoverableConnectionError(ex));
    }

    @Test
    public void testIsRecoverableConnectionError_SocketClosed() {
        Exception ex = new IOException("Socket closed");
        assertTrue(restClient.isRecoverableConnectionError(ex));
    }

    @Test
    public void testIsRecoverableConnectionError_PrematureEOF() {
        Exception ex = new IOException("Premature EOF");
        assertTrue(restClient.isRecoverableConnectionError(ex));
    }

    @Test
    public void testIsRecoverableConnectionError_EndOfStream() {
        Exception ex = new IOException("Unexpected end of stream");
        assertTrue(restClient.isRecoverableConnectionError(ex));
    }

    @Test
    public void testIsRecoverableConnectionError_SSLHandshake() {
        Exception ex = new IOException("SSL handshake failed");
        assertTrue(restClient.isRecoverableConnectionError(ex));
    }

    @Test
    public void testIsRecoverableConnectionError_RemoteHandshake() {
        Exception ex = new IOException("Remote host terminated the handshake");
        assertTrue(restClient.isRecoverableConnectionError(ex));
    }

    @Test
    public void testIsRecoverableConnectionError_NullMessage() {
        Exception ex = new IOException((String) null);
        assertFalse(restClient.isRecoverableConnectionError(ex));
    }

    @Test
    public void testIsRecoverableConnectionError_NonRecoverable() {
        Exception ex = new IOException("Invalid request format");
        assertFalse(restClient.isRecoverableConnectionError(ex));
    }

    @Test
    public void testIsRecoverableConnectionError_CaseInsensitive() {
        Exception ex = new IOException("CONNECTION RESET BY PEER");
        assertTrue(restClient.isRecoverableConnectionError(ex));
    }

    // Additional utility method tests

    @Test
    public void testGetClient() {
        assertNotNull(restClient.getClient());
    }

    @Test
    public void testSetWaitBeforePerform() {
        restClient.setWaitBeforePerform(true);
        // No direct getter, but method should execute without error
    }

    @Test
    public void testCleanupConnectionPool() {
        // Should execute without throwing exception
        restClient.cleanupConnectionPool();
    }

    @Test
    public void testSetCacheGetRequestsEnabled() {
        restClient.setCacheGetRequestsEnabled(false);
        // No direct getter for isCacheGetRequestsEnabled private field
        restClient.setCacheGetRequestsEnabled(true);
    }

    @Test
    public void testClearCache() {
        GenericRequest mockRequest = mock(GenericRequest.class);
        when(mockRequest.url()).thenReturn("http://example.com/test");
        // Should execute without throwing exception
        restClient.clearCache(mockRequest);
    }

    @Test
    public void testGetCacheFolderName() {
        String folderName = restClient.getCacheFolderName();
        assertNotNull(folderName);
        assertTrue(folderName.contains("cache"));
    }

    @Test
    public void testClearCache_FileExists() throws IOException {
        // Create a temporary cached file
        GenericRequest mockRequest = mock(GenericRequest.class);
        when(mockRequest.url()).thenReturn("http://example.com/cache-test");
        
        File cachedFile = restClient.getCachedFile(mockRequest);
        cachedFile.getParentFile().mkdirs();
        FileUtils.writeStringToFile(cachedFile, "cached content", StandardCharsets.UTF_8);
        assertTrue(cachedFile.exists());
        
        // Enable cache
        restClient.setCacheGetRequestsEnabled(true);
        
        // Clear cache
        restClient.clearCache(mockRequest);
        
        // File should be deleted
        assertFalse(cachedFile.exists());
    }

    @Test
    public void testClearCache_FileNotExists() {
        GenericRequest mockRequest = mock(GenericRequest.class);
        when(mockRequest.url()).thenReturn("http://example.com/non-existent");
        
        restClient.setCacheGetRequestsEnabled(true);
        
        // Should not throw exception even if file doesn't exist
        restClient.clearCache(mockRequest);
    }

    @Test
    public void testClearCache_CacheDisabled() throws IOException {
        GenericRequest mockRequest = mock(GenericRequest.class);
        when(mockRequest.url()).thenReturn("http://example.com/disabled-cache");
        
        File cachedFile = restClient.getCachedFile(mockRequest);
        cachedFile.getParentFile().mkdirs();
        FileUtils.writeStringToFile(cachedFile, "cached content", StandardCharsets.UTF_8);
        
        // Disable cache
        restClient.setCacheGetRequestsEnabled(false);
        
        // Clear cache should not delete file when cache is disabled
        restClient.clearCache(mockRequest);
        
        // File should still exist
        assertTrue(cachedFile.exists());
        
        // Cleanup
        cachedFile.delete();
    }

    @Test
    public void testClearRequestIfExpired_NotExpired() throws IOException, InterruptedException {
        GenericRequest mockRequest = mock(GenericRequest.class);
        when(mockRequest.url()).thenReturn("http://example.com/not-expired");
        
        File cachedFile = restClient.getCachedFile(mockRequest);
        cachedFile.getParentFile().mkdirs();
        FileUtils.writeStringToFile(cachedFile, "cached content", StandardCharsets.UTF_8);
        
        restClient.setCacheGetRequestsEnabled(true);
        
        // Use past time (file is newer than this, so not expired)
        Long pastTime = System.currentTimeMillis() - 10000;
        
        restClient.clearRequestIfExpired(mockRequest, pastTime);
        
        // File should still exist (not expired - file is newer than pastTime)
        assertTrue(cachedFile.exists());
        
        // Cleanup
        cachedFile.delete();
    }

    @Test
    public void testClearRequestIfExpired_Expired() throws IOException, InterruptedException {
        GenericRequest mockRequest = mock(GenericRequest.class);
        when(mockRequest.url()).thenReturn("http://example.com/expired");
        
        File cachedFile = restClient.getCachedFile(mockRequest);
        cachedFile.getParentFile().mkdirs();
        FileUtils.writeStringToFile(cachedFile, "cached content", StandardCharsets.UTF_8);
        
        restClient.setCacheGetRequestsEnabled(true);
        
        // Wait a bit to ensure file timestamp is in the past
        Thread.sleep(100);
        // Use future time (file is older than this, so expired)
        Long futureTime = System.currentTimeMillis() + 100000;
        
        restClient.clearRequestIfExpired(mockRequest, futureTime);
        
        // File should be deleted (expired - file is older than futureTime)
        assertFalse(cachedFile.exists());
    }

    @Test
    public void testClearRequestIfExpired_FileNotExists() throws IOException {
        GenericRequest mockRequest = mock(GenericRequest.class);
        when(mockRequest.url()).thenReturn("http://example.com/no-file");
        
        Long updateTime = System.currentTimeMillis();
        
        // Should not throw exception
        restClient.clearRequestIfExpired(mockRequest, updateTime);
    }

    @Test
    public void testClearRequestIfExpired_NullUpdateTime() throws IOException {
        GenericRequest mockRequest = mock(GenericRequest.class);
        when(mockRequest.url()).thenReturn("http://example.com/null-time");
        
        File cachedFile = restClient.getCachedFile(mockRequest);
        cachedFile.getParentFile().mkdirs();
        FileUtils.writeStringToFile(cachedFile, "cached content", StandardCharsets.UTF_8);
        
        // With null update time, file should not be cleared
        restClient.clearRequestIfExpired(mockRequest, null);
        
        assertTrue(cachedFile.exists());
        
        // Cleanup
        cachedFile.delete();
    }

    @Test
    public void testExecute_WithNullRequest() throws IOException {
        String result = restClient.execute((GenericRequest) null);
        assertEquals("", result);
    }

    @Test
    public void testGetTimeout_CloudEnvironment() {
        // Save original env
        try {
            // This test just verifies the method doesn't throw
            // In actual cloud environment, timeout would be 120
            int timeout = restClient.getTimeout();
            assertTrue(timeout > 0);
        } catch (Exception e) {
            fail("getTimeout should not throw exception");
        }
    }

}