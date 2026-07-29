// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.utils;

import io.github.furstenheim.CopyDown;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;

/**
 * Generic, table-aware HTML-to-Markdown converter.
 *
 * <p>Plain {@link CopyDown} (a Java port of the "turndown" JS library) does not generate
 * Markdown tables on its own, and heavily-styled HTML (e.g. content pasted from Google Docs
 * or a browser, which bakes every computed CSS property into inline {@code style="..."}
 * attributes on every single tag) can bloat 1:1 HTML-preserving output by 20-30x with no
 * benefit for a human or LLM reader.
 *
 * <p>This converter:
 * <ol>
 *   <li>Extracts {@code <pre>/<code>} blocks and converts them to fenced code blocks so
 *       language information is preserved and the round-trip back to Confluence's
 *       {@code code} macro works.</li>
 *   <li>Extracts task lists ({@code <ul>} where every item starts with {@code [x]} or
 *       {@code [ ]}) and preserves GitHub-Flavoured Markdown task-list syntax.</li>
 *   <li>Extracts every {@code <table>} and converts it to a GitHub-Flavoured Markdown table
 *       (replacing it with a placeholder so CopyDown does not mangle it).</li>
 *   <li>Runs CopyDown on the remaining HTML to get Markdown for everything else
 *       (paragraphs, bold/italic, links, lists, etc.) — which also naturally drops all
 *       {@code style}/{@code class} attributes, since Markdown has no equivalent.</li>
 *   <li>Restores the extracted Markdown constructs at their placeholders.</li>
 * </ol>
 *
 * <p>Used by {@link com.github.istin.dmtools.atlassian.confluence.ConfluenceStorageMarkdown}
 * (after its own Confluence-specific {@code <ac:*>}/{@code <ri:*>} tag preprocessing) and by
 * {@link com.github.istin.dmtools.testrail.TestRailClient} (for test case preconditions/steps,
 * which are stored as raw HTML and can contain large pasted tables with heavy inline styling).
 */
public final class HtmlToMarkdownConverter {

    private static final Logger logger = LogManager.getLogger(HtmlToMarkdownConverter.class);

    private static final String CODE_PLACEHOLDER_PREFIX = "DMCODEIDX";
    private static final String TASK_PLACEHOLDER_PREFIX = "DMTASKIDX";
    private static final String TABLE_PLACEHOLDER_PREFIX = "DMTABLEIDX";

    private HtmlToMarkdownConverter() {}

    /**
     * Checks whether a requested output-format identifier means "convert to Markdown".
     * Shared across all integrations that expose a {@code format} parameter (Confluence,
     * TestRail, ...) so the accepted values never drift between them.
     *
     * @param format the format identifier (e.g. "md" or "markdown")
     * @return true if the format should trigger Markdown conversion
     */
    public static boolean isMarkdownFormat(String format) {
        return "md".equalsIgnoreCase(format) || "markdown".equalsIgnoreCase(format);
    }

    /**
     * Converts an HTML fragment to Markdown, preserving tables, fenced code blocks and
     * task-list syntax.
     *
     * @param html raw HTML fragment (does not need to be a full document)
     * @return Markdown text, or the original HTML if conversion fails for any reason
     */
    public static String convert(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        try {
            Extraction extraction = new Extraction(html);
            extraction.extractCodeBlocks();
            extraction.extractTaskLists();
            extraction.extractTables();

            String markdown = new CopyDown().convert(extraction.htmlWithoutExtracted);
            markdown = extraction.restore(markdown);
            return markdown;
        } catch (Exception e) {
            logger.warn("HTML->Markdown conversion failed, returning raw HTML: {}", e.getMessage(), e);
            return html;
        }
    }

    /**
     * Mutable extraction context. Holds the working HTML and the Markdown fragments that
     * have been replaced by placeholders, so they can be restored in the final pass.
     */
    private static final class Extraction {
        String htmlWithoutExtracted;
        final List<String> codeBlocks = new ArrayList<>();
        final List<String> taskLists = new ArrayList<>();
        final List<String> tables = new ArrayList<>();

        Extraction(String html) {
            this.htmlWithoutExtracted = html;
        }

