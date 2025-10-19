package com.github.istin.dmtools.common.kb.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class KBRollbackManagerTest {

    private KBRollbackManager rollbackManager;
    private Logger logger;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        rollbackManager = new KBRollbackManager();
        logger = LogManager.getLogger(KBRollbackManagerTest.class);
    }

    @Test
    void testRollbackDeletesExistingFiles() throws IOException {
        Path file1 = tempDir.resolve("file1.txt");
        Path file2 = tempDir.resolve("file2.txt");
        Files.writeString(file1, "test1");
        Files.writeString(file2, "test2");

        List<Path> createdFiles = Arrays.asList(file1, file2);

        rollbackManager.rollbackCreatedFiles(createdFiles, logger);

        assertFalse(Files.exists(file1));
        assertFalse(Files.exists(file2));
    }

    @Test
    void testRollbackIgnoresMissingFiles() {
        Path missingFile = tempDir.resolve("missing.txt");
        List<Path> createdFiles = List.of(missingFile);

        assertDoesNotThrow(() -> rollbackManager.rollbackCreatedFiles(createdFiles, logger));
        assertFalse(Files.exists(missingFile));
    }

    @Test
    void testRollbackHandlesMixedFiles() throws IOException {
        Path existing = tempDir.resolve("existing.txt");
        Files.writeString(existing, "present");
        Path missing = tempDir.resolve("missing.txt");

        List<Path> createdFiles = Arrays.asList(existing, missing);

        rollbackManager.rollbackCreatedFiles(createdFiles, logger);

        assertFalse(Files.exists(existing));
        assertFalse(Files.exists(missing));
    }
}
