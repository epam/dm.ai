package com.github.istin.dmtools.common.kb.model;

import lombok.Data;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Context about existing KB for incremental updates
 */
@Data
public class KBContext {
    // LinkedHashSet preserves insertion order for stable AI prompt generation
    private Set<String> existingPeople = new LinkedHashSet<>();
    private Set<String> existingTopics = new LinkedHashSet<>();
    private List<QuestionSummary> existingQuestions = new ArrayList<>();
    private int maxQuestionId;
    private int maxAnswerId;
    private int maxNoteId;
    
    /**
     * Summary of an existing question for context
     */
    @Data
    public static class QuestionSummary {
        private String id;
        private String author;
        private String text;
        private String area;
        private boolean answered;
        
        public QuestionSummary(String id, String author, String text, String area, boolean answered) {
            this.id = id;
            this.author = author;
            this.text = text;
            this.area = area;
            this.answered = answered;
        }
    }
}


