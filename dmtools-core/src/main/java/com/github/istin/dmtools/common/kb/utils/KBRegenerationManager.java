package com.github.istin.dmtools.common.kb.utils;

import com.github.istin.dmtools.common.kb.KBStructureBuilder;
import com.github.istin.dmtools.common.kb.model.*;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.stream.Stream;

/**
 * Handles regeneration of KB structure from existing filesystem data.
 */
public class KBRegenerationManager {

    private final KBFileParser fileParser;
    private final KBStructureBuilder structureBuilder;
    private final KBStructureManager structureManager;
    private final KBContextLoader contextLoader;

    public KBRegenerationManager(KBFileParser fileParser,
                                 KBStructureBuilder structureBuilder,
                                 KBStructureManager structureManager,
                                 KBContextLoader contextLoader) {
        this.fileParser = fileParser;
        this.structureBuilder = structureBuilder;
        this.structureManager = structureManager;
        this.contextLoader = contextLoader;
    }

    public KBResult regenerate(Path outputPath,
                               String sourceName,
                               Logger logger,
                               KBFileUtils fileUtils) throws Exception {
        if (logger != null) {
            logger.info("=".repeat(80));
            logger.info("REGENERATING KB STRUCTURE FROM EXISTING FILES");
            logger.info("=".repeat(80));
        }

        AnalysisResult combinedResult = new AnalysisResult();
        combinedResult.setQuestions(new ArrayList<>());
        combinedResult.setAnswers(new ArrayList<>());
        combinedResult.setNotes(new ArrayList<>());

        readQuestions(outputPath, combinedResult, logger);
        readAnswers(outputPath, combinedResult, logger);
        readNotes(outputPath, combinedResult, logger);

        if (logger != null) {
            logger.info("Rebuilding KB structure (topics, areas, people)...");
        }

        // Clear existing topics/areas to avoid residue
        contextLoader.clearDirectory(outputPath.resolve("topics"));
        contextLoader.clearDirectory(outputPath.resolve("areas"));

        structureBuilder.buildTopicFiles(combinedResult, outputPath, sourceName);
        structureBuilder.buildAreaStructure(combinedResult, outputPath, sourceName);

        structureManager.rebuildPeopleProfiles(outputPath, sourceName, logger);
        structureManager.generateIndexes(outputPath);

        if (logger != null) {
            logger.info("=".repeat(80));
            logger.info("KB STRUCTURE REGENERATION COMPLETE");
            logger.info("=".repeat(80));
        }

        return structureManager.buildResult(combinedResult, outputPath, fileUtils);
    }

    private void readQuestions(Path outputPath, AnalysisResult combinedResult, Logger logger) throws IOException {
        Path questionsDir = outputPath.resolve("questions");
        if (Files.exists(questionsDir)) {
            try (Stream<Path> files = Files.list(questionsDir)) {
                files.filter(p -> p.toString().endsWith(".md")).forEach(file -> {
                    Question question = fileParser.parseQuestionFromFile(file);
                    if (question != null) {
                        combinedResult.getQuestions().add(question);
                    }
                });
            }
        }
        if (logger != null) {
            logger.info("Read {} existing questions", combinedResult.getQuestions().size());
        }
    }

    private void readAnswers(Path outputPath, AnalysisResult combinedResult, Logger logger) throws IOException {
        Path answersDir = outputPath.resolve("answers");
        if (Files.exists(answersDir)) {
            try (Stream<Path> files = Files.list(answersDir)) {
                files.filter(p -> p.toString().endsWith(".md")).forEach(file -> {
                    Answer answer = fileParser.parseAnswerFromFile(file);
                    if (answer != null) {
                        combinedResult.getAnswers().add(answer);
                    }
                });
            }
        }
        if (logger != null) {
            logger.info("Read {} existing answers", combinedResult.getAnswers().size());
        }
    }

    private void readNotes(Path outputPath, AnalysisResult combinedResult, Logger logger) throws IOException {
        Path notesDir = outputPath.resolve("notes");
        if (Files.exists(notesDir)) {
            try (Stream<Path> files = Files.list(notesDir)) {
                files.filter(p -> p.toString().endsWith(".md")).forEach(file -> {
                    Note note = fileParser.parseNoteFromFile(file);
                    if (note != null) {
                        combinedResult.getNotes().add(note);
                    }
                });
            }
        }
        if (logger != null) {
            logger.info("Read {} existing notes", combinedResult.getNotes().size());
        }
    }
}
