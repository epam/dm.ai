package com.github.istin.dmtools.common.kb.model;

import lombok.Data;

/**
 * Link embedded in questions, answers, or notes
 */
@Data
public class Link {
    private String url;
    private String title;
}


