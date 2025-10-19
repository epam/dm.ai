package com.github.istin.dmtools.common.kb.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class KBFileReaderTest {
    
    private KBFileReader reader;
    
    @TempDir
    Path tempDir;
    
    @BeforeEach
    void setUp() {
        reader = new KBFileReader();
    }
    
    @Test
    void testReadUtf8File() throws IOException {
        // Create UTF-8 file
        Path file = tempDir.resolve("utf8.txt");
        String content = "Hello UTF-8! Привет мир! 你好世界!";
        Files.writeString(file, content, StandardCharsets.UTF_8);
        
        // Read and verify
        String result = reader.readAndNormalize(file);
        assertEquals(content, result);
    }
    
    @Test
    void testReadLatin1File() throws IOException {
        // Create Latin-1 file
        Path file = tempDir.resolve("latin1.txt");
        String content = "Hello Latin-1! Café résumé";
        Files.write(file, content.getBytes(StandardCharsets.ISO_8859_1));
        
        // Read and verify
        String result = reader.readAndNormalize(file);
        assertEquals(content, result);
    }
    
    @Test
    void testNormalizeLineEndingsCRLF() {
        String input = "Line 1\r\nLine 2\r\nLine 3";
        String expected = "Line 1\nLine 2\nLine 3";
        
        String result = reader.normalizeLineEndings(input);
        assertEquals(expected, result);
    }
    
    @Test
    void testNormalizeLineEndingsCR() {
        String input = "Line 1\rLine 2\rLine 3";
        String expected = "Line 1\nLine 2\nLine 3";
        
        String result = reader.normalizeLineEndings(input);
        assertEquals(expected, result);
    }
    
    @Test
    void testNormalizeLineEndingsLF() {
        String input = "Line 1\nLine 2\nLine 3";
        String expected = "Line 1\nLine 2\nLine 3";
        
        String result = reader.normalizeLineEndings(input);
        assertEquals(expected, result);
    }
    
    @Test
    void testNormalizeLineEndingsNEL() {
        String input = "Line 1\u0085Line 2\u0085Line 3";
        String expected = "Line 1\nLine 2\nLine 3";
        
        String result = reader.normalizeLineEndings(input);
        assertEquals(expected, result);
    }
    
    @Test
    void testNormalizeLineEndingsMixed() {
        String input = "Line 1\r\nLine 2\nLine 3\rLine 4\u0085Line 5";
        String expected = "Line 1\nLine 2\nLine 3\nLine 4\nLine 5";
        
        String result = reader.normalizeLineEndings(input);
        assertEquals(expected, result);
    }
    
    @Test
    void testNormalizeLineEndingsNull() {
        String result = reader.normalizeLineEndings(null);
        assertNull(result);
    }
    
    @Test
    void testNormalizeLineEndingsEmpty() {
        String result = reader.normalizeLineEndings("");
        assertEquals("", result);
    }
    
    @Test
    void testReadAndNormalizeWithMixedLineEndings() throws IOException {
        // Create file with mixed line endings
        Path file = tempDir.resolve("mixed.txt");
        byte[] content = "Line 1\r\nLine 2\nLine 3\rLine 4".getBytes(StandardCharsets.UTF_8);
        Files.write(file, content);
        
        // Read and verify line endings are normalized
        String result = reader.readAndNormalize(file);
        assertEquals("Line 1\nLine 2\nLine 3\nLine 4", result);
        assertFalse(result.contains("\r\n"));
        assertFalse(result.contains("\r"));
    }
    
    @Test
    void testReadNonExistentFile() {
        Path nonExistent = tempDir.resolve("does-not-exist.txt");
        
        assertThrows(IOException.class, () -> {
            reader.readAndNormalize(nonExistent);
        });
    }
}

