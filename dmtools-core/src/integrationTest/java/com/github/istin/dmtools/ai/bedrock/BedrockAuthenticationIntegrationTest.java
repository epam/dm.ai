package com.github.istin.dmtools.ai.bedrock;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.ai.ConversationObserver;
import com.github.istin.dmtools.common.config.PropertyReaderConfiguration;
import com.github.istin.dmtools.common.utils.PropertyReader;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Integration test for BedrockAIClient authentication strategies.
 * 
 * Tests different authentication methods:
 * 1. Bearer Token authentication
 * 2. IAM Access Keys authentication (AWS Signature V4)
 * 3. Default Credentials Provider authentication
 * 
 * To run these tests, configure the following properties:
 * 
 * For Bearer Token test:
 * - BEDROCK_REGION (e.g., "eu-north-1")
 * - BEDROCK_MODEL_ID (e.g., "anthropic.claude-sonnet-4-20250514-v1:0")
 * - BEDROCK_BEARER_TOKEN or AWS_BEARER_TOKEN_BEDROCK
 * 
 * For IAM Keys test:
 * - BEDROCK_REGION
 * - BEDROCK_MODEL_ID
 * - BEDROCK_ACCESS_KEY_ID
 * - BEDROCK_SECRET_ACCESS_KEY
 * - BEDROCK_SESSION_TOKEN (optional, for temporary credentials)
 * 
 * For Default Credentials test:
 * - BEDROCK_REGION
 * - BEDROCK_MODEL_ID
 * - AWS credentials configured in ~/.aws/credentials or environment variables
 * 
 * These can be set in:
 * - System environment variables
 * - dmtools.env file
 * - config.properties file
 */
public class BedrockAuthenticationIntegrationTest {

    private ConversationObserver observer;
    private PropertyReader propertyReader;

    @Before
    public void setUp() throws Exception {
        observer = new ConversationObserver();
        propertyReader = new PropertyReader();
    }

    @Test
    public void testBearerTokenAuthentication() throws Exception {
        // Check if Bearer Token is configured
        String bearerToken = propertyReader.getBedrockBearerToken();
        if (bearerToken == null || bearerToken.trim().isEmpty() || bearerToken.startsWith("$")) {
            System.out.println("Skipping testBearerTokenAuthentication: BEDROCK_BEARER_TOKEN not configured");
            return;
        }

        String bedrockModelId = propertyReader.getBedrockModelId();
        String bedrockBasePath = propertyReader.getBedrockBasePath();

        if (bedrockModelId == null || bedrockModelId.trim().isEmpty() || bedrockModelId.startsWith("$")) {
            fail("BEDROCK_MODEL_ID is required for Bearer Token test");
        }
        if (bedrockBasePath == null || bedrockBasePath.trim().isEmpty()) {
            fail("BEDROCK_BASE_PATH or BEDROCK_REGION is required for Bearer Token test");
        }

        // Create client with Bearer Token
        PropertyReaderConfiguration configuration = new PropertyReaderConfiguration();
        AI bedrockAI = new BasicBedrockAI(observer, configuration);

        // Verify it's using Bearer Token authentication
        BedrockAIClient client = (BedrockAIClient) bedrockAI;
        assertEquals("BEARER_TOKEN", client.getAuthenticationStrategy().getAuthenticationType());

        // Test simple chat
        String prompt = "Say 'Hello' in one word.";
        String response = bedrockAI.chat(prompt);

        System.out.println("Bearer Token authentication test - Response: " + response);
        assertNotNull("Response should not be null", response);
        assertFalse("Response should not be empty", response.isEmpty());
    }

