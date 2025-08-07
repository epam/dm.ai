package com.github.istin.dmtools.ai.js;

import com.github.istin.dmtools.ai.ConversationObserver;
import com.github.istin.dmtools.common.utils.PropertyReader;
import freemarker.template.TemplateException;
import org.apache.commons.io.IOUtils;
import org.json.JSONObject;

import javax.script.ScriptException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

public class BasicJSAIClient extends JSAIClient {
    public static final String BASE_PATH;
    public static final String API_KEY;
    public static final String MODEL;
    public static final String JS_SCRIPT_CLASSPATH = "js/dialChatViaJs.js"; // Path within resources

    static {
        PropertyReader propertyReader = new PropertyReader();
        BASE_PATH = propertyReader.getDialBathPath(); // Reuse Dial properties
        API_KEY = propertyReader.getDialIApiKey();
        MODEL = propertyReader.getDialModel();
    }

    public BasicJSAIClient() throws IOException, ScriptException, URISyntaxException, TemplateException {
        this(null);
    }

    public BasicJSAIClient(ConversationObserver conversationObserver) throws IOException, ScriptException, URISyntaxException, TemplateException {
        super(constructConfigJson(), conversationObserver);
    }

    private static String loadTestScriptContent() throws IOException {
        try (InputStream scriptStream = BasicJSAIClient.class.getClassLoader().getResourceAsStream(JS_SCRIPT_CLASSPATH)) {
            if (scriptStream == null) {
                throw new IOException("Test JavaScript file not found in classpath resources: " + JS_SCRIPT_CLASSPATH);
            }
            return IOUtils.toString(scriptStream, StandardCharsets.UTF_8);
        }
    }

    private static JSONObject constructConfigJson() throws IOException {
        String jsCode = loadTestScriptContent();

        JSONObject config = new JSONObject();
        config.put("clientName", "BasicJSAIClient");
        config.put("basePath", BASE_PATH);
        config.put("defaultModel", MODEL);
        
        JSONObject secrets = new JSONObject();
        secrets.put("apiKey", API_KEY);
        config.put("secrets", secrets);
        
        // Option 1: Embed script directly (if not too large)
        config.put("jsScript", jsCode);
        // Option 2: Provide path (JSAIClient will load it - ensure path is correct for JSAIClient's loading logic)
        // config.put("jsScriptPath", JS_SCRIPT_CLASSPATH); 
        // For BasicJSAIClient, embedding is simpler as we load it here anyway.

        return config;
    }
} 