package com.github.istin.dmtools.common.kb.utils;

import com.github.istin.dmtools.common.kb.KBStructureBuilder;
import com.github.istin.dmtools.common.kb.model.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class KBRegenerationManagerTest {

    private KBFileParser fileParser;
    private KBStructureBuilder structureBuilder;
    private KBStructureManager structureManager;
    private KBContextLoader contextLoader;
    private KBRegenerationManager regenerationManager;
    private KBFileUtils fileUtils;
    private Logger logger;

    @BeforeEach
    void setUp() {
        fileParser = mock(KBFileParser.class);
        structureBuilder = mock(KBStructureBuilder.class);
        structureManager = mock(KBStructureManager.class);
        contextLoader = mock(KBContextLoader.class);
        regenerationManager = new KBRegenerationManager(fileParser, structureBuilder, structureManager, contextLoader);
        fileUtils = mock(KBFileUtils.class);
        logger = LogManager.getLogger(KBRegenerationManagerTest.class);
    }

    @Test
    void regeneratesStructureFromExistingFiles() throws Exception {
        Path tempDir = Files.createTempDirectory("kb-regeneration-test");
        Path questionsDir = Files.createDirectories(tempDir.resolve("questions"));
        Path answersDir = Files.createDirectories(tempDir.resolve("answers"));
        Path notesDir = Files.createDirectories(tempDir.resolve("notes"));
        Files.writeString(questionsDir.resolve("q_1.md"), "question");
        Files.writeString(answersDir.resolve("a_1.md"), "answer");
        Files.writeString(notesDir.resolve("n_1.md"), "note");

        when(fileParser.parseQuestionFromFile(any())).thenReturn(new Question());
        when(fileParser.parseAnswerFromFile(any())).thenReturn(new Answer());
        when(fileParser.parseNoteFromFile(any())).thenReturn(new Note());
        when(structureManager.buildResult(any(), eq(tempDir), eq(fileUtils))).thenReturn(new KBResult());

        regenerationManager.regenerate(tempDir, "source", logger, fileUtils);

        verify(contextLoader).clearDirectory(tempDir.resolve("topics"));
        verify(contextLoader).clearDirectory(tempDir.resolve("areas"));
        verify(structureBuilder).buildTopicFiles(any(), eq(tempDir), eq("source"));
        verify(structureBuilder).buildAreaStructure(any(), eq(tempDir), eq("source"));
        verify(structureManager).rebuildPeopleProfiles(eq(tempDir), eq("source"), eq(logger));
        verify(structureManager).generateIndexes(tempDir);
    }

    @Test
    void returnsResultFromStructureManager() throws Exception {
        Path tempDir = Files.createTempDirectory("kb-regeneration-test-result");
        Files.createDirectories(tempDir.resolve("questions"));
        Files.createDirectories(tempDir.resolve("answers"));
        Files.createDirectories(tempDir.resolve("notes"));

        when(fileParser.parseQuestionFromFile(any())).thenReturn(new Question());
        when(fileParser.parseAnswerFromFile(any())).thenReturn(new Answer());
        when(fileParser.parseNoteFromFile(any())).thenReturn(new Note());

        KBResult expectedResult = new KBResult();
        expectedResult.setSuccess(true);
        when(structureManager.buildResult(any(), eq(tempDir), eq(fileUtils))).thenReturn(expectedResult);

        KBResult result = regenerationManager.regenerate(tempDir, "source", logger, fileUtils);

        assertEquals(expectedResult, result);
    }
}
