// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.confluence;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Preserves Confluence inline comment anchors ({@code <ac:inline-comment-marker ac:ref="...">})
 * across page body replacements.
 *
 * <p>Any REST body update that drops these marker elements orphans the corresponding inline
 * comments: they stay in the API but disappear from the page UI. When a page is republished
 * from generated Markdown (e.g. via {@link MarkdownConfluenceSync}), the markers are lost
 * unless they are carried over explicitly.</p>
 *
 * <p>Strategy:</p>
 * <ol>
 *   <li>Agents may carry anchors through Markdown as literal
 *   {@code [[ic:REF]]anchored text[[/ic]]} placeholders (HTML comments do not survive the
 *   Markdown→storage conversion; literal tags do). These are converted back into real
 *   markers unconditionally — Confluence resolves the ref to the comment.</li>
 *   <li>For markers present in the OLD storage body but missing from the new one, the
 *   anchored plain text is matched in the new body (raw and XML-escaped variants) and
 *   re-wrapped with the original ref.</li>
 *   <li>Any leftover placeholder tags are stripped so they never render visibly.</li>
 * </ol>
 */
public final class InlineCommentAnchorPreserver {

    public static final String PLACEHOLDER_OPEN_PREFIX = "[[ic:";
    public static final String PLACEHOLDER_CLOSE = "[[/ic]]";

    private static final Pattern MARKER_PATTERN = Pattern.compile(
            "<ac:inline-comment-marker\\s+ac:ref=\"([^\"]+)\"[^>]*>([\\s\\S]*?)</ac:inline-comment-marker>");
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile(
            "\\[\\[ic:([0-9a-fA-F-]{36})\\]\\]([\\s\\S]*?)\\[\\[/ic\\]\\]");
    private static final Pattern LEFTOVER_OPEN_PATTERN = Pattern.compile("\\[\\[ic:[0-9a-fA-F-]{36}\\]\\]");
    private static final Pattern STRIP_TAGS_PATTERN = Pattern.compile("<[^>]+>");

    /** Minimum anchor-text length eligible for the fallback text-match. */
    static final int MIN_FALLBACK_ANCHOR_LENGTH = 4;

    /** Markdown structural prefixes stripped before the fallback text-match:
     *  heading hashes, list bullets/numbers, blockquotes (possibly chained). */
    private static final Pattern MD_STRUCTURAL_PREFIX = Pattern.compile(
            "^(?:#{1,6}\\s+|[-*+]\\s+|\\d+[.)]\\s+|>\\s*)+");

    /** A paragraph consisting solely of a marker whose content starts with heading
     *  hashes — the signature of a placeholder that wrapped a Markdown heading prefix
     *  and broke heading conversion. */
    private static final Pattern BROKEN_HEADING_PATTERN = Pattern.compile(
            "<p>\\s*(<ac:inline-comment-marker\\s+ac:ref=\"[^\"]+\"[^>]*>)\\s*(#{1,6})\\s+([\\s\\S]*?)(</ac:inline-comment-marker>)\\s*</p>");

    private InlineCommentAnchorPreserver() {
    }

    /**
     * An inline comment anchor: the marker ref (resolves to the comment) and the plain
     * text it wraps.
     */
    public static final class Anchor {
        public final String ref;
        public final String text;

        public Anchor(String ref, String text) {
            this.ref = ref;
            this.text = text;
        }
    }

    /**
     * Extracts inline comment markers from a storage-format body.
     * Nested tags inside the anchored text are stripped — anchors are plain text.
     */
    public static List<Anchor> extractMarkers(String storageBody) {
        List<Anchor> anchors = new ArrayList<>();
        if (storageBody == null) {
            return anchors;
        }
        Matcher m = MARKER_PATTERN.matcher(storageBody);
        while (m.find()) {
            String text = STRIP_TAGS_PATTERN.matcher(m.group(2)).replaceAll("");
            if (!m.group(1).isEmpty() && !text.trim().isEmpty()) {
                anchors.add(new Anchor(m.group(1), text));
            }
        }
        return anchors;
    }

