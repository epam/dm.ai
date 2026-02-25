package com.github.istin.dmtools.github;

import com.github.istin.dmtools.common.code.model.SourceCodeConfig;
import com.github.istin.dmtools.common.model.IActivity;
import com.github.istin.dmtools.common.model.IComment;
import com.github.istin.dmtools.common.model.IPullRequest;
import com.github.istin.dmtools.common.networking.GenericRequest;
import com.github.istin.dmtools.github.model.GitHubActivity;
import com.github.istin.dmtools.github.model.GitHubComment;
import com.github.istin.dmtools.github.model.GitHubConversation;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.List;

import org.mockito.ArgumentCaptor;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GitHub PR-related MCPTool methods.
 */
public class GitHubPRTest {

    private GitHub gitHub;
    private static final String BASE_PATH = "https://api.github.com";
    private static final String WORKSPACE = "IstiN";
    private static final String REPOSITORY = "dmtools";
    private static final String PR_ID = "74";

    @Before
    public void setUp() throws IOException {
        SourceCodeConfig config = SourceCodeConfig.builder()
                .path(BASE_PATH)
                .auth("test-token")
                .workspaceName(WORKSPACE)
                .repoName(REPOSITORY)
                .branchName("main")
                .type(SourceCodeConfig.Type.GITHUB)
                .build();
        gitHub = Mockito.mock(BasicGithub.class,
                withSettings().useConstructor(config).defaultAnswer(CALLS_REAL_METHODS));
    }

    // ---- github_get_pr ----

    @Test
    public void testGetPR_returnsValidPullRequest() throws IOException {
        JSONObject prJson = buildPRJson("74", "Add MCP tools", "open");
        doReturn(prJson.toString()).when(gitHub).execute(any(GenericRequest.class));

        IPullRequest pr = gitHub.pullRequest(WORKSPACE, REPOSITORY, PR_ID);

        assertNotNull(pr);
        assertEquals(Integer.valueOf(74), pr.getId());
        assertEquals("Add MCP tools", pr.getTitle());
    }

    @Test
    public void testGetPR_serializedToJSON() throws IOException {
        JSONObject prJson = buildPRJson("74", "Add MCP tools", "open");
        doReturn(prJson.toString()).when(gitHub).execute(any(GenericRequest.class));

        IPullRequest pr = gitHub.pullRequest(WORKSPACE, REPOSITORY, PR_ID);

        assertNotNull(pr);
        String json = pr.toString();
        assertNotNull(json);
        assertTrue(json.contains("Add MCP tools"));
    }

    // ---- github_get_pr_comments ----

    @Test
    public void testGetPRComments_returnsCombinedInlineAndIssueComments() throws IOException {
        JSONArray inlineComments = new JSONArray();
        inlineComments.put(buildCommentJson(1001L, "Inline code comment", "2024-01-01T10:00:00Z", null));
        inlineComments.put(buildCommentJson(1002L, "Another inline comment", "2024-01-01T11:00:00Z", null));

        JSONArray issueComments = new JSONArray();
        issueComments.put(buildCommentJson(2001L, "General discussion", "2024-01-01T09:00:00Z", null));

        doAnswer(invocation -> {
            GenericRequest req = invocation.getArgument(0);
            String url = req.url();
            if (url.contains("/pulls/") && url.endsWith("/comments")) {
                return inlineComments.toString();
            } else if (url.contains("/issues/")) {
                return issueComments.toString();
            }
            return null;
        }).when(gitHub).execute(any(GenericRequest.class));

        List<IComment> comments = gitHub.pullRequestComments(WORKSPACE, REPOSITORY, PR_ID);

        assertNotNull(comments);
        assertEquals(3, comments.size());
        assertEquals("General discussion", comments.get(0).getBody());
    }

