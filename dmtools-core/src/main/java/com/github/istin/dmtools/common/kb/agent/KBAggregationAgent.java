package com.github.istin.dmtools.common.kb.agent;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.ai.agent.AbstractSimpleAgent;
import com.github.istin.dmtools.common.kb.params.AggregationParams;
import com.github.istin.dmtools.prompt.IPromptTemplateReader;

import javax.inject.Inject;

/**
 * AI-powered agent that generates narrative descriptions
 * for people profiles, topics, and themes
 * Extends AbstractSimpleAgent to use AI client
 * Returns String with generated description
 */
public class KBAggregationAgent extends AbstractSimpleAgent<AggregationParams, String> {
    
    @Inject
    public KBAggregationAgent(AI ai, IPromptTemplateReader promptTemplateReader) {
        super("agents/kb_aggregation");  // XML prompt template name in agents/ folder
        this.ai = ai;
        this.promptTemplateReader = promptTemplateReader;
    }
    
    @Override
    public String transformAIResponse(AggregationParams params, String response) throws Exception {
        // AI returns generated narrative description as plain text
        // No transformation needed, return as is
        return response;
    }
}


