// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HTMLCodeBlockPreserverTest {

    @Test
    public void testSimpleCodeBlock() {
        HTMLCodeBlockPreserver preserver = new HTMLCodeBlockPreserver();
        String input = "<code>Simple code</code>";

        String preserved = preserver.preserveCodeBlocks(input);
        assertEquals("<code>___CODE_BLOCK_PLACEHOLDER___0</code>", preserved);

        String restored = preserver.restoreCodeBlocks(preserved);
        assertEquals("<code>{{Simple code}}</code>", restored);
    }

    @Test
    public void testCodeBlockWithAttributes() {
        HTMLCodeBlockPreserver preserver = new HTMLCodeBlockPreserver();
        String input = "<code class=\"java\">public class Test {}</code>";

        String preserved = preserver.preserveCodeBlocks(input);
        assertEquals("<code>___CODE_BLOCK_PLACEHOLDER___0</code>", preserved);

        String restored = preserver.restoreCodeBlocks(preserved);
        assertEquals("<code>{code:java}public class Test {}{code}</code>", restored);
    }

    @Test
    public void testComplexExample() {
        HTMLCodeBlockPreserver preserver = new HTMLCodeBlockPreserver();
        String input =
                "<p><strong>Example:</strong></p>\n" +
                        "<code class=\"java\">" +
                        "public class Test {\n" +
                        "    List<String> items = new ArrayList<>();\n" +
                        "    // Comment\n" +
                        "}\n" +
                        "</code>\n" +
                        "<p>And another example:</p>\n" +
                        "<code>Simple code</code>";

        String preserved = preserver.preserveCodeBlocks(input);
        assertTrue(preserved.contains("<code>___CODE_BLOCK_PLACEHOLDER___0</code>"));
        assertTrue(preserved.contains("<code>___CODE_BLOCK_PLACEHOLDER___1</code>"));

        String restored = preserver.restoreCodeBlocks(preserved);
        String expected =
                "<p><strong>Example:</strong></p>\n" +
                        "<code>{code:java}public class Test {\n" +
                        "    List<String> items = new ArrayList<>();\n" +
                        "    // Comment\n" +
                        "}{code}</code>\n" +
                        "<p>And another example:</p>\n" +
                        "<code>{{Simple code}}</code>";
        assertEquals(expected, restored);
    }

}