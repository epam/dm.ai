package com.github.istin.dmtools.ai.google;

import com.github.istin.dmtools.ai.ConversationObserver;
import com.github.istin.dmtools.ai.js.JSAIClient;
import freemarker.template.TemplateException;
import org.json.JSONObject;

import javax.script.ScriptException;
import java.io.IOException;

/**
 * Gemini-specific implementation of JSAIClient.
 * The role name is determined by the JavaScript script via getRoleName() function.
 */
public class GeminiJSAIClient extends JSAIClient {

    public GeminiJSAIClient(JSONObject configJson, ConversationObserver observer) 
            throws IOException, ScriptException, TemplateException {
        super(configJson, observer);
    }
}
