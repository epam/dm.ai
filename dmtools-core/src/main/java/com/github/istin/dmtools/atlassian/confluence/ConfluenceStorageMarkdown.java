// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.confluence;

import io.github.furstenheim.CopyDown;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts Confluence Storage Format (XHTML) to readable Markdown.
 *
 * <p>Confluence uses its own XML dialect on top of HTML that includes
 * {@code <ac:*>} and {@code <ri:*>} tags which standard HTML-to-Markdown
 * converters cannot handle. This class pre-processes those tags into
 * standard HTML equivalents before delegating to {@link CopyDown}.
 *
 * <p>Handled Confluence constructs:
 * <ul>
 *   <li>{@code <ac:image><ri:attachment ri:filename="..."/></ac:image>}
 *       → {@code ![filename](filename)}</li>
 *   <li>{@code <ac:link><ri:page ri:content-title="..."/><ac:link-body>text</ac:link-body></ac:link>}
 *       → {@code [text](title)}</li>
 *   <li>{@code <ac:task>} with status + body → {@code - [ ] body} / {@code - [x] body}</li>
 *   <li>{@code <ac:adf-node type="extension">} (e.g. draw.io)
 *       → {@code [Diagram]}</li>
 *   <li>{@code <ac:structured-macro ac:name="toc">} → removed (auto-generated noise)</li>
 * </ul>
 */
public final class ConfluenceStorageMarkdown {

    private static final Logger logger = LogManager.getLogger(ConfluenceStorageMarkdown.class);

    // ac:image → img: <ac:image ... ac:alt="name.png" ...><ri:attachment .../></ac:image>
    private static final Pattern AC_IMAGE = Pattern.compile(
        "<ac:image[^>]*>\\s*<ri:attachment\\s+ri:filename=\"([^\"]+)\"[^/]*/?>\\s*</ac:image>",
        Pattern.DOTALL | Pattern.CASE_INSENSITIVE
    );

    // ac:image without alt — fall back to ri:filename
    private static final Pattern AC_IMAGE_ALT = Pattern.compile(
        "ac:alt=\"([^\"]+)\"",
        Pattern.CASE_INSENSITIVE
    );

    // ac:link with ri:page title and optional ac:link-body text
    private static final Pattern AC_LINK = Pattern.compile(
        "<ac:link[^>]*>\\s*<ri:page\\s+ri:content-title=\"([^\"]+)\"[^/]*/?>\\s*(?:<ac:link-body>(.*?)</ac:link-body>)?\\s*</ac:link>",
        Pattern.DOTALL | Pattern.CASE_INSENSITIVE
    );

    // ac:task-list wrapper — just unwrap it
    private static final Pattern AC_TASK_LIST = Pattern.compile(
        "<ac:task-list>(.*?)</ac:task-list>",
        Pattern.DOTALL | Pattern.CASE_INSENSITIVE
    );

    // individual ac:task
    private static final Pattern AC_TASK = Pattern.compile(
        "<ac:task>.*?<ac:task-status>(.*?)</ac:task-status>.*?<ac:task-body>(.*?)</ac:task-body>.*?</ac:task>",
        Pattern.DOTALL | Pattern.CASE_INSENSITIVE
    );

    // ac:adf-node (draw.io / ecosystem extensions)
    private static final Pattern AC_ADF_NODE = Pattern.compile(
        "<ac:adf-node[^>]*>.*?</ac:adf-node>",
        Pattern.DOTALL | Pattern.CASE_INSENSITIVE
    );

    // ac:structured-macro name="toc" (table of contents — auto-generated, noise)
    private static final Pattern AC_TOC = Pattern.compile(
        "<ac:structured-macro\\s+ac:name=\"toc\"[^>]*/?>",
        Pattern.DOTALL | Pattern.CASE_INSENSITIVE
    );

    private ConfluenceStorageMarkdown() {}

    /**
     * Converts Confluence Storage Format HTML to Markdown.
     *
     * @param confluenceHtml raw Confluence Storage Format (XHTML) string
     * @return Markdown text, or the original HTML if conversion fails
     */
    public static String toMarkdown(String confluenceHtml) {
        if (confluenceHtml == null || confluenceHtml.isBlank()) {
            return "";
        }
        try {
            String preprocessed = preprocess(confluenceHtml);
            return new CopyDown().convert(preprocessed);
        } catch (Exception e) {
            logger.warn("Confluence HTML→Markdown conversion failed, returning raw HTML: {}", e.getMessage());
            return confluenceHtml;
        }
    }

    /**
     * Pre-processes Confluence-specific tags into standard HTML so that
     * a generic HTML-to-Markdown converter can handle them.
     *
     * @param html Confluence Storage Format HTML
     * @return HTML with Confluence macros replaced by standard equivalents
     */
    static String preprocess(String html) {
        String result = html;

        // 1. Remove table-of-contents macro (auto-generated, adds noise)
        result = AC_TOC.matcher(result).replaceAll("");

        // 2. ac:image → <img src="filename" alt="filename">
        result = replaceAcImages(result);

        // 3. ac:link with ri:page → <a href="page title">link text</a>
        result = replaceAcLinks(result);

        // 4. ac:task-list / ac:task → <ul><li>[ ] body</li></ul>
        result = replaceAcTasks(result);

        // 5. ac:adf-node (draw.io, ecosystem apps) → [Diagram]
        result = AC_ADF_NODE.matcher(result).replaceAll("<p>[Diagram]</p>");

        return result;
    }

    private static String replaceAcImages(String html) {
        // First try to extract alt from the ac:image opening tag, then use ri:filename
        StringBuffer sb = new StringBuffer();
        Matcher m = AC_IMAGE.matcher(html);
        while (m.find()) {
            String filename = m.group(1);
            // Try to get alt from the full match's ac:image tag
            Matcher altMatcher = AC_IMAGE_ALT.matcher(m.group(0));
            String alt = altMatcher.find() ? altMatcher.group(1) : filename;
            m.appendReplacement(sb, Matcher.quoteReplacement(
                "<img src=\"" + filename + "\" alt=\"" + alt + "\" />"
            ));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String replaceAcLinks(String html) {
        StringBuffer sb = new StringBuffer();
        Matcher m = AC_LINK.matcher(html);
        while (m.find()) {
            String pageTitle = m.group(1);
            String linkBody = m.group(2);
            String text = (linkBody != null && !linkBody.isBlank()) ? linkBody : pageTitle;
            // Use page title as href (no real URL available without API call)
            m.appendReplacement(sb, Matcher.quoteReplacement(
                "<a href=\"" + pageTitle + "\">" + text + "</a>"
            ));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String replaceAcTasks(String html) {
        // First replace individual tasks within task-list
        StringBuffer sb = new StringBuffer();
        Matcher listMatcher = AC_TASK_LIST.matcher(html);
        while (listMatcher.find()) {
            String listContent = listMatcher.group(1);
            // Convert each ac:task inside to a list item
            StringBuffer tasksSb = new StringBuffer("<ul>");
            Matcher taskMatcher = AC_TASK.matcher(listContent);
            while (taskMatcher.find()) {
                String status = taskMatcher.group(1).trim().toLowerCase();
                String body = taskMatcher.group(2).trim();
                String checkbox = "complete".equals(status) ? "[x]" : "[ ]";
                tasksSb.append("<li>").append(checkbox).append(" ").append(body).append("</li>");
            }
            tasksSb.append("</ul>");
            listMatcher.appendReplacement(sb, Matcher.quoteReplacement(tasksSb.toString()));
        }
        listMatcher.appendTail(sb);
        return sb.toString();
    }
}
