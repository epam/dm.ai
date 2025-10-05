package com.github.istin.dmtools.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ChunkPreparationTest {

    @Mock
    private TokenCounter mockTokenCounter;

    private ChunkPreparation chunkPreparation;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(mockTokenCounter.countTokens(anyString())).thenReturn(10);
        chunkPreparation = new ChunkPreparation(mockTokenCounter);
    }

    @Test
    void testConstructorWithTokenCounter() {
        ChunkPreparation cp = new ChunkPreparation(mockTokenCounter);
        assertNotNull(cp);
        assertTrue(cp.getTokenLimit() > 0);
    }

    @Test
    void testConstructorDefault() {
        ChunkPreparation cp = new ChunkPreparation();
        assertNotNull(cp);
        assertTrue(cp.getTokenLimit() > 0);
    }

    @Test
    void testPrepareChunks_EmptyCollection() throws Exception {
        List<ChunkPreparation.Chunk> result = chunkPreparation.prepareChunks(List.of());
        
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testPrepareChunks_SingleString() throws Exception {
        String text = "Test text";
        List<ChunkPreparation.Chunk> result = chunkPreparation.prepareChunks(List.of(text));
        
        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.get(0).getText().contains(text));
    }

    @Test
    void testPrepareChunks_MultipleStrings() throws Exception {
        List<String> texts = List.of("Text 1", "Text 2", "Text 3");
        List<ChunkPreparation.Chunk> result = chunkPreparation.prepareChunks(texts);
        
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    void testPrepareChunks_WithTokenLimit() throws Exception {
        String text = "Test text";
        List<ChunkPreparation.Chunk> result = chunkPreparation.prepareChunks(List.of(text), 1000);
        
        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    void testPrepareChunks_SmallTokenLimit() throws Exception {
        // Use a reasonable token count that won't cause splitting issues
        when(mockTokenCounter.countTokens(anyString())).thenReturn(5);
        List<ChunkPreparation.Chunk> result = chunkPreparation.prepareChunks(List.of("Text1", "Text2"), 15);
        
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    void testChunk_Constructor() {
        String text = "Test text";
        List<File> files = List.of(new File("test.txt"));
        long size = 1000L;
        
        ChunkPreparation.Chunk chunk = new ChunkPreparation.Chunk(text, files, size);
        
        assertEquals(text, chunk.getText());
        assertEquals(1, chunk.getFiles().size());
        assertEquals(size, chunk.getTotalFilesSize());
    }

    @Test
    void testChunk_ConstructorWithNullFiles() {
        String text = "Test text";
        
        ChunkPreparation.Chunk chunk = new ChunkPreparation.Chunk(text, null, 0);
        
        assertEquals(text, chunk.getText());
        assertNull(chunk.getFiles());
        assertEquals(0, chunk.getTotalFilesSize());
    }

    @Test
    void testChunk_ToString() {
        String text = "Test text";
        ChunkPreparation.Chunk chunk = new ChunkPreparation.Chunk(text, null, 0);
        
        String result = chunk.toString();
        assertTrue(result.contains(text));
    }

    @Test
    void testChunk_ToStringWithFiles() {
        String text = "Test text";
        List<File> files = List.of(new File("test.txt"));
        
        ChunkPreparation.Chunk chunk = new ChunkPreparation.Chunk(text, files, 1000);
        
        String result = chunk.toString();
        assertTrue(result.contains(text));
        assertTrue(result.contains("files"));
    }

    @Test
    void testGetTokenLimit() {
        int tokenLimit = chunkPreparation.getTokenLimit();
        assertTrue(tokenLimit > 0);
    }
}