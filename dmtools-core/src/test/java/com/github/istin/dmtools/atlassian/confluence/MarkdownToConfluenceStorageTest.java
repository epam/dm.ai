// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.confluence;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests that Markdown converts back into Confluence Storage Format (XHTML)
 * correctly, covering the inverse of {@link ConfluenceStorageMarkdown}.
 */
public class MarkdownToConfluenceStorageTest {

    @Test
    public void testEmptyAndNullInput() {
        assertEquals("", MarkdownToConfluenceStorage.toStorage(null));
        assertEquals("", MarkdownToConfluenceStorage.toStorage(""));
        assertEquals("", MarkdownToConfluenceStorage.toStorage("   \n  "));
    }

    @Test
    public void testHeadingsAndParagraphs() {
        String md = "# Feature Overview\n\nThis document describes the **core business scenarios**.";
        String storage = MarkdownToConfluenceStorage.toStorage(md);

        assertTrue("Should contain h1", storage.contains("<h1>Feature Overview</h1>"));
        assertTrue("Should contain paragraph", storage.contains("<p>"));
        assertTrue("Should preserve bold", storage.contains("<strong>core business scenarios</strong>"));
    }

    @Test
    public void testLists() {
        String md = "- First item\n- Second item\n- **Bold** item";
        String storage = MarkdownToConfluenceStorage.toStorage(md);

        assertTrue("Should contain unordered list", storage.contains("<ul>"));
        assertTrue("Should contain list items", storage.contains("<li>First item</li>"));
        assertTrue("Should contain bold inside list", storage.contains("<li><strong>Bold</strong> item</li>"));
    }

    @Test
    public void testOrderedList() {
        String md = "1. First\n2. Second";
        String storage = MarkdownToConfluenceStorage.toStorage(md);

        assertTrue("Should contain ordered list", storage.contains("<ol>"));
        assertTrue("Should contain list items", storage.contains("<li>First</li>"));
    }

    @Test
    public void testTable() {
        String md = "| Field | Value |\n|---|---|\n| A | 1 |\n| B | 2 |";
        String storage = MarkdownToConfluenceStorage.toStorage(md);

        assertTrue("Should contain table", storage.contains("<table>"));
        assertTrue("Should contain header cell", storage.contains("<th>Field</th>"));
        assertTrue("Should contain data cell", storage.contains("<td>A</td>"));
    }

    @Test
    public void testFencedCodeBlock() {
        String md = "```java\nSystem.out.println(\"hello\");\n```";
        String storage = MarkdownToConfluenceStorage.toStorage(md);

        assertTrue("Should contain code macro", storage.contains("ac:name=\"code\""));
        assertTrue("Should contain language parameter", storage.contains("<ac:parameter ac:name=\"language\">java</ac:parameter>"));
        assertTrue("Should contain CDATA body", storage.contains("<![CDATA[System.out.println(\"hello\");]]>"));
    }

    @Test
    public void testFencedCodeBlockPreservesNewlines() {
        // Regression test: code.text() (jsoup) collapses newlines into spaces, which
        // corrupts multi-line content such as Mermaid diagrams (statements run
        // together without a separator, producing invalid diagram syntax).
        String md = "```mermaid\nflowchart TD\n    A --> B\n    B --> C\n```";
        String storage = MarkdownToConfluenceStorage.toStorage(md);

        assertTrue("Should contain CDATA body with preserved newlines",
                storage.contains("<![CDATA[flowchart TD\n    A --> B\n    B --> C]]>"));
        assertFalse("Should not collapse newlines into spaces",
                storage.contains("flowchart TD A --> B B --> C"));
    }

    @Test
    public void testInlineCode() {
        String md = "Use `System.out.println` for output.";
        String storage = MarkdownToConfluenceStorage.toStorage(md);

        assertTrue("Should contain inline code", storage.contains("<code>System.out.println</code>"));
    }

    @Test
    public void testTaskList() {
        String md = "- [x] Done task\n- [ ] Todo task";
        String storage = MarkdownToConfluenceStorage.toStorage(md);

        assertTrue("Should contain task list", storage.contains("<ac:task-list>"));
        assertTrue("Should contain complete task", storage.contains("<ac:task-status>complete</ac:task-status>"));
        assertTrue("Should contain incomplete task", storage.contains("<ac:task-status>incomplete</ac:task-status>"));
        assertTrue("Should contain task body", storage.contains("<ac:task-body>Done task</ac:task-body>"));
        assertFalse("Should not contain raw checkbox input", storage.contains("<input"));
    }

    @Test
    public void testPageLink() {
        String md = "See [Requirements and Specifications](Requirements and Specifications) for details.";
        String storage = MarkdownToConfluenceStorage.toStorage(md);

        assertTrue("Should contain ac:link", storage.contains("<ac:link>"));
        assertTrue("Should contain ri:page", storage.contains("<ri:page"));
        assertTrue("Should contain content title", storage.contains("ri:content-title=\"Requirements and Specifications\""));
        assertTrue("Should contain link body", storage.contains("<ac:link-body>Requirements and Specifications</ac:link-body>"));
    }

