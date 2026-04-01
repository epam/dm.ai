// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.ai.openai;

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
import java.util.stream.Collectors;

public class OpenAIClient extends AbstractRestClient implements AI {

    private final Logger logger;

    @Getter
    private final String apiKey;

    @Getter
    private final String model;

    @Getter
    private final int maxTokens;

    @Getter
    private final double temperature;

    @Getter
    private final String maxTokensParamName;

    @Setter
    private Metadata metadata;

    @Getter
    @Setter
    private ConversationObserver conversationObserver;

    @Getter
    private final Map<String, String> customHeaders;

    // Default constructor
    public OpenAIClient(String basePath, String apiKey, String model) throws IOException {
        this(basePath, apiKey, model, 4096, -1, "max_completion_tokens", null, null);
    }

    // Constructor with observer
    public OpenAIClient(String basePath, String apiKey, String model, ConversationObserver conversationObserver) throws IOException {
        this(basePath, apiKey, model, 4096, -1, "max_completion_tokens", conversationObserver, null);
    }

    // Full constructor with all parameters
    public OpenAIClient(String basePath, String apiKey, String model, int maxTokens, double temperature, ConversationObserver conversationObserver) throws IOException {
        this(basePath, apiKey, model, maxTokens, temperature, "max_completion_tokens", conversationObserver, null);
    }

    // Constructor with custom headers
    public OpenAIClient(String basePath, String apiKey, String model, int maxTokens, double temperature, ConversationObserver conversationObserver, Map<String, String> customHeaders) throws IOException {
        this(basePath, apiKey, model, maxTokens, temperature, "max_completion_tokens", conversationObserver, customHeaders);
    }

    // Constructor with maxTokensParamName
    public OpenAIClient(String basePath, String apiKey, String model, int maxTokens, double temperature, String maxTokensParamName, ConversationObserver conversationObserver, Map<String, String> customHeaders) throws IOException {
        this(basePath, apiKey, model, maxTokens, temperature, maxTokensParamName, conversationObserver, customHeaders, LogManager.getLogger(OpenAIClient.class));
    }

    // Constructor with logger injection for server-managed mode
    public OpenAIClient(String basePath, String apiKey, String model, int maxTokens, double temperature, String maxTokensParamName, ConversationObserver conversationObserver, Map<String, String> customHeaders, Logger logger) throws IOException {
        super(basePath, null);
        this.apiKey = apiKey;
        this.model = model;
        this.maxTokens = maxTokens;
        this.temperature = temperature;
        this.maxTokensParamName = maxTokensParamName;
        this.conversationObserver = conversationObserver;
        this.customHeaders = customHeaders != null ? new HashMap<>(customHeaders) : null;
        this.logger = logger != null ? logger : LogManager.getLogger(OpenAIClient.class);
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
        builder = builder.header("Authorization", "Bearer " + apiKey);

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
        name = "openai_ai_chat",
        description = "Send a text message to OpenAI and get response",
        integration = "ai"
    )
    public String chat(@MCPParam(name = "message", description = "Text message to send to AI") String message) throws Exception {
        return chat(model, message);
    }

