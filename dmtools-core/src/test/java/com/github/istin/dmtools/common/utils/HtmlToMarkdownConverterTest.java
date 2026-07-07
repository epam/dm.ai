// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.utils;

import org.junit.Test;

import static org.junit.Assert.*;

public class HtmlToMarkdownConverterTest {

    @Test
    public void testConvertNullOrBlankReturnsEmptyString() {
        assertEquals("", HtmlToMarkdownConverter.convert(null));
        assertEquals("", HtmlToMarkdownConverter.convert(""));
        assertEquals("", HtmlToMarkdownConverter.convert("   "));
    }

    @Test
    public void testConvertPlainParagraph() {
        String html = "<p>Hello world</p>";
        String markdown = HtmlToMarkdownConverter.convert(html);
        assertEquals("Hello world", markdown.trim());
    }

    @Test
    public void testConvertStripsInlineStylesAndClasses() {
        // Content pasted from Google Docs/a browser typically bakes every computed CSS
        // property into inline style attributes on every tag -- Markdown has no equivalent,
        // so CopyDown naturally drops all of it while preserving the actual text.
        String html = "<p style=\"margin: 0px 0px 12px; font-family: Arial, sans-serif; "
                + "color: rgb(32, 32, 32);\" class=\"foo\"><span style=\"font-weight: 400;\">"
                + "The screenshot is captured.</span></p>";
        String markdown = HtmlToMarkdownConverter.convert(html);
        assertFalse("converted markdown must not contain leftover style attributes", markdown.contains("style="));
        assertFalse("converted markdown must not contain leftover class attributes", markdown.contains("class="));
        assertTrue(markdown.contains("The screenshot is captured."));
    }

    @Test
    public void testConvertTableToGithubFlavouredMarkdown() {
        String html = "<table>"
                + "<tr><th>Sample ID</th><th>Status</th></tr>"
                + "<tr><td>Sample1</td><td>Passed</td></tr>"
                + "<tr><td>Sample2</td><td>Failed</td></tr>"
                + "</table>";
        String markdown = HtmlToMarkdownConverter.convert(html);

        assertTrue(markdown.contains("| Sample ID | Status |"));
        assertTrue(markdown.contains("---|"));
        assertTrue(markdown.contains("| Sample1 | Passed |"));
        assertTrue(markdown.contains("| Sample2 | Failed |"));
    }

    @Test
    public void testConvertTableWithHeavyInlineStylesShrinksDramatically() {
        StringBuilder styledCell = new StringBuilder();
        styledCell.append("<td style=\"margin: 0px 0px 12px; padding: 0px; border: 0px; "
                + "font-style: normal; font-variant-ligatures: normal; font-variant-caps: normal; "
                + "font-variant-numeric: inherit; font-variant-east-asian: inherit; "
                + "color: rgb(32, 32, 32); letter-spacing: normal;\">Sample1</td>");
        String html = "<table><tr>" + styledCell + styledCell + "</tr></table>";

        String markdown = HtmlToMarkdownConverter.convert(html);

        assertFalse(markdown.contains("style="));
        assertTrue(markdown.contains("Sample1"));
        // The whole point: a table cell with ~250 chars of inline CSS per cell should shrink
        // to a tiny fraction of its original size once styles are stripped.
        assertTrue("markdown should be dramatically smaller than the styled HTML input",
                markdown.length() < html.length() / 4);
    }

    @Test
    public void testConvertHandlesMultipleTablesAndPreservesOrder() {
        String html = "<p>Before</p>"
                + "<table><tr><td>A</td></tr></table>"
                + "<p>Between</p>"
                + "<table><tr><td>B</td></tr></table>"
                + "<p>After</p>";
        String markdown = HtmlToMarkdownConverter.convert(html);

        int beforeIdx = markdown.indexOf("Before");
        int aIdx = markdown.indexOf("| A |");
        int betweenIdx = markdown.indexOf("Between");
        int bIdx = markdown.indexOf("| B |");
        int afterIdx = markdown.indexOf("After");

        assertTrue(beforeIdx >= 0 && aIdx > beforeIdx);
        assertTrue(betweenIdx > aIdx && bIdx > betweenIdx);
        assertTrue(afterIdx > bIdx);
    }

    @Test
    public void testConvertEscapesPipeCharactersInsideTableCells() {
        String html = "<table><tr><td>A | B</td><td>C</td></tr></table>";
        String markdown = HtmlToMarkdownConverter.convert(html);
        assertTrue(markdown.contains("A \\| B"));
    }
}
