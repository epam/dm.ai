// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.utils;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Final approach to match the test's spacing/line-break expectations as best as possible.
 *
 * Key changes for formatting:
 * - We do a final pass of regex replacements to unify newlines before links or after certain punctuation
 *   so that "Design_\nhttps://..." or "Design_\n[https://..." becomes "Design_\n[https://..." (no blank line).
 */
public class MarkdownToJiraConverter {

    private static final Pattern HTML_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("```(\\w*)\\n([\\s\\S]*?)```");
    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+)$", Pattern.MULTILINE);

    public static String convertToJiraMarkdown(String input) {
        if (input == null || input.trim().isEmpty()) {
            return "";
        }

        // If it's purely HTML entities
        if (containsOnlyHtmlEntities(input)) {
            return decodeEntities(input);
        }

        boolean hasMarkdown = input.contains("#") || input.contains("```") || input.contains("* ");
        boolean hasHtml = containsHtml(input);

        if (hasMarkdown && hasHtml) {
            return convertMixedContent(input);
        } else if (hasHtml) {
            return convertHtmlToJiraMarkdown(input);
        } else {
            return convertMarkdownToJiraMarkdown(input);
        }
    }

    private static boolean containsOnlyHtmlEntities(String input) {
        // Regex to match HTML entities (e.g., &lt;, &gt;, &amp;)
        Pattern htmlEntityPattern = Pattern.compile("&[a-zA-Z]+;");

        // Replace all HTML entities and check if the remaining content is empty
        String withoutEntities = input.replaceAll(htmlEntityPattern.pattern(), "").trim();

        // If the input contains `&` and the remaining content is empty, it's purely HTML entities
        return input.contains("&") && withoutEntities.isEmpty();
    }

    private static String convertMixedContent(String input) {
        HTMLCodeBlockPreserver preserver = new HTMLCodeBlockPreserver();
        String preserved = preserver.preserveCodeBlocks(input);
        String[] chunks = preserved.split("\n\n");
        List<String> parts = new ArrayList<>();
        for (String chunk : chunks) {
            String trimmed = chunk.trim();
            if (trimmed.isEmpty()) continue;
            if (containsHtml(trimmed)) {
                parts.add(convertHtmlToJiraMarkdown(trimmed));
            } else {
                parts.add(convertMarkdownToJiraMarkdown(trimmed));
            }
        }
        String results = String.join("\n\n", parts);
        results = preserver.restoreCodeBlocks(results);
        return results.trim();
    }

    /**
     * Convert HTML => JIRA by enumerating top-level child nodes.
     * - If block-level => flush inline, then handle block
     * - If inline => accumulate in inline buffer
     */
    private static String convertHtmlToJiraMarkdown(String html) {
        HTMLCodeBlockPreserver preserver = new HTMLCodeBlockPreserver();
        String preserved = preserver.preserveCodeBlocks(html);

        Document doc = Jsoup.parse(preserved);
        List<Node> nodes = doc.body().childNodes();

        List<String> blocks = new ArrayList<>();
        StringBuilder inlineBuffer = new StringBuilder();

        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            if (node instanceof Element) {
                Element el = (Element) node;
                String tag = el.tagName().toLowerCase();

                if (isBlockLevel(tag)) {
                    // flush any inline
                    flushInlineBuffer(inlineBuffer, blocks);

                    // special <strong> + <ul>
                    if ("strong".equals(tag) && (i + 1 < nodes.size())) {
                        Node nxt = nodes.get(i + 1);
                        if (nxt instanceof Element nxtEl) {
                            if ("ul".equalsIgnoreCase(nxtEl.tagName())) {
                                String heading = "# *" + el.text().trim() + "*";
                                blocks.add(heading + "\n" + processUnorderedList(nxtEl));
                                i++;
                                continue;
                            }
                        }
                    }

                    // consecutive <b>
                    if ("b".equals(tag)) {
                        StringBuilder combined = new StringBuilder(trimLeadingSpaces(el.outerHtml()));
                        while (i + 1 < nodes.size()) {
                            Node nxt = nodes.get(i + 1);
                            if (nxt instanceof Element nxtEl) {
                                if ("b".equalsIgnoreCase(nxtEl.tagName())) {
                                    combined.append(" ").append(trimLeadingSpaces(nxtEl.outerHtml()));
                                    i++;
                                } else break;
                            } else break;
                        }
                        Element fakeP = Jsoup.parseBodyFragment(combined.toString()).body();
                        blocks.add(processParagraph(fakeP));
                        continue;
                    }

                    // consecutive <i>
                    if ("i".equals(tag)) {
                        StringBuilder combined = new StringBuilder(trimLeadingSpaces(el.outerHtml()));
                        while (i + 1 < nodes.size()) {
                            Node nxt = nodes.get(i + 1);
                            if (nxt instanceof Element nxtEl) {
                                if ("i".equalsIgnoreCase(nxtEl.tagName())) {
                                    combined.append(" ").append(trimLeadingSpaces(nxtEl.outerHtml()));
                                    i++;
                                } else break;
                            } else break;
                        }
                        Element fakeP = Jsoup.parseBodyFragment(combined.toString()).body();
                        blocks.add(processParagraph(fakeP));
                        continue;
                    }

                    // top-level <a>
                    if ("a".equals(tag)) {
                        blocks.add("[" + el.text() + "|" + el.attr("href") + "]");
                        continue;
                    }

                    // normal block
                    blocks.add(handleBlockElement(el));
                }
                else {
                    // inline
                    inlineBuffer.append(el.outerHtml());
                }
            } else {
                // text node => inline
                inlineBuffer.append(node.outerHtml());
            }
        }

        flushInlineBuffer(inlineBuffer, blocks);

        String joined = String.join("\n\n", removeEmpty(blocks)).trim();
        // final pass to fix awkward newlines before link
        joined = fixNewlineBeforeLink(joined);

        return preserver.restoreCodeBlocks(joined);
    }

    private static boolean isBlockLevel(String tag) {
        switch (tag) {
            case "ac:structured-macro":
            case "p":
            case "pre":
            case "ul":
            case "ol":
            case "table":
            case "h1":
            case "h2":
            case "h3":
            case "h4":
            case "h5":
            case "h6":
            case "code":
            case "strong":
            case "em":
            case "b":
            case "i":
            case "a":
                return true;
            default:
                return false;
        }
    }

    private static void flushInlineBuffer(StringBuilder inlineBuffer, List<String> blocks) {
        String raw = inlineBuffer.toString().trim();
        if (!raw.isEmpty()) {
            Document frag = Jsoup.parseBodyFragment(raw);
            blocks.add(processParagraph(frag.body()));
        }
        inlineBuffer.setLength(0);
    }

    private static String handleBlockElement(Element el) {
        String tag = el.tagName().toLowerCase();
        switch (tag) {
            case "h1":
            case "h2":
            case "h3":
            case "h4":
            case "h5":
            case "h6": {
                int level = Integer.parseInt(tag.substring(1));
                return "h" + level + ". " + el.text();
            }
            case "p":
                return processParagraph(el);
            case "pre":
                return processPre(el);
            case "ul":
                return processUnorderedList(el);
            case "ol":
                return processOrderedList(el);
            case "table":
                return processTable(el);
            case "code":
            case "ac:structured-macro":
                return processCodeElement(el);
            default:
                // fallback => treat as paragraph
                return processParagraph(el);
        }
    }

    private static String trimLeadingSpaces(String html) {
        Document tmp = Jsoup.parseBodyFragment(html);
        for (Element e : tmp.body().getAllElements()) {
            if (e.ownText() != null && !e.ownText().isEmpty()) {
                String cleaned = e.ownText().replaceAll("^\\s+", "");
                e.text(cleaned);
            }
        }
        return tmp.body().html();
    }

    /**
     * Attempt to unify newlines before a link bracket or after punctuation
     * so that the link doesn't appear on a lonely line.
     */
    private static String fixNewlineBeforeLink(String text) {
        // e.g. "Design_\n[https => unify
        text = text.replaceAll("(\\S)\\n\\[(https?://)", "$1\n[$2");
        text = text.replaceAll("\\n\\[\\n(https?://)", "\n[https://");
        text = text.replaceAll("\\n\\[https?://", "\n[https://");
        return text;
    }

    private static String convertMarkdownToJiraMarkdown(String markdown) {
        // Split the input into lines
        String[] lines = markdown.split("\n");
        List<String> blocks = new ArrayList<>();

        boolean inCodeBlock = false; // Flag to track if we are inside a code block
        StringBuilder codeBuf = new StringBuilder(); // Buffer to accumulate code block content
        String codeLang = ""; // Language of the code block
        StringBuilder paragraph = new StringBuilder(); // Buffer to accumulate paragraph content

        for (String line : lines) {
            // Use trimmed line only for code block recognition
            String trimmedLine = line.trim();

            // Check if the line starts or ends a code block
            if (trimmedLine.startsWith("```")) {
                if (!inCodeBlock) {
                    // If entering a code block, flush any accumulated paragraph content
                    if (!paragraph.isEmpty()) {
                        blocks.add(processTextParagraph(paragraph.toString()));
                        paragraph.setLength(0);
                    }
                    inCodeBlock = true; // Mark that we are inside a code block
                    codeLang = trimmedLine.substring(3).trim(); // Extract the language (if specified)
                } else {
                    // If exiting a code block, process the accumulated code content
                    String code = codeBuf.toString()
                            .replaceAll("^[\\r\\n]+", "") // Remove leading newlines
                            .replaceAll("[\\r\\n]+$", "")
                            ; // Remove trailing newlines
                    blocks.add("{code:" + (codeLang.isEmpty() ? "java" : codeLang) + "}" + code + "{code}");
                    inCodeBlock = false; // Mark that we are outside the code block
                    codeBuf.setLength(0); // Reset the code buffer
                    codeLang = ""; // Reset the language
                }
                continue; // Skip further processing for this line
            }

            if (inCodeBlock) {
                // If inside a code block, preserve the original line content
                codeBuf.append(line).append("\n");
            } else {
                // Process images in the line
                line = processImages(line);

                if (trimmedLine.isEmpty()) {
                    // If the line is empty, flush the paragraph buffer
                    if (!paragraph.isEmpty()) {
                        blocks.add(processTextParagraph(paragraph.toString()));
                        paragraph.setLength(0);
                    }
                } else {
                    // Accumulate non-empty lines into the paragraph buffer
                    if (!paragraph.isEmpty()) {
                        paragraph.append("\n");
                    }
                    paragraph.append(line); // Use the processed line content for paragraphs
                }
            }
        }

        // Flush any remaining paragraph content
        if (!paragraph.isEmpty()) {
            blocks.add(processTextParagraph(paragraph.toString()));
        }

        // Join all blocks with double newlines and return the result
        return String.join("\n\n", blocks).trim();
    }

    /**
     * Converts Markdown image syntax to Jira image syntax
     * ![alt](url) -> !url!
     * ![alt|attributes] -> !attributes!
     */
    /**
     * Converts Markdown image syntax to Jira image syntax
     * ![alt](url) -> !url!
     * ![alt|attributes] -> !attributes!
     */
    private static final Pattern IMAGE_PATTERN = Pattern.compile("!\\[(.*?)\\|([^\\]]+)\\]");

    /**
     * Converts Markdown image syntax to Jira image syntax
     * ![image-name|attributes] -> !image-name|attributes!
     */
    private static String processImages(String text) {
        if (text == null || !text.contains("![")) {
            return text;
        }

        Matcher imageMatcher = IMAGE_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();

        while (imageMatcher.find()) {
            // Format: ![image-name|attributes]
            String imageName = imageMatcher.group(1);
            String attributes = imageMatcher.group(2);
            String replacement = "!" + imageName + "|" + attributes + "!";
            imageMatcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }

        imageMatcher.appendTail(sb);
        return sb.toString();
    }

    private static String processTextParagraph(String text) {
        // First process images
        text = processImages(text);

        String[] lines = text.split("\n");
        List<String> output = new ArrayList<>();

        for (String line : lines) {
            String trimmed = line.trim();
            trimmed = trimmed.replaceAll("^1\\. \\*\\*(.*?)\\*\\*", "*$1*");

            Matcher headingMatch = HEADING_PATTERN.matcher(trimmed);
            if (headingMatch.matches()) {
                String hashes = headingMatch.group(1);
                String headingText = headingMatch.group(2);
                int level = hashes.length();
                output.add("h" + level + ". " + headingText);
                continue;
            }

            // inline code, bold, links
            trimmed = trimmed.replaceAll("`\\s*([^`]+)\\s*`", "{{$1}}");
            trimmed = trimmed.replaceAll("\\*\\*([^*]+)\\*\\*", "*$1*");
            trimmed = trimmed.replaceAll("\\[([^\\]]+)\\]\\(([^)]+)\\)", "[$1|$2]");

            output.add(trimmed);
        }

        return String.join("\n", output);
    }

    private static boolean containsHtml(String s) {
        String noCodeBlocks = CODE_BLOCK_PATTERN.matcher(s).replaceAll("");
        String noCode = noCodeBlocks.replaceAll("`[^`]*`", "");
        return HTML_PATTERN.matcher(noCode).find();
    }

    private static String decodeEntities(String s) {
        return s.replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&apos;", "'");
    }

    public static String unescapeHtml(String s) {
        return s
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("-&gt;", "->")
                .replaceAll("\\R", "\n")
                ;
    }

    private static List<String> removeEmpty(List<String> blocks) {
        List<String> cleaned = new ArrayList<>();
        for (String b : blocks) {
            String t = b.trim();
            if (!t.isEmpty()) {
                cleaned.add(t);
            }
        }
        return cleaned;
    }

    private static String processParagraph(Element p) {
        if (p.html().contains(HTMLCodeBlockPreserver.CODE_BLOCK_PLACEHOLDER)) {
            return p.html().replaceAll("(?i)</?code[^>]*>", "");
        }
        String placeholderPattern = Pattern.quote(HTMLCodeBlockPreserver.CODE_BLOCK_PLACEHOLDER) + "\\d+";

        // convert known tags => JIRA
        String text = p.html()
                .replaceAll("(?i)<a\\s+href=\"([^\"]+)\">(.*?)</a>", "[$2|$1]")
                .replaceAll("(?i)<strong>(.*?)</strong>", "*$1*")
                .replaceAll("(?i)<em>(.*?)</em>", "_$1_")
                .replaceAll("(?i)<b>(.*?)</b>", "*$1*")
                .replaceAll("(?i)<i>(.*?)</i>",  "_$1_")
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)<code>(?!" + placeholderPattern + ")(.*?)</code>", "{{$1}}")
                .replaceAll("(?i)<[^>]+>", "");

        text = unescapeHtml(text).trim();
        text = fixNewlineBeforeLink(text);
        return text;
    }

    private static String processCodeElement(Element codeEl) {
        String html = codeEl.html();
        String outer = codeEl.outerHtml();

        if (outer.contains(HTMLCodeBlockPreserver.CODE_BLOCK_PLACEHOLDER)) {
            return outer.replaceAll("(?i)</?code[^>]*>", "");
        }

        String codeText = Parser.unescapeEntities(html, false)
                .replaceAll("^[\\r\\n]+", "")
                .replaceAll("[\\r\\n]+$", "");
        String lang = "java";
        if (codeEl.hasAttr("class") && !codeEl.attr("class").trim().isEmpty()) {
            lang = codeEl.attr("class").trim();
        }
        if (codeText.contains("\n")) {
            return "\n{code:" + lang + "}\n" + codeText + "\n{code}\n";
        } else {
            return "{{" + codeText + "}}";
        }
    }

    private static String processPre(Element pre) {
        Element codeEl = pre.selectFirst("code");
        if (codeEl != null) {
            if (codeEl.html().contains(HTMLCodeBlockPreserver.CODE_BLOCK_PLACEHOLDER)) {
                return codeEl.html().replaceAll("(?i)</?code[^>]*>", "");
            }
            String codeHtml = codeEl.html();
            String codeText = Parser.unescapeEntities(codeHtml, false)
                    .replaceAll("^[\\r\\n]+", "")
                    .replaceAll("[\\r\\n]+$", "");
            String lang = "java";
            if (codeEl.hasAttr("class") && !codeEl.attr("class").trim().isEmpty()) {
                lang = codeEl.attr("class").trim();
            }
            return "{code:" + lang + "}\n" + codeText + "\n{code}";
        }
        return unescapeHtml(pre.text());
    }

    private static String processUnorderedList(Element ul) {
        StringBuilder sb = new StringBuilder();
        Elements liList = ul.select("> li");
        for (Element li : liList) {
            Element liClone = li.clone();
            liClone.select("ul,ol").remove();

            String raw = liClone.html().replaceAll("(?i)</?code[^>]*>", "");
            String placeholderPattern = Pattern.quote(HTMLCodeBlockPreserver.CODE_BLOCK_PLACEHOLDER) + "\\d+";
            String liText = raw
                    .replaceAll("(?i)<a\\s+href=\"([^\"]+)\">(.*?)</a>", "[$2|$1]")
                    .replaceAll("(?i)<strong>(.*?)</strong>", "*$1*")
                    .replaceAll("(?i)<em>(.*?)</em>", "_$1_")
                    .replaceAll("(?i)<b>(.*?)</b>", "*$1*")
                    .replaceAll("(?i)<i>(.*?)</i>", "_$1_")
                    .replaceAll("(?i)<br\\s*/?>", "\n")
                    .replaceAll("(?i)<code>(?!" + placeholderPattern + ")(.*?)</code>", "{{$1}}")
                    .replaceAll("(?i)<[^>]+>", "");

            liText = unescapeHtml(liText).trim();
            if (!liText.isEmpty()) {
                sb.append("* ").append(liText).append("\n");
            }

            for (Element child : li.children()) {
                if ("ul".equalsIgnoreCase(child.tagName())) {
                    sb.append(processUnorderedList(child)).append("\n");
                } else if ("ol".equalsIgnoreCase(child.tagName())) {
                    sb.append(processOrderedList(child)).append("\n");
                }
            }
        }
        return sb.toString().trim();
    }

    private static String processOrderedList(Element ol) {
        StringBuilder sb = new StringBuilder();
        Elements liList = ol.select("> li");

        for (Element li : liList) {
            Element strongEl = li.selectFirst("> strong");
            Element nestedUl = li.selectFirst("> ul");
            if (strongEl != null && nestedUl != null) {
                String headingText = strongEl.text().trim();
                sb.append("# *").append(headingText).append("*\n");
                sb.append(processUnorderedList(nestedUl)).append("\n");
                continue;
            }

            if (li.html().contains(HTMLCodeBlockPreserver.CODE_BLOCK_PLACEHOLDER)) {
                sb.append(li.html().replaceAll("(?i)</?code[^>]*>", "")).append("\n");
                continue;
            }

            Element liClone = li.clone();
            liClone.select("ul,ol").remove();

            String placeholderPattern = Pattern.quote(HTMLCodeBlockPreserver.CODE_BLOCK_PLACEHOLDER) + "\\d+";
            String liText = liClone.html()
                    .replaceAll("(?i)<a\\s+href=\"([^\"]+)\">(.*?)</a>", "[$2|$1]")
                    .replaceAll("(?i)<strong>(.*?)</strong>", "*$1*")
                    .replaceAll("(?i)<em>(.*?)</em>", "_$1_")
                    .replaceAll("(?i)<b>(.*?)</b>", "*$1*")
                    .replaceAll("(?i)<i>(.*?)</i>", "_$1_")
                    .replaceAll("(?i)<br\\s*/?>", "\n")
                    .replaceAll("(?i)<code>(?!" + placeholderPattern + ")(.*?)</code>", "{{$1}}")
                    .replaceAll("(?i)<[^>]+>", "");

            liText = unescapeHtml(liText).trim();
            if (!liText.isEmpty()) {
                sb.append("# ").append(liText).append("\n");
            }

            for (Element child : li.children()) {
                if ("ul".equalsIgnoreCase(child.tagName())) {
                    sb.append(processUnorderedList(child)).append("\n");
                } else if ("ol".equalsIgnoreCase(child.tagName())) {
                    sb.append(processOrderedList(child)).append("\n");
                }
            }
        }
        return sb.toString().trim();
    }

    private static String processTable(Element table) {
        StringBuilder sb = new StringBuilder();
        Elements rows = table.select("tr");
        boolean headerDone = false;
        for (Element row : rows) {
            Elements cells = row.select("th, td");
            if (cells.isEmpty()) continue;
            if (!headerDone && !row.select("th").isEmpty()) {
                sb.append("||");
                for (Element th : cells) {
                    String trimmed = th.text().trim();
                    sb.append(unescapeHtml(trimmed.isEmpty() ? " " : trimmed)).append("||");
                    // Handle colspan for header
                    int colspan = parseColspan(th);
                    for (int i = 1; i < colspan; i++) {
                        sb.append(" ||");
                    }
                }
                sb.append("\n");
                headerDone = true;
            } else {
                sb.append("|");
                for (Element cell : cells) {
                    if (cell.html().contains(HTMLCodeBlockPreserver.CODE_BLOCK_PLACEHOLDER)) {
                        String stripped = cell.html().replaceAll("(?i)</?code[^>]*>", "");
                        sb.append(stripped).append("|");
                    } else {
                        String placeholderPattern = Pattern.quote(HTMLCodeBlockPreserver.CODE_BLOCK_PLACEHOLDER) + "\\d+";
                        String cellText = cell.html()
                                .replaceAll("(?i)<a\\s+href=\"([^\"]+)\">(.*?)</a>", "[$2|$1]")
                                .replaceAll("(?i)<strong>(.*?)</strong>", "*$1*")
                                .replaceAll("(?i)<em>(.*?)</em>", "_$1_")
                                .replaceAll("(?i)<b>(.*?)</b>", "*$1*")
                                .replaceAll("(?i)<br\\s*/?>", "\n\\\\\\\\")
                                .replaceAll("(?i)<i>(.*?)</i>", "_$1_")
                                .replaceAll("(?i)<code>(?!" + placeholderPattern + ")(.*?)</code>", "{{$1}}")
                                .replaceAll("(?i)<[^>]+>", "")
                                ;
                        String trimmed = cellText.trim().replaceAll("\\|", "/");
                        sb.append(unescapeHtml(trimmed.isEmpty() ? " " : trimmed)).append("|");
                    }

                    // Handle colspan for regular cells
                    int colspan = parseColspan(cell);
                    for (int i = 1; i < colspan; i++) {
                        sb.append(" |");
                    }
                }
                sb.append("\n");
            }
        }
        return sb.toString().trim();
    }

    private static int parseColspan(Element cell) {
        String colspan = cell.attr("colspan");
        if (colspan != null && !colspan.isEmpty()) {
            try {
                return Integer.parseInt(colspan);
            } catch (NumberFormatException e) {
                return 1;
            }
        }
        return 1;
    }

    private static String processGenericBlock(Element el) {
        if (el.html().contains(HTMLCodeBlockPreserver.CODE_BLOCK_PLACEHOLDER)) {
            return el.html().replaceAll("(?i)</?code[^>]*>", "");
        }

        // strong + ul
        Elements kids = el.children();
        if (kids.size() == 2
                && "strong".equalsIgnoreCase(kids.get(0).tagName())
                && "ul".equalsIgnoreCase(kids.get(1).tagName())) {
            String headingText = kids.get(0).text().trim();
            String bullet = processUnorderedList(kids.get(1));
            return "# *" + headingText + "*\n" + bullet;
        }

        String placeholderPattern = Pattern.quote(HTMLCodeBlockPreserver.CODE_BLOCK_PLACEHOLDER) + "\\d+";
        String html = el.html()
                .replaceAll("(?i)<a\\s+href=\"([^\"]+)\">(.*?)</a>", "[$2|$1]")
                .replaceAll("(?i)<strong>(.*?)</strong>", "*$1*")
                .replaceAll("(?i)<em>(.*?)</em>", "_$1_")
                .replaceAll("(?i)<b>(.*?)</b>", "*$1*")
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)<i>(.*?)</i>", "_$1_")
                .replaceAll("(?i)<code>(?!" + placeholderPattern + ")(.*?)</code>", "{{$1}}")
                .replaceAll("(?i)<[^>]+>", "");

        return unescapeHtml(html).trim();
    }
}
