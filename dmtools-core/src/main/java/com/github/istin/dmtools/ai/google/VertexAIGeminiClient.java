package com.github.istin.dmtools.ai.google;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.ai.ConversationObserver;
import com.github.istin.dmtools.ai.Message;
import com.github.istin.dmtools.ai.model.Metadata;
import com.github.istin.dmtools.ai.google.auth.GeminiAuthenticationStrategy;
import com.github.istin.dmtools.ai.google.auth.ServiceAccountAuthenticationStrategy;
import com.github.istin.dmtools.common.networking.GenericRequest;
import com.github.istin.dmtools.mcp.MCPParam;
import com.github.istin.dmtools.mcp.MCPTool;
import com.github.istin.dmtools.networking.AbstractRestClient;
import lombok.Getter;
import lombok.Setter;
import okhttp3.Request;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Vertex AI Gemini client for Java that supports both API key and Google Cloud service account authentication.
 * Uses OAuth2 Bearer token authentication for Vertex AI endpoints.
 *
 * Endpoint format: https://{region}-aiplatform.googleapis.com/{apiVersion}/projects/{project}/locations/{location}/publishers/google/models/{model}:generateContent
 * API version can be "v1" (default) or "v1beta1" (required for some models and global location)
 */
public class VertexAIGeminiClient extends AbstractRestClient implements AI {

    private static final Logger logger = LogManager.getLogger(VertexAIGeminiClient.class);

    @Getter
    private final String projectId;

    @Getter
    private final String location;

    @Getter
    private final String model;

    @Getter
    private final String apiVersion;

    @Getter
    @Setter
    private ConversationObserver conversationObserver;

    @Getter
    private final Map<String, String> customHeaders;

    @Getter
    private final GeminiAuthenticationStrategy authenticationStrategy;

    @Setter
    private Metadata metadata;

    /**
     * Constructor with Service Account credentials from file.
     *
     * @param projectId GCP project ID
     * @param location GCP region (e.g., "us-central1")
     * @param model Model name (e.g., "gemini-2.0-flash-exp")
     * @param credentialsPath Path to service account JSON file
     * @param observer Conversation observer
     * @param customHeaders Custom headers to add to requests
     * @param apiVersion API version (e.g., "v1" or "v1beta1")
     * @throws IOException if credentials cannot be loaded
     */
    public VertexAIGeminiClient(String projectId, String location, String model, String credentialsPath,
                               ConversationObserver observer, Map<String, String> customHeaders, String apiVersion) throws IOException {
        super(buildBasePath(location), null);

        if (projectId == null || projectId.trim().isEmpty()) {
            throw new IllegalArgumentException("Project ID cannot be null or empty");
        }
        if (location == null || location.trim().isEmpty()) {
            throw new IllegalArgumentException("Location cannot be null or empty");
        }
        if (model == null || model.trim().isEmpty()) {
            throw new IllegalArgumentException("Model cannot be null or empty");
        }

        this.projectId = projectId;
        this.location = location;
        this.model = model;
        this.apiVersion = (apiVersion != null && !apiVersion.trim().isEmpty()) ? apiVersion : "v1";
        this.conversationObserver = observer;
        this.customHeaders = customHeaders != null ? new HashMap<>(customHeaders) : null;
        this.authenticationStrategy = new ServiceAccountAuthenticationStrategy(credentialsPath);

        logger.info("VertexAIGeminiClient initialized with SERVICE_ACCOUNT authentication (from file)");
        logger.info("Project: {}, Location: {}, Model: {}, API Version: {}", projectId, location, model, this.apiVersion);
        setCachePostRequestsEnabled(true);
    }

    /**
     * Constructor with Service Account credentials from JSON string.
     *
     * @param projectId GCP project ID
     * @param location GCP region (e.g., "us-central1")
     * @param model Model name (e.g., "gemini-2.0-flash-exp")
     * @param observer Conversation observer
     * @param credentialsJson JSON string containing service account credentials
     * @param customHeaders Custom headers to add to requests
     * @param apiVersion API version (e.g., "v1" or "v1beta1")
     * @throws IOException if credentials cannot be parsed
     */
    public VertexAIGeminiClient(String projectId, String location, String model,
                               ConversationObserver observer, String credentialsJson,
                               Map<String, String> customHeaders, String apiVersion) throws IOException {
        super(buildBasePath(location), null);

        if (projectId == null || projectId.trim().isEmpty()) {
            throw new IllegalArgumentException("Project ID cannot be null or empty");
        }
        if (location == null || location.trim().isEmpty()) {
            throw new IllegalArgumentException("Location cannot be null or empty");
        }
        if (model == null || model.trim().isEmpty()) {
            throw new IllegalArgumentException("Model cannot be null or empty");
        }

        this.projectId = projectId;
        this.location = location;
        this.model = model;
        this.apiVersion = (apiVersion != null && !apiVersion.trim().isEmpty()) ? apiVersion : "v1";
        this.conversationObserver = observer;
        this.customHeaders = customHeaders != null ? new HashMap<>(customHeaders) : null;
        this.authenticationStrategy = new ServiceAccountAuthenticationStrategy(credentialsJson, true);

        logger.info("VertexAIGeminiClient initialized with SERVICE_ACCOUNT authentication (from JSON)");
        logger.info("Project: {}, Location: {}, Model: {}, API Version: {}", projectId, location, model, this.apiVersion);
        setCachePostRequestsEnabled(true);
    }

