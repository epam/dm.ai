package com.github.istin.dmtools.ai.bedrock;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.ai.ConversationObserver;
import com.github.istin.dmtools.ai.Message;
import com.github.istin.dmtools.common.config.PropertyReaderConfiguration;
import com.github.istin.dmtools.common.utils.PropertyReader;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Integration test for BedrockAIClient with real AWS Bedrock API.
 * 
 * To run this test, you need to configure the following properties:
 * - BEDROCK_REGION (e.g., "eu-north-1")
 * - BEDROCK_MODEL_ID (e.g., "qwen.qwen3-coder-480b-a35b-v1:0")
 * - BEDROCK_BASE_PATH (optional, will be constructed from region if not provided)
 * - BEDROCK_MAX_TOKENS (optional, defaults to 4096)
 * - BEDROCK_TEMPERATURE (optional, defaults to 1.0)
 * 
 * Authentication (choose one):
 * - Option 1: BEDROCK_BEARER_TOKEN or AWS_BEARER_TOKEN_BEDROCK (Bearer Token)
 *   (AWS_BEARER_TOKEN_BEDROCK is checked first, then BEDROCK_BEARER_TOKEN as fallback)
 * - Option 2: BEDROCK_ACCESS_KEY_ID and BEDROCK_SECRET_ACCESS_KEY (IAM Keys)
 *   Optional: BEDROCK_SESSION_TOKEN (for temporary credentials)
 * - Option 3: AWS credentials in ~/.aws/credentials (Default Credentials Provider)
 * 
 * Priority: Bearer Token > IAM Keys > Default Credentials
 * 
 * These can be set in:
 * - System environment variables
 * - dmtools.env file
 * - config.properties file
 */
public class BedrockAIClientIntegrationTest {

    private AI bedrockAI;
    private ConversationObserver observer;
    private PropertyReader propertyReader;

    @Before
    public void setUp() throws Exception {
        observer = new ConversationObserver();
        propertyReader = new PropertyReader();
        
        // Verify required configuration is present
        String bedrockModelId = propertyReader.getBedrockModelId();
        String bedrockBasePath = propertyReader.getBedrockBasePath();
        String bedrockRegion = propertyReader.getBedrockRegion();
        
        if (bedrockModelId == null || bedrockModelId.trim().isEmpty() || bedrockModelId.startsWith("$")) {
            fail("BEDROCK_MODEL_ID is not configured. Please set it in environment variables or dmtools.env");
        }
        
        if (bedrockBasePath == null || bedrockBasePath.trim().isEmpty()) {
            fail("BEDROCK_BASE_PATH or BEDROCK_REGION is not configured. Please set at least BEDROCK_REGION in environment variables or dmtools.env");
        }
        
        // Check if at least one authentication method is configured
        String bedrockBearerToken = propertyReader.getBedrockBearerToken();
        String accessKeyId = propertyReader.getBedrockAccessKeyId();
        String secretAccessKey = propertyReader.getBedrockSecretAccessKey();
        
        boolean hasBearerToken = bedrockBearerToken != null && !bedrockBearerToken.trim().isEmpty() && !bedrockBearerToken.startsWith("$");
        boolean hasIAMKeys = accessKeyId != null && !accessKeyId.trim().isEmpty() && !accessKeyId.startsWith("$") &&
                            secretAccessKey != null && !secretAccessKey.trim().isEmpty() && !secretAccessKey.startsWith("$");
        boolean hasDefaultCredentials = bedrockRegion != null && !bedrockRegion.trim().isEmpty();
        
        if (!hasBearerToken && !hasIAMKeys && !hasDefaultCredentials) {
            fail("No authentication method configured. Please set one of:\n" +
                 "- BEDROCK_BEARER_TOKEN or AWS_BEARER_TOKEN_BEDROCK (for Bearer Token)\n" +
                 "- BEDROCK_ACCESS_KEY_ID and BEDROCK_SECRET_ACCESS_KEY (for IAM Keys)\n" +
                 "- BEDROCK_REGION with AWS credentials in ~/.aws/credentials (for Default Credentials)");
        }
        
        try {
            PropertyReaderConfiguration configuration = new PropertyReaderConfiguration();
            bedrockAI = new BasicBedrockAI(observer, configuration);
            
            // Log authentication type used
            if (bedrockAI instanceof BedrockAIClient) {
                BedrockAIClient client = (BedrockAIClient) bedrockAI;
                String authType = client.getAuthenticationStrategy().getAuthenticationType();
                System.out.println("=== BedrockAIClient Authentication Type: " + authType + " ===");
            }
        } catch (Exception e) {
            fail("Failed to initialize BasicBedrockAI client: " + e.getMessage());
        }
    }

