package com.github.istin.dmtools.ai.anthropic;

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

public class AnthropicAIClient extends AbstractRestClient implements AI {

    private final Logger logger;

    @Getter
    private final String model;

    @Getter
    private final int maxTokens;

    @Setter
    private Metadata metadata;

    @Getter
    @Setter
    private ConversationObserver conversationObserver;

    @Getter
    private final Map<String, String> customHeaders;

    // Default constructor - backward compatibility
    public AnthropicAIClient(String basePath, String model) throws IOException {
        this(basePath, model, 4096, null, null);
    }

    // Default constructor with observer - backward compatibility
    public AnthropicAIClient(String basePath, String model, ConversationObserver conversationObserver) throws IOException {
        this(basePath, model, 4096, conversationObserver, null);
    }

    // Full constructor with all parameters
    public AnthropicAIClient(String basePath, String model, int maxTokens, ConversationObserver conversationObserver) throws IOException {
        this(basePath, model, maxTokens, conversationObserver, null);
    }
    
    // Constructor with custom headers
    public AnthropicAIClient(String basePath, String model, int maxTokens, ConversationObserver conversationObserver, Map<String, String> customHeaders) throws IOException {
        this(basePath, model, maxTokens, conversationObserver, customHeaders, LogManager.getLogger(AnthropicAIClient.class));
    }
    
    // Constructor with logger injection for server-managed mode
    public AnthropicAIClient(String basePath, String model, int maxTokens, ConversationObserver conversationObserver, Map<String, String> customHeaders, Logger logger) throws IOException {
        super(basePath, null);
        this.model = model;
        this.maxTokens = maxTokens;
        this.conversationObserver = conversationObserver;
        this.customHeaders = customHeaders != null ? new HashMap<>(customHeaders) : null;
        this.logger = logger != null ? logger : LogManager.getLogger(AnthropicAIClient.class);
        setCachePostRequestsEnabled(true);
    }

    public String getName() {
        return model;
    }

    @Override
    public String roleName() {
        return "assistant";
    }

    @Override
    public String path(String path) {
        return getBasePath() + path;
    }

