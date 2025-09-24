package com.github.istin.dmtools.ai.google;

import com.github.istin.dmtools.ai.ConversationObserver;
import com.github.istin.dmtools.ai.js.JSAIClient;
import com.github.istin.dmtools.mcp.MCPTool;
import com.github.istin.dmtools.mcp.MCPParam;
import freemarker.template.TemplateException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import javax.script.ScriptException;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Gemini-specific implementation of JSAIClient.
 * The role name is determined by the JavaScript script via getRoleName() function.
 */
public class GeminiJSAIClient extends JSAIClient {

    private static final Logger logger = LogManager.getLogger(GeminiJSAIClient.class);

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

    /**
     * Chat with Gemini AI using file attachments
     * @param message The text message to send to Gemini
     * @param filePaths Array of file paths to attach to the message
     * @return AI response as string
     */
    @MCPTool(
        name = "gemini_ai_chat_with_files",
        description = "Send a text message to Gemini AI with file attachments. Supports images, documents, and other file types for analysis and questions.",
        integration = "ai"
    )
    public String chatWithFiles(
            @MCPParam(
                name = "message",
                description = "Text message to send to Gemini AI",
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

        logger.info("Gemini AI chat with files: message='{}', files={}", 
                message, files.stream().map(File::getName).collect(Collectors.toList()));

        // Call the parent chat method with files
        return super.chat(null, message, files); // null model uses default model
    }
}
