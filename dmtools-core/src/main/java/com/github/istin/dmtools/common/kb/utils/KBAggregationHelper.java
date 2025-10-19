package com.github.istin.dmtools.common.kb.utils;

import com.github.istin.dmtools.common.kb.KBStructureBuilder;
import com.github.istin.dmtools.common.kb.agent.KBAggregationAgent;
import com.github.istin.dmtools.common.kb.params.AggregationParams;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility for performing AI aggregation on KB entities
 * Generates high-level descriptions for people, topics, and themes
 */
public class KBAggregationHelper {
    
    private static final Logger logger = LogManager.getLogger(KBAggregationHelper.class);
    private static final Pattern LINK_PATTERN = Pattern.compile("\\[\\[.*?/([qan]_\\d+)\\|");
    private static final Pattern EMBED_PATTERN = Pattern.compile("!\\[\\[([qan]_\\d+)\\]\\]");
    
    private final KBAggregationAgent aggregationAgent;
    private final KBStructureBuilder structureBuilder;
    private final KBContextLoader contextLoader;
    
    public KBAggregationHelper(KBAggregationAgent aggregationAgent, 
                               KBStructureBuilder structureBuilder,
                               KBContextLoader contextLoader) {
        this.aggregationAgent = aggregationAgent;
        this.structureBuilder = structureBuilder;
        this.contextLoader = contextLoader;
    }
    
    /**
     * Aggregate person profile - generate AI description
     */
    public void aggregatePerson(String personName, Path outputPath, String extraInstructions) throws Exception {
        String personId = structureBuilder.normalizePersonName(personName);
        Path personFile = outputPath.resolve("people").resolve(personId).resolve(personId + ".md");
        
        if (!Files.exists(personFile)) {
            return;
        }
        
        // Read person data
        String personContent = Files.readString(personFile);
        
        // Extract Q/A/N IDs and build full content
        StringBuilder fullContent = buildPersonContent(personName, personContent, outputPath);
        
        // Prepare and run aggregation
        AggregationParams params = new AggregationParams();
        params.setEntityType("person");
        params.setEntityId(personId);
        params.setKbPath(outputPath);
        params.setExtraInstructions(extraInstructions);
        
        Map<String, Object> entityData = new HashMap<>();
        entityData.put("name", personName);
        entityData.put("content", fullContent.toString());
        params.setEntityData(entityData);
        
        logger.info("Generating AI description for person: {}", personName);
        String description = aggregationAgent.run(params);
        
        // Update description file
        Path descFile = personFile.getParent().resolve(personId + "-desc.md");
        String descContent = "<!-- AI_CONTENT_START -->\n" + description + "\n<!-- AI_CONTENT_END -->\n";
        Files.writeString(descFile, descContent);
    }
    
    /**
     * Aggregate topic - generate AI description
     */
    public void aggregateTopic(String topicName, Path outputPath, String extraInstructions) throws Exception {
        String topicId = structureBuilder.slugify(topicName);
        Path topicFile = outputPath.resolve("topics").resolve(topicId + ".md");
        
        if (!Files.exists(topicFile)) {
            logger.warn("Topic file not found: {}", topicFile);
            return;
        }
        
        // Read topic data
        String topicContent = Files.readString(topicFile);
        
        // Extract embedded Q/A/N IDs and build full content
        StringBuilder fullContent = buildTopicContent(topicName, topicContent, outputPath);
        
        // Prepare and run aggregation
        AggregationParams params = new AggregationParams();
        params.setEntityType("topic");
        params.setEntityId(topicId);
        params.setKbPath(outputPath);
        params.setExtraInstructions(extraInstructions);
        
        Map<String, Object> entityData = new HashMap<>();
        entityData.put("title", topicName);
        entityData.put("content", fullContent.toString());
        params.setEntityData(entityData);
        
        logger.info("Generating AI description for topic: {}", topicName);
        String description = aggregationAgent.run(params);
        
        // Update description file
        Path descFile = outputPath.resolve("topics").resolve(topicId + "-desc.md");
        String descContent = "<!-- AI_CONTENT_START -->\n" + description + "\n<!-- AI_CONTENT_END -->\n";
        Files.writeString(descFile, descContent);
    }
    
