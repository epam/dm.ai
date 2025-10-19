package com.github.istin.dmtools.common.kb.utils;

import com.github.istin.dmtools.common.kb.model.KBContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Utility for loading and managing KB context
 * Handles directory initialization, max ID calculation, and context loading
 */
public class KBContextLoader {
    
    private static final Logger logger = LogManager.getLogger(KBContextLoader.class);
    
    private final KBFileParser fileParser;
    
    public KBContextLoader(KBFileParser fileParser) {
        this.fileParser = fileParser;
    }
    
    /**
     * Load existing KB context (people, topics, questions)
     */
    public KBContext loadKBContext(Path outputPath) throws IOException {
        KBContext context = new KBContext();
        
        // Load existing people (sorted for stable order)
        Path peopleDir = outputPath.resolve("people");
        if (Files.exists(peopleDir)) {
            try (Stream<Path> people = Files.list(peopleDir)) {
                people.filter(Files::isDirectory)
                        .sorted()
                        .forEach(p -> context.getExistingPeople().add(p.getFileName().toString()));
            }
        }
        
        // Load existing topics (sorted for stable order)
        Path topicsDir = outputPath.resolve("topics");
        if (Files.exists(topicsDir)) {
            try (Stream<Path> topics = Files.list(topicsDir)) {
                topics.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().endsWith(".md"))
                        .filter(p -> !p.getFileName().toString().endsWith("-desc.md"))
                        .sorted()
                        .forEach(p -> {
                            String filename = p.getFileName().toString();
                            String topicId = filename.substring(0, filename.lastIndexOf(".md"));
                            context.getExistingTopics().add(topicId);
                        });
            }
        }
        
        // Load existing questions for Q&A mapping
        Path questionsDir = outputPath.resolve("questions");
        if (Files.exists(questionsDir)) {
            try (Stream<Path> questions = Files.list(questionsDir)) {
                questions.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().endsWith(".md"))
                        .forEach(p -> {
                            try {
                                String content = Files.readString(p);
                                String id = p.getFileName().toString().replace(".md", "");
                                String author = fileParser.extractAuthor(content);
                                String area = fileParser.extractArea(content);
                                boolean answered = content.contains("answered: true");
                                String text = fileParser.extractQuestionText(content);
                                
                                if (author != null && text != null) {
                                    context.getExistingQuestions().add(
                                        new KBContext.QuestionSummary(id, author, text, area, answered)
                                    );
                                }
                            } catch (IOException e) {
                                logger.warn("Failed to read question file: {}", p, e);
                            }
                        });
            }
        }
        
        logger.info("Loaded KB context: {} people, {} topics, {} questions", 
                   context.getExistingPeople().size(),
                   context.getExistingTopics().size(),
                   context.getExistingQuestions().size());
        
        // Find max IDs
        context.setMaxQuestionId(findMaxId(outputPath, "q_", "questions"));
        context.setMaxAnswerId(findMaxId(outputPath, "a_", "answers"));
        context.setMaxNoteId(findMaxId(outputPath, "n_", "notes"));
        
        return context;
    }
    
    /**
     * Find maximum ID for a given prefix in a directory
     */
    public int findMaxId(Path outputPath, String prefix, String dirName) throws IOException {
        int maxId = 0;
        Path dir = outputPath.resolve(dirName);
        
        if (!Files.exists(dir)) {
            return maxId;
        }
        
        try (Stream<Path> files = Files.list(dir)) {
            maxId = files
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().startsWith(prefix))
                    .filter(p -> p.getFileName().toString().endsWith(".md"))
                    .mapToInt(p -> {
                        String filename = p.getFileName().toString();
                        String idPart = filename.substring(prefix.length(), filename.lastIndexOf("."));
                        try {
                            return Integer.parseInt(idPart);
                        } catch (NumberFormatException e) {
                            return 0;
                        }
                    })
                    .max()
                    .orElse(0);
        }
        
        return maxId;
    }
    
    /**
     * Extract topic title from frontmatter content
     */
    public String extractTopicTitle(String content, String defaultTitle) {
        try {
            int titleIndex = content.indexOf("title:");
            if (titleIndex != -1) {
                int lineEnd = content.indexOf('\n', titleIndex);
                if (lineEnd != -1) {
                    String titleLine = content.substring(titleIndex + 6, lineEnd).trim();
                    return titleLine.replace("\"", "").trim();
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to extract topic title, using ID: {}", defaultTitle);
        }
        return defaultTitle;
    }
    
    /**
     * Initialize output directories
     */
    public void initializeOutputDirectories(Path outputPath, boolean cleanExisting) throws IOException {
        Files.createDirectories(outputPath);

        Path topicsDir = outputPath.resolve("topics");
        Path peopleDir = outputPath.resolve("people");
        Path statsDir = outputPath.resolve("stats");
        Path inboxDir = outputPath.resolve("inbox");
        Path inboxRawDir = inboxDir.resolve("raw");
        Path inboxAnalyzedDir = inboxDir.resolve("analyzed");
        Path questionsDir = outputPath.resolve("questions");
        Path answersDir = outputPath.resolve("answers");
        Path notesDir = outputPath.resolve("notes");
        Path areasDir = outputPath.resolve("areas");

        Files.createDirectories(topicsDir);
        Files.createDirectories(peopleDir);
        Files.createDirectories(statsDir);
        Files.createDirectories(inboxDir);
        Files.createDirectories(inboxRawDir);
        Files.createDirectories(inboxAnalyzedDir);
        Files.createDirectories(questionsDir);
        Files.createDirectories(answersDir);
        Files.createDirectories(notesDir);
        Files.createDirectories(areasDir);

        if (cleanExisting) {
            clearDirectory(topicsDir);
            clearDirectory(peopleDir);
            clearDirectory(statsDir);
            clearDirectory(questionsDir);
            clearDirectory(answersDir);
            clearDirectory(notesDir);
            clearDirectory(areasDir);
            clearDirectory(inboxDir);

            Files.createDirectories(inboxRawDir);
            Files.createDirectories(inboxAnalyzedDir);
        }
    }
    
    /**
     * Clear all files in a directory without deleting the directory itself
     */
    public void clearDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        
        try (Stream<Path> files = Files.list(directory)) {
            files.forEach(path -> {
                try {
                    if (Files.isDirectory(path)) {
                        clearDirectory(path);
                        Files.delete(path);
                    } else {
                        Files.delete(path);
                    }
                } catch (IOException e) {
                    logger.warn("Failed to delete: {}", path, e);
                }
            });
        }
    }
}

