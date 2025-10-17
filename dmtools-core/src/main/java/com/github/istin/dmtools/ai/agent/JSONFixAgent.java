package com.github.istin.dmtools.ai.agent;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.ai.utils.AIResponseParser;
import com.github.istin.dmtools.ai.params.JSONFixParams;
import com.github.istin.dmtools.prompt.IPromptTemplateReader;

import javax.inject.Inject;

/**
 * AI-powered agent that fixes malformed JSON responses
 * Used as a fallback when initial JSON parsing fails
 */
public class JSONFixAgent extends AbstractSimpleAgent<JSONFixParams, String> {
    
    @Inject
    public JSONFixAgent(AI ai, IPromptTemplateReader promptTemplateReader) {
        super("agents/json_fix");  // XML prompt template name
        this.ai = ai;
        this.promptTemplateReader = promptTemplateReader;
    }
    
    @Override
    public String transformAIResponse(JSONFixParams params, String response) throws Exception {
        // Clean markdown code blocks if present
        return AIResponseParser.parseCodeResponse(response);
    }
}

