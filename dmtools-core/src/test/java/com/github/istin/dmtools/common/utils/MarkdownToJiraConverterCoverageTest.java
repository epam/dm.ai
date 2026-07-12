// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.utils;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Additional coverage tests for {@link MarkdownToJiraConverter} targeting branches
 * not exercised by {@link MarkdownToJiraConverterTest}: null input, HTML-entity-only
 * input, top-level strong+ul / consecutive b / consecutive i / top-level a handling,
 * inline element accumulation, block fallback, pre/code variants, nested lists,
 * table colspan handling, markdown image / numbered-bold / unclosed code fences,
 * mixed content chunking and the public unescapeHtml helper.
 */
public class MarkdownToJiraConverterCoverageTest {

    @Test
    public void testNullInput() {
        assertEquals("", MarkdownToJiraConverter.convertToJiraMarkdown(null));
    }

    @Test
    public void testHtmlEntitiesOnlyInput() {
        assertEquals("<>&\"'", MarkdownToJiraConverter.convertToJiraMarkdown("&lt;&gt;&amp;&quot;&apos;"));
    }

    @Test
    public void testPlainTextWithAmpersandNotEntities() {
        assertEquals("A & B", MarkdownToJiraConverter.convertToJiraMarkdown("A & B"));
    }

    @Test
    public void testTopLevelStrongFollowedByUl() {
        String input = "<strong>Features</strong><ul><li>One</li><li>Two</li></ul>";
        String expected = "# *Features*\n* One\n* Two";
        assertEquals(expected, MarkdownToJiraConverter.convertToJiraMarkdown(input));
    }

    @Test
    public void testConsecutiveBoldElements() {
        String input = "<b>One</b><b>Two</b>";
        assertEquals("*One* *Two*", MarkdownToJiraConverter.convertToJiraMarkdown(input));
    }

    @Test
    public void testConsecutiveItalicElements() {
        String input = "<i>One</i><i>Two</i>";
        assertEquals("_One_ _Two_", MarkdownToJiraConverter.convertToJiraMarkdown(input));
    }

    @Test
    public void testTopLevelAnchor() {
        String input = "<a href=\"https://example.com\">Click here</a>";
        assertEquals("[Click here|https://example.com]",
                MarkdownToJiraConverter.convertToJiraMarkdown(input));
    }

    @Test
    public void testInlineElementAndTextNodeAccumulation() {
        String input = "<span>inline</span> plain text";
        assertEquals("inline plain text", MarkdownToJiraConverter.convertToJiraMarkdown(input));
    }

    @Test
    public void testEmBlockFallsBackToParagraph() {
        assertEquals("note", MarkdownToJiraConverter.convertToJiraMarkdown("<em>note</em>"));
    }

    @Test
    public void testHtmlHeadingLevels() {
        String input = "<h2>Sub</h2><h4>Deep</h4><h6>Deepest</h6>";
        assertEquals("h2. Sub\n\nh4. Deep\n\nh6. Deepest",
                MarkdownToJiraConverter.convertToJiraMarkdown(input));
    }

    @Test
    public void testEmptyParagraphRemoved() {
        String input = "<p></p><p>Text</p>";
        assertEquals("Text", MarkdownToJiraConverter.convertToJiraMarkdown(input));
    }

    @Test
    public void testParagraphWithInlineCodePlaceholder() {
        String input = "<p>Use <code>inlineCode</code> here</p>";
        assertEquals("Use {{inlineCode}} here", MarkdownToJiraConverter.convertToJiraMarkdown(input));
    }

    @Test
    public void testParagraphWithLinkAfterLineBreak() {
        String input = "<p>Design<br><a href=\"https://example.com\">https://example.com</a></p>";
        assertEquals("Design\n[https://example.com|https://example.com]",
                MarkdownToJiraConverter.convertToJiraMarkdown(input));
    }

    @Test
    public void testParagraphWithHtmlEntities() {
        String input = "<p>Fish &amp; Chips &lt;3</p>";
        assertEquals("Fish & Chips <3", MarkdownToJiraConverter.convertToJiraMarkdown(input));
    }

    @Test
    public void testPreWithInlineCodeOnly() {
        String input = "<pre><code>quick</code></pre>";
        assertEquals("{{quick}}", MarkdownToJiraConverter.convertToJiraMarkdown(input));
    }

    @Test
    public void testPreWithoutCodeElement() {
        String input = "<pre>plain pre text</pre>";
        assertEquals("plain pre text", MarkdownToJiraConverter.convertToJiraMarkdown(input));
    }

    @Test
    public void testPreWithLanguageClass() {
        String input = "<pre><code class=\"python\">print(1)</code></pre>";
        assertEquals("{code:python}print(1){code}",
                MarkdownToJiraConverter.convertToJiraMarkdown(input));
    }

