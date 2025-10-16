package com.github.istin.dmtools.common.kb.model;

import lombok.Data;
import java.util.List;

/**
 * Question asked in the knowledge base
 */
@Data
public class Question {
    private String id;            // q_XXXX format
    private String author;
    private String text;
    private String date;          // ISO 8601
    private String area;          // Top-level category (e.g., "docker", "python", "kubernetes")
    private List<String> topics;  // Detailed themes (e.g., ["dockerfile", "best-practices", "optimization"])
    private List<String> tags;    // Techniques/tools (e.g., ["buildkit", "caching", "multi-stage"])
    private String answeredBy;    // a_XXXX or null
    private List<Link> links;
}


