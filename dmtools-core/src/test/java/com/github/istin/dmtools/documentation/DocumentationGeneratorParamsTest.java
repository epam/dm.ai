// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.documentation;

import org.junit.Test;
import org.json.JSONObject;
import org.json.JSONException;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DocumentationGeneratorParamsTest {

    @Test
    public void testConstructorWithJSONString() throws JSONException {
        String jsonString = "{\"confluenceRootPage\":\"rootPage\",\"eachPagePrefix\":\"prefix\",\"jql\":\"query\",\"isReadFeatureAreasFromConfluenceRootPage\":true,\"listOfStatusesToSort\":[\"status1\",\"status2\"]}";
        DocumentationGeneratorParams params = new DocumentationGeneratorParams(jsonString);

        assertEquals("rootPage", params.getConfluenceRootPage());
        assertEquals("prefix", params.getEachPagePrefix());
        assertEquals("query", params.getJQL());
        assertTrue(params.isReadFeatureAreasFromConfluenceRootPage());
        String[] statuses = params.getListOfStatusesToSort();
        assertEquals(2, statuses.length);
        assertEquals("status1", statuses[0]);
        assertEquals("status2", statuses[1]);
    }

    @Test
    public void testConstructorWithJSONObject() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("confluenceRootPage", "rootPage");
        jsonObject.put("eachPagePrefix", "prefix");
        jsonObject.put("jql", "query");
        jsonObject.put("isReadFeatureAreasFromConfluenceRootPage", true);
        jsonObject.put("listOfStatusesToSort", new String[]{"status1", "status2"});

        DocumentationGeneratorParams params = new DocumentationGeneratorParams(jsonObject);

        assertEquals("rootPage", params.getConfluenceRootPage());
        assertEquals("prefix", params.getEachPagePrefix());
        assertEquals("query", params.getJQL());
        assertTrue(params.isReadFeatureAreasFromConfluenceRootPage());
    }

    @Test
    public void testDefaultConstructor() {
        DocumentationGeneratorParams params = new DocumentationGeneratorParams();
        // Assuming default values or empty JSONModel behavior
        // Add assertions if there are default values expected
    }
}