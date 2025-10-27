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
    
    // ========== writeFile Tests ==========
    
    @Test
    void testWriteFile_Success() {
        String content = "Test content";
        String result = fileTools.writeFile("test-write.txt", content);
        
        assertNotNull(result);
        assertTrue(result.contains("successfully"));
        
        // Verify file was actually written
        Path writtenFile = tempDir.resolve("test-write.txt");
        assertTrue(Files.exists(writtenFile));
        
        // Verify content
        String readContent = fileTools.readFile("test-write.txt");
        assertEquals(content, readContent);
    }
    
    @Test
    void testWriteFile_NullPath() {
        String result = fileTools.writeFile(null, "content");
        assertNull(result);
    }
    
    @Test
    void testWriteFile_EmptyPath() {
        String result = fileTools.writeFile("", "content");
        assertNull(result);
    }
    
    @Test
    void testWriteFile_WhitespacePath() {
        String result = fileTools.writeFile("   ", "content");
        assertNull(result);
    }
    
    @Test
    void testWriteFile_NullContent() {
        String result = fileTools.writeFile("test.txt", null);
        assertNull(result);
    }
    
    @Test
    void testWriteFile_EmptyContent() {
        String result = fileTools.writeFile("empty.txt", "");
        
        assertNotNull(result);
        assertTrue(result.contains("successfully"));
        
        // Verify file exists and is empty
        Path writtenFile = tempDir.resolve("empty.txt");
        assertTrue(Files.exists(writtenFile));
        
        String readContent = fileTools.readFile("empty.txt");
        assertEquals("", readContent);
    }
    
    @Test
    void testWriteFile_CreateParentDirectories() {
        String content = "Nested content";
        String result = fileTools.writeFile("inbox/raw/teams_messages/test.json", content);
        
        assertNotNull(result);
        assertTrue(result.contains("successfully"));
        
        // Verify nested directories were created
        Path writtenFile = tempDir.resolve("inbox/raw/teams_messages/test.json");
        assertTrue(Files.exists(writtenFile));
        assertTrue(Files.isDirectory(tempDir.resolve("inbox")));
        assertTrue(Files.isDirectory(tempDir.resolve("inbox/raw")));
        assertTrue(Files.isDirectory(tempDir.resolve("inbox/raw/teams_messages")));
        
        // Verify content
        String readContent = fileTools.readFile("inbox/raw/teams_messages/test.json");
        assertEquals(content, readContent);
    }
    
    @Test
    void testWriteFile_OverwriteExisting() throws IOException {
        String path = "overwrite.txt";
        
        // Write initial content
        Files.writeString(tempDir.resolve(path), "Initial content");
        
        // Overwrite with new content
        String newContent = "Overwritten content";
        String result = fileTools.writeFile(path, newContent);
        
        assertNotNull(result);
        assertTrue(result.contains("successfully"));
        
        // Verify new content
        String readContent = fileTools.readFile(path);
        assertEquals(newContent, readContent);
    }
    
    @Test
    void testWriteFile_UTF8Content() {
        String content = "UTF-8 content: 你好世界 🌍 Привет";
        String result = fileTools.writeFile("utf8-write.txt", content);
        
        assertNotNull(result);
        assertTrue(result.contains("successfully"));
        
        // Verify UTF-8 encoding preserved
        String readContent = fileTools.readFile("utf8-write.txt");
        assertEquals(content, readContent);
    }
    
    @Test
    void testWriteFile_LargeContent() {
        StringBuilder largeContent = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            largeContent.append("Line ").append(i).append("\n");
        }
        
        String result = fileTools.writeFile("large-write.txt", largeContent.toString());
        
        assertNotNull(result);
        assertTrue(result.contains("successfully"));
        
        // Verify large content
        String readContent = fileTools.readFile("large-write.txt");
        assertNotNull(readContent);
        assertTrue(readContent.length() > 10000);
    }
    
    @Test
    void testWriteFile_PathTraversalBlocked() {
        String result = fileTools.writeFile("../../../etc/passwd", "malicious");
        // Should return null because path traversal is blocked
        assertNull(result);
        
        // Verify no file was created in working directory with traversal path
        assertFalse(Files.exists(tempDir.resolve("../../../etc/passwd")));
    }
    
    @Test
    void testWriteFile_AbsolutePathOutsideWorkingDir() {
        String result = fileTools.writeFile("/tmp/outside.txt", "content");
        assertNull(result);
    }
    
    @Test
    void testWriteFile_AbsolutePathWithinWorkingDir() {
        Path absolutePath = tempDir.resolve("absolute-write.txt");
        String content = "Absolute path content";
        
        String result = fileTools.writeFile(absolutePath.toString(), content);
        
        assertNotNull(result);
        assertTrue(result.contains("successfully"));
        assertTrue(Files.exists(absolutePath));
        
        String readContent = fileTools.readFile(absolutePath.toString());
        assertEquals(content, readContent);
    }
    
    @Test
    void testWriteFile_DotSlashPrefix() {
        String content = "Dot slash content";
        String result = fileTools.writeFile("./dotslash-write.txt", content);
        
        assertNotNull(result);
        assertTrue(result.contains("successfully"));
        
        String readContent = fileTools.readFile("./dotslash-write.txt");
        assertEquals(content, readContent);
    }
    
    @Test
    void testWriteFile_JSONContent() {
        String jsonContent = "{\"messages\": [{\"id\": 1, \"text\": \"Hello\"}]}";
        String result = fileTools.writeFile("inbox/raw/test_source/messages.json", jsonContent);
        
        assertNotNull(result);
        assertTrue(result.contains("successfully"));
        
        // Verify JSON content preserved
        String readContent = fileTools.readFile("inbox/raw/test_source/messages.json");
        assertEquals(jsonContent, readContent);
    }
    
    @Test
    void testWriteFile_SpecialCharactersInPath() {
        String content = "Content with special chars";
        String result = fileTools.writeFile("inbox/raw/source_name/123-test.json", content);
        
        assertNotNull(result);
        assertTrue(result.contains("successfully"));
        
        String readContent = fileTools.readFile("inbox/raw/source_name/123-test.json");
        assertEquals(content, readContent);
    }
}
