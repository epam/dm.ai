package com.github.istin.dmtools.common.kb.model;

import lombok.Data;
import java.util.List;

/**
 * Important fact or observation in the knowledge base
 */
@Data
public class Note {
    private String id;            // n_XXXX format
    private String text;
    private String area;          // Top-level category (e.g., "docker", "python", "kubernetes")
    private List<String> topics;  // Detailed themes (e.g., ["dockerfile", "best-practices", "optimization"])
    private List<String> tags;    // Techniques/tools (e.g., ["buildkit", "caching", "multi-stage"])
    private List<String> themes;  // @Deprecated - old field name
    private String author;
    private String date;          // ISO 8601
    private List<Link> links;
}


