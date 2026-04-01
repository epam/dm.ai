// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.ai.bedrock;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.ai.AIFileFilter;
import com.github.istin.dmtools.ai.ConversationObserver;
import com.github.istin.dmtools.ai.Message;
import com.github.istin.dmtools.ai.model.Metadata;
import com.github.istin.dmtools.ai.bedrock.auth.BedrockAuthenticationStrategy;
import com.github.istin.dmtools.ai.bedrock.auth.BearerTokenAuthenticationStrategy;
import com.github.istin.dmtools.ai.bedrock.auth.DefaultCredentialsAuthenticationStrategy;
import com.github.istin.dmtools.ai.bedrock.auth.IAMKeysAuthenticationStrategy;
import com.github.istin.dmtools.common.networking.GenericRequest;
import com.github.istin.dmtools.common.utils.ImageUtils;
import com.github.istin.dmtools.common.utils.RetryUtil;
import com.github.istin.dmtools.mcp.MCPParam;
import com.github.istin.dmtools.mcp.MCPTool;
import com.github.istin.dmtools.networking.AbstractRestClient;
import com.google.gson.Gson;
import lombok.Getter;
import lombok.Setter;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.signer.Aws4Signer;
import software.amazon.awssdk.auth.signer.params.Aws4SignerParams;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.regions.Region;

public class BedrockAIClient extends AbstractRestClient implements AI {

    private final Logger logger;

    @Getter
    private final String modelId;

    @Getter
    private final String region;

    @Getter
    private final String bearerToken;

    @Getter
    private final int maxTokens;

    @Getter
    private final double temperature;

    @Setter
    private Metadata metadata;

    @Getter
    @Setter
    private ConversationObserver conversationObserver;

    @Getter
    private final Map<String, String> customHeaders;
    
    @Getter
    private final BedrockAuthenticationStrategy authenticationStrategy;

    // Constructor with Bearer Token (existing, for backward compatibility)
    public BedrockAIClient(String basePath, String region, String modelId, String bearerToken, 
                          int maxTokens, double temperature, ConversationObserver conversationObserver, 
                          Map<String, String> customHeaders, Logger logger) throws IOException {
        super(basePath, null);
        this.region = region;
        this.modelId = modelId;
        this.bearerToken = bearerToken;
        this.maxTokens = maxTokens;
        this.temperature = temperature;
        this.conversationObserver = conversationObserver;
        this.customHeaders = customHeaders != null ? new HashMap<>(customHeaders) : null;
        this.logger = logger != null ? logger : LogManager.getLogger(BedrockAIClient.class);
        this.authenticationStrategy = new BearerTokenAuthenticationStrategy(bearerToken);
        this.logger.info("BedrockAIClient initialized with authentication type: BEARER_TOKEN");
        setCachePostRequestsEnabled(true);
    }
    
    // Constructor with IAM Access Keys
    public BedrockAIClient(String basePath, String region, String modelId, 
                          String accessKeyId, String secretAccessKey, String sessionToken,
                          int maxTokens, double temperature, 
                          ConversationObserver conversationObserver, 
                          Map<String, String> customHeaders, Logger logger) throws IOException {
        super(basePath, null);
        this.region = region;
        this.modelId = modelId;
        this.bearerToken = null;  // Not used for IAM authentication
        this.maxTokens = maxTokens;
        this.temperature = temperature;
        this.conversationObserver = conversationObserver;
        this.customHeaders = customHeaders != null ? new HashMap<>(customHeaders) : null;
        this.logger = logger != null ? logger : LogManager.getLogger(BedrockAIClient.class);
        this.authenticationStrategy = new IAMKeysAuthenticationStrategy(accessKeyId, secretAccessKey, sessionToken);
        this.logger.info("BedrockAIClient initialized with authentication type: IAM_KEYS (Access Key ID: {}...)", 
                accessKeyId != null && accessKeyId.length() > 8 ? accessKeyId.substring(0, 8) : "N/A");
        setCachePostRequestsEnabled(true);
    }
    
    // Constructor with Default Credentials Provider (reads from ~/.aws/credentials)
    public BedrockAIClient(String basePath, String region, String modelId,
                          int maxTokens, double temperature, 
                          ConversationObserver conversationObserver, 
                          Map<String, String> customHeaders, Logger logger) throws IOException {
        super(basePath, null);
        this.region = region;
        this.modelId = modelId;
        this.bearerToken = null;  // Not used for default credentials
        this.maxTokens = maxTokens;
        this.temperature = temperature;
        this.conversationObserver = conversationObserver;
        this.customHeaders = customHeaders != null ? new HashMap<>(customHeaders) : null;
        this.logger = logger != null ? logger : LogManager.getLogger(BedrockAIClient.class);
        this.authenticationStrategy = new DefaultCredentialsAuthenticationStrategy();
        this.logger.info("BedrockAIClient initialized with authentication type: DEFAULT_CREDENTIALS (using ~/.aws/credentials or environment variables)");
        setCachePostRequestsEnabled(true);
    }
    
    /**
     * Protected constructor for delegation pattern.
     * Used by BasicBedrockAI to delegate to the appropriate authentication strategy.
     */
    protected BedrockAIClient(BedrockAIClient delegate) throws IOException {
        super(delegate.getBasePath(), null);
        this.region = delegate.region;
        this.modelId = delegate.modelId;
        this.bearerToken = delegate.bearerToken;
        this.maxTokens = delegate.maxTokens;
        this.temperature = delegate.temperature;
        this.conversationObserver = delegate.conversationObserver;
        this.customHeaders = delegate.customHeaders;
        this.logger = delegate.logger;
        this.authenticationStrategy = delegate.authenticationStrategy;
        setCachePostRequestsEnabled(true);
    }

    public String getName() {
        return modelId;
    }

    @Override
    public String roleName() {
        return "assistant";
    }