    @Test
    public void testIAMKeysAuthentication() throws Exception {
        // Check if Bearer Token is configured (it has priority over IAM Keys)
        String bearerToken = propertyReader.getBedrockBearerToken();
        if (bearerToken != null && !bearerToken.trim().isEmpty() && !bearerToken.startsWith("$")) {
            System.out.println("Skipping testIAMKeysAuthentication: BEDROCK_BEARER_TOKEN or AWS_BEARER_TOKEN_BEDROCK is configured (has priority over IAM Keys). " +
                    "To test IAM Keys, please comment out or remove Bearer Token configuration.");
            return;
        }
        
        // Check if IAM Keys are configured
        String accessKeyId = propertyReader.getBedrockAccessKeyId();
        String secretAccessKey = propertyReader.getBedrockSecretAccessKey();
        String sessionToken = propertyReader.getBedrockSessionToken();

        if (accessKeyId == null || accessKeyId.trim().isEmpty() || accessKeyId.startsWith("$")) {
            System.out.println("Skipping testIAMKeysAuthentication: BEDROCK_ACCESS_KEY_ID not configured");
            return;
        }
        if (secretAccessKey == null || secretAccessKey.trim().isEmpty() || secretAccessKey.startsWith("$")) {
            System.out.println("Skipping testIAMKeysAuthentication: BEDROCK_SECRET_ACCESS_KEY not configured");
            return;
        }

        String bedrockModelId = propertyReader.getBedrockModelId();
        String bedrockBasePath = propertyReader.getBedrockBasePath();
        String bedrockRegion = propertyReader.getBedrockRegion();

        if (bedrockModelId == null || bedrockModelId.trim().isEmpty() || bedrockModelId.startsWith("$")) {
            fail("BEDROCK_MODEL_ID is required for IAM Keys test");
        }
        if (bedrockBasePath == null || bedrockBasePath.trim().isEmpty()) {
            fail("BEDROCK_BASE_PATH or BEDROCK_REGION is required for IAM Keys test");
        }
        if (bedrockRegion == null || bedrockRegion.trim().isEmpty()) {
            fail("BEDROCK_REGION is required for IAM Keys test (needed for AWS Signature V4)");
        }

        // Create client with IAM Keys
        PropertyReaderConfiguration configuration = new PropertyReaderConfiguration();
        AI bedrockAI = new BasicBedrockAI(observer, configuration);

        // Verify it's using IAM Keys authentication
        BedrockAIClient client = (BedrockAIClient) bedrockAI;
        assertEquals("IAM_KEYS", client.getAuthenticationStrategy().getAuthenticationType());

        // Test simple chat
        String prompt = "Say 'Hello' in one word.";
        String response = bedrockAI.chat(prompt);

        System.out.println("IAM Keys authentication test - Response: " + response);
        assertNotNull("Response should not be null", response);
        assertFalse("Response should not be empty", response.isEmpty());
    }

    @Test
    public void testIAMKeysAuthenticationWithSessionToken() throws Exception {
        // Check if Bearer Token is configured (it has priority over IAM Keys)
        String bearerToken = propertyReader.getBedrockBearerToken();
        if (bearerToken != null && !bearerToken.trim().isEmpty() && !bearerToken.startsWith("$")) {
            System.out.println("Skipping testIAMKeysAuthenticationWithSessionToken: BEDROCK_BEARER_TOKEN or AWS_BEARER_TOKEN_BEDROCK is configured (has priority over IAM Keys). " +
                    "To test IAM Keys, please comment out or remove Bearer Token configuration.");
            return;
        }
        
        // Check if IAM Keys with Session Token are configured
        String accessKeyId = propertyReader.getBedrockAccessKeyId();
        String secretAccessKey = propertyReader.getBedrockSecretAccessKey();
        String sessionToken = propertyReader.getBedrockSessionToken();
        
        // sessionToken is used implicitly in BasicBedrockAI when creating the client

        if (accessKeyId == null || accessKeyId.trim().isEmpty() || accessKeyId.startsWith("$")) {
            System.out.println("Skipping testIAMKeysAuthenticationWithSessionToken: BEDROCK_ACCESS_KEY_ID not configured");
            return;
        }
        if (secretAccessKey == null || secretAccessKey.trim().isEmpty() || secretAccessKey.startsWith("$")) {
            System.out.println("Skipping testIAMKeysAuthenticationWithSessionToken: BEDROCK_SECRET_ACCESS_KEY not configured");
            return;
        }
        if (sessionToken == null || sessionToken.trim().isEmpty() || sessionToken.startsWith("$")) {
            System.out.println("Skipping testIAMKeysAuthenticationWithSessionToken: BEDROCK_SESSION_TOKEN not configured");
            return;
        }

        String bedrockModelId = propertyReader.getBedrockModelId();
        String bedrockBasePath = propertyReader.getBedrockBasePath();
        String bedrockRegion = propertyReader.getBedrockRegion();

        if (bedrockModelId == null || bedrockModelId.trim().isEmpty() || bedrockModelId.startsWith("$")) {
            fail("BEDROCK_MODEL_ID is required for IAM Keys with Session Token test");
        }
        if (bedrockBasePath == null || bedrockBasePath.trim().isEmpty()) {
            fail("BEDROCK_BASE_PATH or BEDROCK_REGION is required for IAM Keys with Session Token test");
        }
        if (bedrockRegion == null || bedrockRegion.trim().isEmpty()) {
            fail("BEDROCK_REGION is required for IAM Keys with Session Token test (needed for AWS Signature V4)");
        }

        // Create client with IAM Keys and Session Token
        PropertyReaderConfiguration configuration = new PropertyReaderConfiguration();
        AI bedrockAI = new BasicBedrockAI(observer, configuration);

        // Verify it's using IAM Keys authentication
        BedrockAIClient client = (BedrockAIClient) bedrockAI;
        assertEquals("IAM_KEYS", client.getAuthenticationStrategy().getAuthenticationType());

        // Test simple chat
        String prompt = "Say 'Hello' in one word.";
        String response = bedrockAI.chat(prompt);

        System.out.println("IAM Keys with Session Token authentication test - Response: " + response);
        assertNotNull("Response should not be null", response);
        assertFalse("Response should not be empty", response.isEmpty());
    }

