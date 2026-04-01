// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.ai.dial;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.ai.AIFileFilter;
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
import okhttp3.HttpUrl;
import okhttp3.Request;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class DialAIClient extends AbstractRestClient implements AI {

    private final Logger logger;  // Changed from static to instance member

    /**
     * -- GETTER --
     *  Gets the model name used by this client
     *
     * @return The model name
     */
    @Getter
    private final String model;

    @Getter
    private final String apiVersion;

    @Setter
    private Metadata metadata;

    @Getter
    @Setter
    private ConversationObserver conversationObserver;

    // Default constructor - backward compatibility
    public DialAIClient(String basePath, String authorization, String model) throws IOException {
        this(basePath, authorization, model, null, null);
    }

    // Default constructor with observer - backward compatibility
    public DialAIClient(String basePath, String authorization, String model, ConversationObserver conversationObserver) throws IOException {
        this(basePath, authorization, model, null, conversationObserver, LogManager.getLogger(DialAIClient.class));
    }
    
    // Constructor with apiVersion - backward compatibility
    public DialAIClient(String basePath, String authorization, String model, String apiVersion) throws IOException {
        this(basePath, authorization, model, apiVersion, null, LogManager.getLogger(DialAIClient.class));
    }
    
    // Constructor with apiVersion and observer
    public DialAIClient(String basePath, String authorization, String model, String apiVersion, ConversationObserver conversationObserver) throws IOException {
        this(basePath, authorization, model, apiVersion, conversationObserver, LogManager.getLogger(DialAIClient.class));
    }

    // NEW: Constructor with logger injection for server-managed mode
    public DialAIClient(String basePath, String authorization, String model, String apiVersion, ConversationObserver conversationObserver, Logger logger) throws IOException {
        super(basePath, authorization);
        this.model = model;
        this.apiVersion = apiVersion;
        this.conversationObserver = conversationObserver;
        this.logger = logger != null ? logger : LogManager.getLogger(DialAIClient.class);
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
        return getBasePath() + path;// + "?api-version=" + API_VERSION;
    }

    @Override
    public Request.Builder sign(Request.Builder builder) {
        return builder
                .header("api-key", authorization)
                .header("Content-Type", "application/json");
    }

    @Override
    public int getTimeout() {
        return 1400;
    }

    @Override
    @MCPTool(
        name = "dial_ai_chat",
        description = "Send a text message to Dial AI and get response",
        integration = "ai"
    )

    public String chat(@MCPParam(name = "message", description = "Text message to send to AI") String message) throws Exception {
        return chat(this.model, message);
    }

    @MCPTool(
        name = "dial_ai_chat_with_files",
        description = "Send a text message to Dial AI with file attachments. Supports images and documents for analysis and questions.",
        integration = "ai"
    )
    public String chatWithFiles(
            @MCPParam(
                name = "message",
                description = "Text message to send to Dial AI",
                example = "What is in this image? Please analyze the document content."
            ) String message,
            @MCPParam(
                name = "filePaths",
                description = "Comma-separated list of file paths to attach (supports images and documents)",
                example = "/path/to/image.png,/path/to/document.pdf"
            ) String filePaths
    ) throws Exception {
        // Parse file paths and create File objects
        String[] paths = filePaths.split(",");
        List<File> files = new java.util.ArrayList<>();
        for (String path : paths) {
            path = path.trim();
            File file = new File(path);
            if (!file.exists()) {
                throw new IllegalArgumentException("File not found: " + path);
            }
            files.add(file);
        }

        // Use existing chat method with files
        return chat(this.model, message, files);
    }

    @Override
    public String chat(String executionModel, String message, File imageFile) throws Exception {
        return chat(executionModel, message, imageFile, (JSONObject) null);
    }

    @Override
    public String chat(String executionModel, String message, File imageFile, JSONObject agentContext) throws Exception {
        if (executionModel == null) {
            executionModel = this.model;
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

        return performChatCompletion(executionModel, messagesArray, agentContext);
    }

    private String performChatCompletion(String executionModel, JSONArray messagesArray, JSONObject agentContext) throws Exception {
        String basePath = path("openai/deployments/" + executionModel + "/chat/completions");
        String path = basePath;
        
        // Append api-version query parameter if configured
        if (apiVersion != null && !apiVersion.trim().isEmpty()) {
            HttpUrl httpUrl = HttpUrl.parse(basePath);
            if (httpUrl != null) {
                HttpUrl urlWithApiVersion = httpUrl.newBuilder()
                        .addQueryParameter("api-version", apiVersion)
                        .build();
                path = urlWithApiVersion.toString();
            }
        }
        
        logger.info(path);
        GenericRequest postRequest = new GenericRequest(this, path);

        JSONObject jsonObject = buildParams(executionModel, messagesArray, agentContext);
        if (metadata != null) {
            jsonObject.put("metadata", new JSONObject(new Gson().toJson(metadata)));
        }
        postRequest.setBody(jsonObject.toString());
        return RetryUtil.executeWithRetry(() -> processResponse(executionModel, postRequest));
    }

    private JSONObject buildParams(String executionModel, JSONArray messagesArray, JSONObject agentContext) {
        JSONObject jsonObjectParams = new JSONObject();
        if (executionModel.toLowerCase().contains("gpt-5")) {
            jsonObjectParams
                .put("max_completion_tokens", 65536)
                    //.put("reasoning_effort", "none")
            ;
        } else {
            jsonObjectParams
                    .put("temperature", 0.1)
                    .put("max_tokens", 65536)
                    //.put("reasoning_effort", "none")
                    ;
        }
        AI.AgentParams.apply(jsonObjectParams, agentContext);
        logger.info("-------- chat ai params --------");
        logger.info(jsonObjectParams);
        logger.info("-------- end chat ai params --------");
        jsonObjectParams.put("messages", messagesArray);
        return jsonObjectParams;
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
    public String chat(String executionModel, Message... messages) throws Exception {
        return chat(executionModel, null, messages);
    }

    @Override
    public String chat(String executionModel, JSONObject agentContext, Message... messages) throws Exception {
        if (executionModel == null) {
            executionModel = this.model;
        }

        // Normalize message roles to ensure compatibility with this AI provider
        Message[] normalizedMessages = normalizeMessageRoles(messages);

        logger.info("-------- start chat ai with messages --------");
        JSONArray messagesArray = new JSONArray();
        for (Message message : normalizedMessages) {
            if (conversationObserver != null) {
                // Assuming ConversationObserver.Message can be created from com.github.istin.dmtools.ai.Message
                // If not, this part might need adjustment or a new constructor/method in ConversationObserver.Message
                conversationObserver.addMessage(new ConversationObserver.Message(message.getRole(), message.getText()));
            }
            logger.info("Processing message from role: {} with text: {}", message.getRole(), message.getText());

            JSONObject messageJson = new JSONObject();
            messageJson.put("role", message.getRole());

            if (message.getFiles() != null && !message.getFiles().isEmpty()) {
                // Assuming we process only the first file as an image, similar to the existing chat method
                File imageFile = message.getFiles().get(0);
                String extension = ImageUtils.getExtension(imageFile);
                String imageBase64 = ImageUtils.convertToBase64(imageFile, "png"); // Assuming png, or derive from extension

                JSONArray contentArray = new JSONArray();
                // Add text part if text is not null or empty
                if (message.getText() != null && !message.getText().isEmpty()) {
                    contentArray.put(new JSONObject().put("type", "text").put("text", message.getText()));
                }
                // Add image part
                contentArray.put(new JSONObject()
                        .put("type", "image_url")
                        .put("image_url", new JSONObject()
                                .put("url", "data:image/" + extension + ";base64," + imageBase64)));
                messageJson.put("content", contentArray);
            } else {
                messageJson.put("content", message.getText());
            }
            messagesArray.put(messageJson);
        }
        logger.info("-------- end chat ai with messages processing --------");

        return performChatCompletion(executionModel, messagesArray, agentContext);
    }

    @Override
    public String chat(Message... messages) throws Exception {
        return chat((JSONObject) null, messages);
    }
    @Override
    public String chat(JSONObject agentContext, Message... messages) throws Exception {
        return chat(this.model, agentContext, messages);
    }

    @Override
    public String chat(String model, String message, List<File> files) throws Exception {
        return chat(model, message, files, (JSONObject) null);
    }

    @Override
    public String chat(String model, String message, List<File> files, JSONObject agentContext) throws Exception {
        return chat(model, message, files != null && !files.isEmpty() ? files.get(0) : null, agentContext);
    }

    @Override
    public String chat(String model, String message) throws Exception {
        return chat(model, message, (JSONObject) null);
    }
    @Override
    public String chat(String model, String message, JSONObject agentContext) throws Exception {
        return chat(model, message, (File) null, agentContext);
    }

    @Override
    protected @NotNull String buildHashForPostRequest(GenericRequest genericRequest, String url) {
        return url + genericRequest.getBody();
    }

}
