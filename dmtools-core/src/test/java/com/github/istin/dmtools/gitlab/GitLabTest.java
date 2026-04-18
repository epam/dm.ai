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
        try {
            gitLab.addPullRequestLabel("workspace", "repository", "1", "label");
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // Expected exception
        }
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
    public void testPullRequestActivities_approvalsFromApi() throws IOException {
        // First call = Approvals API → returns two approvers
        JSONObject approvalsResponse = new JSONObject();
        JSONArray approvedBy = new JSONArray();
        JSONObject approverEntry = new JSONObject();
        approverEntry.put("user", new JSONObject().put("id", 7).put("username", "alice").put("name", "Alice"));
        approvedBy.put(approverEntry);
        approvalsResponse.put("approved_by", approvedBy);

        // Second call = notes endpoint → one non-system comment
        JSONArray notes = new JSONArray();
        JSONObject commentNote = new JSONObject();
        commentNote.put("id", 2);
        commentNote.put("body", "looks good");
        commentNote.put("type", JSONObject.NULL);
        commentNote.put("system", false);
        commentNote.put("author", new JSONObject().put("id", 10).put("username", "bob"));
        notes.put(commentNote);

        // execute() is called twice: first for approvals, then for notes
        doReturn(approvalsResponse.toString(), notes.toString()).when(gitLab).execute(any(GenericRequest.class));

        List<IActivity> activities = gitLab.pullRequestActivities("workspace", "repo", "1");
        assertEquals("One approval + one comment = 2 activities", 2, activities.size());
        assertEquals("APPROVED", activities.get(0).getAction());
        assertNotNull("Approval must carry the approver user", activities.get(0).getApproval());
        assertEquals("Alice", activities.get(0).getApproval().getFullName());
        assertEquals("COMMENTED", activities.get(1).getAction());
        assertNull("Comment activities have null approval", activities.get(1).getApproval());
    }

    @Test
    public void testPullRequestActivities_noApprovalsNoComments() throws IOException {
        // Approvals API returns empty approved_by
        JSONObject approvalsResponse = new JSONObject();
        approvalsResponse.put("approved_by", new JSONArray());
        // Notes endpoint returns empty array
        doReturn(approvalsResponse.toString(), new JSONArray().toString()).when(gitLab).execute(any(GenericRequest.class));

        List<IActivity> activities = gitLab.pullRequestActivities("workspace", "repo", "1");
        assertTrue("No activities when there are no approvals and no notes", activities.isEmpty());
    }

    @Test
    public void testGetCommitsFromBranch_singlePage() throws IOException {
        // Response with 2 commits (< 100) → only one page fetched
        JSONArray page1 = new JSONArray();
        JSONObject c1 = new JSONObject();
        c1.put("id", "abc1");
        c1.put("author_name", "Alice");
        c1.put("author_email", "alice@example.com");
        c1.put("committed_date", "2024-01-10T10:00:00.000Z");
        c1.put("message", "Fix bug");
        c1.put("web_url", "https://gitlab.com/g/r/-/commit/abc1");
        page1.put(c1);
        JSONObject c2 = new JSONObject();
        c2.put("id", "abc2");
        c2.put("author_name", "Bob");
        c2.put("author_email", "bob@example.com");
        c2.put("committed_date", "2024-01-11T10:00:00.000Z");
        c2.put("message", "Add feature");
        c2.put("web_url", "https://gitlab.com/g/r/-/commit/abc2");
        page1.put(c2);

        doReturn(page1.toString()).when(gitLab).execute(any(GenericRequest.class));

        List<ICommit> commits = gitLab.getCommitsFromBranch("workspace", "repo", "main", "2024-01-01", null);
        assertEquals("Two commits returned from single page", 2, commits.size());
        assertNotNull("Author must be set", commits.get(0).getAuthor());
        assertEquals("Alice", commits.get(0).getAuthor().getFullName());
    }

    @Test
    public void testGetCommitsFromBranch_nullResponseReturnsEmptyList() throws IOException {
        doReturn(null).when(gitLab).execute(any(GenericRequest.class));
        List<ICommit> commits = gitLab.getCommitsFromBranch("workspace", "repo", "main", null, null);
        assertTrue("Null HTTP response must yield empty list", commits.isEmpty());
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
        JSONArray discussions = new JSONArray();
        JSONObject disc = new JSONObject();
        disc.put("id", "abc123");
        disc.put("individual_note", false);
        discussions.put(disc);

        doReturn(discussions.toString()).when(gitLab).execute(any(GenericRequest.class));
        String result = gitLab.getPRDiscussions("workspace", "repo", "1");
        assertNotNull(result);
        JSONArray parsed = new JSONArray(result);
        assertEquals(1, parsed.length());
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
}


