package com.github.istin.dmtools.common.kb.utils;

import com.github.istin.dmtools.common.kb.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class KBIdMapperTest {
    
    private KBIdMapper mapper;
    
    @BeforeEach
    void setUp() {
        mapper = new KBIdMapper();
    }
    
    @Test
    void testMapAndUpdateIds_Questions() {
        // Setup
        KBContext context = new KBContext();
        context.setMaxQuestionId(0);
        context.setMaxAnswerId(0);
        context.setMaxNoteId(0);
        
        AnalysisResult result = new AnalysisResult();
        result.setQuestions(new ArrayList<>());
        result.setAnswers(new ArrayList<>());
        result.setNotes(new ArrayList<>());
        
        Question q1 = new Question();
        q1.setId("q_1");
        q1.setAuthor("Alice");
        
        Question q2 = new Question();
        q2.setId("q_2");
        q2.setAuthor("Bob");
        
        result.getQuestions().add(q1);
        result.getQuestions().add(q2);
        
        // Execute
        mapper.mapAndUpdateIds(result, context);
        
        // Verify
        assertEquals("q_0001", result.getQuestions().get(0).getId());
        assertEquals("q_0002", result.getQuestions().get(1).getId());
    }
    
    @Test
    void testMapAndUpdateIds_Answers() {
        // Setup
        KBContext context = new KBContext();
        context.setMaxQuestionId(0);
        context.setMaxAnswerId(5); // Start from 5
        context.setMaxNoteId(0);
        
        AnalysisResult result = new AnalysisResult();
        result.setQuestions(new ArrayList<>());
        result.setAnswers(new ArrayList<>());
        result.setNotes(new ArrayList<>());
        
        Answer a1 = new Answer();
        a1.setId("a_1");
        a1.setAuthor("Charlie");
        
        Answer a2 = new Answer();
        a2.setId("a_2");
        a2.setAuthor("Dave");
        
        result.getAnswers().add(a1);
        result.getAnswers().add(a2);
        
        // Execute
        mapper.mapAndUpdateIds(result, context);
        
        // Verify - should start from 6
        assertEquals("a_0006", result.getAnswers().get(0).getId());
        assertEquals("a_0007", result.getAnswers().get(1).getId());
    }
    
    @Test
    void testMapAndUpdateIds_Notes() {
        // Setup
        KBContext context = new KBContext();
        context.setMaxQuestionId(0);
        context.setMaxAnswerId(0);
        context.setMaxNoteId(10); // Start from 10
        
        AnalysisResult result = new AnalysisResult();
        result.setQuestions(new ArrayList<>());
        result.setAnswers(new ArrayList<>());
        result.setNotes(new ArrayList<>());
        
        Note n1 = new Note();
        n1.setId("n_1");
        n1.setAuthor("Eve");
        
        result.getNotes().add(n1);
        
        // Execute
        mapper.mapAndUpdateIds(result, context);
        
        // Verify - should be 11
        assertEquals("n_0011", result.getNotes().get(0).getId());
    }
    
    @Test
    void testMapAndUpdateIds_QuestionAnswerReferences() {
        // Setup
        KBContext context = new KBContext();
        context.setMaxQuestionId(0);
        context.setMaxAnswerId(0);
        context.setMaxNoteId(0);
        
        AnalysisResult result = new AnalysisResult();
        result.setQuestions(new ArrayList<>());
        result.setAnswers(new ArrayList<>());
        result.setNotes(new ArrayList<>());
        
        Question q1 = new Question();
        q1.setId("q_1");
        q1.setAuthor("Alice");
        q1.setAnsweredBy("a_1"); // Temporary reference
        
        Answer a1 = new Answer();
        a1.setId("a_1");
        a1.setAuthor("Bob");
        a1.setAnswersQuestion("q_1"); // Temporary reference
        
        result.getQuestions().add(q1);
        result.getAnswers().add(a1);
        
        // Execute
        mapper.mapAndUpdateIds(result, context);
        
        // Verify IDs are mapped
        assertEquals("q_0001", result.getQuestions().get(0).getId());
        assertEquals("a_0001", result.getAnswers().get(0).getId());
        
        // Verify references are updated
        assertEquals("a_0001", result.getQuestions().get(0).getAnsweredBy());
        assertEquals("q_0001", result.getAnswers().get(0).getAnswersQuestion());
    }
    
    @Test
    void testMapAndUpdateIds_EmptyResult() {
        // Setup
        KBContext context = new KBContext();
        context.setMaxQuestionId(0);
        context.setMaxAnswerId(0);
        context.setMaxNoteId(0);
        
        AnalysisResult result = new AnalysisResult();
        result.setQuestions(new ArrayList<>());
        result.setAnswers(new ArrayList<>());
        result.setNotes(new ArrayList<>());
        
        // Execute - should not throw
        assertDoesNotThrow(() -> mapper.mapAndUpdateIds(result, context));
    }
    
    @Test
    void testMapAndUpdateIds_MixedReferences() {
        // Setup
        KBContext context = new KBContext();
        context.setMaxQuestionId(0);
        context.setMaxAnswerId(0);
        context.setMaxNoteId(0);
        
        AnalysisResult result = new AnalysisResult();
        result.setQuestions(new ArrayList<>());
        result.setAnswers(new ArrayList<>());
        result.setNotes(new ArrayList<>());
        
        // Question with no answer reference
        Question q1 = new Question();
        q1.setId("q_1");
        q1.setAuthor("Alice");
        q1.setAnsweredBy(""); // Empty reference
        
        // Answer with no question reference
        Answer a1 = new Answer();
        a1.setId("a_1");
        a1.setAuthor("Bob");
        a1.setAnswersQuestion(null); // Null reference
        
        result.getQuestions().add(q1);
        result.getAnswers().add(a1);
        
        // Execute
        mapper.mapAndUpdateIds(result, context);
        
        // Verify IDs are mapped but empty/null references are preserved
        assertEquals("q_0001", result.getQuestions().get(0).getId());
        assertEquals("a_0001", result.getAnswers().get(0).getId());
        assertEquals("", result.getQuestions().get(0).getAnsweredBy());
        assertNull(result.getAnswers().get(0).getAnswersQuestion());
    }
    
    @Test
    void testMapAndUpdateIds_MultipleQuestionsAndAnswers() {
        // Setup
        KBContext context = new KBContext();
        context.setMaxQuestionId(5);
        context.setMaxAnswerId(10);
        context.setMaxNoteId(0);
        
        AnalysisResult result = new AnalysisResult();
        result.setQuestions(new ArrayList<>());
        result.setAnswers(new ArrayList<>());
        result.setNotes(new ArrayList<>());
        
        // Create 3 questions
        for (int i = 1; i <= 3; i++) {
            Question q = new Question();
            q.setId("q_" + i);
            q.setAuthor("Author" + i);
            result.getQuestions().add(q);
        }
        
        // Create 2 answers
        for (int i = 1; i <= 2; i++) {
            Answer a = new Answer();
            a.setId("a_" + i);
            a.setAuthor("Answerer" + i);
            a.setAnswersQuestion("q_" + i); // Reference to questions
            result.getAnswers().add(a);
        }
        
        // Link questions to answers
        result.getQuestions().get(0).setAnsweredBy("a_1");
        result.getQuestions().get(1).setAnsweredBy("a_2");
        
        // Execute
        mapper.mapAndUpdateIds(result, context);
        
        // Verify question IDs (should be 6, 7, 8)
        assertEquals("q_0006", result.getQuestions().get(0).getId());
        assertEquals("q_0007", result.getQuestions().get(1).getId());
        assertEquals("q_0008", result.getQuestions().get(2).getId());
        
        // Verify answer IDs (should be 11, 12)
        assertEquals("a_0011", result.getAnswers().get(0).getId());
        assertEquals("a_0012", result.getAnswers().get(1).getId());
        
        // Verify references are updated
        assertEquals("a_0011", result.getQuestions().get(0).getAnsweredBy());
        assertEquals("a_0012", result.getQuestions().get(1).getAnsweredBy());
        assertEquals("q_0006", result.getAnswers().get(0).getAnswersQuestion());
        assertEquals("q_0007", result.getAnswers().get(1).getAnswersQuestion());
    }
}

