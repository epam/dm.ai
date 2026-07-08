// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.confluence;

import com.github.istin.dmtools.common.utils.HtmlToMarkdownConverter;
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
 * standard HTML equivalents before delegating to {@link HtmlToMarkdownConverter}.
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

            // 3. Delegate table-aware HTML→Markdown conversion (table extraction,
            //    CopyDown, table restoration) to the shared, generic converter.
            return HtmlToMarkdownConverter.convert(withStandardLinksAndImages);
        } catch (Exception e) {
            logger.warn("Confluence HTML→Markdown conversion failed, returning raw HTML: {}", e.getMessage(), e);
            return confluenceHtml;
        }
    }

    /**
     * Structured macros whose Markdown output is already complete/meaningful from
     * {@code body.storage} alone (their content lives in the storage document itself,
     * or we already render them into a sensible placeholder). Anything NOT in this set
     * is treated as a candidate for {@link #toMarkdownWithRenderedFallback} — most
     * commonly third-party "live"/aggregated-table macros (e.g. table-excerpt-include,
     * livesearch, pivot-table, sparkline, content-report-table, multiexcerpt-include,
     * jiraissues, ...) whose actual output is computed by Confluence only when the page
     * is rendered, and is therefore absent from {@code body.storage}.
     */
    private static final java.util.Set<String> SAFE_LOCAL_MACROS = java.util.Set.of(
        "toc", "code", "info", "note", "warning", "tip", "panel",
        "expand", "status", "children", "childpages", "anchor", "excerpt"
    );

    /**
     * Converts Confluence Storage Format HTML to Markdown, using the page's rendered
     * {@code body.export_view} HTML as a fallback source for sections whose storage
     * representation only contains a "live"/aggregated-table macro's configuration
     * (i.e. no usable content) instead of real data.
     *
     * <p>Confluence macros that aggregate or transclude data from elsewhere (a common
     * pattern for third-party "table filter/chart" style macros) only compute their
     * actual output when the page is rendered server-side; {@code body.storage} only
     * stores the macro's configuration parameters. {@code body.export_view} is
     * Confluence's own rendered HTML for the page, with every macro already resolved
     * into real markup (including such live tables).
     *
     * <p>Matching between the two documents is done by aligning top-level heading
     * sections (by heading text, falling back to positional order for unmatched
     * headings). For any storage section that contains an unresolved/unknown
     * structured macro and whose matching export_view section contains an actual
     * {@code <table>}, the storage section's content is replaced by the export_view
     * section's content before the normal Markdown conversion pipeline runs. All other
     * sections are left untouched, so existing behavior (ac:link/ac:image resolution
     * to clean titles/filenames, etc.) is unaffected.
     *
     * @param storageHtml raw Confluence Storage Format (XHTML) string ({@code body.storage.value})
     * @param exportViewHtml rendered Confluence HTML ({@code body.export_view.value}), may be null/blank
     * @return Markdown text
     */
    public static String toMarkdownWithRenderedFallback(String storageHtml, String exportViewHtml) {
        if (exportViewHtml == null || exportViewHtml.isBlank()) {
            return toMarkdown(storageHtml);
        }
        if (storageHtml == null || storageHtml.isBlank()) {
            return toMarkdown(exportViewHtml);
        }
        try {
            String merged = mergeUnresolvedSectionsWithRenderedContent(storageHtml, exportViewHtml);
            return toMarkdown(merged);
        } catch (Exception e) {
            logger.warn("Storage/export_view merge failed, falling back to storage-only Markdown: {}", e.getMessage(), e);
            return toMarkdown(storageHtml);
        }
    }

    private static String mergeUnresolvedSectionsWithRenderedContent(String storageHtml, String exportViewHtml) {
        Document storageDoc = parseFragment(storageHtml);
        Document exportDoc = parseFragment(exportViewHtml);

        List<Section> storageSections = buildSections(storageDoc);
        List<Section> exportSections = buildSections(exportDoc);

        java.util.Map<String, java.util.ArrayDeque<Section>> exportByHeading = new java.util.HashMap<>();
        List<Section> exportHeadingSectionsInOrder = new ArrayList<>();
        for (Section section : exportSections) {
            if (section.headingElement == null) {
                continue;
            }
            exportHeadingSectionsInOrder.add(section);
            exportByHeading
                .computeIfAbsent(section.headingText, k -> new java.util.ArrayDeque<>())
                .add(section);
        }

        int headingOrdinal = 0;
        for (Section storageSection : storageSections) {
            if (storageSection.headingElement == null) {
                continue; // Preamble content before the first heading is left untouched.
            }
            int ordinal = headingOrdinal++;
            if (!needsRenderedFallback(storageSection.bodyElements)) {
                continue;
            }

            Section matched = pollMatchingSection(storageSection.headingText, exportByHeading, ordinal, exportHeadingSectionsInOrder);
            if (matched == null || !containsTable(matched.bodyElements)) {
                continue;
            }
            replaceSectionBody(storageSection, matched);
        }

        return bodyHtml(storageDoc);
    }

    private static Section pollMatchingSection(
            String headingText,
            java.util.Map<String, java.util.ArrayDeque<Section>> byText,
            int ordinal,
            List<Section> ordinalList
    ) {
        java.util.ArrayDeque<Section> queue = byText.get(headingText);
        if (queue != null && !queue.isEmpty()) {
            return queue.poll();
        }
        if (ordinal < ordinalList.size()) {
            return ordinalList.get(ordinal);
        }
        return null;
    }

    private static boolean needsRenderedFallback(List<Element> bodyElements) {
        for (Element el : bodyElements) {
            for (Element macro : el.getElementsByTag("ac:structured-macro")) {
                if (!SAFE_LOCAL_MACROS.contains(macro.attr("ac:name").toLowerCase())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean containsTable(List<Element> bodyElements) {
        for (Element el : bodyElements) {
            if (!el.getElementsByTag("table").isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static void replaceSectionBody(Section storageSection, Section replacementSection) {
        StringBuilder replacementHtml = new StringBuilder();
        for (Element el : replacementSection.bodyElements) {
            replacementHtml.append(el.outerHtml());
        }
        if (replacementHtml.length() == 0) {
            return;
        }
        if (!storageSection.bodyElements.isEmpty()) {
            storageSection.bodyElements.get(0).before(replacementHtml.toString());
            for (Element el : storageSection.bodyElements) {
                el.remove();
            }
        } else {
            storageSection.headingElement.after(replacementHtml.toString());
        }
    }

    /**
     * Splits a document's top-level body elements into sections, one per heading
     * ({@code h1}-{@code h6}), plus a leading "preamble" section (heading == null)
     * for any content before the first heading.
     */
    private static List<Section> buildSections(Document doc) {
        List<Section> sections = new ArrayList<>();
        Section preamble = new Section(null, null);
        sections.add(preamble);
        Section current = preamble;
        if (doc.body() == null) {
            return sections;
        }
        for (Element el : doc.body().children()) {
            if (isHeading(el)) {
                current = new Section(normalizeHeadingText(el.text()), el);
                sections.add(current);
            } else {
                current.bodyElements.add(el);
            }
        }
        return sections;
    }

    private static boolean isHeading(Element el) {
        return el.tagName().matches("h[1-6]");
    }

    private static String normalizeHeadingText(String text) {
        return text == null ? "" : text.trim().replaceAll("\\s+", " ").toLowerCase();
    }

    /** A heading (or the preamble, when {@code headingElement == null}) and its direct body elements. */
    private static final class Section {
        final String headingText;
        final Element headingElement;
        final List<Element> bodyElements = new ArrayList<>();

        Section(String headingText, Element headingElement) {
            this.headingText = headingText;
            this.headingElement = headingElement;
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

        resolveStructuredMacros(doc);

        // Safety net: <ac:parameter> is always macro configuration, never real page
        // content. If any survive (e.g. malformed/unexpected nesting the loop above
        // did not anticipate), strip them so their raw text never leaks into Markdown.
        doc.getElementsByTag("ac:parameter").remove();

        return bodyHtml(doc);
    }

    // Unwrapping an unknown macro's <ac:rich-text-body> can reveal further nested
    // <ac:structured-macro> elements (e.g. third-party "live table" macros such as
    // Table Filter/Charts' table-excerpt-include, livesearch, sparkline, pivot-table,
    // etc. wrap their filter/sort configuration macro inside the body of an outer
    // macro). A single pass over doc.getElementsByTag(...) does not revisit elements
    // inserted during that same pass, so nested macros/parameters were previously left
    // untouched and leaked as raw concatenated text once handed to the Markdown
    // converter. Re-run the resolution loop until no structured macros remain (bounded
    // to avoid an infinite loop on pathological/self-referential input).
    private static void resolveStructuredMacros(Document doc) {
        int maxIterations = 25;
        for (int iteration = 0; iteration < maxIterations; iteration++) {
            Elements macros = doc.getElementsByTag("ac:structured-macro");
            if (macros.isEmpty()) {
                return;
            }
            for (Element macro : macros) {
                resolveStructuredMacro(doc, macro);
            }
        }
        // Give up gracefully: remove any macros still standing rather than let their
        // configuration parameters leak into the final Markdown.
        doc.getElementsByTag("ac:structured-macro").remove();
    }

    private static void resolveStructuredMacro(Document doc, Element macro) {
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
                // Macro configuration (ac:parameter) never belongs inside the rendered
                // body — strip it before unwrapping so it cannot leak as text, even
                // before the next iteration gets a chance to process nested macros.
                richBody.getElementsByTag("ac:parameter").remove();
                Element div = doc.createElement("div");
                div.html(richBody.html());
                macro.replaceWith(div);
            } else {
                // Remove unknown macros so their parameter text doesn't leak into Markdown.
                macro.remove();
            }
        }
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

    private static Document parseFragment(String html) {
        Document doc = Jsoup.parseBodyFragment(html);
        doc.outputSettings().prettyPrint(false);
        return doc;
    }

    private static String bodyHtml(Document doc) {
        return doc.body() != null ? doc.body().html() : doc.html();
    }

}
