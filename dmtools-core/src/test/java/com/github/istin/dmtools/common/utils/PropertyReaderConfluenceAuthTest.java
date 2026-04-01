// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.utils;

import org.junit.Before;
import org.junit.Test;
import org.junit.After;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Test class for PropertyReader Confluence authentication enhancements (DMC-28).
 * Tests the new email + API token authentication with automatic base64 encoding,
 * Basic/Bearer auth type support, and backward compatibility with legacy base64 tokens.
 */
public class PropertyReaderConfluenceAuthTest {

    private TestablePropertyReader propertyReader;
    
    /**
     * Test-specific PropertyReader that allows us to control the property values
     * without relying on external configuration files or environment variables.
     */
    private static class TestablePropertyReader extends PropertyReader {
        private final Map<String, String> testProperties = new HashMap<>();
        
        public void setTestProperty(String key, String value) {
            testProperties.put(key, value);
        }
        
        public void clearTestProperty(String key) {
            testProperties.remove(key);
        }
        
        public void clearAllTestProperties() {
            testProperties.clear();
        }
        
        @Override
        public String getValue(String propertyKey) {
            return testProperties.get(propertyKey);
        }
    }
    
    @Before
    public void setUp() {
        propertyReader = new TestablePropertyReader();
        propertyReader.clearAllTestProperties();
    }
    
    @After
    public void tearDown() {
        propertyReader.clearAllTestProperties();
    }

    @Test
    public void testNewAuthenticationMethod_BasicAuth() {
        // Given: separate email and API token with Basic auth type
        propertyReader.setTestProperty("CONFLUENCE_EMAIL", "john.doe@company.com");
        propertyReader.setTestProperty("CONFLUENCE_API_TOKEN", "ATT123456789");
        propertyReader.setTestProperty("CONFLUENCE_AUTH_TYPE", "Basic");
        
        // When: getting the login pass token
        String result = propertyReader.getConfluenceLoginPassToken();
        
        // Then: should return auto-encoded base64 token
        String expectedCredentials = "john.doe@company.com:ATT123456789";
        String expectedEncoded = Base64.getEncoder().encodeToString(expectedCredentials.getBytes());
        assertEquals("Should auto-encode email:token to base64 for Basic auth", expectedEncoded, result);
    }

    @Test
    public void testNewAuthenticationMethod_BearerAuth() {
        // Given: separate email and API token with Bearer auth type
        propertyReader.setTestProperty("CONFLUENCE_EMAIL", "john.doe@company.com");
        propertyReader.setTestProperty("CONFLUENCE_API_TOKEN", "ATT123456789");
        propertyReader.setTestProperty("CONFLUENCE_AUTH_TYPE", "Bearer");
        
        // When: getting the login pass token
        String result = propertyReader.getConfluenceLoginPassToken();
        
        // Then: should return token directly without encoding
        assertEquals("Should return token directly for Bearer auth", "ATT123456789", result);
    }

    @Test
    public void testNewAuthenticationMethod_DefaultToBasicAuth() {
        // Given: separate email and API token without explicit auth type
        propertyReader.setTestProperty("CONFLUENCE_EMAIL", "john.doe@company.com");
        propertyReader.setTestProperty("CONFLUENCE_API_TOKEN", "ATT123456789");
        
        // When: getting the login pass token
        String result = propertyReader.getConfluenceLoginPassToken();
        
        // Then: should default to Basic auth with base64 encoding
        String expectedCredentials = "john.doe@company.com:ATT123456789";
        String expectedEncoded = Base64.getEncoder().encodeToString(expectedCredentials.getBytes());
        assertEquals("Should default to Basic auth with base64 encoding", expectedEncoded, result);
    }

    @Test
    public void testNewAuthenticationMethod_EmailAndApiTokenWithWhitespace() {
        // Given: email and API token with whitespace
        propertyReader.setTestProperty("CONFLUENCE_EMAIL", "  john.doe@company.com  ");
        propertyReader.setTestProperty("CONFLUENCE_API_TOKEN", "  ATT123456789  ");
        propertyReader.setTestProperty("CONFLUENCE_AUTH_TYPE", "Basic");
        
        // When: getting the login pass token
        String result = propertyReader.getConfluenceLoginPassToken();
        
        // Then: should trim whitespace and encode properly
        String expectedCredentials = "john.doe@company.com:ATT123456789";
        String expectedEncoded = Base64.getEncoder().encodeToString(expectedCredentials.getBytes());
        assertEquals("Should trim whitespace before encoding", expectedEncoded, result);
    }