    @Test
    public void testGetPRComments_nullInlineResponseReturnsOnlyIssueComments() throws IOException {
        JSONArray issueComments = new JSONArray();
        issueComments.put(buildCommentJson(2001L, "Only issue comment", "2024-01-01T09:00:00Z", null));

        doAnswer(invocation -> {
            GenericRequest req = invocation.getArgument(0);
            String url = req.url();
            if (url.contains("/pulls/") && url.endsWith("/comments")) {
                return null;
            } else if (url.contains("/issues/")) {
                return issueComments.toString();
            }
            return null;
        }).when(gitHub).execute(any(GenericRequest.class));

        List<IComment> comments = gitHub.pullRequestComments(WORKSPACE, REPOSITORY, PR_ID);

        assertNotNull(comments);
        assertEquals(1, comments.size());
        assertEquals("Only issue comment", comments.get(0).getBody());
    }

    // ---- github_get_pr_conversations ----

    @Test
    public void testGetPRConversations_groupsRepliesIntoThreads() throws IOException {
        JSONArray inlineComments = new JSONArray();
        inlineComments.put(buildCommentJson(1001L, "Root comment", "2024-01-01T10:00:00Z", null));
        inlineComments.put(buildCommentJson(1002L, "Reply to root", "2024-01-01T11:00:00Z", 1001L));
        inlineComments.put(buildCommentJson(1003L, "Another root", "2024-01-01T12:00:00Z", null));

        JSONArray issueComments = new JSONArray();

        doAnswer(invocation -> {
            GenericRequest req = invocation.getArgument(0);
            String url = req.url();
            if (url.contains("/pulls/") && url.endsWith("/comments")) {
                return inlineComments.toString();
            } else if (url.contains("/issues/")) {
                return issueComments.toString();
            }
            return null;
        }).when(gitHub).execute(any(GenericRequest.class));

        List<GitHubConversation> conversations = gitHub.getPRConversations(WORKSPACE, REPOSITORY, PR_ID);

        assertNotNull(conversations);
        assertEquals(2, conversations.size());

        GitHubConversation firstConversation = conversations.get(0);
        assertEquals("Root comment", firstConversation.getRootComment().getBody());
        assertEquals(1, firstConversation.getReplies().size());
        assertEquals("Reply to root", firstConversation.getReplies().get(0).getBody());
        assertEquals(2, firstConversation.getTotalComments());

        GitHubConversation secondConversation = conversations.get(1);
        assertEquals("Another root", secondConversation.getRootComment().getBody());
        assertEquals(0, secondConversation.getReplies().size());
    }

    @Test
    public void testGetPRConversations_includesIssueCommentsAsStandaloneConversations() throws IOException {
        JSONArray inlineComments = new JSONArray();
        JSONArray issueComments = new JSONArray();
        issueComments.put(buildCommentJson(2001L, "Issue comment", "2024-01-01T09:00:00Z", null));

        doAnswer(invocation -> {
            GenericRequest req = invocation.getArgument(0);
            String url = req.url();
            if (url.contains("/pulls/") && url.endsWith("/comments")) {
                return inlineComments.toString();
            } else if (url.contains("/issues/")) {
                return issueComments.toString();
            }
            return null;
        }).when(gitHub).execute(any(GenericRequest.class));

        List<GitHubConversation> conversations = gitHub.getPRConversations(WORKSPACE, REPOSITORY, PR_ID);

        assertNotNull(conversations);
        assertEquals(1, conversations.size());
        assertEquals("Issue comment", conversations.get(0).getRootComment().getBody());
    }

    @Test
    public void testGetPRConversations_nullInlineResponseReturnsOnlyIssueConversations() throws IOException {
        JSONArray issueComments = new JSONArray();
        issueComments.put(buildCommentJson(2001L, "Issue only", "2024-01-01T09:00:00Z", null));

        doAnswer(invocation -> {
            GenericRequest req = invocation.getArgument(0);
            String url = req.url();
            if (url.contains("/pulls/") && url.endsWith("/comments")) {
                return null;
            } else if (url.contains("/issues/")) {
                return issueComments.toString();
            }
            return null;
        }).when(gitHub).execute(any(GenericRequest.class));

        List<GitHubConversation> conversations = gitHub.getPRConversations(WORKSPACE, REPOSITORY, PR_ID);

        assertNotNull(conversations);
        assertEquals(1, conversations.size());
    }

