package com.github.istin.dmtools.github;

import com.github.istin.dmtools.common.code.model.SourceCodeConfig;
import okhttp3.Request;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

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
}
