package com.github.istin.dmtools.atlassian.confluence.index;

import com.github.istin.dmtools.atlassian.confluence.Confluence;
import com.github.istin.dmtools.atlassian.confluence.model.Attachment;
import com.github.istin.dmtools.atlassian.confluence.model.Content;
import com.github.istin.dmtools.atlassian.confluence.model.ContentResult;
import com.github.istin.dmtools.common.utils.StringUtils;
import com.github.istin.dmtools.index.mermaid.MermaidIndexIntegration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Confluence implementation of MermaidIndexIntegration.
 * Retrieves content from Confluence spaces and pages using path-based patterns.
 * <p>
 * Supported path formats:
 * <ul>
 *   <li>{@code [SPACE]/pages/[pageId]/[PageName]} - direct page only</li>
 *   <li>{@code [SPACE]/pages/[pageId]/[PageName]/*} - direct page + immediate children</li>
 *   <li>{@code [SPACE]/pages/[pageId]/[PageName]/**} - direct page + all descendants</li>
 *   <li>{@code [SPACE]/**} - all pages in space</li>
 *   <li>Full URL: {@code https://company.atlassian.net/wiki/spaces/[space]/pages/[pageId]/[page]}</li>
 * </ul>
 * Page names can contain '+' characters (URL-encoded or as spaces).
 */
public class ConfluenceMermaidIndexIntegration implements MermaidIndexIntegration {
    
    private static final Logger logger = LogManager.getLogger(ConfluenceMermaidIndexIntegration.class);
    
    private final Confluence confluence;
    
    public ConfluenceMermaidIndexIntegration(Confluence confluence) {
        this.confluence = confluence;
    }
    
    @Override
    public void getContentForIndex(List<String> includePatterns, List<String> excludePatterns, ContentProcessor processor) {
        try {
            // Process content one-by-one to optimize memory usage
            // Track processed IDs to avoid duplicates across patterns
            Set<String> processedIds = new HashSet<>();
            
            if (includePatterns != null && !includePatterns.isEmpty()) {
                for (String pattern : includePatterns) {
                    logger.info("Processing pattern: {}", pattern);
                    processContentByPattern(pattern, excludePatterns, processor, processedIds);
                }
            } else {
                logger.warn("No include patterns provided, no content will be retrieved");
            }
            
        } catch (Exception e) {
            logger.error("Error retrieving content from Confluence", e);
            throw new RuntimeException("Failed to retrieve content from Confluence", e);
        }
    }
    
    /**
     * Process content matching a pattern one-by-one.
     * Processes each page immediately as it's retrieved, without loading all into memory.
     * 
     * @param pattern the pattern to match
     * @param excludePatterns patterns to exclude
     * @param processor the content processor
     * @param processedIds set of already processed content IDs to avoid duplicates
     * @throws IOException if an API error occurs
     */
    private void processContentByPattern(String pattern, List<String> excludePatterns, 
                                         ContentProcessor processor, Set<String> processedIds) throws IOException {
        // Parse as a structured path pattern
        if (!ConfluencePagePathParser.isValidPattern(pattern)) {
            throw new IllegalArgumentException("Invalid path pattern: " + pattern);
        }
        
        ConfluencePagePathParser.ParsedPath parsedPath = ConfluencePagePathParser.parse(pattern);
        
        // Process content immediately based on parsed path, without collecting into list
        processContentByParsedPath(parsedPath, excludePatterns, processor, processedIds);
    }
    
    /**
     * Process content based on a parsed path structure, streaming each page immediately.
     * 
     * @param parsedPath the parsed path information
     * @param excludePatterns patterns to exclude
     * @param processor the content processor
     * @param processedIds set of already processed content IDs
     * @throws IOException if an API error occurs
     */
    private void processContentByParsedPath(ConfluencePagePathParser.ParsedPath parsedPath,
                                            List<String> excludePatterns,
                                            ContentProcessor processor,
                                            Set<String> processedIds) throws IOException {
        switch (parsedPath.getDepth()) {
            case PAGE_ONLY:
                // Get and process only the specified page
                Content page = confluence.contentById(parsedPath.getPageId());
                if (page != null) {
                    processContentIfNotProcessed(page, excludePatterns, processor, processedIds);
                }
                break;
                
            case IMMEDIATE_CHILDREN:
                // Process page and its immediate children one-by-one
                Content parentPage = confluence.contentById(parsedPath.getPageId());
                if (parentPage != null) {
                    processContentIfNotProcessed(parentPage, excludePatterns, processor, processedIds);
                    
                    List<Content> children = confluence.getChildrenOfContentById(parsedPath.getPageId());
                    for (Content child : children) {
                        processContentIfNotProcessed(child, excludePatterns, processor, processedIds);
                    }
                }
                break;
                
            case ALL_DESCENDANTS:
                // Process page and all descendants recursively, streaming each one
                Content rootPage = confluence.contentById(parsedPath.getPageId());
                if (rootPage != null) {
                    processContentIfNotProcessed(rootPage, excludePatterns, processor, processedIds);
                    // Process descendants streaming style
                    processDescendantsStreaming(parsedPath.getPageId(), parsedPath.getPageId(), 
                                               excludePatterns, processor, processedIds);
                }
                break;
                
            case ALL_SPACE_PAGES:
                // Process all pages in the space, streaming each one
                processAllSpacePagesStreaming(parsedPath.getSpaceKey(), excludePatterns, processor, processedIds);
                break;
        }
    }
    
    /**
     * Process a single content if not already processed and not excluded.
     */
    private void processContentIfNotProcessed(Content content, List<String> excludePatterns,
                                             ContentProcessor processor, Set<String> processedIds) {
        String contentId = content.getId();
        
        // Skip if already processed
        if (processedIds.contains(contentId)) {
            logger.debug("Skipping already processed content: {}", contentId);
            return;
        }
        
        String spaceKey = getSpaceKey(content);
        String contentName = content.getTitle();
        
        // Check if content matches exclude patterns
        if (matchesExcludePattern(content, contentName, spaceKey, contentId, excludePatterns)) {
            logger.info("Skipping content {} ({}) due to exclude pattern", contentId, contentName);
            return;
        }
        
        // Process this content immediately
        processContent(content, spaceKey, contentName, contentId, processor);
        
        // Mark as processed
        processedIds.add(contentId);
    }
    
    /**
     * Process descendants streaming - get child, process immediately, recurse.
     * Does NOT collect all descendants into memory first.
     */
    private void processDescendantsStreaming(String parentId, String rootPageId,
                                            List<String> excludePatterns,
                                            ContentProcessor processor,
                                            Set<String> processedIds) throws IOException {
        Queue<String> queue = new LinkedList<>();
        queue.add(parentId);
        Set<String> visited = new HashSet<>();
        visited.add(parentId);
        
        while (!queue.isEmpty()) {
            String currentId = queue.poll();
            
            try {
                List<Content> children = confluence.getChildrenOfContentById(currentId);
                for (Content child : children) {
                    if (!visited.contains(child.getId())) {
                        String childParentId = child.getParentId();
                        if (childParentId != null && childParentId.equals(currentId)) {
                            visited.add(child.getId());
                            
                            // PROCESS IMMEDIATELY - don't add to list
                            processContentIfNotProcessed(child, excludePatterns, processor, processedIds);
                            
                            queue.add(child.getId());
                            logger.debug("Processed descendant page {} (parent: {}) under root {}", 
                                child.getId(), currentId, rootPageId);
                        } else if (currentId.equals(rootPageId)) {
                            visited.add(child.getId());
                            
                            // PROCESS IMMEDIATELY
                            processContentIfNotProcessed(child, excludePatterns, processor, processedIds);
                            
                            queue.add(child.getId());
                            logger.debug("Processed first-level child page {} under root {}", 
                                child.getId(), rootPageId);
                        } else {
                            logger.warn("Skipping page {} - parent ID mismatch (expected: {}, got: {})", 
                                child.getId(), currentId, childParentId);
                        }
                    }
                }
            } catch (Exception e) {
                logger.warn("Failed to get children for page {}: {}", currentId, e.getMessage());
            }
        }
    }
    
    /**
     * Process all pages in a space streaming style.
     */
    private void processAllSpacePagesStreaming(String spaceKey, List<String> excludePatterns,
                                              ContentProcessor processor, Set<String> processedIds) throws IOException {
        Set<String> visited = new HashSet<>();
        
        try {
            // Get root pages in the space
            List<Content> rootPages = getSpaceRootPages(spaceKey);
            
            for (Content rootPage : rootPages) {
                if (!visited.contains(rootPage.getId())) {
                    visited.add(rootPage.getId());
                    
                    // PROCESS IMMEDIATELY
                    processContentIfNotProcessed(rootPage, excludePatterns, processor, processedIds);
                    
                    // Process its descendants streaming
                    processDescendantsStreaming(rootPage.getId(), rootPage.getId(), 
                                               excludePatterns, processor, processedIds);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to process pages for space {}: {}", spaceKey, e.getMessage());
        }
    }
    
    /**
     * Gets root pages in a space (pages with no parent).
     *
     * @param spaceKey the space key
     * @return list of root pages
     * @throws IOException if an API error occurs
     */
    private List<Content> getSpaceRootPages(String spaceKey) throws IOException {
        // Use the content API to get pages in the space
        // The Confluence API path is: /rest/api/content?spaceKey={spaceKey}&type=page
        ContentResult contentResult = confluence.content("", spaceKey);
        
        List<Content> contents = contentResult.getContents();
        if (contents == null) {
            return new ArrayList<>();
        }
        
        // Filter to get only root pages (no ancestors or empty ancestors)
        return contents.stream()
                .filter(this::isRootPage)
                .collect(Collectors.toList());
    }
    
    /**
     * Checks if a content page is a root page (has no parent).
     *
     * @param content the content to check
     * @return true if the content is a root page
     */
    private boolean isRootPage(Content content) {
        String parentId = content.getParentId();
        return parentId == null || parentId.isEmpty();
    }
    
    /**
     * Processes a single content item.
     * Downloads attachments and passes them as File objects for processing.
     */
    private void processContent(Content content, String spaceKey, String contentName, String contentId, 
                               ContentProcessor processor) {
        // Extract content body
        String contentBody = "";
        if (content.getStorage() != null && content.getStorage().getValue() != null) {
            contentBody = content.getStorage().getValue();
        } else {
            // If storage is not available, fetch full content with body.storage
            try {
                logger.debug("Storage not available for content {}, fetching full content with body.storage", contentId);
                Content fullContent = confluence.contentById(contentId);
                if (fullContent != null && fullContent.getStorage() != null && fullContent.getStorage().getValue() != null) {
                    contentBody = fullContent.getStorage().getValue();
                } else {
                    logger.warn("Content body is still empty after fetching full content for {}", contentId);
                }
            } catch (Exception e) {
                logger.warn("Failed to fetch full content for {}: {}", contentId, e.getMessage());
            }
        }
        
        // Get metadata
        List<String> metadata = new ArrayList<>();
        metadata.add("spaceKey:" + spaceKey);
        metadata.add("contentId:" + contentId);
        
        // Get and download attachments
        List<File> attachmentFiles = new ArrayList<>();
        Path tempDirPath = null;
        try {
            List<Attachment> attachments = confluence.getContentAttachments(contentId);
            if (attachments != null && !attachments.isEmpty()) {
                // Create temp directory for attachments
                tempDirPath = Files.createTempDirectory("confluence-attachments-" + contentId);
                File tempDir = tempDirPath.toFile();
                
                for (Attachment attachment : attachments) {
                    String title = attachment.getTitle();
                    String downloadLink = attachment.getDownloadLink();
                    
                    if (downloadLink != null && !downloadLink.isEmpty()) {
                        try {
                            // Download attachment
                            File downloadedFile = confluence.downloadAttachment(attachment, tempDir);
                            if (downloadedFile != null && downloadedFile.exists()) {
                                attachmentFiles.add(downloadedFile);
                                logger.debug("Downloaded attachment {} for content {}", title, contentId);
                            } else {
                                logger.warn("Downloaded file is null or doesn't exist for attachment {} in content {}", title, contentId);
                            }
                        } catch (Exception e) {
                            logger.warn("Failed to download attachment {} for content {}: {}", title, contentId, e.getMessage());
                        }
                    } else {
                        logger.warn("Attachment {} has no download link for content {}", title, contentId);
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to get attachments for content {}: {}", contentId, e.getMessage());
        }
        
        // Get last modified date
        Date lastModified = content.getLastModifiedDate();
        if (lastModified == null) {
            lastModified = new Date();
        }
        
        // Build pathOrId with full ancestor chain to preserve hierarchy
        // Format: spaceKey/pageId for root pages
        // Format: spaceKey/ancestor1Id/ancestor1Name/ancestor2Id/ancestor2Name/.../pageId for nested pages
        String pathOrId;
        List<Content> ancestors = content.getModels(Content.class, "ancestors");
        if (ancestors != null && !ancestors.isEmpty()) {
            // Build full path through all ancestors to preserve hierarchy
            StringBuilder pathBuilder = new StringBuilder(spaceKey);
            for (Content ancestor : ancestors) {
                String ancestorId = ancestor.getId();
                String ancestorName = ancestor.getTitle();
                String sanitizedAncestorName = StringUtils.sanitizeFileName(ancestorName, "untitled", 200);
                pathBuilder.append("/").append(ancestorId).append("/").append(sanitizedAncestorName);
            }
            // Add the current page ID at the end
            pathBuilder.append("/").append(contentId);
            pathOrId = pathBuilder.toString();
        } else {
            // This is a root page
            pathOrId = spaceKey + "/" + contentId;
        }
        
        // Process content with attachments
        try {
            processor.process(pathOrId, contentName, contentBody, metadata, attachmentFiles, lastModified);
        } finally {
            // Clean up temp directory after processing is complete
            if (tempDirPath != null) {
                cleanupTempDirectory(tempDirPath);
            }
        }
    }
    
    /**
     * Cleans up a temporary directory and its contents.
     * Walks the directory tree in reverse order to delete files before directories.
     *
     * @param tempDirPath the path to the temporary directory
     */
    private void cleanupTempDirectory(Path tempDirPath) {
        try {
            Files.walk(tempDirPath)
                .sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        logger.debug("Failed to delete temp file: {}", path, e);
                    }
                });
        } catch (IOException e) {
            logger.warn("Failed to clean up temp directory: {}", tempDirPath, e);
        }
    }
    
    /**
     * Checks if content matches any of the exclude patterns.
     *
     * @param contentName the content title
     * @param spaceKey the space key
     * @param contentId the content ID
     * @param patterns the list of exclude patterns
     * @return true if content should be excluded
     */
    private boolean matchesExcludePattern(Content content, String contentName, String spaceKey, String contentId, List<String> patterns) {
        if (patterns == null || patterns.isEmpty()) {
            return false;
        }
        
        for (String pattern : patterns) {
            // Try to parse as structured path pattern
            if (ConfluencePagePathParser.isValidPattern(pattern)) {
                ConfluencePagePathParser.ParsedPath parsedPath = ConfluencePagePathParser.parse(pattern);
                
                // Check if this content matches the exclude pattern
                if (parsedPath.getPageId() != null) {
                    // Exact match
                    if (parsedPath.getPageId().equals(contentId)) {
                        logger.debug("Content {} matches exclude pattern (exact match): {}", contentId, pattern);
                        return true;
                    }
                    
                    // If pattern includes descendants (/**), check if current page is a descendant
                    if (parsedPath.getDepth() == ConfluencePagePathParser.RetrievalDepth.ALL_DESCENDANTS) {
                        if (isDescendantOf(content, parsedPath.getPageId())) {
                            logger.debug("Content {} matches exclude pattern (descendant of {}): {}", 
                                contentId, parsedPath.getPageId(), pattern);
                            return true;
                        }
                    }
                }
                
                if (parsedPath.isSpaceWide() && parsedPath.getSpaceKey().equals(spaceKey)) {
                    logger.debug("Content {} matches exclude pattern (space-wide): {}", contentId, pattern);
                    return true;
                }
            }
            
            // Legacy pattern matching
            if (pattern.endsWith("*")) {
                String prefix = pattern.substring(0, pattern.length() - 1);
                if (contentName != null && contentName.startsWith(prefix)) {
                    return true;
                }
                if (spaceKey != null && spaceKey.startsWith(prefix)) {
                    return true;
                }
            } else {
                if (pattern.equals(contentName) || pattern.equals(spaceKey) || pattern.equals(contentId)) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Check if content is a descendant of a given parent page ID.
     * Uses ancestors information from the Content object.
     */
    private boolean isDescendantOf(Content content, String parentPageId) {
        if (content == null || parentPageId == null) {
            return false;
        }
        
        // Get ancestors from content
        List<Content> ancestors = content.getModels(Content.class, "ancestors");
        if (ancestors == null || ancestors.isEmpty()) {
            // No ancestors info, can't determine hierarchy
            // For safety, don't exclude unless we're sure
            logger.debug("No ancestors info for content {}, cannot determine if descendant of {}", 
                content.getId(), parentPageId);
            return false;
        }
        
        // Check if any ancestor matches the parent page ID
        for (Content ancestor : ancestors) {
            if (parentPageId.equals(ancestor.getId())) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Extracts space key from Content object.
     *
     * @param content the content object
     * @return the space key or "UNKNOWN" if not found
     */
    private String getSpaceKey(Content content) {
        try {
            JSONObject expandableObj = content.getJSONObject().optJSONObject("_expandable");
            if (expandableObj != null) {
                String space = expandableObj.optString("space");
                if (space != null && !space.isEmpty()) {
                    String[] split = space.split("/");
                    if (split.length > 0) {
                        return split[split.length - 1];
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to extract space key from content: {}", e.getMessage());
        }
        
        // Fallback: try to get from space object if available
        try {
            JSONObject spaceObj = content.getJSONObject().optJSONObject("space");
            if (spaceObj != null) {
                return spaceObj.optString("key", "UNKNOWN");
            }
        } catch (Exception e) {
            logger.warn("Failed to get space key from space object: {}", e.getMessage());
        }
        
        return "UNKNOWN";
    }
}
