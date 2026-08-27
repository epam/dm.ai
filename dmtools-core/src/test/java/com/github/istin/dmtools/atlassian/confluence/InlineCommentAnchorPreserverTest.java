// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.confluence;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link InlineCommentAnchorPreserver} — the heading-repair and
 * fallback text-match hardening.
 */
public class InlineCommentAnchorPreserverTest {

    private static final String REF_A = "a40ec14d-0d73-4f55-9a69-7026900b6623";
    private static final String REF_B = "11111111-2222-3333-4444-555555555555";

    // -------------------------------------------------------------------------
    // Heading repair (placeholder wrapped the Markdown heading prefix)
    // -------------------------------------------------------------------------

    @Test
    public void repairsMarkerWrappedHeadingParagraph() {
        String body = "<p><ac:inline-comment-marker ac:ref=\"" + REF_A + "\">## Purpose</ac:inline-comment-marker></p>";
        String result = InlineCommentAnchorPreserver.restoreAnchors(body, List.of());
        assertEquals("<h2><ac:inline-comment-marker ac:ref=\"" + REF_A + "\">Purpose</ac:inline-comment-marker></h2>", result);
    }

    @Test
    public void repairsHeadingLevels() {
        String body = "<p><ac:inline-comment-marker ac:ref=\"" + REF_A + "\"># Top</ac:inline-comment-marker></p>"
                + "<p>middle</p>"
                + "<p><ac:inline-comment-marker ac:ref=\"" + REF_B + "\">#### Deep</ac:inline-comment-marker></p>";
        String result = InlineCommentAnchorPreserver.restoreAnchors(body, List.of());
        assertTrue(result.contains("<h1><ac:inline-comment-marker ac:ref=\"" + REF_A + "\">Top</ac:inline-comment-marker></h1>"));
        assertTrue(result.contains("<h4><ac:inline-comment-marker ac:ref=\"" + REF_B + "\">Deep</ac:inline-comment-marker></h4>"));
        assertTrue(result.contains("<p>middle</p>"));
    }

    @Test
    public void doesNotTouchMarkersInsideProperHeadings() {
        String body = "<h2><ac:inline-comment-marker ac:ref=\"" + REF_A + "\">Purpose</ac:inline-comment-marker></h2>";
        String result = InlineCommentAnchorPreserver.restoreAnchors(body, List.of());
        assertEquals(body, result);
    }

    @Test
    public void doesNotTouchParagraphsWithTextAroundMarker() {
        String body = "<p>Prefix <ac:inline-comment-marker ac:ref=\"" + REF_A + "\">## Purpose</ac:inline-comment-marker> suffix</p>";
        String result = InlineCommentAnchorPreserver.restoreAnchors(body, List.of());
        assertEquals(body, result);
    }

    @Test
    public void placeholderWrappingHeadingIsRepairedEndToEnd() {
        String body = "<p>" + "[[ic:" + REF_A + "]]" + "## Purpose" + "[[/ic]]" + "</p>";
        String result = InlineCommentAnchorPreserver.restoreAnchors(body, List.of());
        assertEquals("<h2><ac:inline-comment-marker ac:ref=\"" + REF_A + "\">Purpose</ac:inline-comment-marker></h2>", result);
    }

    // -------------------------------------------------------------------------
    // Fallback text-match hardening
    // -------------------------------------------------------------------------

    @Test
    public void stripsMarkdownHeadingPrefixBeforeFallbackMatch() {
        String body = "<h2>Purpose</h2><p>content</p>";
        String result = InlineCommentAnchorPreserver.restoreAnchors(body,
                List.of(new InlineCommentAnchorPreserver.Anchor(REF_A, "## Purpose")));
        assertTrue(result.contains("<h2><ac:inline-comment-marker ac:ref=\"" + REF_A + "\">Purpose</ac:inline-comment-marker></h2>"));
    }

    @Test
    public void stripsListAndQuotePrefixesBeforeFallbackMatch() {
        String body = "<ul><li>Item one</li></ul>";
        String result = InlineCommentAnchorPreserver.restoreAnchors(body,
                List.of(new InlineCommentAnchorPreserver.Anchor(REF_A, "- Item one")));
        assertTrue(result.contains("ac:ref=\"" + REF_A + "\""));
    }

    @Test
    public void skipsTooShortSelections() {
        String body = "<p>some h2. text with h2. inside</p>";
        String result = InlineCommentAnchorPreserver.restoreAnchors(body,
                List.of(new InlineCommentAnchorPreserver.Anchor(REF_A, "h2.")));
        assertFalse("3-char selection must not be anchored", result.contains("inline-comment-marker"));
    }

    @Test
    public void respectsWordBoundaries() {
        String body = "<p>Purposedriven text</p>";
        String result = InlineCommentAnchorPreserver.restoreAnchors(body,
                List.of(new InlineCommentAnchorPreserver.Anchor(REF_A, "Purpose")));
        assertFalse("substring inside a longer word must not match", result.contains("inline-comment-marker"));
    }

    @Test
    public void findsLaterOccurrenceWhenFirstFailsBoundary() {
        String body = "<p>Purposes are many. Purpose matters.</p>";
        String result = InlineCommentAnchorPreserver.restoreAnchors(body,
                List.of(new InlineCommentAnchorPreserver.Anchor(REF_A, "Purpose")));
        assertTrue(result.contains("<ac:inline-comment-marker ac:ref=\"" + REF_A + "\">Purpose</ac:inline-comment-marker> matters."));
    }

    @Test
    public void doesNotMatchInsideTagAttributes() {
        String body = "<ac:link><ri:page ri:content-title=\"Purpose\"/></ac:link><p>real text</p>";
        String result = InlineCommentAnchorPreserver.restoreAnchors(body,
                List.of(new InlineCommentAnchorPreserver.Anchor(REF_A, "Purpose")));
        assertFalse("attribute value must not be wrapped", result.contains("inline-comment-marker"));
    }
}