    // ---- github_get_pr_activities ----

    @Test
    public void testGetPRActivities_returnsReviewsAndComments() throws IOException {
        JSONArray reviews = new JSONArray();
        reviews.put(buildReviewJson(5001L, "APPROVED"));

        JSONArray inlineComments = new JSONArray();
        inlineComments.put(buildCommentJson(1001L, "Code review comment", "2024-01-01T10:00:00Z", null));

        JSONArray issueComments = new JSONArray();
        issueComments.put(buildCommentJson(2001L, "General comment", "2024-01-01T09:00:00Z", null));

        doAnswer(invocation -> {
            GenericRequest req = invocation.getArgument(0);
            String url = req.url();
            if (url.endsWith("/reviews")) {
                return reviews.toString();
            } else if (url.contains("/pulls/") && url.endsWith("/comments")) {
                return inlineComments.toString();
            } else if (url.contains("/issues/")) {
                return issueComments.toString();
            }
            return null;
        }).when(gitHub).execute(any(GenericRequest.class));

        List<IActivity> activities = gitHub.pullRequestActivities(WORKSPACE, REPOSITORY, PR_ID);

        assertNotNull(activities);
        assertEquals(3, activities.size());
        assertEquals("APPROVED", activities.get(0).getAction());
        assertEquals("COMMENTED", activities.get(1).getAction());
        assertEquals("COMMENTED", activities.get(2).getAction());
    }

    @Test
    public void testGetPRActivities_nullReviewsReturnsOnlyComments() throws IOException {
        JSONArray inlineComments = new JSONArray();
        inlineComments.put(buildCommentJson(1001L, "Inline only", "2024-01-01T10:00:00Z", null));

        doAnswer(invocation -> {
            GenericRequest req = invocation.getArgument(0);
            String url = req.url();
            if (url.endsWith("/reviews")) {
                return null;
            } else if (url.contains("/pulls/") && url.endsWith("/comments")) {
                return inlineComments.toString();
            } else if (url.contains("/issues/")) {
                return new JSONArray().toString();
            }
            return null;
        }).when(gitHub).execute(any(GenericRequest.class));

        List<IActivity> activities = gitHub.pullRequestActivities(WORKSPACE, REPOSITORY, PR_ID);

        assertNotNull(activities);
        assertEquals(1, activities.size());
        assertEquals("COMMENTED", activities.get(0).getAction());
    }

    // ---- Serialization / JSON view ----

    @Test
    public void testGitHubConversation_toJSONProducesValidJSON() {
        JSONObject commentJson = buildCommentJson(1001L, "Root comment", "2024-01-01T10:00:00Z", null);
        GitHubComment root = new GitHubComment(commentJson);
        GitHubConversation conversation = new GitHubConversation(root);

        JSONObject reply1Json = buildCommentJson(1002L, "First reply", "2024-01-01T11:00:00Z", 1001L);
        conversation.addReply(new GitHubComment(reply1Json));

        JSONObject json = conversation.toJSON();
        assertNotNull(json);
        assertTrue(json.has("rootComment"));
        assertTrue(json.has("replies"));
        assertTrue(json.has("totalComments"));
        assertEquals(2, json.getInt("totalComments"));
        assertEquals(1, json.getJSONArray("replies").length());
    }

    @Test
    public void testGitHubComment_newFieldsDeserializeCorrectly() {
        JSONObject commentJson = new JSONObject();
        commentJson.put("id", 1001L);
        commentJson.put("body", "Inline comment");
        commentJson.put("created_at", "2024-01-01T10:00:00Z");
        commentJson.put("diff_hunk", "@@ -1,3 +1,4 @@");
        commentJson.put("path", "src/main/java/Example.java");
        commentJson.put("in_reply_to_id", 999L);
        commentJson.put("pull_request_review_id", 5001L);
        commentJson.put("line", 42L);

        GitHubComment comment = new GitHubComment(commentJson);

        assertEquals("src/main/java/Example.java", comment.getPath());
        assertEquals(Long.valueOf(999L), comment.getInReplyToId());
        assertEquals(Long.valueOf(5001L), comment.getPullRequestReviewId());
        assertEquals(Long.valueOf(42L), comment.getLine());
        assertEquals("@@ -1,3 +1,4 @@", comment.getDiffHunk());
    }

