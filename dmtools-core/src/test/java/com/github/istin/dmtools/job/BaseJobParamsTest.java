// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.job;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

public class BaseJobParamsTest {

    private BaseJobParams baseJobParams;
    private JSONObject mockJsonObject;

    @Before
    public void setUp() throws JSONException {
        mockJsonObject = new JSONObject();
        mockJsonObject.put(BaseJobParams.INPUT_JQL, "testJQL");
        mockJsonObject.put(BaseJobParams.INITIATOR, "testInitiator");
        mockJsonObject.put(BaseJobParams.CONFLUENCE_PAGES, new String[]{"page1", "page2"});

        baseJobParams = new BaseJobParams(mockJsonObject);
    }

    @Test
    public void testGetInputJQL() {
        assertEquals("testJQL", baseJobParams.getInputJQL());
    }

    @Test
    public void testGetInitiator() {
        assertEquals("testInitiator", baseJobParams.getInitiator());
    }


    @Test
    public void testSetInputJQL() {
        BaseJobParams result = baseJobParams.setInputJQL("newJQL");
        assertEquals("newJQL", baseJobParams.getInputJQL());
        assertEquals(baseJobParams, result);
    }

    @Test
    public void testSetConfluencePages() {
        BaseJobParams result = baseJobParams.setConfluencePages("newPage1", "newPage2");
        assertArrayEquals(new String[]{"newPage1", "newPage2"}, baseJobParams.getConfluencePages());
        assertEquals(baseJobParams, result);
    }

    @Test
    public void testSetInitiator() {
        BaseJobParams result = baseJobParams.setInitiator("newInitiator");
        assertEquals("newInitiator", baseJobParams.getInitiator());
        assertEquals(baseJobParams, result);
    }

    @Test
    public void testConstructorWithString() throws JSONException {
        BaseJobParams params = new BaseJobParams(mockJsonObject.toString());
        assertEquals("testJQL", params.getInputJQL());
        assertEquals("testInitiator", params.getInitiator());
        assertArrayEquals(new String[]{"page1", "page2"}, params.getConfluencePages());
    }

    @Test
    public void testConstructorWithJSONObject() {
        BaseJobParams params = new BaseJobParams(mockJsonObject);
        assertEquals("testJQL", params.getInputJQL());
        assertEquals("testInitiator", params.getInitiator());
    }
}