    @Test
    public void testPreCodeNotPreservedByPreserver() {
        // case-sensitive preserver skips <PRE><CODE>, so processPre runs un-preserved
        String input = "<PRE><CODE class=\"python\">print(1)</CODE></PRE>";
        assertEquals("{code:python}\nprint(1)\n{code}",
                MarkdownToJiraConverter.convertToJiraMarkdown(input));
    }

    @Test
    public void testUppercaseCodeTagSingleLine() {
        // case-sensitive preserver skips <CODE>, so processCodeElement runs un-preserved
        String input = "<CODE>single</CODE>";
        assertEquals("{{single}}", MarkdownToJiraConverter.convertToJiraMarkdown(input));
    }

    @Test
    public void testUppercaseCodeTagMultiLineWithLanguage() {
        // whitespace inside <pre> is preserved by jsoup, so the multiline branch runs
        String input = "<PRE><CODE class=\"python\">line1\nline2</CODE></PRE>";
        assertEquals("{code:python}\nline1\nline2\n{code}",
                MarkdownToJiraConverter.convertToJiraMarkdown(input));
    }

    @Test
    public void testUnorderedListWithFormattingAndBreak() {
        String input = "<ul><li>First</li><li><strong>Bold</strong> and <em>em</em><br>next line</li></ul>";
        assertEquals("* First\n* *Bold* and _em_\n\nnext line",
                MarkdownToJiraConverter.convertToJiraMarkdown(input));
    }

    @Test
    public void testUnorderedListWithNestedOrderedList() {
        String input = "<ul><li>Parent<ol><li>Sub</li></ol></li></ul>";
        assertEquals("* Parent\n# Sub", MarkdownToJiraConverter.convertToJiraMarkdown(input));
    }

    @Test
    public void testUnorderedListWithNestedUnorderedList() {
        String input = "<ul><li>A<ul><li>B</li></ul></li></ul>";
        assertEquals("* A\n* B", MarkdownToJiraConverter.convertToJiraMarkdown(input));
    }

    @Test
    public void testUnorderedListSkipsEmptyItem() {
        String input = "<ul><li>   </li><li>Real</li></ul>";
        assertEquals("* Real", MarkdownToJiraConverter.convertToJiraMarkdown(input));
    }

    @Test
    public void testOrderedListPlainAndNestedOrderedList() {
        String input = "<ol><li>One</li><li>Two<ol><li>Sub</li></ol></li></ol>";
        assertEquals("# One\n# Two\n# Sub", MarkdownToJiraConverter.convertToJiraMarkdown(input));
    }

    @Test
    public void testOrderedListItemWithPreservedCodeBlock() {
        String input = "<ol><li><code class=\"java\">code line</code></li></ol>";
        assertEquals("{code:java}code line{code}",
                MarkdownToJiraConverter.convertToJiraMarkdown(input));
    }

    @Test
    public void testOrderedListWithStrongAndNestedUl() {
        String input = "<ol><li><strong>Group</strong><ul><li>Child</li></ul></li></ol>";
        assertEquals("# *Group*\n* Child", MarkdownToJiraConverter.convertToJiraMarkdown(input));
    }

    @Test
    public void testTableWithInvalidColspan() {
        String input = "<table><tr><th colspan=\"abc\">H1</th><th>H2</th></tr>"
                + "<tr><td>A</td><td>B</td></tr></table>";
        assertEquals("||H1||H2||\n|A|B|", MarkdownToJiraConverter.convertToJiraMarkdown(input));
    }

    @Test
    public void testTableWithHeaderColspan() {
        String input = "<table><tr><th colspan=\"2\">Wide</th></tr>"
                + "<tr><td>A</td><td>B</td></tr></table>";
        assertEquals("||Wide|| ||\n|A|B|", MarkdownToJiraConverter.convertToJiraMarkdown(input));
    }

    @Test
    public void testTableSkipsEmptyRow() {
        String input = "<table><tr><th>H</th></tr><tr> </tr><tr><td>A</td></tr></table>";
        assertEquals("||H||\n|A|", MarkdownToJiraConverter.convertToJiraMarkdown(input));
    }

    @Test
    public void testTableWithEmptyCells() {
        String input = "<table><tr><th></th></tr><tr><td></td></tr></table>";
        assertEquals("|| ||\n| |", MarkdownToJiraConverter.convertToJiraMarkdown(input));
    }

    @Test
    public void testTableCellWithPreservedCodeBlock() {
        String input = "<table><tr><td><code>cell</code></td></tr></table>";
        assertEquals("|{{cell}}|", MarkdownToJiraConverter.convertToJiraMarkdown(input));
    }

    @Test
    public void testTableCellWithColspan() {
        String input = "<table><tr><td colspan=\"2\">Wide</td></tr></table>";
        assertEquals("|Wide| |", MarkdownToJiraConverter.convertToJiraMarkdown(input));
    }

