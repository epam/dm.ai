package com.github.istin.dmtools.common.kb.params;

import lombok.Data;

/**
 * Parameters for KBOrchestrator main agent
 */
@Data
public class KBOrchestratorParams {
    private String sourceName;
    private String inputFile;
    private String dateTime;
    private String outputPath;
}


