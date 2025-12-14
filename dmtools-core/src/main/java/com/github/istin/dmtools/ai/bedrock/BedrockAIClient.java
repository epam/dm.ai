package com.github.istin.dmtools.ai.bedrock;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.ai.ConversationObserver;
import com.github.istin.dmtools.ai.Message;
import com.github.istin.dmtools.ai.model.Metadata;
import com.github.istin.dmtools.common.networking.GenericRequest;
import com.github.istin.dmtools.common.utils.ImageUtils;
import com.github.istin.dmtools.common.utils.RetryUtil;
import com.github.istin.dmtools.mcp.MCPTool;
import com.github.istin.dmtools.mcp.MCPParam;
import com.github.istin.dmtools.networking.AbstractRestClient;
import com.google.gson.Gson;
import lombok.Getter;
import lombok.Setter;
import okhttp3.Request;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    // Constructor with all parameters
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
        // Bedrock endpoint format: /model/{modelId}/invoke
        if (path.startsWith("/model/")) {
            return getBasePath() + path;
        }
        return getBasePath() + "/model/" + modelId + "/invoke";
    }

    @Override
    public Request.Builder sign(Request.Builder builder) {
        builder = builder.header("Content-Type", "application/json");
        
        // Add Bearer token authentication
        if (bearerToken != null && !bearerToken.trim().isEmpty()) {
            builder = builder.header("Authorization", "Bearer " + bearerToken);
        }
        
        // Add custom headers if provided
        if (customHeaders != null && !customHeaders.isEmpty()) {
            for (Map.Entry<String, String> header : customHeaders.entrySet()) {
                builder = builder.header(header.getKey(), header.getValue());
            }
        }
        
        return builder;
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
        if (lowerModelId.contains("claude") || lowerModelId.contains("anthropic")) {
            return ModelType.CLAUDE;
        } else if (lowerModelId.contains("qwen")) {
            return ModelType.QWEN;
        } else if (lowerModelId.contains("nova") || lowerModelId.contains("amazon")) {
            return ModelType.NOVA;
        }
        // Default to Claude for unknown models
        return ModelType.CLAUDE;
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
        logger.info("-------- message to ai --------");
        logger.info(message);
        logger.info("-------- start chat ai --------");
        if (conversationObserver != null) {
            conversationObserver.addMessage(new ConversationObserver.Message("DMTools", message));
        }

        JSONArray messagesArray = new JSONArray();
        ModelType modelType = detectModelType(model);
        
        if (imageFile != null) {
            // Build content array with image and text
            JSONArray contentArray = buildContentArrayWithImage(message, imageFile, modelType);
            messagesArray.put(new JSONObject()
                    .put("role", "user")
                    .put("content", contentArray));
        } else {
            // Text only
            if (modelType == ModelType.CLAUDE || modelType == ModelType.NOVA) {
                // Claude and Nova support both string and array format
                messagesArray.put(new JSONObject()
                        .put("role", "user")
                        .put("content", message));
            } else {
                // Qwen only supports text
                messagesArray.put(new JSONObject()
                        .put("role", "user")
                        .put("content", message));
            }
        }

        return performChatCompletion(model, messagesArray, modelType);
    }

    private JSONArray buildContentArrayWithImage(String message, File imageFile, ModelType modelType) throws IOException {
        JSONArray contentArray = new JSONArray();
        
        // Add text part if message is not null or empty
        if (message != null && !message.trim().isEmpty()) {
            contentArray.put(new JSONObject()
                    .put("type", "text")
                    .put("text", message));
        }
        
        // Add image part
        String extension = ImageUtils.getExtension(imageFile);
        String imageBase64 = ImageUtils.convertToBase64WithoutPrefix(imageFile, extension);
        String mimeType = ImageUtils.getMimeType(imageFile);
        
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
        
        return contentArray;
    }

    private String performChatCompletion(String model, JSONArray messagesArray, ModelType modelType) throws Exception {
        String path = path("/model/" + model + "/invoke");
        logger.info(path);
        GenericRequest postRequest = new GenericRequest(this, path);

        JSONObject jsonObject = buildRequestBody(model, messagesArray, modelType);
        
        if (metadata != null) {
            jsonObject.put("metadata", new JSONObject(new Gson().toJson(metadata)));
        }
        postRequest.setBody(jsonObject.toString());
        return RetryUtil.executeWithRetry(() -> processResponse(model, postRequest, modelType));
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
            // Amazon Nova format
            jsonObject.put("messages", messagesArray);
            jsonObject.put("max_tokens", maxTokens);
            jsonObject.put("temperature", temperature);
        }
        
        return jsonObject;
    }

    private String processResponse(String model, GenericRequest postRequest, ModelType modelType) throws IOException {
        String response = post(postRequest);
        logger.info(response);
        
        String content;
        try {
            JSONObject jsonResponse = new JSONObject(response);
            
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
                // Qwen response format: output.choices[0].message.content
                JSONObject output = jsonResponse.optJSONObject("output");
                if (output != null) {
                    JSONArray choices = output.optJSONArray("choices");
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
                // Nova format (similar to Claude)
                JSONArray contentArray = jsonResponse.optJSONArray("content");
                if (contentArray != null && contentArray.length() > 0) {
                    JSONObject firstContent = contentArray.getJSONObject(0);
                    content = firstContent.optString("text", "");
                } else {
                    content = "";
                }
            }
            
            // Handle error responses
            if (response.contains("error") || response.contains("\"error\"")) {
                logger.error("Error in Bedrock response: {}", response);
                content = response;
            }
            
            // Return empty string if no content and no error
            if (content == null || content.isEmpty()) {
                if (!response.contains("error") && !response.contains("\"error\"")) {
                    content = "";
                }
            }
        } catch (Exception e) {
            logger.error("Failed to parse Bedrock response: " + e.getMessage(), e);
            if (response.contains("error")) {
                content = response;
            } else {
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
                // Handle multiple files - process all images
                JSONArray contentArray = new JSONArray();
                
                // Add text part if text is not null or empty
                if (message.getText() != null && !message.getText().isEmpty()) {
                    contentArray.put(new JSONObject().put("type", "text").put("text", message.getText()));
                }
                
                // Add all image files
                for (File imageFile : message.getFiles()) {
                    try {
                        String extension = ImageUtils.getExtension(imageFile);
                        String imageBase64 = ImageUtils.convertToBase64WithoutPrefix(imageFile, extension);
                        String mimeType = ImageUtils.getMimeType(imageFile);
                        
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
                
                messageJson.put("content", contentArray);
            } else {
                // Text only
                if (modelType == ModelType.CLAUDE || modelType == ModelType.NOVA) {
                    // Claude and Nova support both string and array format
                    messageJson.put("content", message.getText());
                } else {
                    // Qwen only supports text
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
        
        // For multiple files, create a message with all files
        Message userMessage = new Message("user", message, files);
        return chat(model, userMessage);
    }

    public String chat(String model, String message) throws Exception {
        return chat(model, message, (File) null);
    }

    @Override
    protected @NotNull String buildHashForPostRequest(GenericRequest genericRequest, String url) {
        return url + genericRequest.getBody();
    }

    /**
     * Enum to represent different Bedrock model types
     */
    private enum ModelType {
        CLAUDE,  // Claude Sonnet (Anthropic)
        QWEN,    // Qwen3 Coder
        NOVA     // Amazon Nova
    }
}