    @Test
    public void testLegacyAuthentication_Base64Token() {
        // Given: only legacy base64 token is provided
        String legacyToken = "bGVnYWN5VG9rZW46cGFzc3dvcmQ="; // base64("legacyToken:password")
        propertyReader.setTestProperty("CONFLUENCE_LOGIN_PASS_TOKEN", legacyToken);
        
        // When: getting the login pass token
        String result = propertyReader.getConfluenceLoginPassToken();
        
        // Then: should return the legacy token as-is
        assertEquals("Should return legacy token unchanged", legacyToken, result);
    }

    @Test
    public void testPriorityLogic_NewMethodTakesPrecedence() {
        // Given: both new method and legacy method are configured
        propertyReader.setTestProperty("CONFLUENCE_EMAIL", "new@company.com");
        propertyReader.setTestProperty("CONFLUENCE_API_TOKEN", "NEWTOKEN123");
        propertyReader.setTestProperty("CONFLUENCE_AUTH_TYPE", "Basic");
        propertyReader.setTestProperty("CONFLUENCE_LOGIN_PASS_TOKEN", "bGVnYWN5VG9rZW46cGFzc3dvcmQ=");
        
        // When: getting the login pass token
        String result = propertyReader.getConfluenceLoginPassToken();
        
        // Then: should use new method and ignore legacy token
        String expectedCredentials = "new@company.com:NEWTOKEN123";
        String expectedEncoded = Base64.getEncoder().encodeToString(expectedCredentials.getBytes());
        assertEquals("New method should take precedence over legacy", expectedEncoded, result);
    }

    @Test
    public void testPriorityLogic_BearerAuthTakesPrecedence() {
        // Given: both new Bearer method and legacy method are configured
        propertyReader.setTestProperty("CONFLUENCE_EMAIL", "new@company.com");
        propertyReader.setTestProperty("CONFLUENCE_API_TOKEN", "NEWTOKEN123");
        propertyReader.setTestProperty("CONFLUENCE_AUTH_TYPE", "Bearer");
        propertyReader.setTestProperty("CONFLUENCE_LOGIN_PASS_TOKEN", "bGVnYWN5VG9rZW46cGFzc3dvcmQ=");
        
        // When: getting the login pass token
        String result = propertyReader.getConfluenceLoginPassToken();
        
        // Then: should use Bearer token directly
        assertEquals("Bearer auth should return token directly", "NEWTOKEN123", result);
    }

    @Test
    public void testFallbackToLegacy_WhenEmailMissing() {
        // Given: only API token (no email) and legacy token
        propertyReader.setTestProperty("CONFLUENCE_API_TOKEN", "TOKEN123");
        propertyReader.setTestProperty("CONFLUENCE_LOGIN_PASS_TOKEN", "bGVnYWN5VG9rZW46cGFzc3dvcmQ=");
        
        // When: getting the login pass token
        String result = propertyReader.getConfluenceLoginPassToken();
        
        // Then: should fall back to legacy token
        assertEquals("Should fall back to legacy when email is missing", "bGVnYWN5VG9rZW46cGFzc3dvcmQ=", result);
    }

    @Test
    public void testFallbackToLegacy_WhenApiTokenMissing() {
        // Given: only email (no API token) and legacy token
        propertyReader.setTestProperty("CONFLUENCE_EMAIL", "user@company.com");
        propertyReader.setTestProperty("CONFLUENCE_LOGIN_PASS_TOKEN", "bGVnYWN5VG9rZW46cGFzc3dvcmQ=");
        
        // When: getting the login pass token
        String result = propertyReader.getConfluenceLoginPassToken();
        
        // Then: should fall back to legacy token
        assertEquals("Should fall back to legacy when API token is missing", "bGVnYWN5VG9rZW46cGFzc3dvcmQ=", result);
    }

