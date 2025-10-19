package com.github.istin.dmtools.common.kb.agent;

import com.github.istin.dmtools.common.kb.model.KBContext;
import com.github.istin.dmtools.common.kb.model.QAMappingResult;
import com.github.istin.dmtools.common.kb.params.QAMappingParams;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for KBQuestionAnswerMappingAgent logic
 */
class KBQuestionAnswerMappingAgentTest {
    
    @Test
    void testMappingResultParsing() {
        // Test that QAMappingResult model can be created and accessed correctly
        QAMappingResult result = new QAMappingResult();
        List<QAMappingResult.Mapping> mappings = new ArrayList<>();
        
        QAMappingResult.Mapping mapping = new QAMappingResult.Mapping();
        mapping.setAnswerId("a_1");
        mapping.setQuestionId("q_0001");
        mapping.setConfidence(0.95);
        
        mappings.add(mapping);
        result.setMappings(mappings);
        
        assertEquals(1, result.getMappings().size());
        assertEquals("a_1", result.getMappings().get(0).getAnswerId());
        assertEquals("q_0001", result.getMappings().get(0).getQuestionId());
        assertEquals(0.95, result.getMappings().get(0).getConfidence());
    }
    
    @Test
    void testAnswerLikeCreation() {
        // Test creating AnswerLike from Answer
        com.github.istin.dmtools.common.kb.model.Answer answer = new com.github.istin.dmtools.common.kb.model.Answer();
        answer.setId("a_1");
        answer.setAuthor("Bob Smith");
        answer.setText("Use multi-stage builds");
        answer.setArea("docker");
        answer.setTopics(Arrays.asList("build-optimization", "best-practices"));
        
        QAMappingParams.AnswerLike answerLike = QAMappingParams.AnswerLike.fromAnswer(answer);
        
        assertEquals("a_1", answerLike.getId());
        assertEquals("Bob Smith", answerLike.getAuthor());
        assertEquals("Use multi-stage builds", answerLike.getText());
        assertEquals("docker", answerLike.getArea());
        assertEquals(2, answerLike.getTopics().size());
        assertTrue(answerLike.getTopics().contains("build-optimization"));
    }
    
    @Test
    void testAnswerLikeFromNote() {
        // Test creating AnswerLike from Note
        com.github.istin.dmtools.common.kb.model.Note note = new com.github.istin.dmtools.common.kb.model.Note();
        note.setId("n_1");
        note.setAuthor("Charlie White");
        note.setText("Important: Always use .dockerignore");
        note.setArea("docker");
        note.setTopics(Arrays.asList("best-practices"));
        
        QAMappingParams.AnswerLike answerLike = QAMappingParams.AnswerLike.fromNote(note);
        
        assertEquals("n_1", answerLike.getId());
        assertEquals("Charlie White", answerLike.getAuthor());
        assertEquals("Important: Always use .dockerignore", answerLike.getText());
        assertEquals("docker", answerLike.getArea());
        assertEquals(1, answerLike.getTopics().size());
        assertTrue(answerLike.getTopics().contains("best-practices"));
    }
    
    @Test
    void testQuestionSummaryCreation() {
        // Test creating QuestionSummary
        KBContext.QuestionSummary question = new KBContext.QuestionSummary(
                "q_0001",
                "Alice Brown",
                "How do I optimize Docker build speed?",
                "docker",
                false
        );
        
        assertEquals("q_0001", question.getId());
        assertEquals("Alice Brown", question.getAuthor());
        assertEquals("How do I optimize Docker build speed?", question.getText());
        assertEquals("docker", question.getArea());
        assertFalse(question.isAnswered());
    }
    
    @Test
    void testMappingParamsSetup() {
        // Test setting up mapping params
        QAMappingParams params = new QAMappingParams();
        
        List<QAMappingParams.AnswerLike> newAnswers = new ArrayList<>();
        QAMappingParams.AnswerLike answer = new QAMappingParams.AnswerLike();
        answer.setId("a_1");
        answer.setAuthor("Bob Smith");
        answer.setText("Use python:3.11-slim as base image");
        answer.setArea("docker");
        answer.setTopics(Arrays.asList("dockerfile", "python-application"));
        newAnswers.add(answer);
        
        List<KBContext.QuestionSummary> existingQuestions = new ArrayList<>();
        existingQuestions.add(new KBContext.QuestionSummary(
                "q_0001",
                "Alice Brown",
                "How do I create a Dockerfile for a Python application?",
                "docker",
                false
        ));
        
        params.setNewAnswers(newAnswers);
        params.setExistingQuestions(existingQuestions);
        
        assertEquals(1, params.getNewAnswers().size());
        assertEquals(1, params.getExistingQuestions().size());
        assertEquals("a_1", params.getNewAnswers().get(0).getId());
        assertEquals("q_0001", params.getExistingQuestions().get(0).getId());
    }
    
    @Test
    void testMultipleMappings() {
        // Test result with multiple mappings
        QAMappingResult result = new QAMappingResult();
        List<QAMappingResult.Mapping> mappings = new ArrayList<>();
        
        // Mapping 1: high confidence
        QAMappingResult.Mapping mapping1 = new QAMappingResult.Mapping();
        mapping1.setAnswerId("a_1");
        mapping1.setQuestionId("q_0001");
        mapping1.setConfidence(0.95);
        mappings.add(mapping1);
        
        // Mapping 2: medium confidence
        QAMappingResult.Mapping mapping2 = new QAMappingResult.Mapping();
        mapping2.setAnswerId("n_1");
        mapping2.setQuestionId("q_0002");
        mapping2.setConfidence(0.75);
        mappings.add(mapping2);
        
        result.setMappings(mappings);
        
        assertEquals(2, result.getMappings().size());
        
        // Verify we can filter by confidence
        long highConfidenceMappings = result.getMappings().stream()
                .filter(m -> m.getConfidence() >= 0.9)
                .count();
        assertEquals(1, highConfidenceMappings);
        
        long mediumConfidenceMappings = result.getMappings().stream()
                .filter(m -> m.getConfidence() >= 0.6 && m.getConfidence() < 0.9)
                .count();
        assertEquals(1, mediumConfidenceMappings);
    }
}

