// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.utils;

import com.github.istin.dmtools.atlassian.confluence.MarkdownToConfluenceStorage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Strikethrough between Markdown and Jira / Confluence (epam/dm.ai#676): ~~text~~ is written as
 * struck-through text, and struck-through HTML read back from Confluence returns as ~~text~~.
 */
class StrikethroughMarkdownTest {

    // ---- AC1: Markdown -> Jira wiki ----

    @Test
    void markdownToJira_strikeBecomesDashes() {
        assertEquals("plain -gone- and *bold* and _it_",
                MarkdownToJiraConverter.convertToJiraMarkdown("plain ~~gone~~ and **bold** and _it_"));
    }

    @Test
    void markdownToJira_hyphenatedWordsAndRangesAreUntouched() {
        assertEquals("a-b-c 1-2 -x-", MarkdownToJiraConverter.convertToJiraMarkdown("a-b-c 1-2 ~~x~~"));
        assertEquals("well-known - not struck -",
                MarkdownToJiraConverter.convertToJiraMarkdown("well-known - not struck -"));
    }

    @Test
    void markdownToJira_codeAndLinkTargetsAreProtected() {
        assertEquals("{{~~code~~}} -y-", MarkdownToJiraConverter.convertToJiraMarkdown("`~~code~~` ~~y~~"));
        assertEquals("[~~l~~|http://x/~~a~~] -z-",
                MarkdownToJiraConverter.convertToJiraMarkdown("[~~l~~](http://x/~~a~~) ~~z~~"));
    }

    @Test
    void markdownToJira_unbalancedOrSpacedTildesStayLiteral() {
        assertEquals("~~ not~~ a~~b~~c", MarkdownToJiraConverter.convertToJiraMarkdown("~~ not~~ a~~b~~c"));
        assertEquals("only ~~open", MarkdownToJiraConverter.convertToJiraMarkdown("only ~~open"));
    }

    @Test
    void markdownToJira_noTildes_exactlyAsBefore() {
        assertEquals("h2. Title\n*bold* {{c}} [t|http://u]",
                MarkdownToJiraConverter.convertToJiraMarkdown("## Title\n**bold** `c` [t](http://u)"));
        assertNull(MarkdownToJiraConverter.convertStrikethrough(null));
    }

    // ---- AC2: Markdown -> Confluence storage ----

    @Test
    void markdownToStorage_strikeBecomesDel() {
        assertEquals("<p>plain <del>gone</del> and <strong>bold</strong> and <em>it</em></p>",
                MarkdownToConfluenceStorage.toStorage("plain ~~gone~~ and **bold** and _it_").trim());
    }

    @Test
    void markdownToStorage_strikeNestedInLinkAndInsideCodeStaysLiteral() {
        String storage = MarkdownToConfluenceStorage.toStorage("[~~l~~](http://x) `~~code~~`");
        assertTrue(storage.contains("<del>l</del>"), storage);
        assertTrue(storage.contains("<code>~~code~~</code>"), storage);
    }

    // ---- AC3 / AC5: HTML -> Markdown ----

    @Test
    void htmlToMarkdown_allFourStrikeFormsBecomeTildes() {
        assertEquals("a ~~s~~ ~~d~~ ~~o~~ ~~c~~ b",
                HtmlToMarkdownConverter.convert("<p>a <s>s</s> <del>d</del> <strike>o</strike> "
                        + "<span style=\"text-decoration: line-through;\">c</span> b</p>"));
    }

    @Test
    void htmlToMarkdown_strikeNestedInBoldLinkAndList() {
        assertEquals("**~~bd~~** [~~lnk~~](http://x)",
                HtmlToMarkdownConverter.convert("<p><strong><del>bd</del></strong> <a href=\"http://x\"><s>lnk</s></a></p>"));
        assertTrue(HtmlToMarkdownConverter.convert("<ul><li><del>item</del></li></ul>").contains("~~item~~"));
    }

    @Test
    void htmlToMarkdown_codeBlocksAreNeverTransformed() {
        String md = HtmlToMarkdownConverter.convert("<pre><code>~~keep~~</code></pre><p><del>x</del></p>");
        assertTrue(md.contains("```\n~~keep~~\n```"), md);
        assertTrue(md.contains("~~x~~"), md);
    }

    @Test
    void htmlToMarkdown_withoutStrikeIsUnchanged() {
        assertEquals("**b** and _i_", HtmlToMarkdownConverter.convert("<p><strong>b</strong> and <em>i</em></p>"));
        assertEquals("", HtmlToMarkdownConverter.convert(null));
    }

    // ---- AC6: round trip ----

    @Test
    void roundTrip_markdownToStorageAndBack_keepsStrike() {
        String storage = MarkdownToConfluenceStorage.toStorage("keep ~~this~~ please");
        assertEquals("keep ~~this~~ please", HtmlToMarkdownConverter.convert(storage).trim());
    }
}
