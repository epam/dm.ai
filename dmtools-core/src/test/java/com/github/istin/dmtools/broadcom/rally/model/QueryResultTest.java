// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.broadcom.rally.model;

import com.github.istin.dmtools.common.model.IAttachment;
import com.github.istin.dmtools.common.model.IComment;
import com.github.istin.dmtools.common.model.IHistory;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.*;

public class QueryResultTest {

    private QueryResult queryResult;
    private JSONObject mockJsonObject;

    @Before
    public void setUp() throws JSONException {
        mockJsonObject = mock(JSONObject.class);
        queryResult = new QueryResult(mockJsonObject);
    }

    @Test
    public void testGetIssues() {
        List<RallyIssue> issues = queryResult.getIssues();
        assertNotNull(issues);
    }

    @Test
    public void testGetRevisions() {
        List<Revision> revisions = queryResult.getRevisions();
        assertNotNull(revisions);
    }

    @Test
    public void testGetIterations() {
        List<Iteration> iterations = queryResult.getIterations();
        assertNotNull(iterations);
    }

    @Test
    public void testGetErrors() throws JSONException {
        JSONArray mockErrors = new JSONArray();
        when(mockJsonObject.getJSONArray("Errors")).thenReturn(mockErrors);
        JSONArray errors = queryResult.getErrors();
        assertEquals(mockErrors, errors);
    }

    @Test
    public void testGetTotalResultCount() throws JSONException {
        when(mockJsonObject.optInt("TotalResultCount")).thenReturn(10);
        int totalResultCount = queryResult.getTotalResultCount();
        assertEquals(10, totalResultCount);
    }

    @Test
    public void testGetPageSize() throws JSONException {
        when(mockJsonObject.optInt("PageSize")).thenReturn(5);
        int pageSize = queryResult.getPageSize();
        assertEquals(5, pageSize);
    }

    @Test
    public void testGetHistories() {
        List<? extends IHistory> histories = queryResult.getHistories();
        assertNotNull(histories);
    }

    @Test
    public void testGetComments() {
        List<? extends IComment> comments = queryResult.getComments();
        assertNotNull(comments);
    }

    @Test
    public void testGetFlowStates() {
        List<FlowState> flowStates = queryResult.getFlowStates();
        assertNotNull(flowStates);
    }

    @Test
    public void testGetTags() {
        List<RallyTag> tags = queryResult.getTags();
        assertNotNull(tags);
    }

    @Test
    public void testGetAttachments() {
        List<? extends IAttachment> attachments = queryResult.getAttachments();
        assertNotNull(attachments);
    }
}