    /**
     * Builds the Vertex AI base path from the region.
     * For "global" location, uses aiplatform.googleapis.com without region prefix.
     * For regional locations, uses {region}-aiplatform.googleapis.com.
     *
     * @param location GCP region (e.g., "us-central1", "europe-west4", "global")
     * @return Base path URL
     */
    private static String buildBasePath(String location) {
        // Special case: "global" location doesn't use region prefix
        if ("global".equalsIgnoreCase(location)) {
            return "https://aiplatform.googleapis.com";
        }
        // Regional locations use region prefix
        return String.format("https://%s-aiplatform.googleapis.com", location);
    }

    /**
     * Builds the complete endpoint URL for generateContent.
     *
     * @param modelName The model to use
     * @return Complete endpoint URL
     */
    private String buildEndpointUrl(String modelName) {
        return String.format("%s/%s/projects/%s/locations/%s/publishers/google/models/%s:generateContent",
                basePath, apiVersion, projectId, location, modelName);
    }

    @Override
    public void setMetadata(Metadata metadata) {
        this.metadata = metadata;
    }

    @Override
    public Request.Builder sign(Request.Builder builder) {
        // Use authentication strategy to sign the request
        return authenticationStrategy.sign(builder, customHeaders);
    }

    @Override
    public String path(String path) {
        // For Vertex AI Gemini, paths are constructed differently
        // Return the base path + provided path
        if (path == null || path.isEmpty()) {
            return buildEndpointUrl(this.model);
        }
        return getBasePath() + path;
    }

    @Override
    @MCPTool(
        name = "vertex_ai_gemini_chat",
        description = "Send a text message to Google Vertex AI Gemini (service account auth)",
        integration = "ai",
        category = "Vertex AI"
    )
    public String chat(@MCPParam(name = "message", description = "Text message") String message) throws Exception {
        return chat(this.model, message);
    }

    @Override
    public String chat(String model, String message) throws Exception {
        return chat(model, message, (File) null);
    }

    @Override
    public String chat(String model, String message, File imageFile) throws Exception {
        if (imageFile != null) {
            return chat(model, message, Collections.singletonList(imageFile));
        }
        return chat(model, message, (List<File>) null);
    }