    @Test
    public void testAttachmentLink() {
        String md = "[Download](doc.pdf)";
        String storage = MarkdownToConfluenceStorage.toStorage(md);

        assertTrue("Should contain ac:link", storage.contains("<ac:link>"));
        assertTrue("Should contain ri:attachment", storage.contains("<ri:attachment"));
        assertTrue("Should contain filename", storage.contains("ri:filename=\"doc.pdf\""));
        assertTrue("Should contain link body", storage.contains("<ac:link-body>Download</ac:link-body>"));
    }

    @Test
    public void testExternalLinkKeptAsAnchor() {
        String md = "Visit [Example](http://example.com).";
        String storage = MarkdownToConfluenceStorage.toStorage(md);

        assertTrue("Should contain standard anchor", storage.contains("<a href=\"http://example.com\">Example</a>"));
        assertFalse("Should not convert external link to ac:link", storage.contains("<ac:link>"));
    }

    @Test
    public void testPageLinkWithSpaceKey() {
        String md = "See [Home](PROJ:Home).";
        String storage = MarkdownToConfluenceStorage.toStorage(md);

        assertTrue("Should contain ri:page with space key", storage.contains("ri:space-key=\"PROJ\""));
        assertTrue("Should contain content title", storage.contains("ri:content-title=\"Home\""));
    }

    @Test
    public void testPageLinkToSameSpaceTitleStartingWithWordAndColonIsNotMisparsedAsSpaceKey() {
        // Regression test: a same-space page whose own title is "SingleWord: Rest of title"
        // (e.g. "Recommendations: Primary Biospecimen QC at Accessioning") must NOT be
        // misinterpreted as an explicit "SPACE:Title" cross-space reference just because
        // the first word before the colon happens to look like a valid space key. Real
        // page titles always have a space after the colon, unlike the legacy "PROJ:Home"
        // syntax (no space after colon) — see testPageLinkWithSpaceKey.
        String md = "See [Recommendations](Recommendations: Primary Biospecimen QC at Accessioning).";
        String storage = MarkdownToConfluenceStorage.toStorage(md);

        assertFalse("Should not contain a bogus ri:space-key", storage.contains("ri:space-key="));
        assertTrue("Should keep the whole string as the content title",
                storage.contains("ri:content-title=\"Recommendations: Primary Biospecimen QC at Accessioning\""));
    }

    @Test
    public void testLocalImageConvertedToAttachment() {
        String md = "![architecture.png](architecture.png)";
        String storage = MarkdownToConfluenceStorage.toStorage(md);

        assertTrue("Should contain ac:image", storage.contains("<ac:image"));
        assertTrue("Should contain ri:attachment", storage.contains("<ri:attachment"));
        assertTrue("Should contain filename", storage.contains("ri:filename=\"architecture.png\""));
        assertTrue("Should preserve alt", storage.contains("ac:alt=\"architecture.png\""));
    }

    @Test
    public void testExternalImageKeptAsImg() {
        String md = "![logo](https://example.com/logo.png)";
        String storage = MarkdownToConfluenceStorage.toStorage(md);

        assertTrue("Should contain standard img", storage.contains("<img src=\"https://example.com/logo.png\""));
        assertFalse("Should not convert external image to ac:image", storage.contains("<ac:image>"));
    }

    @Test
    public void testRoundTripWithExistingSample() {
        // This is the Markdown produced by ConfluenceStorageMarkdown from the test sample.
        String md = "# Feature Overview\n\n" +
                "This document describes the **core business scenarios** for the integration feature.\n\n" +
                "## Scenario 1: Basic Flow\n\n" +
                "When the system receives *a single input record*, it generates one output file.\n\n" +
                "- The output is stored in the designated storage location\n" +
                "- A notification is sent to the downstream service\n" +
                "- Status is updated to **COMPLETED**\n\n" +
                "## Scenario 2: Batch Processing\n\n" +
                "When the system receives *multiple records*, it must process them in batches.\n\n" +
                "TBD: Confirm whether batch splitting is one-per-type or one-per-group.\n\n" +
                "| Field | Value | Required |\n" +
                "|---|---|---|\n" +
                "| RECORD_TYPE | TYPE_A | Yes |\n" +
                "| OUTPUT_FILE | output_v1.dat | Yes |\n" +
                "| CONTROL_FLAG | ENABLED or empty | No |\n\n" +
                "### Related Pages\n\n" +
                "See [Requirements and Specifications](Requirements and Specifications) for field definitions.\n\n" +
                "Also check [API Design](API Design).\n\n" +
                "### Architecture Diagram\n\n" +
                "![architecture.png](architecture.png)\n\n" +
                "![flow-diagram.png](flow-diagram.png)\n\n" +
                "[Diagram]\n\n" +
                "### Review Checklist\n\n" +
                "- [x] Define API contract\n" +
                "- [ ] Write integration tests\n" +
                "- [ ] Get approval from stakeholders\n\n" +
                "### Edge Cases\n\n" +
                "1. Input has no records — output should be empty array\n" +
                "2. Reference file not found — out of scope for this story";

        String storage = MarkdownToConfluenceStorage.toStorage(md);

        assertTrue("Should contain h1", storage.contains("<h1>Feature Overview</h1>"));
        assertTrue("Should contain h2", storage.contains("<h2>Scenario 1: Basic Flow</h2>"));
        assertTrue("Should contain h3", storage.contains("<h3>Related Pages</h3>"));
        assertTrue("Should contain page link", storage.contains("ri:content-title=\"Requirements and Specifications\""));
        assertTrue("Should contain image attachment", storage.contains("ri:filename=\"architecture.png\""));
        assertTrue("Should contain task list", storage.contains("<ac:task-list>"));
        assertTrue("Should contain table", storage.contains("<table>"));
        assertFalse("Should not leak raw markdown", storage.contains("## "));
    }

