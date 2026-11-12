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
    // Strikethrough markers survive CopyDown as plain text and become ~~ in restore() (dm.ai#676).
    private static final String STRIKE_OPEN = "DMSTRIKEOPEN";
    private static final String STRIKE_CLOSE = "DMSTRIKECLOSE";
    private static final String BR_PLACEHOLDER = "DMBRIDX";

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
            extraction.markStrikethrough();

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
            // Convert innermost tables first so nested tables inside <td>/<th> cells
            // are rendered into compact inline rows before their enclosing outer table
            // iterates over its own direct <tr> rows.
            for (int i = tableElements.size() - 1; i >= 0; i--) {
                Element table = tableElements.get(i);
                boolean isNestedInTable = table.parent() != null && table.parent().closest("table") != null;
                if (isNestedInTable) {
                    String inlineNested = convertNestedTableToInlineHtml(table);
                    Element span = doc.createElement("span");
                    span.html(inlineNested);
                    table.replaceWith(span);
                } else {
                    String markdownTable = convertTableToMarkdown(table);
                    tables.add(markdownTable);
                    Element placeholder = doc.createElement("p").text(TABLE_PLACEHOLDER_PREFIX + index);
                    table.replaceWith(placeholder);
                    index++;
                }
            }
            htmlWithoutExtracted = bodyHtml(doc);
        }

        private static List<Element> directTableRows(Element table) {
            List<Element> trs = new ArrayList<>();
            for (Element child : table.children()) {
                String tag = child.tagName();
                if ("tr".equalsIgnoreCase(tag)) {
                    trs.add(child);
                } else if ("thead".equalsIgnoreCase(tag)
                        || "tbody".equalsIgnoreCase(tag)
                        || "tfoot".equalsIgnoreCase(tag)) {
                    for (Element row : child.children()) {
                        if ("tr".equalsIgnoreCase(row.tagName())) {
                            trs.add(row);
                        }
                    }
                }
            }
            return trs;
        }

        private static String convertNestedTableToInlineHtml(Element table) {
            List<String> renderedRows = new ArrayList<>();
            for (Element tr : directTableRows(table)) {
                List<String> cells = new ArrayList<>();
                for (Element child : tr.children()) {
                    String tag = child.tagName();
                    if ("th".equalsIgnoreCase(tag) || "td".equalsIgnoreCase(tag)) {
                        String cellText = cellToMarkdown(child);
                        if (!cellText.isEmpty()) {
                            cells.add(cellText);
                        }
                    }
                }
                if (!cells.isEmpty()) {
                    renderedRows.add(String.join(": ", cells));
                }
            }
            return String.join("<br/>", renderedRows);
        }

        private static String convertTableToMarkdown(Element table) {
            List<List<String>> rows = new ArrayList<>();
            int maxColumns = 0;
            boolean hasHeader = false;

            for (Element tr : directTableRows(table)) {
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
            String cellHtml = cell.html().replaceAll("(?i)<br\\s*/?>", " " + BR_PLACEHOLDER + " ");
            String cellMarkdown = new CopyDown().convert(cellHtml).trim();
            cellMarkdown = cellMarkdown.replace("|", "\\|");
            cellMarkdown = cellMarkdown.replaceAll("\\s+", " ").trim();
            cellMarkdown = cellMarkdown.replaceAll("\\s*" + BR_PLACEHOLDER + "\\s*", "<br/>");
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

        /**
         * Wraps the content of every {@code <s>}, {@code <del>}, {@code <strike>} and
         * {@code line-through} span in text markers; CopyDown has no rule for them and would
         * silently drop the strike. Table cells are handled by the table converter itself.
         */
        void markStrikethrough() {
            Document doc = parseFragment(htmlWithoutExtracted);
            Elements strikes = doc.select("s, del, strike, [style~=line-through]");
            if (strikes.isEmpty()) {
                return;
            }
            List<Element> nested = strikes.stream()
                    .filter(e -> e.parents().stream().anyMatch(p -> p.is("s, del, strike, [style~=line-through], code")))
                    .toList();
            for (Element strike : strikes) {
                if (nested.contains(strike)) {
                    strike.unwrap(); // nested strike or inline code: no extra markers
                    continue;
                }
                strike.prepend(STRIKE_OPEN);
                strike.append(STRIKE_CLOSE);
                strike.unwrap();
            }
            htmlWithoutExtracted = bodyHtml(doc);
        }

        /**
         * Merges touching struck runs, drops empty ones and moves edge whitespace outside the
         * markers (Markdown strikethrough cannot start or end with whitespace).
         */
        static String normalizeStrikeMarkers(String markdown) {
            if (!markdown.contains(STRIKE_OPEN)) {
                return markdown;
            }
            String result = markdown.replaceAll(STRIKE_OPEN + "(\\s*)" + STRIKE_CLOSE, "$1")
                    .replaceAll(STRIKE_OPEN + "(\\s+)", "$1" + STRIKE_OPEN)
                    .replaceAll("(\\s+)" + STRIKE_CLOSE, STRIKE_CLOSE + "$1")
                    .replace(STRIKE_CLOSE + STRIKE_OPEN, "");
            return result.replace(STRIKE_OPEN, "~~").replace(STRIKE_CLOSE, "~~");
        }

        String restore(String markdown) {
            String result = normalizeStrikeMarkers(markdown);
            // Tables first: their cells may still contain code/task placeholders.
            // Descending order keeps "PREFIX1" from matching inside "PREFIX10".
            for (int i = tables.size() - 1; i >= 0; i--) {
                result = result.replace(TABLE_PLACEHOLDER_PREFIX + i, tables.get(i).trim());
            }
            for (int i = taskLists.size() - 1; i >= 0; i--) {
                result = result.replace(TASK_PLACEHOLDER_PREFIX + i, taskLists.get(i).trim());
            }
            for (int i = codeBlocks.size() - 1; i >= 0; i--) {
                result = result.replace(CODE_PLACEHOLDER_PREFIX + i, codeBlocks.get(i).trim());
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