    @Override
    public String path(String path) {
        // Bedrock endpoint format: /model/{modelId}/invoke or /converse
        if (path.startsWith("/model/") || path.startsWith("/converse")) {
            return getBasePath() + path;
        }
        return getBasePath() + "/model/" + modelId + "/invoke";
    }

    @Override
    public Request.Builder sign(Request.Builder builder) {
        // Don't set Content-Type here - let RequestBody's MediaType set it
        // This ensures we use "application/json" without charset for Bedrock API compatibility
        
        // Use authentication strategy to sign the request
        return authenticationStrategy.sign(builder, customHeaders);
    }

    @Override
    public int getTimeout() {
        return 700;
    }

    /**
     * Detects the model type based on model ID
     */
    private ModelType detectModelType(String modelId) {
        if (modelId == null) {
            return ModelType.CLAUDE;
        }
        String lowerModelId = modelId.toLowerCase();
        
        // Check for Claude models (Anthropic)
        if (lowerModelId.contains("claude") || lowerModelId.contains("anthropic")) {
            return ModelType.CLAUDE;
        }
        
        // Check for Qwen models
        if (lowerModelId.contains("qwen")) {
            return ModelType.QWEN;
        }
        
        // Check for Nova models specifically (not all Amazon models are Nova)
        // Nova models have format: eu.amazon.nova-* or amazon.nova-*
        // Examples: eu.amazon.nova-lite-v1:0, amazon.nova-pro-v1:0
        if (lowerModelId.contains("nova")) {
            return ModelType.NOVA;
        }
        
        // Check for Mistral models
        // Mistral models have format: mistral.*
        // Examples: mistral.mistral-large-2407-v1:0, mistral.pixtral-large-2502-v1:0
        if (lowerModelId.contains("mistral")) {
            return ModelType.MISTRAL;
        }
        
        // Default to Claude for unknown models (most Bedrock models use Claude-like format)
        return ModelType.CLAUDE;
    }

    /**
     * Checks if the model type supports image inputs
     * @param modelType The model type to check
     * @return true if the model supports images, false otherwise
     */
    private boolean supportsImages(ModelType modelType) {
        // QWEN models are text-only and do not support images
        // Nova models support images through the Converse API
        return modelType != ModelType.QWEN;
    }
    

    @Override
    @MCPTool(
        name = "bedrock_ai_chat",
        description = "Send a text message to AWS Bedrock AI and get response",
        integration = "ai"
    )
    public String chat(@MCPParam(name = "message", description = "Text message to send to AI") String message) throws Exception {
        return chat(modelId, message);
    }

    @Override
    public String chat(String model, String message, File imageFile) throws Exception {
        if (model == null) {
            model = this.modelId;
        }
        // Apply attachment filter: skip file if it exceeds size limit or fails extension check
        if (imageFile != null && !new AIFileFilter().shouldInclude(imageFile)) {
            imageFile = null;
        }
        logger.info("-------- message to ai --------");
        logger.info(message);
        logger.info("-------- start chat ai --------");
        if (conversationObserver != null) {
            conversationObserver.addMessage(new ConversationObserver.Message("DMTools", message));
        }

        JSONArray messagesArray = new JSONArray();
        ModelType modelType = detectModelType(model);
        
        if (imageFile != null) {
            // Check if model supports images
            if (!supportsImages(modelType)) {
                logger.warn("Model {} (type: {}) does not support images. Ignoring image file and sending text only.", model, modelType);
                // Text only - model doesn't support images
                if (modelType == ModelType.CLAUDE) {
                    JSONArray contentArray = new JSONArray();
                    contentArray.put(new JSONObject()
                            .put("type", "text")
                            .put("text", message));
                    messagesArray.put(new JSONObject()
                            .put("role", "user")
                            .put("content", contentArray));
                } else if (modelType == ModelType.NOVA) {
                    // Nova models require content to be an array with text objects
                    JSONArray contentArray = new JSONArray();
                    contentArray.put(new JSONObject()
                            .put("text", message));
                    messagesArray.put(new JSONObject()
                            .put("role", "user")
                            .put("content", contentArray));
                } else {
                    // For other model types, use plain string
                    messagesArray.put(new JSONObject()
                            .put("role", "user")
                            .put("content", message));
                }
            } else {
                // Build content array with image and text
                JSONArray contentArray = buildContentArrayWithImage(message, imageFile, modelType);
                messagesArray.put(new JSONObject()
                        .put("role", "user")
                        .put("content", contentArray));
            }
        } else {
            // Text only - use block-array format for Claude models
            if (modelType == ModelType.CLAUDE) {
                JSONArray contentArray = new JSONArray();
                contentArray.put(new JSONObject()
                        .put("type", "text")
                        .put("text", message));
                messagesArray.put(new JSONObject()
                        .put("role", "user")
                        .put("content", contentArray));
            } else if (modelType == ModelType.NOVA) {
                // Nova models require content to be an array, but with text objects
                JSONArray contentArray = new JSONArray();
                contentArray.put(new JSONObject()
                        .put("text", message));
                messagesArray.put(new JSONObject()
                        .put("role", "user")
                        .put("content", contentArray));
            } else {
                // For other model types, use plain string
                messagesArray.put(new JSONObject()
                        .put("role", "user")
                        .put("content", message));
            }
        }

        return performChatCompletion(model, messagesArray, modelType);
    }