    @Test
    public void testPrintOutputForReview() {
        String md = "# Heading\n\nParagraph with **bold** and *italic* and `code`.\n\n" +
                "| A | B |\n|---|---|\n| 1 | 2 |\n\n" +
                "```java\nSystem.out.println();\n```\n\n" +
                "- [x] task\n\n" +
                "[Page](Page Title)\n\n" +
                "[File](file.pdf)";
        String storage = MarkdownToConfluenceStorage.toStorage(md);

        System.out.println("=== CONFLUENCE STORAGE OUTPUT ===");
        System.out.println(storage);
        System.out.println("=== END OUTPUT ===");

        assertFalse("Output should not be empty", storage.isBlank());
    }

    @Test
    public void testRelativePathAttachmentLink() {
        String md = "[Download](./attachments/document.pdf)";
        String storage = MarkdownToConfluenceStorage.toStorage(md);

        assertTrue("Should contain ac:link", storage.contains("<ac:link>"));
        assertTrue("Should normalize to filename", storage.contains("ri:filename=\"document.pdf\""));
        assertTrue("Should preserve link body", storage.contains("<ac:link-body>Download</ac:link-body>"));
    }

    @Test
    public void testRelativePathImage() {
        String md = "![Architecture diagram](images/architecture.png)";
        String storage = MarkdownToConfluenceStorage.toStorage(md);

        assertTrue("Should contain ac:image", storage.contains("<ac:image"));
        assertTrue("Should normalize to filename", storage.contains("ri:filename=\"architecture.png\""));
        assertTrue("Should preserve alt text", storage.contains("ac:alt=\"Architecture diagram\""));
    }

    @Test
    public void testBackslashPathAttachmentLink() {
        String md = "[Download](assets\\report.xlsx)";
        String storage = MarkdownToConfluenceStorage.toStorage(md);

        assertTrue("Should contain ac:link", storage.contains("<ac:link>"));
        assertTrue("Should normalize backslash path to filename", storage.contains("ri:filename=\"report.xlsx\""));
    }

    @Test
    public void testExtractAttachmentReferences() {
        String md = "# Doc\n\n" +
                "See [spec](docs/spec.pdf) and [diagram](./images/arch.png).\n\n" +
                "![screenshot](screenshots/screen.png)\n\n" +
                "External [link](https://example.com/file.pdf) and [page](Some Page) should be ignored.";

        java.util.Set<String> refs = MarkdownToConfluenceStorage.extractAttachmentReferences(md);

        assertEquals("Should extract three attachment references", 3, refs.size());
        assertTrue("Should include spec.pdf", refs.contains("spec.pdf"));
        assertTrue("Should include arch.png", refs.contains("arch.png"));
        assertTrue("Should include screen.png", refs.contains("screen.png"));
        assertFalse("Should not include external URL", refs.contains("https://example.com/file.pdf"));
        assertFalse("Should not include page reference", refs.contains("Some Page"));
    }

    @Test
    public void testExtractAttachmentReferencesIgnoresAnchors() {
        String md = "[Anchor](#section) and [file](file.pdf)";
        java.util.Set<String> refs = MarkdownToConfluenceStorage.extractAttachmentReferences(md);

        assertEquals(1, refs.size());
        assertTrue(refs.contains("file.pdf"));
    }

    @Test
    public void testExtractAttachmentReferencePathsKeepsRelativePaths() {
        String md = "See [spec](docs/spec.pdf) and ![diagram](./images/arch.png).";
        java.util.Set<String> refs = MarkdownToConfluenceStorage.extractAttachmentReferencePaths(md);

        assertEquals(2, refs.size());
        assertTrue(refs.contains("docs/spec.pdf"));
        assertTrue(refs.contains("./images/arch.png"));
    }
}
