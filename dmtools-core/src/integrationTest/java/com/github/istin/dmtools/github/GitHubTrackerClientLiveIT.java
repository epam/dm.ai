// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.github;

import com.github.istin.dmtools.common.model.IComment;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.github.model.GitHubTicket;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Live integration test for {@link GitHubTrackerClient} against a real
 * repository. Runs only when SOURCE_GITHUB_TOKEN, IT_GITHUB_WORKSPACE and
 * IT_GITHUB_REPOSITORY are set (e.g. owner=IstiN repo=fah-git-test). Skipped by
 * default in unit-test runs.
 */
public class GitHubTrackerClientLiveIT {

    private GitHubTrackerClient client;

    @Before
    public void setUp() throws Exception {
        String token = System.getenv("SOURCE_GITHUB_TOKEN");
        String owner = System.getenv("IT_GITHUB_WORKSPACE");
        String repo = System.getenv("IT_GITHUB_REPOSITORY");
        Assume.assumeTrue("live IT skipped: no token/workspace/repo",
                token != null && !token.isEmpty() && owner != null && repo != null);
        GitHubTrackerClient.resetInstanceForTesting();
        client = new GitHubTrackerClient("https://api.github.com", token);
    }

    @Test
    public void testFullTrackerFlow() throws Exception {
        String owner = System.getenv("IT_GITHUB_WORKSPACE");
        String repo = System.getenv("IT_GITHUB_REPOSITORY");
        String project = owner + "/" + repo;

        // create
        String key = client.createTicketInProject(project, "Issue", "[it] TrackerClient live", "live body", null);
        assertNotNull(key);
        assertTrue(key.contains("#"));

        // read back
        GitHubTicket ticket = client.performTicket(key, null);
        assertEquals(key, ticket.getCompositeKey());
        assertEquals("open", ticket.getStatus());

        // comment
        client.postComment(key, "live tracker comment");
        List<? extends IComment> comments = client.getComments(key, ticket);
        assertTrue(comments.stream().anyMatch(c -> c.getBody() != null && c.getBody().contains("live tracker comment")));

        // label
        client.addLabelIfNotExists(ticket, "it-live");
        GitHubTicket relabeled = client.performTicket(key, null);
        assertTrue(TrackerClient.Utils.isLabelExists(relabeled, "it-live"));

        // move to Done -> closes
        client.moveToStatus(key, "Done");
        assertEquals("closed", client.performTicket(key, null).getStatus());

        // assign (to workspace owner)
        client.assignTo(key, owner);
        GitHubTicket assigned = client.performTicket(key, null);
        assertFalse(assigned.getAssignees().isEmpty());

        // cleanup label
        client.deleteLabelInTicket(assigned, "it-live");
        assertFalse(TrackerClient.Utils.isLabelExists(client.performTicket(key, null), "it-live"));
    }

    @Test
    public void testSearchAndBrowseUrl() throws Exception {
        String owner = System.getenv("IT_GITHUB_WORKSPACE");
        String repo = System.getenv("IT_GITHUB_REPOSITORY");
        List<GitHubTicket> tickets = client.searchAndPerform("repo:" + owner + "/" + repo + " is:issue", null);
        assertNotNull(tickets);
        assertTrue(client.getTicketBrowseUrl(owner + "/" + repo + "#1").contains("github.com/" + owner + "/" + repo + "/issues/1"));
        assertTrue(client.buildUrlToSearch("is:issue").contains("type=issues"));
    }
}
