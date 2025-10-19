package com.github.istin.dmtools.common.kb.model;

import lombok.Data;

/**
 * Information about a single source in the KB
 */
@Data
public class SourceInfo {
    private String lastSyncDate;  // ISO 8601
    private String updatedAt;     // ISO 8601
}


