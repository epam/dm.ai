package com.github.istin.dmtools.common.kb.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for transforming WebVTT (Video Text Tracks) format into clean text for KB processing.
 * 
 * <p>Input VTT format:</p>
 * <pre>
 * WEBVTT
 * 
 * uuid/277-0
 * 00:00:17.013 --> 00:00:17.213
 * &lt;v Speaker Name&gt;Some text.&lt;/v&gt;
 * </pre>
 * 
 * <p>Output format:</p>
 * <pre>
 * Date: 2025-10-24
 * 
 * [00:00:17] Speaker Name: Some text.
 * [00:00:20] Another Speaker: More text here.
 * </pre>
 */
public class VTTUtils {
    
    private static final Logger logger = LogManager.getLogger(VTTUtils.class);
    
    // Pattern to match VTT timestamp line: 00:00:17.013 --> 00:00:17.213
    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile(
        "(\\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\s*-->\\s*(\\d{2}:\\d{2}:\\d{2}\\.\\d{3})"
    );
    
    // Pattern to match speaker tag opening: <v Speaker Name>
    private static final Pattern SPEAKER_OPEN_PATTERN = Pattern.compile(
        "<v\\s+([^>]+)>"
    );
    
    // Pattern to match closing tag: </v>
    private static final Pattern SPEAKER_CLOSE_PATTERN = Pattern.compile(
        "</v>"
    );
    
    /**
     * Transform VTT content into clean, human-readable text format.
     * 
     * @param vttContent Raw VTT file content
     * @return Cleaned text with timestamps, speakers, and phrases
     */
    public static String transformVTT(String vttContent) {
        return transformVTT(vttContent, null);
    }
    
    /**
     * Transform VTT content into clean, human-readable text format with optional date header.
     * 
     * @param vttContent Raw VTT file content
     * @param date Optional date to add at the beginning (format: YYYY-MM-DD)
     * @return Cleaned text with timestamps, speakers, and phrases
     */
    public static String transformVTT(String vttContent, String date) {
        if (vttContent == null || vttContent.trim().isEmpty()) {
            logger.warn("Empty VTT content provided");
            return "";
        }
        
        List<VTTEntry> entries = parseVTT(vttContent);
        
        if (entries.isEmpty()) {
            logger.warn("No valid VTT entries found in content");
            return vttContent; // Return original if parsing fails
        }
        
        return formatEntries(entries, date);
    }
    
    /**
     * Parse VTT content into structured entries.
     * 
     * @param vttContent Raw VTT file content
     * @return List of parsed VTT entries
     */
    private static List<VTTEntry> parseVTT(String vttContent) {
        List<VTTEntry> entries = new ArrayList<>();
        String[] lines = vttContent.split("\n");
        
        String currentTimestamp = null;
        StringBuilder currentText = new StringBuilder();
        String currentSpeaker = null;
        boolean insideSpeakerTag = false;
        
        for (String line : lines) {
            line = line.trim();
            
            // Skip empty lines, WEBVTT header, and ID lines (uuid/xxx-x)
            if (line.isEmpty() || line.equals("WEBVTT") || line.matches("^[a-f0-9-]+/\\d+-\\d+$")) {
                continue;
            }
            
            // Check if line is a timestamp
            Matcher timestampMatcher = TIMESTAMP_PATTERN.matcher(line);
            if (timestampMatcher.find()) {
                // Save previous entry if exists
                if (currentTimestamp != null && currentSpeaker != null && currentText.length() > 0) {
                    entries.add(new VTTEntry(currentTimestamp, currentSpeaker, currentText.toString().trim()));
                }
                
                // Start new entry
                currentTimestamp = timestampMatcher.group(1); // Use start time only
                currentText = new StringBuilder();
                currentSpeaker = null;
                insideSpeakerTag = false;
                continue;
            }
            
            // Check if line contains speaker tag opening
            Matcher speakerOpenMatcher = SPEAKER_OPEN_PATTERN.matcher(line);
            if (speakerOpenMatcher.find()) {
                currentSpeaker = speakerOpenMatcher.group(1).trim();
                insideSpeakerTag = true;
                
                // Extract text after opening tag
                String textAfterTag = line.substring(speakerOpenMatcher.end());
                
                // Check if closing tag is on same line
                Matcher closeMatcher = SPEAKER_CLOSE_PATTERN.matcher(textAfterTag);
                if (closeMatcher.find()) {
                    // Extract text between tags
                    String text = textAfterTag.substring(0, closeMatcher.start()).trim();
                    if (!text.isEmpty()) {
                        if (currentText.length() > 0) {
                            currentText.append(" ");
                        }
                        currentText.append(text);
                    }
                    insideSpeakerTag = false;
                } else {
                    // No closing tag on this line, text continues
                    if (!textAfterTag.trim().isEmpty()) {
                        if (currentText.length() > 0) {
                            currentText.append(" ");
                        }
                        currentText.append(textAfterTag.trim());
                    }
                }
                continue;
            }
            
            // Check if we're inside a speaker tag
            if (insideSpeakerTag) {
                // Check if this line has the closing tag
                Matcher closeMatcher = SPEAKER_CLOSE_PATTERN.matcher(line);
                if (closeMatcher.find()) {
                    // Extract text before closing tag
                    String text = line.substring(0, closeMatcher.start()).trim();
                    if (!text.isEmpty()) {
                        if (currentText.length() > 0) {
                            currentText.append(" ");
                        }
                        currentText.append(text);
                    }
                    insideSpeakerTag = false;
                } else {
                    // Continuation line
                    if (!line.isEmpty()) {
                        if (currentText.length() > 0) {
                            currentText.append(" ");
                        }
                        currentText.append(line);
                    }
                }
            }
        }
        
        // Add last entry if exists
        if (currentTimestamp != null && currentSpeaker != null && currentText.length() > 0) {
            entries.add(new VTTEntry(currentTimestamp, currentSpeaker, currentText.toString().trim()));
        }
        
        logger.debug("Parsed {} VTT entries", entries.size());
        return entries;
    }
    
