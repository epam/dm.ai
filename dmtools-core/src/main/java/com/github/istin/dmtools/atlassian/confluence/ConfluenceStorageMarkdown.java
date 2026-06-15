// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.confluence;

import io.github.furstenheim.CopyDown;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;
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
 *   <li>{@code <ac:link><ri:attachment ri:filename="..."/>...}</ac:link>}
 *       → {@code [text](filename)}</li>
 *   <li>{@code <ac:link><ri:url ri:value="..."/>...}</ac:link>}
 *       → {@code [text](url)}</li>
 *   <li>{@code <ac:plain-text-link-body>} → plain text link body preserved</li>
 *   <li>{@code <ac:task>} with status + body → {@code - [ ] body} / {@code - [x] body}</li>
 *   <li>{@code <ac:adf-node type="extension">} (e.g. draw.io)
 *       → {@code [Diagram]}</li>
 *   <li>{@code <ac:structured-macro ac:name="toc">} → removed (auto-generated noise)</li>
 *   <li>{@code <table>} → GitHub-Flavoured Markdown table</li>
 * </ul>
 */
public final class ConfluenceStorageMarkdown {

    private static final Logger logger = LogManager.getLogger(ConfluenceStorageMarkdown.class);

    private static final String TABLE_PLACEHOLDER_PREFIX = "DMTABLEIDX";

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
            // 1. Fast regex-based pre-processing for the most common constructs.
            String preprocessed = preprocess(confluenceHtml);

            // 2. Jsoup-based handling for links/images that the regex does not cover
            //    (attachments, URLs, plain-text link bodies, links inside tables, etc.).
            String withStandardLinksAndImages = replaceRemainingAcLinksAndImages(preprocessed);

            // 3. Convert HTML tables to GitHub-Flavoured Markdown tables.
            //    Tables are replaced with placeholders because CopyDown does not
            //    generate Markdown tables on its own.
            TableExtraction extraction = extractTables(withStandardLinksAndImages);

            // 4. Run the generic HTML→Markdown converter on the table-less HTML.
            String markdown = new CopyDown().convert(extraction.htmlWithoutTables);

