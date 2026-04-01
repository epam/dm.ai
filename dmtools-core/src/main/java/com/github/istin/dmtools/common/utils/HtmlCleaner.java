// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class HtmlCleaner {

    private static final Logger logger = LogManager.getLogger(HtmlCleaner.class);

    public static String cleanUselessHTMLTagsAndAdjustImageUrls(String basePath, String taggedInput) {
        Document document = Jsoup.parse(taggedInput);
        document.outputSettings(new org.jsoup.nodes.Document.OutputSettings().prettyPrint(false));
        // Remove all style attributes
        document.select("[style]").removeAttr("style");
        document.select("[class]").removeAttr("class");
        Elements paragraphs = document.select("p");
        for (Element paragraph : paragraphs) {
            if (paragraph.html().replaceAll("&nbsp;", " ").trim().isEmpty()) {
                paragraph.remove();
            }
        }
        // Remove all <span> tags but keep their content
        document.select("span").unwrap();

        adjustImageUrls(basePath, document);

        return document.body().html();
    }

    public static void adjustImageUrls(String basePath, Document document) {
        // Select all img tags with src attributes
        Elements imgTags = document.select("img[src]");

        // Attach the base path to each img tag
        for (Element img : imgTags) {
            String originalSrc = img.attr("src");
            if (!originalSrc.startsWith(basePath)) {
                String modifiedSrc = basePath + originalSrc;
                img.attr("src", modifiedSrc);
            }
        }
    }

    public static List<String> getAllImageUrls(String basePath, String htmlDescription) {
        Document document = Jsoup.parse(htmlDescription);
        Elements imgTags = document.select("img[src]");

        List<String> urls = new ArrayList<>();
        // Attach the base path to each img tag
        for (Element img : imgTags) {
            String originalSrc = img.attr("src");
            if (!originalSrc.startsWith(basePath)) {
                String modifiedSrc = basePath + originalSrc;
                urls.add(modifiedSrc);
            }
        }
        return urls;
    }

    public static List<String> getAllLinksUrls(String urlFilter, String htmlDescription) {
        Document document = Jsoup.parse(htmlDescription);
        Elements aTags = document.select("a[href]");

        List<String> urls = new ArrayList<>();
        // Attach the base path to each img tag
        for (Element a : aTags) {
            String originalHref = a.attr("href");
            if (urlFilter != null && originalHref.contains(urlFilter)) {
                urls.add(originalHref);
            }
        }
        return urls;
    }

    public static String cleanAllHtmlTags(String basePath, String html) {
        // Use jsoup to clean and minimize the HTML
        Document document = Jsoup.parse(html);
        adjustImageUrls(basePath, document);
        // Create a Safelist that allows <a> tags
        Safelist safelist = Safelist.none();
        safelist.addTags("a");
        safelist.addAttributes("a","href");
        safelist.addTags("img");
        safelist.addAttributes("img","src");
        return Jsoup.clean(document.body().html(), "", safelist, new org.jsoup.nodes.Document.OutputSettings().prettyPrint(false)).replaceAll("&nbsp;"," ");
    }

    public static String convertLinksUrlsToConfluenceFormat(String body) {
        // Parse the HTML
        Document doc = Jsoup.parse(body);
        doc.outputSettings(new org.jsoup.nodes.Document.OutputSettings().prettyPrint(false));
        // Select all anchor tags with href attributes
        Elements anchorTags = doc.select("a[href]");

        // Modify the href attributes: replacing "&", with "&amp;"
        for (Element anchor : anchorTags) {
            String originalHref = anchor.attr("href");
            logger.info(originalHref);
        }

        // Print the modified HTML
        return doc.body().html();
    }

    private static final Set<String> SIZE_RELATED_ATTRIBUTES = Set.of(
            "width", "height", "min-width", "min-height", "max-width", "max-height",
            "margin", "margin-top", "margin-right", "margin-bottom", "margin-left",
            "padding", "padding-top", "padding-right", "padding-bottom", "padding-left",
            "data-highlight-colour", "data-table-width", "data-layout", "ac:local-id", "ri:version-at-save", "ac:schema-version", "ac:macro-id"
    );

    public static String cleanOnlyStylesAndSizes(String html) {
        Document doc = Jsoup.parse(html);
        doc.outputSettings(new Document.OutputSettings().prettyPrint(false));

        // Remove CSS from head
        doc.select("head style").remove();
        doc.select("link[rel=stylesheet]").remove();

        // Remove JavaScript
        doc.select("script").remove();

        // Process main document and shadow DOM content
        processElementAndShadowDOM(doc);

        return cleanSvgFragment(filterBase64InText(doc.html()));
    }

    private static void processElementAndShadowDOM(Element root) {
        // Clean style attributes in current element
        if (root.hasAttr("style")) {
            String style = root.attr("style");
            String cleanedStyle = cleanSizeRelatedStyles(style);
            if (cleanedStyle.isEmpty()) {
                root.removeAttr("style");
            } else {
                root.attr("style", cleanedStyle);
            }
        }

        // Remove size-related attributes
        SIZE_RELATED_ATTRIBUTES.forEach(root::removeAttr);

        // Process all child elements
        Elements children = root.children();
        for (Element child : children) {
            // Check if this element contains shadow DOM content
            if (child.tagName().equals("cookie-consent-widget")) {
                String shadowContent = child.html();
                if (shadowContent.contains("#shadow-root")) {
                    // Create a temporary document for shadow DOM content
                    Document shadowDoc = Jsoup.parse(shadowContent);

                    // Remove styles from shadow DOM
                    shadowDoc.select("style").remove();

                    // Process elements within shadow DOM
                    processElementAndShadowDOM(shadowDoc.body());

                    // Update the shadow DOM content
                    child.html("#shadow-root (open)\n" + shadowDoc.body().html());
                }
            }

            // Continue processing regular elements
            processElementAndShadowDOM(child);
        }
    }

    /**
     * Replaces base64-encoded blocks in the given text with a placeholder.
     *
     * @param text The original text.
     * @return The text with base64 blocks replaced by a placeholder.
     */
    public static String filterBase64InText(String text) {
        // Regular expression to match base64-encoded blocks
        String base64Pattern = "data:image/[^;]+;base64,[a-zA-Z0-9+/=]+";

        // Replace all base64 blocks with a placeholder
        return text.replaceAll(base64Pattern, "[Base64 image]");
    }

    public static String cleanSvgFragment(String fragment) {
        // Regular expression to match SVG tags (e.g., <path>, <rect>, <g>, etc.)
        String svgTagPattern = "<(path|rect|g|circle|line|polygon|polyline|ellipse|defs|linearGradient|radialGradient|stop|clipPath|mask|symbol|use|text|tspan|image|pattern|marker)[^>]*>(.*?)</\\1>|<(path|rect|g|circle|line|polygon|polyline|ellipse|defs|linearGradient|radialGradient|stop|clipPath|mask|symbol|use|text|tspan|image|pattern|marker)[^>]*/?>";

        // Replace SVG tags with a placeholder

        return fragment.replaceAll(svgTagPattern, "[SVG]");
    }

    /**
     * Cleans only size-related CSS properties from style attribute
     */
    private static String cleanSizeRelatedStyles(String style) {
        if (style == null || style.isEmpty()) {
            return "";
        }

        // Split style into individual properties
        String[] properties = style.split(";");
        List<String> cleanedProperties = new ArrayList<>();

        for (String property : properties) {
            property = property.trim();
            if (property.isEmpty()) continue;

            // Check if property is size-related
            boolean isSizeRelated = false;
            for (String sizeAttr : SIZE_RELATED_ATTRIBUTES) {
                if (property.startsWith(sizeAttr + ":")) {
                    isSizeRelated = true;
                    break;
                }
            }

            // Additional size-related CSS properties
            if (property.startsWith("position:") ||
                    property.startsWith("top:") ||
                    property.startsWith("right:") ||
                    property.startsWith("bottom:") ||
                    property.startsWith("left:") ||
                    property.startsWith("float:") ||
                    property.startsWith("display:") ||
                    property.startsWith("flex:") ||
                    property.startsWith("grid:") ||
                    property.startsWith("box-sizing:")) {
                isSizeRelated = true;
            }

            // Keep non-size-related properties
            if (!isSizeRelated) {
                cleanedProperties.add(property);
            }
        }

        return String.join("; ", cleanedProperties);
    }

    /**
     * Alternative version that preserves style tags but removes size-related rules
     */
    public static String cleanOnlyStylesAndSizesPreservingStyleTags(String html) {
        Document doc = Jsoup.parse(html);
        doc.outputSettings(new Document.OutputSettings().prettyPrint(false));

        // Clean style tags content
        Elements styleTags = doc.select("style");
        for (Element styleTag : styleTags) {
            String css = styleTag.html();
            String cleanedCss = cleanSizeRelatedCSSRules(css);
            styleTag.html(cleanedCss);
        }

        // Remove JavaScript
        doc.select("script").remove();

        // Clean style attributes
        Elements elementsWithStyle = doc.select("[style]");
        for (Element element : elementsWithStyle) {
            String style = element.attr("style");
            String cleanedStyle = cleanSizeRelatedStyles(style);
            if (cleanedStyle.isEmpty()) {
                element.removeAttr("style");
            } else {
                element.attr("style", cleanedStyle);
            }
        }

        // Remove size-related attributes
        Elements allElements = doc.getAllElements();
        for (Element element : allElements) {
            SIZE_RELATED_ATTRIBUTES.forEach(element::removeAttr);
        }

        return doc.html();
    }

    /**
     * Cleans size-related rules from CSS content
     */
    private static String cleanSizeRelatedCSSRules(String css) {
        // This is a simplified version. For production use,
        // consider using a proper CSS parser
        StringBuilder cleanedCss = new StringBuilder();
        String[] rules = css.split("}");

        for (String rule : rules) {
            if (rule.trim().isEmpty()) continue;

            String[] parts = rule.split("\\{");
            if (parts.length != 2) continue;

            String selector = parts[0].trim();
            String properties = parts[1].trim();

            String cleanedProperties = cleanSizeRelatedStyles(properties);
            if (!cleanedProperties.isEmpty()) {
                cleanedCss.append(selector)
                        .append(" { ")
                        .append(cleanedProperties)
                        .append(" }\n");
            }
        }

        return cleanedCss.toString();
    }

}