        /**
         * Extracts {@code <pre>} and {@code <pre><code>} blocks, converts them to fenced
         * Markdown code blocks, and replaces them with placeholders.
         */
        void extractCodeBlocks() {
            Document doc = parseFragment(htmlWithoutExtracted);
            Elements pres = doc.getElementsByTag("pre");
            if (pres.isEmpty()) {
                htmlWithoutExtracted = bodyHtml(doc);
                return;
            }
            int index = 0;
            for (Element pre : pres) {
                String code = convertPreToMarkdown(pre);
                codeBlocks.add(code);
                Element placeholder = doc.createElement("p").text(CODE_PLACEHOLDER_PREFIX + index);
                pre.replaceWith(placeholder);
                index++;
            }
            htmlWithoutExtracted = bodyHtml(doc);
        }

        private static String convertPreToMarkdown(Element pre) {
            Element codeEl = pre.selectFirst("code");
            String language = "";
            if (codeEl != null) {
                String classAttr = codeEl.attr("class").trim();
                if (classAttr.startsWith("language-")) {
                    language = classAttr.substring("language-".length()).trim();
                } else if (!classAttr.isBlank()) {
                    language = classAttr;
                }
            }
            String text = codeEl != null ? codeEl.text() : pre.text();
            // Normalise trailing newline to keep fences tidy.
            text = text.replaceAll("\n+$", "").replaceAll("^\n+", "");
            return "```" + language + "\n" + text + "\n```";
        }

        /**
         * Extracts task lists ({@code <ul>} whose items start with {@code [x]} / {@code [ ]})
         * and preserves them as GFM task-list Markdown.
         */
        void extractTaskLists() {
            Document doc = parseFragment(htmlWithoutExtracted);
            Elements uls = doc.getElementsByTag("ul");
            int index = 0;
            for (Element ul : uls) {
                String taskList = convertUlToTaskListMarkdown(ul);
                if (taskList == null) {
                    continue;
                }
                taskLists.add(taskList);
                Element placeholder = doc.createElement("p").text(TASK_PLACEHOLDER_PREFIX + index);
                ul.replaceWith(placeholder);
                index++;
            }
            htmlWithoutExtracted = bodyHtml(doc);
        }

        private static String convertUlToTaskListMarkdown(Element ul) {
            StringBuilder sb = new StringBuilder();
            boolean isTaskList = false;
            for (Element li : ul.select("> li")) {
                String text = li.text().trim();
                String status;
                if (text.startsWith("[x] ") || text.equals("[x]")) {
                    status = "[x]";
                    text = text.substring("[x]".length()).trim();
                    isTaskList = true;
                } else if (text.startsWith("[X] ") || text.equals("[X]")) {
                    status = "[x]";
                    text = text.substring("[X]".length()).trim();
                    isTaskList = true;
                } else if (text.startsWith("[ ] ") || text.equals("[ ]")) {
                    status = "[ ]";
                    text = text.substring("[ ]".length()).trim();
                    isTaskList = true;
                } else {
                    return null;
                }
                if (sb.length() > 0) {
                    sb.append("\n");
                }
                sb.append("- ").append(status).append(" ").append(text);
            }
            return isTaskList ? sb.toString() : null;
        }

        /**
         * Extracts all {@code <table>} elements, converts them to GitHub-Flavoured Markdown
         * tables, and replaces them with placeholders.
         */
        void extractTables() {
            Document doc = parseFragment(htmlWithoutExtracted);
            Elements tableElements = doc.getElementsByTag("table");
            int index = 0;
            for (Element table : tableElements) {
                String markdownTable = convertTableToMarkdown(table);
                tables.add(markdownTable);
                Element placeholder = doc.createElement("p").text(TABLE_PLACEHOLDER_PREFIX + index);
                table.replaceWith(placeholder);
                index++;
            }
            htmlWithoutExtracted = bodyHtml(doc);
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
            String cellMarkdown = new CopyDown().convert(cell.html()).trim();
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

        String restore(String markdown) {
            String result = markdown;
            for (int i = 0; i < codeBlocks.size(); i++) {
                result = result.replace(CODE_PLACEHOLDER_PREFIX + i, codeBlocks.get(i).trim());
            }
            for (int i = 0; i < taskLists.size(); i++) {
                result = result.replace(TASK_PLACEHOLDER_PREFIX + i, taskLists.get(i).trim());
            }
            for (int i = 0; i < tables.size(); i++) {
                result = result.replace(TABLE_PLACEHOLDER_PREFIX + i, tables.get(i).trim());
            }
            return result;
        }
    }

    private static Document parseFragment(String html) {
        Document doc = Jsoup.parseBodyFragment(html);
        doc.outputSettings().prettyPrint(false);
        return doc;
    }

    private static String bodyHtml(Document doc) {
        return doc.body() != null ? doc.body().html() : doc.html();
    }
}
