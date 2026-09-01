// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.confluence;

import com.vladsch.flexmark.ext.autolink.AutolinkExtension;
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Document;
import com.vladsch.flexmark.util.data.MutableDataSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts Markdown to Confluence Storage Format (XHTML).
 *
 * <p>This is the inverse of {@link ConfluenceStorageMarkdown}: content that was
 * downloaded as Markdown (or authored locally as Markdown) can be turned back into
 * Confluence's {@code body.storage} representation and sent via the REST API.</p>
 *
 * <p>Supported Markdown constructs:</p>
 * <ul>
 *   <li>Headings, paragraphs, bold/italic/code, lists, horizontal rules</li>
 *   <li>Tables (GitHub-Flavored Markdown)</li>
 *   <li>Fenced code blocks → {@code <ac:structured-macro ac:name="code">}</li>
 *   <li>Task lists → {@code <ac:task-list>/<ac:task>}</li>
 *   <li>Images → {@code <ac:image><ri:attachment .../></ac:image>} (local filenames)
 *       or standard {@code <img>} (external URLs)</li>
 *   <li>Links → {@code <ac:link><ri:page .../></ac:link>} (page references),
 *       {@code <ac:link><ri:attachment .../></ac:link>} (attachments with known
 *       extensions), or standard {@code <a>} (external URLs / anchors)</li>
 * </ul>
 */
public final class MarkdownToConfluenceStorage {

    private static final Logger logger = LogManager.getLogger(MarkdownToConfluenceStorage.class);

    private static final Parser PARSER;
    private static final HtmlRenderer RENDERER;

    private static final Set<String> ATTACHMENT_EXTENSIONS = Set.of(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "png", "jpg", "jpeg", "gif", "svg", "webp",
            "zip", "tar", "gz", "tgz", "bz2", "7z",
            "txt", "csv", "json", "xml", "yaml", "yml", "html", "md"
    );

    static {
        MutableDataSet options = new MutableDataSet();
        options.set(Parser.EXTENSIONS, Arrays.asList(
                TablesExtension.create(),
                TaskListExtension.create(),
                AutolinkExtension.create()
        ));
        // Standard GFM behaviour: soft line breaks become a single space.
        options.set(HtmlRenderer.SOFT_BREAK, " ");
        PARSER = Parser.builder(options).build();
        RENDERER = HtmlRenderer.builder(options).build();
    }

    private MarkdownToConfluenceStorage() {
    }

