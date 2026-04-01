// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.qa;

import com.github.istin.dmtools.job.TrackerParams;
import lombok.*;

import java.util.List;

/**
 * Parameters for the InstructionsGeneratorJob.
 * Extends TrackerParams to inherit tracker configuration capabilities.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public class InstructionsGeneratorParams extends TrackerParams {

    /**
     * List of fields to extract and generate instructions for
     * Examples: ["summary", "description", "acceptance_criteria"] for stories
     *           ["description", "test_steps", "expected_results"] for test cases
     */
    private List<String> fields;

    /**
     * Type of instructions to generate (e.g., "test_cases", "user_story", "technical_spec")
     */
    private String instructionType;

    /**
     * Output destination: "confluence" or "file"
     */
    private String outputDestination;

    /**
     * Output location:
     * - For file: absolute file path
     * - For Confluence: full URL to the page (https://...)
     */
    private String outputPath;

    /**
     * Additional Confluence pages with rules/context (optional)
     * Can be URLs or local file paths
     */
    private String[] confluencePages;

    /**
     * Whether to merge with existing content (default: true)
     * If true and output exists, will merge new instructions with existing ones
     */
    private boolean mergeWithExisting = true;

    /**
     * Model to use for AI processing (optional, uses default if not specified)
     */
    private String model;

    /**
     * Additional context or rules for instruction generation (optional)
     * Can include team-specific guidelines, formatting preferences, etc.
     */
    private String additionalContext;

    /**
     * Number of threads to use for parallel instruction generation from chunks
     * Default: 4 threads for optimal performance without overwhelming the system
     */
    private int generationThreads = 4;

    /**
     * Number of threads to use for parallel merging of instruction chunks
     * Default: 2 threads (merging is often more memory-intensive)
     */
    private int mergingThreads = 2;

    /**
     * Platform for which to generate formatting rules
     * Options: "jira", "ado", "confluence", "github", "gitlab"
     * Default: "jira" (most common)
     */
    private String platform = "jira";

}