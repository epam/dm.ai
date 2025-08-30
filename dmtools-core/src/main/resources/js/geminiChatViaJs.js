// graaljs-java-production-bindings-commonjs-header-pre
(function(global) {
    // graaljs-java-production-bindings-commonjs-header-post

    // Inject the GEMINI_API_KEY from secrets via Freemarker template processing
    var GEMINI_API_KEY = "${secrets.GEMINI_API_KEY!''}";

    // Function to be called by Java
    // Receives messagesString (JSON string of messages array, already in Gemini's "contents" format)
    // modelToUse (string, e.g., "gemini-2.0-flash")
    // metadataString (JSON string of metadata or null)
    // javaClient (the JSAIClient instance from Java for callbacks like logging or further HTTP calls)
    function handleChat(messagesString, modelToUse, metadataString, javaClient) {
        try {
            javaClient.jsLogInfo("Gemini JS handleChat called with model: " + modelToUse);

            // messagesString is already a JSON array of message objects, where each object has "role" and "parts".
            // This directly maps to Gemini's "contents" array.
            const contents = JSON.parse(messagesString);

            const requestPayload = {
                contents: contents
                // TODO: Add generationConfig if needed from metadataString or defaults (e.g., temperature, maxOutputTokens)
                // Example: 
                // generationConfig: {
                //   temperature: 0.9,
                //   topK: 1,
                //   topP: 1,
                //   maxOutputTokens: 2048,
                //   stopSequences: []
                // }
            };

            if (metadataString) {
                try {
                    const metadata = JSON.parse(metadataString);
                    if (metadata && Object.keys(metadata).length > 0) {
                         // For Gemini, things like temperature, maxOutputTokens go into a generationConfig object
                        requestPayload.generationConfig = {};
                        if (metadata.temperature) requestPayload.generationConfig.temperature = metadata.temperature;
                        if (metadata.max_tokens) requestPayload.generationConfig.maxOutputTokens = metadata.max_tokens; // map max_tokens to maxOutputTokens
                        if (metadata.topK) requestPayload.generationConfig.topK = metadata.topK;
                        if (metadata.topP) requestPayload.generationConfig.topP = metadata.topP;
                        // Add other relevant Gemini generationConfig parameters from metadata as needed
                    }
                } catch (e) {
                    javaClient.jsLogWarn("Gemini JS: Could not parse metadataString: " + metadataString + ", Error: " + e.toString());
                }
            }

            // API Key is expected to be available as a global variable 'GEMINI_API_KEY'
            // This variable should be injected by the Freemarker template processing in Java.
            // Ensure GEMINI_API_KEY is defined in the scope, typically by Freemarker replacement.
            if (typeof GEMINI_API_KEY === 'undefined' || GEMINI_API_KEY === null || GEMINI_API_KEY === "" || GEMINI_API_KEY.startsWith("$")) {
                 let errorMsg = "GEMINI_API_KEY is not defined or not replaced by Freemarker. Please check JSAIClient configuration and ensure GEMINI_API_KEY is in JSAI_SECRETS_KEYS and application properties.";
                 javaClient.jsLogError(errorMsg);
                 return "Error: " + errorMsg;
            }

            const url = "https://generativelanguage.googleapis.com/v1beta/models/" + modelToUse + ":generateContent?key=" + GEMINI_API_KEY;

            const headers = {
                "Content-Type": "application/json"
            };

            javaClient.jsLogInfo("Gemini Request URL: " + url);
            javaClient.jsLogInfo("Gemini Request Payload: " + JSON.stringify(requestPayload));

            const responseBody = javaClient.executePost(url, JSON.stringify(requestPayload), headers);
            javaClient.jsLogInfo("Gemini Raw Response: " + responseBody);

            const responseJson = JSON.parse(responseBody);

            if (responseJson.candidates && responseJson.candidates.length > 0 &&
                responseJson.candidates[0].content && responseJson.candidates[0].content.parts &&
                responseJson.candidates[0].content.parts.length > 0 && responseJson.candidates[0].content.parts[0].text) {
                return responseJson.candidates[0].content.parts[0].text;
            } else if (responseJson.promptFeedback && responseJson.promptFeedback.blockReason) {
                let blockMessage = "Blocked by API. Reason: " + responseJson.promptFeedback.blockReason;
                if (responseJson.promptFeedback.safetyRatings) {
                    blockMessage += ". Safety Ratings: " + JSON.stringify(responseJson.promptFeedback.safetyRatings);
                }
                javaClient.jsLogError(blockMessage);
                return "Error: " + blockMessage;
            } else if (responseJson.error) {
                let errorDetails = responseJson.error.message || "Unknown error structure";
                if (responseJson.error.details) {
                    errorDetails += " Details: " + JSON.stringify(responseJson.error.details);
                }
                javaClient.jsLogError("Error from Gemini API: " + errorDetails + ". Full response: " + responseBody);
                return "Error: Gemini API Error - " + errorDetails;
            } else {
                javaClient.jsLogError("Could not extract text from Gemini response. Full response: " + responseBody);
                return "Error: Could not parse Gemini response or find text content.";
            }

        } catch (e) {
            let stack = e.stack ? e.stack : "No stack trace available";
            javaClient.jsLogErrorWithException("Exception in Gemini JS handleChat: " + e.toString(), stack);
            return "Error: Exception during Gemini API call - " + e.toString();
        }
    }

    // Function to return the role name used by Gemini for assistant/model responses
    function getRoleName() {
        return "model";
    }

    // graaljs-java-production-bindings-commonjs-footer-pre
    global.handleChat = handleChat;
    global.getRoleName = getRoleName;
})(this);
// graaljs-java-production-bindings-commonjs-footer-post 