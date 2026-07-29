// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.confluence;

import com.github.istin.dmtools.atlassian.confluence.model.Content;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

/**
 * Downloads Confluence pages (and optionally their attachments) recursively.
 * <p>
 * Given one or more seed URLs, the downloader resolves each page, converts its
 * {@code body.storage} value to Markdown, writes it to a per-page folder, and then
 * discovers related pages up to the configured depth:
 * <ul>
 *   <li>external/full Confluence URLs found in the page body,</li>
 *   <li>child pages declared by the {@code children} macro,</li>
 *   <li>internal {@code <ac:link><ri:page .../></ac:link>} references.</li>
 * </ul>
 * <p>
 * This logic is shared between the CLI MCP tool and the Teammate input-folder
 * preparation step so both behave identically.
 */
public class ConfluencePageDownloader {

    private final Logger logger = LogManager.getLogger(ConfluencePageDownloader.class);

    private final Confluence confluence;

    public ConfluencePageDownloader(Confluence confluence) {
        this.confluence = confluence;
    }

    /**
     * Downloads Confluence pages starting from {@code seedUrls} into {@code outputDir}.
     *
     * @param seedUrls            URLs of the pages linked from the ticket / explicitly requested
     * @param outputDir           folder where pages will be written (created if missing)
     * @param depth               how many levels of related pages to follow (0 = seed pages only)
     * @param downloadAttachments whether to download attachments for every fetched page
     * @return the number of pages written
     * @throws IOException if the output directory cannot be created
     */
    public int downloadPages(List<String> seedUrls, File outputDir, int depth, boolean downloadAttachments)
            throws IOException {
        if (confluence == null || seedUrls == null || seedUrls.isEmpty()) {
            return 0;
        }

        Files.createDirectories(outputDir.toPath());
        logger.info("Downloading Confluence pages to {} with depth={} attachments={}",
                outputDir.getAbsolutePath(), depth, downloadAttachments);

        Queue<PageLevel> queue = new LinkedList<>();
        Set<String> processedIds = new HashSet<>();
        Set<String> processedUrls = new HashSet<>();

        // Seed queue with the explicitly requested pages (level 0)
        for (String url : seedUrls) {
            if (url == null || url.isBlank()) {
                continue;
            }
            try {
                Content page = confluence.contentByUrl(url);
                processedUrls.add(url);
                if (page != null && page.getId() != null) {
                    queue.add(new PageLevel(page, 0));
                } else {
                    logger.warn("Confluence returned null Content for {}", url);
                }
            } catch (Exception e) {
                logger.warn("Could not fetch Confluence seed page {} (skipping): {}", url, e.getMessage());
            }
        }

        int written = 0;
        int index = 0;
        while (!queue.isEmpty()) {
            PageLevel current = queue.poll();
            Content page = current.page;
            int level = current.level;
            String contentId = page.getId();

            if (!processedIds.add(contentId)) {
                logger.debug("Skipping already processed Confluence page {}", contentId);
                continue;
            }

            try {
                String title = page.getTitle();
                String safeName = deriveSafeName(title, null, index++);
                Path pageFolder = outputDir.toPath().resolve(safeName);
                Files.createDirectories(pageFolder);

                // Write page text
                String bodyText = page.getStorage() != null && page.getStorage().getValue() != null
                        ? page.getStorage().getValue().trim() : "";
                if (!bodyText.isBlank()) {
                    String markdownText = ConfluenceStorageMarkdown.toMarkdown(bodyText);
                    Path mdFile = pageFolder.resolve(safeName + ".md");
                    Files.write(mdFile, markdownText.getBytes(StandardCharsets.UTF_8));
                    logger.info("Wrote Confluence page -> {} ({} chars, markdown)", mdFile, markdownText.length());
                    written++;

                    // Discover related pages for the next depth level
                    if (level < depth) {
                        List<Content> related = discoverRelatedPages(page, processedIds, processedUrls);
                        for (Content relatedPage : related) {
                            queue.add(new PageLevel(relatedPage, level + 1));
                        }
                    }
                } else {
                    logger.warn("Confluence page '{}' has empty body, skipping text write", title);
                }

                // Download attachments
                if (downloadAttachments && contentId != null && !contentId.isBlank()) {
                    downloadPageAttachments(contentId, pageFolder.toFile());
                }
            } catch (Exception e) {
                logger.warn("Could not process Confluence page {} (skipping): {}", contentId, e.getMessage());
            }
        }

        logger.info("Wrote {} Confluence page(s) to {}", written, outputDir.getAbsolutePath());
        return written;
    }

    /**
     * Convenience overload that accepts an array of seed URLs.
     */
    public int downloadPages(String[] seedUrls, File outputDir, int depth, boolean downloadAttachments)
            throws IOException {
        return downloadPages(seedUrls != null ? Arrays.asList(seedUrls) : null, outputDir, depth, downloadAttachments);
    }