    @Test
    public void testDefaultCredentialsAuthentication() throws Exception {
        // Check if Bearer Token and IAM Keys are NOT configured (to force Default Credentials)
        String bearerToken = propertyReader.getBedrockBearerToken();
        String accessKeyId = propertyReader.getBedrockAccessKeyId();
        String secretAccessKey = propertyReader.getBedrockSecretAccessKey();

        // Skip if Bearer Token is configured (it has priority)
        if (bearerToken != null && !bearerToken.trim().isEmpty() && !bearerToken.startsWith("$")) {
            System.out.println("Skipping testDefaultCredentialsAuthentication: BEDROCK_BEARER_TOKEN is configured (has priority)");
            return;
        }

        // Skip if IAM Keys are configured (they have priority over Default Credentials)
        if (accessKeyId != null && !accessKeyId.trim().isEmpty() && !accessKeyId.startsWith("$") &&
            secretAccessKey != null && !secretAccessKey.trim().isEmpty() && !secretAccessKey.startsWith("$")) {
            System.out.println("Skipping testDefaultCredentialsAuthentication: BEDROCK_ACCESS_KEY_ID and BEDROCK_SECRET_ACCESS_KEY are configured (have priority)");
            return;
        }

        String bedrockModelId = propertyReader.getBedrockModelId();
        String bedrockBasePath = propertyReader.getBedrockBasePath();
        String bedrockRegion = propertyReader.getBedrockRegion();

        if (bedrockModelId == null || bedrockModelId.trim().isEmpty() || bedrockModelId.startsWith("$")) {
            fail("BEDROCK_MODEL_ID is required for Default Credentials test");
        }
        if (bedrockBasePath == null || bedrockBasePath.trim().isEmpty()) {
            fail("BEDROCK_BASE_PATH or BEDROCK_REGION is required for Default Credentials test");
        }
        if (bedrockRegion == null || bedrockRegion.trim().isEmpty()) {
            fail("BEDROCK_REGION is required for Default Credentials test (needed for AWS Signature V4)");
        }

        // Create client with Default Credentials
        PropertyReaderConfiguration configuration = new PropertyReaderConfiguration();
        AI bedrockAI = new BasicBedrockAI(observer, configuration);

        // Verify it's using Default Credentials authentication
        BedrockAIClient client = (BedrockAIClient) bedrockAI;
        assertEquals("DEFAULT_CREDENTIALS", client.getAuthenticationStrategy().getAuthenticationType());

        // Test simple chat
        String prompt = "Say 'Hello' in one word.";
        String response = bedrockAI.chat(prompt);

        System.out.println("Default Credentials authentication test - Response: " + response);
        assertNotNull("Response should not be null", response);
        assertFalse("Response should not be empty", response.isEmpty());
    }

