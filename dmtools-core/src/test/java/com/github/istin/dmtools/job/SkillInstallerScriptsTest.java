// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.job;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards epam/dm.ai#675: the CLI installer and the skill installer share ONE list of skills, every
 * advertised skill has a release pack, and the skill step never deletes skills the user kept.
 */
class SkillInstallerScriptsTest {

    private static String read(String relative) throws IOException {
        Path root = Paths.get("").toAbsolutePath().getParent();
        return new String(Files.readAllBytes(root.resolve(relative)), StandardCharsets.UTF_8);
    }

    private static List<String> words(String block) {
        List<String> out = new ArrayList<>();
        for (String w : block.trim().split("\\s+")) {
            if (!w.isEmpty()) out.add(w);
        }
        return out;
    }

    private static List<String> mainInstallerSkills() throws IOException {
        Matcher m = Pattern.compile("AVAILABLE_SKILLS=\\(([^)]*)\\)").matcher(read("install.sh"));
        assertTrue(m.find(), "AVAILABLE_SKILLS not found in install.sh");
        return words(m.group(1));
    }

    private static List<String> skillInstallerSkills() throws IOException {
        Matcher m = Pattern.compile("(?m)^ALL_SKILL_KEYS=\\(([^)]*)\\)").matcher(read("dmtools-ai-docs/install.sh"));
        assertTrue(m.find(), "ALL_SKILL_KEYS not found in dmtools-ai-docs/install.sh");
        return words(m.group(1));
    }

    @Test
    void bothInstallersAcceptExactlyTheSameSkills() throws IOException {
        List<String> a = new ArrayList<>(mainInstallerSkills());
        List<String> b = new ArrayList<>(skillInstallerSkills());
        a.sort(null);
        b.sort(null);
        assertEquals(a, b, "install.sh AVAILABLE_SKILLS and skill-install ALL_SKILL_KEYS diverged");
        assertTrue(b.containsAll(Arrays.asList("jira", "gitlab", "confluence")), "the reported skills must be installable");
    }

    @Test
    void everyAdvertisedSkillHasAReleasePack() throws IOException {
        String release = read(".github/workflows/release.yml");
        for (String skill : skillInstallerSkills()) {
            String pack = "dmtools".equals(skill) ? "dmtools-skill" : "dmtools-" + skill + "-skill";
            assertTrue(release.contains("dist/" + pack + "*.zip"), "no upload path for " + pack);
            assertTrue(release.contains("skill/" + pack + ".zip"), "no release asset for " + pack);
        }
    }

    @Test
    void skillStepIsNonDestructiveUnlessPruneIsRequested() throws IOException {
        String script = read("dmtools-ai-docs/install.sh");
        assertTrue(script.contains("--prune"), "--prune flag must exist");
        assertTrue(Pattern.compile("if \\[ \"\\$PRUNE_SKILLS\" = true \\]; then\\s+remove_deselected_skills").matcher(script).find(),
                "remove_deselected_skills must only run with --prune");
    }

    @Test
    void mainInstallerRunsTheSkillStepForTheSameRelease_andItIsNeverFatal() throws IOException {
        String script = read("install.sh");
        assertTrue(script.contains("install_agent_skills \"$version\""), "main() must call install_agent_skills");
        assertTrue(script.contains("--no-skills"), "--no-skills opt-out must exist");
        String fn = script.substring(script.indexOf("install_agent_skills() {"));
        fn = fn.substring(0, fn.indexOf("\n}\n"));
        assertTrue(fn.contains("download/${version}"), "skills come from the release being installed");
        assertTrue(fn.contains("--global"), "the main installer installs globally only");
        assertFalse(fn.contains("error \""), "a failing skill step must warn, never abort the CLI install");
    }
}
