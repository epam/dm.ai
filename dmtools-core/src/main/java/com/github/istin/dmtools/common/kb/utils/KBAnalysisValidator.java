package com.github.istin.dmtools.common.kb.utils;

import com.github.istin.dmtools.common.kb.model.AnalysisResult;
import org.apache.logging.log4j.Logger;

/**
 * Validates analysis results ensuring mandatory fields are populated.
 */
public class KBAnalysisValidator {

    public void validateAndClean(AnalysisResult analysisResult, Logger logger) {
        int initialQuestions = analysisResult.getQuestions().size();
        int initialAnswers = analysisResult.getAnswers().size();
        int initialNotes = analysisResult.getNotes().size();

        analysisResult.getQuestions().removeIf(q -> {
            boolean remove = missing(q.getAuthor()) || missing(q.getDate()) || missing(q.getArea());
            if (remove && logger != null) {
                logger.warn("Filtering out question with missing metadata: id={}", q.getId());
            }
            return remove;
        });

        analysisResult.getAnswers().removeIf(a -> {
            boolean remove = missing(a.getAuthor()) || missing(a.getDate()) || missing(a.getArea());
            if (remove && logger != null) {
                logger.warn("Filtering out answer with missing metadata: id={}", a.getId());
            }
            return remove;
        });

        analysisResult.getNotes().removeIf(n -> {
            boolean remove = missing(n.getAuthor()) || missing(n.getDate()) || missing(n.getArea());
            if (remove && logger != null) {
                logger.warn("Filtering out note with missing metadata: id={}", n.getId());
            }
            return remove;
        });

        int filteredQuestions = initialQuestions - analysisResult.getQuestions().size();
        int filteredAnswers = initialAnswers - analysisResult.getAnswers().size();
        int filteredNotes = initialNotes - analysisResult.getNotes().size();

        if (logger != null) {
            if (filteredQuestions + filteredAnswers + filteredNotes > 0) {
                logger.warn("Validation removed entries - questions: {}/{} answers: {}/{} notes: {}/{}",
                        filteredQuestions, initialQuestions,
                        filteredAnswers, initialAnswers,
                        filteredNotes, initialNotes);
            } else {
                logger.info("Validation passed: all entries contain required metadata");
            }
        }
    }

    private boolean missing(String value) {
        return value == null || value.trim().isEmpty();
    }
}
