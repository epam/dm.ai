package com.github.istin.dmtools.common.kb.model;

import lombok.Data;
import java.util.List;

/**
 * Answer or solution provided in the knowledge base
 */
@Data
public class Answer {
    private String id;            // a_XXXX format
    private String author;
    private String text;
    private String date;          // ISO 8601
    private String area;          // Top-level category (e.g., "docker", "python", "kubernetes")
    private List<String> topics;  // Detailed themes (e.g., ["dockerfile", "best-practices", "optimization"])
    private List<String> tags;    // Techniques/tools (e.g., ["buildkit", "caching", "multi-stage"])
    private String answersQuestion; // q_XXXX or null
    private double quality;       // 0.0-1.0
    private List<Link> links;
}


