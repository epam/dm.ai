// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.bitbucket.model;

import com.github.istin.dmtools.atlassian.common.model.Assignee;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.*;

public class ApprovalTest {

    @Test
    public void testApprovalDefaultConstructor() {
        Approval approval = new Approval();
        assertNotNull(approval);
    }

    @Test
    public void testApprovalJsonStringConstructor() throws JSONException {
        String jsonString = "{\"user\": {\"name\": \"testUser\"}}";
        Approval approval = new Approval(jsonString);
        assertNotNull(approval);
    }

    @Test
    public void testApprovalJsonObjectConstructor() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("user", new JSONObject().put("name", "testUser"));
        Approval approval = new Approval(jsonObject);
        assertNotNull(approval);
    }

    @Test
    public void testGetUser() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("user", new JSONObject().put("name", "testUser"));
        Approval approval = new Approval(jsonObject);

        Assignee user = approval.getUser();
        assertNotNull(user);
    }

    @Test
    public void testGetUserWhenUserIsNull() {
        JSONObject jsonObject = new JSONObject();
        Approval approval = new Approval(jsonObject);

        Assignee user = approval.getUser();
        assertNull(user);
    }
}