package com.github.istin.dmtools.common.kb.agent;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.ai.agent.AbstractSimpleAgent;
import com.github.istin.dmtools.ai.utils.AIResponseParser;
import com.github.istin.dmtools.common.kb.model.AnalysisResult;
import com.github.istin.dmtools.common.kb.params.AnalysisParams;
import com.github.istin.dmtools.common.kb.params.JSONFixParams;
import com.github.istin.dmtools.prompt.IPromptTemplateReader;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import javax.inject.Inject;

/**
 * AI-powered agent that analyzes chat messages and extracts semantic information
 * Extends AbstractSimpleAgent to use AI client
 * Returns AnalysisResult with themes, questions, answers, and notes
 */
public class KBAnalysisAgent extends AbstractSimpleAgent<AnalysisParams, AnalysisResult> {
    
    private static final Logger logger = LogManager.getLogger(KBAnalysisAgent.class);
    private static final Gson GSON = new Gson();
    
    private final JSONFixAgent jsonFixAgent;
    
    @Inject
    public KBAnalysisAgent(AI ai, IPromptTemplateReader promptTemplateReader, JSONFixAgent jsonFixAgent) {
        super("agents/kb_analysis");  // XML prompt template name in agents/ folder
        this.ai = ai;
        this.promptTemplateReader = promptTemplateReader;
        this.jsonFixAgent = jsonFixAgent;
    }
    
    @Override
    public AnalysisResult transformAIResponse(AnalysisParams params, String response) throws Exception {
        // Parse JSON response from AI into AnalysisResult
        // AI returns JSON following analysis_schema.json format
        
        // Clean markdown code blocks if present (AI sometimes wraps JSON in ```json ... ```)
        String cleanedResponse;
        try {
            JSONObject jsonObject = AIResponseParser.parseResponseAsJSONObject(response);
            cleanedResponse = jsonObject.toString();
        } catch (Exception e) {
            cleanedResponse = AIResponseParser.parseCodeResponse(response);
        }

        // Try to parse JSON and capture exception
        ParseResult parseResult = tryParseJson(cleanedResponse);
        
        // If parsing failed, try to fix JSON with JSONFixAgent (1 retry)
        if (parseResult.result == null && parseResult.exception != null) {
            logger.warn("Initial JSON parsing failed: {}", parseResult.exception.getMessage());
            logger.warn("Attempting to fix with JSONFixAgent...");
            
            String fixedJson = fixMalformedJson(cleanedResponse, parseResult.exception);
            ParseResult fixedParseResult = tryParseJson(fixedJson);
            
            if (fixedParseResult.result == null) {
                logger.error("Failed to parse JSON even after fix attempt");
                if (fixedParseResult.exception != null) {
                    throw new Exception("Failed to parse JSON after fix: " + fixedParseResult.exception.getMessage(), 
                                      fixedParseResult.exception);
                } else {
                    throw new Exception("Failed to parse JSON after fix attempt");
                }
            }
            
            logger.info("✓ JSON fixed successfully by JSONFixAgent");
            parseResult = fixedParseResult;
        }
        
        AnalysisResult result = parseResult.result;
        
        // Ensure lists are not null
        if (result.getQuestions() == null) result.setQuestions(new java.util.ArrayList<>());
        if (result.getAnswers() == null) result.setAnswers(new java.util.ArrayList<>());
        if (result.getNotes() == null) result.setNotes(new java.util.ArrayList<>());
        
        return result;
    }
    
    /**
     * Try to parse JSON, return result with exception if fails
     */
    private ParseResult tryParseJson(String json) {
        try {
            AnalysisResult result = GSON.fromJson(json, AnalysisResult.class);
            return new ParseResult(result, null);
        } catch (JsonSyntaxException e) {
            logger.debug("JSON parsing failed: {}", e.getMessage());
            return new ParseResult(null, e);
        }
    }
    
    /**
     * Helper class to return both result and exception
     */
    private static class ParseResult {
        final AnalysisResult result;
        final JsonSyntaxException exception;
        
        ParseResult(AnalysisResult result, JsonSyntaxException exception) {
            this.result = result;
            this.exception = exception;
        }
    }
    
    /**
     * Use JSONFixAgent to fix malformed JSON
     */
    private String fixMalformedJson(String malformedJson, JsonSyntaxException exception) throws Exception {
        JSONFixParams fixParams = new JSONFixParams();
        fixParams.setMalformedJson(malformedJson);
        
        // Build detailed error message from exception
        StringBuilder errorMsg = new StringBuilder();
        errorMsg.append(exception.getClass().getSimpleName()).append(": ");
        errorMsg.append(exception.getMessage());
        if (exception.getCause() != null) {
            errorMsg.append("\nCaused by: ").append(exception.getCause().getMessage());
        }
        
        fixParams.setErrorMessage(errorMsg.toString());
        fixParams.setExpectedSchema("AnalysisResult with questions[], answers[], notes[] arrays");
        
        logger.debug("Sending to JSONFixAgent with error: {}", errorMsg);
        
        return jsonFixAgent.run(fixParams);
    }
}

