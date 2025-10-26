package com.github.istin.dmtools.common.kb;

import com.github.istin.dmtools.common.kb.model.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
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
        
        // Track which areas have contributions from the current analysis
        Set<String> areasFromCurrentAnalysis = new HashSet<>();
        
        // FIRST: Collect from current analysis to identify which areas are actually from this source
        if (analysis.getQuestions() != null) {
            analysis.getQuestions().forEach(q -> {
                if (q.getArea() != null && !q.getArea().isEmpty()) {
                    areasFromCurrentAnalysis.add(q.getArea());
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
                    areasFromCurrentAnalysis.add(a.getArea());
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
                    areasFromCurrentAnalysis.add(n.getArea());
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
        
        // THEN: Collect contributors and topics from existing area files (for incremental updates)
        Path areasDir = outputPath.resolve("areas");
        if (Files.exists(areasDir)) {
            try (java.util.stream.Stream<Path> areaDirs = Files.list(areasDir)) {
                areaDirs.filter(Files::isDirectory).forEach(areaDir -> {
                    try {
                        String areaId = areaDir.getFileName().toString();
                        Path areaFile = areaDir.resolve(areaId + ".md");
                        if (Files.exists(areaFile)) {
                            String content = Files.readString(areaFile);
                            
                            // Extract area title from frontmatter
                            String areaTitle = extractFromFrontmatter(content, "title");
                            if (areaTitle != null) {
                                
                                // Extract existing contributors
                                List<String> existingContributors = extractListFromFrontmatter(content, "contributors");
                                if (!existingContributors.isEmpty()) {
                                    areaContributors.computeIfAbsent(areaTitle, k -> new HashSet<>()).addAll(existingContributors);
                                }
                                
                                // Extract existing topics from content (after ## Topics)
                                java.util.regex.Pattern topicsPattern = java.util.regex.Pattern.compile("##\\s+Topics\\s+(.+?)(?=##|<!--|$)", java.util.regex.Pattern.DOTALL);
                                java.util.regex.Matcher topicsMatcher = topicsPattern.matcher(content);
                                if (topicsMatcher.find()) {
                                    String topicsSection = topicsMatcher.group(1);
                                    // Extract topic names from links: [[topic-id|Topic Name]]
                                    java.util.regex.Pattern linkPattern = java.util.regex.Pattern.compile("\\[\\[([^|\\]]+)\\|([^\\]]+)\\]\\]");
                                    java.util.regex.Matcher linkMatcher = linkPattern.matcher(topicsSection);
                                    while (linkMatcher.find()) {
                                        String topicName = linkMatcher.group(2).trim();
                                        areaTopics.computeIfAbsent(areaTitle, k -> new HashSet<>()).add(topicName);
                                    }
                                }
                            }
                        }
                    } catch (IOException e) {
                        logger.warn("Failed to read area file: {}", areaDir, e);
                    }
                });
            }
        }
        
        // Create directory and files for each area
        Files.createDirectories(areasDir);
        for (String area : areaContributors.keySet()) {
            String areaId = slugify(area);
            Path areaDir = areasDir.resolve(areaId);
            Files.createDirectories(areaDir);
            
            // Get contributors and topics for this area
            List<String> contributors = new ArrayList<>(areaContributors.getOrDefault(area, Collections.emptySet()));
            List<String> topics = new ArrayList<>(areaTopics.getOrDefault(area, Collections.emptySet()));
            
            // Create main area file
            Path areaFile = areaDir.resolve(areaId + ".md");
            Path areaDescFile = areaDir.resolve(areaId + "-desc.md");
            
            // Only pass sourceName if this area has contributions from current analysis
            String sourceToAdd = areasFromCurrentAnalysis.contains(area) ? sourceName : null;
            
            createAreaFileWithTopics(areaFile, areaDescFile, area, areaId, sourceToAdd, contributors, topics);
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
            updatePersonFile(personFile, sourceName, questionsCount, answersCount, notesCount, contributions);
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
    

    /**
     * Update topic file with statistics about questions, answers, and notes
     */
    public void updateTopicWithStats(Path outputPath, String topicId, int questionsCount, 
                                       int answersCount, int notesCount, 
                                       List<String> contributors) throws IOException {
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
        autoGenSection.append("- Notes: ").append(notesCount).append("\n\n");
        
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
                                   int questions, int answers, int notes,
                                   PersonContributions contributions) throws IOException {
        Map<String, Object> frontmatter = new LinkedHashMap<>();
        frontmatter.put("id", id);
        frontmatter.put("name", name);
        frontmatter.put("type", "person");
        frontmatter.put("sources", Arrays.asList(source));
        frontmatter.put("questionsAsked", questions);
        frontmatter.put("answersProvided", answers);
        frontmatter.put("notesContributed", notes);
        
        // Generate tags for all sources
        List<String> tags = new ArrayList<>();
        tags.add("#person");
        tags.add(formatSourceTag(source));
        frontmatter.put("tags", tags);
        
        String content = "---\n" + toYaml(frontmatter) + "---\n\n"
                + "# " + name + "\n\n"
                + "![[" + id + "-desc]]\n\n";
        
        // Auto-generated section
        content += "<!-- AUTO_GENERATED_START -->\n\n";
        
        // If we have detailed contributions, use them
        if (contributions != null) {
            // Sort contributions by ID number for consistent ordering
            sortContributionsByIdNumber(contributions);
            
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
        
        // NOTE: Description file (-desc.md) is NOT created here
        // It will be created only by KBAggregationAgent when AI descriptions are generated
        // This allows PROCESS_ONLY mode to skip AI description generation
    }
    
    /**
     * Format source name for tag (add #source_ prefix if not already present)
     */
    private String formatSourceTag(String source) {
        if (source.startsWith("source_")) {
            return "#" + source;
        }
        return "#source_" + source;
    }
    
    /**
     * Sort contributions by ID number for consistent ordering AND deduplicate by ID.
     * IDs are in format: q_0001, a_0002, n_0003, etc.
     * 
     * CRITICAL: When a Q/A/N belongs to multiple topics, it appears multiple times in contributions.
     * Example: q_0006 has 2 topics → appears twice in getQuestions()
     * We must deduplicate to show each Q/A/N only once in person profile.
     */
    private void sortContributionsByIdNumber(PersonContributions contributions) {
        Comparator<PersonContributions.ContributionItem> idComparator = (a, b) -> {
            int numA = extractIdNumber(a.getId());
            int numB = extractIdNumber(b.getId());
            return Integer.compare(numA, numB);
        };
        
        // CRITICAL: Deduplicate by ID first, then sort
        // Use LinkedHashMap to preserve insertion order during deduplication
        contributions.setQuestions(deduplicateAndSort(contributions.getQuestions(), idComparator));
        contributions.setAnswers(deduplicateAndSort(contributions.getAnswers(), idComparator));
        contributions.setNotes(deduplicateAndSort(contributions.getNotes(), idComparator));

        // Sort topics by count (descending) for more useful display
        contributions.getTopics().sort((a, b) -> Integer.compare(b.getCount(), a.getCount()));
    }
    
    /**
     * Deduplicate contribution items by ID and sort them.
     * If a Q/A/N appears multiple times (due to multiple topics), keep only the first occurrence.
     */
    private List<PersonContributions.ContributionItem> deduplicateAndSort(
            List<PersonContributions.ContributionItem> items,
            Comparator<PersonContributions.ContributionItem> comparator) {
        
        // Use LinkedHashMap to deduplicate while preserving first occurrence
        Map<String, PersonContributions.ContributionItem> deduped = new LinkedHashMap<>();
        for (PersonContributions.ContributionItem item : items) {
            deduped.putIfAbsent(item.getId(), item);
        }
        
        // Convert to list and sort
        List<PersonContributions.ContributionItem> result = new ArrayList<>(deduped.values());
        result.sort(comparator);
        return result;
    }
    
    /**
     * Extract numeric part from ID (e.g., "q_0001" -> 1, "a_0042" -> 42)
     */
    private int extractIdNumber(String id) {
        if (id == null || id.isEmpty()) {
            return 0;
        }
        
        // ID format: q_0001, a_0002, n_0003
        String[] parts = id.split("_");
        if (parts.length == 2) {
            try {
                return Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }
    
    /**
     * Update a single field in frontmatter
     */
    private String updateFrontmatterField(String content, String fieldName, String newValue) {
        // Pattern to match field in frontmatter
        String pattern = "(" + fieldName + ":\\s*)([^\\n]+)";
        return content.replaceFirst(pattern, "$1" + newValue);
    }
    
    /**
     * Extract sources from frontmatter
     */
    private List<String> extractSourcesFromFrontmatter(String content) {
        List<String> sources = new ArrayList<>();
        // Match sources: [source1, source2] or sources: source1
        Pattern pattern = Pattern.compile("sources?:\\s*\\[([^\\]]+)\\]");
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            String sourcesStr = matcher.group(1);
            for (String source : sourcesStr.split(",")) {
                String cleaned = source.trim().replace("\"", "").replace("'", "");
                if (!cleaned.isEmpty()) {
                    sources.add(cleaned);
                }
            }
        } else {
            // Try single source: sources: "source1"
            pattern = Pattern.compile("sources?:\\s*\"?([^\\n\"]+)\"?");
            matcher = pattern.matcher(content);
            if (matcher.find()) {
                sources.add(matcher.group(1).trim());
            }
        }
        return sources;
    }
    
    /**
     * Extract list from frontmatter (e.g., contributors, tags)
     */
    private List<String> extractListFromFrontmatter(String content, String fieldName) {
        List<String> items = new ArrayList<>();
        // Match field: [item1, item2] or field: item1
        Pattern pattern = Pattern.compile(fieldName + ":\\s*\\[([^\\]]+)\\]");
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            String itemsStr = matcher.group(1);
            for (String item : itemsStr.split(",")) {
                String cleaned = item.trim().replace("\"", "").replace("'", "");
                if (!cleaned.isEmpty()) {
                    items.add(cleaned);
                }
            }
        } else {
            // Try single item: field: "item1"
            pattern = Pattern.compile(fieldName + ":\\s*\"?([^\\n\"]+)\"?");
            matcher = pattern.matcher(content);
            if (matcher.find()) {
                items.add(matcher.group(1).trim());
            }
        }
        return items;
    }
    
    /**
     * Update sources in frontmatter
     */
    private String updateFrontmatterSources(String content, List<String> sources) {
        String sourcesYaml = sources.stream()
                .map(s -> "\"" + s + "\"")
                .collect(Collectors.joining(", ", "[", "]"));
        
        // Try to replace sources: [...] or sources: "..."
        String pattern = "(sources?:\\s*)(?:\\[[^\\]]+\\]|\"[^\"]+\"|[^\\n]+)";
        content = content.replaceFirst(pattern, "$1" + sourcesYaml);
        
        // Also update tags to include all sources
        List<String> tags = new ArrayList<>();
        tags.add("#person");
        for (String source : sources) {
            tags.add(formatSourceTag(source));
        }
        String tagsYaml = tags.stream()
                .map(t -> "\"" + t + "\"")
                .collect(Collectors.joining(", ", "[", "]"));
        pattern = "(tags:\\s*)\\[[^\\]]+\\]";
        content = content.replaceFirst(pattern, "$1" + tagsYaml);
        
        return content;
    }

    private void updatePersonFile(Path file, String newSource, int questions, int answers, int notes, PersonContributions contributions) throws IOException {
        String content = Files.readString(file);
        
        // Extract existing sources from frontmatter and merge with new source
        List<String> existingSources = extractSourcesFromFrontmatter(content);
        // Only add newSource if provided (null means no contribution from current source)
        if (newSource != null && !existingSources.contains(newSource)) {
            existingSources.add(newSource);
        }
        
        // Update frontmatter
        content = updateFrontmatterField(content, "questionsAsked", String.valueOf(questions));
        content = updateFrontmatterField(content, "answersProvided", String.valueOf(answers));
        content = updateFrontmatterField(content, "notesContributed", String.valueOf(notes));
        content = updateFrontmatterSources(content, existingSources);
        
        // Build the replacement content
        String replacement = "<!-- AUTO_GENERATED_START -->\n\n";
        
        // If we have detailed contributions, use them
        if (contributions != null) {
            // Sort contributions by ID number for consistent ordering
            sortContributionsByIdNumber(contributions);
            
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
        allTags.add(formatSourceTag(source));
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
        
        // Add links section if available
        if (question.getLinks() != null && !question.getLinks().isEmpty()) {
            content += "\n**Links:**\n";
            for (Link link : question.getLinks()) {
                content += "- [" + link.getTitle() + "](" + link.getUrl() + ")\n";
            }
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
        allTags.add(formatSourceTag(source));
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
        
        // Add links section if available
        if (answer.getLinks() != null && !answer.getLinks().isEmpty()) {
            content += "\n**Links:**\n";
            for (Link link : answer.getLinks()) {
                content += "- [" + link.getTitle() + "](" + link.getUrl() + ")\n";
            }
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
        allTags.add(formatSourceTag(source));
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
        
        // Add links section if available
        if (note.getLinks() != null && !note.getLinks().isEmpty()) {
            content += "\n**Links:**\n";
            for (Link link : note.getLinks()) {
                content += "- [" + link.getTitle() + "](" + link.getUrl() + ")\n";
            }
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
        // Extract existing sources and created timestamp if file exists
        List<String> sources = new ArrayList<>();
        String existingCreated = null;
        if (Files.exists(areaFile)) {
            String existingContent = Files.readString(areaFile);
            sources = extractSourcesFromFrontmatter(existingContent);
            existingCreated = extractFromFrontmatter(existingContent, "created");
        }
        
        // Add new source if not already present and if source is provided (null means no contribution from current source)
        if (source != null && !sources.contains(source)) {
            sources.add(source);
        }
        
        // Sort contributors for frontmatter to ensure consistent order
        List<String> sortedContributorsForFrontmatter = new ArrayList<>(contributors);
        Collections.sort(sortedContributorsForFrontmatter);
        
        Map<String, Object> frontmatter = new LinkedHashMap<>();
        frontmatter.put("type", "area");
        frontmatter.put("title", title);
        frontmatter.put("id", id);
        frontmatter.put("sources", sources); // Changed from "source" to "sources" (plural, array)
        frontmatter.put("contributors", sortedContributorsForFrontmatter);
        // Preserve existing created timestamp, or create new one if doesn't exist
        frontmatter.put("created", existingCreated != null ? existingCreated : java.time.Instant.now().toString());
        
        StringBuilder content = new StringBuilder();
        content.append("---\n");
        content.append(toYaml(frontmatter));
        content.append("---\n\n");
        content.append("# ").append(title).append("\n\n");
        
        // AI description embed
        content.append("![[").append(id).append("-desc]]\n\n");
        
        // Topics (themes within this area) - sorted alphabetically to reduce git diffs
        if (!topics.isEmpty()) {
            content.append("## Topics\n\n");
            List<String> sortedTopics = new ArrayList<>(topics);
            Collections.sort(sortedTopics);
            for (String topic : sortedTopics) {
                String topicId = slugify(topic);
                content.append("- [[").append(topicId).append("|").append(topic).append("]]\n");
            }
            content.append("\n");
        }
        
        // Contributors - sorted alphabetically to reduce git diffs
        content.append("## Key Contributors\n\n");
        List<String> sortedContributors = new ArrayList<>(contributors);
        Collections.sort(sortedContributors);
        for (String contributor : sortedContributors) {
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
     * Build topic files (detailed themes within areas)
     * NEW: Creates topics/*.md with Q/A/N aggregation and contributors
     * IMPORTANT: For incremental updates, also collects Q/A/N from existing files
     */
    public void buildTopicFiles(AnalysisResult analysis, Path outputPath, String sourceName) throws IOException {
        // Collect data per topic
        Map<String, TopicData> topicDataMap = new HashMap<>();
        
        // Track which topics have contributions from the current analysis
        Set<String> topicsFromCurrentAnalysis = new HashSet<>();
        
        // FIRST: Collect from current analysis to identify which topics are actually from this source
        // Collect from questions
        if (analysis.getQuestions() != null) {
            for (Question q : analysis.getQuestions()) {
                if (q.getTopics() != null) {
                    for (String topic : q.getTopics()) {
                        topicsFromCurrentAnalysis.add(topic);
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
                        topicsFromCurrentAnalysis.add(topic);
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
                        topicsFromCurrentAnalysis.add(topic);
                        TopicData data = topicDataMap.computeIfAbsent(topic, k -> new TopicData());
                        if (n.getId() != null) data.notes.add(n.getId());
                        if (n.getAuthor() != null) data.contributors.add(n.getAuthor());
                        if (n.getTags() != null) data.tags.addAll(n.getTags());
                    }
                }
            }
        }
        
        // THEN: Collect from existing Q/A/N files (for incremental updates) to get full picture
        collectFromExistingFiles(outputPath, topicDataMap);
        
        // Create topics directory
        Path topicsDir = outputPath.resolve("topics");
        Files.createDirectories(topicsDir);
        
        // Create file for each unique topic
        // Only add current source to topics that have Q/A/N from current analysis
        for (Map.Entry<String, TopicData> entry : topicDataMap.entrySet()) {
            String topic = entry.getKey();
            String topicId = slugify(topic);
            Path topicFile = topicsDir.resolve(topicId + ".md");
            Path topicDescFile = topicsDir.resolve(topicId + "-desc.md");
            
            // Only pass sourceName if this topic has contributions from current analysis
            String sourceToAdd = topicsFromCurrentAnalysis.contains(topic) ? sourceName : null;
            
            createTopicFileWithAggregation(topicFile, topicDescFile, topic, topicId, sourceToAdd, entry.getValue(), analysis);
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
     * Removes surrounding quotes from the extracted value
     */
    private String extractFromFrontmatter(String content, String key) {
        String pattern = key + ":\\s*(.+)";
        Pattern p = Pattern.compile(pattern);
        Matcher m = p.matcher(content);
        if (m.find()) {
            String value = m.group(1).trim();
            // Remove surrounding quotes (single or double)
            if (value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1);
            } else if (value.startsWith("'") && value.endsWith("'")) {
                value = value.substring(1, value.length() - 1);
            }
            return value;
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
        // Extract existing sources and created timestamp if file exists
        List<String> sources = new ArrayList<>();
        String existingCreated = null;
        if (Files.exists(topicFile)) {
            String existingContent = Files.readString(topicFile);
            sources = extractSourcesFromFrontmatter(existingContent);
            existingCreated = extractFromFrontmatter(existingContent, "created");
        }
        
        // Add new source if not already present and if source is provided (null means no contribution from current source)
        if (source != null && !sources.contains(source)) {
            sources.add(source);
        }
        
        Map<String, Object> frontmatter = new LinkedHashMap<>();
        frontmatter.put("type", "topic");
        frontmatter.put("title", title);
        frontmatter.put("id", id);
        frontmatter.put("sources", sources); // Changed from "source" to "sources" (plural, array)
        frontmatter.put("contributors", new ArrayList<>(data.contributors));
        
        // Add aggregated tags to frontmatter (sorted)
        if (!data.tags.isEmpty()) {
            List<String> sortedTags = new ArrayList<>(data.tags);
            Collections.sort(sortedTags);
            frontmatter.put("tags", sortedTags);
        }
        
        // Preserve existing created timestamp, or create new one if doesn't exist
        frontmatter.put("created", existingCreated != null ? existingCreated : java.time.Instant.now().toString());
        
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
    
    /**
     * Generate people.md file listing all people in the KB
     */
    public void generatePeopleIndex(Path outputPath) throws IOException {
        Path peopleDir = outputPath.resolve("people");
        if (!Files.exists(peopleDir)) {
            return;
        }
        
        Path peopleFile = peopleDir.resolve("people.md");
        
        // Collect all people
        List<String> people = new ArrayList<>();
        try (Stream<Path> stream = Files.list(peopleDir)) {
            stream.filter(Files::isDirectory)
                  .forEach(personDir -> people.add(personDir.getFileName().toString()));
        }
        
        // Sort alphabetically
        Collections.sort(people);
        
        StringBuilder content = new StringBuilder();
        content.append("# People\n\n");
        content.append("<!-- AUTO_GENERATED_START -->\n\n");
        content.append("**Total contributors:** ").append(people.size()).append("\n\n");
        content.append("<!-- AUTO_GENERATED_END -->\n\n");
        
        content.append("## All Contributors\n\n");
        
        for (String personId : people) {
            // Embed person profile (so it's visible in Obsidian)
            content.append("![[").append(personId).append("/").append(personId).append("]]\n\n");
            content.append("---\n\n");
        }
        
        // Remove last separator
        if (!people.isEmpty()) {
            int lastSeparator = content.lastIndexOf("---\n\n");
            if (lastSeparator > 0) {
                content.delete(lastSeparator, content.length());
            }
        }
        
        Files.writeString(peopleFile, content.toString());
    }
}

