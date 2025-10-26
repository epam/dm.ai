package com.github.istin.dmtools.common.kb.utils;

import com.github.istin.dmtools.common.kb.model.KBResult;
import org.apache.logging.log4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Executes AGGREGATE_ONLY mode by generating AI descriptions for existing people and topics.
 */
public class KBAggregateOnlyService {

    private final KBAggregationHelper aggregationHelper;
    private final KBStructureManager structureManager;
    private final KBSmartRegenerationHelper smartRegenerationHelper;

    public KBAggregateOnlyService(KBAggregationHelper aggregationHelper,
                                  KBStructureManager structureManager) {
        this.aggregationHelper = aggregationHelper;
        this.structureManager = structureManager;
        this.smartRegenerationHelper = new KBSmartRegenerationHelper();
    }

    public KBResult aggregateExisting(Path outputPath,
                                      String extraInstructions,
                                      KBFileUtils fileUtils,
                                      Logger logger) throws Exception {
        return aggregateExisting(outputPath, extraInstructions, fileUtils, logger, false);
    }

    public KBResult aggregateExisting(Path outputPath,
                                      String extraInstructions,
                                      KBFileUtils fileUtils,
                                      Logger logger,
                                      boolean smartMode) throws Exception {
        Set<String> people = collectPeople(outputPath);
        if (logger != null) {
            logger.info("Found {} people in existing KB", people.size());
        }
        
        int skippedPeople = 0;
        int regeneratedPeople = 0;
        
        for (String personId : people) {
            if (smartMode) {
                Path personFile = outputPath.resolve("people").resolve(personId).resolve(personId + ".md");
                Path descFile = outputPath.resolve("people").resolve(personId).resolve(personId + "-desc.md");
                
                if (!smartRegenerationHelper.needsRegeneration(descFile, personFile, outputPath)) {
                    if (logger != null) {
                        logger.debug("Skipped person '{}' (no changes in Q/A/N)", personId);
                    }
                    skippedPeople++;
                    continue;
                }
                
                if (logger != null) {
                    logger.info("Regenerating person '{}' (Q/A/N modified)", personId);
                }
                regeneratedPeople++;
            }
            
            aggregationHelper.aggregatePerson(personId, outputPath, extraInstructions);
        }
        
        if (smartMode && logger != null) {
            logger.info("People: regenerated {}, skipped {}", regeneratedPeople, skippedPeople);
        }

        Set<String> topics = collectTopics(outputPath);
        if (logger != null) {
            logger.info("Found {} topics in existing KB", topics.size());
        }
        
        int skippedTopics = 0;
        int regeneratedTopics = 0;
        
        for (String topicId : topics) {
            if (smartMode) {
                Path topicFile = outputPath.resolve("topics").resolve(topicId + ".md");
                Path descFile = outputPath.resolve("topics").resolve(topicId + "-desc.md");
                
                if (!smartRegenerationHelper.needsRegeneration(descFile, topicFile, outputPath)) {
                    if (logger != null) {
                        logger.debug("Skipped topic '{}' (no changes in Q/A/N)", topicId);
                    }
                    skippedTopics++;
                    continue;
                }
                
                if (logger != null) {
                    logger.info("Regenerating topic '{}' (Q/A/N modified)", topicId);
                }
                regeneratedTopics++;
            }
            
            aggregationHelper.aggregateTopicById(topicId, outputPath, extraInstructions);
        }
        
        if (smartMode && logger != null) {
            logger.info("Topics: regenerated {}, skipped {}", regeneratedTopics, skippedTopics);
        }

        structureManager.generateIndexes(outputPath);
        return structureManager.buildResult(null, outputPath, fileUtils);
    }

    private Set<String> collectPeople(Path outputPath) throws Exception {
        Set<String> people = new LinkedHashSet<>();
        Path peopleDir = outputPath.resolve("people");
        if (Files.exists(peopleDir)) {
            try (Stream<Path> dirs = Files.list(peopleDir)) {
                dirs.filter(Files::isDirectory)
                        .sorted()
                        .forEach(dir -> people.add(dir.getFileName().toString()));
            }
        }
        return people;
    }

    private Set<String> collectTopics(Path outputPath) throws Exception {
        Set<String> topics = new LinkedHashSet<>();
        Path topicsDir = outputPath.resolve("topics");
        if (Files.exists(topicsDir)) {
            try (Stream<Path> files = Files.list(topicsDir)) {
                files.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().endsWith(".md"))
                        .filter(p -> !p.getFileName().toString().endsWith("-desc.md"))
                        .sorted()
                        .forEach(file -> topics.add(file.getFileName().toString().replace(".md", "")));
            }
        }
        return topics;
    }
}
