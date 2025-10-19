package com.github.istin.dmtools.common.kb;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Generates statistics and reports for the Knowledge Base
 * This is a mechanical helper class with no AI calls
 */
public class KBStatistics {
    
    /**
     * Generate all statistics for the KB
     */
    public void generateStatistics(Path kbPath) throws IOException {
        Files.createDirectories(kbPath.resolve("stats"));
        
        generateActivityTimeline(kbPath);
        generateTopicOverview(kbPath);
        generateIndex(kbPath);
    }
    
    /**
     * Generate activity timeline
     */
    public void generateActivityTimeline(Path kbPath) throws IOException {
        StringBuilder content = new StringBuilder();
        content.append("# Activity Timeline\n\n");
        content.append("<!-- AUTO_GENERATED_START -->\n\n");
        
        // Scan all questions/answers/notes and group by date
        Map<String, Integer> activityByDate = new TreeMap<>(Collections.reverseOrder());
        
        // Scan questions, answers, and notes (flat structure)
        scanForDates(kbPath.resolve("questions"), activityByDate);
        scanForDates(kbPath.resolve("answers"), activityByDate);
        scanForDates(kbPath.resolve("notes"), activityByDate);
        
        content.append("## Recent Activity\n\n");
        activityByDate.forEach((date, count) -> {
            content.append("- **").append(date).append("**: ").append(count).append(" contributions\n");
        });
        
        content.append("\n<!-- AUTO_GENERATED_END -->\n");
        
        Files.writeString(kbPath.resolve("stats/activity_timeline.md"), content.toString());
    }
    
    /**
     * Generate topic overview with statistics
     */
    public void generateTopicOverview(Path kbPath) throws IOException {
        // Ensure stats directory exists
        Files.createDirectories(kbPath.resolve("stats"));
        
        StringBuilder content = new StringBuilder();
        content.append("# Topics Overview\n\n");
        content.append("<!-- AUTO_GENERATED_START -->\n\n");
        
        Path topicsDir = kbPath.resolve("topics");
        if (Files.exists(topicsDir)) {
            try (Stream<Path> topics = Files.list(topicsDir)) {
                List<TopicStats> stats = topics
                        .filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().endsWith(".md"))
                        .filter(p -> !p.getFileName().toString().endsWith("-desc.md"))
                        .map(this::collectTopicStats)
                        .filter(Objects::nonNull)
                        .sorted(Comparator.comparingInt(TopicStats::getTotalContributions).reversed())
                        .toList();
                
                content.append("| Topic | Questions | Answers | Notes | Total |\n");
                content.append("|-------|-----------|---------|-------|-------|\n");
                
                for (TopicStats stat : stats) {
                    content.append(String.format("| [[%s\\|%s]] | %d | %d | %d | %d |\n",
                            stat.getId(),
                            stat.getName(),
                            stat.getQuestions(),
                            stat.getAnswers(),
                            stat.getNotes(),
                            stat.getTotalContributions()));
                }
            }
        }
        
        content.append("\n<!-- AUTO_GENERATED_END -->\n");
        
