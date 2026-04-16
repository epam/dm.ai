// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.github;

import com.github.istin.dmtools.common.code.model.SourceCodeConfig;
import okhttp3.Request;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Unit tests for GitHub class that test functionality without external API calls.
 * Integration tests that require external API calls are in GitHubIntegrationTest.
 */
public class GitHubTest {

    private GitHub gitHub;
    private static final String BASE_PATH = "https://api.github.com";
    private static final String AUTHORIZATION = "token";

    @Before
    public void setUp() throws IOException {
        // Create a test configuration
        SourceCodeConfig config = SourceCodeConfig.builder()
                .path(BASE_PATH)
                .auth(AUTHORIZATION)
                .workspaceName("testWorkspace")
                .repoName("testRepo")
                .branchName("main")
                .type(SourceCodeConfig.Type.GITHUB)
                .build();
        gitHub = new BasicGithub(config);
    }

    @Test
    public void testPath() {
        String path = "repos/test/repo";
        String expected = BASE_PATH + "/" + path;
        assertEquals(expected, gitHub.path(path));
    }

    @Test
    public void testSign() {
        Request.Builder builder = new Request.Builder();
        Request.Builder signedBuilder = gitHub.sign(builder);
        assertNotNull(signedBuilder);
    }

    @Test
    public void testGetPullRequestUrl() {
        String workspace = "testWorkspace";
        String repository = "testRepo";
        String id = "1";

        String url = gitHub.getPullRequestUrl(workspace, repository, id);
        String expected = "https://github.com/testWorkspace/testRepo/pull/1";
        assertEquals(expected, url);
    }

    @Test
    public void testGetDefaultRepository() {
        String repository = gitHub.getDefaultRepository();
        assertEquals("testRepo", repository);
    }

    @Test
    public void testGetDefaultBranch() {
        String branch = gitHub.getDefaultBranch();
        assertEquals("main", branch);
    }

    @Test
    public void testGetDefaultWorkspace() {
        String workspace = gitHub.getDefaultWorkspace();
        assertEquals("testWorkspace", workspace);
    }

    // ── parseUris tests ──────────────────────────────────────────────────────

    @Test
    public void testParseUris_plainUrl() throws Exception {
        String input = "See https://github.com/epam/dm.ai/blob/main/README.md for details.";
        Set<String> uris = gitHub.parseUris(input);
        assertEquals(1, uris.size());
        assertEquals("https://github.com/epam/dm.ai/blob/main/README.md", uris.iterator().next());
    }

    @Test
    public void testParseUris_smartLinkSuffix_doesNotPollutePath() throws Exception {
        // Jira/Confluence smart-link format:
        // [url|display-text|smart-link]
        String input = "[[https://github.com/epam/dm.ai/blob/main/agents/js/smAgent.js" +
                "|https://github.com/epam/dm.ai/blob/main/agents/js/smAgent.js|smart-link]]";
        Set<String> uris = gitHub.parseUris(input);
        assertEquals(1, uris.size());
        String parsed = uris.iterator().next();
        assertFalse("URL must not contain pipe character", parsed.contains("|"));
        assertEquals("https://github.com/epam/dm.ai/blob/main/agents/js/smAgent.js", parsed);
    }

    @Test
    public void testParseUris_multipleSmartLinks() throws Exception {
        String input = "See [[https://github.com/epam/dm.ai/blob/main/README.md" +
                "|https://github.com/epam/dm.ai/blob/main/README.md|smart-link]] " +
                "and [[https://github.com/epam/dm.ai/blob/main/CONTRIBUTING.md" +
                "|https://github.com/epam/dm.ai/blob/main/CONTRIBUTING.md|smart-link]]";
        Set<String> uris = gitHub.parseUris(input);
        assertEquals(2, uris.size());
        for (String uri : uris) {
            assertFalse("URL must not contain pipe character", uri.contains("|"));
        }
        assertTrue(uris.contains("https://github.com/epam/dm.ai/blob/main/README.md"));
        assertTrue(uris.contains("https://github.com/epam/dm.ai/blob/main/CONTRIBUTING.md"));
    }

    @Test
    public void testParseUris_emptyInput() throws Exception {
        Set<String> uris = gitHub.parseUris("");
        assertTrue(uris.isEmpty());
    }

    @Test
    public void testParseUris_nullInput() throws Exception {
        Set<String> uris = gitHub.parseUris(null);
        assertTrue(uris.isEmpty());
    }

    @Test
    public void testParseUris_noGithubUrls() throws Exception {
        Set<String> uris = gitHub.parseUris("Just some text without any GitHub links.");
        assertTrue(uris.isEmpty());
    }
}
