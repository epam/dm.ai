package com.github.istin.dmtools.file;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FileToolsTest {

    private FileTools fileTools;
    private String originalWorkingDir;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        fileTools = new FileTools();
        originalWorkingDir = System.getProperty("user.dir");
        // Set working directory to temp directory for tests
        System.setProperty("user.dir", tempDir.toString());
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        // Restore original working directory
        System.setProperty("user.dir", originalWorkingDir);
    }

    @Test
    void testReadFile_Success() throws IOException {
        Path testFile = tempDir.resolve("test.txt");
        String content = "Test content";
        Files.writeString(testFile, content);

        String result = fileTools.readFile("test.txt");
        assertEquals(content, result);
    }

    @Test
    void testReadFile_NonExistent() {
        String result = fileTools.readFile("non_existent.txt");
        assertNull(result);
    }

    @Test
    void testReadFile_NullPath() {
        String result = fileTools.readFile(null);
        assertNull(result);
    }

    @Test
    void testReadFile_EmptyPath() {
        String result = fileTools.readFile("");
        assertNull(result);
    }

    @Test
    void testReadFile_WhitespacePath() {
        String result = fileTools.readFile("   ");
        assertNull(result);
    }

    @Test
    void testReadFile_SubDirectory() throws IOException {
        Path subDir = tempDir.resolve("outputs");
        Files.createDirectories(subDir);
        Path testFile = subDir.resolve("response.md");
        String content = "Response content";
        Files.writeString(testFile, content);

        String result = fileTools.readFile("outputs/response.md");
        assertEquals(content, result);
    }

    @Test
    void testReadFile_AbsolutePathWithinWorkingDir() throws IOException {
        Path testFile = tempDir.resolve("absolute.txt");
        String content = "Absolute path content";
        Files.writeString(testFile, content);

        String result = fileTools.readFile(testFile.toString());
        assertEquals(content, result);
    }

    @Test
    void testReadFile_PathTraversalBlocked() {
        String result = fileTools.readFile("../../../etc/passwd");
        assertNull(result);
    }

    @Test
    void testReadFile_AbsolutePathOutsideWorkingDir() {
        String result = fileTools.readFile("/etc/passwd");
        assertNull(result);
    }

    @Test
    void testReadFile_Directory() throws IOException {
        Path subDir = tempDir.resolve("testdir");
        Files.createDirectories(subDir);

        String result = fileTools.readFile("testdir");
        assertNull(result);
    }

    @Test
    void testReadFile_UTF8Content() throws IOException {
        Path testFile = tempDir.resolve("utf8.txt");
        String content = "UTF-8 content: 你好世界 🌍";
        Files.writeString(testFile, content);

        String result = fileTools.readFile("utf8.txt");
        assertEquals(content, result);
    }

    @Test
    void testReadFile_LargeFile() throws IOException {
        Path testFile = tempDir.resolve("large.txt");
        StringBuilder largeContent = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            largeContent.append("Line ").append(i).append("\n");
        }
        Files.writeString(testFile, largeContent.toString());

        String result = fileTools.readFile("large.txt");
        assertNotNull(result);
        assertTrue(result.length() > 10000);
    }

    @Test
    void testReadFile_InputDirectory() throws IOException {
        Path inputDir = tempDir.resolve("input").resolve("TICKET-123");
        Files.createDirectories(inputDir);
        Path testFile = inputDir.resolve("request.md");
        String content = "Request markdown content";
        Files.writeString(testFile, content);

        String result = fileTools.readFile("input/TICKET-123/request.md");
        assertEquals(content, result);
    }

    @Test
    void testReadFile_DotSlashPrefix() throws IOException {
        Path testFile = tempDir.resolve("dotslash.txt");
        String content = "Dot slash content";
        Files.writeString(testFile, content);

        String result = fileTools.readFile("./dotslash.txt");
        assertEquals(content, result);
    }

    @Test
    void testReadFile_EmptyFile() throws IOException {
        Path testFile = tempDir.resolve("empty.txt");
        Files.writeString(testFile, "");

        String result = fileTools.readFile("empty.txt");
        assertEquals("", result);
    }
}