    @Test
    public void testFallbackToLegacy_WhenEmailEmpty() {
        // Given: empty email and valid API token
        propertyReader.setTestProperty("CONFLUENCE_EMAIL", "   ");
        propertyReader.setTestProperty("CONFLUENCE_API_TOKEN", "TOKEN123");
        propertyReader.setTestProperty("CONFLUENCE_LOGIN_PASS_TOKEN", "bGVnYWN5VG9rZW46cGFzc3dvcmQ=");
        
        // When: getting the login pass token
        String result = propertyReader.getConfluenceLoginPassToken();
        
        // Then: should fall back to legacy token
        assertEquals("Should fall back to legacy when email is empty", "bGVnYWN5VG9rZW46cGFzc3dvcmQ=", result);
    }

    @Test
    public void testFallbackToLegacy_WhenApiTokenEmpty() {
        // Given: valid email and empty API token
        propertyReader.setTestProperty("CONFLUENCE_EMAIL", "user@company.com");
        propertyReader.setTestProperty("CONFLUENCE_API_TOKEN", "   ");
        propertyReader.setTestProperty("CONFLUENCE_LOGIN_PASS_TOKEN", "bGVnYWN5VG9rZW46cGFzc3dvcmQ=");
        
        // When: getting the login pass token
        String result = propertyReader.getConfluenceLoginPassToken();
        
        // Then: should fall back to legacy token
        assertEquals("Should fall back to legacy when API token is empty", "bGVnYWN5VG9rZW46cGFzc3dvcmQ=", result);
    }

    @Test
    public void testNoAuthentication_ReturnsNull() {
        // Given: no authentication parameters are set
        // (test properties are cleared in setUp)
        
        // When: getting the login pass token
        String result = propertyReader.getConfluenceLoginPassToken();
        
        // Then: should return null
        assertNull("Should return null when no authentication is configured", result);
    }

    @Test
    public void testGetConfluenceEmail() {
        // Given: CONFLUENCE_EMAIL is set
        propertyReader.setTestProperty("CONFLUENCE_EMAIL", "test@example.com");
        
        // When: getting the email
        String result = propertyReader.getConfluenceEmail();
        
        // Then: should return the email
        assertEquals("Should return configured email", "test@example.com", result);
    }

    @Test
    public void testGetConfluenceApiToken() {
        // Given: CONFLUENCE_API_TOKEN is set
        propertyReader.setTestProperty("CONFLUENCE_API_TOKEN", "TESTTOKEN123");
        
        // When: getting the API token
        String result = propertyReader.getConfluenceApiToken();
        
        // Then: should return the API token
        assertEquals("Should return configured API token", "TESTTOKEN123", result);
    }

    @Test
    public void testGetConfluenceAuthType_Basic() {
        // Given: CONFLUENCE_AUTH_TYPE is set to Basic
        propertyReader.setTestProperty("CONFLUENCE_AUTH_TYPE", "Basic");
        
        // When: getting the auth type
        String result = propertyReader.getConfluenceAuthType();
        
        // Then: should return Basic
        assertEquals("Should return configured auth type", "Basic", result);
    }

    @Test
    public void testGetConfluenceAuthType_Bearer() {
        // Given: CONFLUENCE_AUTH_TYPE is set to Bearer
        propertyReader.setTestProperty("CONFLUENCE_AUTH_TYPE", "Bearer");
        
        // When: getting the auth type
        String result = propertyReader.getConfluenceAuthType();
        
        // Then: should return Bearer
        assertEquals("Should return configured auth type", "Bearer", result);
    }

    @Test
    public void testGetConfluenceAuthType_DefaultToBasic() {
        // Given: CONFLUENCE_AUTH_TYPE is not set
        
        // When: getting the auth type
        String result = propertyReader.getConfluenceAuthType();
        
        // Then: should default to Basic
        assertEquals("Should default to Basic when auth type is not configured", "Basic", result);
    }

    @Test
    public void testGetConfluenceEmail_NotSet() {
        // Given: CONFLUENCE_EMAIL is not set
        
        // When: getting the email
        String result = propertyReader.getConfluenceEmail();
        
        // Then: should return null
        assertNull("Should return null when email is not configured", result);
    }

