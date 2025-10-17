package com.github.istin.dmtools.common.kb.params;

import com.github.istin.dmtools.common.kb.model.Answer;
import com.github.istin.dmtools.common.kb.model.KBContext;
import com.github.istin.dmtools.common.kb.model.Note;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Parameters for question-answer mapping agent
 */
@Data
public class QAMappingParams {
    private List<AnswerLike> newAnswers = new ArrayList<>();
    private List<KBContext.QuestionSummary> existingQuestions = new ArrayList<>();
    private String extraInstructions;  // Optional extra instructions for AI
    
    /**
     * Unified interface for answers and notes that can answer questions
     */
    @Data
    public static class AnswerLike {
        private String id;
        private String author;
        private String text;
        private String area;
        private List<String> topics;
        
        public static AnswerLike fromAnswer(Answer answer) {
            AnswerLike al = new AnswerLike();
            al.setId(answer.getId());
            al.setAuthor(answer.getAuthor());
            al.setText(answer.getText());
            al.setArea(answer.getArea());
            al.setTopics(answer.getTopics());
            return al;
        }
        
        public static AnswerLike fromNote(Note note) {
            AnswerLike al = new AnswerLike();
            al.setId(note.getId());
            al.setAuthor(note.getAuthor());
            al.setText(note.getText());
            al.setArea(note.getArea());
            al.setTopics(note.getTopics());
            return al;
        }
    }
}