    /**
     * Aggregate topic by ID (for AGGREGATE_ONLY mode)
     */
    public void aggregateTopicById(String topicId, Path outputPath, String extraInstructions) throws Exception {
        Path topicFile = outputPath.resolve("topics").resolve(topicId + ".md");
        
        if (!Files.exists(topicFile)) {
            logger.warn("Topic file not found: {}", topicFile);
            return;
        }
        
        // Read topic data and extract title
        String topicContent = Files.readString(topicFile);
        String topicName = contextLoader.extractTopicTitle(topicContent, topicId);
        
        // Delegate to aggregateTopic
        aggregateTopic(topicName, outputPath, extraInstructions);
    }
    
    /**
     * Build full content for person aggregation
     */
    private StringBuilder buildPersonContent(String personName, String personContent, Path outputPath) throws IOException {
        StringBuilder fullContent = new StringBuilder();
        fullContent.append("# Person Profile: ").append(personName).append("\n\n");
        fullContent.append("## Metadata\n");
        
        int metadataEnd = personContent.indexOf("---", 5) + 3;
        if (metadataEnd > 2) {
            fullContent.append(personContent.substring(0, metadataEnd)).append("\n\n");
        }
        
        // Extract Q/A/N IDs from links
        Set<String> processedIds = extractIdsFromContent(personContent, LINK_PATTERN);
        
        // Read and append content for each ID
        for (String itemId : processedIds) {
            String type = getTypeFromId(itemId);
            Path itemFile = outputPath.resolve(type).resolve(itemId + ".md");
            
            if (Files.exists(itemFile)) {
                String itemContent = Files.readString(itemFile);
                fullContent.append("## ").append(itemId.toUpperCase()).append("\n");
                fullContent.append(itemContent).append("\n\n");
            }
        }
        
        return fullContent;
    }
    
    /**
     * Build full content for topic aggregation
     */
    private StringBuilder buildTopicContent(String topicName, String topicContent, Path outputPath) throws IOException {
        StringBuilder fullContent = new StringBuilder();
        fullContent.append("# Topic: ").append(topicName).append("\n\n");
        fullContent.append("## Metadata\n");
        
        int metadataEnd = topicContent.indexOf("---", 5) + 3;
        if (metadataEnd > 2) {
            fullContent.append(topicContent.substring(0, metadataEnd)).append("\n\n");
        }
        
        // Extract embedded Q/A/N IDs
        Set<String> processedIds = extractIdsFromContent(topicContent, EMBED_PATTERN);
        
        // Read and append content for each ID
        for (String itemId : processedIds) {
            String type = getTypeFromId(itemId);
            Path itemFile = outputPath.resolve(type).resolve(itemId + ".md");
            
            if (Files.exists(itemFile)) {
                String itemContent = Files.readString(itemFile);
                fullContent.append("## ").append(itemId.toUpperCase()).append("\n");
                fullContent.append(itemContent).append("\n\n");
            }
        }
        
        return fullContent;
    }
    
    /**
     * Extract IDs from content using pattern
     */
    private Set<String> extractIdsFromContent(String content, Pattern pattern) {
        Set<String> ids = new HashSet<>();
        Matcher matcher = pattern.matcher(content);
        
        while (matcher.find()) {
            ids.add(matcher.group(1));
        }
        
        return ids;
    }
    
    /**
     * Get directory type from ID prefix
     */
    private String getTypeFromId(String itemId) {
        if (itemId.startsWith("q_")) return "questions";
        if (itemId.startsWith("a_")) return "answers";
        return "notes";
    }
}