    /**
     * Converts a Markdown string into Confluence Storage Format XHTML.
     *
     * @param markdown the Markdown source
     * @return Confluence Storage Format XHTML, or an escaped paragraph fallback
     *         if parsing fails
     */
    public static String toStorage(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }
        try {
            String normalized = normalizeLegacyLinks(markdown);
            Document document = PARSER.parse(normalized);
            String html = RENDERER.render(document);
            return convertHtmlToConfluenceStorage(html);
        } catch (Exception e) {
            logger.warn("Markdown->Confluence storage conversion failed, returning escaped paragraph: {}",
                    e.getMessage(), e);
            return "<p>" + escapeHtml(markdown).replace("\n", "<br/>") + "</p>";
        }
    }

    /**
     * The legacy {@link ConfluenceStorageMarkdown} exporter emits Markdown links with
     * spaces inside the URL, e.g. {@code [text](Target Page)}. Standard Markdown parsers
     * do not recognise that as a link. This method wraps such URLs in angle brackets so
     * the round-trip works while still accepting properly authored Markdown.
     */
    private static String normalizeLegacyLinks(String markdown) {
        String result = normalizeLegacyImages(markdown);
        return normalizeLegacyLinkPattern(result, LINK_PATTERN);
    }

    private static String normalizeLegacyImages(String markdown) {
        return normalizeLegacyLinkPattern(markdown, IMAGE_PATTERN);
    }

    private static final Pattern LINK_PATTERN = Pattern.compile(
            "\\[([^\\]]*)\\]\\(([^)]+)\\)", Pattern.DOTALL);
    private static final Pattern IMAGE_PATTERN = Pattern.compile(
            "!\\[([^\\]]*)\\]\\(([^)]+)\\)", Pattern.DOTALL);

    private static String normalizeLegacyLinkPattern(String markdown, Pattern pattern) {
        Matcher matcher = pattern.matcher(markdown);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String text = matcher.group(1);
            String url = matcher.group(2).trim();
            if (url.contains(" ") && !url.startsWith("<") && !url.startsWith("\"") && !url.startsWith("'")) {
                String encodedUrl = url.replace(" ", "%20");
                matcher.appendReplacement(sb, Matcher.quoteReplacement(
                        pattern == IMAGE_PATTERN
                                ? "![" + text + "](" + encodedUrl + ")"
                                : "[" + text + "](" + encodedUrl + ")"));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String convertHtmlToConfluenceStorage(String html) {
        // Parse as XML so that raw CDATA sections we inject for code macros are
        // preserved instead of being converted to HTML comments.
        org.jsoup.nodes.Document doc = Jsoup.parse(html, "", org.jsoup.parser.Parser.xmlParser());
        doc.outputSettings()
                .prettyPrint(false)
                .syntax(org.jsoup.nodes.Document.OutputSettings.Syntax.xml);

        convertCodeBlocks(doc);
        convertTaskLists(doc);
        convertImages(doc);
        convertLinks(doc);

        String result = fragmentHtml(doc);
        // XML syntax can leave empty element tags like <br /> with a space before />;
        // Confluence is fine with that, but normalize to a consistent form.
        return result.replaceAll("(?i)<br\\s*/?>", "<br/>");
    }

    private static String fragmentHtml(org.jsoup.nodes.Document doc) {
        if (doc.body() != null && !doc.body().html().isBlank()) {
            return doc.body().html();
        }
        // XML parser places fragment children at the document root and adds an empty
        // synthetic <html><body> wrapper; serialize the real fragment nodes instead.
        StringBuilder sb = new StringBuilder();
        for (Node node : doc.childNodes()) {
            if (node instanceof Element && "html".equals(((Element) node).tagName())) {
                continue;
            }
            sb.append(node.outerHtml());
        }
        return sb.toString();
    }

    /**
     * Converts fenced code blocks ({@code <pre><code class="language-xxx">...}) into
     * Confluence's {@code code} macro.
     */
    private static void convertCodeBlocks(org.jsoup.nodes.Document doc) {
        for (Element pre : doc.select("pre")) {
            Element code = pre.selectFirst("code");
            String language = "";
            if (code != null) {
                String classAttr = code.attr("class").trim();
                if (classAttr.startsWith("language-")) {
                    language = classAttr.substring("language-".length()).trim();
                } else if (!classAttr.isBlank()) {
                    language = classAttr;
                }
            }
            // Use wholeText() instead of text(): text() normalizes/collapses whitespace
            // (including newlines), which corrupts multi-line code/diagram content
            // (e.g. Mermaid diagrams become invalid because statement-separating
            // newlines are collapsed into single spaces). wholeText() preserves the
            // original line breaks exactly as authored.
            String codeText = (code != null ? code.wholeText() : pre.wholeText()).strip();

            StringBuilder macro = new StringBuilder();
            macro.append("<ac:structured-macro ac:name=\"code\" ac:schema-version=\"1\">");
            if (!language.isBlank()) {
                macro.append("<ac:parameter ac:name=\"language\">")
                        .append(escapeXml(language))
                        .append("</ac:parameter>");
            }
            macro.append("<ac:plain-text-body><![CDATA[")
                    .append(codeText)
                    .append("]]></ac:plain-text-body>");
            macro.append("</ac:structured-macro>");

            pre.after(macro.toString());
            pre.remove();
        }
    }

    /**
     * Converts GFM task lists ({@code <ul>} with checkbox inputs) into Confluence
     * {@code ac:task-list} elements.
     */
    private static void convertTaskLists(org.jsoup.nodes.Document doc) {
        for (Element ul : doc.select("ul")) {
            boolean isTaskList = false;
            StringBuilder taskListXml = new StringBuilder("<ac:task-list>");
            for (Element li : ul.select("> li")) {
                Element input = li.selectFirst("input[type=checkbox]");
                if (input == null) {
                    continue;
                }
                isTaskList = true;
                String status = input.hasAttr("checked") ? "complete" : "incomplete";
                input.remove();
                String bodyText = li.text().trim();
                taskListXml.append("<ac:task>")
                        .append("<ac:task-status>").append(status).append("</ac:task-status>")
                        .append("<ac:task-body>").append(escapeXml(bodyText)).append("</ac:task-body>")
                        .append("</ac:task>");
            }
            if (isTaskList) {
                taskListXml.append("</ac:task-list>");
                ul.after(taskListXml.toString());
                ul.remove();
            }
        }
    }

    /**
     * Converts local image references to {@code ac:image/ri:attachment} and leaves
     * external URLs as standard {@code <img>} tags. Relative paths such as
     * {@code ./images/diagram.png} are normalized to the filename only, because
     * Confluence page attachments are flat.
     */
    private static void convertImages(org.jsoup.nodes.Document doc) {
        for (Element img : doc.select("img")) {
            String rawSrc = img.attr("src").trim();
            String alt = img.attr("alt").trim();
            if (rawSrc.isBlank()) {
                img.remove();
                continue;
            }
            if (isExternalUrl(rawSrc)) {
                // External image: keep standard <img> tag.
                continue;
            }
            String filename = attachmentFileName(rawSrc);
            if (filename.isBlank()) {
                img.remove();
                continue;
            }
            String effectiveAlt = alt.isBlank() ? filename : alt;
            String attachmentXml = "<ac:image" + (effectiveAlt.isBlank() ? "" : " ac:alt=\"" + escapeXml(effectiveAlt) + "\"") + ">" +
                    "<ri:attachment ri:filename=\"" + escapeXml(filename) + "\"></ri:attachment>" +
                    "</ac:image>";
            img.after(attachmentXml);
            img.remove();
        }
    }

    /**
     * Converts page/attachment references to {@code ac:link} elements and leaves
     * external URLs and anchors as standard {@code <a>} tags. Relative paths to
     * attachments are normalized to the filename only.
     */
    private static void convertLinks(org.jsoup.nodes.Document doc) {
        for (Element a : doc.select("a")) {
            String rawHref = a.attr("href").trim();
            if (rawHref.isBlank()) {
                continue;
            }
            if (isExternalUrl(rawHref) || rawHref.startsWith("#")) {
                continue;
            }

            String href = decodeUrl(rawHref);
            String linkBody = a.html();
            if (linkBody == null || linkBody.isBlank()) {
                linkBody = escapeXml(href);
            }

            String linkXml;
            String attachmentName = attachmentFileName(href);
            if (looksLikeAttachment(attachmentName)) {
                linkXml = "<ac:link><ri:attachment ri:filename=\"" + escapeXml(attachmentName) + "\"></ri:attachment>" +
                        "<ac:link-body>" + linkBody + "</ac:link-body></ac:link>";
            } else {
                String spaceKey = null;
                String title = href;
                int colonIndex = href.indexOf(':');
                // Only treat "SPACE:Title" as an explicit cross-space reference when
                // the colon is immediately followed by the title with no space, e.g.
                // "PROJ:Home". A same-space page whose OWN title merely starts with a
                // single word + colon (e.g. "Recommendations: Some Feature") always has
                // a space after the colon (normal punctuation), so requiring no space
                // there disambiguates the two cases without breaking the legacy
                // "SPACE:Title" syntax (see testPageLinkWithSpaceKey). Without this
                // check, such a page title got misparsed as a link into a
                // non-existent "Recommendations" space, producing a broken Confluence
                // link that rendered as a "create this page" prompt instead.
                if (colonIndex > 0 && colonIndex + 1 < href.length() && !Character.isWhitespace(href.charAt(colonIndex + 1))) {
                    String candidateSpace = href.substring(0, colonIndex);
                    if (isValidSpaceKey(candidateSpace)) {
                        spaceKey = candidateSpace;
                        title = href.substring(colonIndex + 1);
                    }
                }
                StringBuilder sb = new StringBuilder("<ac:link>");
                sb.append("<ri:page");
                if (spaceKey != null) {
                    sb.append(" ri:space-key=\"").append(escapeXml(spaceKey)).append("\"");
                }
                sb.append(" ri:content-title=\"").append(escapeXml(title)).append("\"></ri:page>");
                sb.append("<ac:link-body>").append(linkBody).append("</ac:link-body>");
                sb.append("</ac:link>");
                linkXml = sb.toString();
            }
            a.after(linkXml);
            a.remove();
        }
    }

    private static String decodeUrl(String url) {
        try {
            return URLDecoder.decode(url, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return url;
        }
    }

    private static boolean isExternalUrl(String href) {
        return isExternalUrlStatic(href);
    }

    /**
     * Public helper for callers that need to filter external URLs before passing
     * Markdown through the converter.
     */
    public static boolean isExternalUrlStatic(String href) {
        if (href == null || href.isBlank()) {
            return false;
        }
        String lower = href.toLowerCase(Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://")
                || lower.startsWith("mailto:") || lower.startsWith("ftp://");
    }

    private static boolean isValidSpaceKey(String spaceKey) {
        return spaceKey.matches("[A-Za-z][A-Za-z0-9]*");
    }

    /**
     * Extracts the filename portion from a possibly relative attachment path.
     * URLs pointing outside the page (external URLs, anchors) should be filtered
     * before calling this method.
     */
    public static String attachmentFileName(String href) {
        if (href == null || href.isBlank()) {
            return "";
        }
        String decoded = decodeUrl(href);
        // Normalize backslashes to forward slashes and strip leading ./ or ../ segments.
        String normalized = decoded.replace('\\', '/').replaceAll("^(\\./)+", "");
        Path path = Paths.get(normalized);
        Path fileName = path.getFileName();
        return fileName != null ? fileName.toString() : normalized;
    }

    private static boolean looksLikeAttachment(String href) {
        if (href == null || href.isBlank()) {
            return false;
        }
        int lastDot = href.lastIndexOf('.');
        if (lastDot <= 0 || lastDot == href.length() - 1) {
            return false;
        }
        String ext = href.substring(lastDot + 1).toLowerCase(Locale.ROOT);
        return ATTACHMENT_EXTENSIONS.contains(ext);
    }

    /**
     * Extracts attachment filenames referenced by local Markdown links/images.
     * External URLs, anchors, and page references are ignored. The returned
     * filenames are stripped of any relative path segments and are suitable for
     * matching against files to upload to Confluence.
     *
     * @param markdown the Markdown source
     * @return ordered set of unique attachment filenames
     */
    public static Set<String> extractAttachmentReferences(String markdown) {
        Set<String> result = new LinkedHashSet<>();
        if (markdown == null || markdown.isBlank()) {
            return result;
        }
        collectAttachmentReferences(markdown, LINK_PATTERN, result);
        collectAttachmentReferences(markdown, IMAGE_PATTERN, result);
        return result;
    }

    private static void collectAttachmentReferences(String markdown, Pattern pattern, Set<String> result) {
        Matcher matcher = pattern.matcher(markdown);
        while (matcher.find()) {
            String url = matcher.group(2).trim();
            if (url.isBlank() || isExternalUrl(url) || url.startsWith("#")) {
                continue;
            }
            String filename = attachmentFileName(url);
            if (filename.isBlank()) {
                continue;
            }
            if (looksLikeAttachment(filename)) {
                result.add(filename);
            }
        }
    }

    /**
     * Extracts the original (decoded) attachment reference paths from local Markdown
     * links/images. Unlike {@link #extractAttachmentReferences(String)}, this keeps
     * the relative path segments, so callers can resolve the referenced file in the
     * local filesystem. External URLs, anchors, and page references are ignored.
     *
     * @param markdown the Markdown source
     * @return ordered set of unique decoded attachment reference paths
     */
    public static Set<String> extractAttachmentReferencePaths(String markdown) {
        Set<String> result = new LinkedHashSet<>();
        if (markdown == null || markdown.isBlank()) {
            return result;
        }
        collectAttachmentReferencePaths(markdown, LINK_PATTERN, result);
        collectAttachmentReferencePaths(markdown, IMAGE_PATTERN, result);
        return result;
    }

    private static void collectAttachmentReferencePaths(String markdown, Pattern pattern, Set<String> result) {
        Matcher matcher = pattern.matcher(markdown);
        while (matcher.find()) {
            String url = matcher.group(2).trim();
            if (url.isBlank() || isExternalUrl(url) || url.startsWith("#")) {
                continue;
            }
            String decoded = decodeUrl(url);
            String filename = attachmentFileName(decoded);
            if (filename.isBlank()) {
                continue;
            }
            if (looksLikeAttachment(filename)) {
                result.add(decoded);
            }
        }
    }

    private static String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    public static String escapeXml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
