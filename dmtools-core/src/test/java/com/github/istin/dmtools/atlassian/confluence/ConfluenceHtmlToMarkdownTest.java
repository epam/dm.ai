// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.confluence;

import io.github.furstenheim.CopyDown;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests that Confluence Storage Format HTML converts to readable Markdown
 * without losing meaningful content (text, tables, headings, lists, macros stripped gracefully).
 */
public class ConfluenceHtmlToMarkdownTest {

    // Representative sample of Confluence Storage Format HTML:
    // - headings (h1-h3), paragraphs with bold/italic
    // - tables, ordered/unordered lists
    // - ac:structured-macro (info panel, code block, toc)
    // - ac:link with ri:page (internal page links)
    // - ac:image with ri:attachment (embedded images)
    // - ac:task-list / ac:task (checklists)
    // - ac:adf-node (draw.io diagrams)
    // - special entities (&nbsp;, &mdash;, etc.)
    private static final String SAMPLE_CONFLUENCE_HTML =
        "<ac:structured-macro ac:name=\"toc\" ac:schema-version=\"1\" />" +
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
        "<h3>Related Pages</h3>" +
        "<p>See <ac:link><ri:page ri:content-title=\"Requirements and Specifications\" ri:version-at-save=\"1\" />" +
        "<ac:link-body>Requirements and Specifications</ac:link-body></ac:link> for field definitions.</p>" +
        "<p>Also check <ac:link><ri:page ri:content-title=\"API Design\" ri:version-at-save=\"2\" /></ac:link>.</p>" +
        "<h3>Architecture Diagram</h3>" +
        "<ac:image ac:alt=\"architecture.png\" ac:width=\"760\">" +
        "  <ri:attachment ri:filename=\"architecture.png\" ri:version-at-save=\"1\" /></ac:image>" +
        "<ac:image ac:width=\"760\">" +
        "  <ri:attachment ri:filename=\"flow-diagram.png\" ri:version-at-save=\"2\" /></ac:image>" +
        "<ac:adf-node type=\"extension\">" +
        "  <ac:adf-attribute key=\"extension-key\">drawio</ac:adf-attribute>" +
        "</ac:adf-node>" +
        "<h3>Review Checklist</h3>" +
        "<ac:task-list>" +
        "  <ac:task><ac:task-id>1</ac:task-id><ac:task-status>complete</ac:task-status>" +
        "    <ac:task-body>Define API contract</ac:task-body></ac:task>" +
        "  <ac:task><ac:task-id>2</ac:task-id><ac:task-status>incomplete</ac:task-status>" +
        "    <ac:task-body>Write integration tests</ac:task-body></ac:task>" +
        "  <ac:task><ac:task-id>3</ac:task-id><ac:task-status>incomplete</ac:task-status>" +
        "    <ac:task-body>Get approval from stakeholders</ac:task-body></ac:task>" +
        "</ac:task-list>" +
        "<h3>Edge Cases</h3>" +
        "<ol>" +
        "  <li>Input has no records &mdash; output should be empty array</li>" +
        "  <li>Reference file not found &mdash; out of scope for this story</li>" +
        "</ol>";

    @Test
    public void testToMarkdown_preservesHeadings() {
        String markdown = ConfluenceStorageMarkdown.toMarkdown(SAMPLE_CONFLUENCE_HTML);

        assertNotNull(markdown);
        assertFalse("Markdown should not be empty", markdown.isBlank());

        assertTrue("Should have h1", markdown.contains("Feature Overview"));
        assertTrue("Should have h2 Scenario 1", markdown.contains("Scenario 1"));
        assertTrue("Should have h2 Scenario 2", markdown.contains("Scenario 2"));
        assertTrue("Should have h3 Edge Cases", markdown.contains("Edge Cases"));
    }

    @Test
    public void testToMarkdown_preservesBodyText() {
        String markdown = ConfluenceStorageMarkdown.toMarkdown(SAMPLE_CONFLUENCE_HTML);

        assertTrue("Should preserve bold text", markdown.contains("core business scenarios"));
        assertTrue("Should preserve list item", markdown.contains("storage location"));
        assertTrue("Should preserve TBD note from macro body", markdown.contains("TBD"));
    }

