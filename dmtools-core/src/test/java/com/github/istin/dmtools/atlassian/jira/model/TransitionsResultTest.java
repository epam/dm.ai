// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.jira.model;

import org.json.JSONObject;
import org.json.JSONException;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

public class TransitionsResultTest {

    @Test
    public void testDefaultConstructor() {
        TransitionsResult transitionsResult = new TransitionsResult();
        assertNotNull(transitionsResult);
    }

    @Test
    public void testJsonConstructor() throws JSONException {
        String jsonString = "{\"transitions\":[]}";
        TransitionsResult transitionsResult = new TransitionsResult(jsonString);
        assertNotNull(transitionsResult);
    }

    @Test
    public void testJSONObjectConstructor() {
        JSONObject jsonObject = new JSONObject();
        TransitionsResult transitionsResult = new TransitionsResult(jsonObject);
        assertNotNull(transitionsResult);
    }

    @Test
    public void testGetTransitions() {
        TransitionsResult transitionsResult = Mockito.spy(new TransitionsResult());
        doReturn(List.of()).when(transitionsResult).getModels(Transition.class, TransitionsResult.TRANSITIONS);
        
        List<Transition> transitions = transitionsResult.getTransitions();
        assertNotNull(transitions);
        assertTrue(transitions.isEmpty());
    }
}