    @Test
    public void testGitHubComment_missingOptionalFieldsReturnNull() {
        JSONObject commentJson = new JSONObject();
        commentJson.put("id", 1001L);
        commentJson.put("body", "Simple comment");

        GitHubComment comment = new GitHubComment(commentJson);

        assertNull(comment.getPath());
        assertNull(comment.getInReplyToId());
        assertNull(comment.getPullRequestReviewId());
        assertNull(comment.getLine());
    }

    // ---- github_reply_to_pr_thread ----

    @Test
    public void testReplyToPullRequestComment_postsToCorrectEndpoint() throws IOException {
        String responseJson = buildCommentJson(9001L, "Reply text", "2024-01-02T10:00:00Z", 1001L).toString();
        doReturn(responseJson).when(gitHub).post(any(GenericRequest.class));

        String result = gitHub.replyToPullRequestComment(WORKSPACE, REPOSITORY, PR_ID, "1001", "Reply text");

        assertNotNull(result);
        verify(gitHub, times(1)).post(any(GenericRequest.class));
        // verify() that execute was never called (no GET needed for reply)
        verify(gitHub, never()).execute(any(GenericRequest.class));
    }

    @Test
    public void testReplyToPullRequestComment_returnsPostResponse() throws IOException {
        JSONObject responseJson = buildCommentJson(9001L, "My reply", "2024-01-02T10:00:00Z", 1001L);
        doReturn(responseJson.toString()).when(gitHub).post(any(GenericRequest.class));

        String result = gitHub.replyToPullRequestComment(WORKSPACE, REPOSITORY, PR_ID, "1001", "My reply");

        assertNotNull(result);
        JSONObject parsed = new JSONObject(result);
        assertEquals(9001L, parsed.getLong("id"));
        assertEquals("My reply", parsed.getString("body"));
    }

    // ---- github_add_inline_comment ----

    @Test
    public void testAddInlineReviewComment_withExplicitCommitId() throws IOException {
        String responseJson = buildCommentJson(9002L, "Inline review", "2024-01-02T10:00:00Z", null).toString();
        doReturn(responseJson).when(gitHub).post(any(GenericRequest.class));

        String result = gitHub.addInlineReviewComment(
                WORKSPACE, REPOSITORY, PR_ID,
                "src/Main.java", "42", "This needs refactoring",
                "abc123def456", null, "RIGHT");

        assertNotNull(result);
        verify(gitHub, times(1)).post(any(GenericRequest.class));
        // explicit commitId provided — no GET request for PR head
        verify(gitHub, never()).execute(any(GenericRequest.class));
    }

    @Test
    public void testAddInlineReviewComment_autofetchesCommitIdWhenEmpty() throws IOException {
        JSONObject prJson = buildPRJsonWithSha("74", "Test PR", "open", "deadbeef1234");
        doReturn(prJson.toString()).when(gitHub).execute(any(GenericRequest.class));

        String responseJson = buildCommentJson(9003L, "Auto-fetched commit", "2024-01-02T10:00:00Z", null).toString();
        doReturn(responseJson).when(gitHub).post(any(GenericRequest.class));

        String result = gitHub.addInlineReviewComment(
                WORKSPACE, REPOSITORY, PR_ID,
                "src/Main.java", "10", "Missing null check",
                "", null, null);

        assertNotNull(result);
        // should GET the PR to fetch head sha
        verify(gitHub, times(1)).execute(any(GenericRequest.class));
        verify(gitHub, times(1)).post(any(GenericRequest.class));
    }