    private JSONArray buildContentArrayWithImage(String message, File imageFile, ModelType modelType) throws IOException {
        JSONArray contentArray = new JSONArray();
        
        if (modelType == ModelType.NOVA) {
            // Nova models use invoke endpoint format for images
            // Content array: [{"image":{"format":"jpeg","source":{"bytes":"..."}}}, {"text":"..."}]
            // Note: Image should come first, then text
            // For invoke endpoint, bytes is Base64-encoded string
            
            // Add image part for Nova invoke format
            String extension = ImageUtils.getExtension(imageFile);
            String mimeType = ImageUtils.getMimeType(imageFile);
            
            // Convert MIME type to format (jpeg, png, gif, webp)
            // Note: WebP may not be supported by default Java ImageIO
            String format = "jpeg";
            if (mimeType.contains("png")) {
                format = "png";
            } else if (mimeType.contains("gif")) {
                format = "gif";
            } else if (mimeType.contains("webp")) {
                format = "webp";
                // Warn about potential WebP issues
                logger.warn("WebP format detected. Java ImageIO may not support WebP by default. " +
                           "If conversion fails, consider converting to PNG/JPEG first.");
            }
            
            // Convert image to base64 with error handling for WebP
            String imageBase64;
            try {
                imageBase64 = ImageUtils.convertToBase64(imageFile, extension);
            } catch (Exception e) {
                if (format.equals("webp")) {
                    logger.error("Failed to convert WebP image. Java ImageIO typically doesn't support WebP. " +
                               "Error: {}. Consider converting the image to PNG or JPEG first.", e.getMessage());
                    throw new IOException("WebP image conversion failed. Java ImageIO doesn't support WebP by default. " +
                                         "Please convert the image to PNG or JPEG format.", e);
                }
                throw e;
            }
            
            // Validate image size (approximately 5MB limit)
            long fileSize = imageFile.length();
            if (fileSize > 5 * 1024 * 1024) {
                logger.warn("Image file size {} exceeds 5MB limit, may cause issues", fileSize);
            }
            
            // Add image first
            contentArray.put(new JSONObject()
                    .put("image", new JSONObject()
                            .put("format", format)
                            .put("source", new JSONObject()
                                    .put("bytes", imageBase64))));
            
            // Add text after image
            if (message != null && !message.trim().isEmpty()) {
                contentArray.put(new JSONObject()
                        .put("text", message));
            }
        } else {
            // Claude format - uses type field
            // Add text part if message is not null or empty
            if (message != null && !message.trim().isEmpty()) {
                contentArray.put(new JSONObject()
                        .put("type", "text")
                        .put("text", message));
            }
            
            // Add image part
            String extension = ImageUtils.getExtension(imageFile);
            String mimeType = ImageUtils.getMimeType(imageFile);
            
            // Convert image to base64 with error handling for WebP
            String imageBase64;
            try {
                imageBase64 = ImageUtils.convertToBase64(imageFile, extension);
            } catch (Exception e) {
                if (mimeType.contains("webp")) {
                    logger.error("Failed to convert WebP image. Java ImageIO typically doesn't support WebP. " +
                               "Error: {}. Consider converting the image to PNG or JPEG first.", e.getMessage());
                    throw new IOException("WebP image conversion failed. Java ImageIO doesn't support WebP by default. " +
                                         "Please convert the image to PNG or JPEG format.", e);
                }
                throw e;
            }
            
            // Validate image size (approximately 5MB limit)
            long fileSize = imageFile.length();
            if (fileSize > 5 * 1024 * 1024) {
                logger.warn("Image file size {} exceeds 5MB limit, may cause issues", fileSize);
            }
            
            JSONObject imageSource = new JSONObject()
                    .put("type", "base64")
                    .put("media_type", mimeType)
                    .put("data", imageBase64);
            
            contentArray.put(new JSONObject()
                    .put("type", "image")
                    .put("source", imageSource));
        }
        
        return contentArray;
    }

    private String performChatCompletion(String model, JSONArray messagesArray, ModelType modelType) throws Exception {
        // Always use invoke endpoint (Nova models support images via invoke)
        // For inference profile ARN, use the full ARN (Bedrock InvokeModel accepts full ARN in path)
        // Inference profile ARN format: arn:aws:bedrock:region:account:inference-profile/model-id
        // Direct model ID format: model-id (e.g., eu.amazon.nova-lite-v1:0)
        // Note: Bedrock API expects the full ARN or model ID in the path, not just the last segment
        String modelIdForPath = model;
        if (model != null && model.contains("inference-profile/")) {
            // Use full inference profile ARN - Bedrock InvokeModel accepts it in the path
            // URL encode special characters (like ':') but keep the ARN structure
            try {
                // URL encode the ARN, but replace spaces with %20 and keep other encoding
                modelIdForPath = java.net.URLEncoder.encode(model, "UTF-8")
                    .replace("+", "%20"); // Replace + with %20 for proper URL encoding
                logger.debug("Using full inference profile ARN '{}' (URL encoded) for path", model);
            } catch (java.io.UnsupportedEncodingException e) {
                logger.warn("Failed to URL encode inference profile ARN, using as-is: {}", e.getMessage());
                // Fallback: use as-is (may cause issues with special characters)
            }
        }
        String path = path("/model/" + modelIdForPath + "/invoke");
        logger.info(path);
        GenericRequest postRequest = new GenericRequest(this, path);

        // Build standard invoke request body
        JSONObject jsonObject = buildRequestBody(model, messagesArray, modelType);
        if (metadata != null) {
            jsonObject.put("metadata", new JSONObject(new Gson().toJson(metadata)));
        }
        
        postRequest.setBody(jsonObject.toString());
        logger.debug("Request body for {}: {}", model, sanitizeJsonForLogging(jsonObject));
        return RetryUtil.executeWithRetry(() -> processResponse(model, postRequest, modelType));
    }
    
    /**
     * Sanitizes JSON for logging by truncating long base64 strings in image data
     */
    private String sanitizeJsonForLogging(JSONObject jsonObject) {
        try {
            Object sanitized = sanitizeJsonObject(jsonObject);
            if (sanitized instanceof JSONObject) {
                return ((JSONObject) sanitized).toString();
            } else {
                return sanitized.toString();
            }
        } catch (Exception e) {
            // If sanitization fails, return truncated original
            String original = jsonObject.toString();
            if (original.length() > 500) {
                return original.substring(0, 500) + "... [truncated]";
            }
            return original;
        }
    }
    