    /**
     * Discovers pages related to {@code page}: external/full Confluence URLs, children-macro children,
     * and internal {@code ac:link} references to other pages.
     */
    private List<Content> discoverRelatedPages(Content page, Set<String> alreadyQueuedIds,
                                                Set<String> alreadyProcessedUrls) {
        List<Content> related = new ArrayList<>();
        String storageHtml = page.getStorage() != null ? page.getStorage().getValue() : null;
        if (storageHtml == null || storageHtml.isBlank()) {
            return related;
        }

        // 1. External/full Confluence URLs inside the page body
        try {
            Set<String> urls = confluence.parseUris(storageHtml);
            if (urls != null) {
                for (String url : urls) {
                    if (!alreadyProcessedUrls.add(url)) {
                        continue;
                    }
                    try {
                        Content linked = confluence.contentByUrl(url);
                        if (linked != null && linked.getId() != null && !alreadyQueuedIds.contains(linked.getId())) {
                            related.add(linked);
                        }
                    } catch (Exception e) {
                        logger.debug("Could not resolve linked Confluence URL {}: {}", url, e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Failed to parse URIs from page {}: {}", page.getId(), e.getMessage());
        }

        // 2. Children macros
        if (hasChildrenMacro(storageHtml)) {
            try {
                List<Content> children = confluence.getChildrenOfContentById(page.getId());
                if (children != null) {
                    for (Content child : children) {
                        if (child != null && child.getId() != null && !alreadyQueuedIds.contains(child.getId())) {
                            related.add(child);
                        }
                    }
                }
            } catch (Exception e) {
                logger.warn("Could not fetch children for page {}: {}", page.getId(), e.getMessage());
            }
        }

        // 3. Internal ac:link page references
        List<PageLink> internalLinks = extractInternalPageLinks(storageHtml);
        for (PageLink link : internalLinks) {
            try {
                Content linked;
                if (link.spaceKey != null && !link.spaceKey.isBlank()) {
                    linked = confluence.findContent(link.title, link.spaceKey);
                } else {
                    linked = confluence.findContent(link.title);
                }
                if (linked != null && linked.getId() != null && !alreadyQueuedIds.contains(linked.getId())) {
                    related.add(linked);
                }
            } catch (Exception e) {
                logger.debug("Could not resolve internal Confluence link '{}' (space={}): {}",
                        link.title, link.spaceKey, e.getMessage());
            }
        }

        return related;
    }

    /**
     * Parses Confluence storage format HTML and returns all internal page links
     * ({@code <ac:link><ri:page ri:content-title="..." [ri:space-key="..."]/></ac:link>}).
     */
    private List<PageLink> extractInternalPageLinks(String storageHtml) {
        List<PageLink> links = new ArrayList<>();
        if (storageHtml == null || storageHtml.isBlank()) {
            return links;
        }
        try {
            Document doc = Jsoup.parseBodyFragment(storageHtml);
            for (Element link : doc.getElementsByTag("ac:link")) {
                Element page = link.getElementsByTag("ri:page").first();
                if (page != null) {
                    String title = page.attr("ri:content-title");
                    String spaceKey = page.attr("ri:space-key");
                    if (title != null && !title.isBlank()) {
                        links.add(new PageLink(title, spaceKey));
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Failed to extract internal Confluence links: {}", e.getMessage());
        }
        return links;
    }

    /**
     * Returns true if the storage format contains a {@code children} macro.
     */
    private boolean hasChildrenMacro(String storageHtml) {
        if (storageHtml == null || storageHtml.isBlank()) {
            return false;
        }
        try {
            Document doc = Jsoup.parseBodyFragment(storageHtml);
            for (Element macro : doc.getElementsByTag("ac:structured-macro")) {
                if ("children".equalsIgnoreCase(macro.attr("ac:name"))) {
                    return true;
                }
            }
        } catch (Exception e) {
            logger.debug("Failed to check for children macro: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Downloads all attachments for a Confluence content item into {@code targetDir}.
     */
    private void downloadPageAttachments(String contentId, File targetDir) {
        try {
            List<File> files = confluence.downloadPageAttachments(contentId, targetDir);
            logger.info("Downloaded {} attachment(s) for Confluence page {} to {}",
                    files.size(), contentId, targetDir.getAbsolutePath());
        } catch (Exception e) {
            logger.warn("Could not download attachments for content {} (skipping): {}", contentId, e.getMessage());
        }
    }

    /** Derives a safe filesystem name from a Confluence page title or URL. */
    private static String deriveSafeName(String title, String url, int index) {
        if (title != null && !title.isBlank()) {
            return title.replaceAll("[^\\w.\\-]", "_");
        }
        if (url != null && !url.isBlank()) {
            String urlPath = url.replaceAll("\\?.*", "").replaceAll("#.*", "");
            String[] segments = urlPath.split("/");
            String raw = segments[segments.length - 1];
            return raw.isBlank() ? "page-" + index : raw.replaceAll("[^\\w.\\-]", "_");
        }
        return "page-" + index;
    }

    /**
     * Simple carrier for a page + its depth level in the BFS.
     */
    private static final class PageLevel {
        final Content page;
        final int level;

        PageLevel(Content page, int level) {
            this.page = page;
            this.level = level;
        }
    }

    /**
     * Simple carrier for an internal Confluence page link.
     */
    private static final class PageLink {
        final String title;
        final String spaceKey;

        PageLink(String title, String spaceKey) {
            this.title = title;
            this.spaceKey = spaceKey;
        }
    }
}
