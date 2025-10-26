package com.github.istin.dmtools.common.kb.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helper class for smart regeneration of KB descriptions.
 * Determines if a person/topic description needs regeneration based on Q/A/N modification times.
 */
public class KBSmartRegenerationHelper {
    
    private static final Logger logger = LogManager.getLogger(KBSmartRegenerationHelper.class);
    
    // Pattern to match Q/A/N references in markdown
    // Matches: [Q-abc123], [A-abc123], [N-abc123], questions/q-abc123.md, answers/a-abc123.md, notes/n-abc123.md
    private static final Pattern QAN_PATTERN = Pattern.compile(
        "\\[([QAN])-([a-z0-9_-]+)\\]|(?:questions/q-|answers/a-|notes/n-)([a-z0-9_-]+)\\.md"
    );
    
    /**
     * Determine if a person or topic description needs regeneration.
     * 
     * @param descFile Path to description file ([id]-desc.md)
     * @param entityFile Path to entity file (person/topic .md file)
     * @param outputPath KB output directory
     * @return true if regeneration needed, false otherwise
     */
    public boolean needsRegeneration(Path descFile, Path entityFile, Path outputPath) {
        try {
            // Check entity file first - can't regenerate without it
            if (!Files.exists(entityFile)) {
                logger.warn("Entity file doesn't exist: {}", entityFile);
                return false; // Can't regenerate without entity file
            }
            
            // If description file doesn't exist, regeneration is needed
            if (!Files.exists(descFile)) {
                logger.debug("Description file doesn't exist, regeneration needed: {}", descFile);
                return true;
            }
            
            // Get description file modification time
            long descModTime = getFileModificationTime(descFile);
            
            String entityContent = Files.readString(entityFile);
            Set<String> qanIds = extractQANIds(entityContent);
            
            if (qanIds.isEmpty()) {
                logger.debug("No Q/A/N references found in entity file: {}", entityFile);
                return false; // No Q/A/N to check
            }
            
            // Check if any Q/A/N file is newer than description
            for (String qanId : qanIds) {
                Path qanFile = resolveQANPath(qanId, outputPath);
                
                if (qanFile != null && Files.exists(qanFile)) {
                    long qanModTime = getFileModificationTime(qanFile);
                    
                    if (qanModTime > descModTime) {
                        logger.debug("Q/A/N file {} is newer than description, regeneration needed", qanId);
                        return true;
                    }
                }
            }
            
            logger.debug("All Q/A/N files older than description, regeneration not needed: {}", descFile.getFileName());
            return false;
            
        } catch (IOException e) {
            logger.error("Error checking regeneration need for {}: {}", descFile, e.getMessage());
            // On error, regenerate to be safe
            return true;
        }
    }
    
    /**
     * Extract Q/A/N IDs from markdown content.
     * 
     * @param content Markdown content to parse
     * @return Set of Q/A/N IDs (e.g., "Q-abc123", "A-xyz789")
     */
    public Set<String> extractQANIds(String content) {
        Set<String> ids = new HashSet<>();
        
        if (content == null || content.isEmpty()) {
            return ids;
        }
        
        Matcher matcher = QAN_PATTERN.matcher(content);
        
        while (matcher.find()) {
            String type = matcher.group(1); // Q, A, or N
            String id = matcher.group(2);   // ID part
            
            if (type != null && id != null) {
                // Format: Q-abc123
                ids.add(type + "-" + id);
            } else {
                // Format from file path: questions/q-abc123.md
                String fileId = matcher.group(3);
                if (fileId != null) {
                    // Determine type from context (this is a simplified approach)
                    String matchText = matcher.group();
                    if (matchText.contains("questions")) {
                        ids.add("Q-" + fileId);
                    } else if (matchText.contains("answers")) {
                        ids.add("A-" + fileId);
                    } else if (matchText.contains("notes")) {
                        ids.add("N-" + fileId);
                    }
                }
            }
        }
        
        return ids;
    }
    
    /**
     * Get file modification time in milliseconds since epoch.
     * 
     * @param file File to check
     * @return Modification time in milliseconds
     * @throws IOException if file cannot be read
     */
    public long getFileModificationTime(Path file) throws IOException {
        FileTime modTime = Files.getLastModifiedTime(file);
        return modTime.toMillis();
    }
    
    /**
     * Resolve Q/A/N ID to file path.
     * 
     * @param qanId Q/A/N ID (e.g., "Q-abc123")
     * @param outputPath KB output directory
     * @return Path to Q/A/N file, or null if invalid ID
     */
    private Path resolveQANPath(String qanId, Path outputPath) {
        if (qanId == null || qanId.length() < 3) {
            return null;
        }
        
        String type = qanId.substring(0, 1).toUpperCase();
        String id = qanId.substring(2); // Skip "Q-", "A-", or "N-"
        
        switch (type) {
            case "Q":
                return outputPath.resolve("questions").resolve("q-" + id + ".md");
            case "A":
                return outputPath.resolve("answers").resolve("a-" + id + ".md");
            case "N":
                return outputPath.resolve("notes").resolve("n-" + id + ".md");
            default:
                logger.warn("Unknown Q/A/N type: {}", type);
                return null;
        }
    }
}

