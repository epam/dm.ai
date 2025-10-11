package com.github.istin.dmtools.common.kb;

import com.github.istin.dmtools.common.kb.model.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;

/**
 * Builds Obsidian-compatible Markdown structure
 * This is a mechanical helper class with no AI calls
 */
public class KBStructureBuilder {
    
    private static final Logger logger = LogManager.getLogger(KBStructureBuilder.class);
    
    /**
     * Build topic structure from analysis result
     */
    /**
     * Build area structure (top-level knowledge domains)
     * NEW: Creates areas/ directory structure with topic links
     */
    public void buildAreaStructure(AnalysisResult analysis, Path outputPath, String sourceName) throws IOException {
        // Extract unique areas from all entities and collect topics per area
        Map<String, Set<String>> areaContributors = new HashMap<>();
        Map<String, Set<String>> areaTopics = new HashMap<>();
        
        if (analysis.getQuestions() != null) {
            analysis.getQuestions().forEach(q -> {
                if (q.getArea() != null && !q.getArea().isEmpty()) {
                    // Track contributors per area
                    areaContributors.computeIfAbsent(q.getArea(), k -> new HashSet<>()).add(q.getAuthor());
                    // Track topics per area
                    if (q.getTopics() != null) {
                        areaTopics.computeIfAbsent(q.getArea(), k -> new HashSet<>()).addAll(q.getTopics());
                    }
                }
            });
        }
        if (analysis.getAnswers() != null) {
            analysis.getAnswers().forEach(a -> {
                if (a.getArea() != null && !a.getArea().isEmpty()) {
                    // Track contributors per area
                    areaContributors.computeIfAbsent(a.getArea(), k -> new HashSet<>()).add(a.getAuthor());
                    // Track topics per area
                    if (a.getTopics() != null) {
                        areaTopics.computeIfAbsent(a.getArea(), k -> new HashSet<>()).addAll(a.getTopics());
                    }
                }
            });
        }
        if (analysis.getNotes() != null) {
            analysis.getNotes().forEach(n -> {
                if (n.getArea() != null && !n.getArea().isEmpty()) {
                    // Track contributors per area
                    if (n.getAuthor() != null) {
                        areaContributors.computeIfAbsent(n.getArea(), k -> new HashSet<>()).add(n.getAuthor());
                    }
                    // Track topics per area
                    if (n.getTopics() != null) {
                        areaTopics.computeIfAbsent(n.getArea(), k -> new HashSet<>()).addAll(n.getTopics());
                    }
                }
            });
        }
        
        // Create directory and files for each area
        for (String area : areaContributors.keySet()) {
            String areaId = slugify(area);
            Path areaDir = outputPath.resolve("areas").resolve(areaId);
            Files.createDirectories(areaDir);
            
            // Get contributors and topics for this area
            List<String> contributors = new ArrayList<>(areaContributors.getOrDefault(area, Collections.emptySet()));
            List<String> topics = new ArrayList<>(areaTopics.getOrDefault(area, Collections.emptySet()));
            
            // Create main area file (always recreate to ensure all topics/contributors are included)
            Path areaFile = areaDir.resolve(areaId + ".md");
            Path areaDescFile = areaDir.resolve(areaId + "-desc.md");
            
            createAreaFileWithTopics(areaFile, areaDescFile, area, areaId, sourceName, contributors, topics);
        }
    }
    
    /**
     * Build person profile files (simple version without details)
     */
    public void buildPersonProfile(String personName, Path outputPath, String sourceName, 
                                     int questionsCount, int answersCount, int notesCount) throws IOException {
        buildPersonProfile(personName, outputPath, sourceName, questionsCount, answersCount, notesCount, null);
    }
    
    /**
     * Build person profile files with detailed contributions
     */
    public void buildPersonProfile(String personName, Path outputPath, String sourceName, 
                                     int questionsCount, int answersCount, int notesCount,
                                     PersonContributions contributions) throws IOException {
        String personId = normalizePersonName(personName);
        Path personDir = outputPath.resolve("people").resolve(personId);
        Files.createDirectories(personDir);
        
        Path personFile = personDir.resolve(personId + ".md");
        
        if (!Files.exists(personFile)) {
            createPersonFile(personFile, personName, personId, sourceName, questionsCount, answersCount, notesCount, contributions);
        } else {
            updatePersonFile(personFile, questionsCount, answersCount, notesCount, contributions);
        }
    }
    
    /**
     * Build theme file
     */
    public void buildThemeFile(Theme theme, Path outputPath, String sourceName) throws IOException {
        if (theme.getTopics() == null || theme.getTopics().isEmpty()) {
            return; // Skip themes without topics
        }
        
        String themeId = slugify(theme.getTitle());
        
        for (String topic : theme.getTopics()) {
            String topicId = slugify(topic);
            Path topicDir = outputPath.resolve("topics").resolve(topicId);
            
            // Only create themes directory if we have actual theme content
            Path themeDir = topicDir.resolve("themes");
            Files.createDirectories(themeDir);
            
            Path themeFile = themeDir.resolve(themeId + ".md");
            createThemeFile(themeFile, theme, themeId, sourceName);
        }
    }
    
    /**
     * Build question file
     */
    /**
     * Build question file (with all answers that reference it)
     */
    public void buildQuestionFile(Question question, Path outputPath, String sourceName, AnalysisResult analysisResult) throws IOException {
        if (question.getArea() == null || question.getArea().isEmpty()) {
            return; // Skip questions without area
        }
        
        // Find all answers that reference this question
        List<String> answerIds = new ArrayList<>();
        if (analysisResult != null && analysisResult.getAnswers() != null) {
            for (Answer answer : analysisResult.getAnswers()) {
                if (answer.getAnswersQuestion() != null && answer.getAnswersQuestion().equals(question.getId())) {
                    answerIds.add(answer.getId());
                }
            }
        }
        
        // NEW: Save in flat questions/ folder
        Path questionDir = outputPath.resolve("questions");
        Files.createDirectories(questionDir);
        
        Path questionFile = questionDir.resolve(question.getId() + ".md");
        createQuestionFile(questionFile, question, sourceName, answerIds);
    }
    
