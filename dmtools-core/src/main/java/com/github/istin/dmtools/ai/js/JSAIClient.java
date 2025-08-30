package com.github.istin.dmtools.ai.js;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.ai.ConversationObserver;
import com.github.istin.dmtools.ai.Message;
import com.github.istin.dmtools.ai.model.Metadata;
import com.github.istin.dmtools.bridge.DMToolsBridge;
import com.github.istin.dmtools.common.networking.GenericRequest;
import com.github.istin.dmtools.networking.AbstractRestClient;
import com.google.gson.Gson;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import lombok.Getter;
import lombok.Setter;
import okhttp3.Request;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.graalvm.polyglot.HostAccess;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.script.*;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class JSAIClient extends AbstractRestClient implements AI {

    private static final Logger logger = LogManager.getLogger(JSAIClient.class);

    private final ScriptEngine scriptEngine;
    private final String defaultModel;
    private final String scriptName; // For logging/identification
    private final DMToolsBridge dmToolsBridge;

    @Setter
    private Metadata metadata;

    @Getter
    @Setter
    private ConversationObserver conversationObserver;

    public JSAIClient(JSONObject configJson, ConversationObserver observer) throws IOException, ScriptException, TemplateException {
        super(configJson.optString("basePath", null),
              // Primary authorization for AbstractRestClient can still be set from a main apiKey if provided
              // This allows JS to still use javaClient.executePost without setting auth if this one is valid
              // OR JS can completely ignore this and set its own headers (recommended for flexibility)
              configJson.optJSONObject("secrets") != null ? configJson.optJSONObject("secrets").optString("apiKey", null) : null);

        this.scriptName = configJson.optString("clientName", "JSAIClientFromJson");
        this.defaultModel = configJson.optString("defaultModel", null);
        this.conversationObserver = observer;
        this.setCachePostRequestsEnabled(true);

        // Create a bridge with AI-specific permissions
        this.dmToolsBridge = DMToolsBridge.withPermissions(
            this.scriptName,
            DMToolsBridge.Permission.LOGGING_INFO,
            DMToolsBridge.Permission.LOGGING_WARN,
            DMToolsBridge.Permission.LOGGING_ERROR,
            DMToolsBridge.Permission.HTTP_POST_REQUESTS,
            DMToolsBridge.Permission.HTTP_GET_REQUESTS,
            DMToolsBridge.Permission.HTTP_BASE_PATH_ACCESS
        );

        // Configure the bridge's HTTP handler to delegate to this client
        this.dmToolsBridge.setHttpHandler(new DMToolsBridge.HttpHandler() {
            @Override
            public String executePost(String pathOrUrl, String bodyJson, Map<String, Object> headersMap) throws IOException {
                return JSAIClient.this.executePostForBridge(pathOrUrl, bodyJson, headersMap);
            }

            @Override
            public String executeGet(String pathOrUrl, Map<String, Object> headersMap) throws IOException {
                return JSAIClient.this.executeGetForBridge(pathOrUrl, headersMap);
            }

            @Override
            public String getBasePath() {
                return JSAIClient.this.getBasePath();
            }
        });

        String jsTemplate = loadJsScript(configJson);
        JSONObject secretsObject = configJson.optJSONObject("secrets");
        String processedJsCode = processJsTemplateWithFreemarker(jsTemplate, secretsObject, this.scriptName);

        this.scriptEngine = createGraalJSEngine();
        logger.info("Evaluating processed JavaScript for: {}", this.scriptName);
        this.scriptEngine.eval(processedJsCode); // Evaluate the processed script
    }

    /**
     * Creates a GraalJS script engine with proper configuration and fallback support.
     * 
     * @return a configured ScriptEngine instance
     * @throws ScriptException if no suitable JavaScript engine can be found
     */
    private ScriptEngine createGraalJSEngine() throws ScriptException {
        ScriptEngineManager manager = new ScriptEngineManager();
        
        // First, try to get the GraalJS engine
        ScriptEngine engine = manager.getEngineByName("graal.js");
        if (engine != null) {
            logger.info("Successfully initialized GraalJS engine for: {}", this.scriptName);
            return engine;
        }
        
        // Fallback: try alternative GraalJS engine names
        String[] alternativeNames = {"Graal.js", "js", "JavaScript"};
        for (String name : alternativeNames) {
            engine = manager.getEngineByName(name);
            if (engine != null) {
                logger.warn("GraalJS engine not found by name 'graal.js', using fallback engine: {} for: {}", 
                           name, this.scriptName);
                return engine;
            }
        }
        
        // List available engines for debugging
        StringBuilder availableEngines = new StringBuilder("Available script engines: ");
        for (ScriptEngineFactory factory : manager.getEngineFactories()) {
            availableEngines.append(factory.getEngineName())
                           .append(" (")
                           .append(String.join(", ", factory.getNames()))
                           .append("), ");
        }
        logger.error("No JavaScript engine found. {}", availableEngines.toString());
        
        throw new ScriptException("Graal.js script engine not found. Ensure GraalVM's JS dependencies are correctly configured. " + 
                                  availableEngines.toString());
    }

    private static String loadJsScript(JSONObject configJson) throws IOException {
        if (configJson.has("jsScript") && !configJson.isNull("jsScript")) {
            return configJson.getString("jsScript");
        }
        if (configJson.has("jsScriptPath") && !configJson.isNull("jsScriptPath")) {
            String path = configJson.getString("jsScriptPath");
            String classpathPath = path.startsWith("/") ? path.substring(1) : path;
            try (java.io.InputStream inputStream = JSAIClient.class.getClassLoader().getResourceAsStream(classpathPath)) {
                if (inputStream != null) {
                    return IOUtils.toString(inputStream, StandardCharsets.UTF_8);
                }
                logger.warn("Could not load jsScriptPath {} from classpath, trying filesystem.", path);
                return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
            } catch (Exception e) {
                logger.warn("Error loading jsScriptPath {} from classpath/filesystem.", path, e);
                // Fallback to trying direct path if classpath fails badly
                return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
            }
        }
        throw new IOException("JSAIClient config JSON must contain either 'jsScript' (string) or 'jsScriptPath' (path to file).");
    }

    private String processJsTemplateWithFreemarker(String jsTemplate, JSONObject secretsJson, String templateName) throws IOException, TemplateException {
        if (jsTemplate == null || jsTemplate.isEmpty()) {
            return "";
        }

        Configuration cfg = new Configuration(Configuration.VERSION_2_3_30); // Use your project's standard version (2.3.30)
        cfg.setDefaultEncoding("UTF-8");
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        cfg.setLogTemplateExceptions(false);
        cfg.setWrapUncheckedExceptions(true);
        // cfg.setNumberFormat("computer"); // Important for not having commas in numbers for JS

        Map<String, Object> dataModel = new HashMap<>();
        if (secretsJson != null) {
            Map<String, Object> secretsMap = new HashMap<>();
            for (String key : secretsJson.keySet()) {
                secretsMap.put(key, secretsJson.get(key));
            }
            dataModel.put("secrets", secretsMap);
        } else {
            dataModel.put("secrets", Collections.emptyMap());
        }
        // Ensure the "javaClient" is available for the JS template if it needs to call back for secrets/config.
        // However, direct injection of secrets is preferred.
        // dataModel.put("javaClient", this);

        Template template = new Template(templateName, jsTemplate, cfg);
        StringWriter out = new StringWriter();
        template.process(dataModel, out);
        return out.toString();
    }

    // No longer needed:
    // public String getSecretsJson() { return this.secretsJson; }
    // public String getAuthorizationToken() { return this.authorization; }
    // public String getBasePathForJs() { return getBasePath(); }

    public String getName() {
        return "JSAIClient:" + scriptName + (defaultModel != null ? "/" + defaultModel : "");
    }

    @Override
    public String path(String subPath) {
        return subPath;
    }

    @Override
    public Request.Builder sign(Request.Builder builder) {
        // JS is responsible for all authorization. Only set Content-Type here.
        // The 'this.authorization' from AbstractRestClient is available if JS calls getAuthorizationToken()
        // on the JSAIClient instance, but that method is also being removed.
        // The main authorization field in AbstractRestClient is set via super() if secrets.apiKey exists,
        // but JS should define its own auth headers for executePost/Get calls.
        builder.header("Content-Type", "application/json");
        return builder;
    }

    // getLogger, executePost, executeGet, invokeJsChatLogic, chat overloads, buildHashForPostRequest remain.
    // AbstractRestClient.getBasePath() can be used by JS via javaClient.getBasePath()

    @Override
    public int getTimeout() {
        return 700;
    }

    private String invokeJsChatLogic(String modelToUse, JSONArray messagesJsonArray, String metadataString) throws Exception {
        if (!(this.scriptEngine instanceof Invocable)) {
            throw new ScriptException("JavaScript engine does not support function invocation (Invocable interface not available).");
        }
        Invocable invocable = (Invocable) this.scriptEngine;
        String messagesString = messagesJsonArray.toString();
        
        // Pass the bridge instead of 'this' for consistent access
        Object result = invocable.invokeFunction("handleChat", messagesString, modelToUse, metadataString, dmToolsBridge);
        if (result == null) {
            logger.warn("JavaScript handleChat function in {} returned null.", scriptName);
            return "";
        }
        return result.toString();
    }

    // New private helper method for common chat execution logic
    private String executeChatInternal(String modelToUse, JSONArray messagesJsonArray, String initialUserLogTextForObserver /* Not directly used if messages are logged individually */) throws Exception {
        String responseContent = invokeJsChatLogic(modelToUse, messagesJsonArray, (this.metadata != null) ? new Gson().toJson(this.metadata) : null);

        if (conversationObserver != null) {
            conversationObserver.addMessage(new ConversationObserver.Message(getName(), responseContent)); // Log AI response
        }
        logger.info("-------- JSAI response ({}/{}) --------", scriptName, modelToUse);
        logger.info(responseContent);
        return responseContent;
    }

    /**
     * Provides access to the DMToolsBridge for JavaScript code
     */
    @HostAccess.Export
    public DMToolsBridge getBridge() {
        return dmToolsBridge;
    }

    @Override
    public String chat(String messageContent) throws Exception {
        return chat(this.defaultModel, messageContent);
    }

    @Override
    public String chat(String model, String messageContent) throws Exception {
        return chat(model, messageContent, (File) null);
    }

    @Override
    public String chat(String modelName, String messageContent, File imageFile) throws Exception {
        List<File> files = null;
        if (imageFile != null) {
            files = Collections.singletonList(imageFile);
        }
        return chat(modelName, messageContent, files);
    }

    @Override
    public String chat(String modelName, String messageContent, List<File> files) throws Exception {
        String modelToUse = (modelName == null || modelName.trim().isEmpty()) ? this.defaultModel : modelName;
        logger.info("-------- message to JSAI ({}/{}) --------", scriptName, modelToUse);
        logger.info("Text: {}", messageContent);
        if (files != null && !files.isEmpty()) {
            logger.info("Files: {}", files.stream().map(File::getName).collect(Collectors.toList()));
        }

        if (conversationObserver != null) {
            conversationObserver.addMessage(new ConversationObserver.Message("DMToolsUser", messageContent));
        }

        JSONArray messagesArray = new JSONArray();
        JSONObject userMessage = new JSONObject().put("role", "user");
        userMessage.put("parts", createJsonParts(messageContent, files));
        messagesArray.put(userMessage);
        
        return executeChatInternal(modelToUse, messagesArray, messageContent);
    }

    @Override
    public String chat(String modelName, Message... messages) throws Exception {
        String modelToUse = (modelName == null || modelName.trim().isEmpty()) ? this.defaultModel : modelName;
        logger.info("-------- messages to JSAI ({}/{}) --------", scriptName, modelToUse);

        // Normalize message roles to ensure compatibility with this AI provider
        Message[] normalizedMessages = normalizeMessageRoles(messages);

        JSONArray messagesJsonArray = new JSONArray();
        String firstUserMessageForObserver = null; 

        for (int i = 0; i < normalizedMessages.length; i++) {
            Message msg = normalizedMessages[i];
            if (conversationObserver != null) {
                conversationObserver.addMessage(new ConversationObserver.Message(msg.getRole(), msg.getText()));
            }
            if (i == 0 && "user".equalsIgnoreCase(msg.getRole())) {
                firstUserMessageForObserver = msg.getText();
            }

            logger.info("Role: {}, Text: {}", msg.getRole(), msg.getText());
            JSONObject jsonMsg = new JSONObject();
            jsonMsg.put("role", msg.getRole());
            jsonMsg.put("parts", createJsonParts(msg.getText(), msg.getFiles()));
            
            if (msg.getFiles() != null && !msg.getFiles().isEmpty()) {
                logger.info("Files for this message: {}", msg.getFiles().stream().map(File::getName).collect(Collectors.toList()));
            }
            messagesJsonArray.put(jsonMsg);
        }
        
        return executeChatInternal(modelToUse, messagesJsonArray, firstUserMessageForObserver);
    }

    @Override
    public String chat(Message... messages) throws Exception {
        return chat(this.defaultModel, messages);
    }

    @Override
    public String roleName() {
        try {
            if (!(this.scriptEngine instanceof Invocable)) {
                logger.warn("JavaScript engine does not support function invocation, falling back to default role name 'assistant'");
                return "assistant";
            }
            
            Invocable invocable = (Invocable) this.scriptEngine;
            Object result = invocable.invokeFunction("getRoleName");
            if (result != null) {
                String roleName = result.toString();
                logger.debug("JavaScript script returned role name: {}", roleName);
                return roleName;
            } else {
                logger.debug("JavaScript getRoleName() returned null, using default 'assistant'");
                return "assistant";
            }
        } catch (Exception e) {
            logger.debug("Failed to invoke getRoleName() from JavaScript ({}), falling back to default 'assistant': {}", 
                        scriptName, e.getMessage());
            // Fallback to default for backward compatibility
            return "assistant";
        }
    }

    @Override
    protected @NotNull String buildHashForPostRequest(GenericRequest genericRequest, String url) {
        String body = genericRequest.getBody();
        if (body == null) {
            body = "";
        }
        return url + body;
    }

    private String encodeFileToBase64(File file) throws IOException {
        byte[] fileContent = Files.readAllBytes(file.toPath());
        return Base64.getEncoder().encodeToString(fileContent);
    }

    private String determineMimeType(File file) throws IOException {
        String mimeType = Files.probeContentType(file.toPath());
        if (mimeType == null) {
            // Basic fallback
            String fileName = file.getName().toLowerCase();
            if (fileName.endsWith(".png")) {
                mimeType = "image/png";
            } else if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
                mimeType = "image/jpeg";
            } else if (fileName.endsWith(".gif")) {
                mimeType = "image/gif";
            } else if (fileName.endsWith(".webp")) {
                mimeType = "image/webp";
            } else if (fileName.endsWith(".heic")) {
                mimeType = "image/heic";
            } else if (fileName.endsWith(".heif")) {
                mimeType = "image/heif";
            } else {
                mimeType = "application/octet-stream"; // Default
            }
        }
        return mimeType;
    }

    private JSONArray createJsonParts(String textContent, List<File> files) {
        JSONArray partsArray = new JSONArray();
        if (textContent != null && !textContent.isEmpty()) {
            partsArray.put(new JSONObject().put("text", textContent));
        }

        if (files != null && !files.isEmpty()) {
            for (File file : files) {
                try {
                    String base64Data = encodeFileToBase64(file);
                    String mimeType = determineMimeType(file);
                    JSONObject inlineDataPartInfo = new JSONObject()
                            .put("mime_type", mimeType)
                            .put("data", base64Data);
                    partsArray.put(new JSONObject().put("inline_data", inlineDataPartInfo));
                } catch (IOException e) {
                    logger.error("Failed to process file {} for JSAIClient: {}", file.getName(), e.getMessage(), e);
                    // Optionally, add a part indicating the error, or skip
                    partsArray.put(new JSONObject().put("error", "Failed to process file: " + file.getName()));
                }
            }
        }
        return partsArray;
    }

    // Internal methods for bridge delegation
    private String executePostForBridge(String pathOrUrl, String bodyJson, Map<String, Object> headersMap) throws IOException {
        String finalUrl = (pathOrUrl.toLowerCase().startsWith("http://") || pathOrUrl.toLowerCase().startsWith("https://")) ? pathOrUrl : getBasePath() + pathOrUrl;
        GenericRequest postRequest = new GenericRequest(this, finalUrl);
        postRequest.setBody(bodyJson);
        if (headersMap != null) {
            for (Map.Entry<String, Object> entry : headersMap.entrySet()) {
                postRequest.getHeaders().put(entry.getKey(), String.valueOf(entry.getValue()));
            }
        }
        logger.debug("JSAIClient.executePostForBridge to: {} with body: {} and headers: {}", finalUrl, bodyJson, postRequest.getHeaders());
        return post(postRequest);
    }

    private String executeGetForBridge(String pathOrUrl, Map<String, Object> headersMap) throws IOException {
        String finalUrl = (pathOrUrl.toLowerCase().startsWith("http://") || pathOrUrl.toLowerCase().startsWith("https://")) ? pathOrUrl : getBasePath() + pathOrUrl;
        GenericRequest getRequest = new GenericRequest(this, finalUrl);
        if (headersMap != null) {
            for (Map.Entry<String, Object> entry : headersMap.entrySet()) {
                getRequest.getHeaders().put(entry.getKey(), String.valueOf(entry.getValue()));
            }
        }
        logger.debug("JSAIClient.executeGetForBridge to: {} with headers: {}", finalUrl, getRequest.getHeaders());
        return execute(getRequest);
    }
} 