    @Test
    public void testAddInlineReviewComment_multilineUsesStartLine() throws IOException {
        JSONObject prJson = buildPRJsonWithSha("74", "Test PR", "open", "sha123");
        doReturn(prJson.toString()).when(gitHub).execute(any(GenericRequest.class));
        doReturn("{}").when(gitHub).post(any(GenericRequest.class));

        String result = gitHub.addInlineReviewComment(
                WORKSPACE, REPOSITORY, PR_ID,
                "src/Foo.java", "50", "This block is too long",
                null, "45", "RIGHT");

        assertNotNull(result);
        verify(gitHub, times(1)).post(any(GenericRequest.class));
    }

    // ---- github_get_pr_review_threads ----

    @Test
    public void testGetPullRequestReviewThreads_callsGraphQL() throws IOException {
        JSONObject graphqlResponse = new JSONObject();
        JSONObject data = new JSONObject();
        data.put("repository", new JSONObject());
        graphqlResponse.put("data", data);
        doReturn(graphqlResponse.toString()).when(gitHub).post(any(GenericRequest.class));

        String result = gitHub.getPullRequestReviewThreads(WORKSPACE, REPOSITORY, PR_ID);

        assertNotNull(result);
        verify(gitHub, times(1)).post(any(GenericRequest.class));
        verify(gitHub, never()).execute(any(GenericRequest.class));
        // should be a GraphQL response
        JSONObject parsed = new JSONObject(result);
        assertTrue(parsed.has("data"));
    }

    // ---- github_resolve_pr_thread ----

    @Test
    public void testResolveReviewThread_callsGraphQLMutation() throws IOException {
        JSONObject graphqlResponse = new JSONObject();
        JSONObject data = new JSONObject();
        JSONObject resolveResult = new JSONObject();
        JSONObject thread = new JSONObject();
        thread.put("isResolved", true);
        resolveResult.put("thread", thread);
        data.put("resolveReviewThread", resolveResult);
        graphqlResponse.put("data", data);
        doReturn(graphqlResponse.toString()).when(gitHub).post(any(GenericRequest.class));

        String threadId = "PRRT_kwDOBQfyNc5A_test";
        String result = gitHub.resolveReviewThread(threadId);

        assertNotNull(result);
        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(gitHub, times(1)).post(captor.capture());
        String body = captor.getValue().getBody();
        JSONObject requestBody = new JSONObject(body);
        assertTrue(requestBody.getString("query").contains(threadId));
        JSONObject parsed = new JSONObject(result);
        assertTrue(parsed.getJSONObject("data").getJSONObject("resolveReviewThread").getJSONObject("thread").getBoolean("isResolved"));
    }

    // ---- Helper builders ----

    private JSONObject buildPRJson(String number, String title, String state) {
        return buildPRJsonWithSha(number, title, state, "abc123sha456");
    }

    private JSONObject buildPRJsonWithSha(String number, String title, String state, String headSha) {
        JSONObject json = new JSONObject();
        json.put("number", Integer.parseInt(number));
        json.put("title", title);
        json.put("state", state);
        json.put("merged", false);
        json.put("body", "PR description");
        JSONObject head = new JSONObject();
        head.put("ref", "feature-branch");
        head.put("sha", headSha);
        json.put("head", head);
        JSONObject base = new JSONObject();
        base.put("ref", "main");
        json.put("base", base);
        JSONObject user = new JSONObject();
        user.put("login", "testuser");
        json.put("user", user);
        json.put("created_at", "2024-01-01T10:00:00Z");
        return json;
    }

    private JSONObject buildCommentJson(long id, String body, String createdAt, Long inReplyToId) {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("body", body);
        json.put("created_at", createdAt);
        JSONObject user = new JSONObject();
        user.put("login", "reviewer");
        json.put("user", user);
        if (inReplyToId != null) {
            json.put("in_reply_to_id", inReplyToId);
        }
        return json;
    }

    private JSONObject buildReviewJson(long id, String state) {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("state", state);
        json.put("body", "");
        JSONObject user = new JSONObject();
        user.put("login", "reviewer");
        json.put("user", user);
        json.put("submitted_at", "2024-01-01T10:00:00Z");
        return json;
    }
}
