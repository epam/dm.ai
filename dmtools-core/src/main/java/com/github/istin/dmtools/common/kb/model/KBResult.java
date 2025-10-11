package com.github.istin.dmtools.common.kb.model;

import lombok.Data;

/**
 * Result of KB build operation
 */
@Data
public class KBResult {
    private boolean success;
    private String message;
    private int themesCount;
    private int questionsCount;
    private int answersCount;
    private int notesCount;
    private int peopleCount;
    private int topicsCount;
}


