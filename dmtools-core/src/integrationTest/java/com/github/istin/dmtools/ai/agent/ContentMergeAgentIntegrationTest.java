// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.ai.agent;

import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class ContentMergeAgentIntegrationTest {

    @Test
    public void testMergeHtmlContent() throws Exception {
        ContentMergeAgent agent = new ContentMergeAgent();
        ContentMergeAgent.Params params = new ContentMergeAgent.Params(
                "Give me merged html content of 2 pages",
                "<html><head><title>Page 1</title></head><body><h1>Welcome</h1><p>This is page 1.</p></body></html>",
                "<html><head><title>Page 2</title></head><body><h1>Welcome</h1><p>This is page 2.</p></body></html>",
                "html"
        );

        String result = agent.run(params);
        assertTrue(result.contains("This is page 1."));
        assertTrue(result.contains("This is page 2."));
    }

    @Test
    public void testMergeMermaidContent() throws Exception {
        ContentMergeAgent agent = new ContentMergeAgent();
        ContentMergeAgent.Params params = new ContentMergeAgent.Params(
                "Give me merged flow diagram",
                "graph TD;\nA-->B;",
                "graph TD;\nB-->C;",
                "mermaid"
        );

        String result = agent.run(params);
        assertTrue(result.contains("A-->B"));
        assertTrue(result.contains("B-->C"));
    }

    @Test
    public void testMergeTextContent() throws Exception {
        ContentMergeAgent agent = new ContentMergeAgent();
        ContentMergeAgent.Params params = new ContentMergeAgent.Params(
                "Give me merged content of the documents",
                "Hello, this is the first document.",
                "Hello, this is the second document.",
                "text"
        );

        String result = agent.run(params);
        assertTrue(result.contains("Hello, this is the first document."));
        assertTrue(result.contains("Hello, this is the second document."));
    }
}