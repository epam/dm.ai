package com.github.istin.dmtools.common.kb.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.istin.dmtools.common.kb.agent.KBQuestionAnswerMappingAgent;
import com.github.istin.dmtools.common.kb.model.*;
import com.github.istin.dmtools.common.kb.params.QAMappingParams;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class KBQAMappingServiceTest {

    private KBQuestionAnswerMappingAgent mappingAgent;
    private KBQAMappingService mappingService;
    private Logger logger;

    @BeforeEach
    void setUp() {
        mappingAgent = mock(KBQuestionAnswerMappingAgent.class);
        mappingService = new KBQAMappingService(mappingAgent);
        logger = LogManager.getLogger(KBQAMappingServiceTest.class);
    }

    @Test
    void skipsWhenNoExistingQuestions() throws Exception {
        AnalysisResult result = minimalAnalysisResult();
        KBContext context = new KBContext();

        mappingService.applyMapping(result, context, null, logger);

        verifyNoInteractions(mappingAgent);
    }

    @Test
    void filtersAndMapsAnswers() throws Exception {
        AnalysisResult result = minimalAnalysisResult();
        Answer unmapped = new Answer();
        unmapped.setId("a_001");
        unmapped.setAuthor("Alice");
        unmapped.setArea("Space");
        result.getAnswers().add(unmapped);

        KBContext context = new KBContext();
        KBContext.QuestionSummary summary = new KBContext.QuestionSummary("q_100", "Bob", "Question?", "Space", false);
        context.getExistingQuestions().add(summary);

        QAMappingResult mappingResult = new QAMappingResult();
        QAMappingResult.Mapping mapping = new QAMappingResult.Mapping();
        mapping.setAnswerId("a_001");
        mapping.setQuestionId("q_100");
        mapping.setConfidence(0.9);
        mappingResult.setMappings(List.of(mapping));

        when(mappingAgent.run(any())).thenReturn(mappingResult);

        mappingService.applyMapping(result, context, "instructions", logger);

        assertEquals("q_100", result.getAnswers().get(0).getAnswersQuestion());
        verify(mappingAgent).run(argThat(params ->
                params.getNewAnswers().size() == 1 &&
                        "instructions".equals(params.getExtraInstructions()) &&
                        params.getExistingQuestions().get(0).equals(summary)));
    }

    @Test
    void mapsNoteToQuestion() throws Exception {
        AnalysisResult result = minimalAnalysisResult();
        Note note = new Note();
        note.setId("n_001");
        note.setAuthor("Alice");
        note.setArea("Space");
        note.setText("note");
        result.getNotes().add(note);

        KBContext context = new KBContext();
        KBContext.QuestionSummary summary = new KBContext.QuestionSummary("q_100", "Bob", "Question?", "Space", false);
        context.getExistingQuestions().add(summary);

        QAMappingResult mappingResult = new QAMappingResult();
        QAMappingResult.Mapping mapping = new QAMappingResult.Mapping();
        mapping.setAnswerId("n_001");
        mapping.setQuestionId("q_100");
        mapping.setConfidence(0.8);
        mappingResult.setMappings(Collections.singletonList(mapping));

        when(mappingAgent.run(any())).thenReturn(mappingResult);

        mappingService.applyMapping(result, context, null, logger);

        // Note should remain as note (not converted to answer)
        assertEquals(1, result.getNotes().size());
        assertEquals(0, result.getAnswers().size());
        
        // Note should have answersQuestions field set
        Note mappedNote = result.getNotes().get(0);
        assertEquals("n_001", mappedNote.getId());
        assertEquals(1, mappedNote.getAnswersQuestions().size());
        assertEquals("q_100", mappedNote.getAnswersQuestions().get(0));
    }

    @Test
    void mapsNoteToMultipleQuestions() throws Exception {
        AnalysisResult result = minimalAnalysisResult();
        Note note = new Note();
        note.setId("n_001");
        note.setAuthor("Alice");
        note.setArea("Space");
        note.setText("This note answers multiple questions");
        result.getNotes().add(note);

        KBContext context = new KBContext();
        context.getExistingQuestions().add(new KBContext.QuestionSummary("q_100", "Bob", "Q1?", "Space", false));
        context.getExistingQuestions().add(new KBContext.QuestionSummary("q_101", "Charlie", "Q2?", "Space", false));
        context.getExistingQuestions().add(new KBContext.QuestionSummary("q_102", "Dave", "Q3?", "Space", false));

        QAMappingResult mappingResult = new QAMappingResult();
        // Same note mapped to three questions
        QAMappingResult.Mapping mapping1 = new QAMappingResult.Mapping();
        mapping1.setAnswerId("n_001");
        mapping1.setQuestionId("q_100");
        mapping1.setConfidence(0.9);
        
        QAMappingResult.Mapping mapping2 = new QAMappingResult.Mapping();
        mapping2.setAnswerId("n_001");
        mapping2.setQuestionId("q_101");
        mapping2.setConfidence(0.85);
        
        QAMappingResult.Mapping mapping3 = new QAMappingResult.Mapping();
        mapping3.setAnswerId("n_001");
        mapping3.setQuestionId("q_102");
        mapping3.setConfidence(0.88);
        
        mappingResult.setMappings(List.of(mapping1, mapping2, mapping3));

        when(mappingAgent.run(any())).thenReturn(mappingResult);

        mappingService.applyMapping(result, context, null, logger);

        // Note should remain as note
        assertEquals(1, result.getNotes().size());
        assertEquals(0, result.getAnswers().size());
        
        // Note should have all three questions
        Note mappedNote = result.getNotes().get(0);
        assertEquals("n_001", mappedNote.getId());
        assertEquals(3, mappedNote.getAnswersQuestions().size());
        assertTrue(mappedNote.getAnswersQuestions().contains("q_100"));
        assertTrue(mappedNote.getAnswersQuestions().contains("q_101"));
        assertTrue(mappedNote.getAnswersQuestions().contains("q_102"));
    }

    @Test
    void mapsMultipleNotesToSameQuestion() throws Exception {
        AnalysisResult result = minimalAnalysisResult();
        
        Note note1 = new Note();
        note1.setId("n_001");
        note1.setAuthor("Alice");
        note1.setArea("Space");
        note1.setText("First note");
        result.getNotes().add(note1);
        
        Note note2 = new Note();
        note2.setId("n_002");
        note2.setAuthor("Bob");
        note2.setArea("Space");
        note2.setText("Second note");
        result.getNotes().add(note2);

        KBContext context = new KBContext();
        context.getExistingQuestions().add(new KBContext.QuestionSummary("q_100", "Charlie", "Question?", "Space", false));

        QAMappingResult mappingResult = new QAMappingResult();
        // Both notes map to same question
        QAMappingResult.Mapping mapping1 = new QAMappingResult.Mapping();
        mapping1.setAnswerId("n_001");
        mapping1.setQuestionId("q_100");
        mapping1.setConfidence(0.9);
        
        QAMappingResult.Mapping mapping2 = new QAMappingResult.Mapping();
        mapping2.setAnswerId("n_002");
        mapping2.setQuestionId("q_100");
        mapping2.setConfidence(0.85);
        
        mappingResult.setMappings(List.of(mapping1, mapping2));

        when(mappingAgent.run(any())).thenReturn(mappingResult);

        mappingService.applyMapping(result, context, null, logger);

        // Both notes should remain as notes
        assertEquals(2, result.getNotes().size());
        assertEquals(0, result.getAnswers().size());
        
        // Both notes should reference the same question
        Note mappedNote1 = result.getNotes().stream().filter(n -> n.getId().equals("n_001")).findFirst().get();
        assertEquals(1, mappedNote1.getAnswersQuestions().size());
        assertEquals("q_100", mappedNote1.getAnswersQuestions().get(0));
        
        Note mappedNote2 = result.getNotes().stream().filter(n -> n.getId().equals("n_002")).findFirst().get();
        assertEquals(1, mappedNote2.getAnswersQuestions().size());
        assertEquals("q_100", mappedNote2.getAnswersQuestions().get(0));
    }

    @Test
    void parseAndProcessLLMGeneratedJSON() throws Exception {
        // Load JSON file from test resources
        InputStream jsonStream = getClass().getClassLoader().getResourceAsStream("kb_qa_mapping_with_notes.json");
        assertNotNull(jsonStream, "Test JSON file should exist");
        
        ObjectMapper mapper = new ObjectMapper();
        QAMappingResult mappingResult = mapper.readValue(jsonStream, QAMappingResult.class);
        
        // Verify JSON was parsed correctly
        assertNotNull(mappingResult);
        assertNotNull(mappingResult.getMappings());
        assertEquals(7, mappingResult.getMappings().size());
        
        // Verify specific mappings from JSON
        QAMappingResult.Mapping firstMapping = mappingResult.getMappings().get(0);
        assertEquals("a_0001", firstMapping.getAnswerId());
        assertEquals("q_0001", firstMapping.getQuestionId());
        assertEquals(0.95, firstMapping.getConfidence());
        
        // Note n_0002 should map to two questions (q_0003 and q_0004)
        long n0002Mappings = mappingResult.getMappings().stream()
            .filter(m -> "n_0002".equals(m.getAnswerId()))
            .count();
        assertEquals(2, n0002Mappings);
        
        // Note n_0003 should map to two questions (q_0001 and q_0002)
        long n0003Mappings = mappingResult.getMappings().stream()
            .filter(m -> "n_0003".equals(m.getAnswerId()))
            .count();
        assertEquals(2, n0003Mappings);
    }

    @Test
    void fullIntegrationTestWithComplexMappings() throws Exception {
        // Create analysis result with questions, answers, and notes
        AnalysisResult result = minimalAnalysisResult();
        
        // Add questions
        Question q1 = new Question();
        q1.setId("q_0001");
        q1.setAuthor("Alice");
        q1.setArea("Docker");
        result.getQuestions().add(q1);
        
        Question q2 = new Question();
        q2.setId("q_0002");
        q2.setAuthor("Bob");
        q2.setArea("Docker");
        result.getQuestions().add(q2);
        
        Question q3 = new Question();
        q3.setId("q_0003");
        q3.setAuthor("Charlie");
        q3.setArea("Docker");
        result.getQuestions().add(q3);
        
        // Add answer
        Answer a1 = new Answer();
        a1.setId("a_0001");
        a1.setAuthor("Dave");
        a1.setArea("Docker");
        result.getAnswers().add(a1);
        
        // Add notes
        Note n1 = new Note();
        n1.setId("n_0001");
        n1.setAuthor("Eve");
        n1.setArea("Docker");
        n1.setText("Use BuildKit");
        result.getNotes().add(n1);
        
        Note n2 = new Note();
        n2.setId("n_0002");
        n2.setAuthor("Frank");
        n2.setArea("Docker");
        n2.setText("Multi-stage builds are efficient");
        result.getNotes().add(n2);

        // Create context with existing questions
        KBContext context = new KBContext();
        context.getExistingQuestions().add(new KBContext.QuestionSummary("q_0001", "Alice", "Q1", "Docker", false));
        context.getExistingQuestions().add(new KBContext.QuestionSummary("q_0002", "Bob", "Q2", "Docker", false));
        context.getExistingQuestions().add(new KBContext.QuestionSummary("q_0003", "Charlie", "Q3", "Docker", false));

        // Load mapping from JSON
        InputStream jsonStream = getClass().getClassLoader().getResourceAsStream("kb_qa_mapping_with_notes.json");
        ObjectMapper mapper = new ObjectMapper();
        QAMappingResult mappingResult = mapper.readValue(jsonStream, QAMappingResult.class);

        when(mappingAgent.run(any())).thenReturn(mappingResult);

        mappingService.applyMapping(result, context, null, logger);

        // Verify answer mapping
        Answer mappedAnswer = result.getAnswers().get(0);
        assertEquals("q_0001", mappedAnswer.getAnswersQuestion());
        
        // Verify note mappings
        Note mappedNote1 = result.getNotes().stream().filter(n -> n.getId().equals("n_0001")).findFirst().get();
        assertEquals(1, mappedNote1.getAnswersQuestions().size());
        assertEquals("q_0002", mappedNote1.getAnswersQuestions().get(0));
        
        Note mappedNote2 = result.getNotes().stream().filter(n -> n.getId().equals("n_0002")).findFirst().get();
        assertEquals(2, mappedNote2.getAnswersQuestions().size());
        assertTrue(mappedNote2.getAnswersQuestions().contains("q_0003"));
        assertTrue(mappedNote2.getAnswersQuestions().contains("q_0004")); // This question is not in result, but mapping should work
    }

    @Test
    void preventsDuplicateMappings() throws Exception {
        AnalysisResult result = minimalAnalysisResult();
        Note note = new Note();
        note.setId("n_001");
        note.setAuthor("Alice");
        note.setArea("Space");
        note.setText("note");
        result.getNotes().add(note);

        KBContext context = new KBContext();
        context.getExistingQuestions().add(new KBContext.QuestionSummary("q_100", "Bob", "Question?", "Space", false));

        QAMappingResult mappingResult = new QAMappingResult();
        // Try to map same note to same question twice
        QAMappingResult.Mapping mapping1 = new QAMappingResult.Mapping();
        mapping1.setAnswerId("n_001");
        mapping1.setQuestionId("q_100");
        mapping1.setConfidence(0.9);
        
        QAMappingResult.Mapping mapping2 = new QAMappingResult.Mapping();
        mapping2.setAnswerId("n_001");
        mapping2.setQuestionId("q_100");
        mapping2.setConfidence(0.85);
        
        mappingResult.setMappings(List.of(mapping1, mapping2));

        when(mappingAgent.run(any())).thenReturn(mappingResult);

        mappingService.applyMapping(result, context, null, logger);

        // Should only have one mapping, not duplicate
        Note mappedNote = result.getNotes().get(0);
        assertEquals(1, mappedNote.getAnswersQuestions().size());
        assertEquals("q_100", mappedNote.getAnswersQuestions().get(0));
    }

    private AnalysisResult minimalAnalysisResult() {
        AnalysisResult result = new AnalysisResult();
        result.setQuestions(new ArrayList<>());
        result.setAnswers(new ArrayList<>());
        result.setNotes(new ArrayList<>());
        return result;
    }
}