    /**
     * Build question file (backward compatibility - without finding related answers)
     */
    public void buildQuestionFile(Question question, Path outputPath, String sourceName) throws IOException {
        buildQuestionFile(question, outputPath, sourceName, null);
    }
    
    /**
     * Build answer file
     */
    public void buildAnswerFile(Answer answer, Path outputPath, String sourceName) throws IOException {
        if (answer.getArea() == null || answer.getArea().isEmpty()) {
            return; // Skip answers without area
        }
        
        // NEW: Save in flat answers/ folder
        Path answerDir = outputPath.resolve("answers");
        Files.createDirectories(answerDir);
        
        Path answerFile = answerDir.resolve(answer.getId() + ".md");
        createAnswerFile(answerFile, answer, sourceName);
    }
    
    /**
     * Build note file
     */
    public void buildNoteFile(Note note, Path outputPath, String sourceName) throws IOException {
        if (note.getArea() == null || note.getArea().isEmpty()) {
            return; // Skip notes without area
        }
        
        // NEW: Save in flat notes/ folder
        Path noteDir = outputPath.resolve("notes");
        Files.createDirectories(noteDir);
        
        Path noteFile = noteDir.resolve(note.getId() + ".md");
        createNoteFile(noteFile, note, sourceName);
    }
    
    // Helper methods
    
    private void createTopicFile(Path file, String title, String id, String source, List<String> contributors) throws IOException {
        Map<String, Object> frontmatter = new LinkedHashMap<>();
        frontmatter.put("id", id);
        frontmatter.put("title", title);
        frontmatter.put("type", "topic");
        frontmatter.put("source", source);
        frontmatter.put("tags", Arrays.asList("#" + source, "#" + id));
        
        String content = "---\n" + toYaml(frontmatter) + "---\n\n"
                + "# " + title + "\n\n"
                + "<!-- AUTO_GENERATED_START -->\n\n";
        
        // Recent Activity section (will be updated later with actual counts)
        content += "## Recent Activity\n"
                + "- Questions: 0\n"
                + "- Answers: 0\n"
                + "- Notes: 0\n"
                + "- Themes: 0\n\n";
        
        // Themes section (empty initially, will be filled when themes are added)
        // No themes initially
        
        // Key Contributors section
        if (!contributors.isEmpty()) {
            content += "## Key Contributors\n\n";
            for (String contributor : contributors) {
                String slug = normalizePersonName(contributor);
                content += "- [[" + slug + "|" + contributor + "]]\n";
            }
            content += "\n";
        }
        
        content += "<!-- AUTO_GENERATED_END -->\n\n";
        
        // Add embed to description file (outside AUTO_GENERATED)
        content += "## Description\n\n![[" + id + "-desc]]\n";
        
        Files.writeString(file, content);
        
        // Create description file
        Path descFile = file.getParent().resolve(id + "-desc.md");
        if (!Files.exists(descFile)) {
            String descContent = "<!-- AI_CONTENT_START -->\n"
                    + "Overview will be generated by AI.\n"
                    + "<!-- AI_CONTENT_END -->\n";
            Files.writeString(descFile, descContent);
        }
    }
    
    private void updateTopicFileContributors(Path file, List<String> newContributors) throws IOException {
        if (newContributors.isEmpty()) {
            return;
        }
        
        String content = Files.readString(file);
        
        // Extract existing contributors
        Set<String> allContributors = new HashSet<>(newContributors);
        if (content.contains("## Key Contributors")) {
            String pattern = "## Key Contributors\\s+<!-- AUTO_GENERATED_START -->\\s+(.*?)\\s+<!-- AUTO_GENERATED_END -->";
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern, java.util.regex.Pattern.DOTALL);
            java.util.regex.Matcher m = p.matcher(content);
            if (m.find()) {
                String existingSection = m.group(1);
                for (String line : existingSection.split("\n")) {
                    if (line.contains("[[")) {
                        int start = line.indexOf("[[") + 2;
                        int end = line.indexOf("|", start);
                        if (end == -1) end = line.indexOf("]]", start);
                        if (end > start) {
                            String name = line.substring(start, end).replace("_", " ");
                            allContributors.add(name);
                        }
                    }
                }
            }
        }
        
        // Build new contributors section
        StringBuilder contributorsSection = new StringBuilder("## Key Contributors\n\n"
                + "<!-- AUTO_GENERATED_START -->\n");
        for (String contributor : allContributors.stream().sorted().collect(java.util.stream.Collectors.toList())) {
            String slug = normalizePersonName(contributor);
            contributorsSection.append("- [[").append(slug).append("|").append(contributor).append("]]\n");
        }
        contributorsSection.append("<!-- AUTO_GENERATED_END -->\n");
        
        // Replace or append contributors section
        if (content.contains("## Key Contributors")) {
            String pattern = "## Key Contributors\\s+<!-- AUTO_GENERATED_START -->.*?<!-- AUTO_GENERATED_END -->";
            content = content.replaceAll(pattern, contributorsSection.toString().trim());
        } else {
            content += "\n" + contributorsSection;
        }
        
