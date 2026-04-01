// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.figma.model;

import com.github.istin.dmtools.common.utils.DateUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.util.Date;

import static org.junit.Assert.assertEquals;

public class FigmaCommentTest {

    private FigmaComment figmaComment;
    private JSONObject jsonObject;

    @Before
    public void setUp() throws JSONException {
        jsonObject = new JSONObject();
        jsonObject.put("user", new JSONObject());
        jsonObject.put("message", "Test message");
        jsonObject.put("id", "12345");
        jsonObject.put("created_at", "2023-10-01T12:00:00Z");

        figmaComment = new FigmaComment(jsonObject);
    }


    @Test
    public void testGetBody() {
        String body = figmaComment.getBody();
        assertEquals("Test message", body);
    }

    @Test
    public void testGetId() {
        String id = figmaComment.getId();
        assertEquals("12345", id);
    }

    @Test
    public void testGetCreated() {
        Date expectedDate = DateUtils.smartParseDate("2023-10-01T12:00:00Z");
        Date createdDate = figmaComment.getCreated();
        assertEquals(expectedDate, createdDate);
    }
}