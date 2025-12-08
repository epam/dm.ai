package com.github.istin.dmtools.index.mermaid.tool;

import com.github.istin.dmtools.ai.agent.MermaidDiagramGeneratorAgent;
import com.github.istin.dmtools.common.model.ToText;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MermaidIndexTools.
 */
class MermaidIndexToolsTest {

    @Mock
    private MermaidDiagramGeneratorAgent mockDiagramGenerator;

    @TempDir
    Path tempDir;

    private MermaidIndexTools tools;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        tools = new MermaidIndexTools(mockDiagramGenerator);
    }

    @Test
    void testRead_WithValidPath_ReturnsAllMmdFiles() throws IOException {
        // Given: Create test directory structure with .mmd files
        Path storagePath = tempDir.resolve("test-storage");
        Path confluencePath = storagePath.resolve("confluence");
        Path spacePath = confluencePath.resolve("TEST");
        Path pagePath = spacePath.resolve("123");
        Path attachmentsPath = pagePath.resolve("attachments");
        
        Files.createDirectories(attachmentsPath);
        
        // Create main page diagram
        Path mainDiagram = pagePath.resolve("TestPage.mmd");
        Files.write(mainDiagram, "flowchart TD\nA[Main] --> B[Content]".getBytes(StandardCharsets.UTF_8));
        
        // Create attachment diagrams
        Path attachment1 = attachmentsPath.resolve("image1.mmd");
        Files.write(attachment1, "flowchart TD\nC[Image1] --> D[Details]".getBytes(StandardCharsets.UTF_8));
        
        Path attachment2 = attachmentsPath.resolve("image2.mmd");
        Files.write(attachment2, "sequenceDiagram\nA->>B: Message".getBytes(StandardCharsets.UTF_8));
        
        // Create nested page
        Path nestedPagePath = spacePath.resolve("123").resolve("TestPage").resolve("456");
        Files.createDirectories(nestedPagePath);
        Path nestedDiagram = nestedPagePath.resolve("NestedPage.mmd");
        Files.write(nestedDiagram, "mindmap\nroot((Nested))".getBytes(StandardCharsets.UTF_8));
        
        // When
        List<ToText> result = tools.read("confluence", storagePath.toString());
        
        // Then
        assertEquals(4, result.size(), "Should find all 4 .mmd files");
        
        // Verify all files are found by checking toText() output
        List<String> paths = result.stream()
                .map(diagram -> {
                    try {
                        String text = diagram.toText();
                        // Extract path from "Path: ..." format
                        if (text.startsWith("Path: ")) {
                            int pathEnd = text.indexOf("\n\n");
                            if (pathEnd > 0) {
                                return text.substring(6, pathEnd);
                            }
                        }
                        return null;
                    } catch (IOException e) {
                        fail("Should not throw IOException: " + e.getMessage());
                        return null;
                    }
                })
                .filter(path -> path != null)
                .sorted()
                .toList();
        
        assertEquals(4, paths.size(), "Should have 4 paths. Found: " + paths);
        // Paths are relative to confluence directory, so they start with TEST/
        assertTrue(paths.contains("TEST/123/TestPage.mmd") || paths.stream().anyMatch(p -> p.endsWith("123/TestPage.mmd")), 
            "Should contain main page diagram. Found paths: " + paths);
        assertTrue(paths.contains("TEST/123/attachments/image1.mmd") || paths.stream().anyMatch(p -> p.endsWith("123/attachments/image1.mmd")), 
            "Should contain first attachment. Found paths: " + paths);
        assertTrue(paths.contains("TEST/123/attachments/image2.mmd") || paths.stream().anyMatch(p -> p.endsWith("123/attachments/image2.mmd")), 
            "Should contain second attachment. Found paths: " + paths);
        assertTrue(paths.contains("TEST/123/TestPage/456/NestedPage.mmd") || paths.stream().anyMatch(p -> p.endsWith("123/TestPage/456/NestedPage.mmd")), 
            "Should contain nested page. Found paths: " + paths);
    }

    @Test
    void testRead_ToText_ReturnsPathAndContent() throws IOException {
        // Given
        Path storagePath = tempDir.resolve("test-storage");
        Path confluencePath = storagePath.resolve("confluence");
        Path spacePath = confluencePath.resolve("TEST");
        Path pagePath = spacePath.resolve("123");
        Files.createDirectories(pagePath);
        
        String diagramContent = "flowchart TD\nA[Test] --> B[Content]";
        Path diagram = pagePath.resolve("TestPage.mmd");
        Files.write(diagram, diagramContent.getBytes(StandardCharsets.UTF_8));
        
        // When
        List<ToText> result = tools.read("confluence", storagePath.toString());
        
        // Then
        assertEquals(1, result.size());
        String text = result.get(0).toText();
        
        assertTrue(text.startsWith("Path: "), "Should start with 'Path: '");
        assertTrue(text.contains("123/TestPage.mmd"), "Should contain relative path");
        assertTrue(text.contains(diagramContent), "Should contain diagram content");
        assertTrue(text.contains("\n\n"), "Should have separator between path and content");
    }

    @Test
    void testRead_WithEmptyDirectory_ReturnsEmptyList() throws IOException {
        // Given: Empty directory structure
        Path storagePath = tempDir.resolve("test-storage");
        Path confluencePath = storagePath.resolve("confluence");
        Files.createDirectories(confluencePath);
        
        // When
        List<ToText> result = tools.read("confluence", storagePath.toString());
        
        // Then
        assertTrue(result.isEmpty(), "Should return empty list for empty directory");
    }

    @Test
    void testRead_WithNonMmdFiles_IgnoresThem() throws IOException {
        // Given: Directory with .mmd and other files
        Path storagePath = tempDir.resolve("test-storage");
        Path confluencePath = storagePath.resolve("confluence");
        Path spacePath = confluencePath.resolve("TEST");
        Path pagePath = spacePath.resolve("123");
        Files.createDirectories(pagePath);
        
        // Create .mmd file
        Path mmdFile = pagePath.resolve("diagram.mmd");
        Files.write(mmdFile, "flowchart TD\nA --> B".getBytes(StandardCharsets.UTF_8));
        
        // Create other files (should be ignored)
        Path txtFile = pagePath.resolve("readme.txt");
        Files.write(txtFile, "Some text".getBytes(StandardCharsets.UTF_8));
        
        Path jsonFile = pagePath.resolve("config.json");
        Files.write(jsonFile, "{}".getBytes(StandardCharsets.UTF_8));
        
        // When
        List<ToText> result = tools.read("confluence", storagePath.toString());
        
        // Then
        assertEquals(1, result.size(), "Should only find .mmd file");
    }

    @Test
    void testRead_WithUnsupportedIntegration_ThrowsException() {
        // Given
        Path storagePath = tempDir.resolve("test-storage");
        
        // When/Then
        assertThrows(IllegalArgumentException.class, () -> {
            tools.read("unsupported", storagePath.toString());
        }, "Should throw IllegalArgumentException for unsupported integration");
    }

    @Test
    void testRead_WithNullStoragePath_ThrowsException() {
        // When/Then
        assertThrows(IllegalArgumentException.class, () -> {
            tools.read("confluence", null);
        }, "Should throw IllegalArgumentException for null storage path");
    }

    @Test
    void testRead_WithEmptyStoragePath_ThrowsException() {
        // When/Then
        assertThrows(IllegalArgumentException.class, () -> {
            tools.read("confluence", "");
        }, "Should throw IllegalArgumentException for empty storage path");
    }

    @Test
    void testRead_WithNonExistentPath_ThrowsException() {
        // Given
        Path nonExistentPath = tempDir.resolve("non-existent");
        
        // When/Then
        assertThrows(IOException.class, () -> {
            tools.read("confluence", nonExistentPath.toString());
        }, "Should throw IOException for non-existent path");
    }

    @Test
    void testMermaidIndexRead_WithValidPath_ReturnsJson() throws IOException {
        // Given: Create test directory structure
        Path storagePath = tempDir.resolve("test-storage");
        Path confluencePath = storagePath.resolve("confluence");
        Path spacePath = confluencePath.resolve("TEST");
        Path pagePath = spacePath.resolve("123");
        Files.createDirectories(pagePath);
        
        String diagramContent = "flowchart TD\nA[Test] --> B[Content]";
        Path diagram = pagePath.resolve("TestPage.mmd");
        Files.write(diagram, diagramContent.getBytes(StandardCharsets.UTF_8));
        
        // When
        String result = tools.mermaidIndexRead("confluence", storagePath.toString());
        
        // Then
        assertNotNull(result);
        assertTrue(result.contains("\"success\": true"));
        assertTrue(result.contains("\"count\": 1"));
        assertTrue(result.contains("\"diagrams\":"));
        assertTrue(result.contains("123/TestPage.mmd"));
        assertTrue(result.contains("flowchart TD"));
    }

    @Test
    void testMermaidIndexRead_WithMultipleFiles_ReturnsAllInJson() throws IOException {
        // Given: Create multiple .mmd files
        Path storagePath = tempDir.resolve("test-storage");
        Path confluencePath = storagePath.resolve("confluence");
        Path spacePath = confluencePath.resolve("TEST");
        Path pagePath = spacePath.resolve("123");
        Path attachmentsPath = pagePath.resolve("attachments");
        Files.createDirectories(attachmentsPath);
        
        Path mainDiagram = pagePath.resolve("MainPage.mmd");
        Files.write(mainDiagram, "flowchart TD\nA --> B".getBytes(StandardCharsets.UTF_8));
        
        Path attachmentDiagram = attachmentsPath.resolve("attachment.mmd");
        Files.write(attachmentDiagram, "sequenceDiagram\nA->>B: Msg".getBytes(StandardCharsets.UTF_8));
        
        // When
        String result = tools.mermaidIndexRead("confluence", storagePath.toString());
        
        // Then
        assertNotNull(result);
        assertTrue(result.contains("\"success\": true"));
        assertTrue(result.contains("\"count\": 2"));
        assertTrue(result.contains("123/MainPage.mmd"));
        assertTrue(result.contains("123/attachments/attachment.mmd"));
    }

    @Test
    void testMermaidIndexRead_WithUnsupportedIntegration_ReturnsErrorJson() {
        // Given
        Path storagePath = tempDir.resolve("test-storage");
        
        // When
        String result = tools.mermaidIndexRead("unsupported", storagePath.toString());
        
        // Then
        assertNotNull(result);
        assertTrue(result.contains("\"success\": false"));
        assertTrue(result.contains("error"));
        assertTrue(result.contains("Unsupported integration"));
    }

    @Test
    void testMermaidIndexRead_WithNonExistentPath_ReturnsErrorJson() {
        // Given
        Path nonExistentPath = tempDir.resolve("non-existent");
        
        // When
        String result = tools.mermaidIndexRead("confluence", nonExistentPath.toString());
        
        // Then
        assertNotNull(result);
        assertTrue(result.contains("\"success\": false"));
        assertTrue(result.contains("error"));
    }

    @Test
    void testMermaidIndexRead_EscapesSpecialCharacters() throws IOException {
        // Given: Create file with special characters in content
        Path storagePath = tempDir.resolve("test-storage");
        Path confluencePath = storagePath.resolve("confluence");
        Path spacePath = confluencePath.resolve("TEST");
        Path pagePath = spacePath.resolve("123");
        Files.createDirectories(pagePath);
        
        String diagramContent = "flowchart TD\nA[\"Test\"] --> B['Content']\nC[Line\nBreak]";
        Path diagram = pagePath.resolve("TestPage.mmd");
        Files.write(diagram, diagramContent.getBytes(StandardCharsets.UTF_8));
        
        // When
        String result = tools.mermaidIndexRead("confluence", storagePath.toString());
        
        // Then
        assertNotNull(result);
        assertTrue(result.contains("\\\""), "Should escape quotes");
        assertTrue(result.contains("\\n"), "Should escape newlines");
        assertTrue(result.contains("\"success\": true"));
    }
}
