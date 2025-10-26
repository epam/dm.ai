package com.github.istin.dmtools.common.kb.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for KBSmartRegenerationHelper.
 */
public class KBSmartRegenerationHelperTest {

    private KBSmartRegenerationHelper helper;

    @TempDir
    Path tempDir;

    @BeforeEach
    public void setUp() {
        helper = new KBSmartRegenerationHelper();
    }

    @Test
    public void testNeedsRegeneration_NoDescriptionFile() throws IOException {
        // Setup: Create entity file but no description file
        Path peopleDir = tempDir.resolve("people/john_doe");
        Files.createDirectories(peopleDir);
        Path entityFile = peopleDir.resolve("john_doe.md");
        Files.writeString(entityFile, "# John Doe\n\n[Q-abc123](questions/q-abc123.md)");
        
        Path descFile = peopleDir.resolve("john_doe-desc.md");
        
        // Test: Should need regeneration when description doesn't exist
        boolean result = helper.needsRegeneration(descFile, entityFile, tempDir);
        
        // Verify
        assertTrue(result, "Should need regeneration when description file doesn't exist");
    }

    @Test
    public void testNeedsRegeneration_DescriptionNewer() throws Exception {
        // Setup: Create Q/A/N file, then entity file, then description file (newest)
        Path questionsDir = tempDir.resolve("questions");
        Files.createDirectories(questionsDir);
        Path qanFile = questionsDir.resolve("q-abc123.md");
        Files.writeString(qanFile, "Question content");
        Thread.sleep(100); // Ensure different timestamps
        
        Path peopleDir = tempDir.resolve("people/john_doe");
        Files.createDirectories(peopleDir);
        Path entityFile = peopleDir.resolve("john_doe.md");
        Files.writeString(entityFile, "# John Doe\n\n[Q-abc123](questions/q-abc123.md)");
        Thread.sleep(100);
        
        Path descFile = peopleDir.resolve("john_doe-desc.md");
        Files.writeString(descFile, "John Doe is a person.");
        
        // Test: Should NOT need regeneration when description is newer than Q/A/N
        boolean result = helper.needsRegeneration(descFile, entityFile, tempDir);
        
        // Verify
        assertFalse(result, "Should NOT need regeneration when description is newer than all Q/A/N");
    }

    @Test
    public void testNeedsRegeneration_QANNewer() throws Exception {
        // Setup: Create description file first, then Q/A/N file (newer)
        Path peopleDir = tempDir.resolve("people/john_doe");
        Files.createDirectories(peopleDir);
        Path entityFile = peopleDir.resolve("john_doe.md");
        Files.writeString(entityFile, "# John Doe\n\n[Q-abc123](questions/q-abc123.md)");
        
        Path descFile = peopleDir.resolve("john_doe-desc.md");
        Files.writeString(descFile, "John Doe is a person.");
        Thread.sleep(100); // Ensure different timestamps
        
        Path questionsDir = tempDir.resolve("questions");
        Files.createDirectories(questionsDir);
        Path qanFile = questionsDir.resolve("q-abc123.md");
        Files.writeString(qanFile, "Question content");
        
        // Test: Should need regeneration when Q/A/N is newer than description
        boolean result = helper.needsRegeneration(descFile, entityFile, tempDir);
        
        // Verify
        assertTrue(result, "Should need regeneration when any Q/A/N is newer than description");
    }

    @Test
    public void testExtractQANIds_VariousFormats() {
        // Test various markdown link formats
        String content = "# Person Profile\n\n" +
                "Questions: [Q-abc123] [Q-xyz789]\n" +
                "Answers: [A-answer1] [A-answer2]\n" +
                "Notes: [N-note1]\n" +
                "Links: questions/q-linktest.md answers/a-linktest.md notes/n-linktest.md";
        
        Set<String> ids = helper.extractQANIds(content);
        
        // Verify all formats are extracted
        assertEquals(8, ids.size(), "Should extract 8 Q/A/N IDs");
        assertTrue(ids.contains("Q-abc123"), "Should extract Q-abc123");
        assertTrue(ids.contains("Q-xyz789"), "Should extract Q-xyz789");
        assertTrue(ids.contains("A-answer1"), "Should extract A-answer1");
        assertTrue(ids.contains("A-answer2"), "Should extract A-answer2");
        assertTrue(ids.contains("N-note1"), "Should extract N-note1");
        assertTrue(ids.contains("Q-linktest"), "Should extract Q-linktest from file path");
        assertTrue(ids.contains("A-linktest"), "Should extract A-linktest from file path");
        assertTrue(ids.contains("N-linktest"), "Should extract N-linktest from file path");
    }

    @Test
    public void testExtractQANIds_EmptyContent() {
        Set<String> ids = helper.extractQANIds("");
        assertEquals(0, ids.size(), "Should return empty set for empty content");
        
        ids = helper.extractQANIds(null);
        assertEquals(0, ids.size(), "Should return empty set for null content");
    }

    @Test
    public void testExtractQANIds_NoMatches() {
        String content = "# Person Profile\n\nNo Q/A/N references here.";
        
        Set<String> ids = helper.extractQANIds(content);
        
        assertEquals(0, ids.size(), "Should return empty set when no Q/A/N references found");
    }

