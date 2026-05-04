// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.github;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class GitHubRepositoryDiscoverabilityTest {

    @Test
    public void testLoadDefaultReturnsCanonicalRepositoryCopy() {
        GitHubRepositoryDiscoverability metadata = GitHubRepositoryDiscoverability.loadDefault();

        assertEquals(
                "Enterprise dark-factory orchestrator for automating delivery workflows across trackers, source control, documentation, design systems, AI providers, and CI/CD.",
                metadata.getShortDescription()
        );
        assertEquals(
                "DMTools is the orchestration layer for enterprise dark factories: a self-hosted CLI with MCP tools, jobs, and AI agents for delivery workflows across Jira, GitHub, Azure DevOps, documentation, design systems, and CI/CD.",
                metadata.getAboutText()
        );
    }

    @Test
    public void testCanonicalTopicsKeepEnterpriseTermsAheadOfAiDiscoveryTerms() {
        GitHubRepositoryDiscoverability metadata = GitHubRepositoryDiscoverability.loadDefault();

        assertArrayEquals(
                new String[]{
                        "dark-factory",
                        "delivery-automation",
                        "workflow-orchestration",
                        "platform-engineering",
                        "self-hosted",
                        "enterprise-ai",
                        "mcp",
                        "ai-agents",
                        "generative-ai",
                        "workflow-automation"
                },
                metadata.getTopics()
        );
    }

    @Test
    public void testCanonicalTopicsExcludeVendorSpecificKeywordsWhileSupportingKeywordsRetainThem() {
        GitHubRepositoryDiscoverability metadata = GitHubRepositoryDiscoverability.loadDefault();
        List<String> canonicalTopics = Arrays.asList(metadata.getTopics());
        List<String> supportingKeywords = Arrays.asList(metadata.getSupportingKeywords());

        assertFalse(canonicalTopics.contains("cursor"));
        assertFalse(canonicalTopics.contains("claude"));
        assertFalse(canonicalTopics.contains("codex"));
        assertFalse(canonicalTopics.contains("jira"));
        assertFalse(canonicalTopics.contains("azure-devops"));
        assertFalse(canonicalTopics.contains("github"));

        assertTrue(supportingKeywords.contains("cursor"));
        assertTrue(supportingKeywords.contains("claude"));
        assertTrue(supportingKeywords.contains("codex"));
        assertTrue(supportingKeywords.contains("jira"));
        assertTrue(supportingKeywords.contains("azure-devops"));
        assertTrue(supportingKeywords.contains("github"));
    }

    @Test
    public void testSocialPreviewGuidanceMatchesApprovedDirection() {
        GitHubRepositoryDiscoverability metadata = GitHubRepositoryDiscoverability.loadDefault();

        assertEquals(
                "Dark hero card with the DMTools wordmark, one short value line, and a subtle orchestration or industrial background.",
                metadata.getSocialPreviewDirection()
        );
        assertEquals("Enterprise dark-factory orchestrator", metadata.getSocialPreviewValueLine());
        assertArrayEquals(
                new String[]{
                        "Use a dark, high-contrast base with light text and no dense UI screenshots.",
                        "Keep the composition minimal: product name plus one short positioning line only.",
                        "Prefer one restrained accent colour and preserve WCAG AA contrast for any text in the asset.",
                        "Do not use integration collages or feature-list text in the preview image."
                },
                metadata.getSocialPreviewStyleRules()
        );
    }

    @Test
    public void testRepoBackedGitHubSurfacesStayAlignedWithCanonicalMetadata() throws IOException {
        GitHubRepositoryDiscoverability metadata = GitHubRepositoryDiscoverability.loadDefault();
        Path repositoryRoot = findRepositoryRoot();

        String readme = readRepositoryFile(repositoryRoot, "README.md");
        String contributing = readRepositoryFile(repositoryRoot, "CONTRIBUTING.md");
        String playbook = readRepositoryFile(
                repositoryRoot,
                "dmtools-ai-docs/references/workflows/github-repository-discoverability-playbook.md"
        );
        String bugReportTemplate = readRepositoryFile(repositoryRoot, ".github/ISSUE_TEMPLATE/bug_report.md");
        String featureRequestTemplate = readRepositoryFile(repositoryRoot, ".github/ISSUE_TEMPLATE/feature_request.md");
        String releaseWorkflow = readRepositoryFile(repositoryRoot, ".github/workflows/release.yml");
        String releaseAiSkillWorkflow = readRepositoryFile(repositoryRoot, ".github/workflows/release-ai-skill.yml");

        assertTrue(readme.contains("\n" + metadata.getShortDescription() + "\n"));
        assertTrue(contributing.contains("dmtools-ai-docs/references/workflows/github-repository-discoverability-playbook.md"));

        assertTrue(playbook.contains(metadata.getShortDescription()));
        assertTrue(playbook.contains(metadata.getAboutText()));
        assertTrue(playbook.contains(formatMarkdownList(metadata.getTopics())));
        assertTrue(playbook.contains(formatMarkdownList(metadata.getSupportingKeywords())));

        assertTrue(bugReportTemplate.contains("about: " + metadata.getBugReportAbout()));
        assertTrue(featureRequestTemplate.contains("about: " + metadata.getFeatureRequestAbout()));

        assertTrue(releaseWorkflow.contains(metadata.getReleaseNotesLead()));
        assertTrue(releaseWorkflow.contains(metadata.getReleaseSkillBullet()));

        assertTrue(releaseAiSkillWorkflow.contains("\"description\": \"" + metadata.getReleaseSkillPackageDescription() + "\""));
        assertTrue(releaseAiSkillWorkflow.contains("\"keywords\": " + formatInlineJsonArray(metadata.getSupportingKeywords())));
        assertTrue(releaseAiSkillWorkflow.contains(metadata.getReleaseSkillNotesLead()));
    }

    private Path findRepositoryRoot() {
        Path current = Paths.get("").toAbsolutePath();
        while (current != null && !Files.exists(current.resolve("settings.gradle"))) {
            current = current.getParent();
        }
        assertNotNull("Repository root not found", current);
        return current;
    }

    private String readRepositoryFile(Path repositoryRoot, String relativePath) throws IOException {
        return Files.readString(repositoryRoot.resolve(relativePath), StandardCharsets.UTF_8);
    }

    private String formatMarkdownList(String[] values) {
        return Arrays.stream(values)
                .map(value -> "`" + value + "`")
                .collect(Collectors.joining(", "));
    }

    private String formatInlineJsonArray(String[] values) {
        return Arrays.stream(values)
                .map(value -> "\"" + value + "\"")
                .collect(Collectors.joining(", ", "[", "]"));
    }
}
