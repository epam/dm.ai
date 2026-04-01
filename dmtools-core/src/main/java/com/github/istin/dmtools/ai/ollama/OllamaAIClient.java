// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.ai.ollama;

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
import com.github.istin.dmtools.ai.dial.model.model.AIResponse;
import com.github.istin.dmtools.ai.dial.model.model.Choice;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class OllamaAIClient extends AbstractRestClient implements AI {

    private final Logger logger;

    /**
     * -- GETTER --
     *  Gets the model name used by this client
     *
     * @return The model name
     */
    @Getter
    private final String model;

    @Getter
    private final int numCtx;

    @Getter
    private final int numPredict;

    @Setter
    private Metadata metadata;

    @Getter
    @Setter
    private ConversationObserver conversationObserver;

    @Getter
    private final Map<String, String> customHeaders;

    @Getter
    private final String apiKey;

    // Default constructor - backward compatibility
    public OllamaAIClient(String basePath, String model) throws IOException {
        this(basePath, model, null, 16384, -1, null, null, LogManager.getLogger(OllamaAIClient.class));
    }

    // Default constructor with observer - backward compatibility
    public OllamaAIClient(String basePath, String model, ConversationObserver conversationObserver) throws IOException {
        this(basePath, model, null, 16384, -1, conversationObserver, null, LogManager.getLogger(OllamaAIClient.class));
    }

    // Full constructor with all parameters
    public OllamaAIClient(String basePath, String model, int numCtx, int numPredict, ConversationObserver conversationObserver) throws IOException {
        this(basePath, model, null, numCtx, numPredict, conversationObserver, null, LogManager.getLogger(OllamaAIClient.class));
    }

    // Constructor with custom headers
    public OllamaAIClient(String basePath, String model, int numCtx, int numPredict, ConversationObserver conversationObserver, Map<String, String> customHeaders) throws IOException {
        this(basePath, model, null, numCtx, numPredict, conversationObserver, customHeaders, LogManager.getLogger(OllamaAIClient.class));
    }

    // Constructor with API key
    public OllamaAIClient(String basePath, String model, String apiKey) throws IOException {
        this(basePath, model, apiKey, 16384, -1, null, null, LogManager.getLogger(OllamaAIClient.class));
    }

    // Constructor with API key and observer
    public OllamaAIClient(String basePath, String model, ConversationObserver conversationObserver, String apiKey) throws IOException {
        this(basePath, model, apiKey, 16384, -1, conversationObserver, null, LogManager.getLogger(OllamaAIClient.class));
    }

    // Constructor with API key, custom headers and observer
    public OllamaAIClient(String basePath, String model, String apiKey, int numCtx, int numPredict, ConversationObserver conversationObserver, Map<String, String> customHeaders) throws IOException {
        this(basePath, model, apiKey, numCtx, numPredict, conversationObserver, customHeaders, LogManager.getLogger(OllamaAIClient.class));
    }

    // Constructor with API key, logger injection for server-managed mode
    public OllamaAIClient(String basePath, String model, String apiKey, int numCtx, int numPredict, ConversationObserver conversationObserver, Map<String, String> customHeaders, Logger logger) throws IOException {
        super(basePath, null);
        this.model = model;
        this.numCtx = numCtx;
        this.numPredict = numPredict;
        this.conversationObserver = conversationObserver;
        this.customHeaders = customHeaders != null ? new HashMap<>(customHeaders) : null;
        this.apiKey = apiKey;
        this.logger = logger != null ? logger : LogManager.getLogger(OllamaAIClient.class);
        setCachePostRequestsEnabled(true);
    }

    // Constructor with logger injection for backward compatibility (without API key)
    public OllamaAIClient(String basePath, String model, int numCtx, int numPredict, ConversationObserver conversationObserver, Map<String, String> customHeaders, Logger logger) throws IOException {
        super(basePath, null);
        this.model = model;
        this.numCtx = numCtx;
        this.numPredict = numPredict;
        this.conversationObserver = conversationObserver;
        this.customHeaders = customHeaders != null ? new HashMap<>(customHeaders) : null;
        this.apiKey = null;
        this.logger = logger != null ? logger : LogManager.getLogger(OllamaAIClient.class);
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

        // Add API key authentication if provided
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            builder = builder.header("Authorization", "Bearer " + apiKey);
            logger.debug("Using API key authentication for Ollama request");
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

    @Override
    @MCPTool(
        name = "ollama_ai_chat",
        description = "Send a text message to Ollama AI and get response",
        integration = "ai"
    )
    public String chat(@MCPParam(name = "message", description = "Text message to send to AI") String message) throws Exception {
        return chat(model, message);
    }

    /**
     * Chat with Ollama AI using file attachments
     * @param message The text message to send to Ollama
     * @param filePaths Array of file paths to attach to the message
     * @return AI response as string
     */
    @MCPTool(
        name = "ollama_ai_chat_with_files",
        description = "Send a text message to Ollama AI with file attachments. Supports images and other file types for analysis and questions.",
        integration = "ai"
    )
    public String chatWithFiles(
            @MCPParam(
                name = "message",
                description = "Text message to send to Ollama AI",
                example = "What is in this image? Please analyze the document content."
            ) String message,
            @MCPParam(
                name = "filePaths",
                description = "Array of file paths to attach to the message",
                type = "array",
                example = "['/path/to/image.png', '/path/to/document.pdf']"
            ) String[] filePaths
    ) throws Exception {
        if (message == null || message.trim().isEmpty()) {
            throw new IllegalArgumentException("Message cannot be null or empty");
        }

        if (filePaths == null || filePaths.length == 0) {
            throw new IllegalArgumentException("File paths array cannot be null or empty");
        }

        // Convert string paths to File objects
        List<File> files = Arrays.stream(filePaths)
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

        logger.info("Ollama AI chat with files: message='{}', files={}",
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
            String extension = ImageUtils.getExtension(imageFile);
            String imageBase64 = ImageUtils.convertToBase64(imageFile, "png");
            JSONArray content = new JSONArray()
                    .put(new JSONObject()
                            .put("type", "text")
                            .put("text", message)
                    )
                    .put(new JSONObject()
                            .put("type", "image_url")
                            .put("image_url", new JSONObject()
                                    .put("url", "data:image/" + extension + ";base64," + imageBase64))
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
        String path = path("/v1/chat/completions");
        logger.info(path);
        GenericRequest postRequest = new GenericRequest(this, path);

        JSONObject jsonObject = new JSONObject()
                .put("model", model)
                .put("temperature", 0.1)
                .put("messages", messagesArray);
        
        // Add max_tokens if numPredict is not -1
        if (numPredict != -1) {
            jsonObject.put("max_tokens", numPredict);
        }
        
        // Add options with num_ctx
        JSONObject options = new JSONObject()
                .put("num_ctx", numCtx);
        jsonObject.put("options", options);
        
        if (metadata != null) {
            jsonObject.put("metadata", new JSONObject(new Gson().toJson(metadata)));
        }
        postRequest.setBody(jsonObject.toString());
        return RetryUtil.executeWithRetry(() -> processResponse(model, postRequest));
    }

    private String processResponse(String model, GenericRequest postRequest) throws IOException {
        String response = post(postRequest);
        logger.info(response);
        List<Choice> choices = new AIResponse(response).getChoices();
        String content;
        if (choices.isEmpty()) {
            if (response.contains("error")) {
                logger.error(response);
                content = response;
            } else {
                content = "";
            }
        } else {
            content = choices.get(0).getMessage().getContent();
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
                // Assuming we process only the first file as an image, similar to the existing chat method
                File imageFile = message.getFiles().get(0);
                String extension = ImageUtils.getExtension(imageFile);
                String imageBase64 = ImageUtils.convertToBase64(imageFile, "png");

                JSONArray contentArray = new JSONArray();
                // Add text part if text is not null or empty
                if (message.getText() != null && !message.getText().isEmpty()) {
                    contentArray.put(new JSONObject().put("type", "text").put("text", message.getText()));
                }
                // Add image part
                contentArray.put(
                        new JSONObject()
                        .put("type", "image_url")
                        .put("image_url", new JSONObject()
                                .put("url", "data:image/" + extension + ";base64," + imageBase64))
                );
                messageJson.put("content", contentArray);
            } else {
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
        return chat(model, message, files != null && !files.isEmpty() ? files.get(0) : null);
    }

    public String chat(String model, String message) throws Exception {
        return chat(model, message, (File) null);
    }

    @Override
    protected @NotNull String buildHashForPostRequest(GenericRequest genericRequest, String url) {
        return url + genericRequest.getBody();
    }

}