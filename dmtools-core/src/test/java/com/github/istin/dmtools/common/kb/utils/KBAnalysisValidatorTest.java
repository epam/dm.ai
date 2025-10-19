package com.github.istin.dmtools.common.kb.utils;

import com.github.istin.dmtools.common.kb.model.AnalysisResult;
import com.github.istin.dmtools.common.kb.model.Answer;
import com.github.istin.dmtools.common.kb.model.Note;
import com.github.istin.dmtools.common.kb.model.Question;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KBAnalysisValidatorTest {

    private final KBAnalysisValidator validator = new KBAnalysisValidator();
    private final Logger logger = LogManager.getLogger(KBAnalysisValidatorTest.class);

    @Test
    void removesEntriesMissingRequiredFields() {
        AnalysisResult result = new AnalysisResult();
        result.setQuestions(new ArrayList<>());
        result.setAnswers(new ArrayList<>());
        result.setNotes(new ArrayList<>());

        Question validQuestion = new Question();
        validQuestion.setId("q1");
        validQuestion.setAuthor("Alice");
        validQuestion.setDate("2025-01-01");
        validQuestion.setArea("Space");
        result.getQuestions().add(validQuestion);

        Question invalidQuestion = new Question();
        invalidQuestion.setId("q2");
        invalidQuestion.setAuthor(null);
        result.getQuestions().add(invalidQuestion);

        Answer validAnswer = new Answer();
        validAnswer.setId("a1");
        validAnswer.setAuthor("Bob");
        validAnswer.setDate("2025-01-01");
        validAnswer.setArea("Space");
        result.getAnswers().add(validAnswer);

        Answer invalidAnswer = new Answer();
        invalidAnswer.setId("a2");
        invalidAnswer.setAuthor(" ");
        result.getAnswers().add(invalidAnswer);

        Note validNote = new Note();
        validNote.setId("n1");
        validNote.setAuthor("Cara");
        validNote.setDate("2025-01-01");
        validNote.setArea("Space");
        result.getNotes().add(validNote);

        Note invalidNote = new Note();
        invalidNote.setId("n2");
        invalidNote.setDate(null);
        result.getNotes().add(invalidNote);

        validator.validateAndClean(result, logger);

        assertEquals(1, result.getQuestions().size());
        assertEquals("q1", result.getQuestions().get(0).getId());
        assertEquals(1, result.getAnswers().size());
        assertEquals("a1", result.getAnswers().get(0).getId());
        assertEquals(1, result.getNotes().size());
        assertEquals("n1", result.getNotes().get(0).getId());
    }

    @Test
    void keepsAllEntriesWhenComplete() {
        AnalysisResult result = new AnalysisResult();
        result.setQuestions(new ArrayList<>());
        result.setAnswers(new ArrayList<>());
        result.setNotes(new ArrayList<>());

        Question question = new Question();
        question.setId("q1");
        question.setAuthor("Alice");
        question.setDate("2025-01-01");
        question.setArea("Space");
        result.getQuestions().add(question);

        Answer answer = new Answer();
        answer.setId("a1");
        answer.setAuthor("Bob");
        answer.setDate("2025-01-01");
        answer.setArea("Space");
        result.getAnswers().add(answer);

        Note note = new Note();
        note.setId("n1");
        note.setAuthor("Cara");
        note.setDate("2025-01-01");
        note.setArea("Space");
        result.getNotes().add(note);

        validator.validateAndClean(result, logger);

        assertEquals(1, result.getQuestions().size());
        assertEquals(1, result.getAnswers().size());
        assertEquals(1, result.getNotes().size());
    }
}
