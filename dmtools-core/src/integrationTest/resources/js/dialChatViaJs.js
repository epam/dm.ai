// openAiChatViaJs.js - Now a FreeMarker template

// Inject the OPENAI_API_KEY from secrets via Freemarker template processing
var OPENAI_API_KEY = "${secrets.DIAL_API_KEY!''}";

/**
 * Handles chat requests by constructing an DIAL-compatible request.
 * Secrets like apiKey are injected by FreeMarker before this script is evaluated.
 */
function handleChat(messagesString, modelToUse, metadataString, javaClient) {
    try {
        javaClient.jsLogInfo("DIAL JS handleChat called with model: " + modelToUse);
        
        // Check if API key is properly injected
        if (typeof DIAL_API_KEY === 'undefined' || DIAL_API_KEY === null || DIAL_API_KEY === "" || DIAL_API_KEY.startsWith("$")) {
            let errorMsg = "DIAL_API_KEY is not defined or not replaced by Freemarker. Please check JSAIClient configuration and ensure DIAL_API_KEY is in JSAI_SECRETS_KEYS and application properties.";
            javaClient.jsLogError(errorMsg);
            return "Error: " + errorMsg;
        }
        
        const messagesFromJava = JSON.parse(messagesString);
        let processedMessages = [];

        for (let i = 0; i < messagesFromJava.length; i++) {
            const originalMessage = messagesFromJava[i];
            let DIALMessage = {
                role: originalMessage.role,
                content: [] // Content will now be an array for DIAL
            };

            if (originalMessage.parts && originalMessage.parts.length > 0) {
                for (let j = 0; j < originalMessage.parts.length; j++) {
                    const part = originalMessage.parts[j];
                    if (part.text) {
                        DIALMessage.content.push({
                            type: "text",
                            text: part.text
                        });
                    } else if (part.inline_data) {
                        // DIAL expects data:mime_type;base64,actual_base64_data
                        const imageUrl = "data:" + part.inline_data.mime_type + ";base64," + part.inline_data.data;
                        DIALMessage.content.push({
                            type: "image_url",
                            image_url: {
                                url: imageUrl,
                                // detail: "low" // Optional: low, high, auto (default)
                            }
                        });
                    } else if (part.error) {
                        javaClient.jsLogWarn("Skipping part with error in message " + i + ": " + part.error);
                    }
                }
            } else {
                // Fallback for old format or text-only messages without parts - should not happen with new JSAIClient
                // but as a safety measure, or if a message somehow has no parts array.
                if (originalMessage.text_content) { // Check for legacy field first
                     DIALMessage.content.push({ type: "text", text: originalMessage.text_content });
                } else if (originalMessage.text) { // Check for new simple text field if parts is missing
                     DIALMessage.content.push({ type: "text", text: originalMessage.text });
                }
                javaClient.jsLogWarn("Message " + i + " did not have a 'parts' array or content was not in expected format. Original msg: " + JSON.stringify(originalMessage));
            }

            // If content array has only one text item, DIAL API also accepts content as a string directly.
            // However, to be consistent for multimodal, always sending as array is fine.
            if (DIALMessage.content.length === 1 && DIALMessage.content[0].type === "text") {
                 DIALMessage.content = DIALMessage.content[0].text;
            }

            processedMessages.push(DIALMessage);
        }

        const requestPayload = {
            model: modelToUse,
            messages: processedMessages
        };

        // Add metadata if present (e.g., temperature, max_tokens)
        if (metadataString) {
            try {
                const metadata = JSON.parse(metadataString);
                if (metadata.max_tokens) {
                    requestPayload.max_tokens = metadata.max_tokens;
                }
                if (metadata.temperature) {
                    requestPayload.temperature = metadata.temperature;
                }
                 // Add other DIAL specific parameters from metadata as needed
            } catch (e) {
                javaClient.jsLogWarn("Could not parse metadataString: " + metadataString + ", Error: " + e.toString());
            }
        }

        const body = JSON.stringify(requestPayload);
        javaClient.jsLogInfo("Request to DIAL: " + body);

        // API Key is expected to be available as a global variable 'DIAL_API_KEY'
        // This variable should be injected by the Freemarker template processing in Java.
        const headers = {
            "Content-Type": "application/json",
            "Authorization": "Bearer " + DIAL_API_KEY
        };

        // Assuming JSAIClient's basePath is correctly set to the DIAL API base URL (e.g., "https://api.DIAL.com/v1")
        // Or if this script specific basePath is injected via secrets, e.g. secrets.DIALBasePath
        // The path for chat completions is "/chat/completions"
        // javaClient.executePost will prepend client's base path if not an absolute URL.
        // For DIAL, this means JSAIClient should have its base path set to "https://api.DIAL.com"
        // and the relative path here would be "/v1/chat/completions".
        // Or, the full URL can be constructed here and passed to executePost.
        // Let's assume secrets.DIALBasePath is "https://api.DIAL.com/v1"
        const DIALBasePath = typeof secrets !== 'undefined' && secrets.DIALBasePath ? secrets.DIALBasePath : "https://api.openai.com/v1";
        const fullUrl = openAIBasePath.endsWith('/') ? openAIBasePath + "chat/completions" : openAIBasePath + "/chat/completions";


        const responseBody = javaClient.executePost(fullUrl, body, headers);
        javaClient.jsLogInfo("Response from OpenAI: " + responseBody);

        const responseJson = JSON.parse(responseBody);

        if (responseJson.choices && responseJson.choices.length > 0 && responseJson.choices[0].message) {
            let assistantResponse = responseJson.choices[0].message.content;
            if (!assistantResponse && responseJson.choices[0].message.tool_calls) {
                // Handle tool calls if necessary, for now, just stringify them.
                assistantResponse = "Tool calls: " + JSON.stringify(responseJson.choices[0].message.tool_calls);
                javaClient.jsLogInfo("OpenAI response included tool calls: " + assistantResponse);
            }
            return assistantResponse;
        } else if (responseJson.error) {
            javaClient.jsLogError("Error from OpenAI: " + responseJson.error.message);
            return "Error: " + responseJson.error.message;
        } else {
            javaClient.jsLogError("Could not extract content from OpenAI response. Full response: " + responseBody);
            return "Error: No content in OpenAI response";
        }

    } catch (e) {
        // Ensure the exception message and stack trace (if available) are logged to Java.
        let stack = e.stack ? e.stack : "No stack trace available";
        javaClient.jsLogErrorWithException("Exception in OpenAI JS handleChat: " + e.toString(), stack);
        return "Error: Exception in JavaScript - " + e.toString();
    }
}

// Function to return the role name used by DIAL/OpenAI for assistant/model responses
function getRoleName() {
    return "assistant";
} 