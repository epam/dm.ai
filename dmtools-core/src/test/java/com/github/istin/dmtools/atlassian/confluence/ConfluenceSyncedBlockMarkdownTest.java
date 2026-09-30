// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.confluence;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests for Confluence synced blocks (bodied-sync-block) in
 * {@link ConfluenceStorageMarkdown} — dm.ai issue #596.
 *
 * A synced block reuses the {@code <ac:adf-node>} tag (same as draw.io /
 * ecosystem extensions) but carries its real content inline in
 * {@code <ac:adf-content>}. The generic rule that maps every adf-node to
 * {@code [Diagram]} must not drop that content.
 */
public class ConfluenceSyncedBlockMarkdownTest {

    private static String syncBlock(String innerContent) {
        return "<h2>2. Get pre-signed URLs</h2>"
                + "<ac:adf-extension>"
                + "<ac:adf-node type=\"bodied-sync-block\">"
                + "<ac:adf-attribute key=\"resource-id\">fedb2b81-0000</ac:adf-attribute>"
                + "<ac:adf-attribute key=\"local-id\">08922e0e-0000</ac:adf-attribute>"
                + "<ac:adf-content>"
                + innerContent
                + "</ac:adf-content>"
                + "</ac:adf-node>"
                + "</ac:adf-extension>";
    }

    @Test
    public void syncedBlockParagraphContentIsConvertedInline() {
        String md = ConfluenceStorageMarkdown.toMarkdown(
                syncBlock("<p>Mahler responds with the upload pre-signed URLs</p>"));

        assertTrue("synced block paragraph must survive conversion",
                md.contains("Mahler responds with the upload pre-signed URLs"));
        assertFalse("synced block must NOT be replaced by the [Diagram] placeholder",
                md.contains("Diagram"));
    }

    @Test
    public void syncedBlockCodeMacroContentIsPreserved() {
        String md = ConfluenceStorageMarkdown.toMarkdown(
                syncBlock("<ac:structured-macro ac:name=\"code\" ac:schema-version=\"1\">"
                        + "<ac:plain-text-body><![CDATA[{\"payload\": {\"request_id\": \"abc\"}}]]></ac:plain-text-body>"
                        + "</ac:structured-macro>"));

        assertTrue("code macro body inside a synced block must survive",
                md.contains("request_id"));
        assertFalse("synced block must NOT be replaced by the [Diagram] placeholder",
                md.contains("Diagram"));
    }

    @Test
    public void realDrawioExtensionStillBecomesDiagramPlaceholder() {
        // Control: a real draw.io / ecosystem extension (type="extension", no
        // readable content) must keep mapping to the [Diagram] placeholder.
        String md = ConfluenceStorageMarkdown.toMarkdown(
                "<ac:adf-extension>"
                        + "<ac:adf-node type=\"extension\">"
                        + "<ac:adf-attribute key=\"extension-key\">drawio</ac:adf-attribute>"
                        + "</ac:adf-node>"
                        + "</ac:adf-extension>");

        assertTrue("draw.io extension keeps the [Diagram] placeholder",
                md.contains("Diagram"));
    }
}