    /**
     * Format VTT entries into clean text output.
     * 
     * @param entries List of parsed VTT entries
     * @param date Optional date header
     * @return Formatted text
     */
    private static String formatEntries(List<VTTEntry> entries, String date) {
        StringBuilder output = new StringBuilder();
        
        // Add date header if provided
        if (date != null && !date.trim().isEmpty()) {
            output.append("Date: ").append(date.trim()).append("\n\n");
        }
        
        // Group consecutive entries by same speaker
        String previousSpeaker = null;
        StringBuilder currentSpeakerText = new StringBuilder();
        String currentTimestamp = null;
        
        for (VTTEntry entry : entries) {
            String timestamp = formatTimestamp(entry.timestamp);
            
            if (entry.speaker.equals(previousSpeaker)) {
                // Same speaker continues - append text
                currentSpeakerText.append(" ").append(entry.text);
            } else {
                // New speaker - output previous speaker's text if exists
                if (previousSpeaker != null && currentSpeakerText.length() > 0) {
                    output.append("[").append(currentTimestamp).append("] ")
                          .append(previousSpeaker).append(": ")
                          .append(currentSpeakerText.toString()).append("\n");
                }
                
                // Start new speaker
                previousSpeaker = entry.speaker;
                currentTimestamp = timestamp;
                currentSpeakerText = new StringBuilder(entry.text);
            }
        }
        
        // Output last speaker's text
        if (previousSpeaker != null && currentSpeakerText.length() > 0) {
            output.append("[").append(currentTimestamp).append("] ")
                  .append(previousSpeaker).append(": ")
                  .append(currentSpeakerText.toString()).append("\n");
        }
        
        return output.toString();
    }
    
    /**
     * Format timestamp from HH:MM:SS.mmm to HH:MM:SS.
     * 
     * @param timestamp Full timestamp with milliseconds
     * @return Simplified timestamp (HH:MM:SS)
     */
    private static String formatTimestamp(String timestamp) {
        // Remove milliseconds: 00:00:17.013 -> 00:00:17
        if (timestamp.contains(".")) {
            return timestamp.substring(0, timestamp.lastIndexOf("."));
        }
        return timestamp;
    }
    
    /**
     * Check if content appears to be in VTT format.
     * 
     * @param content Content to check
     * @return true if content starts with WEBVTT header or contains VTT patterns
     */
    public static boolean isVTTFormat(String content) {
        if (content == null || content.trim().isEmpty()) {
            return false;
        }
        
        String trimmed = content.trim();
        
        // Check for WEBVTT header
        if (trimmed.startsWith("WEBVTT")) {
            return true;
        }
        
        // Check for VTT timestamp pattern
        Matcher timestampMatcher = TIMESTAMP_PATTERN.matcher(content);
        if (timestampMatcher.find()) {
            return true;
        }
        
        // Check for speaker tag pattern
        Matcher speakerMatcher = SPEAKER_OPEN_PATTERN.matcher(content);
        if (speakerMatcher.find()) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Internal class to represent a VTT entry.
     */
    private static class VTTEntry {
        final String timestamp;
        final String speaker;
        final String text;
        
        VTTEntry(String timestamp, String speaker, String text) {
            this.timestamp = timestamp;
            this.speaker = speaker;
            this.text = text;
        }
    }
}