    /**
     * Recursively sanitizes JSON objects and arrays, truncating long base64 strings
     */
    private Object sanitizeJsonObject(Object obj) {
        if (obj instanceof JSONObject) {
            JSONObject jsonObj = (JSONObject) obj;
            JSONObject sanitized = new JSONObject();
            for (String key : jsonObj.keySet()) {
                Object value = jsonObj.get(key);
                if (key.equals("bytes") || key.equals("data")) {
                    // Truncate base64 strings
                    if (value instanceof String) {
                        String str = (String) value;
                        if (str.length() > 100) {
                            sanitized.put(key, str.substring(0, 50) + "... [base64 truncated, " + str.length() + " chars]");
                        } else {
                            sanitized.put(key, value);
                        }
                    } else {
                        sanitized.put(key, sanitizeJsonObject(value));
                    }
                } else {
                    sanitized.put(key, sanitizeJsonObject(value));
                }
            }
            return sanitized;
        } else if (obj instanceof JSONArray) {
            JSONArray jsonArray = (JSONArray) obj;
            JSONArray sanitized = new JSONArray();
            for (int i = 0; i < jsonArray.length(); i++) {
                sanitized.put(sanitizeJsonObject(jsonArray.get(i)));
            }
            return sanitized;
        } else {
            return obj;
        }
    }
    

    private JSONObject buildRequestBody(String model, JSONArray messagesArray, ModelType modelType) {
        JSONObject jsonObject = new JSONObject();
        
        if (modelType == ModelType.CLAUDE) {
            // Claude Sonnet format
            jsonObject.put("anthropic_version", "bedrock-2023-05-31");
            jsonObject.put("messages", messagesArray);
            jsonObject.put("max_tokens", maxTokens);
            jsonObject.put("temperature", temperature);
            // Optional parameters can be added here if needed
        } else if (modelType == ModelType.QWEN) {
            // Qwen format (text only, no images)
            jsonObject.put("messages", messagesArray);
            jsonObject.put("max_tokens", maxTokens);
            jsonObject.put("temperature", temperature);
            // Qwen-specific parameters can be added here if needed
        } else if (modelType == ModelType.NOVA) {
            // Amazon Nova format - always use schemaVersion and inferenceConfig for invoke endpoint
            // Nova's Invoke API requires the messages schema structure
            jsonObject.put("schemaVersion", "messages-v1");
            jsonObject.put("messages", messagesArray);
            
            JSONObject inferenceConfig = new JSONObject();
            inferenceConfig.put("maxTokens", maxTokens);
            inferenceConfig.put("temperature", temperature);
            inferenceConfig.put("topP", 0.9); // Default topP for Nova
            inferenceConfig.put("stopSequences", new JSONArray());
            jsonObject.put("inferenceConfig", inferenceConfig);
        } else if (modelType == ModelType.MISTRAL) {
            // Mistral format - similar to Qwen but may have different response structure
            jsonObject.put("messages", messagesArray);
            jsonObject.put("max_tokens", maxTokens);
            jsonObject.put("temperature", temperature);
            // Mistral-specific parameters can be added here if needed
        }
        
        return jsonObject;
    }
    
