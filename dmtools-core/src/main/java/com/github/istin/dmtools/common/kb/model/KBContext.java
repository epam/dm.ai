package com.github.istin.dmtools.common.kb.model;

import lombok.Data;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Context about existing KB for incremental updates
 */
@Data
public class KBContext {
    private Set<String> existingPeople = new HashSet<>();
    private Set<String> existingTopics = new HashSet<>();
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


