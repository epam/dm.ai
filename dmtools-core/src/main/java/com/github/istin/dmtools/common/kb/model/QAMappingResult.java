package com.github.istin.dmtools.common.kb.model;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

/**
 * Result of question-answer mapping
 */
@Data
public class QAMappingResult {
    private List<Mapping> mappings = new ArrayList<>();
    
    @Data
    public static class Mapping {
        private String answerId;    // e.g., "a_1", "n_1"
        private String questionId;  // e.g., "q_0001"
        private double confidence;
    }
}

