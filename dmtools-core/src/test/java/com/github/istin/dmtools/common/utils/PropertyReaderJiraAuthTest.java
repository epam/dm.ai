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
 * Test class for PropertyReader Jira authentication enhancements (DMC-27).
 * Tests the new email + API token authentication with automatic base64 encoding,
 * as well as backward compatibility with legacy base64 tokens.
 */
public class PropertyReaderJiraAuthTest {

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
    public void testNewAuthenticationMethod_EmailAndApiToken() {
        // Given: separate email and API token are provided
        propertyReader.setTestProperty("JIRA_EMAIL", "john.doe@company.com");
        propertyReader.setTestProperty("JIRA_API_TOKEN", "ATT123456789");
        
        // When: getting the login pass token
        String result = propertyReader.getJiraLoginPassToken();
        
        // Then: should return auto-encoded base64 token
        String expectedCredentials = "john.doe@company.com:ATT123456789";
        String expectedEncoded = Base64.getEncoder().encodeToString(expectedCredentials.getBytes());
        assertEquals("Should auto-encode email:token to base64", expectedEncoded, result);
    }

    @Test
    public void testNewAuthenticationMethod_EmailAndApiTokenWithWhitespace() {
        // Given: email and API token with whitespace
        propertyReader.setTestProperty("JIRA_EMAIL", "  john.doe@company.com  ");
        propertyReader.setTestProperty("JIRA_API_TOKEN", "  ATT123456789  ");
        
        // When: getting the login pass token
        String result = propertyReader.getJiraLoginPassToken();
        
        // Then: should trim whitespace and encode properly
        String expectedCredentials = "john.doe@company.com:ATT123456789";
        String expectedEncoded = Base64.getEncoder().encodeToString(expectedCredentials.getBytes());
        assertEquals("Should trim whitespace before encoding", expectedEncoded, result);
    }

    @Test
    public void testLegacyAuthentication_Base64Token() {
        // Given: only legacy base64 token is provided
        String legacyToken = "bGVnYWN5VG9rZW46cGFzc3dvcmQ="; // base64("legacyToken:password")
        propertyReader.setTestProperty("JIRA_LOGIN_PASS_TOKEN", legacyToken);
        
        // When: getting the login pass token
        String result = propertyReader.getJiraLoginPassToken();
        
        // Then: should return the legacy token as-is
        assertEquals("Should return legacy token unchanged", legacyToken, result);
    }

    @Test
    public void testPriorityLogic_NewMethodTakesPrecedence() {
        // Given: both new method and legacy method are configured
        propertyReader.setTestProperty("JIRA_EMAIL", "new@company.com");
        propertyReader.setTestProperty("JIRA_API_TOKEN", "NEWTOKEN123");
        propertyReader.setTestProperty("JIRA_LOGIN_PASS_TOKEN", "bGVnYWN5VG9rZW46cGFzc3dvcmQ=");
        
        // When: getting the login pass token
        String result = propertyReader.getJiraLoginPassToken();
        
        // Then: should use new method and ignore legacy token
        String expectedCredentials = "new@company.com:NEWTOKEN123";
        String expectedEncoded = Base64.getEncoder().encodeToString(expectedCredentials.getBytes());
        assertEquals("New method should take precedence over legacy", expectedEncoded, result);
    }

    @Test
    public void testFallbackToLegacy_WhenEmailMissing() {
        // Given: only API token (no email) and legacy token
        propertyReader.setTestProperty("JIRA_API_TOKEN", "TOKEN123");
        propertyReader.setTestProperty("JIRA_LOGIN_PASS_TOKEN", "bGVnYWN5VG9rZW46cGFzc3dvcmQ=");
        
        // When: getting the login pass token
        String result = propertyReader.getJiraLoginPassToken();
        
        // Then: should fall back to legacy token
        assertEquals("Should fall back to legacy when email is missing", "bGVnYWN5VG9rZW46cGFzc3dvcmQ=", result);
    }

    @Test
    public void testFallbackToLegacy_WhenApiTokenMissing() {
                // Given: only email (no API token) and legacy token
        propertyReader.setTestProperty("JIRA_EMAIL", "user@company.com");
        propertyReader.setTestProperty("JIRA_LOGIN_PASS_TOKEN", "bGVnYWN5VG9rZW46cGFzc3dvcmQ=");

        // When: getting the login pass token
        String result = propertyReader.getJiraLoginPassToken();
        
        // Then: should fall back to legacy token
        assertEquals("Should fall back to legacy when API token is missing", "bGVnYWN5VG9rZW46cGFzc3dvcmQ=", result);
    }

