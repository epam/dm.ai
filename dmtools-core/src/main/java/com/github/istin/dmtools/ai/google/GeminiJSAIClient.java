package com.github.istin.dmtools.ai.google;

import com.github.istin.dmtools.ai.ConversationObserver;
import com.github.istin.dmtools.ai.js.JSAIClient;
import com.github.istin.dmtools.mcp.MCPTool;
import com.github.istin.dmtools.mcp.MCPParam;
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

    @Override
    @MCPTool(
        name = "gemini_ai_chat",
        description = "Send a text message to Gemini AI and get response",
        integration = "ai"
    )
    public String chat(@MCPParam(name = "message", description = "Text message to send to AI") String messageContent) throws Exception {
        return super.chat(messageContent);
    }
}
