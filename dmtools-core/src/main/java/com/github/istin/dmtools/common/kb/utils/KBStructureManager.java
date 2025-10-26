package com.github.istin.dmtools.common.kb.utils;

import com.github.istin.dmtools.common.kb.KBStatistics;
import com.github.istin.dmtools.common.kb.KBStructureBuilder;
import com.github.istin.dmtools.common.kb.model.*;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * Encapsulates structure building steps previously handled in KBOrchestrator.
 */
public class KBStructureManager {

    private final KBStructureBuilder structureBuilder;
    private final PersonStatsCollector statsCollector;
    private final KBStatistics statistics;
    private final KBContextLoader contextLoader;

    public KBStructureManager(KBStructureBuilder structureBuilder,
                              PersonStatsCollector statsCollector,
                              KBStatistics statistics,
                              KBContextLoader contextLoader) {
        this.structureBuilder = structureBuilder;
        this.statsCollector = statsCollector;
        this.statistics = statistics;
        this.contextLoader = contextLoader;
    }

    public void buildStructure(AnalysisResult analysisResult,
                               Path outputPath,
                               String sourceName,
                               Map<String, PersonContributions> personContributions,
                               Logger logger) throws Exception {
        // Map temporary IDs (q_1, a_1, n_1) to permanent IDs (q_0001, a_0001, n_0001)
        KBContext idMappingContext = contextLoader.loadKBContext(outputPath);
        KBIdMapper idMapper = new KBIdMapper();
        idMapper.mapAndUpdateIds(analysisResult, idMappingContext);
        
        // CRITICAL: Collect person contributions AFTER ID mapping
        // This ensures we use permanent IDs (q_0001) not temporary IDs (q_1)
        if (personContributions == null) {
            personContributions = collectPersonContributionsFromAnalysis(analysisResult);
            if (logger != null) {
                logger.info("Collected contributions for {} people from current analysis (after ID mapping)", personContributions.size());
                if (!personContributions.isEmpty()) {
                    logger.debug("Person contributions collected: {}", personContributions.keySet());
                    for (Map.Entry<String, PersonContributions> entry : personContributions.entrySet()) {
                        logger.debug("  - {}: Q={}, A={}, N={}", entry.getKey(), 
                            entry.getValue().getQuestions().size(),
                            entry.getValue().getAnswers().size(),
                            entry.getValue().getNotes().size());
                    }
                }
            }
        }
        
        // Build answers first
        for (Answer answer : analysisResult.getAnswers()) {
            structureBuilder.buildAnswerFile(answer, outputPath, sourceName);
        }

        // Build questions with references
        for (Question question : analysisResult.getQuestions()) {
            structureBuilder.buildQuestionFile(question, outputPath, sourceName, analysisResult);
        }

        // Build notes
        for (Note note : analysisResult.getNotes()) {
            structureBuilder.buildNoteFile(note, outputPath, sourceName);
        }

        // Build topic & area structures
        structureBuilder.buildTopicFiles(analysisResult, outputPath, sourceName);
        structureBuilder.buildAreaStructure(analysisResult, outputPath, sourceName);

        // Track which people have contributions from the current analysis
        Set<String> peopleFromCurrentAnalysis = new HashSet<>();
        if (analysisResult.getQuestions() != null) {
            analysisResult.getQuestions().forEach(q -> {
                if (q.getAuthor() != null) {
                    peopleFromCurrentAnalysis.add(structureBuilder.normalizePersonName(q.getAuthor()));
                }
            });
        }
        if (analysisResult.getAnswers() != null) {
            analysisResult.getAnswers().forEach(a -> {
                if (a.getAuthor() != null) {
                    peopleFromCurrentAnalysis.add(structureBuilder.normalizePersonName(a.getAuthor()));
                }
            });
        }
        if (analysisResult.getNotes() != null) {
            analysisResult.getNotes().forEach(n -> {
                if (n.getAuthor() != null) {
                    peopleFromCurrentAnalysis.add(structureBuilder.normalizePersonName(n.getAuthor()));
                }
            });
        }
        
        if (logger != null && !peopleFromCurrentAnalysis.isEmpty()) {
            logger.debug("People from current analysis ({}): {}", peopleFromCurrentAnalysis.size(), peopleFromCurrentAnalysis);
        }

        Map<String, PersonStatsCollector.PersonStats> personStats;
        Map<String, PersonContributions> contributions;

        if (personContributions != null && !personContributions.isEmpty()) {
            // Reload context after creating files to get updated question/answer/note lists
            KBContext context = contextLoader.loadKBContext(outputPath);
            personStats = new HashMap<>();
            for (String person : context.getExistingPeople()) {
                // Normalize person name to match peopleFromCurrentAnalysis format
                String normalizedPerson = structureBuilder.normalizePersonName(person);
                personStats.putIfAbsent(normalizedPerson, new PersonStatsCollector.PersonStats());
            }
            
            // IMPORTANT: Also add people from current analysis (personContributions)
            // On first run, context.getExistingPeople() is empty, so we need to add people from current analysis
            for (String person : personContributions.keySet()) {
                personStats.putIfAbsent(person, new PersonStatsCollector.PersonStats());
            }

            Map<String, List<KBContext.QuestionSummary>> questionsByPerson = new HashMap<>();
            for (KBContext.QuestionSummary summary : context.getExistingQuestions()) {
                String author = summary.getAuthor();
                if (author != null) {
                    // Normalize author name using the same method as peopleFromCurrentAnalysis
                    String normalizedAuthor = structureBuilder.normalizePersonName(author);
                    questionsByPerson.computeIfAbsent(normalizedAuthor, key -> new ArrayList<>()).add(summary);
                }
            }

            for (Map.Entry<String, PersonStatsCollector.PersonStats> entry : personStats.entrySet()) {
                PersonStatsCollector.PersonStats stats = entry.getValue();
                List<KBContext.QuestionSummary> authoredQuestions = questionsByPerson.get(entry.getKey());
                if (authoredQuestions != null) {
                    stats.questions = (int) authoredQuestions.stream()
                            .map(KBContext.QuestionSummary::getId)
                            .filter(id -> Files.exists(outputPath.resolve("questions").resolve(id + ".md")))
                            .count();
                }
            }

            // CRITICAL: Merge personContributions from current analysis with existing contributions from files
            // Otherwise, contributions from previous sessions will be lost
            Map<String, PersonContributions> existingContributions = statsCollector.collectPersonContributionsFromFiles(outputPath);
            contributions = new HashMap<>(existingContributions);
            
            // Merge new contributions into existing ones
            for (Map.Entry<String, PersonContributions> entry : personContributions.entrySet()) {
                String person = entry.getKey();
                PersonContributions newContribs = entry.getValue();
                
                PersonContributions merged = contributions.computeIfAbsent(person, k -> new PersonContributions());
                
                // Merge questions (avoid duplicates by ID)
                Set<String> existingQuestionIds = merged.getQuestions().stream()
                    .map(PersonContributions.ContributionItem::getId)
                    .collect(java.util.stream.Collectors.toSet());
                for (PersonContributions.ContributionItem q : newContribs.getQuestions()) {
                    if (!existingQuestionIds.contains(q.getId())) {
                        merged.getQuestions().add(q);
                    }
                }
                
                // Merge answers (avoid duplicates by ID)
                Set<String> existingAnswerIds = merged.getAnswers().stream()
                    .map(PersonContributions.ContributionItem::getId)
                    .collect(java.util.stream.Collectors.toSet());
                for (PersonContributions.ContributionItem a : newContribs.getAnswers()) {
                    if (!existingAnswerIds.contains(a.getId())) {
                        merged.getAnswers().add(a);
                    }
                }
                
                // Merge notes (avoid duplicates by ID)
                Set<String> existingNoteIds = merged.getNotes().stream()
                    .map(PersonContributions.ContributionItem::getId)
                    .collect(java.util.stream.Collectors.toSet());
                for (PersonContributions.ContributionItem n : newContribs.getNotes()) {
                    if (!existingNoteIds.contains(n.getId())) {
                        merged.getNotes().add(n);
                    }
                }
                
                // NOTE: Topics will be recalculated from files after merge, not merged here
                // because the topic counts may be incorrect (based on first topic only in some cases)
            }
            
            // CRITICAL: Recalculate topic contributions from ALL files (current + existing)
            // This ensures we count ALL topics per Q/A/N, not just the first one
            recalculateTopicContributionsFromFiles(contributions, outputPath);
            
            if (logger != null) {
                logger.debug("Merged contributions: size={}, keys={}", contributions.size(), contributions.keySet());
                for (Map.Entry<String, PersonContributions> entry : contributions.entrySet()) {
                    logger.debug("  - {}: Q={}, A={}, N={}", entry.getKey(),
                        entry.getValue().getQuestions().size(),
                        entry.getValue().getAnswers().size(),
                        entry.getValue().getNotes().size());
                }
            }

            Path answersDir = outputPath.resolve("answers");
            Path notesDir = outputPath.resolve("notes");

            if (Files.exists(answersDir)) {
                try (Stream<Path> files = Files.list(answersDir)) {
                    files.filter(Files::isRegularFile)
                            .forEach(path -> {
                                try {
                                    String content = Files.readString(path);
                                    String author = statsCollector.extractAuthor(content);
                                    if (author != null) {
                                        // Normalize author name using the same method as peopleFromCurrentAnalysis
                                        String normalizedAuthor = structureBuilder.normalizePersonName(author);
                                        PersonStatsCollector.PersonStats stats = personStats.computeIfAbsent(normalizedAuthor, k -> new PersonStatsCollector.PersonStats());
                                        stats.answers++;
                                    }
                                } catch (java.io.IOException ignored) {
                                }
                            });
                }
            }

            if (Files.exists(notesDir)) {
                try (Stream<Path> files = Files.list(notesDir)) {
                    files.filter(Files::isRegularFile)
                            .forEach(path -> {
                                try {
                                    String content = Files.readString(path);
                                    String author = statsCollector.extractAuthor(content);
                                    if (author != null) {
                                        // Normalize author name using the same method as peopleFromCurrentAnalysis
                                        String normalizedAuthor = structureBuilder.normalizePersonName(author);
                                        PersonStatsCollector.PersonStats stats = personStats.computeIfAbsent(normalizedAuthor, k -> new PersonStatsCollector.PersonStats());
                                        stats.notes++;
                                    }
                                } catch (java.io.IOException ignored) {
                                }
                            });
                }
            }
        } else {
            personStats = statsCollector.collectPersonStatsFromFiles(outputPath);
            contributions = statsCollector.collectPersonContributionsFromFiles(outputPath);
        }

        if (logger != null) {
            logger.debug("personStats keys: {}", personStats.keySet());
            logger.debug("contributions keys: {}", contributions.keySet());
        }
        
        for (Map.Entry<String, PersonStatsCollector.PersonStats> entry : personStats.entrySet()) {
            PersonStatsCollector.PersonStats stats = entry.getValue();
            PersonContributions contribution = contributions.get(entry.getKey());
            
            if (logger != null) {
                if (contribution != null) {
                    logger.debug("Person '{}': contribution found - Q={}, A={}, N={}", 
                        entry.getKey(), 
                        contribution.getQuestions().size(), 
                        contribution.getAnswers().size(), 
                        contribution.getNotes().size());
                } else {
                    logger.warn("No contribution found for person '{}' (key in contributions: {})", 
                        entry.getKey(), contributions.containsKey(entry.getKey()));
                    logger.debug("Available contribution keys: {}", contributions.keySet());
                }
            }
            
            // Only pass sourceName if this person has contributions from current analysis
            boolean inCurrentAnalysis = peopleFromCurrentAnalysis.contains(entry.getKey());
            String sourceToAdd = inCurrentAnalysis ? sourceName : null;
            
            if (logger != null) {
                logger.debug("Processing person '{}': inCurrentAnalysis={}, sourceToAdd={}, stats=[Q:{}, A:{}, N:{}], key='{}', peopleSet contains key={}",
                        entry.getKey(),
                        inCurrentAnalysis,
                        sourceToAdd,
                        stats.questions,
                        stats.answers,
                        stats.notes,
                        entry.getKey(),
                        peopleFromCurrentAnalysis.contains(entry.getKey()));
                if (!inCurrentAnalysis && entry.getKey().contains("Tarasevich")) {
                    logger.debug("DEBUG: peopleFromCurrentAnalysis={}", peopleFromCurrentAnalysis);
                    logger.debug("DEBUG: Looking for key='{}' in set", entry.getKey());
                }
            }
            
            structureBuilder.buildPersonProfile(
                    entry.getKey(),
                    outputPath,
                    sourceToAdd,
                    stats.questions,
                    stats.answers,
                    stats.notes,
                    contribution
            );
        }

        if (logger != null) {
            logger.info("Built {} person profiles.", personStats.size());
        }

        updateTopicStatistics(analysisResult, outputPath, logger);
    }

