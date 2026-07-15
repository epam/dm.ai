// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.jira.utils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class AtlassianDocumentFormatTest {

    @Test
    public void testWrapPlainTextProducesAdfDocument() {
        JSONObject doc = AtlassianDocumentFormat.wrapPlainText("Hello ADF");

        assertEquals(1, doc.getInt("version"));
        assertEquals("doc", doc.getString("type"));

        JSONArray content = doc.getJSONArray("content");
        assertEquals(1, content.length());

        JSONObject paragraph = content.getJSONObject(0);
        assertEquals("paragraph", paragraph.getString("type"));

        JSONArray paragraphContent = paragraph.getJSONArray("content");
        assertEquals(1, paragraphContent.length());

        JSONObject textNode = paragraphContent.getJSONObject(0);
        assertEquals("text", textNode.getString("type"));
        assertEquals("Hello ADF", textNode.getString("text"));
    }

    @Test
    public void testNormalize_passesThroughJSONObject() {
        JSONObject adf = new JSONObject();
        adf.put("version", 1);
        adf.put("type", "doc");
        adf.put("content", new JSONArray());

        assertSame(adf, AtlassianDocumentFormat.normalize(adf));
    }

    @Test
    public void testNormalize_wrapsJSONArrayAsContent() {
        JSONArray content = new JSONArray()
                .put(new JSONObject().put("type", "paragraph").put("content", new JSONArray()));

        JSONObject doc = AtlassianDocumentFormat.normalize(content);

        assertEquals("doc", doc.getString("type"));
        assertSame(content, doc.getJSONArray("content"));
    }

    @Test
    public void testNormalize_parsesAdfJsonObjectString() {
        String adfJson = "{\"version\":1,\"type\":\"doc\",\"content\":[]}";

        JSONObject doc = AtlassianDocumentFormat.normalize(adfJson);

        assertEquals("doc", doc.getString("type"));
        assertTrue(doc.getJSONArray("content").isEmpty());
    }

    @Test
    public void testNormalize_parsesAdfJsonArrayStringAsContent() {
        String adfJson = "[{\"type\":\"paragraph\",\"content\":[]}]";

        JSONObject doc = AtlassianDocumentFormat.normalize(adfJson);

        assertEquals("doc", doc.getString("type"));
        assertEquals(1, doc.getJSONArray("content").length());
    }

    @Test
    public void testNormalize_wrapsPlainString() {
        JSONObject doc = AtlassianDocumentFormat.normalize("Plain text value");

        assertEquals("doc", doc.getString("type"));
        assertEquals("Plain text value",
                doc.getJSONArray("content")
                        .getJSONObject(0)
                        .getJSONArray("content")
                        .getJSONObject(0)
                        .getString("text"));
    }

    @Test
    public void testNormalize_wrapsJiraWikiMarkupString() {
        // Wiki markup like {code} must NOT be treated as JSON
        String wiki = "{code}\nSome code\n{code}";

        JSONObject doc = AtlassianDocumentFormat.normalize(wiki);

        assertEquals("paragraph", doc.getJSONArray("content").getJSONObject(0).getString("type"));
        assertEquals(wiki,
                doc.getJSONArray("content")
                        .getJSONObject(0)
                        .getJSONArray("content")
                        .getJSONObject(0)
                        .getString("text"));
    }

    @Test
    public void testNormalize_taskListAdfPassesThrough() {
        String taskListJson = """
                {
                  "version": 1,
                  "type": "doc",
                  "content": [
                    {
                      "type": "taskList",
                      "attrs": { "localId": "" },
                      "content": [
                        { "type": "taskItem", "attrs": { "localId": "", "state": "DONE" }, "content": [{ "type": "text", "text": "E2E" }] }
                      ]
                    }
                  ]
                }
                """;

        JSONObject doc = AtlassianDocumentFormat.normalize(taskListJson);

        assertEquals("doc", doc.getString("type"));
        JSONObject taskList = doc.getJSONArray("content").getJSONObject(0);
        assertEquals("taskList", taskList.getString("type"));
        assertEquals("DONE", taskList.getJSONArray("content")
                .getJSONObject(0)
                .getJSONObject("attrs")
                .getString("state"));
    }
}
