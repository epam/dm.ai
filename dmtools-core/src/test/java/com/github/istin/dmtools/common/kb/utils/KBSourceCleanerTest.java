package com.github.istin.dmtools.common.kb.utils;

import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class KBSourceCleanerTest {

    @TempDir
    Path tempDir;

    @Mock
    private KBFileParser fileParser;

    @Mock
    private KBStructureManager structureManager;

    @Mock
    private Logger logger;

    private KBSourceCleaner sourceCleaner;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        sourceCleaner = new KBSourceCleaner(fileParser, structureManager);
    }

    @Test
    void testCleanSourceFiles_DeletesMatchingFiles() throws Exception {
        // Setup directory structure
        Path questionsDir = tempDir.resolve("questions");
        Path answersDir = tempDir.resolve("answers");
        Path notesDir = tempDir.resolve("notes");
        Files.createDirectories(questionsDir);
        Files.createDirectories(answersDir);
        Files.createDirectories(notesDir);

        // Create test files with source metadata
        String sourceA = "confluence_page_123";
        String sourceB = "teams_chat";

        Path q1 = questionsDir.resolve("q_0001.md");
        Path q2 = questionsDir.resolve("q_0002.md");
        Path a1 = answersDir.resolve("a_0001.md");
        Path n1 = notesDir.resolve("n_0001.md");

        Files.writeString(q1, "---\nsource: " + sourceA + "\n---\nQuestion 1");
        Files.writeString(q2, "---\nsource: " + sourceB + "\n---\nQuestion 2");
        Files.writeString(a1, "---\nsource: " + sourceA + "\n---\nAnswer 1");
        Files.writeString(n1, "---\nsource: " + sourceA + "\n---\nNote 1");

        // Mock parser to extract source
        when(fileParser.extractSource(contains(sourceA))).thenReturn(sourceA);
        when(fileParser.extractSource(contains(sourceB))).thenReturn(sourceB);

        // Execute
        List<String> deletedIds = sourceCleaner.cleanSourceFiles(tempDir, sourceA, logger);

        // Verify
        assertEquals(3, deletedIds.size());
        assertTrue(deletedIds.contains("q_0001"));
        assertTrue(deletedIds.contains("a_0001"));
        assertTrue(deletedIds.contains("n_0001"));

        // Verify files deleted
        assertFalse(Files.exists(q1));
        assertFalse(Files.exists(a1));
        assertFalse(Files.exists(n1));

        // Verify file from other source NOT deleted
        assertTrue(Files.exists(q2));

        // Verify structure regeneration was called
        verify(structureManager).rebuildPeopleProfiles(eq(tempDir), eq(sourceA), eq(logger));
        verify(structureManager).generateIndexes(eq(tempDir));
    }

    @Test
    void testCleanSourceFiles_HandlesEmptyDirectories() throws Exception {
        // Setup empty directory structure
        Path questionsDir = tempDir.resolve("questions");
        Path answersDir = tempDir.resolve("answers");
        Path notesDir = tempDir.resolve("notes");
        Files.createDirectories(questionsDir);
        Files.createDirectories(answersDir);
        Files.createDirectories(notesDir);

        // Execute
        List<String> deletedIds = sourceCleaner.cleanSourceFiles(tempDir, "any_source", logger);

        // Verify
        assertTrue(deletedIds.isEmpty());

        // Verify structure regeneration was still called
        verify(structureManager).rebuildPeopleProfiles(eq(tempDir), eq("any_source"), eq(logger));
        verify(structureManager).generateIndexes(eq(tempDir));
    }

    @Test
    void testCleanSourceFiles_HandlesNonExistentDirectories() throws Exception {
        // Execute without creating directories
        List<String> deletedIds = sourceCleaner.cleanSourceFiles(tempDir, "any_source", logger);

        // Verify
        assertTrue(deletedIds.isEmpty());

        // Verify structure regeneration was still called
        verify(structureManager).rebuildPeopleProfiles(eq(tempDir), eq("any_source"), eq(logger));
        verify(structureManager).generateIndexes(eq(tempDir));
    }

    @Test
    void testCleanSourceFiles_HandlesNullSource() throws Exception {
        // Setup directory structure
        Path questionsDir = tempDir.resolve("questions");
        Files.createDirectories(questionsDir);

        // Create test file with source metadata
        Path q1 = questionsDir.resolve("q_0001.md");
        Files.writeString(q1, "---\nsource: confluence_page_123\n---\nQuestion 1");

        // Mock parser to return null for source
        when(fileParser.extractSource(anyString())).thenReturn(null);

        // Execute
        List<String> deletedIds = sourceCleaner.cleanSourceFiles(tempDir, "confluence_page_123", logger);

        // Verify - file should NOT be deleted because source is null
        assertTrue(deletedIds.isEmpty());
        assertTrue(Files.exists(q1));
    }

    @Test
    void testCleanSourceFiles_SkipsNonMarkdownFiles() throws Exception {
        // Setup directory structure
        Path questionsDir = tempDir.resolve("questions");
        Files.createDirectories(questionsDir);

        // Create test files
        Path mdFile = questionsDir.resolve("q_0001.md");
        Path txtFile = questionsDir.resolve("q_0002.txt");

        Files.writeString(mdFile, "---\nsource: confluence_page_123\n---\nQuestion 1");
        Files.writeString(txtFile, "---\nsource: confluence_page_123\n---\nQuestion 2");

        // Mock parser
        when(fileParser.extractSource(anyString())).thenReturn("confluence_page_123");

        // Execute
        List<String> deletedIds = sourceCleaner.cleanSourceFiles(tempDir, "confluence_page_123", logger);

        // Verify - only .md file should be processed
        assertEquals(1, deletedIds.size());
        assertTrue(deletedIds.contains("q_0001"));
        assertFalse(Files.exists(mdFile));
        assertTrue(Files.exists(txtFile)); // .txt file should remain
    }

    @Test
    void testCleanSourceFiles_UpdatesPersonProfiles() throws Exception {
        // Setup directory structure
        Path questionsDir = tempDir.resolve("questions");
        Files.createDirectories(questionsDir);

        // Create test file
        Path q1 = questionsDir.resolve("q_0001.md");
        Files.writeString(q1, "---\nsource: confluence_page_123\n---\nQuestion 1");

        // Mock parser
        when(fileParser.extractSource(anyString())).thenReturn("confluence_page_123");

        // Execute
        sourceCleaner.cleanSourceFiles(tempDir, "confluence_page_123", logger);

        // Verify person profiles were rebuilt
        verify(structureManager, times(1)).rebuildPeopleProfiles(eq(tempDir), eq("confluence_page_123"), eq(logger));
    }

    @Test
    void testCleanSourceFiles_RegeneratesIndexes() throws Exception {
        // Setup directory structure
        Path questionsDir = tempDir.resolve("questions");
        Files.createDirectories(questionsDir);

        // Create test file
        Path q1 = questionsDir.resolve("q_0001.md");
        Files.writeString(q1, "---\nsource: confluence_page_123\n---\nQuestion 1");

        // Mock parser
        when(fileParser.extractSource(anyString())).thenReturn("confluence_page_123");

        // Execute
        sourceCleaner.cleanSourceFiles(tempDir, "confluence_page_123", logger);

        // Verify indexes were regenerated
        verify(structureManager, times(1)).generateIndexes(eq(tempDir));
    }
}

