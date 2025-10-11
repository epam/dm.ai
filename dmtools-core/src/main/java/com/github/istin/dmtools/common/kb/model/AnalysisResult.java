package com.github.istin.dmtools.common.kb.model;

import lombok.Data;
import java.util.List;

/**
 * Result of AI analysis
 * Contains questions, answers, and notes with temporary IDs (q_1, a_1, n_1)
 * System will auto-increment these to q_0001, a_0001, n_0001
 */
@Data
public class AnalysisResult {
    private List<Question> questions;
    private List<Answer> answers;
    private List<Note> notes;
}


