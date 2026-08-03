// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.docs;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Ensures that every job with its own detailed section in the jobs reference
 * is also surfaced in the main skill manifest (SKILL.md).
 */
class SkillDocumentationTest {

    private static final Pattern JOB_HEADING_PATTERN = Pattern.compile(
            "^### ([A-Z][a-zA-Z0-9]+)$", Pattern.MULTILINE);

    // Single-word category headings that happen to match the job heading pattern.
    private static final Set<String> IGNORED_HEADINGS = Set.of("Documentation", "Utilities");

    @Test
    void everyDetailedJobIsLinkedFromSkillManifest() throws IOException {
        Path moduleRoot = Paths.get("").toAbsolutePath();
        Path jobsReadme = moduleRoot.getParent().resolve("dmtools-ai-docs/references/jobs/README.md");
        Path skillManifest = moduleRoot.getParent().resolve("dmtools-ai-docs/SKILL.md");

        if (!Files.exists(jobsReadme)) {
            fail("Jobs README not found: " + jobsReadme);
        }
        if (!Files.exists(skillManifest)) {
            fail("Skill manifest not found: " + skillManifest);
        }

        String readme = Files.readString(jobsReadme, StandardCharsets.UTF_8);
        String skill = Files.readString(skillManifest, StandardCharsets.UTF_8);

        Set<String> detailedJobs = collectDetailedJobNames(readme);
        assertFalse(detailedJobs.isEmpty(),
                "No detailed job headings found in " + jobsReadme + "; check the heading pattern.");

        Set<String> missing = detailedJobs.stream()
                .filter(job -> !hasSkillLink(skill, job))
                .collect(Collectors.toSet());

        assertTrue(missing.isEmpty(),
                "Detailed jobs missing from SKILL.md: " + missing + ". "
                        + "Add a link like [JobName](references/jobs/README.md#jobname).");
    }

    private Set<String> collectDetailedJobNames(String readme) {
        return JOB_HEADING_PATTERN.matcher(readme).results()
                .map(mr -> mr.group(1))
                .filter(name -> !IGNORED_HEADINGS.contains(name))
                .collect(Collectors.toSet());
    }

    private boolean hasSkillLink(String skill, String jobName) {
        return skill.contains("[" + jobName + "](");
    }
}