    @Override
    public Request.Builder sign(Request.Builder builder) {
        builder = builder.header("Content-Type", "application/json");
        
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

    @Override
    @MCPTool(
        name = "anthropic_ai_chat",
        description = "Send a text message to Anthropic Claude AI and get response",
        integration = "ai"
    )
    public String chat(@MCPParam(name = "message", description = "Text message to send to AI") String message) throws Exception {
        return chat(model, message);
    }

    @Override
    public String chat(String model, String message, File imageFile) throws Exception {
        if (model == null) {
            model = this.model;
        }
        logger.info("-------- message to ai --------");
        logger.info(message);
        logger.info("-------- start chat ai --------");
        if (conversationObserver != null) {
            conversationObserver.addMessage(new ConversationObserver.Message("DMTools", message));
        }

        JSONArray messagesArray = new JSONArray();
        if (imageFile != null) {
            String extension = ImageUtils.getExtension(imageFile);
            String imageBase64 = ImageUtils.convertToBase64(imageFile, extension);
            String mediaType = "image/" + extension;
            
            JSONArray content = new JSONArray()
                    .put(new JSONObject()
                            .put("type", "text")
                            .put("text", message)
                    )
                    .put(new JSONObject()
                            .put("type", "image")
                            .put("source", new JSONObject()
                                    .put("type", "base64")
                                    .put("filename", imageFile.getName())
                                    .put("media_type", mediaType)
                                    .put("data", imageBase64)
                            )
                    );
            messagesArray.put(new JSONObject()
                    .put("role", "user")
                    .put("content", content));
        } else {
            messagesArray.put(new JSONObject()
                    .put("role", "user")
                    .put("content", new JSONArray()
                            .put(new JSONObject()
                                    .put("type", "text")
                                    .put("text", message)
                            )
                    ));
        }

        return performChatCompletion(model, messagesArray);
    }

    private String performChatCompletion(String model, JSONArray messagesArray) throws Exception {
        String path = getBasePath();
        logger.info(path);
        GenericRequest postRequest = new GenericRequest(this, path);

        JSONObject jsonObject = new JSONObject()
                .put("model", model)
                .put("max_tokens", maxTokens)
                .put("messages", messagesArray);
        
        if (metadata != null) {
            jsonObject.put("metadata", new JSONObject(new Gson().toJson(metadata)));
        }
        postRequest.setBody(jsonObject.toString());
        return RetryUtil.executeWithRetry(() -> processResponse(model, postRequest));
    }

    private String processResponse(String model, GenericRequest postRequest) throws IOException {
        String response = post(postRequest);
        logger.info(response);
        
        String content;
        try {
            JSONObject jsonResponse = new JSONObject(response);
            
            // Try OpenAI-compatible format first (used by AWS Bedrock and some Anthropic endpoints)
            JSONArray choices = jsonResponse.optJSONArray("choices");
            if (choices != null && choices.length() > 0) {
                JSONObject firstChoice = choices.getJSONObject(0);
                JSONObject message = firstChoice.optJSONObject("message");
                if (message != null) {
                    Object contentObj = message.opt("content");
                    if (contentObj instanceof String) {
                        content = (String) contentObj;
                    } else if (contentObj instanceof JSONArray) {
                        // Native Anthropic format with content array
                        JSONArray contentArray = (JSONArray) contentObj;
                        if (contentArray.length() > 0) {
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
            } else {
                // Try native Anthropic format
                JSONArray contentArray = jsonResponse.optJSONArray("content");
                if (contentArray != null && contentArray.length() > 0) {
                    JSONObject firstContent = contentArray.getJSONObject(0);
                    content = firstContent.optString("text", "");
                } else {
                    if (response.contains("error")) {
                        logger.error(response);
                        content = response;
                    } else {
                        content = "";
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Failed to parse Anthropic response: " + e.getMessage(), e);
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
            model = this.model;
        }

        // Normalize message roles to ensure compatibility with this AI provider
        Message[] normalizedMessages = normalizeMessageRoles(messages);

        logger.info("-------- start chat ai with messages --------");
        JSONArray messagesArray = new JSONArray();
        for (Message message : normalizedMessages) {
            if (conversationObserver != null) {
                conversationObserver.addMessage(new ConversationObserver.Message(message.getRole(), message.getText()));
            }
            logger.info("Processing message from role: {} with text: {}", message.getRole(), message.getText());

            JSONObject messageJson = new JSONObject();
            messageJson.put("role", message.getRole());

            if (message.getFiles() != null && !message.getFiles().isEmpty()) {
                File imageFile = message.getFiles().getFirst();
                String extension = ImageUtils.getExtension(imageFile);
                String imageBase64 = ImageUtils.convertToBase64(imageFile, extension);
                String mediaType = "image/" + extension;

                JSONArray contentArray = new JSONArray();
                // Add text part if text is not null or empty
                if (message.getText() != null && !message.getText().isEmpty()) {
                    contentArray.put(new JSONObject().put("type", "text").put("text", message.getText()));
                }
                // Add image part with Anthropic format
                contentArray.put(new JSONObject()
                        .put("type", "image")
                        .put("source", new JSONObject()
                                .put("type", "base64")
                                .put("filename", imageFile.getName())
                                .put("media_type", mediaType)
                                .put("data", imageBase64)
                        ));
                messageJson.put("content", contentArray);
            } else {
                messageJson.put("content", new JSONArray()
                        .put(new JSONObject()
                                .put("type", "text")
                                .put("text", message.getText())
                        ));
            }
            messagesArray.put(messageJson);
        }
        logger.info("-------- end chat ai with messages processing --------");

        return performChatCompletion(model, messagesArray);
    }

    @Override
    public String chat(Message... messages) throws Exception {
        return chat(this.model, messages);
    }

    @Override
    public String chat(String model, String message, List<File> files) throws Exception {
        return chat(model, message, files != null && !files.isEmpty() ? files.getFirst() : null);
    }

    public String chat(String model, String message) throws Exception {
        return chat(model, message, (File) null);
    }

    @Override
    protected @NotNull String buildHashForPostRequest(GenericRequest genericRequest, String url) {
        return url + genericRequest.getBody();
    }

}