    @Test
    public void testGetFileModificationTime() throws IOException {
        // Create a test file
        Path testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "test content");
        
        // Get modification time
        long modTime = helper.getFileModificationTime(testFile);
        
        // Verify it's a reasonable timestamp (should be recent)
        long now = System.currentTimeMillis();
        assertTrue(modTime > 0, "Modification time should be positive");
        assertTrue(modTime <= now, "Modification time should not be in the future");
        assertTrue(now - modTime < 10000, "Modification time should be within last 10 seconds");
    }

    @Test
    public void testNeedsRegeneration_MultipleQAN_AllOlder() throws Exception {
        // Setup: Create multiple Q/A/N files (old), then description (new)
        Path questionsDir = tempDir.resolve("questions");
        Path answersDir = tempDir.resolve("answers");
        Path notesDir = tempDir.resolve("notes");
        Files.createDirectories(questionsDir);
        Files.createDirectories(answersDir);
        Files.createDirectories(notesDir);
        
        Files.writeString(questionsDir.resolve("q-test1.md"), "Question 1");
        Files.writeString(answersDir.resolve("a-test1.md"), "Answer 1");
        Files.writeString(notesDir.resolve("n-test1.md"), "Note 1");
        Thread.sleep(100);
        
        Path peopleDir = tempDir.resolve("people/john_doe");
        Files.createDirectories(peopleDir);
        Path entityFile = peopleDir.resolve("john_doe.md");
        Files.writeString(entityFile, 
            "# John Doe\n\n[Q-test1] [A-test1] [N-test1]");
        
        Path descFile = peopleDir.resolve("john_doe-desc.md");
        Files.writeString(descFile, "Description content");
        
        // Test: Should NOT need regeneration when all Q/A/N are older
        boolean result = helper.needsRegeneration(descFile, entityFile, tempDir);
        
        // Verify
        assertFalse(result, "Should NOT need regeneration when all Q/A/N are older than description");
    }

    @Test
    public void testNeedsRegeneration_MultipleQAN_OneNewer() throws Exception {
        // Setup: Create description first, then Q/A/N files (one is newer)
        Path peopleDir = tempDir.resolve("people/john_doe");
        Files.createDirectories(peopleDir);
        Path entityFile = peopleDir.resolve("john_doe.md");
        Files.writeString(entityFile, 
            "# John Doe\n\n[Q-test1] [A-test1] [N-test1]");
        
        Path descFile = peopleDir.resolve("john_doe-desc.md");
        Files.writeString(descFile, "Description content");
        Thread.sleep(100);
        
        Path questionsDir = tempDir.resolve("questions");
        Path answersDir = tempDir.resolve("answers");
        Path notesDir = tempDir.resolve("notes");
        Files.createDirectories(questionsDir);
        Files.createDirectories(answersDir);
        Files.createDirectories(notesDir);
        
        // Create two old Q/A/N but modify one to be newer
        long oldTime = System.currentTimeMillis() - 10000; // 10 seconds ago
        Path q1 = questionsDir.resolve("q-test1.md");
        Path a1 = answersDir.resolve("a-test1.md");
        Path n1 = notesDir.resolve("n-test1.md");
        
        Files.writeString(q1, "Question 1");
        Files.writeString(a1, "Answer 1");
        Files.writeString(n1, "Note 1");
        
        // Make q1 and a1 appear old
        Files.setLastModifiedTime(q1, FileTime.fromMillis(oldTime));
        Files.setLastModifiedTime(a1, FileTime.fromMillis(oldTime));
        // n1 stays new (just created)
        
        // Test: Should need regeneration when ANY Q/A/N is newer
        boolean result = helper.needsRegeneration(descFile, entityFile, tempDir);
        
        // Verify
        assertTrue(result, "Should need regeneration when ANY Q/A/N is newer than description");
    }

    @Test
    public void testNeedsRegeneration_NoEntityFile() {
        Path descFile = tempDir.resolve("people/john_doe/john_doe-desc.md");
        Path entityFile = tempDir.resolve("people/john_doe/john_doe.md");
        
        // Test: Should return false when entity file doesn't exist
        boolean result = helper.needsRegeneration(descFile, entityFile, tempDir);
        
        // Verify
        assertFalse(result, "Should return false when entity file doesn't exist");
    }

    @Test
    public void testNeedsRegeneration_EntityFileWithNoQAN() throws IOException {
        // Setup: Create entity file with no Q/A/N references
        Path peopleDir = tempDir.resolve("people/john_doe");
        Files.createDirectories(peopleDir);
        Path entityFile = peopleDir.resolve("john_doe.md");
        Files.writeString(entityFile, "# John Doe\n\nNo Q/A/N references.");
        
        Path descFile = peopleDir.resolve("john_doe-desc.md");
        Files.writeString(descFile, "Description");
        
        // Test: Should return false when no Q/A/N to check
        boolean result = helper.needsRegeneration(descFile, entityFile, tempDir);
        
        // Verify
        assertFalse(result, "Should return false when entity has no Q/A/N references");
    }
}

