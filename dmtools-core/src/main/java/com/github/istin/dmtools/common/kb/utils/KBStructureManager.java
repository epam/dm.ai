package com.github.istin.dmtools.common.kb.utils;

import com.github.istin.dmtools.common.kb.KBStatistics;
import com.github.istin.dmtools.common.kb.KBStructureBuilder;
import com.github.istin.dmtools.common.kb.model.*;
import org.apache.logging.log4j.Logger;

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

        Map<String, PersonStatsCollector.PersonStats> personStats;
        Map<String, PersonContributions> contributions;

        if (personContributions != null && !personContributions.isEmpty()) {
            // Reload context after creating files to get updated question/answer/note lists
            KBContext context = contextLoader.loadKBContext(outputPath);
            personStats = new HashMap<>();
            for (String person : context.getExistingPeople()) {
                personStats.putIfAbsent(person, new PersonStatsCollector.PersonStats());
            }

            Map<String, List<KBContext.QuestionSummary>> questionsByPerson = new HashMap<>();
            for (KBContext.QuestionSummary summary : context.getExistingQuestions()) {
                String author = summary.getAuthor();
                if (author != null) {
                    // Normalize author name to match directory format (replace spaces with underscores)
                    String normalizedAuthor = author.replace(" ", "_");
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

            contributions = personContributions;

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
                                        // Normalize author name to match directory format
                                        String normalizedAuthor = author.replace(" ", "_");
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
                                        // Normalize author name to match directory format
                                        String normalizedAuthor = author.replace(" ", "_");
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

        for (Map.Entry<String, PersonStatsCollector.PersonStats> entry : personStats.entrySet()) {
            PersonStatsCollector.PersonStats stats = entry.getValue();
            PersonContributions contribution = contributions.get(entry.getKey());
            structureBuilder.buildPersonProfile(
                    entry.getKey(),
                    outputPath,
                    sourceName,
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
}