    @Test
    public void testFallbackToLegacy_WhenEmailEmpty() {
        // Given: empty email and valid API token
        propertyReader.setTestProperty("JIRA_EMAIL", "   ");
        propertyReader.setTestProperty("JIRA_API_TOKEN", "TOKEN123");
        propertyReader.setTestProperty("JIRA_LOGIN_PASS_TOKEN", "bGVnYWN5VG9rZW46cGFzc3dvcmQ=");

        // When: getting the login pass token
        String result = propertyReader.getJiraLoginPassToken();
        
        // Then: should fall back to legacy token
        assertEquals("Should fall back to legacy when email is empty", "bGVnYWN5VG9rZW46cGFzc3dvcmQ=", result);
    }

    @Test
    public void testFallbackToLegacy_WhenApiTokenEmpty() {
        // Given: valid email and empty API token
        propertyReader.setTestProperty("JIRA_EMAIL", "user@company.com");
        propertyReader.setTestProperty("JIRA_API_TOKEN", "   ");
        propertyReader.setTestProperty("JIRA_LOGIN_PASS_TOKEN", "bGVnYWN5VG9rZW46cGFzc3dvcmQ=");
        
        // When: getting the login pass token
        String result = propertyReader.getJiraLoginPassToken();
        
        // Then: should fall back to legacy token
        assertEquals("Should fall back to legacy when API token is empty", "bGVnYWN5VG9rZW46cGFzc3dvcmQ=", result);
    }

    @Test
    public void testNoAuthentication_ReturnsNull() {
        // Given: no authentication parameters are set
        // (system properties are cleared in setUp)
        
        // When: getting the login pass token
        String result = propertyReader.getJiraLoginPassToken();
        
        // Then: should return null
        assertNull("Should return null when no authentication is configured", result);
    }

        @Test
    public void testGetJiraEmail() {
        // Given: JIRA_EMAIL is set
        propertyReader.setTestProperty("JIRA_EMAIL", "test@example.com");

        // When: getting the email
        String result = propertyReader.getJiraEmail();
        
        // Then: should return the email
        assertEquals("Should return configured email", "test@example.com", result);
    }

    @Test
    public void testGetJiraApiToken() {
        // Given: JIRA_API_TOKEN is set
        propertyReader.setTestProperty("JIRA_API_TOKEN", "TESTTOKEN123");

        // When: getting the API token
        String result = propertyReader.getJiraApiToken();
        
        // Then: should return the API token
        assertEquals("Should return configured API token", "TESTTOKEN123", result);
    }

    @Test
    public void testGetJiraEmail_NotSet() {
        // Given: JIRA_EMAIL is not set
        
        // When: getting the email
        String result = propertyReader.getJiraEmail();
        
        // Then: should return null
        assertNull("Should return null when email is not configured", result);
    }

    @Test
    public void testGetJiraApiToken_NotSet() {
        // Given: JIRA_API_TOKEN is not set
        
        // When: getting the API token
        String result = propertyReader.getJiraApiToken();
        
        // Then: should return null
        assertNull("Should return null when API token is not configured", result);
    }

    @Test
    public void testBackwardCompatibility_ExistingConfigurations() {
        // This test ensures that existing configurations continue to work
        // without any changes required from users
        
        // Given: existing base64 token configuration (simulating existing user setup)
        String existingToken = Base64.getEncoder().encodeToString("user@company.com:APIToken123".getBytes());
        propertyReader.setTestProperty("JIRA_LOGIN_PASS_TOKEN", existingToken);
        
        // When: getting the login pass token
        String result = propertyReader.getJiraLoginPassToken();
        
        // Then: should return the existing token unchanged
        assertEquals("Existing configurations should work without changes", existingToken, result);
    }

    @Test
    public void testRealWorldExample_AtlassianCloud() {
        // Given: realistic Atlassian Cloud credentials
        propertyReader.setTestProperty("JIRA_EMAIL", "developer@mycompany.com");
        propertyReader.setTestProperty("JIRA_API_TOKEN", "ATATT3xFfGF0a1b2c3d4e5f6g7h8i9j0");
        
        // When: getting the login pass token
        String result = propertyReader.getJiraLoginPassToken();
        
        // Then: should properly encode realistic credentials
        String expectedCredentials = "developer@mycompany.com:ATATT3xFfGF0a1b2c3d4e5f6g7h8i9j0";
        String expectedEncoded = Base64.getEncoder().encodeToString(expectedCredentials.getBytes());
        assertEquals("Should handle realistic Atlassian credentials", expectedEncoded, result);
        
        // And: verify the encoding is correct by decoding
        String decoded = new String(Base64.getDecoder().decode(result));
        assertEquals("Decoded credentials should match original format", expectedCredentials, decoded);
    }
} 