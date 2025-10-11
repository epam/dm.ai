package com.github.istin.dmtools.common.kb.model;

import lombok.Data;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for all sources in the KB
 * Stored in <output_path>/inbox/source_config.json
 */
@Data
public class SourceConfig {
    private Map<String, SourceInfo> sources = new HashMap<>();
}