    @Test
    public void testMarkdownCodeBlockWithoutLanguage() {
        String input = "```\ncode\n```";
        assertEquals("{code:java}code{code}", MarkdownToJiraConverter.convertToJiraMarkdown(input));
    }

    @Test
    public void testMarkdownParagraphFlushedBeforeCodeBlock() {
        String input = "Intro text\n```js\ncode\n```";
        assertEquals("Intro text\n\n{code:js}code{code}",
                MarkdownToJiraConverter.convertToJiraMarkdown(input));
    }

    @Test
    public void testMarkdownUnclosedCodeBlockIsDropped() {
        String input = "```java\ncode";
        assertEquals("", MarkdownToJiraConverter.convertToJiraMarkdown(input));
    }

    @Test
    public void testMarkdownCodeBlockTrimsBlankLines() {
        String input = "```java\n\ncode\n\n```";
        assertEquals("{code:java}code{code}", MarkdownToJiraConverter.convertToJiraMarkdown(input));
    }

    @Test
    public void testMarkdownImageConversion() {
        String input = "![diagram|width=300]";
        assertEquals("!diagram|width=300!", MarkdownToJiraConverter.convertToJiraMarkdown(input));
    }

    @Test
    public void testMarkdownNumberedBoldItem() {
        String input = "1. **First step**";
        assertEquals("*First step*", MarkdownToJiraConverter.convertToJiraMarkdown(input));
    }

    @Test
    public void testMarkdownHeadingLevelThree() {
        assertEquals("h3. Sub heading",
                MarkdownToJiraConverter.convertToJiraMarkdown("### Sub heading"));
    }

    @Test
    public void testMarkdownInlineCodeBoldAndLink() {
        String input = "A ` spaced ` **bold** [text](http://example.com)";
        assertEquals("A {{spaced }} *bold* [text|http://example.com]",
                MarkdownToJiraConverter.convertToJiraMarkdown(input));
    }

    @Test
    public void testMarkdownHtmlInsideInlineCodeIsNotDetectedAsHtml() {
        String input = "Use `<div>` tag";
        assertEquals("Use {{<div>}} tag", MarkdownToJiraConverter.convertToJiraMarkdown(input));
    }

    @Test
    public void testMarkdownHtmlInsideCodeFenceIsNotDetectedAsHtml() {
        String input = "```html\n<div>x</div>\n```";
        assertEquals("{code:html}<div>x</div>{code}",
                MarkdownToJiraConverter.convertToJiraMarkdown(input));
    }

    @Test
    public void testMarkdownParagraphSplitByBlankLine() {
        String input = "Para one\n\nPara two";
        assertEquals("Para one\n\nPara two", MarkdownToJiraConverter.convertToJiraMarkdown(input));
    }

    @Test
    public void testMixedContentSkipsEmptyChunks() {
        String input = "# H\n\n\n\n<p>Para</p>\n\n";
        assertEquals("h1. H\n\nPara", MarkdownToJiraConverter.convertToJiraMarkdown(input));
    }

    @Test
    public void testUnescapeHtmlDecodesAllEntitiesAndNormalizesNewlines() {
        assertEquals("<a> \"q\" 'a'\n->b",
                MarkdownToJiraConverter.unescapeHtml("&lt;a&gt; &quot;q&quot; &apos;a&apos;\r\n-&gt;b"));
    }

    @Test
    public void testClassCanBeInstantiated() {
        assertNotNull(new MarkdownToJiraConverter());
    }

    @Test
    public void testMarkdownConsecutiveLinesJoinedIntoOneParagraph() {
        String input = "Line one\nLine two";
        assertEquals("Line one\nLine two", MarkdownToJiraConverter.convertToJiraMarkdown(input));
    }

    @Test
    public void testTopLevelCodeWithClassPreservedByPreserver() {
        String input = "<code class=\"java\">int x = 1;</code>";
        assertEquals("{code:java}int x = 1;{code}",
                MarkdownToJiraConverter.convertToJiraMarkdown(input));
    }

    @Test
    public void testUppercaseCodeTagWithLanguageClass() {
        // case-sensitive preserver skips <CODE>, so the class attr branch runs un-preserved
        String input = "<CODE class=\"js\">snippet</CODE>";
        assertEquals("{{snippet}}", MarkdownToJiraConverter.convertToJiraMarkdown(input));
    }

    @Test
    public void testPreCodeWithoutLanguageClassDefaultsToJava() {
        String input = "<PRE><CODE>code text</CODE></PRE>";
        assertEquals("{code:java}\ncode text\n{code}",
                MarkdownToJiraConverter.convertToJiraMarkdown(input));
    }

    @Test
    public void testOrderedListItemWithNestedUnorderedList() {
        String input = "<ol><li>Item<ul><li>Sub</li></ul></li></ol>";
        assertEquals("# Item\n* Sub", MarkdownToJiraConverter.convertToJiraMarkdown(input));
    }
}
