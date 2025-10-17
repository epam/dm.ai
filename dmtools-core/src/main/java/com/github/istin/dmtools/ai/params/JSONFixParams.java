package com.github.istin.dmtools.ai.params;

import lombok.Data;

/**
 * Parameters for JSONFixAgent
 */
@Data
public class JSONFixParams {
    private String malformedJson;     // The malformed JSON that needs fixing
    private String errorMessage;      // The parsing error message
    private String expectedSchema;    // Description of expected JSON schema
}

