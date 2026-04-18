// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.gitlab.model;

import com.github.istin.dmtools.common.model.IUser;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

public class GitLabMRApprovalTest {

    @Test
    public void testGetUser_returnsUserFromNestedUserObject() throws JSONException {
        JSONObject userJson = new JSONObject();
        userJson.put("id", 7);
        userJson.put("name", "Alice");
        userJson.put("username", "alice");

        JSONObject approvalJson = new JSONObject();
        approvalJson.put("user", userJson);

        GitLabMRApproval approval = new GitLabMRApproval(approvalJson);
        IUser user = approval.getUser();

        assertNotNull(user);
        assertEquals("Alice", user.getFullName());
    }

    @Test
    public void testGetUser_returnsNullWhenUserAbsent() throws JSONException {
        GitLabMRApproval approval = new GitLabMRApproval(new JSONObject());
        assertNull(approval.getUser());
    }

    @Test
    public void testConstructFromString() throws JSONException {
        String json = "{\"user\":{\"id\":3,\"name\":\"Bob\",\"username\":\"bob\"}}";
        GitLabMRApproval approval = new GitLabMRApproval(json);
        assertNotNull(approval.getUser());
        assertEquals("Bob", approval.getUser().getFullName());
    }
}