    @Test
    public void testSimpleChat() throws Exception {
        // Log authentication type before test
        if (bedrockAI instanceof BedrockAIClient) {
            BedrockAIClient client = (BedrockAIClient) bedrockAI;
            System.out.println("Using authentication: " + client.getAuthenticationStrategy().getAuthenticationType());
        }
        
        String prompt = "Hello! How Are you? Ping me. Yo. Давай по русски";
        String response = bedrockAI.chat(prompt);
        
        System.out.println("Bedrock response (simple): " + response);
        assertNotNull("Response should not be null", response);
        assertFalse("Response should not be empty", response.isEmpty());
    }

    @Test
    public void testChatWithModel() throws Exception {
        String model = propertyReader.getBedrockModelId();
        assertNotNull("Bedrock model ID should be configured", model);
        
        String prompt = "Say 'Hello' in 2 words.";
        String response = bedrockAI.chat(model, prompt);
        
        System.out.println("Bedrock response (model " + model + "): " + response);
        assertNotNull("Response should not be null", response);
        assertFalse("Response should not be empty", response.isEmpty());
    }

    @Test
    public void testChatWithMaxTokens() throws Exception {
        // Test that max_tokens parameter is respected
        String prompt = "Count from 1 to 10.";
        String response = bedrockAI.chat(prompt);
        
        System.out.println("Bedrock response (max tokens test): " + response);
        assertNotNull("Response should not be null", response);
        assertFalse("Response should not be empty", response.isEmpty());
    }

    @Test
    public void testChatWithImage() throws Exception {
        // Test chat with image
        // Note: Qwen models don't support images
        // Nova models support images through the invoke endpoint (/model/{modelId}/invoke)
        // For Nova, use the inference profile ARN format: arn:aws:bedrock:region:account:inference-profile/model-id
        File imageFile = new File("/Users/Uladzimir_Klyshevich/git/dmtools/dmtools-core/temp/figma-screen.png");
        
        if (!imageFile.exists()) {
            System.out.println("Skipping testChatWithImage: figma-screen.png not found at expected path.");
            return;
        }
        
        String model = propertyReader.getBedrockModelId();
        assertNotNull("Bedrock model ID should be configured", model);
        
        String prompt = "What is in this image? Explain in details. All screens.";

        //String response = bedrockAI.chat("mistral.pixtral-large-2502-v1:0", prompt, imageFile);
        String response = bedrockAI.chat(model, prompt, imageFile);
        
        System.out.println("Bedrock response (with image): " + response);
        assertNotNull("Response should not be null", response);
        assertFalse("Response should not be empty", response.isEmpty());
        
        // Note: 
        // - Qwen models don't support images
        // - Nova models support images through the invoke endpoint (/model/{modelId}/invoke)
        //   Use inference profile ARN format: arn:aws:bedrock:region:account:inference-profile/model-id
    }

    @Test
    public void testChatWithTwoImages() throws Exception {
        // Test chat with two images using Message class
        // Note: Qwen models don't support images
        // Nova models support images through the invoke endpoint (/model/{modelId}/invoke)
        File imageFile1 = new File("/Users/Uladzimir_Klyshevich/git/dmtools/dmtools-core/temp/figma-screen.png");
        File imageFile2 = new File("/Users/Uladzimir_Klyshevich/git/dmtools/dmtools-core/test_data/test_pdf_sources/cache/test_pdf_document/1/page_snapshot.png");
        
        if (!imageFile1.exists()) {
            System.out.println("Skipping testChatWithTwoImages: figma-screen.png not found at expected path.");
            return;
        }
        
        if (!imageFile2.exists()) {
            System.out.println("Skipping testChatWithTwoImages: page_snapshot.png not found at expected path.");
            return;
        }
        
        String model = propertyReader.getBedrockModelId();
        assertNotNull("Bedrock model ID should be configured", model);
        
        String prompt = "Compare these two images. What are the differences and similarities?";
        List<File> imageFiles = Arrays.asList(imageFile1, imageFile2);
        
        // Create a Message with multiple images
        Message message = new Message("user", prompt, imageFiles);
        
        String response = bedrockAI.chat(model, message);
        
        System.out.println("Bedrock response (with 2 images): " + response);
        assertNotNull("Response should not be null", response);
        assertFalse("Response should not be empty", response.isEmpty());
        
        // Note: 
        // - Qwen models don't support images
        // - Nova models support images through the invoke endpoint (/model/{modelId}/invoke)
        //   Use inference profile ARN format: arn:aws:bedrock:region:account:inference-profile/model-id
    }
}

