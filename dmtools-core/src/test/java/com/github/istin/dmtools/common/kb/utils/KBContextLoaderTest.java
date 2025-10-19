package com.github.istin.dmtools.common.kb.utils;

import com.github.istin.dmtools.common.kb.model.KBContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class KBContextLoaderTest {
    
    private KBContextLoader loader;
    private KBFileParser parser;
    
    @TempDir
    Path tempDir;
    
    @BeforeEach
    void setUp() {
        parser = new KBFileParser();
        loader = new KBContextLoader(parser);
    }
    
    @Test
    void testLoadKBContext_EmptyKB() throws IOException {
        KBContext context = loader.loadKBContext(tempDir);
        
        assertNotNull(context);
        assertTrue(context.getExistingPeople().isEmpty());
        assertTrue(context.getExistingTopics().isEmpty());
        assertTrue(context.getExistingQuestions().isEmpty());
        assertEquals(0, context.getMaxQuestionId());
        assertEquals(0, context.getMaxAnswerId());
        assertEquals(0, context.getMaxNoteId());
    }
    
    @Test
    void testLoadKBContext_WithPeople() throws IOException {
        // Create people directories
        Path peopleDir = tempDir.resolve("people");
        Files.createDirectories(peopleDir.resolve("alice"));
        Files.createDirectories(peopleDir.resolve("bob"));
        
        KBContext context = loader.loadKBContext(tempDir);
        
        assertEquals(2, context.getExistingPeople().size());
        assertTrue(context.getExistingPeople().contains("alice"));
        assertTrue(context.getExistingPeople().contains("bob"));
    }
    
    @Test
    void testLoadKBContext_WithTopics() throws IOException {
        // Create topic files
        Path topicsDir = tempDir.resolve("topics");
        Files.createDirectories(topicsDir);
        Files.writeString(topicsDir.resolve("docker.md"), "# Docker");
        Files.writeString(topicsDir.resolve("kubernetes.md"), "# Kubernetes");
        Files.writeString(topicsDir.resolve("docker-desc.md"), "Description"); // Should be filtered
        
        KBContext context = loader.loadKBContext(tempDir);
        
        assertEquals(2, context.getExistingTopics().size());
        assertTrue(context.getExistingTopics().contains("docker"));
        assertTrue(context.getExistingTopics().contains("kubernetes"));
        assertFalse(context.getExistingTopics().contains("docker-desc"));
    }
    
    @Test
    void testLoadKBContext_WithQuestions() throws IOException {
        // Create question file
        Path questionsDir = tempDir.resolve("questions");
        Files.createDirectories(questionsDir);
        
        String questionContent = """
                ---
                author: "Alice"
                date: "2025-01-15"
                area: "Technology"
                answered: false
                ---
                # Question: q_0001
                
                What is Docker?
                
                **Asked by:** Alice
                """;
        Files.writeString(questionsDir.resolve("q_0001.md"), questionContent);
        
        KBContext context = loader.loadKBContext(tempDir);
        
        assertEquals(1, context.getExistingQuestions().size());
        KBContext.QuestionSummary q = context.getExistingQuestions().get(0);
        assertEquals("q_0001", q.getId());
        assertEquals("Alice", q.getAuthor());
        assertEquals("What is Docker?", q.getText());
        assertFalse(q.isAnswered());
    }
    
    @Test
    void testFindMaxId_NoFiles() throws IOException {
        Files.createDirectories(tempDir.resolve("questions"));
        
        int maxId = loader.findMaxId(tempDir, "q_", "questions");
        
        assertEquals(0, maxId);
    }
    
    @Test
    void testFindMaxId_WithFiles() throws IOException {
        Path questionsDir = tempDir.resolve("questions");
        Files.createDirectories(questionsDir);
        Files.writeString(questionsDir.resolve("q_0001.md"), "Q1");
        Files.writeString(questionsDir.resolve("q_0005.md"), "Q5");
        Files.writeString(questionsDir.resolve("q_0003.md"), "Q3");
        
        int maxId = loader.findMaxId(tempDir, "q_", "questions");
        
        assertEquals(5, maxId);
    }
    
    @Test
    void testFindMaxId_DirectoryNotExists() throws IOException {
        int maxId = loader.findMaxId(tempDir, "q_", "questions");
        
        assertEquals(0, maxId);
    }
    
    @Test
    void testExtractTopicTitle() {
        String content = "---\ntitle: \"Docker Tutorial\"\n---\nContent";
        
        String title = loader.extractTopicTitle(content, "default");
        
        assertEquals("Docker Tutorial", title);
    }
    
    @Test
    void testExtractTopicTitle_NoTitle() {
        String content = "---\nauthor: Alice\n---\nContent";
        
        String title = loader.extractTopicTitle(content, "default-title");
        
        assertEquals("default-title", title);
    }
    
    @Test
    void testInitializeOutputDirectories() throws IOException {
        Path outputPath = tempDir.resolve("kb_output");
        
        loader.initializeOutputDirectories(outputPath, true);
        
        assertTrue(Files.exists(outputPath));
        assertTrue(Files.exists(outputPath.resolve("topics")));
        assertTrue(Files.exists(outputPath.resolve("people")));
        assertTrue(Files.exists(outputPath.resolve("stats")));
        assertTrue(Files.exists(outputPath.resolve("inbox")));
    }
    
    @Test
    void testClearDirectory() throws IOException {
        // Create directory with files
        Path testDir = tempDir.resolve("test_clear");
        Files.createDirectories(testDir);
        Files.writeString(testDir.resolve("file1.txt"), "content1");
        Files.writeString(testDir.resolve("file2.txt"), "content2");
        Path subDir = testDir.resolve("subdir");
        Files.createDirectories(subDir);
        Files.writeString(subDir.resolve("file3.txt"), "content3");
        
        loader.clearDirectory(testDir);
        
        assertTrue(Files.exists(testDir)); // Directory itself should exist
        assertTrue(Files.list(testDir).count() == 0); // But be empty
    }
    
    @Test
    void testClearDirectory_NotExists() throws IOException {
        Path nonExistent = tempDir.resolve("does_not_exist");
        
        // Should not throw
        assertDoesNotThrow(() -> loader.clearDirectory(nonExistent));
    }
}

