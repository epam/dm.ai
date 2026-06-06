// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.confluence;

import io.github.furstenheim.CopyDown;
import io.github.furstenheim.Options;
import io.github.furstenheim.OptionsBuilder;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests that Confluence Storage Format HTML converts to readable Markdown
 * without losing meaningful content (text, tables, headings, lists, macros stripped gracefully).
 */
public class ConfluenceHtmlToMarkdownTest {

    // Representative sample of Confluence Storage Format HTML:
    // - headings (h1-h3)
    // - paragraphs with bold/italic
    // - tables
    // - ordered/unordered lists
    // - ac:structured-macro (info panel, code block)
    // - ac:link (internal page links)
    // - special entities (&nbsp;, &mdash;, etc.)
    private static final String SAMPLE_CONFLUENCE_HTML =
        "<h1>Feature Overview</h1>" +
        "<p>This document describes the <strong>core business scenarios</strong> for the integration feature.</p>" +
        "<h2>Scenario 1: Basic Flow</h2>" +
        "<p>When the system receives <em>a single input record</em>, it generates one output file.</p>" +
        "<ul>" +
        "  <li>The output is stored in the designated storage location</li>" +
        "  <li>A notification is sent to the downstream service</li>" +
        "  <li>Status is updated to <strong>COMPLETED</strong></li>" +
        "</ul>" +
        "<h2>Scenario 2: Batch Processing</h2>" +
        "<p>When the system receives <em>multiple records</em>, it must process them in batches.</p>" +
        "<ac:structured-macro ac:name=\"info\" ac:schema-version=\"1\">" +
        "  <ac:parameter ac:name=\"title\">Note</ac:parameter>" +
        "  <ac:rich-text-body><p>TBD: Confirm whether batch splitting is one-per-type or one-per-group.</p></ac:rich-text-body>" +
        "</ac:structured-macro>" +
        "<table>" +
        "  <tbody>" +
        "    <tr><th>Field</th><th>Value</th><th>Required</th></tr>" +
        "    <tr><td>RECORD_TYPE</td><td>TYPE_A</td><td>Yes</td></tr>" +
        "    <tr><td>OUTPUT_FILE</td><td>output_v1.dat</td><td>Yes</td></tr>" +
        "    <tr><td>CONTROL_FLAG</td><td>ENABLED or empty</td><td>No</td></tr>" +
        "  </tbody>" +
        "</table>" +
        "<h3>Edge Cases</h3>" +
        "<ol>" +
        "  <li>Input has no records &mdash; output should be empty array</li>" +
        "  <li>Reference file not found &mdash; out of scope for this story</li>" +
        "</ol>" +
        "<p>See <ac:link><ri:page ri:content-title=\"Requirements and Specifications\" ri:space-key=\"PROJ\"/></ac:link> for field definitions.</p>" +
        "<ac:structured-macro ac:name=\"code\" ac:schema-version=\"1\">" +
        "  <ac:parameter ac:name=\"language\">json</ac:parameter>" +
        "  <ac:plain-text-body><![CDATA[{\"records\": [{\"type\": \"TYPE_A\"}]}]]></ac:plain-text-body>" +
        "</ac:structured-macro>";

    @Test
    public void testBasicConversion_preservesHeadings() {
        CopyDown converter = new CopyDown();
        String markdown = converter.convert(SAMPLE_CONFLUENCE_HTML);

        assertNotNull(markdown);
        assertFalse("Markdown should not be empty", markdown.isBlank());

        assertTrue("Should have h1", markdown.contains("Feature Overview"));
        assertTrue("Should have h2 Scenario 1", markdown.contains("Scenario 1"));
        assertTrue("Should have h2 Scenario 2", markdown.contains("Scenario 2"));
        assertTrue("Should have h3 Edge Cases", markdown.contains("Edge Cases"));

        System.out.println("=== HEADINGS OK ===");
    }

    @Test
    public void testBasicConversion_preservesBodyText() {
        CopyDown converter = new CopyDown();
        String markdown = converter.convert(SAMPLE_CONFLUENCE_HTML);

        assertTrue("Should preserve bold text", markdown.contains("core business scenarios"));
        assertTrue("Should preserve output mention", markdown.contains("output"));
        assertTrue("Should preserve storage mention", markdown.contains("storage location"));
        assertTrue("Should preserve TBD note from macro body", markdown.contains("TBD"));

        System.out.println("=== BODY TEXT OK ===");
    }

    @Test
    public void testBasicConversion_preservesTable() {
        CopyDown converter = new CopyDown();
        String markdown = converter.convert(SAMPLE_CONFLUENCE_HTML);

        assertTrue("Should have table header Field", markdown.contains("Field"));
        assertTrue("Should have RECORD_TYPE row (may be escaped)",
            markdown.contains("RECORD_TYPE") || markdown.contains("RECORD\\_TYPE"));
        assertTrue("Should have OUTPUT_FILE row (may be escaped)",
            markdown.contains("OUTPUT_FILE") || markdown.contains("OUTPUT\\_FILE"));
        assertTrue("Should have CONTROL_FLAG row (may be escaped)",
            markdown.contains("CONTROL_FLAG") || markdown.contains("CONTROL\\_FLAG"));
        assertTrue("Should have table value TYPE_A", markdown.contains("TYPE_A") || markdown.contains("TYPE\\_A"));

        System.out.println("=== TABLE OK ===");
    }

    @Test
    public void testBasicConversion_preservesLists() {
        CopyDown converter = new CopyDown();
        String markdown = converter.convert(SAMPLE_CONFLUENCE_HTML);

        assertTrue("Should have list item storage", markdown.contains("storage location"));
        assertTrue("Should have list item COMPLETED", markdown.contains("COMPLETED"));
        assertTrue("Should have numbered list item 1", markdown.contains("Input has no records"));
        assertTrue("Should have numbered list item 2", markdown.contains("out of scope"));

        System.out.println("=== LISTS OK ===");
    }

    @Test
    public void testBasicConversion_noRawHtmlTags() {
        CopyDown converter = new CopyDown();
        String markdown = converter.convert(SAMPLE_CONFLUENCE_HTML);

        // Should not contain raw ac: or ri: tags in output  
        assertFalse("Should not have raw <ac: tags", markdown.contains("<ac:"));
        assertFalse("Should not have raw <ri: tags", markdown.contains("<ri:"));

        System.out.println("=== NO RAW MACRO TAGS OK ===");
    }

    @Test
    public void testConversionOutput_printForReview() {
        CopyDown converter = new CopyDown();
        String markdown = converter.convert(SAMPLE_CONFLUENCE_HTML);

        System.out.println("=== FULL MARKDOWN OUTPUT ===");
        System.out.println(markdown);
        System.out.println("=== END OUTPUT ===");
        System.out.printf("Input HTML: %d chars%n", SAMPLE_CONFLUENCE_HTML.length());
        System.out.printf("Output Markdown: %d chars%n", markdown.length());
    }
}
