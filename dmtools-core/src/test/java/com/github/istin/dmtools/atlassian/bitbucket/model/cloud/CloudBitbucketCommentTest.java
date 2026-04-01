// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.bitbucket.model.cloud;

import com.github.istin.dmtools.atlassian.common.model.Assignee;
import com.github.istin.dmtools.common.model.IUser;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.*;

public class CloudBitbucketCommentTest {

    private CloudBitbucketComment comment;
    private JSONObject mockJson;

    @Before
    public void setUp() throws JSONException {
        mockJson = mock(JSONObject.class);
        when(mockJson.getString("raw")).thenReturn("Sample body");
        when(mockJson.getLong("id")).thenReturn(123L);
        when(mockJson.getJSONObject("content")).thenReturn(mockJson);
        comment = new CloudBitbucketComment(mockJson);
    }

    @Test
    public void testGetAuthor() {
        Assignee mockAssignee = mock(Assignee.class);
        CloudBitbucketComment spyComment = Mockito.spy(comment);
        doReturn(mockAssignee).when(spyComment).getModel(Assignee.class, "user");

        IUser author = spyComment.getAuthor();
        assertEquals(mockAssignee, author);
    }

    @Test
    public void testGetBody() {
        String body = comment.getBody();
        assertEquals("Sample body", body);
    }

    @Test
    public void testGetId() {
        String id = comment.getId();
        assertEquals("123", id);
    }

    @Test
    public void testGetCreatedThrowsException() {
        assertThrows(UnsupportedOperationException.class, () -> comment.getCreated());
    }
}