    /**
     * Restores inline comment anchors in a fresh storage body. See the class javadoc for
     * the strategy. Returns the (possibly unchanged) body.
     */
    public static String restoreAnchors(String newStorageBody, List<Anchor> oldAnchors) {
        if (newStorageBody == null) {
            return null;
        }
        String body = newStorageBody;

        // 1. Agent-preserved placeholders → real markers (unconditional: Confluence
        //    resolves the ref to the comment even if the old body had no marker).
        Matcher pm = PLACEHOLDER_PATTERN.matcher(body);
        StringBuffer sb = new StringBuffer();
        while (pm.find()) {
            pm.appendReplacement(sb, Matcher.quoteReplacement(
                    "<ac:inline-comment-marker ac:ref=\"" + pm.group(1) + "\">" + pm.group(2) + "</ac:inline-comment-marker>"));
        }
        pm.appendTail(sb);
        body = sb.toString();

        // 1b. Repair headings broken by placeholders that wrapped the heading's Markdown
        //     prefix: a paragraph consisting solely of a marker whose content starts with
        //     heading hashes never became an <hN> during conversion — restore it.
        body = repairMarkerWrappedHeadings(body);

        // 2. Text-match fallback for anchors from the old body that didn't make it over.
        for (Anchor anchor : oldAnchors == null ? List.<Anchor>of() : oldAnchors) {
            if (anchor == null || anchor.ref == null || anchor.text == null) {
                continue;
            }
            if (body.contains("ac:ref=\"" + anchor.ref + "\"")) {
                continue;
            }
            String normalized = stripMarkdownStructuralPrefix(anchor.text);
            // Too short selections (e.g. a stray "h2." from legacy markup) match random
            // fragments — better to leave the comment orphaned than mis-anchor it.
            if (normalized.length() < MIN_FALLBACK_ANCHOR_LENGTH) {
                continue;
            }
            body = wrapFirstOccurrence(body, escapeXml(normalized), anchor.ref);
            if (!body.contains("ac:ref=\"" + anchor.ref + "\"")) {
                body = wrapFirstOccurrence(body, normalized, anchor.ref);
            }
        }

        // 3. Strip leftover placeholder tags so they never render as visible text.
        body = LEFTOVER_OPEN_PATTERN.matcher(body).replaceAll("");
        body = body.replace(PLACEHOLDER_CLOSE, "");
        return body;
    }

    /**
     * Converts a paragraph that consists solely of an inline comment marker whose content
     * starts with Markdown heading hashes into a proper heading element. This happens when
     * a {@code [[ic:REF]]...[[/ic]]} placeholder wrapped the heading's {@code ## } prefix in
     * the agent's Markdown — the line no longer parsed as a heading.
     */
    static String repairMarkerWrappedHeadings(String storageBody) {
        if (storageBody == null || storageBody.indexOf("inline-comment-marker") == -1) {
            return storageBody;
        }
        Matcher m = BROKEN_HEADING_PATTERN.matcher(storageBody);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            int level = m.group(2).length();
            String replacement = "<h" + level + ">" + m.group(1) + m.group(3) + m.group(4) + "</h" + level + ">";
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * Removes Markdown structural prefixes (heading hashes, list markers, blockquotes)
     * from an anchor text so the fallback text-match works against the rendered content.
     */
    static String stripMarkdownStructuralPrefix(String text) {
        if (text == null) {
            return "";
        }
        return MD_STRUCTURAL_PREFIX.matcher(text).replaceFirst("").trim();
    }

    private static String wrapFirstOccurrence(String body, String needle, String ref) {
        if (needle == null || needle.isEmpty()) {
            return body;
        }
        int from = 0;
        while (true) {
            int idx = body.indexOf(needle, from);
            if (idx == -1) {
                return body;
            }
            if (isWordBoundary(body, idx, needle.length())) {
                return body.substring(0, idx)
                        + "<ac:inline-comment-marker ac:ref=\"" + ref + "\">" + needle + "</ac:inline-comment-marker>"
                        + body.substring(idx + needle.length());
            }
            from = idx + 1;
        }
    }

    /**
     * Word-boundary check for the fallback text-match: the characters surrounding the
     * candidate occurrence must not be letters or digits (or a tag-open that would place
     * the match inside an attribute). Prevents short selections from latching onto
     * unrelated longer words or markup.
     */
    private static boolean isWordBoundary(String body, int idx, int length) {
        if (idx > 0) {
            char before = body.charAt(idx - 1);
            if (Character.isLetterOrDigit(before) || before == '"' || before == '<' || before == '=') {
                return false;
            }
        }
        int after = idx + length;
        if (after < body.length()) {
            char c = body.charAt(after);
            if (Character.isLetterOrDigit(c)) {
                return false;
            }
        }
        return true;
    }

    private static String escapeXml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