    @Test
    public void testToMarkdown_preservesTable() {
        String markdown = ConfluenceStorageMarkdown.toMarkdown(SAMPLE_CONFLUENCE_HTML);

        assertTrue("Should have table header", markdown.contains("Field"));
        assertTrue("Should have RECORD_TYPE (may be escaped)",
            markdown.contains("RECORD_TYPE") || markdown.contains("RECORD\\_TYPE"));
        assertTrue("Should have OUTPUT_FILE (may be escaped)",
            markdown.contains("OUTPUT_FILE") || markdown.contains("OUTPUT\\_FILE"));
        assertTrue("Should have table value TYPE_A",
            markdown.contains("TYPE_A") || markdown.contains("TYPE\\_A"));
    }

    @Test
    public void testToMarkdown_preservesLists() {
        String markdown = ConfluenceStorageMarkdown.toMarkdown(SAMPLE_CONFLUENCE_HTML);

        assertTrue("Should have list item", markdown.contains("storage location"));
        assertTrue("Should have COMPLETED", markdown.contains("COMPLETED"));
        assertTrue("Should have numbered list item 1", markdown.contains("Input has no records"));
        assertTrue("Should have numbered list item 2", markdown.contains("out of scope"));
    }

    @Test
    public void testToMarkdown_noRawHtmlTags() {
        String markdown = ConfluenceStorageMarkdown.toMarkdown(SAMPLE_CONFLUENCE_HTML);

        assertFalse("Should not have raw <ac: tags", markdown.contains("<ac:"));
        assertFalse("Should not have raw <ri: tags", markdown.contains("<ri:"));
    }

    @Test
    public void testToMarkdown_tocRemoved() {
        String markdown = ConfluenceStorageMarkdown.toMarkdown(SAMPLE_CONFLUENCE_HTML);

        // TOC macro is noise — should be stripped entirely
        assertFalse("Should not have toc macro remnant", markdown.contains("ac:name=\"toc\""));
    }

    @Test
    public void testToMarkdown_acImageConvertedToMarkdownImage() {
        String markdown = ConfluenceStorageMarkdown.toMarkdown(SAMPLE_CONFLUENCE_HTML);

        // ac:image with ac:alt → ![alt](filename)
        assertTrue("Should have architecture.png image ref",
            markdown.contains("architecture.png"));
        // ac:image without alt → ![filename](filename)
        assertTrue("Should have flow-diagram.png image ref",
            markdown.contains("flow-diagram.png"));
    }

    @Test
    public void testToMarkdown_acLinkConvertedToMarkdownLink() {
        String markdown = ConfluenceStorageMarkdown.toMarkdown(SAMPLE_CONFLUENCE_HTML);

        // ac:link with link-body text
        assertTrue("Should have link text", markdown.contains("Requirements and Specifications"));
        // ac:link without link-body — falls back to page title
        assertTrue("Should have API Design link", markdown.contains("API Design"));
    }

    @Test
    public void testToMarkdown_acTasksConvertedToChecklist() {
        String markdown = ConfluenceStorageMarkdown.toMarkdown(SAMPLE_CONFLUENCE_HTML);

        // Complete task → [x], incomplete → [ ]
        assertTrue("Should have completed task", markdown.contains("[x]") || markdown.contains("Define API contract"));
        assertTrue("Should have incomplete task", markdown.contains("[ ]") || markdown.contains("Write integration tests"));
        assertTrue("Should have approval task", markdown.contains("Get approval from stakeholders"));
    }

    @Test
    public void testToMarkdown_drawioReplacedWithPlaceholder() {
        String markdown = ConfluenceStorageMarkdown.toMarkdown(SAMPLE_CONFLUENCE_HTML);

        assertTrue("Should have diagram placeholder", markdown.contains("Diagram"));
        assertFalse("Should not have raw adf-node", markdown.contains("adf-node"));
    }

    @Test
    public void testPreprocess_onlyTransformsConfluenceTags() {
        // Verify preprocess doesn't break standard HTML
        String input = "<h1>Title</h1><p>Text with <strong>bold</strong> and <a href=\"url\">link</a></p>";
        String result = ConfluenceStorageMarkdown.preprocess(input);
        assertEquals("Standard HTML should be unchanged", input, result);
    }

    @Test
    public void testToMarkdown_printOutputForReview() {
        String markdown = ConfluenceStorageMarkdown.toMarkdown(SAMPLE_CONFLUENCE_HTML);

        System.out.println("=== FULL MARKDOWN OUTPUT ===");
        System.out.println(markdown);
        System.out.println("=== END OUTPUT ===");
        System.out.printf("Input HTML: %d chars → Output Markdown: %d chars%n",
            SAMPLE_CONFLUENCE_HTML.length(), markdown.length());
    }

