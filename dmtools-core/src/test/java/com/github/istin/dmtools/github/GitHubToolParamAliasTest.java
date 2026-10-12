// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.github;

import com.github.istin.dmtools.common.code.model.SourceCodeConfig;
import com.github.istin.dmtools.mcp.generated.MCPToolExecutor;
import com.github.istin.dmtools.common.networking.GenericRequest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * Goes through the real generated {@code MCPToolExecutor} (the path JS agents and the CLI use) to pin which
 * argument names the GitHub issue tools accept. Agents address an issue by {@code number} on every GitHub tool;
 * a tool that only knows {@code pullRequestId} silently drops it and fails with "Issue reference requires
 * owner/repo/number".
 */
public class GitHubToolParamAliasTest {

    private BasicGithub gitHub;
    private Map<String, Object> clients;

    @Before
    public void setUp() throws IOException {
        SourceCodeConfig config = SourceCodeConfig.builder()
                .path("https://api.github.com")
                .auth("token")
                .workspaceName("defaultOwner")
                .repoName("defaultRepo")
                .branchName("main")
                .type(SourceCodeConfig.Type.GITHUB)
                .build();
        gitHub = spy(new BasicGithub(config));
        clients = new HashMap<>();
        clients.put("github", gitHub);
    }

    private String lastPostedUrl() throws IOException {
        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitHub).post(captor.capture());
        return captor.getValue().url();
    }

    @Test
    public void createCommentAcceptsNumberAsTheIssueNumber() throws Exception {
        doReturn("{}").when(gitHub).post(any(GenericRequest.class));
        Map<String, Object> args = new HashMap<>();
        args.put("workspace", "acme");
        args.put("repository", "widgets");
        args.put("number", 7);
        args.put("body", "hi");

        MCPToolExecutor.executeTool("github_create_comment", args, clients);

        assertEquals("https://api.github.com/repos/acme/widgets/issues/7/comments", lastPostedUrl());
    }

    @Test
    public void createCommentStillAcceptsPullRequestId() throws Exception {
        doReturn("{}").when(gitHub).post(any(GenericRequest.class));
        Map<String, Object> args = new HashMap<>();
        args.put("workspace", "acme");
        args.put("repository", "widgets");
        args.put("pullRequestId", "9");
        args.put("text", "hi");

        MCPToolExecutor.executeTool("github_create_comment", args, clients);

        assertEquals("https://api.github.com/repos/acme/widgets/issues/9/comments", lastPostedUrl());
    }

    @Test
    public void labelToolsHonourWorkspaceAndRepositoryInsteadOfTheDefaultRepository() throws Exception {
        doReturn("[]").when(gitHub).post(any(GenericRequest.class));
        Map<String, Object> args = new HashMap<>();
        args.put("workspace", "acme");
        args.put("repository", "widgets");
        args.put("number", 12);
        args.put("labels", new String[]{"bug"});

        MCPToolExecutor.executeTool("github_add_labels", args, clients);

        assertEquals("https://api.github.com/repos/acme/widgets/issues/12/labels", lastPostedUrl());
    }

    @Test
    public void ownerRepoAndWorkspaceRepositoryAreInterchangeable() throws Exception {
        doReturn("{}").when(gitHub).post(any(GenericRequest.class));
        Map<String, Object> args = new HashMap<>();
        args.put("owner", "acme");
        args.put("repo", "widgets");
        args.put("pullRequestId", "4");
        args.put("body", "x");

        MCPToolExecutor.executeTool("github_create_comment", args, clients);

        assertEquals("https://api.github.com/repos/acme/widgets/issues/4/comments", lastPostedUrl());
    }

    private Map<String, Object> ref(String ownerKey, String repoKey, String numberKey, Object number) {
        Map<String, Object> args = new HashMap<>();
        args.put(ownerKey, "acme");
        args.put(repoKey, "widgets");
        args.put(numberKey, number);
        return args;
    }

    @Test
    public void everyIssueToolAcceptsBothRepositoryNamingsAndEveryNumberSpelling() throws Exception {
        String[][] owners = {{"owner", "repo"}, {"workspace", "repository"}};
        String[] numbers = {"number", "issueNumber", "pullRequestId"};
        for (String[] pair : owners) {
            for (String numberKey : numbers) {
                doReturn("{}").when(gitHub).post(any(GenericRequest.class));
                doReturn("{}").when(gitHub).execute(any(GenericRequest.class));
                doReturn("{}").when(gitHub).delete(any(GenericRequest.class));

                Map<String, Object> labels = ref(pair[0], pair[1], numberKey, 5);
                labels.put("labels", new String[]{"a"});
                MCPToolExecutor.executeTool("github_add_labels", labels, clients);

                Map<String, Object> remove = ref(pair[0], pair[1], numberKey, 5);
                remove.put("label", "a");
                MCPToolExecutor.executeTool("github_remove_label", remove, clients);

                Map<String, Object> comment = ref(pair[0], pair[1], numberKey, 5);
                comment.put("body", "x");
                MCPToolExecutor.executeTool("github_create_comment", comment, clients);
            }
        }
        ArgumentCaptor<GenericRequest> posts = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitHub, org.mockito.Mockito.atLeastOnce()).post(posts.capture());
        for (GenericRequest r : posts.getAllValues()) {
            assertTrue(r.url(), r.url().contains("/repos/acme/widgets/issues/5/"));
        }
        // add_labels + create_comment per combination (2 repository namings x 3 number spellings)
        assertEquals(12, posts.getAllValues().size());
        ArgumentCaptor<GenericRequest> deletes = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitHub, org.mockito.Mockito.atLeastOnce()).delete(deletes.capture());
        for (GenericRequest r : deletes.getAllValues()) {
            assertEquals("https://api.github.com/repos/acme/widgets/issues/5/labels/a", r.url());
        }
        assertEquals(6, deletes.getAllValues().size());
    }

    @Test
    public void getIssueAndGetPrCommentsAcceptOwnerRepoAndNumber() throws Exception {
        doReturn("{\"number\":3,\"title\":\"t\"}").when(gitHub).execute(any(GenericRequest.class));
        Map<String, Object> args = ref("owner", "repo", "number", 3);

        MCPToolExecutor.executeTool("github_get_issue", args, clients);

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitHub, org.mockito.Mockito.atLeastOnce()).execute(captor.capture());
        assertTrue(captor.getValue().url(), captor.getValue().url().contains("/repos/acme/widgets/issues/3"));
    }

    @Test
    public void existingCanonicalNamesAreUnchanged() throws Exception {
        doReturn("[]").when(gitHub).post(any(GenericRequest.class));
        Map<String, Object> args = ref("owner", "repo", "number", 8);
        args.put("labels", new String[]{"x"});

        MCPToolExecutor.executeTool("github_add_labels", args, clients);

        assertEquals("https://api.github.com/repos/acme/widgets/issues/8/labels", lastPostedUrl());
    }
}
