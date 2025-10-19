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

    public KBAggregateOnlyService(KBAggregationHelper aggregationHelper,
                                  KBStructureManager structureManager) {
        this.aggregationHelper = aggregationHelper;
        this.structureManager = structureManager;
    }

    public KBResult aggregateExisting(Path outputPath,
                                      String extraInstructions,
                                      KBFileUtils fileUtils,
                                      Logger logger) throws Exception {
        Set<String> people = collectPeople(outputPath);
        if (logger != null) {
            logger.info("Found {} people in existing KB", people.size());
        }
        for (String personId : people) {
            aggregationHelper.aggregatePerson(personId, outputPath, extraInstructions);
        }

        Set<String> topics = collectTopics(outputPath);
        if (logger != null) {
            logger.info("Found {} topics in existing KB", topics.size());
        }
        for (String topicId : topics) {
            aggregationHelper.aggregateTopicById(topicId, outputPath, extraInstructions);
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
