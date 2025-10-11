package com.github.istin.dmtools.common.kb.agent;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.ai.agent.AbstractSimpleAgent;
import com.github.istin.dmtools.ai.utils.AIResponseParser;
import com.github.istin.dmtools.common.kb.model.AnalysisResult;
import com.github.istin.dmtools.common.kb.params.AnalysisParams;
import com.github.istin.dmtools.prompt.IPromptTemplateReader;
import com.google.gson.Gson;

import javax.inject.Inject;

/**
 * AI-powered agent that analyzes chat messages and extracts semantic information
 * Extends AbstractSimpleAgent to use AI client
 * Returns AnalysisResult with themes, questions, answers, and notes
 */
public class KBAnalysisAgent extends AbstractSimpleAgent<AnalysisParams, AnalysisResult> {
    
    private static final Gson GSON = new Gson();
    
    @Inject
    public KBAnalysisAgent(AI ai, IPromptTemplateReader promptTemplateReader) {
        super("agents/kb_analysis");  // XML prompt template name in agents/ folder
        this.ai = ai;
        this.promptTemplateReader = promptTemplateReader;
    }
    
    @Override
    public AnalysisResult transformAIResponse(AnalysisParams params, String response) throws Exception {
        // Parse JSON response from AI into AnalysisResult
        // AI returns JSON following analysis_schema.json format
        try {
            // Clean markdown code blocks if present (AI sometimes wraps JSON in ```json ... ```)
            String cleanedResponse = AIResponseParser.parseCodeResponse(response);
            
            AnalysisResult result = GSON.fromJson(cleanedResponse, AnalysisResult.class);
            
            // Validate result
            if (result == null) {
                throw new IllegalArgumentException("AI returned null analysis result");
            }
            
            // Ensure lists are not null
            if (result.getQuestions() == null) result.setQuestions(new java.util.ArrayList<>());
            if (result.getAnswers() == null) result.setAnswers(new java.util.ArrayList<>());
            if (result.getNotes() == null) result.setNotes(new java.util.ArrayList<>());
            
            return result;
        } catch (Exception e) {
            throw new Exception("Failed to parse AI analysis response: " + e.getMessage(), e);
        }
    }
}