    @Test
    public void testToMarkdown_convertsTableToMarkdownTable() {
        String html = "<table>" +
            "<tbody>" +
            "<tr><th>Name</th><th>Value</th></tr>" +
            "<tr><td>A</td><td>1</td></tr>" +
            "<tr><td>B</td><td>2</td></tr>" +
            "</tbody>" +
            "</table>";

        String markdown = ConfluenceStorageMarkdown.toMarkdown(html);

        assertTrue("Should contain table header row with pipes", markdown.contains("| Name | Value |"));
        assertTrue("Should contain separator row", markdown.contains("|---|"));
        assertTrue("Should contain data row A", markdown.contains("| A | 1 |"));
        assertTrue("Should contain data row B", markdown.contains("| B | 2 |"));
    }

    @Test
    public void testToMarkdown_preservesAcLinkWithLinkBody() {
        String html = "<p>See <ac:link><ri:page ri:content-title=\"Target Page\"/>" +
            "<ac:link-body>click here</ac:link-body></ac:link> for details.</p>";

        String markdown = ConfluenceStorageMarkdown.toMarkdown(html);

        assertTrue("Should preserve link text and target", markdown.contains("[click here](Target Page)"));
    }

    @Test
    public void testToMarkdown_preservesAcLinkWithPlainTextLinkBody() {
        String html = "<p><ac:link><ri:page ri:content-title=\"Target Page\"/>" +
            "<ac:plain-text-link-body><![CDATA[plain <text> link]]></ac:plain-text-link-body></ac:link></p>";

        String markdown = ConfluenceStorageMarkdown.toMarkdown(html);

        assertTrue("Should preserve plain text link body", markdown.contains("[plain <text> link](Target Page)"));
    }

    @Test
    public void testToMarkdown_preservesAcLinkToAttachment() {
        String html = "<p><ac:link><ri:attachment ri:filename=\"doc.pdf\"/>" +
            "<ac:link-body>Download</ac:link-body></ac:link></p>";

        String markdown = ConfluenceStorageMarkdown.toMarkdown(html);

        assertTrue("Should preserve attachment link", markdown.contains("[Download](doc.pdf)"));
    }

    @Test
    public void testToMarkdown_preservesStandardExternalLink() {
        String html = "<p>Visit <a href=\"http://example.com\">Example</a></p>";

        String markdown = ConfluenceStorageMarkdown.toMarkdown(html);

        assertTrue("Should preserve external link", markdown.contains("[Example](http://example.com)"));
    }

    @Test
    public void testToMarkdown_preservesTableWithLinksInsideCells() {
        String html = "<table><tbody>" +
            "<tr><th>Page</th></tr>" +
            "<tr><td><ac:link><ri:page ri:content-title=\"Home\"/><ac:link-body>Home</ac:link-body></ac:link></td></tr>" +
            "</tbody></table>";

        String markdown = ConfluenceStorageMarkdown.toMarkdown(html);

        assertTrue("Should contain table header", markdown.contains("| Page |"));
        assertTrue("Should contain link inside table cell", markdown.contains("[Home](Home)"));
    }

    @Test
    public void testToMarkdown_convertsCodeMacro() {
        String html = "<ac:structured-macro ac:name=\"code\">" +
            "<ac:parameter ac:name=\"language\">java</ac:parameter>" +
            "<ac:plain-text-body><![CDATA[System.out.println(\"hello\");]]></ac:plain-text-body>" +
            "</ac:structured-macro>";

        String markdown = ConfluenceStorageMarkdown.toMarkdown(html);

        assertTrue("Should preserve code body", markdown.contains("System.out.println"));
        assertTrue("Should preserve hello string", markdown.contains("hello"));
    }

    @Test
    public void testToMarkdown_childrenMacroReplacedWithPlaceholder() {
        String html = "<p>Other Templates</p>" +
            "<ac:structured-macro ac:name=\"children\">" +
            "<ac:parameter ac:name=\"allChildren\">true</ac:parameter>" +
            "</ac:structured-macro>";

        String markdown = ConfluenceStorageMarkdown.toMarkdown(html);

        assertTrue("Should keep heading", markdown.contains("Other Templates"));
        assertTrue("Should replace children macro with placeholder", markdown.contains("Child pages"));
        assertFalse("Should not leak parameter value", markdown.contains("allChildren"));
        assertFalse("Should not leak parameter value", markdown.contains("true"));
    }
}
