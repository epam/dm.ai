package com.github.istin.dmtools.common.kb.utils;

import com.github.istin.dmtools.common.kb.model.Answer;
import com.github.istin.dmtools.common.kb.model.Note;
import com.github.istin.dmtools.common.kb.model.Question;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility for parsing KB entities (Questions, Answers, Notes) from markdown files
 */
public class KBFileParser {
    
    private static final Logger logger = LogManager.getLogger(KBFileParser.class);
    
    private static final Pattern AUTHOR_PATTERN = Pattern.compile("author:\\s*\"?([^\"\\n]+)\"?");
    private static final Pattern DATE_PATTERN = Pattern.compile("date:\\s*\"?([^\"\\n]+)\"?");
    private static final Pattern AREA_PATTERN = Pattern.compile("area:\\s*\"?([^\"\\n]+)\"?");
    private static final Pattern QUALITY_PATTERN = Pattern.compile("quality:\\s*([\\d.]+)");
    private static final Pattern SOURCE_PATTERN = Pattern.compile("source:\\s*\"?([^\"\\n]+)\"?");
    private static final Pattern ANSWERED_BY_PATTERN = Pattern.compile("\\*\\*Answer:\\*\\* \\[\\[../../answers/(a_\\d+)\\|");
    private static final Pattern ANSWERS_QUESTION_PATTERN = Pattern.compile("\\*\\*Question:\\*\\* \\[\\[../../questions/(q_\\d+)\\|");
    
    /**
     * Parse a Question from a markdown file
     */
    public Question parseQuestionFromFile(Path file) {
        try {
            String content = Files.readString(file);
            Question question = new Question();
            
            // Extract ID from filename
            question.setId(file.getFileName().toString().replace(".md", ""));
            
            // Extract metadata
            question.setAuthor(extractAuthor(content));
            question.setArea(extractArea(content));
            question.setTopics(extractTopics(content));
            question.setTags(extractTags(content));
            question.setDate(extractDate(content));
            
            // Extract text
            question.setText(extractText(content));
            
            // Extract answeredBy link
            question.setAnsweredBy(extractAnsweredBy(content));
            
            return question;
        } catch (Exception e) {
            logger.warn("Failed to parse question file: {}", file, e);
            return null;
        }
    }
    
    /**
     * Parse an Answer from a markdown file
     */
    public Answer parseAnswerFromFile(Path file) {
        try {
            String content = Files.readString(file);
            Answer answer = new Answer();
            
            // Extract ID from filename
            answer.setId(file.getFileName().toString().replace(".md", ""));
            
            // Extract metadata
            answer.setAuthor(extractAuthor(content));
            answer.setArea(extractArea(content));
            answer.setTopics(extractTopics(content));
            answer.setTags(extractTags(content));
            answer.setDate(extractDate(content));
            answer.setQuality(extractQuality(content));
            
            // Extract text
            answer.setText(extractText(content));
            
            // Extract answersQuestion link
            answer.setAnswersQuestion(extractAnswersQuestion(content));
            
            return answer;
        } catch (Exception e) {
            logger.warn("Failed to parse answer file: {}", file, e);
            return null;
        }
    }
    
    /**
     * Parse a Note from a markdown file
     */
    public Note parseNoteFromFile(Path file) {
        try {
            String content = Files.readString(file);
            Note note = new Note();
            
            // Extract ID from filename
            note.setId(file.getFileName().toString().replace(".md", ""));
            
            // Extract metadata
            note.setAuthor(extractAuthor(content));
            note.setArea(extractArea(content));
            note.setTopics(extractTopics(content));
            note.setTags(extractTags(content));
            note.setDate(extractDate(content));
            
            // Extract text
            note.setText(extractText(content));
            
            return note;
        } catch (Exception e) {
            logger.warn("Failed to parse note file: {}", file, e);
            return null;
        }
    }
    
    /**
     * Extract author from frontmatter
     */
    public String extractAuthor(String content) {
        Matcher matcher = AUTHOR_PATTERN.matcher(content);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }
    
    /**
     * Extract date from frontmatter
     */
    public String extractDate(String content) {
        Matcher matcher = DATE_PATTERN.matcher(content);
        if (matcher.find()) {
            String date = matcher.group(1).trim();
            return date.length() >= 10 ? date.substring(0, 10) : date;
        }
        return "";
    }
    
    /**
     * Extract area from frontmatter
     */
    public String extractArea(String content) {
        Matcher matcher = AREA_PATTERN.matcher(content);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }
    
