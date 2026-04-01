// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.broadcom.rally.model;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.json.JSONObject;
import org.json.JSONException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.*;

import java.util.Date;

public class CommentTest {

    private Comment comment;
    private JSONObject jsonObject;

    @Before
    public void setUp() throws JSONException {
        jsonObject = new JSONObject();
        jsonObject.put("Text", "Sample body text");
        jsonObject.put("ObjectUUID", "12345");
        jsonObject.put("_ref", "http://example.com/ref");
        comment = new Comment(jsonObject);
    }

    @Test
    public void testGetBody() {
        String expectedBody = "Sample body text";
        String actualBody = comment.getBody();
        assertEquals(expectedBody, actualBody);
    }

    @Test
    public void testGetId() {
        String expectedId = "12345";
        String actualId = comment.getId();
        assertEquals(expectedId, actualId);
    }

    @Test
    public void testGetRef() {
        String expectedRef = "http://example.com/ref";
        String actualRef = comment.getRef();
        assertEquals(expectedRef, actualRef);
    }

    @Test
    public void testGetCreatedThrowsException() {
        assertThrows(UnsupportedOperationException.class, () -> {
            comment.getCreated();
        });
    }

    @Test
    public void testGetAuthor() {
        Comment spyComment = Mockito.spy(comment);
        RallyUser mockUser = mock(RallyUser.class);
        doReturn(mockUser).when(spyComment).getModel(RallyUser.class, RallyFields.USER);
        
        assertEquals(mockUser, spyComment.getAuthor());
    }
}