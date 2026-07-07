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
 *   <li>Extracts every {@code <table>} and converts it to a GitHub-Flavoured Markdown table
 *       (replacing it with a placeholder so CopyDown does not mangle it).</li>
 *   <li>Runs CopyDown on the remaining (table-less) HTML to get Markdown for everything else
 *       (paragraphs, bold/italic, links, lists, etc.) — which also naturally drops all
 *       {@code style}/{@code class} attributes, since Markdown has no equivalent.</li>
 *   <li>Restores the Markdown tables at their placeholders.</li>
 * </ol>
 *
 * <p>Used by {@link com.github.istin.dmtools.atlassian.confluence.ConfluenceStorageMarkdown}
 * (after its own Confluence-specific {@code <ac:*>}/{@code <ri:*>} tag preprocessing) and by
 * {@link com.github.istin.dmtools.testrail.TestRailClient} (for test case preconditions/steps,
 * which are stored as raw HTML and can contain large pasted tables with heavy inline styling).
 */
public final class HtmlToMarkdownConverter {

    private static final Logger logger = LogManager.getLogger(HtmlToMarkdownConverter.class);

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
     * Converts an HTML fragment to Markdown, preserving tables as GitHub-Flavoured Markdown
     * tables.
     *
     * @param html raw HTML fragment (does not need to be a full document)
     * @return Markdown text, or the original HTML if conversion fails for any reason
     */
    public static String convert(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        try {
            TableExtraction extraction = extractTables(html);
            String markdown = new CopyDown().convert(extraction.htmlWithoutTables);
            return restoreTables(markdown, extraction.tables);
        } catch (Exception e) {
            logger.warn("HTML→Markdown conversion failed, returning raw HTML: {}", e.getMessage(), e);
            return html;
        }
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
