// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.github;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
}