        Files.writeString(file, content);
    }
    
    /**
     * Update topic file with statistics about themes, questions, answers, and notes
     */
    public void updateTopicWithStats(Path outputPath, String topicId, int questionsCount, 
                                       int answersCount, int notesCount, int themesCount,
                                       List<String> themeIds, List<String> contributors) throws IOException {
        Path topicFile = outputPath.resolve("topics").resolve(topicId).resolve(topicId + ".md");
        if (!Files.exists(topicFile)) {
            return;
        }
        
        String content = Files.readString(topicFile);
        
        // Build new AUTO_GENERATED section
        StringBuilder autoGenSection = new StringBuilder("<!-- AUTO_GENERATED_START -->\n\n");
        
        // Recent Activity
        autoGenSection.append("## Recent Activity\n");
        autoGenSection.append("- Questions: ").append(questionsCount).append("\n");
        autoGenSection.append("- Answers: ").append(answersCount).append("\n");
        autoGenSection.append("- Notes: ").append(notesCount).append("\n");
        autoGenSection.append("- Themes: ").append(themesCount).append("\n\n");
        
        // Themes
        if (!themeIds.isEmpty()) {
            autoGenSection.append("## Themes\n\n");
            for (String themeId : themeIds) {
                // Convert theme ID to title (capitalize words)
                String themeTitle = Arrays.stream(themeId.split("-"))
                        .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1))
                        .collect(java.util.stream.Collectors.joining(" "));
                autoGenSection.append("- [[themes/").append(themeId).append("|").append(themeTitle).append("]]\n");
            }
            autoGenSection.append("\n");
        }
        
        // Key Contributors
        if (!contributors.isEmpty()) {
            autoGenSection.append("## Key Contributors\n\n");
            for (String contributor : contributors.stream().sorted().collect(java.util.stream.Collectors.toList())) {
                String slug = normalizePersonName(contributor);
                autoGenSection.append("- [[").append(slug).append("|").append(contributor).append("]]\n");
            }
            autoGenSection.append("\n");
        }
        
        autoGenSection.append("<!-- AUTO_GENERATED_END -->");
        
        // Replace AUTO_GENERATED section
        String pattern = "<!-- AUTO_GENERATED_START -->.*?<!-- AUTO_GENERATED_END -->";
        content = content.replaceAll("(?s)" + pattern, autoGenSection.toString());
        
        Files.writeString(topicFile, content);
    }
    
    private void createPersonFile(Path file, String name, String id, String source, 
                                   int questions, int answers, int notes) throws IOException {
        createPersonFile(file, name, id, source, questions, answers, notes, null);
    }
    
    private void createPersonFile(Path file, String name, String id, String source, 
                                   int questions, int answers, int notes,
                                   PersonContributions contributions) throws IOException {
        Map<String, Object> frontmatter = new LinkedHashMap<>();
        frontmatter.put("id", id);
        frontmatter.put("name", name);
        frontmatter.put("type", "person");
        frontmatter.put("source", source);
        frontmatter.put("questionsAsked", questions);
        frontmatter.put("answersProvided", answers);
        frontmatter.put("notesContributed", notes);
        frontmatter.put("tags", Arrays.asList("#person", "#source_" + source));
        
        String content = "---\n" + toYaml(frontmatter) + "---\n\n"
                + "# " + name + "\n\n"
                + "![[" + id + "-desc]]\n\n";
        
        // Auto-generated section
        content += "<!-- AUTO_GENERATED_START -->\n\n";
        
        // If we have detailed contributions, use them
        if (contributions != null) {
            // Questions Asked section
            if (!contributions.getQuestions().isEmpty()) {
                content += "## Questions Asked\n\n";
                for (PersonContributions.ContributionItem q : contributions.getQuestions()) {
                    // NEW: Flat structure - questions are in ../../questions/
                    content += "- [[../../questions/" + q.getId() + "|" + q.getId() + "]] - " + q.getDate() + "\n";
                }
                content += "\n";
            }
            
            // Answers Provided section
            if (!contributions.getAnswers().isEmpty()) {
                content += "## Answers Provided\n\n";
                for (PersonContributions.ContributionItem a : contributions.getAnswers()) {
                    // NEW: Flat structure - answers are in ../../answers/
                    content += "- [[../../answers/" + a.getId() + "|" + a.getId() + "]] - " + a.getDate() + "\n";
                }
                content += "\n";
            }
            
            // Notes Contributed section
            if (!contributions.getNotes().isEmpty()) {
                content += "## Notes Contributed\n\n";
                for (PersonContributions.ContributionItem n : contributions.getNotes()) {
                    // NEW: Flat structure - notes are in ../../notes/
                    content += "- [[../../notes/" + n.getId() + "|" + n.getId() + "]] - " + n.getDate() + "\n";
                }
                content += "\n";
            }
            
            // Topics section
            if (!contributions.getTopics().isEmpty()) {
                content += "## Topics\n\n";
                for (PersonContributions.TopicContribution t : contributions.getTopics()) {
                    if (t.getCount() > 0) {
                        String plural = t.getCount() > 1 ? "s" : "";
                        // NEW: Flat structure - topics are in ../../topics/topic-id.md
                        content += "- [[../../topics/" + t.getTopicId() + "|" + t.getTopicId() + "]] - " + t.getCount() + " contribution" + plural + "\n";
                    }
                }
                content += "\n";
            }
        } else {
            // Fallback to simple counts
            content += "- Questions asked: " + questions + "\n";
            content += "- Answers provided: " + answers + "\n";
            content += "- Notes contributed: " + notes + "\n\n";
        }
        
        content += "<!-- AUTO_GENERATED_END -->\n";
        
        Files.writeString(file, content);
        
        // Create description file
        Path descFile = file.getParent().resolve(id + "-desc.md");
        if (!Files.exists(descFile)) {
            String descContent = "<!-- AI_CONTENT_START -->\n"
                    + "Profile will be generated by AI.\n"
                    + "<!-- AI_CONTENT_END -->\n";
            Files.writeString(descFile, descContent);
        }
    }
    
    /**
     * Update a single field in frontmatter
     */
    private String updateFrontmatterField(String content, String fieldName, String newValue) {
        // Pattern to match field in frontmatter
        String pattern = "(" + fieldName + ":\\s*)([^\\n]+)";
        return content.replaceFirst(pattern, "$1" + newValue);
    }
    
    private void updatePersonFile(Path file, int questions, int answers, int notes) throws IOException {
        updatePersonFile(file, questions, answers, notes, null);
    }
    
    private void updatePersonFile(Path file, int questions, int answers, int notes, PersonContributions contributions) throws IOException {
        String content = Files.readString(file);
        
        // Update frontmatter counts
        content = updateFrontmatterField(content, "questionsAsked", String.valueOf(questions));
        content = updateFrontmatterField(content, "answersProvided", String.valueOf(answers));
        content = updateFrontmatterField(content, "notesContributed", String.valueOf(notes));
        
        // Build the replacement content
        String replacement = "<!-- AUTO_GENERATED_START -->\n\n";
        
        // If we have detailed contributions, use them
        if (contributions != null) {
            // Questions Asked section
            if (!contributions.getQuestions().isEmpty()) {
                replacement += "## Questions Asked\n\n";
                for (PersonContributions.ContributionItem q : contributions.getQuestions()) {
                    // NEW: Flat structure - questions are in ../../questions/
                    replacement += "- [[../../questions/" + q.getId() + "|" + q.getId() + "]] - " + q.getDate() + "\n";
                }
                replacement += "\n";
            }
            
            // Answers Provided section
            if (!contributions.getAnswers().isEmpty()) {
                replacement += "## Answers Provided\n\n";
                for (PersonContributions.ContributionItem a : contributions.getAnswers()) {
                    // NEW: Flat structure - answers are in ../../answers/
                    replacement += "- [[../../answers/" + a.getId() + "|" + a.getId() + "]] - " + a.getDate() + "\n";
                }
                replacement += "\n";
            }
            
            // Notes Contributed section
            if (!contributions.getNotes().isEmpty()) {
                replacement += "## Notes Contributed\n\n";
                for (PersonContributions.ContributionItem n : contributions.getNotes()) {
                    // NEW: Flat structure - notes are in ../../notes/
                    replacement += "- [[../../notes/" + n.getId() + "|" + n.getId() + "]] - " + n.getDate() + "\n";
                }
                replacement += "\n";
            }
            
            // Topics section
            if (!contributions.getTopics().isEmpty()) {
                replacement += "## Topics\n\n";
                for (PersonContributions.TopicContribution t : contributions.getTopics()) {
                    if (t.getCount() > 0) {
                        String plural = t.getCount() > 1 ? "s" : "";
                        // NEW: Flat structure - topics are in ../../topics/topic-id.md
                        replacement += "- [[../../topics/" + t.getTopicId() + "|" + t.getTopicId() + "]] - " + t.getCount() + " contribution" + plural + "\n";
                    }
                }
                replacement += "\n";
            }
        } else {
            // Fallback to simple counts
            replacement += "- Questions asked: " + questions + "\n";
            replacement += "- Answers provided: " + answers + "\n";
            replacement += "- Notes contributed: " + notes + "\n\n";
        }
        
        replacement += "<!-- AUTO_GENERATED_END -->";
        
        // Update contribution section in AUTO_GENERATED
        // Use (?s) flag to make . match newlines
        String pattern = "(?s)<!-- AUTO_GENERATED_START -->.*?<!-- AUTO_GENERATED_END -->";
        content = content.replaceAll(pattern, replacement.replace("\\", "\\\\").replace("$", "\\$"));
        Files.writeString(file, content);
    }
    
    private void createThemeFile(Path file, Theme theme, String id, String source) throws IOException {
        Map<String, Object> frontmatter = new LinkedHashMap<>();
        frontmatter.put("id", id);
        frontmatter.put("title", theme.getTitle());
        frontmatter.put("type", "theme");
        frontmatter.put("source", source);
        frontmatter.put("topics", theme.getTopics());
        if (theme.getContributors() != null && !theme.getContributors().isEmpty()) {
            frontmatter.put("contributors", theme.getContributors());
        }
        frontmatter.put("tags", Arrays.asList("#theme", "#" + source));
        
        String content = "---\n" + toYaml(frontmatter) + "---\n\n"
                + "# " + theme.getTitle() + "\n\n";
        
        // Auto-generated section with Key Contributors
        content += "<!-- AUTO_GENERATED_START -->\n\n";
        
        if (theme.getContributors() != null && !theme.getContributors().isEmpty()) {
            content += "## Key Contributors\n\n";
            for (String contributor : theme.getContributors()) {
                String slug = normalizePersonName(contributor);
                content += "- [[" + slug + "|" + contributor + "]]\n";
            }
            content += "\n";
        }
        
        content += "<!-- AUTO_GENERATED_END -->\n\n";
        
        // Add embed to description file (like topics do)
        content += "## Description\n\n![[" + id + "-desc]]\n";
        
        Files.writeString(file, content);
        
        // Create description file
        Path descFile = file.getParent().resolve(id + "-desc.md");
        if (!Files.exists(descFile)) {
            String descContent = "<!-- AI_CONTENT_START -->\n"
                    + theme.getDescription() + "\n"
                    + "<!-- AI_CONTENT_END -->\n";
            Files.writeString(descFile, descContent);
        }
    }
    
    private void createQuestionFile(Path file, Question question, String source, List<String> answerIds) throws IOException {
        Map<String, Object> frontmatter = new LinkedHashMap<>();
        frontmatter.put("id", question.getId());
        frontmatter.put("type", "question");
        frontmatter.put("author", question.getAuthor());
        frontmatter.put("date", question.getDate());
        frontmatter.put("area", question.getArea());
        frontmatter.put("topics", question.getTopics());
        frontmatter.put("answered", question.getAnsweredBy() != null);
        if (question.getAnsweredBy() != null) {
            frontmatter.put("answeredBy", question.getAnsweredBy());
        }
        frontmatter.put("source", source);
        
        // Combine system tags with LLM-generated tags
        List<String> allTags = new ArrayList<>();
        allTags.add("#question");
        allTags.add("#source_" + source);
        if (question.getTags() != null && !question.getTags().isEmpty()) {
            allTags.addAll(question.getTags());
        }
        frontmatter.put("tags", allTags);
        
        String content = "---\n" + toYaml(frontmatter) + "---\n\n"
                + "# Question: " + question.getId() + "\n\n"
                + question.getText() + "\n\n"
                + "**Asked by:** [[" + normalizePersonName(question.getAuthor()) + "]]\n"
                + "**Date:** " + question.getDate() + "\n";
        
        // Add area reference
        if (question.getArea() != null && !question.getArea().isEmpty()) {
            String areaId = slugify(question.getArea());
            content += "**Area:** [[" + areaId + "|" + question.getArea() + "]]\n";
        }
        
        // Add topics references
        if (question.getTopics() != null && !question.getTopics().isEmpty()) {
            content += "**Topics:** ";
            for (int i = 0; i < question.getTopics().size(); i++) {
                String topic = question.getTopics().get(i);
                String topicId = slugify(topic);
                content += "[[" + topicId + "|" + topic + "]]";
                if (i < question.getTopics().size() - 1) {
                    content += ", ";
                }
            }
            content += "\n";
        }
        
        // Embed all answers that reference this question
        if (answerIds != null && !answerIds.isEmpty()) {
            content += "\n## Answers\n\n";
            for (String answerId : answerIds) {
                content += "![[" + answerId + "]]\n\n";
            }
        }
        
        Files.writeString(file, content);
    }
    
    private void createAnswerFile(Path file, Answer answer, String source) throws IOException {
        Map<String, Object> frontmatter = new LinkedHashMap<>();
        frontmatter.put("id", answer.getId());
        frontmatter.put("type", "answer");
        frontmatter.put("author", answer.getAuthor());
        frontmatter.put("date", answer.getDate());
        frontmatter.put("area", answer.getArea());
        frontmatter.put("topics", answer.getTopics());
        frontmatter.put("quality", answer.getQuality());
        if (answer.getAnswersQuestion() != null) {
            frontmatter.put("answersQuestion", answer.getAnswersQuestion());
        }
        frontmatter.put("source", source);
        
        // Combine system tags with LLM-generated tags
        List<String> allTags = new ArrayList<>();
        allTags.add("#answer");
        allTags.add("#source_" + source);
        if (answer.getTags() != null && !answer.getTags().isEmpty()) {
            allTags.addAll(answer.getTags());
        }
        frontmatter.put("tags", allTags);
        
        String content = "---\n" + toYaml(frontmatter) + "---\n\n"
                + "# Answer: " + answer.getId() + "\n\n"
                + answer.getText() + "\n\n"
                + "**Provided by:** [[" + normalizePersonName(answer.getAuthor()) + "]]\n"
                + "**Date:** " + answer.getDate() + "\n"
                + "**Quality Score:** " + String.format("%.2f", answer.getQuality()) + "\n";
        
        // Add area reference
        if (answer.getArea() != null && !answer.getArea().isEmpty()) {
            String areaId = slugify(answer.getArea());
            content += "**Area:** [[" + areaId + "|" + answer.getArea() + "]]\n";
        }
        
        // Add topics references
        if (answer.getTopics() != null && !answer.getTopics().isEmpty()) {
            content += "**Topics:** ";
            for (int i = 0; i < answer.getTopics().size(); i++) {
                String topic = answer.getTopics().get(i);
                String topicId = slugify(topic);
                content += "[[" + topicId + "|" + topic + "]]";
                if (i < answer.getTopics().size() - 1) {
                    content += ", ";
                }
            }
            content += "\n";
        }
        
        // Only show "Answers:" if there's an actual question reference
        if (answer.getAnswersQuestion() != null && !answer.getAnswersQuestion().isEmpty()) {
            content += "\n**Answers:** [[" + answer.getAnswersQuestion() + "]]\n";
        }
        
        Files.writeString(file, content);
    }
    
    private void createNoteFile(Path file, Note note, String source) throws IOException {
        Map<String, Object> frontmatter = new LinkedHashMap<>();
        frontmatter.put("id", note.getId());
        frontmatter.put("type", "note");
        frontmatter.put("author", note.getAuthor());
        frontmatter.put("date", note.getDate());
        frontmatter.put("area", note.getArea());
        frontmatter.put("topics", note.getTopics());
        frontmatter.put("source", source);
        
        // Combine system tags with LLM-generated tags
        List<String> allTags = new ArrayList<>();
        allTags.add("#note");
        allTags.add("#source_" + source);
        if (note.getTags() != null && !note.getTags().isEmpty()) {
            allTags.addAll(note.getTags());
        }
        frontmatter.put("tags", allTags);
        
        String content = "---\n" + toYaml(frontmatter) + "---\n\n"
                + "# Note: " + note.getId() + "\n\n"
                + note.getText() + "\n\n"
                + "**By:** [[" + normalizePersonName(note.getAuthor()) + "]]\n"
                + "**Date:** " + note.getDate() + "\n";
        
        // Add area reference
        if (note.getArea() != null && !note.getArea().isEmpty()) {
            String areaId = slugify(note.getArea());
            content += "**Area:** [[" + areaId + "|" + note.getArea() + "]]\n";
        }
        
        // Add topics references
        if (note.getTopics() != null && !note.getTopics().isEmpty()) {
            content += "**Topics:** ";
            for (int i = 0; i < note.getTopics().size(); i++) {
                String topic = note.getTopics().get(i);
                String topicId = slugify(topic);
                content += "[[" + topicId + "|" + topic + "]]";
                if (i < note.getTopics().size() - 1) {
                    content += ", ";
                }
            }
            content += "\n";
        }
        
        Files.writeString(file, content);
    }
    
    /**
     * Normalize person name to Obsidian format (spaces to underscores)
     */
    public String normalizePersonName(String name) {
        return name.replaceAll("\\s+", "_");
    }
    
    /**
     * Create slug from title (lowercase with hyphens)
     */
    public String slugify(String text) {
        return text.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
    }
    
    /**
     * Simple YAML serializer for frontmatter
     */
    private String toYaml(Map<String, Object> map) {
        StringBuilder yaml = new StringBuilder();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            yaml.append(entry.getKey()).append(": ");
            Object value = entry.getValue();
            if (value instanceof String) {
                yaml.append("\"").append(((String) value).replace("\"", "\\\"")).append("\"");
            } else if (value instanceof List) {
                yaml.append("[");
                List<?> list = (List<?>) value;
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) yaml.append(", ");
                    Object item = list.get(i);
                    if (item instanceof String) {
                        yaml.append("\"").append(((String) item).replace("\"", "\\\"")).append("\"");
                    } else {
                        yaml.append(item);
                    }
                }
                yaml.append("]");
            } else {
                yaml.append(value);
            }
            yaml.append("\n");
        }
        return yaml.toString();
    }
    
    // =========================================================================
    // NEW: Area-related methods
    // =========================================================================
    
    /**
     * Create area file with topics aggregation, contributors, and AI description
     */
    private void createAreaFileWithTopics(Path areaFile, Path areaDescFile, String title, String id, 
                                          String source, List<String> contributors, List<String> topics) throws IOException {
        Map<String, Object> frontmatter = new LinkedHashMap<>();
        frontmatter.put("type", "area");
        frontmatter.put("title", title);
        frontmatter.put("id", id);
        frontmatter.put("source", source);
        frontmatter.put("contributors", contributors);
        frontmatter.put("created", java.time.Instant.now().toString());
        
        StringBuilder content = new StringBuilder();
        content.append("---\n");
        content.append(toYaml(frontmatter));
        content.append("---\n\n");
        content.append("# ").append(title).append("\n\n");
        
        // AI description embed
        content.append("![[").append(id).append("-desc]]\n\n");
        
        // Topics (themes within this area)
        if (!topics.isEmpty()) {
            content.append("## Topics\n\n");
            for (String topic : topics) {
                String topicId = slugify(topic);
                content.append("- [[").append(topicId).append("|").append(topic).append("]]\n");
            }
            content.append("\n");
        }
        
        // Contributors
        content.append("## Key Contributors\n\n");
        for (String contributor : contributors) {
            content.append("- [[").append(normalizePersonName(contributor)).append("|").append(contributor).append("]]\n");
        }
        
        content.append("\n<!-- AUTO_GENERATED_START -->\n");
        content.append("## Statistics\n\n");
        content.append("*Statistics will be auto-generated here*\n\n");
        content.append("<!-- AUTO_GENERATED_END -->\n");
        
        Files.writeString(areaFile, content.toString());
        
        // Create desc file placeholder for AI (only if it doesn't exist)
        if (!Files.exists(areaDescFile)) {
            StringBuilder descContent = new StringBuilder();
            descContent.append("<!-- AI_CONTENT_START -->\n\n");
            descContent.append("Area description will be generated by AI.\n\n");
            descContent.append("<!-- AI_CONTENT_END -->\n");
            Files.writeString(areaDescFile, descContent.toString());
        }
    }
    
    /**
     * Update area file with new contributors
     */
    private void updateAreaFileContributors(Path areaFile, List<String> newContributors) throws IOException {
        // TODO: Implement merging logic for contributors
        // For now, just leave as is
    }
    
    /**
     * Build topic files (detailed themes within areas)
     * NEW: Creates topics/*.md with Q/A/N aggregation and contributors
     * IMPORTANT: For incremental updates, also collects Q/A/N from existing files
     */
    public void buildTopicFiles(AnalysisResult analysis, Path outputPath, String sourceName) throws IOException {
        // Collect data per topic
        Map<String, TopicData> topicDataMap = new HashMap<>();
        
        // FIRST: Collect from existing Q/A/N files (for incremental updates)
        collectFromExistingFiles(outputPath, topicDataMap);
        
        // THEN: Collect from current analysis (will add new items)
        // Collect from questions
        if (analysis.getQuestions() != null) {
            for (Question q : analysis.getQuestions()) {
                if (q.getTopics() != null) {
                    for (String topic : q.getTopics()) {
                        TopicData data = topicDataMap.computeIfAbsent(topic, k -> new TopicData());
                        if (q.getId() != null) data.questions.add(q.getId());
                        if (q.getAuthor() != null) data.contributors.add(q.getAuthor());
                        if (q.getTags() != null) data.tags.addAll(q.getTags());
                    }
                }
            }
        }
        
        // Collect from answers
        if (analysis.getAnswers() != null) {
            for (Answer a : analysis.getAnswers()) {
                if (a.getTopics() != null) {
                    for (String topic : a.getTopics()) {
                        TopicData data = topicDataMap.computeIfAbsent(topic, k -> new TopicData());
                        if (a.getId() != null) data.answers.add(a.getId());
                        if (a.getAuthor() != null) data.contributors.add(a.getAuthor());
                        if (a.getTags() != null) data.tags.addAll(a.getTags());
                    }
                }
            }
        }
        
        // Collect from notes
        if (analysis.getNotes() != null) {
            for (Note n : analysis.getNotes()) {
                if (n.getTopics() != null) {
                    for (String topic : n.getTopics()) {
                        TopicData data = topicDataMap.computeIfAbsent(topic, k -> new TopicData());
                        if (n.getId() != null) data.notes.add(n.getId());
                        if (n.getAuthor() != null) data.contributors.add(n.getAuthor());
                        if (n.getTags() != null) data.tags.addAll(n.getTags());
                    }
                }
            }
        }
        
        // Create topics directory
        Path topicsDir = outputPath.resolve("topics");
        Files.createDirectories(topicsDir);
        
        // Create file for each unique topic
        // Always recreate files to ensure all Q/A/N are included (important for incremental updates)
        for (Map.Entry<String, TopicData> entry : topicDataMap.entrySet()) {
            String topic = entry.getKey();
            String topicId = slugify(topic);
            Path topicFile = topicsDir.resolve(topicId + ".md");
            Path topicDescFile = topicsDir.resolve(topicId + "-desc.md");
            
            createTopicFileWithAggregation(topicFile, topicDescFile, topic, topicId, sourceName, entry.getValue(), analysis);
        }
    }
    
    /**
     * Collect Q/A/N from existing files (for incremental updates)
     */
    private void collectFromExistingFiles(Path outputPath, Map<String, TopicData> topicDataMap) throws IOException {
        logger.info("Collecting Q/A/N from existing files...");
        // Collect from questions
        Path questionsDir = outputPath.resolve("questions");
        if (Files.exists(questionsDir)) {
            try (Stream<Path> files = Files.list(questionsDir)) {
                files.filter(f -> f.toString().endsWith(".md"))
                    .forEach(file -> {
                        try {
                            collectTopicsFromFile(file, "question", topicDataMap);
                        } catch (IOException e) {
                            // Skip files that can't be read
                        }
                    });
            }
        }
        
        // Collect from answers
        Path answersDir = outputPath.resolve("answers");
        if (Files.exists(answersDir)) {
            try (Stream<Path> files = Files.list(answersDir)) {
                files.filter(f -> f.toString().endsWith(".md"))
                    .forEach(file -> {
                        try {
                            collectTopicsFromFile(file, "answer", topicDataMap);
                        } catch (IOException e) {
                            // Skip files that can't be read
                        }
                    });
            }
        }
        
        // Collect from notes
        Path notesDir = outputPath.resolve("notes");
        if (Files.exists(notesDir)) {
            try (Stream<Path> files = Files.list(notesDir)) {
                files.filter(f -> f.toString().endsWith(".md"))
                    .forEach(file -> {
                        try {
                            collectTopicsFromFile(file, "note", topicDataMap);
                        } catch (IOException e) {
                            // Skip files that can't be read
                        }
                    });
            }
        }
    }
    
    /**
     * Extract topics from a Q/A/N file frontmatter
     */
    private void collectTopicsFromFile(Path file, String type, Map<String, TopicData> topicDataMap) throws IOException {
        String content = Files.readString(file);
        
        // Extract ID from frontmatter
        String id = extractFromFrontmatter(content, "id");
        String author = extractFromFrontmatter(content, "author");
        String topicsStr = extractFromFrontmatter(content, "topics");
        
        if (id != null && topicsStr != null) {
            // Clean ID from quotes
            id = id.replaceAll("\"", "").trim();
            
            // Parse topics array: ["topic1", "topic2"] → ["topic1", "topic2"]
            topicsStr = topicsStr.replaceAll("[\\[\\]\"]", "").trim();
            logger.debug("File {} ({}): id={}, topics={}", file.getFileName(), type, id, topicsStr);
            if (!topicsStr.isEmpty()) {
                String[] topics = topicsStr.split(",\\s*");
                for (String topic : topics) {
                    if (!topic.isEmpty()) {
                        TopicData data = topicDataMap.computeIfAbsent(topic, k -> new TopicData());
                        switch (type) {
                            case "question": 
                                data.questions.add(id);
                                // Check if question is answered (for proper categorization in topics)
                                String answeredBy = extractFromFrontmatter(content, "answeredBy");
                                if (answeredBy != null && !answeredBy.isEmpty()) {
                                    answeredBy = answeredBy.replaceAll("\"", "").trim();
                                    data.qToA.put(id, answeredBy);
                                }
                                break;
                            case "answer": 
                                data.answers.add(id);
                                // Check if this answer references a question (to exclude from standalone)
                                String answersQuestion = extractFromFrontmatter(content, "answersQuestion");
                                if (answersQuestion != null && !answersQuestion.isEmpty()) {
                                    answersQuestion = answersQuestion.replaceAll("\"", "").trim();
                                    // Mark this answer as linked (will be excluded from standalone)
                                    data.linkedAnswers.add(id);
                                    data.aToQ.put(id, answersQuestion);
                                }
                                break;
                            case "note": data.notes.add(id); break;
                        }
                        if (author != null && !author.isEmpty()) {
                            data.contributors.add(author.replaceAll("\"", ""));
                        }
                        
                        // Extract and collect tags from frontmatter (skip system tags like #question, #answer, #source_*, etc.)
                        String tagsStr = extractFromFrontmatter(content, "tags");
                        if (tagsStr != null && !tagsStr.isEmpty()) {
                            tagsStr = tagsStr.replaceAll("[\\[\\]\"]", "").trim();
                            String[] tags = tagsStr.split(",\\s*");
                            for (String tag : tags) {
                                tag = tag.trim();
                                // Skip system tags (starting with #)
                                if (!tag.isEmpty() && !tag.startsWith("#")) {
                                    data.tags.add(tag);
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Extract value from frontmatter
     */
    private String extractFromFrontmatter(String content, String key) {
        String pattern = key + ":\\s*(.+)";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher m = p.matcher(content);
        if (m.find()) {
            return m.group(1).trim();
        }
        return null;
    }
    
    /**
     * Helper class to collect topic data
     */
    private static class TopicData {
        Set<String> questions = new HashSet<>();
        Set<String> answers = new HashSet<>();
        Set<String> notes = new HashSet<>();
        Set<String> contributors = new HashSet<>();
        Set<String> tags = new HashSet<>(); // Aggregated tags from Q/A/N
        Map<String, String> qToA = new HashMap<>(); // questionId -> answerId (for tracking answered questions)
        Map<String, String> aToQ = new HashMap<>(); // answerId -> questionId (for checking if answer belongs to this topic)
        Set<String> linkedAnswers = new HashSet<>(); // All answers that reference questions (to exclude from standalone)
    }
    
    /**
     * Create topic file with Q/A/N aggregation, contributors, and AI description
     * Order: Notes → Questions with Answers → Questions without Answers → Standalone Answers
     */
    private void createTopicFileWithAggregation(Path topicFile, Path topicDescFile, String title, String id, 
                                                 String source, TopicData data, AnalysisResult analysis) throws IOException {
        Map<String, Object> frontmatter = new LinkedHashMap<>();
        frontmatter.put("type", "topic");
        frontmatter.put("title", title);
        frontmatter.put("id", id);
        frontmatter.put("source", source);
        frontmatter.put("contributors", new ArrayList<>(data.contributors));
        
        // Add aggregated tags to frontmatter (sorted)
        if (!data.tags.isEmpty()) {
            List<String> sortedTags = new ArrayList<>(data.tags);
            Collections.sort(sortedTags);
            frontmatter.put("tags", sortedTags);
        }
        
        frontmatter.put("created", java.time.Instant.now().toString());
        
        StringBuilder content = new StringBuilder();
        content.append("---\n");
        content.append(toYaml(frontmatter));
        content.append("---\n\n");
        content.append("# ").append(title).append("\n\n");
        
        // AI description embed
        content.append("![[").append(id).append("-desc]]\n\n");
        
        // Contributors
        content.append("## Key Contributors\n\n");
        for (String contributor : data.contributors) {
            content.append("- [[").append(normalizePersonName(contributor)).append("|").append(contributor).append("]]\n");
        }
        content.append("\n");
        
        // Build Q/A mapping for this topic
        Set<String> questionsInTopic = data.questions;
        Set<String> answersInTopic = data.answers;
        
        // Use Q→A mapping collected from existing files + current analysis
        Map<String, String> qToA = new HashMap<>(data.qToA); // Start with mappings from existing files
        Set<String> questionsWithAnswers = new HashSet<>(qToA.keySet());
        Set<String> standaloneAnswers = new HashSet<>(answersInTopic);
        
        // Add Q→A mapping from current analysis
        if (analysis != null && analysis.getQuestions() != null) {
            for (Question q : analysis.getQuestions()) {
                if (questionsInTopic.contains(q.getId()) && q.getAnsweredBy() != null && !q.getAnsweredBy().isEmpty()) {
                    qToA.put(q.getId(), q.getAnsweredBy());
                    questionsWithAnswers.add(q.getId());
                }
            }
        }
        
        // Determine which answers to exclude from standalone
        // IMPORTANT: Only exclude answers if BOTH answer AND question are in this topic
        // If answer is in topic X but question is in topic Y, show answer as standalone in topic X
        Set<String> answersToExclude = new HashSet<>();
        
        // Check all answers in this topic
        for (String answerId : answersInTopic) {
            boolean shouldExclude = false;
            
            // First, check answers from current analysis
            if (analysis != null && analysis.getAnswers() != null) {
                for (Answer a : analysis.getAnswers()) {
                    if (a.getId().equals(answerId) && a.getAnswersQuestion() != null && !a.getAnswersQuestion().isEmpty()) {
                        // If question is also in this topic, exclude the answer
                        if (questionsInTopic.contains(a.getAnswersQuestion())) {
                            shouldExclude = true;
                        }
                        break;
                    }
                }
            }
            
            // If not found in analysis, check if it's in linkedAnswers (from existing files)
            if (!shouldExclude && data.linkedAnswers.contains(answerId)) {
                // This answer has answersQuestion, check if question is also in this topic
                String questionId = data.aToQ.get(answerId);
                if (questionId != null && questionsInTopic.contains(questionId)) {
                    shouldExclude = true;
                }
            }
            
            if (shouldExclude) {
                answersToExclude.add(answerId);
            }
        }
        
        // Remove answers that are embedded in questions within this same topic
        standaloneAnswers.removeAll(answersToExclude);
        
        Set<String> questionsWithoutAnswers = new HashSet<>(questionsInTopic);
        questionsWithoutAnswers.removeAll(questionsWithAnswers);
        
        // 1. Notes first
        if (!data.notes.isEmpty()) {
            content.append("## Notes\n\n");
            for (String nId : data.notes) {
                content.append("![[").append(nId).append("]]\n\n");
            }
        }
        
        // 2. Questions with Answers (Q + embedded A)
        if (!questionsWithAnswers.isEmpty()) {
            content.append("## Questions with Answers\n\n");
            for (String qId : questionsWithAnswers) {
                content.append("![[").append(qId).append("]]\n\n");
                // Answer already embedded in question file, no need to embed again
            }
        }
        
        // 3. Questions without Answers
        if (!questionsWithoutAnswers.isEmpty()) {
            content.append("## Unanswered Questions\n\n");
            for (String qId : questionsWithoutAnswers) {
                content.append("![[").append(qId).append("]]\n\n");
            }
        }
        
        // 4. Standalone Answers (not linked to questions in this topic)
        if (!standaloneAnswers.isEmpty()) {
            content.append("## Additional Answers\n\n");
            for (String aId : standaloneAnswers) {
                content.append("![[").append(aId).append("]]\n\n");
            }
        }
        
        Files.writeString(topicFile, content.toString());
        
        // Create desc file placeholder for AI (only if it doesn't exist)
        if (!Files.exists(topicDescFile)) {
            StringBuilder descContent = new StringBuilder();
            descContent.append("<!-- AI_CONTENT_START -->\n\n");
            descContent.append("Topic description will be generated by AI based on related questions, answers, and notes.\n\n");
            descContent.append("<!-- AI_CONTENT_END -->\n");
            Files.writeString(topicDescFile, descContent.toString());
        }
    }
}

