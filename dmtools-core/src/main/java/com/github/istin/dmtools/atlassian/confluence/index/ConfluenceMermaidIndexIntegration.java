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
import java.util.List;
import java.util.stream.Collectors;

/**
 * Confluence implementation of MermaidIndexIntegration.
 * Retrieves content from Confluence spaces and pages, applies include/exclude pattern matching,
 * and calls the processor for each matching content item.
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
            // Get all spaces or search for content based on include patterns
            List<Content> allContent = new ArrayList<>();
            
            // If include patterns are provided, search for matching content
            if (includePatterns != null && !includePatterns.isEmpty()) {
                for (String pattern : includePatterns) {
                    List<Content> matchingContent = searchContentByPattern(pattern);
                    allContent.addAll(matchingContent);
                }
            } else {
                // If no include patterns, we need to search broadly
                // For now, we'll use a generic search - this could be enhanced
                logger.warn("No include patterns provided, searching all content");
                List<Content> searchResults = searchContentByPattern("*");
                allContent.addAll(searchResults);
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
                
                // Check if content matches include patterns
                if (!matchesPattern(contentName, spaceKey, includePatterns)) {
                    continue;
                }
                
                // Check if content matches exclude patterns
                if (matchesPattern(contentName, spaceKey, excludePatterns)) {
                    logger.debug("Skipping content {} due to exclude pattern", contentId);
                    continue;
                }
                
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
            
        } catch (Exception e) {
            logger.error("Error retrieving content from Confluence", e);
            throw new RuntimeException("Failed to retrieve content from Confluence", e);
        }
    }
    
    /**
     * Searches for content matching a pattern.
     * Supports exact match and prefix wildcard (e.g., "JAI*").
     */
    private List<Content> searchContentByPattern(String pattern) throws IOException {
        List<Content> results = new ArrayList<>();
        
        // If pattern ends with *, treat as prefix match
        if (pattern.endsWith("*")) {
            String prefix = pattern.substring(0, pattern.length() - 1);
            // Search for content with title or space starting with prefix
            List<com.github.istin.dmtools.atlassian.confluence.model.SearchResult> searchResults = 
                    confluence.searchContentByText(prefix, 100);
            
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
        } else {
            // Exact match - search for content with exact title or space key
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
                            String spaceKey = getSpaceKey(content);
                            String title = content.getTitle();
                            // Check for exact match on space key or title
                            if (pattern.equals(spaceKey) || pattern.equals(title)) {
                                results.add(content);
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.warn("Failed to get content by ID {}: {}", result.getId(), e.getMessage());
                }
            }
        }
        
        return results;
    }
    
    /**
     * Checks if content matches any of the patterns.
     */
    private boolean matchesPattern(String contentName, String spaceKey, List<String> patterns) {
        if (patterns == null || patterns.isEmpty()) {
            return false;
        }
        
        for (String pattern : patterns) {
            if (pattern.endsWith("*")) {
                String prefix = pattern.substring(0, pattern.length() - 1);
                if (contentName.startsWith(prefix) || spaceKey.startsWith(prefix)) {
                    return true;
                }
            } else {
                if (pattern.equals(contentName) || pattern.equals(spaceKey)) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Extracts space key from Content object.
     */
    private String getSpaceKey(Content content) {
        try {
            String space = content.getJSONObject().optJSONObject("_expandable").optString("space");
            if (space != null && !space.isEmpty()) {
                String[] split = space.split("/");
                if (split.length > 0) {
                    return split[split.length - 1];
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