    /**
     * Extract source from frontmatter
     */
    public String extractSource(String content) {
        Matcher matcher = SOURCE_PATTERN.matcher(content);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }
    
    /**
     * Extract quality score from frontmatter (for answers)
     */
    public double extractQuality(String content) {
        Matcher matcher = QUALITY_PATTERN.matcher(content);
        if (matcher.find()) {
            try {
                return Double.parseDouble(matcher.group(1));
            } catch (NumberFormatException e) {
                return 0.0;
            }
        }
        return 0.0;
    }
    
    /**
     * Extract topics from frontmatter
     */
    public List<String> extractTopics(String content) {
        List<String> topics = new ArrayList<>();
        int topicsIndex = content.indexOf("topics:");
        if (topicsIndex != -1) {
            int lineEnd = content.indexOf('\n', topicsIndex);
            if (lineEnd != -1) {
                String topicsLine = content.substring(topicsIndex + 7, lineEnd).trim();
                // Parse YAML array: ["topic1", "topic2"] or [topic1, topic2]
                topicsLine = topicsLine.replace("[", "").replace("]", "").replace("\"", "");
                if (!topicsLine.isEmpty()) {
                    String[] topicArray = topicsLine.split(",");
                    for (String topic : topicArray) {
                        String trimmed = topic.trim();
                        if (!trimmed.isEmpty()) {
                            topics.add(trimmed);
                        }
                    }
                }
            }
        }
        return topics;
    }
    
    /**
     * Extract tags from frontmatter
     */
    public List<String> extractTags(String content) {
        List<String> tags = new ArrayList<>();
        String[] lines = content.split("\n");
        boolean inFrontmatter = false;
        boolean inTags = false;
        
        for (String line : lines) {
            if (line.trim().equals("---")) {
                if (!inFrontmatter) {
                    inFrontmatter = true;
                } else {
                    break; // End of frontmatter
                }
            } else if (inFrontmatter) {
                if (line.trim().startsWith("tags:")) {
                    inTags = true;
                } else if (inTags) {
                    if (line.trim().startsWith("-")) {
                        String tag = line.trim().substring(1).trim();
                        if (tag.startsWith("#")) tag = tag.substring(1);
                        tags.add(tag);
                    } else if (!line.trim().isEmpty() && !line.startsWith(" ")) {
                        inTags = false;
                    }
                }
            }
        }
        
        return tags;
    }
    
    /**
     * Extract text content (after frontmatter)
     */
    public String extractText(String content) {
        String[] parts = content.split("---", 3);
        if (parts.length >= 3) {
            String text = parts[2].trim();
            // Remove the first line if it's a title
            if (text.startsWith("#")) {
                int firstNewline = text.indexOf("\n");
                if (firstNewline > 0) {
                    text = text.substring(firstNewline + 1).trim();
                }
            }
            // Remove the "**Question:**" or "**Answer:**" or "**Note:**" line
            text = text.replaceFirst("^\\*\\*Question:\\*\\*.*\\n?", "");
            text = text.replaceFirst("^\\*\\*Answer:\\*\\*.*\\n?", "");
            text = text.replaceFirst("^\\*\\*Note:\\*\\*.*\\n?", "");
            return text.trim();
        }
        return "";
    }
    
    /**
     * Extract answeredBy link from content
     */
    public String extractAnsweredBy(String content) {
        Matcher matcher = ANSWERED_BY_PATTERN.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }
    
    /**
     * Extract answersQuestion link from content
     */
    public String extractAnswersQuestion(String content) {
        Matcher matcher = ANSWERS_QUESTION_PATTERN.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }
    
    /**
     * Extract question text from question file content
     */
    public String extractQuestionText(String content) {
        int questionHeaderIndex = content.indexOf("# Question:");
        if (questionHeaderIndex == -1) {
            return null;
        }
        
        int nextLineIndex = content.indexOf('\n', questionHeaderIndex);
        if (nextLineIndex == -1) {
            return null;
        }
        
        // Skip the empty line after header
        nextLineIndex = content.indexOf('\n', nextLineIndex + 1);
        if (nextLineIndex == -1) {
            return null;
        }
        
        // Find the end of question text (before "**Asked by:**" or next ##)
        int endIndex = content.indexOf("**Asked by:**", nextLineIndex);
        if (endIndex == -1) {
            endIndex = content.indexOf("\n##", nextLineIndex);
        }
        if (endIndex == -1) {
            return null;
        }
        
        String text = content.substring(nextLineIndex + 1, endIndex).trim();
        return text.isEmpty() ? null : text;
    }
}

