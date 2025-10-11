package com.github.istin.dmtools.common.kb.agent;

import com.github.istin.dmtools.ai.ChunkPreparation;
import com.github.istin.dmtools.ai.agent.AbstractSimpleAgent;
import com.github.istin.dmtools.common.kb.KBAnalysisResultMerger;
import com.github.istin.dmtools.common.kb.KBStatistics;
import com.github.istin.dmtools.common.kb.KBStructureBuilder;
import com.github.istin.dmtools.common.kb.SourceConfigManager;
import com.github.istin.dmtools.common.kb.model.*;
import com.github.istin.dmtools.common.kb.params.AggregationParams;
import com.github.istin.dmtools.common.kb.params.AnalysisParams;
import com.github.istin.dmtools.common.kb.params.KBOrchestratorParams;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Main orchestration agent for Knowledge Base building
 * Coordinates all stages: Analysis → Structure → Aggregation → Statistics
 */
public class KBOrchestrator extends AbstractSimpleAgent<KBOrchestratorParams, KBResult> {
    
    private static final Logger logger = LogManager.getLogger(KBOrchestrator.class);
    private static final Gson GSON = new Gson();
    
    @Inject
    protected KBAnalysisAgent analysisAgent;
    
    @Inject
    protected KBStructureBuilder structureBuilder;
    
    @Inject
    protected KBAggregationAgent aggregationAgent;
    
    @Inject
    protected KBQuestionAnswerMappingAgent qaMappingAgent;
    
    @Inject
    protected KBStatistics statistics;
    
    @Inject
    protected KBAnalysisResultMerger resultMerger;
    
    @Inject
    protected SourceConfigManager sourceConfigManager;
    
    @Inject
    protected ChunkPreparation chunkPreparation;
    
    @Inject
    public KBOrchestrator() {
        super("kb_orchestrator");  // XML prompt template name
        logger.info("KBOrchestrator initialized");
    }
    
    @Override
    public KBResult run(KBOrchestratorParams params) throws Exception {
        Path outputPath = Paths.get(params.getOutputPath());
        
        // Step 1: Initialize output directories
        initializeOutputDirectories(outputPath);
        
        // Step 2: Copy input file to inbox/raw/
        Path inputFilePath = Paths.get(params.getInputFile());
        Path rawInboxPath = outputPath.resolve("inbox/raw");
        Files.createDirectories(rawInboxPath);
        
        String timestamp = String.valueOf(System.currentTimeMillis());
        String inputFileName = inputFilePath.getFileName().toString();
        Path rawCopyPath = rawInboxPath.resolve(timestamp + "_" + inputFileName);
        Files.copy(inputFilePath, rawCopyPath);
        logger.info("Copied input file to: {}", rawCopyPath);
        
        // Step 3: Load existing KB context
        KBContext context = loadKBContext(outputPath);
        
        // Step 4: Read and prepare input
        String inputContent = Files.readString(inputFilePath);
        
        // Try to parse as JSON and convert if needed
        inputContent = normalizeInputContent(inputContent);
        
        // Step 5: Chunk input if large
        List<ChunkPreparation.Chunk> chunks = chunkPreparation.prepareChunks(Arrays.asList(inputContent));
        
        // Step 6: AI Analysis (process each chunk separately) and save analyzed JSON
        Path analyzedInboxPath = outputPath.resolve("inbox/analyzed");
        Files.createDirectories(analyzedInboxPath);
        
        AnalysisResult analysisResult;
        if (chunks.size() == 1) {
            logger.info("Processing single chunk");
            analysisResult = analyzeChunk(chunks.get(0).getText(), params.getSourceName(), context);
        } else {
            logger.info("Processing {} chunks", chunks.size());
            analysisResult = analyzeAndMergeChunks(chunks, params.getSourceName(), context);
        }
        
        // Save analyzed JSON
        Path analyzedJsonPath = analyzedInboxPath.resolve(timestamp + "_analyzed.json");
        String analyzedJson = GSON.toJson(analysisResult);
        Files.writeString(analyzedJsonPath, analyzedJson);
        logger.info("Saved analyzed JSON to: {}", analyzedJsonPath);
        logger.info("Analyzed JSON preview: questions={}, answers={}, notes={}", 
                analysisResult.getQuestions().size(),
                analysisResult.getAnswers().size(),
                analysisResult.getNotes().size());
        
        // Step 6.5: Map new answers/notes to existing unanswered questions
        applyQuestionAnswerMapping(analysisResult, context);
        
        // Step 7: Build Structure (mechanical)
        buildStructure(analysisResult, outputPath, params.getSourceName(), context);
        
        // Step 8: AI Aggregation (one-by-one)
        performAggregation(analysisResult, outputPath, context);
        
        // Step 9: Generate Statistics (mechanical)
        statistics.generateStatistics(outputPath);
        
        // Step 10: Update source config
        sourceConfigManager.updateLastSyncDate(params.getSourceName(), params.getDateTime(), outputPath);
        
        // Step 11: Build and return result
        return buildResult(analysisResult, outputPath);
    }
    
    @Override
    public KBResult transformAIResponse(KBOrchestratorParams params, String response) throws Exception {
        // This orchestrator doesn't directly use AI responses
        // Delegated to sub-agents
        throw new UnsupportedOperationException("KBOrchestrator uses sub-agents for AI interactions");
    }
    
