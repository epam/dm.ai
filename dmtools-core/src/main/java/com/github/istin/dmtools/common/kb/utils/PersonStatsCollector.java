package com.github.istin.dmtools.common.kb.utils;

import com.github.istin.dmtools.common.kb.KBStructureBuilder;
import com.github.istin.dmtools.common.kb.model.PersonContributions;
import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Utility for collecting person statistics and contributions from KB files
 */
public class PersonStatsCollector {
    
    private static final Logger logger = LogManager.getLogger(PersonStatsCollector.class);
    
    private final KBFileParser parser;
    private final KBStructureBuilder structureBuilder;
    
    public PersonStatsCollector(KBFileParser parser, KBStructureBuilder structureBuilder) {
        this.parser = parser;
        this.structureBuilder = structureBuilder;
    }
    
    public KBFileParser getFileParser() {
        return parser;
    }

    public String extractAuthor(String content) {
        return parser.extractAuthor(content);
    }
    
    public List<String> extractTopics(String content) {
        return parser.extractTopics(content);
    }
    
    public String extractDate(String content) {
        return parser.extractDate(content);
    }
    
    /**
     * Simple person statistics holder
     */
    public static class PersonStats {
        public int questions = 0;
        public int answers = 0;
        public int notes = 0;
    }
    
    /**
     * Scan ALL Q/A/N files in KB and collect person stats
     */
    public Map<String, PersonStats> collectPersonStatsFromFiles(Path outputPath) throws IOException {
        Map<String, PersonStats> stats = new HashMap<>();
        
        // Scan questions directory
        stats = scanDirectory(outputPath.resolve("questions"), stats, (stat) -> stat.questions++);
        
        // Scan answers directory
        stats = scanDirectory(outputPath.resolve("answers"), stats, (stat) -> stat.answers++);
        
        // Scan notes directory
        stats = scanDirectory(outputPath.resolve("notes"), stats, (stat) -> stat.notes++);
        
        logger.info("Collected stats for {} people from all KB files", stats.size());
        for (Map.Entry<String, PersonStats> entry : stats.entrySet()) {
            logger.info("  {}: {} questions, {} answers, {} notes", 
                       entry.getKey(), 
                       entry.getValue().questions,
                       entry.getValue().answers,
                       entry.getValue().notes);
        }
        
        return stats;
    }
    
    /**
     * Scan a directory and update person stats
     */
    private Map<String, PersonStats> scanDirectory(Path directory, Map<String, PersonStats> stats, 
                                                    StatUpdater updater) throws IOException {
        if (!Files.exists(directory)) {
            return stats;
        }
        
        try (Stream<Path> files = Files.list(directory)) {
            files.filter(Files::isRegularFile)
                 .filter(p -> p.getFileName().toString().endsWith(".md"))
                 .forEach(file -> {
                     try {
                        String content = Files.readString(file);
                        String author = parser.extractAuthor(content);
                        if (author != null) {
                            // Normalize author name to match peopleFromCurrentAnalysis format
                            String normalizedAuthor = structureBuilder.normalizePersonName(author);
                            PersonStats stat = stats.computeIfAbsent(normalizedAuthor, k -> new PersonStats());
                            updater.update(stat);
                        }
                    } catch (IOException e) {
                        logger.warn("Failed to read file: {}", file, e);
                    }
                 });
        }
        
        return stats;
    }
    
    /**
     * Scan ALL Q/A/N files in KB and collect person contributions
     */
    public Map<String, PersonContributions> collectPersonContributionsFromFiles(Path outputPath) throws IOException {
        Map<String, PersonContributions> contributions = new HashMap<>();
        
        // Scan questions directory
        contributions = scanForContributions(outputPath.resolve("questions"), contributions, 
            (pc, id, topic, date) -> pc.getQuestions().add(new PersonContributions.ContributionItem(id, topic, date)));
        
        // Scan answers directory
        contributions = scanForContributions(outputPath.resolve("answers"), contributions,
            (pc, id, topic, date) -> pc.getAnswers().add(new PersonContributions.ContributionItem(id, topic, date)));
        
        // Scan notes directory
        contributions = scanForContributions(outputPath.resolve("notes"), contributions,
            (pc, id, topic, date) -> pc.getNotes().add(new PersonContributions.ContributionItem(id, topic, date)));
        
        // Calculate topic contributions
        for (PersonContributions pc : contributions.values()) {
            Map<String, Integer> topicCounts = new HashMap<>();
            
            // Count contributions per topic
            for (PersonContributions.ContributionItem item : pc.getQuestions()) {
                topicCounts.merge(item.getTopic(), 1, Integer::sum);
            }
            for (PersonContributions.ContributionItem item : pc.getAnswers()) {
                topicCounts.merge(item.getTopic(), 1, Integer::sum);
            }
            for (PersonContributions.ContributionItem item : pc.getNotes()) {
                topicCounts.merge(item.getTopic(), 1, Integer::sum);
            }
            
            // Convert to TopicContribution list
            List<PersonContributions.TopicContribution> topicList = topicCounts.entrySet().stream()
                    .map(e -> new PersonContributions.TopicContribution(e.getKey(), e.getValue()))
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
            pc.setTopics(topicList);
        }
        
        logger.info("Collected contributions for {} people from all KB files", contributions.size());
        for (Map.Entry<String, PersonContributions> entry : contributions.entrySet()) {
            PersonContributions pc = entry.getValue();
            logger.info("  {}: {} questions, {} answers, {} notes", 
                       entry.getKey(), 
                       pc.getQuestions().size(),
                       pc.getAnswers().size(),
                       pc.getNotes().size());
        }
        
        return contributions;
    }
    
    /**
     * Scan directory for contributions
     */
    private Map<String, PersonContributions> scanForContributions(Path directory, 
                                                                   Map<String, PersonContributions> contributions,
                                                                   ContributionAdder adder) throws IOException {
        if (!Files.exists(directory)) {
            return contributions;
        }
        
        try (Stream<Path> files = Files.list(directory)) {
            files.filter(Files::isRegularFile)
                 .filter(p -> p.getFileName().toString().endsWith(".md"))
                 .forEach(file -> {
                     try {
                         String content = Files.readString(file);
                         String author = parser.extractAuthor(content);
                         List<String> topics = parser.extractTopics(content);
                         String date = parser.extractDate(content);
                         String id = file.getFileName().toString().replace(".md", "");
                         
                        if (author != null && topics != null && !topics.isEmpty()) {
                            // CRITICAL: Normalize author name to match keys from current analysis
                            String normalizedAuthor = structureBuilder.normalizePersonName(author);
                            PersonContributions pc = contributions.computeIfAbsent(normalizedAuthor, k -> new PersonContributions());
                            // Add contribution for each topic
                            for (String topic : topics) {
                                String topicSlug = structureBuilder.slugify(topic);
                                adder.add(pc, id, topicSlug, date);
                            }
                        }
                     } catch (IOException e) {
                         logger.warn("Failed to read file: {}", file, e);
                     }
                 });
        }
        
        return contributions;
    }
    
    /**
     * Functional interface for updating stats
     */
    @FunctionalInterface
    interface StatUpdater {
        void update(PersonStats stat);
    }
    
    /**
     * Functional interface for adding contributions
     */
    @FunctionalInterface
    interface ContributionAdder {
        void add(PersonContributions pc, String id, String topic, String date);
    }
}