        Files.writeString(kbPath.resolve("stats/topics_overview.md"), content.toString());
    }
    
    /**
     * Generate main INDEX.md file
     */
    public void generateIndex(Path kbPath) throws IOException {
        StringBuilder content = new StringBuilder();
        content.append("# Knowledge Base Index\n\n");
        content.append("Welcome to the indexed knowledge base.\n\n");
        
        content.append("## Quick Navigation\n\n");
        content.append("- [[stats/topics_overview|Topics Overview]]\n");
        content.append("- [[stats/activity_timeline|Activity Timeline]]\n");
        content.append("- [[people/people|People]]\n\n");
        
        content.append("## Top Topics\n\n");
        content.append("![[stats/topics_overview]]\n\n");
        
        content.append("## Statistics\n\n");
        content.append("<!-- AUTO_GENERATED_START -->\n");
        
        int totalQuestions = countFiles(kbPath, "questions");
        int totalAnswers = countFiles(kbPath, "answers");
        int totalNotes = countFiles(kbPath, "notes");
        int totalPeople = countDirectories(kbPath.resolve("people"));
        int totalTopics = countTopicFiles(kbPath.resolve("topics"));
        
        content.append("- **Total Topics**: ").append(totalTopics).append("\n");
        content.append("- **Total Questions**: ").append(totalQuestions).append("\n");
        content.append("- **Total Answers**: ").append(totalAnswers).append("\n");
        content.append("- **Total Notes**: ").append(totalNotes).append("\n");
        content.append("- **Total Contributors**: ").append(totalPeople).append("\n");
        
        content.append("<!-- AUTO_GENERATED_END -->\n");
        
        Files.writeString(kbPath.resolve("INDEX.md"), content.toString());
    }
    
    // Helper methods
    
    private void scanForDates(Path topicsDir, Map<String, Integer> activityByDate) {
        if (!Files.exists(topicsDir)) {
            return;
        }
        
        try (Stream<Path> topics = Files.walk(topicsDir)) {
            topics.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".md"))
                    .forEach(file -> {
                        try {
                            String content = Files.readString(file);
                            // Extract date from frontmatter (simple regex)
                            if (content.contains("date:")) {
                                String[] lines = content.split("\n");
                                for (String line : lines) {
                                    if (line.trim().startsWith("date:")) {
                                        String date = line.substring(line.indexOf(":") + 1).trim();
                                        // Remove quotes if present
                                        date = date.replace("\"", "");
                                        // Extract just the date part (YYYY-MM-DD)
                                        if (date.length() >= 10) {
                                            date = date.substring(0, 10);
                                            activityByDate.merge(date, 1, Integer::sum);
                                        }
                                        break;
                                    }
                                }
                            }
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
    }
    
    private TopicStats collectTopicStats(Path topicFile) {
        try {
            String filename = topicFile.getFileName().toString();
            String topicId = filename.substring(0, filename.lastIndexOf(".md"));
            String topicName = topicId.replace("-", " ");
            
            // Read topic file and count embedded Q/A/N
            String content = Files.readString(topicFile);
            
            int questions = countEmbeddedItems(content, "q_");
            int answers = countEmbeddedItems(content, "a_");
            int notes = countEmbeddedItems(content, "n_");
            
            return new TopicStats(topicId, topicName, questions, answers, notes);
        } catch (Exception e) {
            return null;
        }
    }
    
    private int countEmbeddedItems(String content, String prefix) {
        // Count embeds like ![[q_0001]], ![[a_0001]], ![[n_0001]]
        int count = 0;
        String pattern = "!\\[\\[" + prefix + "\\d+\\]\\]";
        Pattern p = Pattern.compile(pattern);
        Matcher m = p.matcher(content);
        while (m.find()) {
            count++;
        }
        return count;
    }
    
    private int countFiles(Path baseDir, String subdirName) {
        Path dir = baseDir.resolve(subdirName);
        if (!Files.exists(dir)) {
            return 0;
        }
        
        try (Stream<Path> files = Files.walk(dir)) {
            return (int) files.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".md"))
                    .count();
        } catch (IOException e) {
            return 0;
        }
    }
    
    private int countTopicFiles(Path topicsDir) {
        if (!Files.exists(topicsDir)) {
            return 0;
        }
        
        try (Stream<Path> files = Files.list(topicsDir)) {
            return (int) files.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".md"))
                    .filter(p -> !p.getFileName().toString().endsWith("-desc.md"))
                    .count();
        } catch (IOException e) {
            return 0;
        }
    }
    
    private int countDirectories(Path dir) {
        if (!Files.exists(dir)) {
            return 0;
        }
        
        try (Stream<Path> dirs = Files.list(dir)) {
            return (int) dirs.filter(Files::isDirectory).count();
        } catch (IOException e) {
            return 0;
        }
    }
    
    // Inner class for topic statistics
    private static class TopicStats {
        private final String id;
        private final String name;
        private final int questions;
        private final int answers;
        private final int notes;
        
        public TopicStats(String id, String name, int questions, int answers, int notes) {
            this.id = id;
            this.name = name;
            this.questions = questions;
            this.answers = answers;
            this.notes = notes;
        }
        
        public String getId() { return id; }
        public String getName() { return name; }
        public int getQuestions() { return questions; }
        public int getAnswers() { return answers; }
        public int getNotes() { return notes; }
        public int getTotalContributions() { return questions + answers + notes; }
    }
}

