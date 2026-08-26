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

        // 2. Text-match fallback for anchors from the old body that didn't make it over.
        for (Anchor anchor : oldAnchors == null ? List.<Anchor>of() : oldAnchors) {
            if (anchor == null || anchor.ref == null || anchor.text == null) {
                continue;
            }
            if (body.contains("ac:ref=\"" + anchor.ref + "\"")) {
                continue;
            }
            body = wrapFirstOccurrence(body, escapeXml(anchor.text), anchor.ref);
            if (!body.contains("ac:ref=\"" + anchor.ref + "\"")) {
                body = wrapFirstOccurrence(body, anchor.text, anchor.ref);
            }
        }

        // 3. Strip leftover placeholder tags so they never render as visible text.
        body = LEFTOVER_OPEN_PATTERN.matcher(body).replaceAll("");
        body = body.replace(PLACEHOLDER_CLOSE, "");
        return body;
    }

    private static String wrapFirstOccurrence(String body, String needle, String ref) {
        if (needle == null || needle.isEmpty()) {
            return body;
        }
        int idx = body.indexOf(needle);
        if (idx == -1) {
            return body;
        }
        return body.substring(0, idx)
                + "<ac:inline-comment-marker ac:ref=\"" + ref + "\">" + needle + "</ac:inline-comment-marker>"
                + body.substring(idx + needle.length());
    }

    private static String escapeXml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
