// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.gitlab;

import com.github.istin.dmtools.common.model.*;
import com.github.istin.dmtools.gitlab.model.GitLabPullRequest;
import com.github.istin.dmtools.gitlab.model.GitLabComment;
import com.github.istin.dmtools.gitlab.model.GitLabTag;
import com.github.istin.dmtools.gitlab.model.GitLabProject;
import com.github.istin.dmtools.gitlab.model.GitLabCommit;
import com.github.istin.dmtools.gitlab.model.GitLabFile;
import com.github.istin.dmtools.gitlab.model.GitLabJob;
import com.github.istin.dmtools.common.networking.GenericRequest;
import okhttp3.Request;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.ArgumentCaptor;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Calendar;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class GitLabTest {

    private GitLab gitLab;
    private final String basePath = "http://example.com";

    @Before
    public void setUp() throws Exception {
        gitLab = Mockito.mock(GitLab.class, Mockito.CALLS_REAL_METHODS);
        doReturn(basePath).when(gitLab).getBasePath();
        Field authField = com.github.istin.dmtools.networking.AbstractRestClient.class.getDeclaredField("authorization");
        authField.setAccessible(true);
        authField.set(gitLab, "test-token");
    }

    @Test
    public void testPath() {
        String expectedPath = basePath + "/api/v4/testPath";
        assertEquals(expectedPath, gitLab.path("testPath"));
    }

    @Test
    public void testSign() {
        Request.Builder builder = new Request.Builder();
        Request.Builder signedBuilder = gitLab.sign(builder);
        assertNotNull(signedBuilder);
    }


    @Test
    public void testAddPullRequestLabel() throws IOException {
        doReturn("{\"iid\":1,\"labels\":[\"label\"]}").when(gitLab).put(any());
        gitLab.addPullRequestLabel("workspace", "repository", "1", "label");

        ArgumentCaptor<GenericRequest> requestCaptor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitLab, times(1)).put(requestCaptor.capture());
        JSONObject body = new JSONObject(requestCaptor.getValue().getBody());
        assertEquals("label", body.getString("add_labels"));
    }

    @Test
    public void testRemovePullRequestLabel() throws IOException {
        doReturn("{\"iid\":1,\"labels\":[]}").when(gitLab).put(any());
        gitLab.removePullRequestLabel("workspace", "repository", "1", "label");

        ArgumentCaptor<GenericRequest> requestCaptor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitLab, times(1)).put(requestCaptor.capture());
        JSONObject body = new JSONObject(requestCaptor.getValue().getBody());
        assertEquals("label", body.getString("remove_labels"));
    }


    @Test
    public void testPullRequestTasks() throws IOException {
        try {
            gitLab.pullRequestTasks("workspace", "repository", "1");
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // Expected exception
        }
    }

    @Test
    public void testAddTask() throws IOException {
        try {
            gitLab.addTask(1, "task");
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // Expected exception
        }
    }

    @Test
    public void testCreatePullRequestCommentAndTaskIfNotExists() throws IOException {
        try {
            gitLab.createPullRequestCommentAndTaskIfNotExists("workspace", "repository", "1", "comment", "task");
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // Expected exception
        }
    }

    @Test
    public void testCreatePullRequestCommentAndTask() throws IOException {
        try {
            gitLab.createPullRequestCommentAndTask("workspace", "repository", "1", "comment", "task");
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // Expected exception
        }
    }

    @Test
    public void testGetDefaultRepository() {
        try {
            gitLab.getDefaultRepository();
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // Expected exception
        }
    }

    @Test
    public void testGetDefaultBranch() {
        try {
            gitLab.getDefaultBranch();
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // Expected exception
        }
    }

    @Test
    public void testGetDefaultWorkspace() {
        try {
            gitLab.getDefaultWorkspace();
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // Expected exception
        }
    }


    @Test
    public void testGetPullRequestUrl() {
        String url = gitLab.getPullRequestUrl("workspace", "repository", "1");
        assertNotNull(url);
    }

    @Test
    public void testPullRequestCommentsIncludesAllNonSystemNotes() throws IOException {
        JSONArray notes = new JSONArray();

        JSONObject diffNote = new JSONObject();
        diffNote.put("id", 1);
        diffNote.put("body", "inline comment");
        diffNote.put("type", "DiffNote");
        diffNote.put("system", false);
        diffNote.put("author", new JSONObject().put("id", 10).put("username", "user1"));

        JSONObject regularNote = new JSONObject();
        regularNote.put("id", 2);
        regularNote.put("body", "general comment");
        regularNote.put("type", JSONObject.NULL);
        regularNote.put("system", false);
        regularNote.put("author", new JSONObject().put("id", 10).put("username", "user1"));

        JSONObject systemNote = new JSONObject();
        systemNote.put("id", 3);
        systemNote.put("body", "mentioned in...");
        systemNote.put("type", JSONObject.NULL);
        systemNote.put("system", true);
        systemNote.put("author", new JSONObject().put("id", 10).put("username", "system"));

        notes.put(diffNote);
        notes.put(regularNote);
        notes.put(systemNote);

        doReturn(notes.toString()).when(gitLab).execute(any(GenericRequest.class));
        List<IComment> comments = gitLab.pullRequestComments("workspace", "repo", "1");
        assertEquals("Should include DiffNote and regular note, exclude system", 2, comments.size());
    }

    @Test
    public void testPullRequestCommentsPaginatesNotes() throws IOException {
        JSONArray firstPage = new JSONArray();
        for (int i = 1; i <= 100; i++) {
            firstPage.put(createNonSystemNote(i, "comment " + i));
        }
        JSONArray secondPage = new JSONArray();
        secondPage.put(createNonSystemNote(101, "comment 101"));

        doReturn(firstPage.toString(), secondPage.toString()).when(gitLab).execute(any(GenericRequest.class));
        List<IComment> comments = gitLab.pullRequestComments("workspace", "repo", "1");

        assertEquals(101, comments.size());
        ArgumentCaptor<GenericRequest> requestCaptor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitLab, times(2)).execute(requestCaptor.capture());
        List<GenericRequest> requests = requestCaptor.getAllValues();
        assertTrue(requests.get(0).url().contains("page=1"));
        assertTrue(requests.get(1).url().contains("page=2"));
    }

    @Test
    public void testPullRequestActivitiesReturnsAllNonSystemNotes() throws IOException {
        JSONArray notes = new JSONArray();

        JSONObject approvedNote = new JSONObject();
        approvedNote.put("id", 1);
        approvedNote.put("body", "approved");
        approvedNote.put("type", JSONObject.NULL);
        approvedNote.put("system", false);
        approvedNote.put("author", new JSONObject().put("id", 10).put("username", "user1"));

        JSONObject commentNote = new JSONObject();
        commentNote.put("id", 2);
        commentNote.put("body", "looks good");
        commentNote.put("type", JSONObject.NULL);
        commentNote.put("system", false);
        commentNote.put("author", new JSONObject().put("id", 10).put("username", "user1"));

        JSONObject systemNote = new JSONObject();
        systemNote.put("id", 3);
        systemNote.put("body", "system event");
        systemNote.put("type", JSONObject.NULL);
        systemNote.put("system", true);
        systemNote.put("author", new JSONObject().put("id", 0).put("username", "system"));

        notes.put(approvedNote);
        notes.put(commentNote);
        notes.put(systemNote);

        doReturn(notes.toString()).when(gitLab).execute(any(GenericRequest.class));
        List<IActivity> activities = gitLab.pullRequestActivities("workspace", "repo", "1");
        assertEquals("Should return 2 activities (excluding system note)", 2, activities.size());
        assertEquals("APPROVED", activities.get(0).getAction());
        assertEquals("COMMENTED", activities.get(1).getAction());
    }

    @Test
    public void testAddPullRequestComment() throws IOException {
        doReturn("{\"id\": 1, \"body\": \"test comment\"}").when(gitLab).post(any());
        String result = gitLab.addPullRequestComment("workspace", "repo", "1", "test comment");
        assertNotNull(result);
        verify(gitLab, times(1)).post(any());
    }

    @Test
    public void testGetPRDiscussions() throws IOException {
        JSONArray firstPage = new JSONArray();
        for (int i = 1; i <= 100; i++) {
            JSONObject disc = new JSONObject();
            disc.put("id", "abc" + i);
            disc.put("individual_note", false);
            firstPage.put(disc);
        }
        JSONArray secondPage = new JSONArray();
        secondPage.put(new JSONObject().put("id", "abc101").put("individual_note", false));

        doReturn(firstPage.toString(), secondPage.toString()).when(gitLab).execute(any(GenericRequest.class));
        String result = gitLab.getPRDiscussions("workspace", "repo", "1");
        assertNotNull(result);
        JSONArray parsed = new JSONArray(result);
        assertEquals(101, parsed.length());
        verify(gitLab, times(2)).execute(any(GenericRequest.class));
    }

    @Test
    public void testReplyToPullRequestComment() throws IOException {
        doReturn("{\"id\": 10, \"body\": \"reply text\"}").when(gitLab).post(any());
        String result = gitLab.replyToPullRequestComment("workspace", "repo", "1", "disc123", "reply text");
        assertNotNull(result);
        verify(gitLab, times(1)).post(any());
    }

    @Test
    public void testAddInlineReviewComment() throws IOException {
        doReturn("{\"id\": 20, \"body\": \"inline review\"}").when(gitLab).post(any());
        String result = gitLab.addInlineReviewComment("workspace", "repo", "1",
                "src/Foo.java", "10", "This is wrong", "baseSha1", "headSha1", "startSha1");
        assertNotNull(result);
        verify(gitLab, times(1)).post(any());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddInlineReviewCommentInvalidLine() throws IOException {
        gitLab.addInlineReviewComment("workspace", "repo", "1",
                "src/Foo.java", "notanumber", "comment", "base", "head", "start");
    }

    @Test
    public void testResolveReviewThread() throws IOException {
        doReturn("{\"id\": \"disc123\", \"resolved\": true}").when(gitLab).put(any());
        String result = gitLab.resolveReviewThread("workspace", "repo", "1", "disc123");
        assertNotNull(result);
        verify(gitLab, times(1)).put(any());
    }

    @Test
    public void testApproveMergeRequest() throws IOException {
        doReturn("{\"id\": 1, \"approved_by\": [{\"user\": {\"username\": \"testuser\"}}]}").when(gitLab).post(any());
        String result = gitLab.approveMergeRequest("workspace", "repo", "1");
        assertNotNull(result);
        verify(gitLab, times(1)).post(any());
    }

    @Test
    public void testMergeMergeRequestWithMessage() throws IOException {
        doReturn("{\"iid\": 1, \"state\": \"merged\", \"title\": \"Test MR\"}").when(gitLab).put(any());
        String result = gitLab.mergeMergeRequest("workspace", "repo", "1", "Custom merge commit message");
        assertNotNull(result);
        verify(gitLab, times(1)).put(any());
    }

    @Test
    public void testMergeMergeRequestWithoutMessage() throws IOException {
        doReturn("{\"iid\": 1, \"state\": \"merged\", \"title\": \"Test MR\"}").when(gitLab).put(any());
        String result = gitLab.mergeMergeRequest("workspace", "repo", "1", null);
        assertNotNull(result);
        verify(gitLab, times(1)).put(any());
    }

    @Test
    public void testCreateMergeRequestWithOptionalFields() throws IOException {
        doReturn("{\"iid\": 99, \"web_url\": \"https://git.epam.com/group/repo/-/merge_requests/99\"}")
                .when(gitLab).post(any());
        String result = gitLab.createMergeRequest(
                "workspace", "repo", "feature/ABC-1", "main", "ABC-1 title", "Body", "true");
        assertNotNull(result);

        ArgumentCaptor<GenericRequest> requestCaptor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitLab, times(1)).post(requestCaptor.capture());
        JSONObject body = new JSONObject(requestCaptor.getValue().getBody());
        assertEquals("feature/ABC-1", body.getString("source_branch"));
        assertEquals("main", body.getString("target_branch"));
        assertEquals("ABC-1 title", body.getString("title"));
        assertEquals("Body", body.getString("description"));
        assertTrue(body.getBoolean("remove_source_branch"));
    }

    @Test
    public void testRebaseMergeRequest() throws IOException {
        doReturn("{\"rebase_in_progress\": true}").when(gitLab).put(any());
        String result = gitLab.rebaseMergeRequest("workspace", "repo", "42");
        assertNotNull(result);

        ArgumentCaptor<GenericRequest> requestCaptor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitLab, times(1)).put(requestCaptor.capture());
        assertEquals("{}", requestCaptor.getValue().getBody());
    }

    @Test
    public void testListPipelineRunsNormalizesFailureStatus() throws IOException {
        doReturn("[]").when(gitLab).execute(any(GenericRequest.class));
        String result = gitLab.listPipelineRuns("workspace", "repo", "failure", "main", "10");
        assertEquals("[]", result);

        ArgumentCaptor<GenericRequest> requestCaptor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitLab, times(1)).execute(requestCaptor.capture());
        String url = requestCaptor.getValue().url();
        assertTrue(url.contains("status=failed"));
        assertTrue(url.contains("ref=main"));
        assertTrue(url.contains("per_page=10"));
    }

    @Test
    public void testListPipelineRunsRespectsLimitAcrossPages() throws IOException {
        JSONArray firstPage = new JSONArray();
        for (int i = 0; i < 100; i++) {
            firstPage.put(new JSONObject().put("id", i + 1));
        }
        JSONArray secondPage = new JSONArray();
        for (int i = 0; i < 40; i++) {
            secondPage.put(new JSONObject().put("id", i + 101));
        }
        doReturn(firstPage.toString(), secondPage.toString()).when(gitLab).execute(any(GenericRequest.class));

        String result = gitLab.listPipelineRuns("workspace", "repo", "success", "main", "120");
        JSONArray parsed = new JSONArray(result);
        assertEquals(120, parsed.length());

        ArgumentCaptor<GenericRequest> requestCaptor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitLab, times(2)).execute(requestCaptor.capture());
        List<GenericRequest> requests = requestCaptor.getAllValues();
        assertTrue(requests.get(0).url().contains("per_page=100"));
        assertTrue(requests.get(0).url().contains("page=1"));
        assertTrue(requests.get(1).url().contains("page=2"));
    }

    @Test
    public void testGetPipelineJobsAndJobLogsReturnFallbacks() throws IOException {
        doReturn(null).when(gitLab).execute(any(GenericRequest.class));
        assertEquals("[]", gitLab.getPipelineJobs("workspace", "repo", "123"));
        assertEquals("", gitLab.getJobLogs("workspace", "repo", "456"));
        verify(gitLab, times(2)).execute(any(GenericRequest.class));
    }

    private JSONObject createNonSystemNote(int id, String body) {
        return new JSONObject()
                .put("id", id)
                .put("body", body)
                .put("type", JSONObject.NULL)
                .put("system", false)
                .put("author", new JSONObject().put("id", 10).put("username", "user1"));
    }
}
