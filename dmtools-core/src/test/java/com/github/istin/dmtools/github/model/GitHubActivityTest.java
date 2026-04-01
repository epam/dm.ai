// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.github.model;

import com.github.istin.dmtools.common.model.IComment;
import com.github.istin.dmtools.common.model.IUser;
import org.junit.Test;
import org.json.JSONObject;
import org.json.JSONException;

import static org.junit.Assert.*;

public class GitHubActivityTest {

    @Test
    public void testGetAction() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("state", "APPROVED");
        GitHubActivity activity = new GitHubActivity(json);

        assertEquals("APPROVED", activity.getAction());
    }

    @Test
    public void testGetComment_withBody() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("state", "CHANGES_REQUESTED");
        json.put("body", "This is a comment");
        json.put("id", 12345);
        json.put("user", new JSONObject());

        GitHubActivity activity = new GitHubActivity(json);
        IComment comment = activity.getComment();

        assertNotNull(comment);
        assertEquals("This is a comment", comment.getBody());
        assertEquals("12345", comment.getId());
        assertNotNull(comment.getAuthor());
    }

    @Test
    public void testGetComment_commentedStateNoBody() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("state", "COMMENTED");
        json.put("body", "");
        json.put("id", 999);
        json.put("user", new JSONObject());

        GitHubActivity activity = new GitHubActivity(json);
        IComment comment = activity.getComment();

        // COMMENTED with empty body is just a container for inline comments, not a standalone comment
        assertNull(comment);
    }

    @Test
    public void testGetComment_approvedNoBody() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("state", "APPROVED");
        json.put("body", "");
        json.put("user", new JSONObject());

        GitHubActivity activity = new GitHubActivity(json);
        IComment comment = activity.getComment();

        // APPROVED with empty body => no comment
        assertNull(comment);
    }

    @Test
    public void testGetComment_approvedWithBody() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("state", "APPROVED");
        json.put("body", "LGTM!");
        json.put("id", 100);
        json.put("user", new JSONObject());

        GitHubActivity activity = new GitHubActivity(json);
        IComment comment = activity.getComment();

        // APPROVED with body => comment present
        assertNotNull(comment);
        assertEquals("LGTM!", comment.getBody());
    }

    @Test
    public void testGetApproval_approvedState() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("state", "APPROVED");
        json.put("user", new JSONObject());

        GitHubActivity activity = new GitHubActivity(json);
        IUser approval = activity.getApproval();

        assertNotNull(approval);
    }

    @Test
    public void testGetApproval_commentedState() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("state", "COMMENTED");
        json.put("user", new JSONObject());

        GitHubActivity activity = new GitHubActivity(json);
        IUser approval = activity.getApproval();

        // Only APPROVED state returns approval
        assertNull(approval);
    }

    @Test
    public void testGetApproval_changesRequestedState() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("state", "CHANGES_REQUESTED");
        json.put("user", new JSONObject());

        GitHubActivity activity = new GitHubActivity(json);
        IUser approval = activity.getApproval();

        assertNull(approval);
    }

    @Test
    public void testGetApproval_pendingState() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("state", "PENDING");
        json.put("user", new JSONObject());

        GitHubActivity activity = new GitHubActivity(json);

        assertNull(activity.getApproval());
        assertNull(activity.getComment()); // PENDING with no body
    }
}
