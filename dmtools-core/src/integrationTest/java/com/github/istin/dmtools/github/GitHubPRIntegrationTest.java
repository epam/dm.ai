package com.github.istin.dmtools.github;

import com.github.istin.dmtools.common.code.model.SourceCodeConfig;
import com.github.istin.dmtools.common.model.IActivity;
import com.github.istin.dmtools.common.model.IComment;
import com.github.istin.dmtools.common.model.IPullRequest;
import com.github.istin.dmtools.common.utils.PropertyReader;
import com.github.istin.dmtools.github.model.GitHubConversation;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Integration tests for GitHub PR MCPTool methods.
 * Tests against real PR: https://github.com/IstiN/dmtools/pull/74
 *
 * To run: ensure GITHUB_TOKEN env var is set (or dmtools.env file configured).
 * Enable by removing @Ignore annotation.
 */
@Ignore("Enable for manual testing with real GitHub credentials")
public class GitHubPRIntegrationTest {

    private static final String WORKSPACE = "IstiN";
    private static final String REPOSITORY = "dmtools";
    private static final String PR_ID = "74";

    private BasicGithub github;

    @Before
    public void setUp() throws IOException {
        SourceCodeConfig config = SourceCodeConfig.builder()
                .path("https://api.github.com")
                .auth(new PropertyReader().getGithubToken())
                .workspaceName(WORKSPACE)
                .repoName(REPOSITORY)
                .branchName("main")
                .type(SourceCodeConfig.Type.GITHUB)
                .build();
        github = new BasicGithub(config);
        github.setClearCache(true);
    }

    @Test
    public void testGetPR_returnsPR74Details() throws IOException {
        IPullRequest pr = github.pullRequest(WORKSPACE, REPOSITORY, PR_ID);

        assertNotNull(pr);
        assertNotNull(pr.getTitle());
        assertNotNull(pr.getId());

        System.out.println("=== PR #74 Details ===");
        System.out.println("ID: " + pr.getId());
        System.out.println("Title: " + pr.getTitle());
        System.out.println("JSON: " + pr.toString());
    }

    @Test
    public void testGetPRComments_returnsBothInlineAndIssueComments() throws IOException {
        List<IComment> comments = github.pullRequestComments(WORKSPACE, REPOSITORY, PR_ID);

        assertNotNull(comments);
        assertFalse("PR #74 should have comments", comments.isEmpty());

        System.out.println("=== PR #74 All Comments (" + comments.size() + ") ===");
        for (IComment comment : comments) {
            System.out.println("---");
            System.out.println("ID: " + comment.getId());
            System.out.println("Body: " + comment.getBody());
            System.out.println("Created: " + comment.getCreated());
        }
    }

    @Test
    public void testGetPRActivities_returnsReviewsAndComments() throws IOException {
        List<IActivity> activities = github.pullRequestActivities(WORKSPACE, REPOSITORY, PR_ID);

        assertNotNull(activities);
        assertFalse("PR #74 should have activities", activities.isEmpty());

        System.out.println("=== PR #74 Activities (" + activities.size() + ") ===");
        for (IActivity activity : activities) {
            System.out.println("---");
            System.out.println("Action: " + activity.getAction());
            if (activity.getComment() != null) {
                System.out.println("Comment: " + activity.getComment().getBody());
            }
            if (activity.getApproval() != null) {
                System.out.println("Approval by: " + activity.getApproval().getFullName());
            }
        }
    }

    @Test
    public void testGetPRConversations_groupsInlineCommentThreads() throws IOException {
        List<GitHubConversation> conversations = github.getPRConversations(WORKSPACE, REPOSITORY, PR_ID);

        assertNotNull(conversations);
        assertFalse("PR #74 should have conversations", conversations.isEmpty());

        System.out.println("=== PR #74 Conversations (" + conversations.size() + ") ===");
        for (GitHubConversation conv : conversations) {
            System.out.println("---");
            System.out.println("File: " + conv.getPath());
            System.out.println("Root: " + conv.getRootComment().getBody());
            System.out.println("Replies (" + conv.getReplies().size() + "):");
            for (var reply : conv.getReplies()) {
                System.out.println("  > " + reply.getBody());
            }
            System.out.println("JSON: " + conv.toJSON().toString(2));
        }

        boolean hasThreads = conversations.stream().anyMatch(c -> !c.getReplies().isEmpty());
        System.out.println("Has threaded conversations: " + hasThreads);
    }

    @Test
    public void testConversationJSONSerialization() throws IOException {
        List<GitHubConversation> conversations = github.getPRConversations(WORKSPACE, REPOSITORY, PR_ID);

        assertNotNull(conversations);
        for (GitHubConversation conv : conversations) {
            assertNotNull(conv.toJSON());
            assertTrue(conv.toJSON().has("rootComment"));
            assertTrue(conv.toJSON().has("replies"));
            assertTrue(conv.toJSON().has("totalComments"));
        }

        System.out.println("=== Serialization Test PASSED for " + conversations.size() + " conversations ===");
    }
}
