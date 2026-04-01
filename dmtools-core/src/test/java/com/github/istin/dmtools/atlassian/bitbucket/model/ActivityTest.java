// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.bitbucket.model;

import com.github.istin.dmtools.common.model.IUser;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.*;

public class ActivityTest {

    private Activity activity;
    private JSONObject jsonObject;

    @Before
    public void setUp() throws JSONException {
        jsonObject = new JSONObject();
        jsonObject.put("action", "created");
        jsonObject.put("comment", new JSONObject());
        jsonObject.put("approval", new JSONObject());

        activity = new Activity(jsonObject);
    }

    @Test
    public void testGetAction() {
        assertEquals("created", activity.getAction());
    }

    @Test
    public void testGetComment() {
        Comment mockComment = mock(Comment.class);
        Activity spyActivity = Mockito.spy(activity);
        doReturn(mockComment).when(spyActivity).getModel(Comment.class, "comment");

        assertEquals(mockComment, spyActivity.getComment());
    }


    @Test
    public void testGetApprovalWhenNull() {
        Activity spyActivity = Mockito.spy(activity);
        doReturn(null).when(spyActivity).getModel(Approval.class, "approval");

        assertNull(spyActivity.getApproval());
    }
}