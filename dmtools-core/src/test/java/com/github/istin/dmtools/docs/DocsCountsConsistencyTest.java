// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.docs;

import com.github.istin.dmtools.job.Job;
import com.github.istin.dmtools.job.JobRunner;
import com.github.istin.dmtools.mcp.MCPToolDefinition;
import com.github.istin.dmtools.mcp.generated.MCPToolRegistry;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Ensures hard-coded counts in the skill documentation match the actual code:
 * job counts, MCP tool totals, integration counts and per-integration tool counts.
 */
class DocsCountsConsistencyTest {

    private static final Path MODULE_ROOT = Paths.get("").toAbsolutePath();
    private static final Path SKILL_MD = MODULE_ROOT.getParent().resolve("dmtools-ai-docs/SKILL.md");
    private static final Path JOBS_README = MODULE_ROOT.getParent().resolve("dmtools-ai-docs/references/jobs/README.md");

    private static final Pattern JOBS_COUNT_PATTERN = Pattern.compile(
            "all (\\d+)\\+? (?:available )?jobs", Pattern.CASE_INSENSITIVE);

    private static final Pattern TOTAL_TOOLS_PATTERN = Pattern.compile(
            "(\\d+)\\+? MCP tools", Pattern.CASE_INSENSITIVE);

    private static final Pattern TOOLS_AVAILABLE_PATTERN = Pattern.compile(
            "(\\d+)\\+? MCP Tools Available", Pattern.CASE_INSENSITIVE);

    private static final Pattern INTEGRATION_COUNT_PATTERN = Pattern.compile(
            "(\\d+) integrations");

    private static final Pattern PER_INTEGRATION_TOOLS_PATTERN = Pattern.compile(
            "^\\*\\*([^*]+)\\*\\* \\((\\d+)\\+? tools\\)");

    private static final Pattern CONFIG_TABLE_TOOLS_PATTERN = Pattern.compile(
            "\\|[^\\|\\r\\n]*\\|[^\\|\\r\\n]*\\|.*?(\\d+)\\+? tools[^\\|\\r\\n]*\\|");

    private static final Map<String, String> DISPLAY_TO_REGISTRY_INTEGRATION = Map.ofEntries(
            Map.entry("Jira", "jira"),
            Map.entry("Jira Xray", "jira_xray"),
            Map.entry("Teams", "teams"),
            Map.entry("Teams Auth", "teams_auth"),
            Map.entry("Confluence", "confluence"),
            Map.entry("ADO", "ado"),
            Map.entry("Azure DevOps", "ado"),
            Map.entry("Figma", "figma"),
            Map.entry("AI Providers", "ai"),
            Map.entry("Knowledge Base", "kb"),
            Map.entry("File", "file"),
            Map.entry("Mermaid", "mermaid"),
            Map.entry("SharePoint", "sharepoint"),
            Map.entry("CLI", "cli"),
            Map.entry("GitHub", "github"),
            Map.entry("GitLab", "gitlab"),
            Map.entry("Bitrise", "bitrise"),
            Map.entry("TestRail", "testrail"),
            Map.entry("Jenkins", "jenkins"),
            Map.entry("Bitbucket", "bitbucket"),
            Map.entry("Rally", "rally")
    );

    @Test
    void jobCountsMatchActualJobs() throws IOException {
        String skill = readString(SKILL_MD);
        String jobsReadme = readString(JOBS_README);

        int actualJobs = JobRunner.getJobs().size();
        assertTrue(actualJobs > 0, "JobRunner.getJobs() should return jobs");

        List<Integer> skillCounts = extractAll(JOBS_COUNT_PATTERN, skill);
        List<Integer> readmeCounts = extractAll(JOBS_COUNT_PATTERN, jobsReadme);

        assertEquals(List.of(actualJobs), skillCounts,
                "SKILL.md job count should match JobRunner.getJobs().size() = " + actualJobs);
        assertEquals(List.of(actualJobs), readmeCounts,
                "references/jobs/README.md job count should match JobRunner.getJobs().size() = " + actualJobs);
    }

