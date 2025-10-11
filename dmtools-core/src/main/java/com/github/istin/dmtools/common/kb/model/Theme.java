package com.github.istin.dmtools.common.kb.model;

import lombok.Data;
import java.util.List;

/**
 * Theme represents a discussion theme or sub-topic
 */
@Data
public class Theme {
    private String id;            // theme-slug (e.g., "cursor-licensing")
    private String title;
    private String description;
    private List<String> topics;
    private List<String> contributors;
    private List<String> dates;   // ISO 8601 dates
}