    public void rebuildPeopleProfiles(Path outputPath,
                                      String sourceName,
                                      Logger logger) throws Exception {
        Map<String, PersonStatsCollector.PersonStats> personStats =
                statsCollector.collectPersonStatsFromFiles(outputPath);
        Map<String, PersonContributions> personContributions =
                statsCollector.collectPersonContributionsFromFiles(outputPath);

        for (Map.Entry<String, PersonStatsCollector.PersonStats> entry : personStats.entrySet()) {
            PersonStatsCollector.PersonStats stats = entry.getValue();
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

        if (logger != null) {
            logger.info("Rebuilt {} person profiles.", personStats.size());
        }
    }

    public void generateIndexes(Path outputPath) throws Exception {
        statistics.generateStatistics(outputPath);
        structureBuilder.generatePeopleIndex(outputPath);
    }

    public KBResult buildResult(AnalysisResult analysisResult,
                                Path outputPath,
                                KBFileUtils fileUtils) throws Exception {
        KBResult result = new KBResult();
        result.setSuccess(true);

        if (analysisResult != null) {
            result.setMessage("Knowledge base built successfully");
            result.setQuestionsCount(analysisResult.getQuestions() != null ? analysisResult.getQuestions().size() : 0);
            result.setAnswersCount(analysisResult.getAnswers() != null ? analysisResult.getAnswers().size() : 0);
            result.setNotesCount(analysisResult.getNotes() != null ? analysisResult.getNotes().size() : 0);
        } else {
            result.setMessage("KB processing completed.");
        }

        result.setPeopleCount(fileUtils.countDirectories(outputPath.resolve("people")));
        result.setTopicsCount(fileUtils.countFiles(outputPath.resolve("topics"),
                file -> file.toString().endsWith(".md") && !file.toString().endsWith("-desc.md")));
        result.setAreasCount(fileUtils.countDirectories(outputPath.resolve("areas")));
        result.setQuestionsCount(fileUtils.countFiles(outputPath.resolve("questions"),
                file -> file.getFileName().toString().endsWith(".md")));
        result.setAnswersCount(fileUtils.countFiles(outputPath.resolve("answers"),
                file -> file.getFileName().toString().endsWith(".md")));
        result.setNotesCount(fileUtils.countFiles(outputPath.resolve("notes"),
                file -> file.getFileName().toString().endsWith(".md")));

        return result;
    }

    private void updateTopicStatistics(AnalysisResult analysisResult,
                                       Path outputPath,
                                       Logger logger) throws Exception {
        Map<String, TopicStatistics> topicStats = new HashMap<>();

        for (Question q : analysisResult.getQuestions()) {
            if (q.getTopics() != null) {
                for (String topic : q.getTopics()) {
                    TopicStatistics stats = topicStats.computeIfAbsent(topic, k -> new TopicStatistics());
                    stats.questions++;
                    if (q.getAuthor() != null) {
                        stats.contributors.add(q.getAuthor());
                    }
                }
            }
        }

        for (Answer a : analysisResult.getAnswers()) {
            if (a.getTopics() != null) {
                for (String topic : a.getTopics()) {
                    TopicStatistics stats = topicStats.computeIfAbsent(topic, k -> new TopicStatistics());
                    stats.answers++;
                    if (a.getAuthor() != null) {
                        stats.contributors.add(a.getAuthor());
                    }
                }
            }
        }

        for (Note n : analysisResult.getNotes()) {
            if (n.getTopics() != null) {
                for (String topic : n.getTopics()) {
                    TopicStatistics stats = topicStats.computeIfAbsent(topic, k -> new TopicStatistics());
                    stats.notes++;
                    if (n.getAuthor() != null) {
                        stats.contributors.add(n.getAuthor());
                    }
                }
            }
        }

        for (Map.Entry<String, TopicStatistics> entry : topicStats.entrySet()) {
            TopicStatistics stats = entry.getValue();
            structureBuilder.updateTopicWithStats(
                    outputPath,
                    entry.getKey(),
                    stats.questions,
                    stats.answers,
                    stats.notes,
                    new ArrayList<>(stats.contributors)
            );
        }

        if (logger != null) {
            logger.info("Updated statistics for {} topics", topicStats.size());
        }
    }

    private static class TopicStatistics {
        int questions = 0;
        int answers = 0;
        int notes = 0;
        Set<String> contributors = new HashSet<>();
    }
    
    /**
     * Collect person contributions from analysis result (using already-mapped IDs)
     * CRITICAL: This must be called AFTER ID mapping to use permanent IDs (q_0001) not temporary (q_1)
     */
    private Map<String, PersonContributions> collectPersonContributionsFromAnalysis(AnalysisResult analysisResult) {
        Map<String, PersonContributions> contributions = new HashMap<>();
        
        // Collect from questions
        if (analysisResult.getQuestions() != null) {
            for (Question q : analysisResult.getQuestions()) {
                if (q.getAuthor() != null && q.getId() != null) {
                    String normalizedAuthor = structureBuilder.normalizePersonName(q.getAuthor());
                    PersonContributions pc = contributions.computeIfAbsent(normalizedAuthor, k -> new PersonContributions());
                    
                    // Get first topic as primary topic for display
                    String topic = (q.getTopics() != null && !q.getTopics().isEmpty()) ? q.getTopics().get(0) : "general";
                    String date = (q.getDate() != null) ? q.getDate() : "unknown";
                    
                    pc.getQuestions().add(new PersonContributions.ContributionItem(q.getId(), topic, date));
                }
            }
        }
        
        // Collect from answers
        if (analysisResult.getAnswers() != null) {
            for (Answer a : analysisResult.getAnswers()) {
                if (a.getAuthor() != null && a.getId() != null) {
                    String normalizedAuthor = structureBuilder.normalizePersonName(a.getAuthor());
                    PersonContributions pc = contributions.computeIfAbsent(normalizedAuthor, k -> new PersonContributions());
                    
                    String topic = (a.getTopics() != null && !a.getTopics().isEmpty()) ? a.getTopics().get(0) : "general";
                    String date = (a.getDate() != null) ? a.getDate() : "unknown";
                    
                    pc.getAnswers().add(new PersonContributions.ContributionItem(a.getId(), topic, date));
                }
            }
        }
        
        // Collect from notes
        if (analysisResult.getNotes() != null) {
            for (Note n : analysisResult.getNotes()) {
                if (n.getAuthor() != null && n.getId() != null) {
                    String normalizedAuthor = structureBuilder.normalizePersonName(n.getAuthor());
                    PersonContributions pc = contributions.computeIfAbsent(normalizedAuthor, k -> new PersonContributions());
                    
                    String topic = (n.getTopics() != null && !n.getTopics().isEmpty()) ? n.getTopics().get(0) : "general";
                    String date = (n.getDate() != null) ? n.getDate() : "unknown";
                    
                    pc.getNotes().add(new PersonContributions.ContributionItem(n.getId(), topic, date));
                }
            }
        }
        
        // Calculate topic contributions from analysisResult directly to count ALL topics per Q/A/N
        // CRITICAL: Don't use ContributionItem.topic which only stores the first topic!
        // A question with 2 topics should contribute to BOTH topic counts.
        calculateTopicContributionsFromAnalysis(contributions, analysisResult);
        
        return contributions;
    }
    
    /**
     * Recalculate topic contributions by reading ALL Q/A/N files from disk.
     * CRITICAL: Use this after merging contributions to ensure topic counts are correct.
     * Counts ALL topics per Q/A/N (not just the first one).
     * 
     * Example: q_0006 has topics ["enterprise-jira-limitations", "workflow-management"]
     * Should contribute 1 to EACH topic, not just the first one.
     */
    private void recalculateTopicContributionsFromFiles(
            Map<String, PersonContributions> contributions,
            Path outputPath) {
        
        // Initialize topic counts per person
        Map<String, Map<String, Integer>> personTopicCounts = new HashMap<>();
        for (String person : contributions.keySet()) {
            personTopicCounts.put(person, new HashMap<>());
        }
        
        // Read questions directory
        Path questionsDir = outputPath.resolve("questions");
        if (Files.exists(questionsDir)) {
            try (java.util.stream.Stream<Path> files = Files.list(questionsDir)) {
                files.filter(Files::isRegularFile)
                     .filter(p -> p.getFileName().toString().endsWith(".md"))
                     .forEach(file -> {
                         try {
                             String content = Files.readString(file);
                             String author = statsCollector.getFileParser().extractAuthor(content);
                             List<String> topics = statsCollector.getFileParser().extractTopics(content);
                             
                             if (author != null && topics != null && !topics.isEmpty()) {
                                 String normalizedAuthor = structureBuilder.normalizePersonName(author);
                                 Map<String, Integer> topicCounts = personTopicCounts.get(normalizedAuthor);
                                 if (topicCounts != null) {
                                     for (String topic : topics) {
                                         String topicSlug = structureBuilder.slugify(topic);
                                         topicCounts.merge(topicSlug, 1, Integer::sum);
                                     }
                                 }
                             }
                         } catch (IOException e) {
                             // Skip files that can't be read
                         }
                     });
            } catch (IOException e) {
                // Skip if directory doesn't exist or can't be read
            }
        }
        
        // Read answers directory
        Path answersDir = outputPath.resolve("answers");
        if (Files.exists(answersDir)) {
            try (java.util.stream.Stream<Path> files = Files.list(answersDir)) {
                files.filter(Files::isRegularFile)
                     .filter(p -> p.getFileName().toString().endsWith(".md"))
                     .forEach(file -> {
                         try {
                             String content = Files.readString(file);
                             String author = statsCollector.getFileParser().extractAuthor(content);
                             List<String> topics = statsCollector.getFileParser().extractTopics(content);
                             
                             if (author != null && topics != null && !topics.isEmpty()) {
                                 String normalizedAuthor = structureBuilder.normalizePersonName(author);
                                 Map<String, Integer> topicCounts = personTopicCounts.get(normalizedAuthor);
                                 if (topicCounts != null) {
                                     for (String topic : topics) {
                                         String topicSlug = structureBuilder.slugify(topic);
                                         topicCounts.merge(topicSlug, 1, Integer::sum);
                                     }
                                 }
                             }
                         } catch (IOException e) {
                             // Skip files that can't be read
                         }
                     });
            } catch (IOException e) {
                // Skip if directory doesn't exist or can't be read
            }
        }
        
        // Read notes directory
        Path notesDir = outputPath.resolve("notes");
        if (Files.exists(notesDir)) {
            try (java.util.stream.Stream<Path> files = Files.list(notesDir)) {
                files.filter(Files::isRegularFile)
                     .filter(p -> p.getFileName().toString().endsWith(".md"))
                     .forEach(file -> {
                         try {
                             String content = Files.readString(file);
                             String author = statsCollector.getFileParser().extractAuthor(content);
                             List<String> topics = statsCollector.getFileParser().extractTopics(content);
                             
                             if (author != null && topics != null && !topics.isEmpty()) {
                                 String normalizedAuthor = structureBuilder.normalizePersonName(author);
                                 Map<String, Integer> topicCounts = personTopicCounts.get(normalizedAuthor);
                                 if (topicCounts != null) {
                                     for (String topic : topics) {
                                         String topicSlug = structureBuilder.slugify(topic);
                                         topicCounts.merge(topicSlug, 1, Integer::sum);
                                     }
                                 }
                             }
                         } catch (IOException e) {
                             // Skip files that can't be read
                         }
                     });
            } catch (IOException e) {
                // Skip if directory doesn't exist or can't be read
            }
        }
        
        // Update PersonContributions with recalculated topic counts
        for (Map.Entry<String, PersonContributions> entry : contributions.entrySet()) {
            Map<String, Integer> topicCounts = personTopicCounts.get(entry.getKey());
            if (topicCounts != null && !topicCounts.isEmpty()) {
                List<PersonContributions.TopicContribution> topicList = topicCounts.entrySet().stream()
                        .map(e -> new PersonContributions.TopicContribution(e.getKey(), e.getValue()))
                        .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
                entry.getValue().setTopics(topicList);
            }
        }
    }
    
    /**
     * Calculate topic contributions from analysis result.
     * CRITICAL: Goes directly to analysisResult to count ALL topics per Q/A/N.
     * ContributionItem.topic only stores the first topic, which loses multi-topic information.
     * 
     * Example: q_0006 has topics ["enterprise-jira-limitations", "workflow-management"]
     * Should contribute 1 to EACH topic, not just the first one.
     */
    private void calculateTopicContributionsFromAnalysis(
            Map<String, PersonContributions> contributions,
            AnalysisResult analysisResult) {
        
        // Initialize topic counts per person
        Map<String, Map<String, Integer>> personTopicCounts = new HashMap<>();
        for (String person : contributions.keySet()) {
            personTopicCounts.put(person, new HashMap<>());
        }
        
        // Count from questions (using ALL topics per question)
        if (analysisResult.getQuestions() != null) {
            for (Question q : analysisResult.getQuestions()) {
                if (q.getAuthor() != null && q.getTopics() != null) {
                    String normalizedAuthor = structureBuilder.normalizePersonName(q.getAuthor());
                    Map<String, Integer> topicCounts = personTopicCounts.get(normalizedAuthor);
                    if (topicCounts != null) {
                        for (String topic : q.getTopics()) {
                            String topicSlug = structureBuilder.slugify(topic);
                            topicCounts.merge(topicSlug, 1, Integer::sum);
                        }
                    }
                }
            }
        }
        
        // Count from answers (using ALL topics per answer)
        if (analysisResult.getAnswers() != null) {
            for (Answer a : analysisResult.getAnswers()) {
                if (a.getAuthor() != null && a.getTopics() != null) {
                    String normalizedAuthor = structureBuilder.normalizePersonName(a.getAuthor());
                    Map<String, Integer> topicCounts = personTopicCounts.get(normalizedAuthor);
                    if (topicCounts != null) {
                        for (String topic : a.getTopics()) {
                            String topicSlug = structureBuilder.slugify(topic);
                            topicCounts.merge(topicSlug, 1, Integer::sum);
                        }
                    }
                }
            }
        }
        
        // Count from notes (using ALL topics per note)
        if (analysisResult.getNotes() != null) {
            for (Note n : analysisResult.getNotes()) {
                if (n.getAuthor() != null && n.getTopics() != null) {
                    String normalizedAuthor = structureBuilder.normalizePersonName(n.getAuthor());
                    Map<String, Integer> topicCounts = personTopicCounts.get(normalizedAuthor);
                    if (topicCounts != null) {
                        for (String topic : n.getTopics()) {
                            String topicSlug = structureBuilder.slugify(topic);
                            topicCounts.merge(topicSlug, 1, Integer::sum);
                        }
                    }
                }
            }
        }
        
        // Convert to TopicContribution lists
        for (Map.Entry<String, PersonContributions> entry : contributions.entrySet()) {
            Map<String, Integer> topicCounts = personTopicCounts.get(entry.getKey());
            if (topicCounts != null && !topicCounts.isEmpty()) {
                List<PersonContributions.TopicContribution> topicList = topicCounts.entrySet().stream()
                        .map(e -> new PersonContributions.TopicContribution(e.getKey(), e.getValue()))
                        .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
                entry.getValue().setTopics(topicList);
            }
        }
    }
}
