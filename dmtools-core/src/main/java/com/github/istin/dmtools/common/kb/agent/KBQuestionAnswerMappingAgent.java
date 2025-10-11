package com.github.istin.dmtools.common.kb.agent;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.ai.agent.AbstractSimpleAgent;
import com.github.istin.dmtools.ai.utils.AIResponseParser;
import com.github.istin.dmtools.common.kb.model.QAMappingResult;
import com.github.istin.dmtools.common.kb.params.QAMappingParams;
import com.github.istin.dmtools.prompt.IPromptTemplateReader;
import com.google.gson.Gson;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;

/**
 * Agent for mapping new answers to existing questions
 */
public class KBQuestionAnswerMappingAgent extends AbstractSimpleAgent<QAMappingParams, QAMappingResult> {

    private static final Logger logger = LogManager.getLogger(KBQuestionAnswerMappingAgent.class);
    private static final Gson GSON = new Gson();

    @Inject
    public KBQuestionAnswerMappingAgent(AI ai, IPromptTemplateReader promptTemplateReader) {
        super("agents/kb_qa_mapping");
        this.ai = ai;
        this.promptTemplateReader = promptTemplateReader;
        logger.info("KBQuestionAnswerMappingAgent initialized");
    }

    @Override
    public QAMappingResult transformAIResponse(QAMappingParams params, String response) throws Exception {
        try {
            logger.debug("Parsing AI response for Q→A mapping");
            String cleanedResponse = AIResponseParser.parseCodeResponse(response);
            QAMappingResult result = GSON.fromJson(cleanedResponse, QAMappingResult.class);
            
            if (result == null) {
                throw new IllegalArgumentException("AI returned null mapping result");
            }
            
            if (result.getMappings() == null) {
                result.setMappings(new java.util.ArrayList<>());
            }
            
            logger.info("Q→A mapping completed: {} mappings found", result.getMappings().size());
            for (QAMappingResult.Mapping mapping : result.getMappings()) {
                logger.info("  {} → {} (confidence: {})", 
                           mapping.getAnswerId(), 
                           mapping.getQuestionId(),
                           mapping.getConfidence());
            }
            
            return result;
        } catch (Exception e) {
            logger.error("Failed to parse Q→A mapping response", e);
            throw new Exception("Failed to parse Q→A mapping response: " + e.getMessage(), e);
        }
    }
}

