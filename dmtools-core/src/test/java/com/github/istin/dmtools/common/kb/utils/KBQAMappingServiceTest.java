package com.github.istin.dmtools.common.kb.utils;

import com.github.istin.dmtools.common.kb.agent.KBQuestionAnswerMappingAgent;
import com.github.istin.dmtools.common.kb.model.*;
import com.github.istin.dmtools.common.kb.params.QAMappingParams;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void convertsNotesToAnswers() throws Exception {
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

        assertEquals(1, result.getAnswers().size());
        Answer converted = result.getAnswers().get(0);
        assertEquals("a_001", converted.getId());
        assertEquals("q_100", converted.getAnswersQuestion());
        assertEquals(0.8, converted.getQuality());
        assertEquals(0, result.getNotes().size());
    }

    private AnalysisResult minimalAnalysisResult() {
        AnalysisResult result = new AnalysisResult();
        result.setQuestions(new ArrayList<>());
        result.setAnswers(new ArrayList<>());
        result.setNotes(new ArrayList<>());
        return result;
    }
}
