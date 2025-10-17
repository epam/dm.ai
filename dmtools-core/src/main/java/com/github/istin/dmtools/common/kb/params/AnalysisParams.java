package com.github.istin.dmtools.common.kb.params;

import com.github.istin.dmtools.common.kb.model.KBContext;
import lombok.Data;

/**
 * Parameters for KBAnalysisAgent
 * Simple params without chunking support - chunking is handled by orchestrator
 */
@Data
public class AnalysisParams {
    private String inputText;  // Raw text to analyze (single chunk)
    private String sourceName;
    private KBContext context;  // Existing KB structure
    private String extraInstructions;  // Optional extra instructions for AI
}


