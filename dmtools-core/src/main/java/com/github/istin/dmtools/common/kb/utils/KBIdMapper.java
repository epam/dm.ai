package com.github.istin.dmtools.common.kb.utils;

import com.github.istin.dmtools.common.kb.model.AnalysisResult;
import com.github.istin.dmtools.common.kb.model.Answer;
import com.github.istin.dmtools.common.kb.model.KBContext;
import com.github.istin.dmtools.common.kb.model.Note;
import com.github.istin.dmtools.common.kb.model.Question;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility for mapping temporary IDs to permanent IDs in KB entities
 */
public class KBIdMapper {
    
    private static final Logger logger = LogManager.getLogger(KBIdMapper.class);
    
    /**
     * Map temporary IDs (q_1, a_1, n_1) to real IDs (q_0001, a_0001, n_0001)
     * and update Q→A references accordingly.
     * 
     * @param analysisResult The analysis result with temporary IDs
     * @param context The KB context with current max IDs
     */
    public void mapAndUpdateIds(AnalysisResult analysisResult, KBContext context) {
        Map<String, String> idMapping = createIdMapping(analysisResult, context);
        updateReferences(analysisResult, idMapping);
        
        logger.info("ID mapping completed: {} temporary IDs → real IDs", idMapping.size());
    }
    
    /**
     * Create mapping from temporary IDs to real IDs
     */
    private Map<String, String> createIdMapping(AnalysisResult analysisResult, KBContext context) {
        Map<String, String> idMapping = new HashMap<>();
        
        // Map questions
        int nextQuestionId = context.getMaxQuestionId() + 1;
        for (Question question : analysisResult.getQuestions()) {
            String tempId = question.getId(); // e.g., "q_1"
            String realId = String.format("q_%04d", nextQuestionId++);
            idMapping.put(tempId, realId);
            question.setId(realId);
        }
        
        // Map answers
        int nextAnswerId = context.getMaxAnswerId() + 1;
        for (Answer answer : analysisResult.getAnswers()) {
            String tempId = answer.getId(); // e.g., "a_1"
            String realId = String.format("a_%04d", nextAnswerId++);
            idMapping.put(tempId, realId);
            answer.setId(realId);
        }
        
        // Map notes
        int nextNoteId = context.getMaxNoteId() + 1;
        for (Note note : analysisResult.getNotes()) {
            String tempId = note.getId(); // e.g., "n_1"
            String realId = String.format("n_%04d", nextNoteId++);
            idMapping.put(tempId, realId);
            note.setId(realId);
        }
        
        return idMapping;
    }
    
    /**
     * Update Q→A references using mapped IDs
     */
    private void updateReferences(AnalysisResult analysisResult, Map<String, String> idMapping) {
        // Update Question.answeredBy references
        for (Question question : analysisResult.getQuestions()) {
            String tempAnswerId = question.getAnsweredBy();
            if (tempAnswerId != null && !tempAnswerId.isEmpty() && idMapping.containsKey(tempAnswerId)) {
                question.setAnsweredBy(idMapping.get(tempAnswerId));
            }
        }
        
        // Update Answer.answersQuestion references
        for (Answer answer : analysisResult.getAnswers()) {
            String tempQuestionId = answer.getAnswersQuestion();
            if (tempQuestionId != null && !tempQuestionId.isEmpty() && idMapping.containsKey(tempQuestionId)) {
                answer.setAnswersQuestion(idMapping.get(tempQuestionId));
            }
        }
    }

}