            // 5. Restore the Markdown tables at their placeholders.
            return restoreTables(markdown, extraction.tables);
        } catch (Exception e) {
            logger.warn("Confluence HTML→Markdown conversion failed, returning raw HTML: {}", e.getMessage(), e);
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

    /**
     * Uses Jsoup to convert any remaining {@code <ac:image>}, {@code <ac:link>}
     * and {@code <ac:structured-macro>} tags that the fast regex pre-processor
     * did not handle.
     *
     * <p>This covers:
     * <ul>
     *   <li>{@code <ac:image><ri:url ri:value="..."/></ac:image>}</li>
     *   <li>{@code <ac:link><ri:attachment ri:filename="..."/></ac:link>}</li>
     *   <li>{@code <ac:link><ri:url ri:value="..."/></ac:link>}</li>
     *   <li>{@code <ac:plain-text-link-body>}</li>
     *   <li>{@code <ac:structured-macro ac:name="code">} → fenced code block</li>
     *   <li>{@code <ac:structured-macro>} with rich-text-body → {@code <div>}</li>
     * </ul>
     */
    private static String replaceRemainingAcLinksAndImages(String html) {
        Document doc = parseFragment(html);

        for (Element image : doc.getElementsByTag("ac:image")) {
            String alt = image.attr("ac:alt");
            String src = null;

            Element attachment = image.getElementsByTag("ri:attachment").first();
            if (attachment != null) {
                src = attachment.attr("ri:filename");
            }
            Element url = image.getElementsByTag("ri:url").first();
            if (url != null) {
                src = url.attr("ri:value");
            }

            if (src == null || src.isBlank()) {
                src = alt;
            }
            if (alt == null || alt.isBlank()) {
                alt = src;
            }

            Element img = doc.createElement("img");
            if (src != null) img.attr("src", src);
            if (alt != null) img.attr("alt", alt);
            image.replaceWith(img);
        }

        for (Element link : doc.getElementsByTag("ac:link")) {
            String href = resolveLinkHref(link);
            String bodyHtml = resolveLinkBody(link);
            String fallbackText = href.isBlank() ? "link" : href;

            Element a = doc.createElement("a").attr("href", href);
            if (bodyHtml != null && !bodyHtml.isBlank()) {
                a.html(bodyHtml);
            } else {
                a.text(fallbackText);
            }
            link.replaceWith(a);
        }

        for (Element macro : doc.getElementsByTag("ac:structured-macro")) {
            String macroName = macro.attr("ac:name").toLowerCase();
            if ("code".equals(macroName)) {
                String language = "";
                for (Element param : macro.getElementsByTag("ac:parameter")) {
                    if ("language".equals(param.attr("ac:name"))) {
                        language = param.text();
                        break;
                    }
                }
                Element plainBody = macro.getElementsByTag("ac:plain-text-body").first();
                String code = plainBody != null ? plainBody.text() : "";
                Element pre = doc.createElement("pre");
                Element codeEl = doc.createElement("code");
                if (!language.isBlank()) {
                    codeEl.addClass("language-" + language);
                }
                codeEl.html(StringEscapeUtils.escapeHtml4(code));
                pre.appendChild(codeEl);
                macro.replaceWith(pre);
            } else if ("children".equals(macroName) || "childpages".equals(macroName)) {
                macro.replaceWith(doc.createElement("p").text("[Child pages]"));
            } else {
                Element richBody = macro.getElementsByTag("ac:rich-text-body").first();
                if (richBody != null) {
                    Element div = doc.createElement("div");
                    div.html(richBody.html());
                    macro.replaceWith(div);
                } else {
                    // Remove unknown macros so their parameter text doesn't leak into Markdown.
                    macro.remove();
                }
            }
        }

        return bodyHtml(doc);
    }

    private static String resolveLinkHref(Element link) {
        String anchor = link.attr("ac:anchor");
        String anchorSuffix = anchor.isBlank() ? "" : "#" + anchor;

        Element page = link.getElementsByTag("ri:page").first();
        if (page != null) {
            String space = page.attr("ri:space-key");
            String title = page.attr("ri:content-title");
            String href = (space.isBlank() ? "" : space + ":") + title;
            return href + anchorSuffix;
        }

        Element attachment = link.getElementsByTag("ri:attachment").first();
        if (attachment != null) {
            return attachment.attr("ri:filename") + anchorSuffix;
        }

        Element url = link.getElementsByTag("ri:url").first();
        if (url != null) {
            return url.attr("ri:value") + anchorSuffix;
        }

        Element user = link.getElementsByTag("ri:user").first();
        if (user != null) {
            String userKey = user.attr("ri:userkey");
            String username = user.attr("ri:username");
            return (username.isBlank() ? userKey : username) + anchorSuffix;
        }

        return anchorSuffix.isBlank() ? "" : anchorSuffix.substring(1);
    }

    private static String resolveLinkBody(Element link) {
        Element richBody = link.getElementsByTag("ac:link-body").first();
        if (richBody != null) {
            return richBody.html();
        }
        Element plainBody = link.getElementsByTag("ac:plain-text-link-body").first();
        if (plainBody != null) {
            // Escape HTML special chars so CDATA content such as "plain <text> link"
            // is treated as text rather than being re-parsed as HTML tags.
            return escapeHtml(plainBody.text());
        }
        return null;
    }

    private static String escapeHtml(String text) {
        if (text == null) {
            return null;
        }
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * Extracts all {@code <table>} elements, converts them to GitHub-Flavoured Markdown
     * tables, and replaces them with placeholders so that the remaining HTML can be
     * safely passed to CopyDown.
     */
    private static TableExtraction extractTables(String html) {
        Document doc = parseFragment(html);
        List<String> tables = new ArrayList<>();

        Elements tableElements = doc.getElementsByTag("table");
        int index = 0;
        for (Element table : tableElements) {
            String markdownTable = convertTableToMarkdown(table);
            tables.add(markdownTable);

            Element placeholder = doc.createElement("p").text(TABLE_PLACEHOLDER_PREFIX + index);
            table.replaceWith(placeholder);
            index++;
        }

        return new TableExtraction(bodyHtml(doc), tables);
    }

    private static String convertTableToMarkdown(Element table) {
        List<List<String>> rows = new ArrayList<>();
        int maxColumns = 0;
        boolean hasHeader = false;

        for (Element tr : table.getElementsByTag("tr")) {
            List<String> cells = new ArrayList<>();
            for (Element child : tr.children()) {
                String tag = child.tagName();
                if ("th".equalsIgnoreCase(tag) || "td".equalsIgnoreCase(tag)) {
                    if ("th".equalsIgnoreCase(tag)) {
                        hasHeader = true;
                    }
                    cells.add(cellToMarkdown(child));
                }
            }
            if (!cells.isEmpty()) {
                rows.add(cells);
                maxColumns = Math.max(maxColumns, cells.size());
            }
        }

        if (rows.isEmpty()) {
            return "";
        }

        StringBuilder markdown = new StringBuilder();
        for (int i = 0; i < rows.size(); i++) {
            appendMarkdownRow(markdown, rows.get(i), maxColumns);
            if (i == 0 && hasHeader) {
                appendMarkdownRow(markdown, null, maxColumns);
            }
        }
        return markdown.toString();
    }

    private static String cellToMarkdown(Element cell) {
        // Convert the cell's inner HTML to Markdown so that links, bold, etc. are preserved.
        String cellMarkdown = new CopyDown().convert(cell.html()).trim();
        // Markdown tables cannot contain newlines or pipe characters in cells.
        cellMarkdown = cellMarkdown.replace("|", "\\|");
        cellMarkdown = cellMarkdown.replaceAll("\\s+", " ").trim();
        return cellMarkdown;
    }

    private static void appendMarkdownRow(StringBuilder markdown, List<String> cells, int maxColumns) {
        markdown.append("|");
        for (int i = 0; i < maxColumns; i++) {
            if (cells == null) {
                markdown.append("---|");
            } else {
                String value = i < cells.size() ? cells.get(i) : "";
                markdown.append(" ").append(value).append(" |");
            }
        }
        markdown.append("\n");
    }

    private static String restoreTables(String markdown, List<String> tables) {
        String result = markdown;
        for (int i = 0; i < tables.size(); i++) {
            result = result.replace(TABLE_PLACEHOLDER_PREFIX + i, tables.get(i).trim());
        }
        return result;
    }

    private static Document parseFragment(String html) {
        Document doc = Jsoup.parseBodyFragment(html);
        doc.outputSettings().prettyPrint(false);
        return doc;
    }

    private static String bodyHtml(Document doc) {
        return doc.body() != null ? doc.body().html() : doc.html();
    }

    private static final class TableExtraction {
        final String htmlWithoutTables;
        final List<String> tables;

        TableExtraction(String htmlWithoutTables, List<String> tables) {
            this.htmlWithoutTables = htmlWithoutTables;
            this.tables = tables;
        }
    }
}
