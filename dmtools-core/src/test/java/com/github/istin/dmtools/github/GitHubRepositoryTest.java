// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.github;

import com.github.istin.dmtools.common.code.model.SourceCodeConfig;
import com.github.istin.dmtools.common.networking.GenericRequest;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the GitHub repository content tools in {@link GitHubRepository}.
 * All HTTP is mocked via a spy; no external API calls are made.
 */
public class GitHubRepositoryTest {

    private GitHubRepository gitHub;

    @Before
    public void setUp() throws IOException {
        SourceCodeConfig config = SourceCodeConfig.builder()
                .path("https://api.github.com")
                .auth("token")
                .workspaceName("testWorkspace")
                .repoName("testRepo")
                .branchName("main")
                .type(SourceCodeConfig.Type.GITHUB)
                .build();
        gitHub = new BasicGithub(config);
    }

    private ArgumentCaptor<GenericRequest> verifyGet(GitHubRepository spy) throws Exception {
        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).execute(captor.capture());
        return captor;
    }

    @Test
    public void testGetRepoFetchesExpectedUrl() throws Exception {
        GitHubRepository spy = spy(gitHub);
        doReturn("{}").when(spy).execute(any(GenericRequest.class));
        spy.getRepo("acme", "widgets");
        assertEquals("https://api.github.com/repos/acme/widgets", verifyGet(spy).getValue().url());
    }

    @Test
    public void testGetTreeUsesRecursiveQuery() throws Exception {
        GitHubRepository spy = spy(gitHub);
        doReturn("{}").when(spy).execute(any(GenericRequest.class));
        spy.getTree("acme", "widgets", "main");
        assertEquals("https://api.github.com/repos/acme/widgets/git/trees/main?recursive=1",
                verifyGet(spy).getValue().url());
    }

    @Test
    public void testGetCodeownersFetchesExpectedUrl() throws Exception {
        GitHubRepository spy = spy(gitHub);
        doReturn("{}").when(spy).execute(any(GenericRequest.class));
        spy.getCodeowners("acme", "widgets");
        assertEquals("https://api.github.com/repos/acme/widgets/contents/.github/CODEOWNERS",
                verifyGet(spy).getValue().url());
    }

    @Test
    public void testListBranchesFetchesExpectedUrl() throws Exception {
        GitHubRepository spy = spy(gitHub);
        doReturn("[]").when(spy).execute(any(GenericRequest.class));
        spy.listBranches("acme", "widgets");
        assertEquals("https://api.github.com/repos/acme/widgets/branches", verifyGet(spy).getValue().url());
    }

    @Test
    public void testCreateBranchPostsRefAndSha() throws Exception {
        GitHubRepository spy = spy(gitHub);
        doReturn("{}").when(spy).post(any(GenericRequest.class));
        spy.createBranch("acme", "widgets", "feature/x", "abc123");

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).post(captor.capture());
        assertEquals("https://api.github.com/repos/acme/widgets/git/refs", captor.getValue().url());
        JSONObject body = new JSONObject(captor.getValue().getBody());
        assertEquals("refs/heads/feature/x", body.getString("ref"));
        assertEquals("abc123", body.getString("sha"));
    }

    @Test
    public void testDeleteBranchDeletesExpectedUrl() throws Exception {
        GitHubRepository spy = spy(gitHub);
        doReturn("").when(spy).delete(any(GenericRequest.class));
        spy.deleteBranch("acme", "widgets", "feature/x");

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).delete(captor.capture());
        assertEquals("https://api.github.com/repos/acme/widgets/git/refs/heads/feature/x",
                captor.getValue().url());
    }

    @Test
    public void testGetFileContentWithoutRef() throws Exception {
        GitHubRepository spy = spy(gitHub);
        doReturn("{}").when(spy).execute(any(GenericRequest.class));
        spy.getFileContent("acme", "widgets", "README.md", null);
        assertEquals("https://api.github.com/repos/acme/widgets/contents/README.md",
                verifyGet(spy).getValue().url());
    }

    @Test
    public void testGetFileContentWithRef() throws Exception {
        GitHubRepository spy = spy(gitHub);
        doReturn("{}").when(spy).execute(any(GenericRequest.class));
        spy.getFileContent("acme", "widgets", "README.md", "main");
        assertEquals("https://api.github.com/repos/acme/widgets/contents/README.md?ref=main",
                verifyGet(spy).getValue().url());
    }

    @Test
    public void testUpdateFileBase64EncodesContent() throws Exception {
        GitHubRepository spy = spy(gitHub);
        doReturn("{}").when(spy).put(any(GenericRequest.class));
        spy.updateFile("acme", "widgets", "README.md", "Hello world", "Update README", "sha123");

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).put(captor.capture());
        assertEquals("https://api.github.com/repos/acme/widgets/contents/README.md", captor.getValue().url());
        JSONObject body = new JSONObject(captor.getValue().getBody());
        assertEquals("Update README", body.getString("message"));
        assertEquals("sha123", body.getString("sha"));
        String decoded = new String(Base64.getDecoder().decode(body.getString("content")), StandardCharsets.UTF_8);
        assertEquals("Hello world", decoded);
    }

    @Test
    public void testGetCommitFetchesExpectedUrl() throws Exception {
        GitHubRepository spy = spy(gitHub);
        doReturn("{}").when(spy).execute(any(GenericRequest.class));
        spy.getCommit("acme", "widgets", "abc123");
        assertEquals("https://api.github.com/repos/acme/widgets/commits/abc123", verifyGet(spy).getValue().url());
    }

    @Test
    public void testListCommitsWithoutSha() throws Exception {
        GitHubRepository spy = spy(gitHub);
        doReturn("[]").when(spy).execute(any(GenericRequest.class));
        spy.listCommits("acme", "widgets", null);
        assertEquals("https://api.github.com/repos/acme/widgets/commits", verifyGet(spy).getValue().url());
    }

    @Test
    public void testListCommitsWithSha() throws Exception {
        GitHubRepository spy = spy(gitHub);
        doReturn("[]").when(spy).execute(any(GenericRequest.class));
        spy.listCommits("acme", "widgets", "main");
        assertEquals("https://api.github.com/repos/acme/widgets/commits?sha=main", verifyGet(spy).getValue().url());
    }

    @Test
    public void testListReleasesFetchesExpectedUrl() throws Exception {
        GitHubRepository spy = spy(gitHub);
        doReturn("[]").when(spy).execute(any(GenericRequest.class));
        spy.listReleases("acme", "widgets");
        assertEquals("https://api.github.com/repos/acme/widgets/releases", verifyGet(spy).getValue().url());
    }

    @Test
    public void testGetReleaseFetchesExpectedUrl() throws Exception {
        GitHubRepository spy = spy(gitHub);
        doReturn("{}").when(spy).execute(any(GenericRequest.class));
        spy.getRelease("acme", "widgets", "v1.0.0");
        assertEquals("https://api.github.com/repos/acme/widgets/releases/tags/v1.0.0", verifyGet(spy).getValue().url());
    }

    @Test
    public void testCreateReleasePostsTagAndOmitsBlankBody() throws Exception {
        GitHubRepository spy = spy(gitHub);
        doReturn("{}").when(spy).post(any(GenericRequest.class));
        spy.createRelease("acme", "widgets", "v1.0.0", "  ");

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).post(captor.capture());
        assertEquals("https://api.github.com/repos/acme/widgets/releases", captor.getValue().url());
        JSONObject body = new JSONObject(captor.getValue().getBody());
        assertEquals("v1.0.0", body.getString("tag_name"));
        assertFalse(body.has("body"));
    }

    @Test
    public void testAddCollaboratorPutsPermission() throws Exception {
        GitHubRepository spy = spy(gitHub);
        doReturn("{}").when(spy).put(any(GenericRequest.class));
        spy.addCollaborator("acme", "widgets", "octocat", "push");

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).put(captor.capture());
        assertEquals("https://api.github.com/repos/acme/widgets/collaborators/octocat", captor.getValue().url());
        assertEquals("push", new JSONObject(captor.getValue().getBody()).getString("permission"));
    }

    @Test
    public void testRemoveCollaboratorDeletesExpectedUrl() throws Exception {
        GitHubRepository spy = spy(gitHub);
        doReturn("").when(spy).delete(any(GenericRequest.class));
        spy.removeCollaborator("acme", "widgets", "octocat");

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).delete(captor.capture());
        assertEquals("https://api.github.com/repos/acme/widgets/collaborators/octocat", captor.getValue().url());
    }
}
