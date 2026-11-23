// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.gitlab;

import com.github.istin.dmtools.common.networking.GenericRequest;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the GitLab issue tool family in {@link GitLabIssues}.
 * All HTTP is mocked via a spy; no external API calls are made.
 */
public class GitLabIssuesTest {

    private GitLabIssues gitLab;

    @Before
    public void setUp() throws IOException {
        gitLab = spy(new GitLabTrackerClient("https://gitlab.example", "token", "group", "project"));
    }

    // ── key resolution ──────────────────────────────────────────────────────

    @Test
    public void testGetIssueResolvesRouterKeyAgainstDefaultProject() throws Exception {
        doReturn("{\"iid\":2,\"title\":\"Guest login\",\"state\":\"opened\"}")
                .when(gitLab).execute(any(GenericRequest.class));

        gitLab.getIssue(null, null, null, "gl-2");

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitLab).execute(captor.capture());
        assertEquals("https://gitlab.example/api/v4/projects/group%2Fproject/issues/2",
                captor.getValue().url());
    }

    @Test
    public void testGetIssueResolvesMultiSegmentCompositeKey() throws Exception {
        doReturn("{\"iid\":42}").when(gitLab).execute(any(GenericRequest.class));

        gitLab.getIssue(null, null, null, "group/subgroup/project#42");

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitLab).execute(captor.capture());
        assertEquals("https://gitlab.example/api/v4/projects/group%2Fsubgroup%2Fproject/issues/42",
                captor.getValue().url());
    }

    @Test
    public void testGetIssueExplicitPartsWinOverKey() throws Exception {
        doReturn("{\"iid\":5}").when(gitLab).execute(any(GenericRequest.class));

        gitLab.getIssue("other", "repo", "5", "gl-2");

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitLab).execute(captor.capture());
        assertEquals("https://gitlab.example/api/v4/projects/other%2Frepo/issues/5",
                captor.getValue().url());
    }

    @Test
    public void testGetIssueResolvesBareIidKey() throws Exception {
        doReturn("{\"iid\":9}").when(gitLab).execute(any(GenericRequest.class));

        gitLab.getIssue(null, null, null, "9");

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitLab).execute(captor.capture());
        assertEquals("https://gitlab.example/api/v4/projects/group%2Fproject/issues/9",
                captor.getValue().url());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetIssueRejectsUnparseableKey() throws Exception {
        gitLab.getIssue(null, null, null, "EPMCSRCSRD-113");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetIssueRequiresAnyReference() throws Exception {
        gitLab.getIssue(null, null, null, null);
    }

    // ── labels ──────────────────────────────────────────────────────────────

    @Test
    public void testAddIssueLabelPutsAddLabels() throws Exception {
        doReturn("{}").when(gitLab).put(any(GenericRequest.class));

        gitLab.addIssueLabel("ai_questions_asked", null, null, null, "gl-2");

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitLab).put(captor.capture());
        assertEquals("https://gitlab.example/api/v4/projects/group%2Fproject/issues/2",
                captor.getValue().url());
        assertEquals("ai_questions_asked",
                new JSONObject(captor.getValue().getBody()).getString("add_labels"));
    }

    @Test
    public void testRemoveIssueLabelPutsRemoveLabels() throws Exception {
        doReturn("{}").when(gitLab).put(any(GenericRequest.class));

        gitLab.removeIssueLabel("ai_questions_asked", "group", "project", "2", null);

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitLab).put(captor.capture());
        assertEquals("ai_questions_asked",
                new JSONObject(captor.getValue().getBody()).getString("remove_labels"));
    }

    // ── status ──────────────────────────────────────────────────────────────

    @Test
    public void testMoveIssueToDoneClosesIssue() throws Exception {
        doReturn("{}").when(gitLab).put(any(GenericRequest.class));

        gitLab.moveIssueToStatus("Done", null, null, null, "gl-2");

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitLab).put(captor.capture());
        assertEquals("close", new JSONObject(captor.getValue().getBody()).getString("state_event"));
    }

    @Test
    public void testMoveIssueToReopenedReopensIssue() throws Exception {
        doReturn("{}").when(gitLab).put(any(GenericRequest.class));

        gitLab.moveIssueToStatus("Reopened", null, null, null, "gl-2");

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitLab).put(captor.capture());
        assertEquals("reopen", new JSONObject(captor.getValue().getBody()).getString("state_event"));
    }

    @Test
    public void testMoveIssueToCustomStatusAddsScopedLabel() throws Exception {
        doReturn("{}").when(gitLab).put(any(GenericRequest.class));

        gitLab.moveIssueToStatus("In Development", null, null, null, "gl-2");

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitLab).put(captor.capture());
        assertEquals("status::In Development",
                new JSONObject(captor.getValue().getBody()).getString("add_labels"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMoveIssueToStatusRequiresStatus() throws Exception {
        gitLab.moveIssueToStatus(" ", null, null, null, "gl-2");
    }

    // ── open/close ──────────────────────────────────────────────────────────

    @Test
    public void testCloseIssueSendsStateEventClose() throws Exception {
        doReturn("{}").when(gitLab).put(any(GenericRequest.class));

        gitLab.closeIssue(null, null, "7", null);

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitLab).put(captor.capture());
        assertEquals("close", new JSONObject(captor.getValue().getBody()).getString("state_event"));
    }

    @Test
    public void testReopenIssueSendsStateEventReopen() throws Exception {
        doReturn("{}").when(gitLab).put(any(GenericRequest.class));

        gitLab.reopenIssue(null, null, "7", null);

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitLab).put(captor.capture());
        assertEquals("reopen", new JSONObject(captor.getValue().getBody()).getString("state_event"));
    }

    // ── comments ────────────────────────────────────────────────────────────

    @Test
    public void testCreateIssueCommentPostsNote() throws Exception {
        doReturn("{\"id\":1}").when(gitLab).post(any(GenericRequest.class));

        gitLab.createIssueComment(null, null, null, "Question posted", "gl-2");

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitLab).post(captor.capture());
        assertEquals("https://gitlab.example/api/v4/projects/group%2Fproject/issues/2/notes",
                captor.getValue().url());
        assertEquals("Question posted",
                new JSONObject(captor.getValue().getBody()).getString("body"));
    }

    @Test
    public void testGetIssueCommentsReturnsNotesArray() throws Exception {
        doReturn("[{\"id\":1,\"body\":\"first\"},{\"id\":2,\"body\":\"second\"}]")
                .when(gitLab).execute(any(GenericRequest.class));

        String result = gitLab.getIssueComments(null, null, null, "gl-2");

        assertTrue(result.contains("\"first\""));
        assertTrue(result.contains("\"second\""));
    }

    // ── create / search / assign ────────────────────────────────────────────

    @Test
    public void testCreateIssuePostsTitleAndDescription() throws Exception {
        doReturn("{\"iid\":10}").when(gitLab).post(any(GenericRequest.class));

        gitLab.createIssue("group", "project", "New bug", "It broke", null);

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitLab).post(captor.capture());
        assertEquals("https://gitlab.example/api/v4/projects/group%2Fproject/issues",
                captor.getValue().url());
        JSONObject body = new JSONObject(captor.getValue().getBody());
        assertEquals("New bug", body.getString("title"));
        assertEquals("It broke", body.getString("description"));
    }

    @Test
    public void testCreateIssueOmitsBlankDescription() throws Exception {
        doReturn("{\"iid\":11}").when(gitLab).post(any(GenericRequest.class));

        gitLab.createIssue(null, null, "Title only", "   ", "group/project");

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitLab).post(captor.capture());
        JSONObject body = new JSONObject(captor.getValue().getBody());
        assertEquals("Title only", body.getString("title"));
        assertFalse(body.has("description"));
    }

    @Test
    public void testSearchIssuesUsesProjectScopedSearch() throws Exception {
        doReturn("[]").when(gitLab).execute(any(GenericRequest.class));

        gitLab.searchIssues("login fails", null, null, "opened");

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitLab).execute(captor.capture());
        String url = captor.getValue().url();
        assertTrue(url.startsWith("https://gitlab.example/api/v4/projects/group%2Fproject/issues"));
        assertTrue(url.contains("state=opened"));
        assertTrue(url.contains("search=login"));
    }

    @Test
    public void testAssignIssueLooksUpUserAndAssigns() throws Exception {
        doReturn("[{\"id\":99,\"username\":\"jsmith\"}]")
                .when(gitLab).execute(any(GenericRequest.class));
        doReturn("{}").when(gitLab).put(any(GenericRequest.class));

        gitLab.assignIssue("jsmith", null, null, null, "gl-2");

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitLab).put(captor.capture());
        assertEquals(99, new JSONObject(captor.getValue().getBody())
                .getJSONArray("assignee_ids").getInt(0));
    }

    @Test(expected = IOException.class)
    public void testAssignIssueFailsForUnknownUser() throws Exception {
        doReturn("[]").when(gitLab).execute(any(GenericRequest.class));

        gitLab.assignIssue("ghost", null, null, null, "gl-2");
    }
}