    private void initializeOutputDirectories(Path outputPath) throws IOException {
        Files.createDirectories(outputPath);
        Files.createDirectories(outputPath.resolve("topics"));
        Files.createDirectories(outputPath.resolve("people"));
        Files.createDirectories(outputPath.resolve("stats"));
        Files.createDirectories(outputPath.resolve("inbox"));
    }
    
    private KBContext loadKBContext(Path outputPath) throws IOException {
        KBContext context = new KBContext();
        
        // Load existing people
        Path peopleDir = outputPath.resolve("people");
        if (Files.exists(peopleDir)) {
            try (Stream<Path> people = Files.list(peopleDir)) {
                people.filter(Files::isDirectory)
                        .forEach(p -> context.getExistingPeople().add(p.getFileName().toString()));
            }
        }
        
        // Load existing topics (from *.md files, exclude *-desc.md)
        Path topicsDir = outputPath.resolve("topics");
        if (Files.exists(topicsDir)) {
            try (Stream<Path> topics = Files.list(topicsDir)) {
                topics.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().endsWith(".md"))
                        .filter(p -> !p.getFileName().toString().endsWith("-desc.md"))
                        .forEach(p -> {
                            String filename = p.getFileName().toString();
                            String topicId = filename.substring(0, filename.lastIndexOf(".md"));
                            context.getExistingTopics().add(topicId);
                        });
            }
        }
        