    @Test
    public void testGetConfluenceApiToken_NotSet() {
        // Given: CONFLUENCE_API_TOKEN is not set
        
        // When: getting the API token
        String result = propertyReader.getConfluenceApiToken();
        
        // Then: should return null
        assertNull("Should return null when API token is not configured", result);
    }

    @Test
    public void testBackwardCompatibility_ExistingConfigurations() {
        // This test ensures that existing configurations continue to work
        // without any changes required from users
        
        // Given: existing base64 token configuration (simulating existing user setup)
        String existingToken = Base64.getEncoder().encodeToString("user@company.com:APIToken123".getBytes());
        propertyReader.setTestProperty("CONFLUENCE_LOGIN_PASS_TOKEN", existingToken);
        
        // When: getting the login pass token
        String result = propertyReader.getConfluenceLoginPassToken();
        
        // Then: should return the existing token unchanged
        assertEquals("Existing configurations should work without changes", existingToken, result);
    }

    @Test
    public void testRealWorldExample_AtlassianCloudBasic() {
        // Given: realistic Atlassian Cloud credentials with Basic auth
        propertyReader.setTestProperty("CONFLUENCE_EMAIL", "developer@mycompany.com");
        propertyReader.setTestProperty("CONFLUENCE_API_TOKEN", "ATATT3xFfGF0a1b2c3d4e5f6g7h8i9j0");
        propertyReader.setTestProperty("CONFLUENCE_AUTH_TYPE", "Basic");
        
        // When: getting the login pass token
        String result = propertyReader.getConfluenceLoginPassToken();
        
        // Then: should properly encode realistic credentials
        String expectedCredentials = "developer@mycompany.com:ATATT3xFfGF0a1b2c3d4e5f6g7h8i9j0";
        String expectedEncoded = Base64.getEncoder().encodeToString(expectedCredentials.getBytes());
        assertEquals("Should handle realistic Atlassian credentials with Basic auth", expectedEncoded, result);
        
        // And: verify the encoding is correct by decoding
        String decoded = new String(Base64.getDecoder().decode(result));
        assertEquals("Decoded credentials should match original format", expectedCredentials, decoded);
    }

    @Test
    public void testRealWorldExample_AtlassianCloudBearer() {
        // Given: realistic Atlassian Cloud credentials with Bearer auth
        propertyReader.setTestProperty("CONFLUENCE_EMAIL", "developer@mycompany.com");
        propertyReader.setTestProperty("CONFLUENCE_API_TOKEN", "ATATT3xFfGF0a1b2c3d4e5f6g7h8i9j0");
        propertyReader.setTestProperty("CONFLUENCE_AUTH_TYPE", "Bearer");
        
        // When: getting the login pass token
        String result = propertyReader.getConfluenceLoginPassToken();
        
        // Then: should return token directly
        assertEquals("Should handle realistic Atlassian credentials with Bearer auth", "ATATT3xFfGF0a1b2c3d4e5f6g7h8i9j0", result);
    }

    @Test
    public void testAuthTypeIsCaseInsensitive() {
        // Given: auth type with different cases
        propertyReader.setTestProperty("CONFLUENCE_EMAIL", "user@company.com");
        propertyReader.setTestProperty("CONFLUENCE_API_TOKEN", "TOKEN123");
        
        // Test lowercase
        propertyReader.setTestProperty("CONFLUENCE_AUTH_TYPE", "bearer");
        String result1 = propertyReader.getConfluenceLoginPassToken();
        assertEquals("Should handle lowercase Bearer", "TOKEN123", result1);
        
        // Test uppercase
        propertyReader.setTestProperty("CONFLUENCE_AUTH_TYPE", "BEARER");
        String result2 = propertyReader.getConfluenceLoginPassToken();
        assertEquals("Should handle uppercase Bearer", "TOKEN123", result2);
        
        // Test mixed case
        propertyReader.setTestProperty("CONFLUENCE_AUTH_TYPE", "BeArEr");
        String result3 = propertyReader.getConfluenceLoginPassToken();
        assertEquals("Should handle mixed case Bearer", "TOKEN123", result3);
    }
} 