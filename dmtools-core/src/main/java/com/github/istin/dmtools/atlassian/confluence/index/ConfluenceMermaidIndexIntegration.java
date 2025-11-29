package com.github.istin.dmtools.atlassian.confluence.index;

import com.github.istin.dmtools.atlassian.confluence.Confluence;
import com.github.istin.dmtools.atlassian.confluence.model.Attachment;
import com.github.istin.dmtools.atlassian.confluence.model.Content;
import com.github.istin.dmtools.index.mermaid.MermaidIndexIntegration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
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
            // Collect all content based on include patterns
            List<Content> allContent = new ArrayList<>();
            
            if (includePatterns != null && !includePatterns.isEmpty()) {
                for (String pattern : includePatterns) {
                    List<Content> matchingContent = getContentByPattern(pattern);
                    allContent.addAll(matchingContent);
                }
            } else {
                logger.warn("No include patterns provided, no content will be retrieved");
            }
            
            // Remove duplicates based on content ID
            allContent = allContent.stream()
                    .collect(Collectors.toMap(Content::getId, content -> content, (first, second) -> first))
                    .values()
                    .stream()
                    .collect(Collectors.toList());
            
            // Process each content item
            for (Content content : allContent) {
                String spaceKey = getSpaceKey(content);
                String contentName = content.getTitle();
                String contentId = content.getId();
                
                // Check if content matches exclude patterns
                if (matchesExcludePattern(contentName, spaceKey, contentId, excludePatterns)) {
                    logger.debug("Skipping content {} due to exclude pattern", contentId);
                    continue;
                }
                
                processContent(content, spaceKey, contentName, contentId, processor);
            }
            
        } catch (Exception e) {
            logger.error("Error retrieving content from Confluence", e);
            throw new RuntimeException("Failed to retrieve content from Confluence", e);
        }
    }
    
    /**
     * Retrieves content matching the given path pattern.
     * Uses direct API calls instead of search.
     *
     * @param pattern the path pattern to match
     * @return list of matching content
     * @throws IOException if an API error occurs
     */
    private List<Content> getContentByPattern(String pattern) throws IOException {
        List<Content> results = new ArrayList<>();
        
        // Try to parse as a structured path pattern
        if (ConfluencePagePathParser.isValidPattern(pattern)) {
            ConfluencePagePathParser.ParsedPath parsedPath = ConfluencePagePathParser.parse(pattern);
            return getContentByParsedPath(parsedPath);
        }
        
        // Fallback: treat as a legacy simple pattern (space key or page title)
        logger.debug("Pattern '{}' not recognized as structured path, treating as legacy pattern", pattern);
        return getContentByLegacyPattern(pattern);
    }
    
    /**
     * Retrieves content based on a parsed path structure.
     *
     * @param parsedPath the parsed path information
     * @return list of matching content
     * @throws IOException if an API error occurs
     */
    private List<Content> getContentByParsedPath(ConfluencePagePathParser.ParsedPath parsedPath) throws IOException {
        List<Content> results = new ArrayList<>();
        
        switch (parsedPath.getDepth()) {
            case PAGE_ONLY:
                // Get only the specified page
                Content page = confluence.contentById(parsedPath.getPageId());
                if (page != null) {
                    results.add(page);
                }
                break;
                
            case IMMEDIATE_CHILDREN:
                // Get page and its immediate children
                Content parentPage = confluence.contentById(parsedPath.getPageId());
                if (parentPage != null) {
                    results.add(parentPage);
                    List<Content> children = confluence.getChildrenOfContentById(parsedPath.getPageId());
                    results.addAll(children);
                }
                break;
                
            case ALL_DESCENDANTS:
                // Get page and all descendants recursively
                Content rootPage = confluence.contentById(parsedPath.getPageId());
                if (rootPage != null) {
                    results.add(rootPage);
                    collectDescendants(parsedPath.getPageId(), results, new HashSet<>());
                }
                break;
                
            case ALL_SPACE_PAGES:
                // Get all pages in the space
                results.addAll(getAllPagesInSpace(parsedPath.getSpaceKey()));
                break;
        }
        
        return results;
    }
    
    /**
     * Collects all descendant pages iteratively using a queue.
     * This approach avoids potential stack overflow issues with deeply nested page hierarchies.
     *
     * @param parentId the parent page ID
     * @param results the list to add results to
     * @param visited set of already visited page IDs to prevent cycles
     * @throws IOException if an API error occurs
     */
    private void collectDescendants(String parentId, List<Content> results, Set<String> visited) throws IOException {
        java.util.Queue<String> queue = new java.util.LinkedList<>();
        queue.add(parentId);
        visited.add(parentId);
        
        while (!queue.isEmpty()) {
            String currentId = queue.poll();
            
            try {
                List<Content> children = confluence.getChildrenOfContentById(currentId);
                for (Content child : children) {
                    if (!visited.contains(child.getId())) {
                        visited.add(child.getId());
                        results.add(child);
                        queue.add(child.getId());
                    }
                }
            } catch (Exception e) {
                logger.warn("Failed to get children for page {}: {}", currentId, e.getMessage());
            }
        }
    }
    
    /**
     * Gets all pages in a space by starting from root pages.
     * This method retrieves pages without using search.
     *
     * @param spaceKey the space key
     * @return list of all pages in the space
     * @throws IOException if an API error occurs
     */
    private List<Content> getAllPagesInSpace(String spaceKey) throws IOException {
        List<Content> results = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        
        try {
            // Get root pages in the space using content API
            List<Content> rootPages = getSpaceRootPages(spaceKey);
            
            for (Content rootPage : rootPages) {
                if (!visited.contains(rootPage.getId())) {
                    results.add(rootPage);
                    visited.add(rootPage.getId());
                    collectDescendants(rootPage.getId(), results, visited);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to get pages for space {}: {}", spaceKey, e.getMessage());
        }
        
        return results;
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
        com.github.istin.dmtools.atlassian.confluence.model.ContentResult contentResult = 
                confluence.content("", spaceKey);
        
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
     * Handles legacy patterns that are not structured paths.
     * Falls back to search-based retrieval for backward compatibility.
     *
     * @param pattern the legacy pattern
     * @return list of matching content
     * @throws IOException if an API error occurs
     */
    private List<Content> getContentByLegacyPattern(String pattern) throws IOException {
        List<Content> results = new ArrayList<>();
        
        // Try to treat as space key with /** suffix
        if (pattern.endsWith("**")) {
            String spaceKey = pattern.substring(0, pattern.length() - 2);
            if (spaceKey.endsWith("/")) {
                spaceKey = spaceKey.substring(0, spaceKey.length() - 1);
            }
            return getAllPagesInSpace(spaceKey);
        }
        
        // For other patterns, use search as fallback
        List<com.github.istin.dmtools.atlassian.confluence.model.SearchResult> searchResults = 
                confluence.searchContentByText(pattern, 100);
        
        for (com.github.istin.dmtools.atlassian.confluence.model.SearchResult result : searchResults) {
            try {
                String contentId = result.getId();
                if (contentId == null) {
                    contentId = result.getEntityId();
                }
                if (contentId != null) {
                    Content content = confluence.contentById(contentId);
                    if (content != null) {
                        results.add(content);
                    }
                }
            } catch (Exception e) {
                logger.warn("Failed to get content by ID {}: {}", result.getId(), e.getMessage());
            }
        }
        
        return results;
    }
    
    /**
     * Processes a single content item.
     */
    private void processContent(Content content, String spaceKey, String contentName, String contentId, 
                               ContentProcessor processor) {
        // Extract content body
        String contentBody = "";
        if (content.getStorage() != null && content.getStorage().getValue() != null) {
            contentBody = content.getStorage().getValue();
        }
        
        // Get metadata
        List<String> metadata = new ArrayList<>();
        metadata.add("spaceKey:" + spaceKey);
        metadata.add("contentId:" + contentId);
        
        // Get attachments
        try {
            List<Attachment> attachments = confluence.getContentAttachments(contentId);
            if (attachments != null && !attachments.isEmpty()) {
                for (Attachment attachment : attachments) {
                    metadata.add("attachment:" + attachment.getTitle());
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
        
        // Call processor
        String pathOrId = spaceKey + "/" + contentId;
        processor.process(pathOrId, contentName, contentBody, metadata, lastModified);
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
    private boolean matchesExcludePattern(String contentName, String spaceKey, String contentId, List<String> patterns) {
        if (patterns == null || patterns.isEmpty()) {
            return false;
        }
        
        for (String pattern : patterns) {
            // Try to parse as structured path pattern
            if (ConfluencePagePathParser.isValidPattern(pattern)) {
                ConfluencePagePathParser.ParsedPath parsedPath = ConfluencePagePathParser.parse(pattern);
                
                // Check if this content matches the exclude pattern
                if (parsedPath.getPageId() != null && parsedPath.getPageId().equals(contentId)) {
                    return true;
                }
                if (parsedPath.isSpaceWide() && parsedPath.getSpaceKey().equals(spaceKey)) {
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
     * Extracts space key from Content object.
     *
     * @param content the content object
     * @return the space key or "UNKNOWN" if not found
     */
    private String getSpaceKey(Content content) {
        try {
            org.json.JSONObject expandableObj = content.getJSONObject().optJSONObject("_expandable");
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
            org.json.JSONObject spaceObj = content.getJSONObject().optJSONObject("space");
            if (spaceObj != null) {
                return spaceObj.optString("key", "UNKNOWN");
            }
        } catch (Exception e) {
            logger.warn("Failed to get space key from space object: {}", e.getMessage());
        }
        
        return "UNKNOWN";
    }
}
