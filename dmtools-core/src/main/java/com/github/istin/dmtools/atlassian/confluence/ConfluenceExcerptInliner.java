// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.confluence;

import com.github.istin.dmtools.atlassian.confluence.model.Content;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Materializes {@code excerpt-include} and {@code table-excerpt-include} macros in Confluence
 * storage format.
 * <p>
 * Both macros only store configuration (target page and excerpt name) in {@code body.storage};
 * the transcluded content lives on the target page inside {@code excerpt} / {@code table-excerpt}
 * macros. This class replaces every include macro with the body of the matching excerpt so the
 * subsequent Markdown conversion contains the real content.
 * <p>
 * Lookup rules:
 * <ul>
 *   <li>the target page is resolved in the {@code ri:space-key} of its link, otherwise in the
 *       space of the page containing the macro, otherwise via the default lookup,</li>
 *   <li>excerpt names are compared case-insensitively with whitespace and {@code &nbsp;} normalized,</li>
 *   <li>a blank name selects the unnamed excerpts of the target page, or all excerpts of the
 *       matching kind when none is unnamed,</li>
 *   <li>nested includes are resolved up to {@value #MAX_DEPTH} levels with cycle protection,</li>
 *   <li>an include that cannot be resolved is left unchanged.</li>
 * </ul>
 */
public class ConfluenceExcerptInliner {

    private static final Logger logger = LogManager.getLogger(ConfluenceExcerptInliner.class);

    static final int MAX_DEPTH = 3;

    private static final String EXCERPT = "excerpt";
    private static final String TABLE_EXCERPT = "table-excerpt";
    private static final String EXCERPT_INCLUDE = "excerpt-include";
    private static final String TABLE_EXCERPT_INCLUDE = "table-excerpt-include";

    private static final Pattern INCLUDE_MACRO = Pattern.compile(
            "<ac:structured-macro\\b[^>]*ac:name=\"(?:excerpt-include|table-excerpt-include)\"[^>]*?(?:/>|>.*?</ac:structured-macro>)",
            Pattern.DOTALL);

    private final Confluence confluence;
    private final Map<String, Content> pageCache = new HashMap<>();

    public ConfluenceExcerptInliner(Confluence confluence) {
        this.confluence = confluence;
    }

    /**
     * @param storageHtml    storage format of the page being converted
     * @param pageSpaceKey   space key of that page, may be {@code null}
     * @return storage format with resolvable include macros replaced by excerpt content
     */
    public String inline(String storageHtml, String pageSpaceKey) {
        if (confluence == null || storageHtml == null || !storageHtml.contains("excerpt-include")) {
            return storageHtml;
        }
        return inline(storageHtml, pageSpaceKey, 0, new HashSet<>());
    }

    private String inline(String storageHtml, String spaceKey, int level, Set<String> path) {
        if (level >= MAX_DEPTH || !storageHtml.contains("excerpt-include")) {
            return storageHtml;
        }
        Matcher matcher = INCLUDE_MACRO.matcher(storageHtml);
        StringBuilder result = new StringBuilder();
        int last = 0;
        while (matcher.find()) {
            result.append(storageHtml, last, matcher.start());
            String macro = matcher.group();
            String replacement = null;
            try {
                replacement = resolve(macro, spaceKey, level, path);
            } catch (Exception e) {
                logger.warn("Could not resolve Confluence include macro (keeping as is): {}", e.getMessage());
            }
            result.append(replacement != null ? replacement : macro);
            last = matcher.end();
        }
        result.append(storageHtml, last, storageHtml.length());
        return result.toString();
    }

    private String resolve(String macroXml, String spaceKey, int level, Set<String> path) throws Exception {
        Document doc = Jsoup.parse(macroXml, "", Parser.xmlParser());
        Element macro = doc.getElementsByTag("ac:structured-macro").first();
        if (macro == null) {
            return null;
        }
        String kind = TABLE_EXCERPT_INCLUDE.equalsIgnoreCase(macro.attr("ac:name")) ? TABLE_EXCERPT : EXCERPT;

        String title = null;
        String targetSpace = null;
        Element pageRef = macro.getElementsByTag("ri:page").first();
        if (pageRef != null) {
            title = pageRef.attr("ri:content-title");
            targetSpace = pageRef.attr("ri:space-key");
        }
        if (title == null || title.isBlank()) {
            title = parameter(macro, "page-title");
        }
        if (title == null || title.isBlank()) {
            return null;
        }
        if (targetSpace == null || targetSpace.isBlank()) {
            targetSpace = spaceKey;
        }
        String name = parameter(macro, "name");

        Content target = findPage(title, targetSpace);
        if (target == null || target.getStorage() == null || target.getStorage().getValue() == null) {
            logger.debug("Include target '{}' not found, leaving macro unresolved", title);
            return null;
        }

        String visitKey = target.getId() + "|" + normalize(name) + "|" + kind;
        if (!path.add(visitKey)) {
            logger.debug("Include cycle detected at '{}', leaving macro unresolved", title);
            return null;
        }
        try {
            List<String> bodies = extractExcerptBodies(target.getStorage().getValue(), kind, name);
            if (bodies.isEmpty()) {
                logger.debug("No excerpt '{}' on page '{}', leaving macro unresolved", name, title);
                return null;
            }
            String targetPageSpace = target.getSpaceKey() != null ? target.getSpaceKey() : targetSpace;
            StringBuilder sb = new StringBuilder();
            for (String body : bodies) {
                sb.append(inline(body, targetPageSpace, level + 1, path));
            }
            return sb.toString();
        } finally {
            path.remove(visitKey);
        }
    }

    private Content findPage(String title, String spaceKey) throws Exception {
        String cacheKey = (spaceKey == null ? "" : spaceKey) + "|" + title;
        if (pageCache.containsKey(cacheKey)) {
            return pageCache.get(cacheKey);
        }
        Content page = null;
        if (spaceKey != null && !spaceKey.isBlank()) {
            page = confluence.findContent(title, spaceKey);
        }
        if (page == null) {
            page = confluence.findContent(title);
        }
        pageCache.put(cacheKey, page);
        return page;
    }

    static List<String> extractExcerptBodies(String storageHtml, String kind, String requestedName) {
        List<String> named = new ArrayList<>();
        List<String> unnamed = new ArrayList<>();
        List<String> all = new ArrayList<>();
        String wanted = normalize(requestedName);

        Document doc = Jsoup.parse(storageHtml, "", Parser.xmlParser());
        doc.outputSettings().prettyPrint(false);
        for (Element macro : doc.getElementsByTag("ac:structured-macro")) {
            if (!kind.equalsIgnoreCase(macro.attr("ac:name"))) {
                continue;
            }
            Element body = macro.getElementsByTag("ac:rich-text-body").first();
            if (body == null) {
                continue;
            }
            String name = normalize(parameter(macro, "name"));
            String html = body.html();
            all.add(html);
            if (name.isEmpty()) {
                unnamed.add(html);
            }
            if (!wanted.isEmpty() && name.equals(wanted)) {
                named.add(html);
            }
        }
        if (!wanted.isEmpty()) {
            return named;
        }
        return unnamed.isEmpty() ? all : unnamed;
    }

    private static String parameter(Element macro, String parameterName) {
        for (Element param : macro.getElementsByTag("ac:parameter")) {
            if (parameterName.equals(param.attr("ac:name"))) {
                return param.text();
            }
        }
        return "";
    }

    static String normalize(String name) {
        if (name == null) {
            return "";
        }
        return name.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }
}
