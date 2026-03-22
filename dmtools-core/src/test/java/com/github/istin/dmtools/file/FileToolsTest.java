package com.github.istin.dmtools.file;

import com.github.istin.dmtools.common.utils.PropertyReader;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

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
    
    // ========== matchesPattern / allowlist unit tests ==========

    @Test
    void testMatchesPattern_ExactFile_Matches() throws Exception {
        // Sibling directory relative to tempDir
        Path sibling = tempDir.getParent().resolve("sibling");
        Files.createDirectories(sibling);
        Path target = sibling.resolve("config.js");
        Files.writeString(target, "content");

        Path workingDir = tempDir.toAbsolutePath().normalize();
        // pattern "parent/sibling/config.js" expressed with ".." relative syntax
        String pattern = "../" + sibling.getFileName() + "/config.js";
        assertTrue(FileTools.matchesPattern(target.toAbsolutePath().normalize(), workingDir, pattern));
    }

    @Test
    void testMatchesPattern_ExactFile_DoesNotMatch() throws Exception {
        Path sibling = tempDir.getParent().resolve("sibling2");
        Files.createDirectories(sibling);
        Path target = sibling.resolve("other.js");
        Files.writeString(target, "content");

        Path workingDir = tempDir.toAbsolutePath().normalize();
        String pattern = "../" + sibling.getFileName() + "/config.js";   // different filename
        assertFalse(FileTools.matchesPattern(target.toAbsolutePath().normalize(), workingDir, pattern));
    }

    @Test
    void testMatchesPattern_GlobDoubleStarAllSubPaths() throws Exception {
        Path sibling = tempDir.getParent().resolve("siblingDir");
        Path nested  = sibling.resolve("sub/deep");
        Files.createDirectories(nested);
        Path target = nested.resolve("file.txt");
        Files.writeString(target, "content");

        Path workingDir = tempDir.toAbsolutePath().normalize();
        String pattern = "../" + sibling.getFileName() + "/**";
        assertTrue(FileTools.matchesPattern(target.toAbsolutePath().normalize(), workingDir, pattern));
    }

    @Test
    void testMatchesPattern_GlobSingleStar_DirectChildOnly() throws Exception {
        Path sibling = tempDir.getParent().resolve("siblingFlat");
        Files.createDirectories(sibling);
        Path directChild = sibling.resolve("file.js");
        Files.writeString(directChild, "x");
        Path deepChild = sibling.resolve("sub/file.js");
        Files.createDirectories(sibling.resolve("sub"));
        Files.writeString(deepChild, "y");

        Path workingDir = tempDir.toAbsolutePath().normalize();
        String pattern = "../" + sibling.getFileName() + "/*.js";

        // direct child matches
        assertTrue(FileTools.matchesPattern(directChild.toAbsolutePath().normalize(), workingDir, pattern));
        // deep child does NOT match (single * doesn't cross directories)
        assertFalse(FileTools.matchesPattern(deepChild.toAbsolutePath().normalize(), workingDir, pattern));
    }

    @Test
    void testMatchesPattern_PathOutsideBase_NoMatch() throws Exception {
        Path sibling = tempDir.getParent().resolve("siblingA");
        Files.createDirectories(sibling);
        Path target = sibling.resolve("secret.txt");
        Files.writeString(target, "secret");

        Path otherSibling = tempDir.getParent().resolve("siblingB");
        Files.createDirectories(otherSibling);

        Path workingDir = tempDir.toAbsolutePath().normalize();
        // pattern only covers siblingB, not siblingA
        String pattern = "../" + otherSibling.getFileName() + "/**";
        assertFalse(FileTools.matchesPattern(target.toAbsolutePath().normalize(), workingDir, pattern));
    }

    // ========== readFile allowlist integration tests ==========

    @Test
    void testReadFile_OutsideWorkdirBlockedWithoutAllowlist() throws Exception {
        Path sibling = tempDir.getParent().resolve("blockedSibling");
        Files.createDirectories(sibling);
        Path target = sibling.resolve("data.txt");
        Files.writeString(target, "secret");

        // No allowlist configured - should be blocked
        PropertyReader.setOverrides(Map.of());
        try {
            String result = fileTools.readFile(target.toAbsolutePath().toString());
            assertNull(result, "Path outside working dir should return null when no allowlist is set");
        } finally {
            PropertyReader.setOverrides(null);
        }
    }

    @Test
    void testReadFile_OutsideWorkdirAllowedByGlobPattern() throws Exception {
        Path dmtools = tempDir.getParent().resolve(".dmtools");
        Files.createDirectories(dmtools);
        Path config = dmtools.resolve("config.js");
        Files.writeString(config, "module.exports = {};");

        PropertyReader.setOverrides(Map.of(
                PropertyReader.DMTOOLS_FILE_READ_ALLOWED_PATHS,
                "../.dmtools/**"
        ));
        try {
            String result = fileTools.readFile(config.toAbsolutePath().toString());
            assertEquals("module.exports = {};", result);
        } finally {
            PropertyReader.setOverrides(null);
        }
    }

    @Test
    void testReadFile_OutsideWorkdirAllowedByExactPattern() throws Exception {
        Path sibling = tempDir.getParent().resolve("exactSibling");
        Files.createDirectories(sibling);
        Path target = sibling.resolve("only-this.txt");
        Files.writeString(target, "allowed content");

        PropertyReader.setOverrides(Map.of(
                PropertyReader.DMTOOLS_FILE_READ_ALLOWED_PATHS,
                "../exactSibling/only-this.txt"
        ));
        try {
            String result = fileTools.readFile(target.toAbsolutePath().toString());
            assertEquals("allowed content", result);
        } finally {
            PropertyReader.setOverrides(null);
        }
    }

    @Test
    void testReadFile_OutsideWorkdirMultiplePatterns() throws Exception {
        Path sibA = tempDir.getParent().resolve("multiA");
        Path sibB = tempDir.getParent().resolve("multiB");
        Files.createDirectories(sibA);
        Files.createDirectories(sibB);
        Path fileA = sibA.resolve("a.txt");
        Path fileB = sibB.resolve("b.txt");
        Files.writeString(fileA, "A");
        Files.writeString(fileB, "B");

        PropertyReader.setOverrides(Map.of(
                PropertyReader.DMTOOLS_FILE_READ_ALLOWED_PATHS,
                "../multiA/**,../multiB/**"
        ));
        try {
            assertEquals("A", fileTools.readFile(fileA.toAbsolutePath().toString()));
            assertEquals("B", fileTools.readFile(fileB.toAbsolutePath().toString()));
        } finally {
            PropertyReader.setOverrides(null);
        }
    }

    @Test
    void testReadFile_PatternDoesNotMatchOtherPath_StillBlocked() throws Exception {
        Path allowed = tempDir.getParent().resolve("allowedDir");
        Path blocked = tempDir.getParent().resolve("blockedDir");
        Files.createDirectories(allowed);
        Files.createDirectories(blocked);
        Path blockedFile = blocked.resolve("secret.txt");
        Files.writeString(blockedFile, "no access");

        PropertyReader.setOverrides(Map.of(
                PropertyReader.DMTOOLS_FILE_READ_ALLOWED_PATHS,
                "../allowedDir/**"    // only allowedDir is allowed
        ));
        try {
            String result = fileTools.readFile(blockedFile.toAbsolutePath().toString());
            assertNull(result, "File not matching any allowlist pattern should still be blocked");
        } finally {
            PropertyReader.setOverrides(null);
        }
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
        assertNull(result, "Path traversal should be blocked and return null");
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
    
    // ========== deleteFile Tests ==========

    @Test
    void testDeleteFile_Success() throws IOException {
        Path testFile = tempDir.resolve("delete_me.txt");
        Files.writeString(testFile, "content");
        
        String result = fileTools.deleteFile("delete_me.txt");
        
        assertNotNull(result);
        assertTrue(result.contains("successfully"));
        assertFalse(Files.exists(testFile));
    }
    
    @Test
    void testDeleteFile_Directory_Recursive() throws IOException {
        Path subDir = tempDir.resolve("delete_dir");
        Files.createDirectories(subDir);
        Files.writeString(subDir.resolve("file1.txt"), "content1");
        Files.writeString(subDir.resolve("file2.txt"), "content2");
        
        Path nestedDir = subDir.resolve("nested");
        Files.createDirectories(nestedDir);
        Files.writeString(nestedDir.resolve("nested_file.txt"), "nested content");
        
        String result = fileTools.deleteFile("delete_dir");
        
        assertNotNull(result);
        assertTrue(result.contains("successfully"));
        assertFalse(Files.exists(subDir));
        assertFalse(Files.exists(nestedDir));
    }
    
    @Test
    void testDeleteFile_NonExistent() {
        String result = fileTools.deleteFile("non_existent.txt");
        assertNull(result);
    }
    
    @Test
    void testDeleteFile_NullPath() {
        String result = fileTools.deleteFile(null);
        assertNull(result);
    }
    
    @Test
    void testDeleteFile_PathTraversalBlocked() {
        String result = fileTools.deleteFile("../../../etc/passwd");
        assertNull(result);
    }
    
    @Test
    void testDeleteFile_AbsolutePathOutsideWorkingDir() {
        String result = fileTools.deleteFile("/etc/passwd");
        assertNull(result);
    }

    // ========== validateJson Tests ==========
    
    @Test
    void testValidateJson_ValidObject() {
        String validJson = "{\"key\": \"value\", \"number\": 123, \"boolean\": true}";
        String result = fileTools.validateJson(validJson);
        
        assertNotNull(result);
        JSONObject validationResult = new JSONObject(result);
        assertTrue(validationResult.getBoolean("valid"));
        assertFalse(validationResult.has("error"));
    }
    
    @Test
    void testValidateJson_ValidArray() {
        String validJson = "[1, 2, 3, \"test\", true, null]";
        String result = fileTools.validateJson(validJson);
        
        assertNotNull(result);
        JSONObject validationResult = new JSONObject(result);
        assertTrue(validationResult.getBoolean("valid"));
        assertFalse(validationResult.has("error"));
    }
    
    @Test
    void testValidateJson_ValidNestedObject() {
        String validJson = "{\"user\": {\"name\": \"John\", \"age\": 30}, \"tags\": [\"admin\", \"user\"]}";
        String result = fileTools.validateJson(validJson);
        
        assertNotNull(result);
        JSONObject validationResult = new JSONObject(result);
        assertTrue(validationResult.getBoolean("valid"));
    }
    
    @Test
    void testValidateJson_ValidEmptyObject() {
        String validJson = "{}";
        String result = fileTools.validateJson(validJson);
        
        assertNotNull(result);
        JSONObject validationResult = new JSONObject(result);
        assertTrue(validationResult.getBoolean("valid"));
    }
    
    @Test
    void testValidateJson_ValidEmptyArray() {
        String validJson = "[]";
        String result = fileTools.validateJson(validJson);
        
        assertNotNull(result);
        JSONObject validationResult = new JSONObject(result);
        assertTrue(validationResult.getBoolean("valid"));
    }
    
    @Test
    void testValidateJson_ValidWithWhitespace() {
        String validJson = "   {\"key\": \"value\"}   ";
        String result = fileTools.validateJson(validJson);
        
        assertNotNull(result);
        JSONObject validationResult = new JSONObject(result);
        assertTrue(validationResult.getBoolean("valid"));
    }
    
    @Test
    void testValidateJson_NullInput() {
        String result = fileTools.validateJson(null);
        
        assertNotNull(result);
        JSONObject validationResult = new JSONObject(result);
        assertFalse(validationResult.getBoolean("valid"));
        assertTrue(validationResult.has("error"));
        assertTrue(validationResult.getString("error").contains("null"));
    }
    
    @Test
    void testValidateJson_EmptyString() {
        String result = fileTools.validateJson("");
        
        assertNotNull(result);
        JSONObject validationResult = new JSONObject(result);
        assertFalse(validationResult.getBoolean("valid"));
        assertTrue(validationResult.has("error"));
        assertTrue(validationResult.getString("error").contains("empty"));
    }
    
    @Test
    void testValidateJson_WhitespaceOnly() {
        String result = fileTools.validateJson("   ");
        
        assertNotNull(result);
        JSONObject validationResult = new JSONObject(result);
        assertFalse(validationResult.getBoolean("valid"));
        assertTrue(validationResult.has("error"));
    }
    
    @Test
    void testValidateJson_MissingClosingBrace() {
        String invalidJson = "{\"key\": \"value\"";
        String result = fileTools.validateJson(invalidJson);
        
        assertNotNull(result);
        JSONObject validationResult = new JSONObject(result);
        assertFalse(validationResult.getBoolean("valid"));
        assertTrue(validationResult.has("error"));
        assertNotNull(validationResult.getString("error"));
    }
    
    @Test
    void testValidateJson_MissingOpeningBrace() {
        String invalidJson = "\"key\": \"value\"}";
        String result = fileTools.validateJson(invalidJson);
        
        assertNotNull(result);
        JSONObject validationResult = new JSONObject(result);
        assertFalse(validationResult.getBoolean("valid"));
        assertTrue(validationResult.has("error"));
    }
    
    @Test
    void testValidateJson_MissingQuotes() {
        String invalidJson = "{key: \"value\"}";
        String result = fileTools.validateJson(invalidJson);
        
        assertNotNull(result);
        JSONObject validationResult = new JSONObject(result);
        assertFalse(validationResult.getBoolean("valid"));
        assertTrue(validationResult.has("error"));
    }
    
    @Test
    void testValidateJson_TrailingComma() {
        String invalidJson = "{\"key\": \"value\",}";
        String result = fileTools.validateJson(invalidJson);
        
        assertNotNull(result);
        JSONObject validationResult = new JSONObject(result);
        assertFalse(validationResult.getBoolean("valid"));
        assertTrue(validationResult.has("error"));
    }
    
    @Test
    void testValidateJson_DoubleComma() {
        String invalidJson = "{\"key\": \"value\",, \"key2\": \"value2\"}";
        String result = fileTools.validateJson(invalidJson);
        
        assertNotNull(result);
        JSONObject validationResult = new JSONObject(result);
        assertFalse(validationResult.getBoolean("valid"));
        assertTrue(validationResult.has("error"));
    }
    
    @Test
    void testValidateJson_InvalidEscape() {
        String invalidJson = "{\"key\": \"value\\x\"}";
        String result = fileTools.validateJson(invalidJson);
        
        assertNotNull(result);
        JSONObject validationResult = new JSONObject(result);
        assertFalse(validationResult.getBoolean("valid"));
        assertTrue(validationResult.has("error"));
    }
    
    @Test
    void testValidateJson_UnclosedString() {
        String invalidJson = "{\"key\": \"value}";
        String result = fileTools.validateJson(invalidJson);
        
        assertNotNull(result);
        JSONObject validationResult = new JSONObject(result);
        assertFalse(validationResult.getBoolean("valid"));
        assertTrue(validationResult.has("error"));
    }
    
    @Test
    void testValidateJson_InvalidArraySyntax() {
        String invalidJson = "[1, 2, 3";
        String result = fileTools.validateJson(invalidJson);
        
        assertNotNull(result);
        JSONObject validationResult = new JSONObject(result);
        assertFalse(validationResult.getBoolean("valid"));
        assertTrue(validationResult.has("error"));
    }
    
    @Test
    void testValidateJson_InvalidNumber() {
        String invalidJson = "{\"number\": 12.34.56}";
        String result = fileTools.validateJson(invalidJson);
        
        assertNotNull(result);
        JSONObject validationResult = new JSONObject(result);
        assertFalse(validationResult.getBoolean("valid"));
        assertTrue(validationResult.has("error"));
    }
    
    @Test
    void testValidateJson_InvalidBoolean() {
        String invalidJson = "{\"flag\": tru}";
        String result = fileTools.validateJson(invalidJson);
        
        assertNotNull(result);
        JSONObject validationResult = new JSONObject(result);
        assertFalse(validationResult.getBoolean("valid"));
        assertTrue(validationResult.has("error"));
    }
    
    @Test
    void testValidateJson_InvalidNull() {
        String invalidJson = "{\"value\": nul}";
        String result = fileTools.validateJson(invalidJson);
        
        assertNotNull(result);
        JSONObject validationResult = new JSONObject(result);
        assertFalse(validationResult.getBoolean("valid"));
        assertTrue(validationResult.has("error"));
    }
    
    @Test
    void testValidateJson_MismatchedBrackets() {
        String invalidJson = "{\"key\": [\"value\"}";
        String result = fileTools.validateJson(invalidJson);
        
        assertNotNull(result);
        JSONObject validationResult = new JSONObject(result);
        assertFalse(validationResult.getBoolean("valid"));
        assertTrue(validationResult.has("error"));
    }
    
    @Test
    void testValidateJson_ErrorIncludesLineAndColumn() {
        String invalidJson = "{\"key\": \"value\"";
        String result = fileTools.validateJson(invalidJson);
        
        assertNotNull(result);
        JSONObject validationResult = new JSONObject(result);
        assertFalse(validationResult.getBoolean("valid"));
        
        // Gson provides line and column numbers
        if (validationResult.has("line")) {
            int line = validationResult.getInt("line");
            assertTrue(line >= 1, "Line number should be >= 1");
        }
        if (validationResult.has("column")) {
            int column = validationResult.getInt("column");
            assertTrue(column >= 1, "Column number should be >= 1");
        }
        
        // Check if position is included when available
        if (validationResult.has("position")) {
            int position = validationResult.getInt("position");
            assertTrue(position >= 0);
        }
    }
    
    @Test
    void testValidateJson_ErrorIncludesContext() {
        String invalidJson = "{\"key\": \"value\"";
        String result = fileTools.validateJson(invalidJson);
        
        assertNotNull(result);
        JSONObject validationResult = new JSONObject(result);
        assertFalse(validationResult.getBoolean("valid"));
        
        // Check if context is included when position is available
        if (validationResult.has("position") && validationResult.getInt("position") >= 0) {
            if (validationResult.has("context")) {
                String context = validationResult.getString("context");
                assertNotNull(context);
                assertFalse(context.isEmpty());
            }
        }
    }
    
    @Test
    void testValidateJson_ErrorWithLineAndColumnInMultilineJson() {
        String invalidJson = "{\n" +
                "  \"key1\": \"value1\",\n" +
                "  \"key2\": \"value2\"\n" +  // Missing closing brace
                "  \"key3\": \"value3\"\n" +
                "}";
        String result = fileTools.validateJson(invalidJson);
        
        assertNotNull(result);
        JSONObject validationResult = new JSONObject(result);
        assertFalse(validationResult.getBoolean("valid"));
        
        // Should have line and column information
        if (validationResult.has("line")) {
            int line = validationResult.getInt("line");
            assertTrue(line >= 1);
        }
        if (validationResult.has("column")) {
            int column = validationResult.getInt("column");
            assertTrue(column >= 1);
        }
        assertTrue(validationResult.has("error"));
    }
    
    @Test
    void testValidateJson_ComplexValidJson() {
        String validJson = "{\n" +
                "  \"users\": [\n" +
                "    {\"id\": 1, \"name\": \"Alice\", \"active\": true},\n" +
                "    {\"id\": 2, \"name\": \"Bob\", \"active\": false}\n" +
                "  ],\n" +
                "  \"metadata\": {\n" +
                "    \"count\": 2,\n" +
                "    \"timestamp\": 1234567890\n" +
                "  }\n" +
                "}";
        String result = fileTools.validateJson(validJson);
        
        assertNotNull(result);
        JSONObject validationResult = new JSONObject(result);
        assertTrue(validationResult.getBoolean("valid"));
    }
    
    @Test
    void testValidateJson_ComplexInvalidJson() {
        String invalidJson = "{\n" +
                "  \"users\": [\n" +
                "    {\"id\": 1, \"name\": \"Alice\", \"active\": true},\n" +
                "    {\"id\": 2, \"name\": \"Bob\", \"active\": false\n" +  // Missing closing brace
                "  ],\n" +
                "  \"metadata\": {\n" +
                "    \"count\": 2\n" +
                "  }\n" +
                "}";
        String result = fileTools.validateJson(invalidJson);
        
        assertNotNull(result);
        JSONObject validationResult = new JSONObject(result);
        assertFalse(validationResult.getBoolean("valid"));
        assertTrue(validationResult.has("error"));
    }
    
    @Test
    void testValidateJson_ValidWithSpecialCharacters() {
        String validJson = "{\"message\": \"Hello \\\"World\\\"\", \"path\": \"C:\\\\Users\\\\Test\"}";
        String result = fileTools.validateJson(validJson);
        
        assertNotNull(result);
        JSONObject validationResult = new JSONObject(result);
        assertTrue(validationResult.getBoolean("valid"));
    }
    
    @Test
    void testValidateJson_ValidUnicode() {
        String validJson = "{\"text\": \"你好世界 🌍\", \"emoji\": \"😀\"}";
        String result = fileTools.validateJson(validJson);
        
        assertNotNull(result);
        JSONObject validationResult = new JSONObject(result);
        assertTrue(validationResult.getBoolean("valid"));
    }
    
    @Test
    void testValidateJson_InvalidUnicodeEscape() {
        String invalidJson = "{\"text\": \"\\uZZZZ\"}";
        String result = fileTools.validateJson(invalidJson);
        
        assertNotNull(result);
        JSONObject validationResult = new JSONObject(result);
        assertFalse(validationResult.getBoolean("valid"));
        assertTrue(validationResult.has("error"));
    }
    
    @Test
    void testValidateJson_PlainTextNotJson() {
        String invalidJson = "This is not JSON";
        String result = fileTools.validateJson(invalidJson);
        
        assertNotNull(result);
        JSONObject validationResult = new JSONObject(result);
        assertFalse(validationResult.getBoolean("valid"));
        assertTrue(validationResult.has("error"));
    }
    
    @Test
    void testValidateJson_NumberOnly() {
        String validJson = "123";
        String result = fileTools.validateJson(validJson);
        
        // A single number is not valid JSON (must be object or array)
        assertNotNull(result);
        JSONObject validationResult = new JSONObject(result);
        assertFalse(validationResult.getBoolean("valid"));
    }
    
    @Test
    void testValidateJson_StringOnly() {
        String invalidJson = "\"just a string\"";
        String result = fileTools.validateJson(invalidJson);
        
        // A single string is not valid JSON (must be object or array)
        assertNotNull(result);
        JSONObject validationResult = new JSONObject(result);
        assertFalse(validationResult.getBoolean("valid"));
    }
    
    // ========== validateJsonFile Tests ==========
    
    @Test
    void testValidateJsonFile_ValidJson() throws IOException {
        Path testFile = tempDir.resolve("valid.json");
        String validJson = "{\"key\": \"value\", \"number\": 123}";
        Files.writeString(testFile, validJson);
        
        String result = fileTools.validateJsonFile("valid.json");
        
        assertNotNull(result);
        JSONObject validationResult = new JSONObject(result);
        assertTrue(validationResult.getBoolean("valid"));
        assertEquals("valid.json", validationResult.getString("file"));
    }
    
    @Test
    void testValidateJsonFile_ValidArray() throws IOException {
        Path testFile = tempDir.resolve("array.json");
        String validJson = "[1, 2, 3, \"test\"]";
        Files.writeString(testFile, validJson);
        
        String result = fileTools.validateJsonFile("array.json");
        
        assertNotNull(result);
        JSONObject validationResult = new JSONObject(result);
        assertTrue(validationResult.getBoolean("valid"));
        assertEquals("array.json", validationResult.getString("file"));
    }
    
    @Test
    void testValidateJsonFile_ValidNested() throws IOException {
        Path testFile = tempDir.resolve("nested.json");
        String validJson = "{\"user\": {\"name\": \"John\", \"age\": 30}, \"tags\": [\"admin\"]}";
        Files.writeString(testFile, validJson);
        
        String result = fileTools.validateJsonFile("nested.json");
        
        assertNotNull(result);
        JSONObject validationResult = new JSONObject(result);
        assertTrue(validationResult.getBoolean("valid"));
        assertEquals("nested.json", validationResult.getString("file"));
    }
    
    @Test
    void testValidateJsonFile_InvalidJson() throws IOException {
        Path testFile = tempDir.resolve("invalid.json");
        String invalidJson = "{\"key\": \"value\"";
        Files.writeString(testFile, invalidJson);
        
        String result = fileTools.validateJsonFile("invalid.json");
        
        assertNotNull(result);
        JSONObject validationResult = new JSONObject(result);
        assertFalse(validationResult.getBoolean("valid"));
        assertEquals("invalid.json", validationResult.getString("file"));
        assertTrue(validationResult.has("error"));
    }
    
    @Test
    void testValidateJsonFile_InvalidJsonWithLineAndColumn() throws IOException {
        Path testFile = tempDir.resolve("invalid-multiline.json");
        String invalidJson = "{\n" +
                "  \"key1\": \"value1\",\n" +
                "  \"key2\": \"value2\"\n" +  // Missing closing brace
                "  \"key3\": \"value3\"\n" +
                "}";
        Files.writeString(testFile, invalidJson);
        
        String result = fileTools.validateJsonFile("invalid-multiline.json");
        
        assertNotNull(result);
        JSONObject validationResult = new JSONObject(result);
        assertFalse(validationResult.getBoolean("valid"));
        assertEquals("invalid-multiline.json", validationResult.getString("file"));
        assertTrue(validationResult.has("error"));
        
        if (validationResult.has("line")) {
            int line = validationResult.getInt("line");
            assertTrue(line >= 1);
        }
        if (validationResult.has("column")) {
            int column = validationResult.getInt("column");
            assertTrue(column >= 1);
        }
    }
    
    @Test
    void testValidateJsonFile_NonExistentFile() {
        String result = fileTools.validateJsonFile("non_existent.json");
        
        assertNotNull(result);
        JSONObject validationResult = new JSONObject(result);
        assertFalse(validationResult.getBoolean("valid"));
        assertEquals("non_existent.json", validationResult.getString("file"));
        assertTrue(validationResult.getString("error").contains("not found") || 
                   validationResult.getString("error").contains("unreadable"));
    }
    
    @Test
    void testValidateJsonFile_NullPath() {
        String result = fileTools.validateJsonFile(null);
        
        assertNotNull(result);
        JSONObject validationResult = new JSONObject(result);
        assertFalse(validationResult.getBoolean("valid"));
        assertTrue(validationResult.has("error"));
    }
    
    @Test
    void testValidateJsonFile_EmptyPath() {
        String result = fileTools.validateJsonFile("");
        
        assertNotNull(result);
        JSONObject validationResult = new JSONObject(result);
        assertFalse(validationResult.getBoolean("valid"));
        assertTrue(validationResult.has("error"));
    }
    
    @Test
    void testValidateJsonFile_EmptyFile() throws IOException {
        Path testFile = tempDir.resolve("empty.json");
        Files.writeString(testFile, "");
        
        String result = fileTools.validateJsonFile("empty.json");
        
        assertNotNull(result);
        JSONObject validationResult = new JSONObject(result);
        assertFalse(validationResult.getBoolean("valid"));
        assertEquals("empty.json", validationResult.getString("file"));
        assertTrue(validationResult.getString("error").contains("empty"));
    }
    
    @Test
    void testValidateJsonFile_WhitespaceOnlyFile() throws IOException {
        Path testFile = tempDir.resolve("whitespace.json");
        Files.writeString(testFile, "   \n\t  ");
        
        String result = fileTools.validateJsonFile("whitespace.json");
        
        assertNotNull(result);
        JSONObject validationResult = new JSONObject(result);
        assertFalse(validationResult.getBoolean("valid"));
        assertEquals("whitespace.json", validationResult.getString("file"));
        assertTrue(validationResult.has("error"));
    }
    
    @Test
    void testValidateJsonFile_SubDirectory() throws IOException {
        Path subDir = tempDir.resolve("outputs");
        Files.createDirectories(subDir);
        Path testFile = subDir.resolve("response.json");
        String validJson = "{\"status\": \"success\", \"data\": {}}";
        Files.writeString(testFile, validJson);
        
        String result = fileTools.validateJsonFile("outputs/response.json");
        
        assertNotNull(result);
        JSONObject validationResult = new JSONObject(result);
        assertTrue(validationResult.getBoolean("valid"));
        assertEquals("outputs/response.json", validationResult.getString("file"));
    }
    
    @Test
    void testValidateJsonFile_InvalidInSubDirectory() throws IOException {
        Path subDir = tempDir.resolve("data");
        Files.createDirectories(subDir);
        Path testFile = subDir.resolve("bad.json");
        String invalidJson = "{\"key\": \"value\",}";  // Trailing comma
        Files.writeString(testFile, invalidJson);
        
        String result = fileTools.validateJsonFile("data/bad.json");
        
        assertNotNull(result);
        JSONObject validationResult = new JSONObject(result);
        assertFalse(validationResult.getBoolean("valid"));
        assertEquals("data/bad.json", validationResult.getString("file"));
        assertTrue(validationResult.has("error"));
    }
    
    @Test
    void testValidateJsonFile_PathTraversalBlocked() {
        String result = fileTools.validateJsonFile("../../../etc/passwd");
        
        assertNotNull(result);
        JSONObject validationResult = new JSONObject(result);
        assertFalse(validationResult.getBoolean("valid"));
        assertTrue(validationResult.has("error"));
    }
    
    @Test
    void testValidateJsonFile_ComplexValidJson() throws IOException {
        Path testFile = tempDir.resolve("complex.json");
        String validJson = "{\n" +
                "  \"users\": [\n" +
                "    {\"id\": 1, \"name\": \"Alice\", \"active\": true},\n" +
                "    {\"id\": 2, \"name\": \"Bob\", \"active\": false}\n" +
                "  ],\n" +
                "  \"metadata\": {\n" +
                "    \"count\": 2,\n" +
                "    \"timestamp\": 1234567890\n" +
                "  }\n" +
                "}";
        Files.writeString(testFile, validJson);
        
        String result = fileTools.validateJsonFile("complex.json");
        
        assertNotNull(result);
        JSONObject validationResult = new JSONObject(result);
        assertTrue(validationResult.getBoolean("valid"));
        assertEquals("complex.json", validationResult.getString("file"));
    }
    
    @Test
    void testValidateJsonFile_InvalidWithContext() throws IOException {
        Path testFile = tempDir.resolve("context-test.json");
        String invalidJson = "{\"key\": \"value\"";
        Files.writeString(testFile, invalidJson);
        
        String result = fileTools.validateJsonFile("context-test.json");
        
        assertNotNull(result);
        JSONObject validationResult = new JSONObject(result);
        assertFalse(validationResult.getBoolean("valid"));
        assertEquals("context-test.json", validationResult.getString("file"));
        
        // Check if context is included when position is available
        if (validationResult.has("position") && validationResult.getInt("position") >= 0) {
            if (validationResult.has("context")) {
                String context = validationResult.getString("context");
                assertNotNull(context);
                assertFalse(context.isEmpty());
            }
        }
    }
    
    @Test
    void testValidateJsonFile_UTF8Content() throws IOException {
        Path testFile = tempDir.resolve("utf8.json");
        String validJson = "{\"text\": \"你好世界 🌍\", \"emoji\": \"😀\"}";
        Files.writeString(testFile, validJson);
        
        String result = fileTools.validateJsonFile("utf8.json");
        
        assertNotNull(result);
        JSONObject validationResult = new JSONObject(result);
        assertTrue(validationResult.getBoolean("valid"));
        assertEquals("utf8.json", validationResult.getString("file"));
    }
}