    /**
     * Chat with OpenAI using file attachments
     * @param message The text message to send to OpenAI
     * @param filePaths Array of file paths to attach to the message
     * @return AI response as string
     */
    @MCPTool(
        name = "openai_ai_chat_with_files",
        description = "Send a text message to OpenAI with file attachments. Supports images for vision models (gpt-4-vision-preview, gpt-4-turbo, etc.).",
        integration = "ai"
    )
    public String chatWithFiles(
            @MCPParam(
                name = "message",
                description = "Text message to send to OpenAI",
                example = "What is in this image? Please analyze the content."
            ) String message,
            @MCPParam(
                name = "filePaths",
                description = "Array of file paths to attach to the message (images only for vision models)",
                type = "array",
                example = "['/path/to/image.png', '/path/to/photo.jpg']"
            ) String[] filePaths
    ) throws Exception {
        if (message == null || message.trim().isEmpty()) {
            throw new IllegalArgumentException("Message cannot be null or empty");
        }

        if (filePaths == null || filePaths.length == 0) {
            throw new IllegalArgumentException("File paths array cannot be null or empty");
        }

        // Convert string paths to File objects
        List<File> files = java.util.Arrays.stream(filePaths)
                .map(String::trim)
                .filter(path -> !path.isEmpty())
                .map(File::new)
                .peek(file -> {
                    if (!file.exists()) {
                        logger.warn("File does not exist: {}", file.getAbsolutePath());
                    }
                    if (!file.canRead()) {
                        logger.warn("File is not readable: {}", file.getAbsolutePath());
                    }
                })
                .collect(Collectors.toList());

        if (files.isEmpty()) {
            throw new IllegalArgumentException("No valid files found from provided paths");
        }

        logger.info("OpenAI chat with files: message='{}', files={}",
                message, files.stream().map(File::getName).collect(Collectors.toList()));

        // Call the chat method with files
        return chat(model, message, files);
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
            // OpenAI vision API uses image_url with base64 data
            String extension = ImageUtils.getExtension(imageFile);
            String imageBase64 = ImageUtils.convertToBase64(imageFile, extension);
            String mimeType = ImageUtils.getMimeType(imageFile);

            JSONArray content = new JSONArray()
                    .put(new JSONObject()
                            .put("type", "text")
                            .put("text", message)
                    )
                    .put(new JSONObject()
                            .put("type", "image_url")
                            .put("image_url", new JSONObject()
                                    .put("url", "data:" + mimeType + ";base64," + imageBase64)
                            )
                    );
            messagesArray.put(new JSONObject()
                    .put("role", "user")
                    .put("content", content));
        } else {
            messagesArray.put(new JSONObject()
                    .put("role", "user")
                    .put("content", message));
        }

        return performChatCompletion(model, messagesArray);
    }

    private String performChatCompletion(String model, JSONArray messagesArray) throws Exception {
        String path = getBasePath();
        logger.info(path);
        GenericRequest postRequest = new GenericRequest(this, path);

        JSONObject jsonObject = new JSONObject()
                .put("model", model)
                .put("messages", messagesArray);

        // Add temperature parameter only if it's >= 0 (negative means skip)
        if (temperature >= 0) {
            jsonObject.put("temperature", temperature);
        }

        // Add max tokens parameter if configured (empty string = skip parameter)
        if (maxTokensParamName != null && !maxTokensParamName.trim().isEmpty()) {
            jsonObject.put(maxTokensParamName.trim(), maxTokens);
        }

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

            // OpenAI response format: choices[0].message.content
            JSONArray choices = jsonResponse.optJSONArray("choices");
            if (choices != null && choices.length() > 0) {
                JSONObject firstChoice = choices.getJSONObject(0);
                JSONObject message = firstChoice.optJSONObject("message");
                if (message != null) {
                    Object contentObj = message.opt("content");
                    if (contentObj instanceof String) {
                        content = (String) contentObj;
                    } else {
                        content = "";
                    }
                } else {
                    content = "";
                }
            } else {
                if (response.contains("error")) {
                    logger.error(response);
                    content = response;
                } else {
                    content = "";
                }
            }

            // Log token usage if available
            JSONObject usage = jsonResponse.optJSONObject("usage");
            if (usage != null) {
                logger.debug("Token usage - prompt: {}, completion: {}, total: {}",
                        usage.optInt("prompt_tokens", 0),
                        usage.optInt("completion_tokens", 0),
                        usage.optInt("total_tokens", 0));
            }
        } catch (Exception e) {
            logger.error("Failed to parse OpenAI response: " + e.getMessage(), e);
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

        // Normalize message roles
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
                // Handle multiple files - process all images
                JSONArray contentArray = new JSONArray();

                // Add text first
                if (message.getText() != null && !message.getText().isEmpty()) {
                    contentArray.put(new JSONObject().put("type", "text").put("text", message.getText()));
                }

                // Add all image files
                for (File imageFile : message.getFiles()) {
                    try {
                        String extension = ImageUtils.getExtension(imageFile);
                        String imageBase64 = ImageUtils.convertToBase64(imageFile, extension);
                        String mimeType = ImageUtils.getMimeType(imageFile);

                        contentArray.put(new JSONObject()
                                .put("type", "image_url")
                                .put("image_url", new JSONObject()
                                        .put("url", "data:" + mimeType + ";base64," + imageBase64)
                                ));
                    } catch (IOException e) {
                        logger.warn("Failed to process image file {}: {}", imageFile.getName(), e.getMessage());
                    }
                }

                messageJson.put("content", contentArray);
            } else {
                // Text only
                messageJson.put("content", message.getText());
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
        if (files == null || files.isEmpty()) {
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
}