    @Test
    public void testIAMKeysAuthenticationDirect() throws Exception {
        // This test creates BedrockAIClient directly with IAM Keys to bypass priority logic
        // Check if IAM Keys are configured
        String accessKeyId = propertyReader.getBedrockAccessKeyId();
        String secretAccessKey = propertyReader.getBedrockSecretAccessKey();
        String sessionToken = propertyReader.getBedrockSessionToken();

        if (accessKeyId == null || accessKeyId.trim().isEmpty() || accessKeyId.startsWith("$")) {
            System.out.println("Skipping testIAMKeysAuthenticationDirect: BEDROCK_ACCESS_KEY_ID not configured");
            return;
        }
        if (secretAccessKey == null || secretAccessKey.trim().isEmpty() || secretAccessKey.startsWith("$")) {
            System.out.println("Skipping testIAMKeysAuthenticationDirect: BEDROCK_SECRET_ACCESS_KEY not configured");
            return;
        }

        String bedrockModelId = propertyReader.getBedrockModelId();
        String bedrockBasePath = propertyReader.getBedrockBasePath();
        String bedrockRegion = propertyReader.getBedrockRegion();

        if (bedrockModelId == null || bedrockModelId.trim().isEmpty() || bedrockModelId.startsWith("$")) {
            fail("BEDROCK_MODEL_ID is required for IAM Keys test");
        }
        if (bedrockBasePath == null || bedrockBasePath.trim().isEmpty()) {
            fail("BEDROCK_BASE_PATH or BEDROCK_REGION is required for IAM Keys test");
        }
        if (bedrockRegion == null || bedrockRegion.trim().isEmpty()) {
            fail("BEDROCK_REGION is required for IAM Keys test (needed for AWS Signature V4)");
        }

        // Create client directly with IAM Keys (bypassing BasicBedrockAI priority logic)
        BedrockAIClient client = new BedrockAIClient(
            bedrockBasePath,
            bedrockRegion,
            bedrockModelId,
            accessKeyId,
            secretAccessKey,
            sessionToken, // May be null
            propertyReader.getBedrockMaxTokens(),
            propertyReader.getBedrockTemperature(),
            observer,
            null, // Custom headers
            null  // Logger
        );

        // Verify it's using IAM Keys authentication
        assertEquals("IAM_KEYS", client.getAuthenticationStrategy().getAuthenticationType());

        // Test simple chat
        String prompt = "Say 'Hello' in one word.";
        String response = client.chat(prompt);

        System.out.println("IAM Keys authentication test (direct) - Response: " + response);
        assertNotNull("Response should not be null", response);
        assertFalse("Response should not be empty", response.isEmpty());
    }

    @Test
    public void testAuthenticationPriority() throws Exception {
        // This test verifies that authentication priority is correct:
        // Bearer Token > IAM Keys > Default Credentials

        String bearerToken = propertyReader.getBedrockBearerToken();
        String accessKeyId = propertyReader.getBedrockAccessKeyId();
        String secretAccessKey = propertyReader.getBedrockSecretAccessKey();

        String bedrockModelId = propertyReader.getBedrockModelId();
        String bedrockBasePath = propertyReader.getBedrockBasePath();

        if (bedrockModelId == null || bedrockModelId.trim().isEmpty() || bedrockModelId.startsWith("$")) {
            System.out.println("Skipping testAuthenticationPriority: BEDROCK_MODEL_ID not configured");
            return;
        }
        if (bedrockBasePath == null || bedrockBasePath.trim().isEmpty()) {
            System.out.println("Skipping testAuthenticationPriority: BEDROCK_BASE_PATH or BEDROCK_REGION not configured");
            return;
        }

        PropertyReaderConfiguration configuration = new PropertyReaderConfiguration();
        AI bedrockAI = new BasicBedrockAI(observer, configuration);
        BedrockAIClient client = (BedrockAIClient) bedrockAI;

        String expectedAuthType;
        if (bearerToken != null && !bearerToken.trim().isEmpty() && !bearerToken.startsWith("$")) {
            expectedAuthType = "BEARER_TOKEN";
        } else if (accessKeyId != null && !accessKeyId.trim().isEmpty() && !accessKeyId.startsWith("$") &&
                   secretAccessKey != null && !secretAccessKey.trim().isEmpty() && !secretAccessKey.startsWith("$")) {
            expectedAuthType = "IAM_KEYS";
        } else {
            expectedAuthType = "DEFAULT_CREDENTIALS";
        }

        String actualAuthType = client.getAuthenticationStrategy().getAuthenticationType();
        System.out.println("Authentication priority test - Expected: " + expectedAuthType + ", Actual: " + actualAuthType);
        assertEquals("Authentication type should match priority", expectedAuthType, actualAuthType);

        // Test that it actually works
        String prompt = "Say 'Hello' in one word.";
        String response = bedrockAI.chat(prompt);

        assertNotNull("Response should not be null", response);
        assertFalse("Response should not be empty", response.isEmpty());
    }
}