    /**
     * MCP tool: Chat with files using default model
     */
    @MCPTool(
        name = "vertex_ai_gemini_chat_with_files",
        description = "Send message with file attachments to Vertex AI Gemini. Supports images, documents, and other file types.",
        integration = "ai",
        category = "Vertex AI"
    )
    public String chatWithFiles(
            @MCPParam(
                name = "message",
                description = "Text message to send to Vertex AI Gemini",
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

        // Convert string paths to File objects, filtering out invalid files
        List<File> files = java.util.Arrays.stream(filePaths)
                .map(String::trim)
                .filter(path -> !path.isEmpty())
                .map(File::new)
                .filter(file -> {
                    if (!file.exists()) {
                        logger.warn("File does not exist, skipping: {}", file.getAbsolutePath());
                        return false;
                    }
                    if (!file.canRead()) {
                        logger.warn("File is not readable, skipping: {}", file.getAbsolutePath());
                        return false;
                    }
                    return true;
                })
                .collect(java.util.stream.Collectors.toList());

        // Validate that at least some content exists (message or valid files)
        if ((message == null || message.trim().isEmpty()) && files.isEmpty()) {
            throw new IllegalArgumentException("No valid content provided: message is empty and all files were invalid or inaccessible");
        }

        return chat(this.model, message, files);
    }

    @Override
    public String chat(String model, String message, List<File> files) throws Exception {

        String modelToUse = (model == null || model.trim().isEmpty()) ? this.model : model;

        // Build request JSON
        JSONObject requestJson = new JSONObject();
        JSONArray contentsArray = new JSONArray();

        // Build content with text and files
        JSONObject contentObject = new JSONObject();
        contentObject.put("role", "user");

        JSONArray partsArray = buildContentArrayWithFiles(message, files);
        contentObject.put("parts", partsArray);

        contentsArray.put(contentObject);
        requestJson.put("contents", contentsArray);

        // Note: Vertex AI Gemini doesn't use the Metadata class for temperature/maxTokens
        // Those would be set via environment variables or configuration if needed

        // Notify observer (before request)
        if (conversationObserver != null) {
            conversationObserver.addMessage(new ConversationObserver.Message("DMTools", message));
        }

        logger.debug("Sending request to Vertex AI Gemini: {}", sanitizeUrl(buildEndpointUrl(modelToUse)));
        logger.debug("Request body: {}", requestJson.toString(2));

        // Make API call
        String responseBody = performPost(buildEndpointUrl(modelToUse), requestJson.toString());

        // Parse and notify observer (after response)
        String aiResponse = parseGeminiResponse(responseBody);

        if (conversationObserver != null) {
            conversationObserver.addMessage(new ConversationObserver.Message("AI", aiResponse));
        }

        return aiResponse;

    }

    @Override
    public String chat(String model, Message... messages) throws Exception {
        String modelToUse = (model == null || model.trim().isEmpty()) ? this.model : model;

        // Normalize message roles to "model" (Gemini's assistant role name)
        messages = normalizeMessageRoles(messages);

        // Build request JSON with conversation history
        JSONObject requestJson = new JSONObject();
        JSONArray contentsArray = new JSONArray();

        for (Message msg : messages) {
            JSONObject contentObject = new JSONObject();
            contentObject.put("role", msg.getRole());

            JSONArray partsArray = new JSONArray();

            // Add text part
            if (msg.getText() != null && !msg.getText().trim().isEmpty()) {
                JSONObject textPart = new JSONObject();
                textPart.put("text", msg.getText());
                partsArray.put(textPart);
            }

            // Add file parts if present
            if (msg.getFiles() != null && !msg.getFiles().isEmpty()) {
                for (File file : msg.getFiles()) {
                    // Check file existence and readability before encoding
                    if (!file.exists() || !file.canRead()) {
                        logger.warn("Skipping file (does not exist or not readable): {}", file.getAbsolutePath());
                        continue;
                    }

                    String mimeType = determineMimeType(file);
                    String base64Data = encodeFileToBase64(file);

                    JSONObject filePart = new JSONObject();
                    JSONObject inlineData = new JSONObject();
                    inlineData.put("mime_type", mimeType);
                    inlineData.put("data", base64Data);
                    filePart.put("inline_data", inlineData);
                    partsArray.put(filePart);
                }
            }

            contentObject.put("parts", partsArray);
            contentsArray.put(contentObject);
        }

        requestJson.put("contents", contentsArray);

        // Note: Vertex AI Gemini doesn't use the Metadata class for temperature/maxTokens
        // Those would be set via environment variables or configuration if needed

        logger.debug("Sending multi-turn conversation to Vertex AI Gemini");
        logger.debug("Request body: {}", requestJson.toString(2));

        // Make API call
        String responseBody = performPost(buildEndpointUrl(modelToUse), requestJson.toString());

        // Parse and return response
        return parseGeminiResponse(responseBody);
    }

    @Override
    public String chat(Message... messages) throws Exception {
        return chat(this.model, messages);
    }

    @Override
    public String roleName() {
        return "model"; // Gemini uses "model" as the assistant role name
    }

    /**
     * Performs a POST request with authentication using AbstractRestClient infrastructure.
     * This provides POST caching, retry-with-backoff, and standardized error handling.
     *
     * @param url Request URL
     * @param jsonBody JSON request body
     * @return Response body as string
     * @throws IOException if request fails
     */
    private String performPost(String url, String jsonBody) throws IOException {
        GenericRequest request = new GenericRequest(this, url);
        request.setBody(jsonBody);

        logger.debug("Making POST request to: {}", sanitizeUrl(url));

        // Use parent's post() method which provides caching, retry, and sign() integration
        return post(request);
    }

    /**
     * Parses Gemini API response and extracts text content.
     *
     * @param responseBody Response body JSON
     * @return Extracted text content
     * @throws IOException if response cannot be parsed or is blocked
     */
    private String parseGeminiResponse(String responseBody) throws IOException {
        try {
            JSONObject responseJson = new JSONObject(responseBody);

            // Check for error field
            if (responseJson.has("error")) {
                JSONObject error = responseJson.getJSONObject("error");
                String errorMessage = error.optString("message", "Unknown error");
                int errorCode = error.optInt("code", -1);
                logger.error("Vertex AI Gemini API error: {} (code: {})", errorMessage, errorCode);
                throw new IOException("Vertex AI Gemini API error: " + errorMessage);
            }

            // Check for blocked content
            if (responseJson.has("promptFeedback")) {
                JSONObject promptFeedback = responseJson.getJSONObject("promptFeedback");
                if (promptFeedback.has("blockReason")) {
                    String blockReason = promptFeedback.getString("blockReason");
                    logger.error("Content blocked by Gemini safety filters: {}", blockReason);
                    throw new IOException("Content blocked by safety filters: " + blockReason);
                }
            }

            // Extract text from candidates
            if (responseJson.has("candidates")) {
                JSONArray candidates = responseJson.getJSONArray("candidates");
                if (candidates.length() > 0) {
                    JSONObject firstCandidate = candidates.getJSONObject(0);

                    // Check finish reason
                    if (firstCandidate.has("finishReason")) {
                        String finishReason = firstCandidate.getString("finishReason");
                        if ("SAFETY".equals(finishReason)) {
                            logger.error("Response blocked by safety filters");
                            throw new IOException("Response blocked by safety filters");
                        }
                    }

                    if (firstCandidate.has("content")) {
                        JSONObject content = firstCandidate.getJSONObject("content");
                        if (content.has("parts")) {
                            JSONArray parts = content.getJSONArray("parts");
                            StringBuilder textBuilder = new StringBuilder();

                            for (int i = 0; i < parts.length(); i++) {
                                JSONObject part = parts.getJSONObject(i);
                                if (part.has("text")) {
                                    textBuilder.append(part.getString("text"));
                                }
                            }

                            String result = textBuilder.toString();
                            if (!result.isEmpty()) {
                                return result;
                            }
                        }
                    }
                }
            }

            logger.error("No text content found in Vertex AI Gemini response: {}", responseBody);
            throw new IOException("No text content found in response");

        } catch (IOException e) {
            // Re-throw IOExceptions as-is (already properly formatted)
            throw e;
        } catch (Exception e) {
            // Wrap other exceptions (e.g., JSON parsing errors)
            logger.error("Failed to parse Vertex AI Gemini response", e);
            throw new IOException("Failed to parse response: " + e.getMessage(), e);
        }
    }

    /**
     * Builds content array with text and files (base64 encoded).
     *
     * @param message Text message
     * @param files List of files to attach
     * @return JSONArray of parts
     * @throws IOException if file encoding fails
     */
    private JSONArray buildContentArrayWithFiles(String message, List<File> files) throws IOException {
        JSONArray partsArray = new JSONArray();

        // Add text part
        if (message != null && !message.trim().isEmpty()) {
            JSONObject textPart = new JSONObject();
            textPart.put("text", message);
            partsArray.put(textPart);
        }

        // Add file parts
        if (files != null && !files.isEmpty()) {
            for (File file : files) {
                if (!file.exists() || !file.canRead()) {
                    logger.warn("Skipping file (does not exist or not readable): {}", file.getAbsolutePath());
                    continue;
                }

                String mimeType = determineMimeType(file);
                String base64Data = encodeFileToBase64(file);

                JSONObject filePart = new JSONObject();
                JSONObject inlineData = new JSONObject();
                inlineData.put("mime_type", mimeType);
                inlineData.put("data", base64Data);
                filePart.put("inline_data", inlineData);
                partsArray.put(filePart);

                logger.debug("Added file to request: {} ({})", file.getName(), mimeType);
            }
        }

        return partsArray;
    }

    /**
     * Determines MIME type from file extension.
     *
     * @param file The file
     * @return MIME type string
     */
    private String determineMimeType(File file) {
        String fileName = file.getName().toLowerCase();

        // Images
        if (fileName.endsWith(".png")) return "image/png";
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) return "image/jpeg";
        if (fileName.endsWith(".gif")) return "image/gif";
        if (fileName.endsWith(".webp")) return "image/webp";
        if (fileName.endsWith(".heic")) return "image/heic";
        if (fileName.endsWith(".heif")) return "image/heif";

        // Documents
        if (fileName.endsWith(".pdf")) return "application/pdf";
        if (fileName.endsWith(".txt")) return "text/plain";
        if (fileName.endsWith(".html") || fileName.endsWith(".htm")) return "text/html";
        if (fileName.endsWith(".json")) return "application/json";

        // Default
        return "application/octet-stream";
    }

    /**
     * Encodes a file to Base64 string.
     *
     * @param file The file to encode
     * @return Base64 encoded string
     * @throws IOException if file cannot be read
     */
    private String encodeFileToBase64(File file) throws IOException {
        byte[] fileBytes = FileUtils.readFileToByteArray(file);
        return Base64.getEncoder().encodeToString(fileBytes);
    }
}
