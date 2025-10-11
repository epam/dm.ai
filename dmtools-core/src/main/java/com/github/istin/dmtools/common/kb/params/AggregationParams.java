package com.github.istin.dmtools.common.kb.params;

import lombok.Data;
import java.nio.file.Path;
import java.util.Map;

/**
 * Parameters for KBAggregationAgent
 * Used to generate narrative descriptions for people/topics/themes
 */
@Data
public class AggregationParams {
    private String entityType;  // "person", "topic", "theme"
    private String entityId;
    private Path kbPath;
    private Map<String, Object> entityData;
}