    @Test
    void mcpToolCountsMatchActualTools() {
        List<MCPToolDefinition> tools = MCPToolRegistry.getToolsForIntegrations(
                MCPToolRegistry.getAvailableIntegrations());
        int actualTotal = tools.size();
        assertTrue(actualTotal > 0, "No MCP tools found");

        String skill = readString(SKILL_MD);

        // "152+ MCP Tools Available" heading and any "N+ MCP tools" mentions.
        int toolsAvailable = extractFirst(TOOLS_AVAILABLE_PATTERN, skill)
                .orElseThrow(() -> new AssertionError("Missing 'N+ MCP Tools Available' heading in SKILL.md"));
        List<Integer> totalToolMentions = extractAll(TOTAL_TOOLS_PATTERN, skill);

        assertEquals(actualTotal, toolsAvailable,
                "SKILL.md 'MCP Tools Available' count should match actual MCP tool count");
        assertTrue(totalToolMentions.stream().allMatch(c -> c == actualTotal),
                "Every 'N+ MCP tools' mention in SKILL.md should match actual count " + actualTotal);
    }

    @Test
    void integrationCountMatchesActualIntegrations() {
        int actualIntegrations = MCPToolRegistry.getAvailableIntegrations().size();
        assertTrue(actualIntegrations > 0, "No integrations found");

        String skill = readString(SKILL_MD);
        int docIntegrations = extractFirst(INTEGRATION_COUNT_PATTERN, skill)
                .orElseThrow(() -> new AssertionError("Missing 'N integrations' count in SKILL.md"));

        assertEquals(actualIntegrations, docIntegrations,
                "SKILL.md integration count should match actual integration count");
    }

    @Test
    void perIntegrationToolCountsMatchActualTools() {
        Map<String, Long> actualCounts = MCPToolRegistry.getToolsForIntegrations(
                        MCPToolRegistry.getAvailableIntegrations())
                .stream()
                .collect(Collectors.groupingBy(MCPToolDefinition::getIntegration, Collectors.counting()));

        String skill = readString(SKILL_MD);

        // Core capabilities bullet list.
        Map<String, Integer> docBulletCounts = PER_INTEGRATION_TOOLS_PATTERN.matcher(skill).results()
                .collect(Collectors.toMap(
                        mr -> mr.group(1).trim(),
                        mr -> Integer.parseInt(mr.group(2)),
                        (a, b) -> a));

        for (Map.Entry<String, Integer> entry : docBulletCounts.entrySet()) {
            String registryName = DISPLAY_TO_REGISTRY_INTEGRATION.get(entry.getKey());
            if (registryName == null) {
                fail("Unknown integration display name in SKILL.md: " + entry.getKey()
                        + ". Add it to DISPLAY_TO_REGISTRY_INTEGRATION.");
            }
            Long actual = actualCounts.get(registryName);
            assertTrue(actual != null && actual > 0,
                    "Integration '" + registryName + "' has no registered MCP tools");
            assertEquals(actual.intValue(), entry.getValue(),
                    "SKILL.md tool count for '" + entry.getKey() + "' does not match actual count");
        }

        // Configuration table counts (e.g. "Jira Setup ... 52 tools", "Azure DevOps ... 23+ tools").
        Map<String, Integer> docTableCounts = CONFIG_TABLE_TOOLS_PATTERN.matcher(skill).results()
                .collect(Collectors.toMap(
                        mr -> {
                            // Row like "| | [Azure DevOps](...) | PAT setup and 23+ tools |"
                            String row = mr.group(0);
                            Matcher nameMatcher = Pattern.compile("\\[([^\\]]+)\\]").matcher(row);
                            return nameMatcher.find() ? nameMatcher.group(1).trim() : row;
                        },
                        mr -> Integer.parseInt(mr.group(1)),
                        (a, b) -> a));

        for (Map.Entry<String, Integer> entry : docTableCounts.entrySet()) {
            String registryName = DISPLAY_TO_REGISTRY_INTEGRATION.get(entry.getKey());
            if (registryName == null) {
                continue; // e.g. "Other AI Providers" row, which has no fixed count
            }
            Long actual = actualCounts.get(registryName);
            assertTrue(actual != null && actual > 0,
                    "Integration '" + registryName + "' has no registered MCP tools");
            assertEquals(actual.intValue(), entry.getValue(),
                    "SKILL.md configuration table tool count for '" + entry.getKey() + "' does not match actual count");
        }
    }

    private static String readString(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Cannot read " + path, e);
        }
    }

    private static List<Integer> extractAll(Pattern pattern, String text) {
        return pattern.matcher(text).results()
                .map(mr -> Integer.parseInt(mr.group(1)))
                .toList();
    }

    private static java.util.Optional<Integer> extractFirst(Pattern pattern, String text) {
        return pattern.matcher(text).results()
                .findFirst()
                .map(mr -> Integer.parseInt(mr.group(1)));
    }
}