        // Load existing questions (for helping AI identify answers to old questions)
        Path questionsDir = outputPath.resolve("questions");
        if (Files.exists(questionsDir)) {
            try (Stream<Path> questions = Files.list(questionsDir)) {
                questions.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().endsWith(".md"))
                        .forEach(p -> {
                            try {
                                String content = Files.readString(p);
                                String id = p.getFileName().toString().replace(".md", "");
                                String author = extractAuthorFromContent(content);
                                String area = extractAreaFromContent(content);
                                boolean answered = content.contains("answered: true");
                                
                                // Extract question text (after "# Question: id" line)
                                String text = extractQuestionText(content);
                                
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
        
        logger.info("Loaded KB context: {} people, {} topics, {} unanswered questions", 
                   context.getExistingPeople().size(),
                   context.getExistingTopics().size(),
                   context.getExistingQuestions().size());
        
        // Find max IDs
        context.setMaxQuestionId(findMaxId(outputPath, "q_"));
        context.setMaxAnswerId(findMaxId(outputPath, "a_"));
        context.setMaxNoteId(findMaxId(outputPath, "n_"));
        
        return context;
    }
    
    /**
     * Extract question text from question file content
     */
    private String extractQuestionText(String content) {
        // Question text is after "# Question: id" line and before next section
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
    
    /**
     * Apply Q→A mapping from AI agent to link new answers/notes to existing questions
     */
    private void applyQuestionAnswerMapping(AnalysisResult analysisResult, KBContext context) throws Exception {
        if (context.getExistingQuestions().isEmpty()) {
            logger.info("No existing questions for Q→A mapping, skipping");
            return;
        }
        
        // Collect new answers and notes that could answer questions
        List<com.github.istin.dmtools.common.kb.params.QAMappingParams.AnswerLike> newAnswers = new ArrayList<>();
        
        // Add answers that don't have answersQuestion set
        for (Answer answer : analysisResult.getAnswers()) {
            if (answer.getAnswersQuestion() == null || answer.getAnswersQuestion().isEmpty()) {
                newAnswers.add(com.github.istin.dmtools.common.kb.params.QAMappingParams.AnswerLike.fromAnswer(answer));
            }
        }
        
        // Add notes (notes can also answer questions)
        for (Note note : analysisResult.getNotes()) {
            newAnswers.add(com.github.istin.dmtools.common.kb.params.QAMappingParams.AnswerLike.fromNote(note));
        }
        
        if (newAnswers.isEmpty()) {
            logger.info("No new unmapped answers/notes for Q→A mapping");
            return;
        }
        
        // Filter existing questions by area/topics to reduce context size
        Set<String> relevantAreas = newAnswers.stream()
                .map(com.github.istin.dmtools.common.kb.params.QAMappingParams.AnswerLike::getArea)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        
        Set<String> relevantTopics = newAnswers.stream()
                .flatMap(a -> a.getTopics() != null ? a.getTopics().stream() : Stream.empty())
                .collect(Collectors.toSet());
        
        // Filter questions by relevance (area/topics)
        // Include ALL relevant questions (both answered and unanswered) 
        // because one question can have multiple answers
        List<KBContext.QuestionSummary> relevantQuestions = context.getExistingQuestions().stream()
                .filter(q -> relevantAreas.contains(q.getArea()) || 
                            (q.getArea() != null && relevantTopics.stream().anyMatch(t -> q.getArea().contains(t))))
                .sorted(Comparator.comparing(KBContext.QuestionSummary::isAnswered)) // Unanswered first (but include answered too)
                .collect(Collectors.toList());
        
        if (relevantQuestions.isEmpty()) {
            logger.info("No relevant questions found for Q→A mapping");
            return;
        }
        
        long unansweredCount = relevantQuestions.stream().filter(q -> !q.isAnswered()).count();
        logger.info("Running Q→A mapping: {} new answers/notes × {} relevant questions ({} unanswered)", 
                   newAnswers.size(), relevantQuestions.size(), unansweredCount);
        
        // Run mapping agent
        com.github.istin.dmtools.common.kb.params.QAMappingParams mappingParams = new com.github.istin.dmtools.common.kb.params.QAMappingParams();
        mappingParams.setNewAnswers(newAnswers);
        mappingParams.setExistingQuestions(relevantQuestions);
        
        com.github.istin.dmtools.common.kb.model.QAMappingResult mappingResult = qaMappingAgent.run(mappingParams);
        
        // Apply mappings to AnalysisResult
        for (com.github.istin.dmtools.common.kb.model.QAMappingResult.Mapping mapping : mappingResult.getMappings()) {
            if (mapping.getConfidence() < 0.6) {
                logger.debug("Skipping low-confidence mapping: {} → {} ({})", 
                           mapping.getAnswerId(), mapping.getQuestionId(), mapping.getConfidence());
                continue;
            }
            
            String answerId = mapping.getAnswerId();
            String questionId = mapping.getQuestionId();
            
            // Check if it's an answer or note
            if (answerId.startsWith("a_")) {
                // Find the answer and set answersQuestion
                for (Answer answer : analysisResult.getAnswers()) {
                    if (answer.getId().equals(answerId)) {
                        answer.setAnswersQuestion(questionId);
                        logger.info("Mapped answer {} to question {} (confidence: {})", 
                                   answerId, questionId, mapping.getConfidence());
                        break;
                    }
                }
            } else if (answerId.startsWith("n_")) {
                // Convert note to answer
                Note note = null;
                int noteIndex = -1;
                for (int i = 0; i < analysisResult.getNotes().size(); i++) {
                    if (analysisResult.getNotes().get(i).getId().equals(answerId)) {
                        note = analysisResult.getNotes().get(i);
                        noteIndex = i;
                        break;
                    }
                }
                
                if (note != null) {
                    // Remove from notes
                    analysisResult.getNotes().remove(noteIndex);
                    
                    // Create answer from note
                    Answer newAnswer = new Answer();
                    newAnswer.setId(answerId.replace("n_", "a_")); // Convert n_X to a_X
                    newAnswer.setAuthor(note.getAuthor());
                    newAnswer.setText(note.getText());
                    newAnswer.setDate(note.getDate());
                    newAnswer.setArea(note.getArea());
                    newAnswer.setTopics(note.getTopics());
                    newAnswer.setTags(note.getTags());
                    newAnswer.setAnswersQuestion(questionId);
                    newAnswer.setQuality(mapping.getConfidence()); // Use confidence as quality
                    newAnswer.setLinks(note.getLinks());
                    
                    analysisResult.getAnswers().add(newAnswer);
                    
                    logger.info("Converted note {} to answer and mapped to question {} (confidence: {})",
                               answerId, questionId, mapping.getConfidence());
                }
            }
        }
    }
    
    private int findMaxId(Path outputPath, String prefix) throws IOException {
        int maxId = 0;
        
        // Determine which directory to check based on prefix
        String dirName;
        switch (prefix) {
            case "q_": dirName = "questions"; break;
            case "a_": dirName = "answers"; break;
            case "n_": dirName = "notes"; break;
            default: return maxId;
        }
        
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
    
    private String normalizeInputContent(String content) {
        // Try to detect if JSON and convert to more friendly format if needed
        try {
            JsonElement element = JsonParser.parseString(content);
            if (element.isJsonArray()) {
                JsonArray array = element.getAsJsonArray();
                // Convert JSON array to text format for better chunking
                StringBuilder sb = new StringBuilder();
                for (JsonElement item : array) {
                    sb.append(item.toString()).append("\n\n");
                }
                return sb.toString();
            }
        } catch (Exception e) {
            // Not JSON, return as is
        }
        
        return content;
    }
    
    private void buildStructure(AnalysisResult analysisResult, Path outputPath, 
                                String sourceName, KBContext context) throws IOException {
        // Step 1: Map temporary IDs (q_1, a_1, n_1) to real IDs (q_0001, a_0001, n_0001)
        Map<String, String> idMapping = new HashMap<>();
        
        // Map questions
        int nextQuestionId = context.getMaxQuestionId() + 1;
        for (Question question : analysisResult.getQuestions()) {
            String tempId = question.getId(); // e.g., "q_1"
            String realId = String.format("q_%04d", nextQuestionId++);
            idMapping.put(tempId, realId);
            question.setId(realId);
        }
        
        // Map answers
        int nextAnswerId = context.getMaxAnswerId() + 1;
        for (Answer answer : analysisResult.getAnswers()) {
            String tempId = answer.getId(); // e.g., "a_1"
            String realId = String.format("a_%04d", nextAnswerId++);
            idMapping.put(tempId, realId);
            answer.setId(realId);
        }
        
        // Map notes
        int nextNoteId = context.getMaxNoteId() + 1;
        for (Note note : analysisResult.getNotes()) {
            String tempId = note.getId(); // e.g., "n_1"
            String realId = String.format("n_%04d", nextNoteId++);
            idMapping.put(tempId, realId);
            note.setId(realId);
        }
        
        // Step 2: Update Q→A references using mapped IDs
        for (Question question : analysisResult.getQuestions()) {
            String tempAnswerId = question.getAnsweredBy();
            if (tempAnswerId != null && !tempAnswerId.isEmpty() && idMapping.containsKey(tempAnswerId)) {
                question.setAnsweredBy(idMapping.get(tempAnswerId));
            }
        }
        
        for (Answer answer : analysisResult.getAnswers()) {
            String tempQuestionId = answer.getAnswersQuestion();
            if (tempQuestionId != null && !tempQuestionId.isEmpty() && idMapping.containsKey(tempQuestionId)) {
                answer.setAnswersQuestion(idMapping.get(tempQuestionId));
            }
        }
        
        logger.info("ID mapping completed: {} temporary IDs → real IDs", idMapping.size());
        
        // Step 3: Build individual files in correct order
        // Order: Answers → Questions → Notes → Topics → Areas → People
        
        // 3.1: Build answers FIRST (so questions can reference them)
        for (Answer answer : analysisResult.getAnswers()) {
            structureBuilder.buildAnswerFile(answer, outputPath, sourceName);
        }
        
        // 3.2: Build questions (now can embed all answers that reference this question)
        for (Question question : analysisResult.getQuestions()) {
            structureBuilder.buildQuestionFile(question, outputPath, sourceName, analysisResult);
        }
        
        // 3.3: Build notes
        for (Note note : analysisResult.getNotes()) {
            structureBuilder.buildNoteFile(note, outputPath, sourceName);
        }
        
        // Step 4: Build topic files (now Q/A/N have real IDs and correct links)
        structureBuilder.buildTopicFiles(analysisResult, outputPath, sourceName);
        
        // Step 5: Build area structure
        structureBuilder.buildAreaStructure(analysisResult, outputPath, sourceName);
        
        // Build person profiles with detailed contributions
        // Scan ALL files in KB, not just current AnalysisResult (for incremental updates)
        Map<String, PersonStats> personStats = collectPersonStatsFromFiles(outputPath);
        Map<String, PersonContributions> personContributions = collectPersonContributionsFromFiles(outputPath);
        
        for (Map.Entry<String, PersonStats> entry : personStats.entrySet()) {
            PersonStats stats = entry.getValue();
            PersonContributions contributions = personContributions.get(entry.getKey());
            
            structureBuilder.buildPersonProfile(
                    entry.getKey(), 
                    outputPath, 
                    sourceName, 
                    stats.questions, 
                    stats.answers, 
                    stats.notes,
                    contributions
            );
        }
        
        // Update topics with statistics
        updateTopicStatistics(analysisResult, outputPath, sourceName);
    }
    
    /**
     * Scan ALL Q/A/N files in KB and collect person stats (for incremental updates)
     */
    private Map<String, PersonStats> collectPersonStatsFromFiles(Path outputPath) throws IOException {
        Map<String, PersonStats> stats = new HashMap<>();
        
        // Scan questions directory
        Path questionsDir = outputPath.resolve("questions");
        if (Files.exists(questionsDir)) {
            try (Stream<Path> files = Files.list(questionsDir)) {
                files.filter(Files::isRegularFile)
                     .filter(p -> p.getFileName().toString().endsWith(".md"))
                     .forEach(file -> {
                         try {
                             String content = Files.readString(file);
                             String author = extractAuthorFromContent(content);
                             if (author != null) {
                                 stats.computeIfAbsent(author, k -> new PersonStats()).questions++;
                             }
                         } catch (IOException e) {
                             logger.warn("Failed to read question file: {}", file, e);
                         }
                     });
            }
        }
        
        // Scan answers directory
        Path answersDir = outputPath.resolve("answers");
        if (Files.exists(answersDir)) {
            try (Stream<Path> files = Files.list(answersDir)) {
                files.filter(Files::isRegularFile)
                     .filter(p -> p.getFileName().toString().endsWith(".md"))
                     .forEach(file -> {
                         try {
                             String content = Files.readString(file);
                             String author = extractAuthorFromContent(content);
                             if (author != null) {
                                 stats.computeIfAbsent(author, k -> new PersonStats()).answers++;
                             }
                         } catch (IOException e) {
                             logger.warn("Failed to read answer file: {}", file, e);
                         }
                     });
            }
        }
        
        // Scan notes directory
        Path notesDir = outputPath.resolve("notes");
        if (Files.exists(notesDir)) {
            try (Stream<Path> files = Files.list(notesDir)) {
                files.filter(Files::isRegularFile)
                     .filter(p -> p.getFileName().toString().endsWith(".md"))
                     .forEach(file -> {
                         try {
                             String content = Files.readString(file);
                             String author = extractAuthorFromContent(content);
                             if (author != null) {
                                 stats.computeIfAbsent(author, k -> new PersonStats()).notes++;
                             }
                         } catch (IOException e) {
                             logger.warn("Failed to read note file: {}", file, e);
                         }
                     });
            }
        }
        
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
     * Extract author name from frontmatter
     */
    private String extractAuthorFromContent(String content) {
        // Extract author from frontmatter: author: "Name"
        int authorIndex = content.indexOf("author:");
        if (authorIndex != -1) {
            int lineEnd = content.indexOf('\n', authorIndex);
            if (lineEnd != -1) {
                String authorLine = content.substring(authorIndex + 7, lineEnd).trim();
                return authorLine.replace("\"", "").trim();
            }
        }
        return null;
    }
    
    /**
     * Scan ALL Q/A/N files in KB and collect person contributions (for incremental updates)
     */
    private Map<String, PersonContributions> collectPersonContributionsFromFiles(Path outputPath) throws IOException {
        Map<String, PersonContributions> contributions = new HashMap<>();
        
        // Scan questions directory
        Path questionsDir = outputPath.resolve("questions");
        if (Files.exists(questionsDir)) {
            try (Stream<Path> files = Files.list(questionsDir)) {
                files.filter(Files::isRegularFile)
                     .filter(p -> p.getFileName().toString().endsWith(".md"))
                     .forEach(file -> {
                         try {
                             String content = Files.readString(file);
                             String author = extractAuthorFromContent(content);
                             String area = extractAreaFromContent(content);
                             String date = extractDateFromContent(content);
                             String id = file.getFileName().toString().replace(".md", "");
                             
                             if (author != null) {
                                 PersonContributions pc = contributions.computeIfAbsent(author, k -> new PersonContributions());
                                 if (area != null && !area.isEmpty()) {
                                     String areaSlug = structureBuilder.slugify(area);
                                     pc.getQuestions().add(new PersonContributions.ContributionItem(id, areaSlug, date));
                                 }
                             }
                         } catch (IOException e) {
                             logger.warn("Failed to read question file: {}", file, e);
                         }
                     });
            }
        }
        
        // Scan answers directory
        Path answersDir = outputPath.resolve("answers");
        if (Files.exists(answersDir)) {
            try (Stream<Path> files = Files.list(answersDir)) {
                files.filter(Files::isRegularFile)
                     .filter(p -> p.getFileName().toString().endsWith(".md"))
                     .forEach(file -> {
                         try {
                             String content = Files.readString(file);
                             String author = extractAuthorFromContent(content);
                             String area = extractAreaFromContent(content);
                             String date = extractDateFromContent(content);
                             String id = file.getFileName().toString().replace(".md", "");
                             
                             if (author != null) {
                                 PersonContributions pc = contributions.computeIfAbsent(author, k -> new PersonContributions());
                                 if (area != null && !area.isEmpty()) {
                                     String areaSlug = structureBuilder.slugify(area);
                                     pc.getAnswers().add(new PersonContributions.ContributionItem(id, areaSlug, date));
                                 }
                             }
                         } catch (IOException e) {
                             logger.warn("Failed to read answer file: {}", file, e);
                         }
                     });
            }
        }
        
        // Scan notes directory
        Path notesDir = outputPath.resolve("notes");
        if (Files.exists(notesDir)) {
            try (Stream<Path> files = Files.list(notesDir)) {
                files.filter(Files::isRegularFile)
                     .filter(p -> p.getFileName().toString().endsWith(".md"))
                     .forEach(file -> {
                         try {
                             String content = Files.readString(file);
                             String author = extractAuthorFromContent(content);
                             String area = extractAreaFromContent(content);
                             String date = extractDateFromContent(content);
                             String id = file.getFileName().toString().replace(".md", "");
                             
                             if (author != null) {
                                 PersonContributions pc = contributions.computeIfAbsent(author, k -> new PersonContributions());
                                 if (area != null && !area.isEmpty()) {
                                     String areaSlug = structureBuilder.slugify(area);
                                     pc.getNotes().add(new PersonContributions.ContributionItem(id, areaSlug, date));
                                 }
                             }
                         } catch (IOException e) {
                             logger.warn("Failed to read note file: {}", file, e);
                         }
                     });
            }
        }
        
        // Calculate topic contributions
        for (PersonContributions pc : contributions.values()) {
            Map<String, Integer> topicCounts = new HashMap<>();
            
            // Count contributions per topic (area)
            for (PersonContributions.ContributionItem item : pc.getQuestions()) {
                topicCounts.merge(item.getTopic(), 1, Integer::sum);
            }
            for (PersonContributions.ContributionItem item : pc.getAnswers()) {
                topicCounts.merge(item.getTopic(), 1, Integer::sum);
            }
            for (PersonContributions.ContributionItem item : pc.getNotes()) {
                topicCounts.merge(item.getTopic(), 1, Integer::sum);
            }
            
            // Convert Map to List<TopicContribution>
            List<PersonContributions.TopicContribution> topicList = topicCounts.entrySet().stream()
                    .map(e -> new PersonContributions.TopicContribution(e.getKey(), e.getValue()))
                    .collect(java.util.stream.Collectors.toList());
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
     * Extract area from frontmatter
     */
    private String extractAreaFromContent(String content) {
        int areaIndex = content.indexOf("area:");
        if (areaIndex != -1) {
            int lineEnd = content.indexOf('\n', areaIndex);
            if (lineEnd != -1) {
                String areaLine = content.substring(areaIndex + 5, lineEnd).trim();
                return areaLine.replace("\"", "").trim();
            }
        }
        return null;
    }
    
    /**
     * Extract date from frontmatter
     */
    private String extractDateFromContent(String content) {
        int dateIndex = content.indexOf("date:");
        if (dateIndex != -1) {
            int lineEnd = content.indexOf('\n', dateIndex);
            if (lineEnd != -1) {
                String dateLine = content.substring(dateIndex + 5, lineEnd).trim();
                String date = dateLine.replace("\"", "").trim();
                return date.length() >= 10 ? date.substring(0, 10) : date;
            }
        }
        return "";
    }
    
    @Deprecated
    private Map<String, PersonStats> collectPersonStats(AnalysisResult analysisResult) {
        Map<String, PersonStats> stats = new HashMap<>();
        
        // Count questions
        for (Question q : analysisResult.getQuestions()) {
            stats.computeIfAbsent(q.getAuthor(), k -> new PersonStats()).questions++;
        }
        
        // Count answers
        for (Answer a : analysisResult.getAnswers()) {
            stats.computeIfAbsent(a.getAuthor(), k -> new PersonStats()).answers++;
        }
        
        // Count notes
        for (Note n : analysisResult.getNotes()) {
            if (n.getAuthor() != null) {
                stats.computeIfAbsent(n.getAuthor(), k -> new PersonStats()).notes++;
            }
        }
        
        return stats;
    }
    
    private void performAggregation(AnalysisResult analysisResult, Path outputPath, 
                                    KBContext context) throws Exception {
        // Aggregate people profiles
        Set<String> people = new HashSet<>();
        analysisResult.getQuestions().forEach(q -> people.add(q.getAuthor()));
        analysisResult.getAnswers().forEach(a -> people.add(a.getAuthor()));
        analysisResult.getNotes().forEach(n -> {
            if (n.getAuthor() != null) people.add(n.getAuthor());
        });
        
        for (String person : people) {
            aggregatePerson(person, outputPath);
        }
        
        // Aggregate topics from current batch (detailed themes)
        Set<String> topics = new HashSet<>();
        analysisResult.getQuestions().forEach(q -> {
            if (q.getTopics() != null) topics.addAll(q.getTopics());
        });
        analysisResult.getAnswers().forEach(a -> {
            if (a.getTopics() != null) topics.addAll(a.getTopics());
        });
        analysisResult.getNotes().forEach(n -> {
            if (n.getTopics() != null) topics.addAll(n.getTopics());
        });
        
        // Aggregate areas from current batch (top-level domains)
        Set<String> areas = new HashSet<>();
        analysisResult.getQuestions().forEach(q -> {
            if (q.getArea() != null) areas.add(q.getArea());
        });
        analysisResult.getAnswers().forEach(a -> {
            if (a.getArea() != null) areas.add(a.getArea());
        });
        analysisResult.getNotes().forEach(n -> {
            if (n.getArea() != null) areas.add(n.getArea());
        });
        
        logger.info("Aggregating {} topics, {} areas, and {} people from current batch", 
                    topics.size(), areas.size(), people.size());
        
        // Aggregate topics (detailed themes)
        for (String topic : topics) {
            aggregateTopic(topic, outputPath);
        }
        
        // Note: Areas are aggregated through topic aggregation
        // as area files include links to topics
    }
    
    private void aggregatePerson(String personName, Path outputPath) throws Exception {
        String personId = structureBuilder.normalizePersonName(personName);
        Path personFile = outputPath.resolve("people").resolve(personId).resolve(personId + ".md");
        
        if (!Files.exists(personFile)) {
            return;
        }
        
        // Read person data
        String personContent = Files.readString(personFile);
        
        // Extract Q/A/N IDs from links and read their content
        StringBuilder fullContent = new StringBuilder();
        fullContent.append("# Person Profile: ").append(personName).append("\n\n");
        fullContent.append("## Metadata\n");
        fullContent.append(personContent.substring(0, personContent.indexOf("---", 5) + 3)).append("\n\n");
        
        // Extract Q/A/N IDs from links like [[../../questions/q_0001|q_0001]]
        java.util.regex.Pattern linkPattern = java.util.regex.Pattern.compile("\\[\\[.*?/([qan]_\\d+)\\|");
        java.util.regex.Matcher matcher = linkPattern.matcher(personContent);
        
        Set<String> processedIds = new HashSet<>();
        while (matcher.find()) {
            String itemId = matcher.group(1);
            if (processedIds.contains(itemId)) continue;
            processedIds.add(itemId);
            
            // Determine type and read file
            String type = itemId.startsWith("q_") ? "questions" : 
                         itemId.startsWith("a_") ? "answers" : "notes";
            Path itemFile = outputPath.resolve(type).resolve(itemId + ".md");
            
            if (Files.exists(itemFile)) {
                String itemContent = Files.readString(itemFile);
                fullContent.append("## ").append(itemId.toUpperCase()).append("\n");
                fullContent.append(itemContent).append("\n\n");
            }
        }
        
        // Prepare aggregation params
        AggregationParams params = new AggregationParams();
        params.setEntityType("person");
        params.setEntityId(personId);
        params.setKbPath(outputPath);
        
        Map<String, Object> entityData = new HashMap<>();
        entityData.put("name", personName);
        entityData.put("content", fullContent.toString());
        params.setEntityData(entityData);
        
        logger.info("Generating AI description for person: {} with {} contributions", personName, processedIds.size());
        
        // Run aggregation agent
        String description = aggregationAgent.run(params);
        
        // Update description file
        Path descFile = personFile.getParent().resolve(personId + "-desc.md");
        String descContent = "<!-- AI_CONTENT_START -->\n" + description + "\n<!-- AI_CONTENT_END -->\n";
        Files.writeString(descFile, descContent);
    }
    
    private void aggregateTopic(String topicName, Path outputPath) throws Exception {
        String topicId = structureBuilder.slugify(topicName);
        // NEW: flat structure - topics/topicId.md instead of topics/topicId/topicId.md
        Path topicFile = outputPath.resolve("topics").resolve(topicId + ".md");
        
        if (!Files.exists(topicFile)) {
            logger.warn("Topic file not found: {}", topicFile);
            return;
        }
        
        // Read topic data (including embedded Q/A/N)
        String topicContent = Files.readString(topicFile);
        
        // Extract embedded Q/A/N IDs and read their content
        StringBuilder fullContent = new StringBuilder();
        fullContent.append("# Topic: ").append(topicName).append("\n\n");
        fullContent.append("## Metadata\n");
        fullContent.append(topicContent.substring(0, topicContent.indexOf("---", 5) + 3)).append("\n\n");
        
        // Extract and read embedded items
        java.util.regex.Pattern embedPattern = java.util.regex.Pattern.compile("!\\[\\[([qan]_\\d+)\\]\\]");
        java.util.regex.Matcher matcher = embedPattern.matcher(topicContent);
        
        Set<String> processedIds = new HashSet<>();
        while (matcher.find()) {
            String itemId = matcher.group(1);
            if (processedIds.contains(itemId)) continue;
            processedIds.add(itemId);
            
            // Determine type and read file
            String type = itemId.startsWith("q_") ? "questions" : 
                         itemId.startsWith("a_") ? "answers" : "notes";
            Path itemFile = outputPath.resolve(type).resolve(itemId + ".md");
            
            if (Files.exists(itemFile)) {
                String itemContent = Files.readString(itemFile);
                fullContent.append("## ").append(itemId.toUpperCase()).append("\n");
                fullContent.append(itemContent).append("\n\n");
            }
        }
        
        // Prepare aggregation params
        AggregationParams params = new AggregationParams();
        params.setEntityType("topic");
        params.setEntityId(topicId);
        params.setKbPath(outputPath);
        
        Map<String, Object> entityData = new HashMap<>();
        entityData.put("title", topicName);
        entityData.put("content", fullContent.toString());
        params.setEntityData(entityData);
        
        logger.info("Generating AI description for topic: {} with {} embedded items", topicName, processedIds.size());
        
        // Run aggregation agent
        String description = aggregationAgent.run(params);
        
        // Update description file (flat structure)
        Path descFile = outputPath.resolve("topics").resolve(topicId + "-desc.md");
        String descContent = "<!-- AI_CONTENT_START -->\n" + description + "\n<!-- AI_CONTENT_END -->\n";
        Files.writeString(descFile, descContent);
        
        logger.debug("AI description generated for topic: {}", topicName);
    }
    
    private void aggregateTheme(String themeId, Path outputPath) throws Exception {
        // Themes are stored in topics directories, find all instances
        Path topicsDir = outputPath.resolve("topics");
        if (!Files.exists(topicsDir)) {
            return;
        }
        
        try (Stream<Path> topics = Files.list(topicsDir)) {
            topics.filter(Files::isDirectory).forEach(topicDir -> {
                Path themeFile = topicDir.resolve("themes").resolve(themeId + ".md");
                if (Files.exists(themeFile)) {
                    try {
                        String content = Files.readString(themeFile);
                        
                        AggregationParams params = new AggregationParams();
                        params.setEntityType("theme");
                        params.setEntityId(themeId);
                        params.setKbPath(outputPath);
                        
                        Map<String, Object> entityData = new HashMap<>();
                        entityData.put("id", themeId);
                        entityData.put("content", content);
                        params.setEntityData(entityData);
                        
                        // Run aggregation agent
                        String description = aggregationAgent.run(params);
                        
                        // Update theme content (replace AI_CONTENT section)
                        String updatedContent = content.replaceAll(
                                "<!-- AI_CONTENT_START -->.*?<!-- AI_CONTENT_END -->",
                                "<!-- AI_CONTENT_START -->\n" + description + "\n<!-- AI_CONTENT_END -->"
                        );
                        Files.writeString(themeFile, updatedContent);
                    } catch (Exception e) {
                        // Skip this theme
                    }
                }
            });
        }
    }
    
    private KBResult buildResult(AnalysisResult analysisResult, Path outputPath) {
        KBResult result = new KBResult();
        result.setSuccess(true);
        result.setMessage("Knowledge base built successfully");
        result.setQuestionsCount(analysisResult.getQuestions() != null ? analysisResult.getQuestions().size() : 0);
        result.setAnswersCount(analysisResult.getAnswers() != null ? analysisResult.getAnswers().size() : 0);
        result.setNotesCount(analysisResult.getNotes() != null ? analysisResult.getNotes().size() : 0);
        
        // Count people and topics from filesystem
        try {
            result.setPeopleCount(countDirectories(outputPath.resolve("people")));
            // Count topic files (now flat structure, not directories)
            result.setTopicsCount(countTopicFiles(outputPath.resolve("topics")));
        } catch (Exception e) {
            // Use 0 if can't count
        }
        
        return result;
    }
    
    private int countDirectories(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return 0;
        }
        
        try (Stream<Path> dirs = Files.list(dir)) {
            return (int) dirs.filter(Files::isDirectory).count();
        }
    }
    
    /**
     * Count topic files (excluding *-desc.md files)
     */
    private int countTopicFiles(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return 0;
        }
        
        try (Stream<Path> files = Files.list(dir)) {
            return (int) files
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".md"))
                    .filter(p -> !p.toString().endsWith("-desc.md"))
                    .count();
        }
    }
    
    /**
     * Analyze a single chunk
     */
    private AnalysisResult analyzeChunk(String text, String sourceName, KBContext context) throws Exception {
        AnalysisParams params = new AnalysisParams();
        params.setInputText(text);
        params.setSourceName(sourceName);
        params.setContext(context);
        
        return analysisAgent.run(params);
    }
    
    /**
     * Analyze multiple chunks and merge results using KBAnalysisResultMerger
     */
    private AnalysisResult analyzeAndMergeChunks(List<ChunkPreparation.Chunk> chunks, 
                                                  String sourceName, KBContext context) throws Exception {
        List<AnalysisResult> chunkResults = new ArrayList<>();
        
        // Process each chunk separately
        for (int i = 0; i < chunks.size(); i++) {
            logger.info("Processing chunk {} of {}", i + 1, chunks.size());
            AnalysisResult chunkResult = analyzeChunk(chunks.get(i).getText(), sourceName, context);
            chunkResults.add(chunkResult);
        }
        
        // Merge results using dedicated merger
        logger.info("Merging {} chunk results", chunkResults.size());
        return resultMerger.mergeResults(chunkResults);
    }
    
    /**
     * Update topic files with statistics
     */
    private void updateTopicStatistics(AnalysisResult analysisResult, Path outputPath, String sourceName) throws IOException {
        // Collect statistics for each topic
        Map<String, TopicStatistics> topicStats = new HashMap<>();
        
        // Count questions per area
        for (Question q : analysisResult.getQuestions()) {
            if (q.getArea() != null && !q.getArea().isEmpty()) {
                String areaId = structureBuilder.slugify(q.getArea());
                topicStats.computeIfAbsent(areaId, k -> new TopicStatistics()).questions++;
                if (q.getAuthor() != null) {
                    topicStats.get(areaId).contributors.add(q.getAuthor());
                }
            }
        }
        
        // Count answers per area
        for (Answer a : analysisResult.getAnswers()) {
            if (a.getArea() != null && !a.getArea().isEmpty()) {
                String areaId = structureBuilder.slugify(a.getArea());
                topicStats.computeIfAbsent(areaId, k -> new TopicStatistics()).answers++;
                if (a.getAuthor() != null) {
                    topicStats.get(areaId).contributors.add(a.getAuthor());
                }
            }
        }
        
        // Count notes per area
        for (Note n : analysisResult.getNotes()) {
            if (n.getArea() != null && !n.getArea().isEmpty()) {
                String areaId = structureBuilder.slugify(n.getArea());
                topicStats.computeIfAbsent(areaId, k -> new TopicStatistics()).notes++;
                if (n.getAuthor() != null) {
                    topicStats.get(areaId).contributors.add(n.getAuthor());
                }
            }
        }
        
        // Update each topic file with statistics
        for (Map.Entry<String, TopicStatistics> entry : topicStats.entrySet()) {
            String topicId = entry.getKey();
            TopicStatistics stats = entry.getValue();
            
            structureBuilder.updateTopicWithStats(
                    outputPath,
                    topicId,
                    stats.questions,
                    stats.answers,
                    stats.notes,
                    stats.themes,
                    new ArrayList<>(stats.themeIds),
                    new ArrayList<>(stats.contributors)
            );
        }
    }
    
    /**
     * Collect detailed contributions for each person
     */
    private Map<String, PersonContributions> collectPersonContributions(AnalysisResult analysisResult) {
        Map<String, PersonContributions> contributions = new HashMap<>();
        
        // Collect questions
        for (Question q : analysisResult.getQuestions()) {
            if (q.getAuthor() == null) continue;
            
            PersonContributions pc = contributions.computeIfAbsent(q.getAuthor(), k -> new PersonContributions());
            
            if (q.getId() != null && q.getArea() != null && !q.getArea().isEmpty()) {
                String area = structureBuilder.slugify(q.getArea());
                String date = q.getDate() != null && q.getDate().length() >= 10 ? q.getDate().substring(0, 10) : "";
                pc.getQuestions().add(new PersonContributions.ContributionItem(q.getId(), area, date));
            }
        }
        
        // Collect answers
        for (Answer a : analysisResult.getAnswers()) {
            if (a.getAuthor() == null) continue;
            
            PersonContributions pc = contributions.computeIfAbsent(a.getAuthor(), k -> new PersonContributions());
            
            if (a.getId() != null && a.getArea() != null && !a.getArea().isEmpty()) {
                String area = structureBuilder.slugify(a.getArea());
                String date = a.getDate() != null && a.getDate().length() >= 10 ? a.getDate().substring(0, 10) : "";
                pc.getAnswers().add(new PersonContributions.ContributionItem(a.getId(), area, date));
            }
        }
        
        // Collect notes
        for (Note n : analysisResult.getNotes()) {
            if (n.getAuthor() == null) continue;
            
            PersonContributions pc = contributions.computeIfAbsent(n.getAuthor(), k -> new PersonContributions());
            
            if (n.getId() != null && n.getArea() != null && !n.getArea().isEmpty()) {
                String area = structureBuilder.slugify(n.getArea());
                String date = n.getDate() != null && n.getDate().length() >= 10 ? n.getDate().substring(0, 10) : "";
                pc.getNotes().add(new PersonContributions.ContributionItem(n.getId(), area, date));
            }
        }
        
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
            for (Map.Entry<String, Integer> entry : topicCounts.entrySet()) {
                pc.getTopics().add(new PersonContributions.TopicContribution(entry.getKey(), entry.getValue()));
            }
        }
        
        return contributions;
    }
    
    // Helper class for collecting topic statistics
    private static class TopicStatistics {
        int questions = 0;
        int answers = 0;
        int notes = 0;
        int themes = 0;
        Set<String> themeIds = new HashSet<>();
        Set<String> contributors = new HashSet<>();
    }
    
    // Helper class for collecting person statistics
    private static class PersonStats {
        int questions = 0;
        int answers = 0;
        int notes = 0;
    }
}

