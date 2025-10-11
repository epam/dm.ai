package com.github.istin.dmtools.common.kb.model;

import lombok.Data;
import java.util.HashSet;
import java.util.Set;

/**
 * Context about existing KB for incremental updates
 */
@Data
public class KBContext {
    private Set<String> existingPeople = new HashSet<>();
    private Set<String> existingTopics = new HashSet<>();
    private int maxQuestionId;
    private int maxAnswerId;
    private int maxNoteId;
}