    /**
     * Checks if messages array contains any images
     */
    private boolean hasImagesInMessages(JSONArray messagesArray) {
        for (int i = 0; i < messagesArray.length(); i++) {
            JSONObject message = messagesArray.getJSONObject(i);
            Object contentObj = message.opt("content");
            if (contentObj instanceof JSONArray) {
                JSONArray contentArray = (JSONArray) contentObj;
                for (int j = 0; j < contentArray.length(); j++) {
                    Object item = contentArray.get(j);
                    if (item instanceof JSONObject) {
                        JSONObject contentItem = (JSONObject) item;
                        if (contentItem.has("image") || (contentItem.has("type") && "image".equals(contentItem.optString("type")))) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private String processResponse(String model, GenericRequest postRequest, ModelType modelType) throws IOException {
        String response = post(postRequest);
        logger.info(response);
        
        String content;
        try {
            JSONObject jsonResponse = new JSONObject(response);
            
            // Check for error field in JSON response (proper error detection)
            // Check for Output with __type (AWS error format)
            JSONObject errorOutput = jsonResponse.optJSONObject("Output");
            if (errorOutput != null && errorOutput.has("__type")) {
                String errorType = errorOutput.optString("__type", "");
                logger.error("Error in Bedrock response: {} - {}", errorType, response);
                content = response;
            } else if (jsonResponse.has("error") || jsonResponse.has("__type") || jsonResponse.has("message")) {
                // This is an actual error response from Bedrock API
                logger.error("Error in Bedrock response: {}", response);
                content = response;
            } else {
                // Parse content based on model type
                if (modelType == ModelType.CLAUDE) {
                    // Claude response format: content[].text
                    JSONArray contentArray = jsonResponse.optJSONArray("content");
                    if (contentArray != null && contentArray.length() > 0) {
                        JSONObject firstContent = contentArray.getJSONObject(0);
                        content = firstContent.optString("text", "");
                    } else {
                        content = "";
                    }
                    
                    // Log token usage if available
                    JSONObject usage = jsonResponse.optJSONObject("usage");
                    if (usage != null) {
                        logger.debug("Token usage - input: {}, output: {}", 
                                usage.optInt("input_tokens", 0), 
                                usage.optInt("output_tokens", 0));
                    }
                    
                    // Log stop reason
                    String stopReason = jsonResponse.optString("stop_reason", "");
                    if (!stopReason.isEmpty()) {
                        logger.debug("Stop reason: {}", stopReason);
                    }
                } else if (modelType == ModelType.QWEN) {
                    // Qwen response format: choices[0].message.content (or output.choices[0].message.content)
                    JSONArray choices = null;
                    // Try root-level choices first (actual API format)
                    if (jsonResponse.has("choices")) {
                        choices = jsonResponse.optJSONArray("choices");
                    } else {
                        // Fallback to output.choices format
                        JSONObject output = jsonResponse.optJSONObject("output");
                        if (output != null) {
                            choices = output.optJSONArray("choices");
                        }
                    }
                    
                    if (choices != null && choices.length() > 0) {
                        JSONObject firstChoice = choices.getJSONObject(0);
                        JSONObject message = firstChoice.optJSONObject("message");
                        if (message != null) {
                            content = message.optString("content", "");
                        } else {
                            content = "";
                        }
                    } else {
                        content = "";
                    }
                    
                    // Log token usage if available
                    JSONObject usage = jsonResponse.optJSONObject("usage");
                    if (usage != null) {
                        logger.debug("Token usage - prompt: {}, completion: {}, total: {}", 
                                usage.optInt("prompt_tokens", 0), 
                                usage.optInt("completion_tokens", 0),
                                usage.optInt("total_tokens", 0));
                    }
                } else if (modelType == ModelType.NOVA) {
                    // Nova response format: output.message.content[0].text
                    JSONObject output = jsonResponse.optJSONObject("output");
                    if (output != null) {
                        JSONObject message = output.optJSONObject("message");
                        if (message != null) {
                            JSONArray contentArray = message.optJSONArray("content");
                            if (contentArray != null && contentArray.length() > 0) {
                                JSONObject firstContent = contentArray.getJSONObject(0);
                                content = firstContent.optString("text", "");
                            } else {
                                content = "";
                            }
                        } else {
                            content = "";
                        }
                    } else {
                        content = "";
                    }
                    
                    // Log token usage if available
                    JSONObject usage = jsonResponse.optJSONObject("usage");
                    if (usage != null) {
                        logger.debug("Token usage - input: {}, output: {}, total: {}", 
                                usage.optInt("inputTokens", 0), 
                                usage.optInt("outputTokens", 0),
                                usage.optInt("totalTokens", 0));
                    }
                } else if (modelType == ModelType.MISTRAL) {
                    // Mistral response format: similar to Qwen - choices[0].message.content
                    JSONArray choices = null;
                    if (jsonResponse.has("choices")) {
                        choices = jsonResponse.optJSONArray("choices");
                    } else {
                        JSONObject output = jsonResponse.optJSONObject("output");
                        if (output != null) {
                            choices = output.optJSONArray("choices");
                        }
                    }
                    
                    if (choices != null && choices.length() > 0) {
                        JSONObject firstChoice = choices.getJSONObject(0);
                        JSONObject message = firstChoice.optJSONObject("message");
                        if (message != null) {
                            content = message.optString("content", "");
                        } else {
                            content = "";
                        }
                    } else {
                        content = "";
                    }
                    
                    // Log token usage if available
                    JSONObject usage = jsonResponse.optJSONObject("usage");
                    if (usage != null) {
                        logger.debug("Token usage - prompt: {}, completion: {}, total: {}", 
                                usage.optInt("prompt_tokens", 0), 
                                usage.optInt("completion_tokens", 0),
                                usage.optInt("total_tokens", 0));
                    }
                } else {
                    // Default format (similar to Claude)
                    JSONArray contentArray = jsonResponse.optJSONArray("content");
                    if (contentArray != null && contentArray.length() > 0) {
                        JSONObject firstContent = contentArray.getJSONObject(0);
                        content = firstContent.optString("text", "");
                    } else {
                        content = "";
                    }
                }
                
                // Return empty string if no content was parsed
                if (content == null || content.isEmpty()) {
                    content = "";
                }
            }
        } catch (Exception e) {
            logger.error("Failed to parse Bedrock response: " + e.getMessage(), e);
            // If parsing fails, check if response looks like an error JSON structure
            try {
                JSONObject errorCheck = new JSONObject(response);
                if (errorCheck.has("error") || errorCheck.has("__type") || errorCheck.has("message")) {
                    content = response;
                } else {
                    content = "";
                }
            } catch (Exception parseException) {
                // If response is not valid JSON, return empty string
                content = "";
            }
        }
        
        if (conversationObserver != null) {
            conversationObserver.addMessage(new ConversationObserver.Message(model, content));
        }
        logger.info("-------- ai response --------");
        logger.info(content);
        logger.info("-------- end chat ai --------");
        return content;
    }

    @Override
    public String chat(String model, Message... messages) throws Exception {
        if (model == null) {
            model = this.modelId;
        }

        // Normalize message roles
        Message[] normalizedMessages = normalizeMessageRoles(messages);

        logger.info("-------- start chat ai with messages --------");
        JSONArray messagesArray = new JSONArray();
        ModelType modelType = detectModelType(model);
        
        for (Message message : normalizedMessages) {
            if (conversationObserver != null) {
                conversationObserver.addMessage(new ConversationObserver.Message(message.getRole(), message.getText()));
            }
            logger.info("Processing message from role: {} with text: {}", message.getRole(), message.getText());

            JSONObject messageJson = new JSONObject();
            messageJson.put("role", message.getRole());

            if (message.getFiles() != null && !message.getFiles().isEmpty()) {
                // Check if model supports images
                if (!supportsImages(modelType)) {
                    logger.warn("Model {} (type: {}) does not support images. Ignoring {} image file(s) and sending text only.", 
                            model, modelType, message.getFiles().size());
                    // Text only - model doesn't support images
                    if (modelType == ModelType.NOVA) {
                        // Nova models require content to be an array with text objects
                        JSONArray contentArray = new JSONArray();
                        contentArray.put(new JSONObject()
                                .put("text", message.getText()));
                        messageJson.put("content", contentArray);
                    } else {
                        messageJson.put("content", message.getText());
                    }
                } else {
                    // Handle multiple files - process all images
                    JSONArray contentArray = new JSONArray();
                    
                    // For Nova models, add images first, then text
                    // For Claude models, add text first, then images
                    if (modelType == ModelType.NOVA) {
                        // Add all image files first for Nova
                        for (File imageFile : message.getFiles()) {
                            try {
                                String extension = ImageUtils.getExtension(imageFile);
                                String mimeType = ImageUtils.getMimeType(imageFile);
                                
                                // Nova invoke API format: {"image": {"format": "jpeg", "source": {"bytes": "..."}}}
                                // bytes is Base64-encoded string for invoke endpoint
                                String format = "jpeg";
                                if (mimeType.contains("png")) {
                                    format = "png";
                                } else if (mimeType.contains("gif")) {
                                    format = "gif";
                                } else if (mimeType.contains("webp")) {
                                    format = "webp";
                                    // Warn about potential WebP issues
                                    logger.warn("WebP format detected. Java ImageIO may not support WebP by default. " +
                                               "If conversion fails, consider converting to PNG/JPEG first.");
                                }
                                
                                // Convert image to base64 with error handling for WebP
                                String imageBase64Converted;
                                try {
                                    imageBase64Converted = ImageUtils.convertToBase64(imageFile, extension);
                                } catch (Exception e) {
                                    if (format.equals("webp")) {
                                        logger.error("Failed to convert WebP image. Java ImageIO typically doesn't support WebP. " +
                                                   "Error: {}. Consider converting the image to PNG or JPEG first.", e.getMessage());
                                        throw new IOException("WebP image conversion failed. Java ImageIO doesn't support WebP by default. " +
                                                             "Please convert the image to PNG or JPEG format.", e);
                                    }
                                    throw e;
                                }
                                
                                contentArray.put(new JSONObject()
                                        .put("image", new JSONObject()
                                                .put("format", format)
                                                .put("source", new JSONObject()
                                                        .put("bytes", imageBase64Converted))));
                            } catch (IOException e) {
                                logger.warn("Failed to process image file {}: {}", imageFile.getName(), e.getMessage());
                            }
                        }
                        
                        // Add text after images for Nova
                        if (message.getText() != null && !message.getText().isEmpty()) {
                            contentArray.put(new JSONObject().put("text", message.getText()));
                        }
                    } else {
                        // Claude format: text first, then images
                        // Add text part if text is not null or empty
                        if (message.getText() != null && !message.getText().isEmpty()) {
                            contentArray.put(new JSONObject().put("type", "text").put("text", message.getText()));
                        }
                        
                        // Add all image files
                        for (File imageFile : message.getFiles()) {
                            try {
                                String extension = ImageUtils.getExtension(imageFile);
                                String mimeType = ImageUtils.getMimeType(imageFile);
                                
                                // Convert image to base64 with error handling for WebP
                                String imageBase64;
                                try {
                                    imageBase64 = ImageUtils.convertToBase64(imageFile, extension);
                                } catch (Exception e) {
                                    if (mimeType.contains("webp")) {
                                        logger.error("Failed to convert WebP image. Java ImageIO typically doesn't support WebP. " +
                                                   "Error: {}. Consider converting the image to PNG or JPEG first.", e.getMessage());
                                        throw new IOException("WebP image conversion failed. Java ImageIO doesn't support WebP by default. " +
                                                             "Please convert the image to PNG or JPEG format.", e);
                                    }
                                    throw e;
                                }
                                
                                JSONObject imageSource = new JSONObject()
                                        .put("type", "base64")
                                        .put("media_type", mimeType)
                                        .put("data", imageBase64);
                                
                                contentArray.put(new JSONObject()
                                        .put("type", "image")
                                        .put("source", imageSource));
                            } catch (IOException e) {
                                logger.warn("Failed to process image file {}: {}", imageFile.getName(), e.getMessage());
                            }
                        }
                    }
                    
                    messageJson.put("content", contentArray);
                }
            } else {
                // Text only - use block-array format for Claude models
                if (modelType == ModelType.CLAUDE) {
                    JSONArray contentArray = new JSONArray();
                    contentArray.put(new JSONObject()
                            .put("type", "text")
                            .put("text", message.getText()));
                    messageJson.put("content", contentArray);
                } else if (modelType == ModelType.NOVA) {
                    // Nova models require content to be an array with text objects
                    JSONArray contentArray = new JSONArray();
                    contentArray.put(new JSONObject()
                            .put("text", message.getText()));
                    messageJson.put("content", contentArray);
                } else {
                    // For other model types, use plain string
                    messageJson.put("content", message.getText());
                }
            }
            messagesArray.put(messageJson);
        }
        logger.info("-------- end chat ai with messages processing --------");

        return performChatCompletion(model, messagesArray, modelType);
    }

    @Override
    public String chat(Message... messages) throws Exception {
        return chat(this.modelId, messages);
    }

    @Override
    public String chat(String model, String message, List<File> files) throws Exception {
        if (files == null || files.isEmpty()) {
            return chat(model, message, (File) null);
        }
        // Apply attachment filter before forwarding to AI
        files = new AIFileFilter().filter(files);
        if (files.isEmpty()) {
            return chat(model, message, (File) null);
        }
        
        // For multiple files, create a message with all files
        Message userMessage = new Message("user", message, files);
        return chat(model, userMessage);
    }

    @Override
    public String chat(String model, String message) throws Exception {
        return chat(model, message, (File) null);
    }

    @Override
    protected @NotNull String buildHashForPostRequest(GenericRequest genericRequest, String url) {
        String body = genericRequest.getBody();
        return url + (body != null ? body : "");
    }

    /**
     * Override post method to use MediaType without charset for Bedrock API compatibility.
     * Bedrock Qwen models require "application/json" without charset parameter.
     * This is necessary because AbstractRestClient uses "application/json; charset=utf-8"
     * which some Bedrock models don't accept.
     */
    @Override
    public String post(GenericRequest genericRequest) throws IOException {
        if (genericRequest == null) {
            return "";
        }
        String url = genericRequest.url();

        // Check cache first
        if (isCachePostRequestsEnabled() && !genericRequest.isIgnoreCache()) {
            String value = getCacheFileName(genericRequest);
            File cache = new File(getCacheFolderName());
            cache.mkdirs();
            File cachedFile = new File(getCacheFolderName() + "/" + value);
            if (cachedFile.exists()) {
                logger.info("Read From Cache: ");
                return FileUtils.readFileToString(cachedFile, "UTF-8");
            } else {
                logger.info("Network Request: ");
            }
        } else {
            logger.info("Network Request: ");
        }

        // Use MediaType without charset for Bedrock API compatibility
        // Create RequestBody with bytes to have full control over Content-Type
        String requestBody = genericRequest.getBody();
        if (requestBody == null) {
            logger.error("Request body is null for URL: {}. Bedrock API requires a request body.", url);
            throw new IllegalArgumentException("Request body cannot be null for Bedrock API calls");
        }
        
        byte[] bodyBytes = requestBody.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        MediaType jsonMediaType = MediaType.parse("application/json");
        RequestBody body = RequestBody.create(bodyBytes, jsonMediaType);
        
        // Build request with headers
        Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .header("User-Agent", "DMTools");
        
        // Apply additional headers from GenericRequest
        for (String key : genericRequest.getHeaders().keySet()) {
            requestBuilder.header(key, genericRequest.getHeaders().get(key));
        }
        
        // Set Content-Type exactly as "application/json" without charset
        requestBuilder
                .removeHeader("Content-Type")
                .header("Content-Type", "application/json")
                .post(body);
        
        // Sign the request using authentication strategy
        // For Bearer Token, this adds Authorization header
        // For IAM Keys/Default Credentials, this signs with AWS Signature V4
        String authType = authenticationStrategy.getAuthenticationType();
        logger.info("Signing request with authentication type: {}", authType);
        Request request = authenticationStrategy.signRequest(requestBuilder, url, requestBody, region, customHeaders);
        
        long startTime = System.currentTimeMillis();
        logger.debug("POST request starting for URL: {} (attempt: {}, auth: {})", url, 1, authType);
        
        try (Response response = getClient().newCall(request).execute()) {
            long responseTime = System.currentTimeMillis() - startTime;
            logger.debug("POST response received for URL: {} in {}ms, status: {}", url, responseTime, response.code());
            
            if (response.isSuccessful()) {
                String responseAsString = response.body() != null ? response.body().string() : "";
                logger.debug("POST success for URL: {} ({}ms, {} chars response)", url, responseTime, responseAsString.length());
                
                if (isCachePostRequestsEnabled()) {
                    String value = getCacheFileName(genericRequest);
                    File cache = new File(getCacheFolderName());
                    cache.mkdirs();
                    File cachedFile = new File(getCacheFolderName() + "/" + value);
                    FileUtils.writeStringToFile(cachedFile, responseAsString, "UTF-8");
                }
                return responseAsString;
            } else {
                logger.warn("POST failed for URL: {} ({}ms, status: {})", url, responseTime, response.code());
                throw AbstractRestClient.printAndCreateException(request, response);
            }
        } catch (IOException e) {
            logger.warn("POST connection error for URL: {} - Error: {} (Attempt: {}/{})", url, e.getMessage(), 1, 3);
            throw e;
        }
    }

    /**
     * Gets a list of available foundation models from AWS Bedrock.
     * Uses the control plane endpoint (bedrock.{region}.amazonaws.com) instead of runtime endpoint.
     * Uses the existing execute() infrastructure like POST requests.
     * 
     * @return JSON string containing the list of available foundation models
     * @throws IOException if the request fails
     */
    @MCPTool(
        name = "bedrock_list_models",
        description = "Get a list of available AWS Bedrock foundation models",
        integration = "ai"
    )
    public String getAvailableModels() throws IOException {
        if (region == null || region.trim().isEmpty()) {
            throw new IOException("Bedrock region is not configured. Cannot list available models.");
        }
        
        // Use control plane endpoint for listing models (not runtime endpoint)
        // Override path() behavior temporarily by using the full URL
        String controlPlaneBasePath = "https://bedrock." + region + ".amazonaws.com";
        String fullUrl = controlPlaneBasePath + "/foundation-models";
        
        logger.info("Fetching available Bedrock models from: {}", fullUrl);
        
        // Use the existing execute() infrastructure which handles authentication properly
        // For Bearer Token: sign() method adds Authorization header
        // For IAM auth: we need to sign GET requests with AWS Signature V4
        String authType = authenticationStrategy.getAuthenticationType();
        
        if ("BEARER_TOKEN".equals(authType)) {
            // For Bearer Token, use execute() which calls sign() - this works fine
            GenericRequest request = new GenericRequest(this, fullUrl);
            return execute(request);
        } else {
            // For IAM-based auth, we need to sign GET requests with AWS Signature V4
            // Use the authentication strategy's credentials to sign the request
            return executeSignedGetRequest(fullUrl);
        }
    }
    
    /**
     * Executes a signed GET request for IAM-based authentication.
     * Uses the authentication strategy's credentials to sign with AWS Signature V4.
     */
    private String executeSignedGetRequest(String url) throws IOException {
        Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .header("User-Agent", "DMTools")
                .get();
        
        // Sign the GET request using AWS Signature V4 with credentials from authentication strategy
        Request request = signGetRequestWithAWS4(url, requestBuilder);
        
        logger.debug("GET request starting for URL: {} (auth: {})", url, authenticationStrategy.getAuthenticationType());
        
        try (Response response = getClient().newCall(request).execute()) {
            if (response.isSuccessful()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                
                // Cache the response if caching is enabled (handled by execute() method normally)
                // For this custom GET request, we skip caching to keep it simple
                
                return responseBody;
            } else {
                // Parse AWS error response for better error messages
                String errorBody = response.body() != null ? response.body().string() : "";
                if (response.code() == 403 && errorBody.contains("ListFoundationModels")) {
                    // Provide helpful error message about missing permissions
                    String errorMessage = parseAWSErrorMessage(errorBody);
                    throw new IOException("AWS Bedrock permission denied: " + errorMessage + 
                        "\n\nTo fix this, add the following IAM policy to your IAM user/role:\n" +
                        "{\n" +
                        "  \"Version\": \"2012-10-17\",\n" +
                        "  \"Statement\": [\n" +
                        "    {\n" +
                        "      \"Effect\": \"Allow\",\n" +
                        "      \"Action\": [\n" +
                        "        \"bedrock:ListFoundationModels\"\n" +
                        "      ],\n" +
                        "      \"Resource\": \"*\"\n" +
                        "    }\n" +
                        "  ]\n" +
                        "}");
                }
                throw printAndCreateException(request, response);
            }
        }
    }
    
    /**
     * Signs a GET request using AWS Signature V4 with credentials from the authentication strategy.
     */
    private Request signGetRequestWithAWS4(String url, Request.Builder requestBuilder) throws IOException {
        try {
            AwsCredentials credentials;
            
            // Get credentials based on authentication strategy type
            if (authenticationStrategy instanceof IAMKeysAuthenticationStrategy) {
                // Extract credentials from IAMKeysAuthenticationStrategy using reflection
                IAMKeysAuthenticationStrategy iamStrategy = (IAMKeysAuthenticationStrategy) authenticationStrategy;
                credentials = getCredentialsFromIAMStrategy(iamStrategy);
            } else if (authenticationStrategy instanceof DefaultCredentialsAuthenticationStrategy) {
                // Use DefaultCredentialsProvider
                credentials = DefaultCredentialsProvider.create().resolveCredentials();
            } else {
                // Fallback to DefaultCredentialsProvider
                credentials = DefaultCredentialsProvider.create().resolveCredentials();
            }
            
            // Create AWS SDK HttpFullRequest for signing with GET method
            SdkHttpFullRequest.Builder sdkRequestBuilder = 
                SdkHttpFullRequest.builder()
                    .uri(URI.create(url))
                    .method(SdkHttpMethod.GET);
            
            SdkHttpFullRequest sdkRequest = sdkRequestBuilder.build();
            
            // Create signer
            Aws4Signer signer = Aws4Signer.create();
            
            // Parameters for signing
            Aws4SignerParams signerParams = 
                Aws4SignerParams.builder()
                    .awsCredentials(credentials)
                    .signingName("bedrock")
                    .signingRegion(Region.of(region))
                    .build();
            
            // Sign the request
            SdkHttpFullRequest signedRequest = signer.sign(sdkRequest, signerParams);
            
            // Transfer signed headers to OkHttp Request.Builder
            signedRequest.headers().forEach((name, values) -> {
                if (!values.isEmpty()) {
                    requestBuilder.header(name, values.get(0));
                }
            });
            
            // Add custom headers
            if (customHeaders != null && !customHeaders.isEmpty()) {
                for (Map.Entry<String, String> header : customHeaders.entrySet()) {
                    requestBuilder.header(header.getKey(), header.getValue());
                }
            }
            
            return requestBuilder.get().build();
            
        } catch (Exception e) {
            logger.error("Failed to sign GET request: {}", e.getMessage(), e);
            throw new IOException("Failed to sign GET request: " + e.getMessage(), e);
        }
    }
    
    /**
     * Parses AWS error message from error response body.
     * Extracts the main error message for better user feedback.
     */
    private String parseAWSErrorMessage(String errorBody) {
        try {
            JSONObject errorJson = new JSONObject(errorBody);
            if (errorJson.has("Message")) {
                return errorJson.getString("Message");
            } else if (errorJson.has("message")) {
                return errorJson.getString("message");
            } else if (errorJson.has("error")) {
                return errorJson.getString("error");
            }
        } catch (Exception e) {
            // If parsing fails, return the raw error body (truncated if too long)
            if (errorBody.length() > 500) {
                return errorBody.substring(0, 500) + "...";
            }
            return errorBody;
        }
        return errorBody;
    }
    
    /**
     * Extracts AWS credentials from IAMKeysAuthenticationStrategy using reflection.
     * This is necessary because the strategy doesn't expose getter methods for credentials.
     */
    private AwsCredentials getCredentialsFromIAMStrategy(IAMKeysAuthenticationStrategy strategy) throws Exception {
        try {
            // Use reflection to access private fields
            java.lang.reflect.Field accessKeyIdField = IAMKeysAuthenticationStrategy.class.getDeclaredField("accessKeyId");
            java.lang.reflect.Field secretAccessKeyField = IAMKeysAuthenticationStrategy.class.getDeclaredField("secretAccessKey");
            java.lang.reflect.Field sessionTokenField = IAMKeysAuthenticationStrategy.class.getDeclaredField("sessionToken");
            
            accessKeyIdField.setAccessible(true);
            secretAccessKeyField.setAccessible(true);
            sessionTokenField.setAccessible(true);
            
            String accessKeyId = (String) accessKeyIdField.get(strategy);
            String secretAccessKey = (String) secretAccessKeyField.get(strategy);
            String sessionToken = (String) sessionTokenField.get(strategy);
            
            if (sessionToken != null && !sessionToken.trim().isEmpty()) {
                return AwsSessionCredentials.create(accessKeyId, secretAccessKey, sessionToken);
            } else {
                return AwsBasicCredentials.create(accessKeyId, secretAccessKey);
            }
        } catch (Exception e) {
            logger.error("Failed to extract credentials from IAM strategy: {}", e.getMessage(), e);
            // Fallback to DefaultCredentialsProvider
            return DefaultCredentialsProvider.create().resolveCredentials();
        }
    }

    /**
     * Enum to represent different Bedrock model types
     */
    private enum ModelType {
        CLAUDE,  // Claude Sonnet (Anthropic)
        QWEN,    // Qwen3 Coder
        NOVA,    // Amazon Nova
        MISTRAL  // Mistral models
    }
}
