// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.ai;

import com.github.istin.dmtools.common.model.ToText;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ChunkPreparationCoverageTest {

    @Mock
    private TokenCounter mockTokenCounter;

    private ChunkPreparation chunkPreparation;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // Default: 1 token per character
        when(mockTokenCounter.countTokens(anyString()))
                .thenAnswer(inv -> ((String) inv.getArgument(0)).length());
        chunkPreparation = new ChunkPreparation(mockTokenCounter);
    }

    private File createFile(String name, long size) throws IOException {
        File file = tempDir.resolve(name).toFile();
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            raf.setLength(size); // sparse file, no real disk usage
        }
        return file;
    }

    @Test
    void testPrepareChunks_FilesExceedMaxFilesPerChunk() throws Exception {
        List<File> files = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            files.add(createFile("f" + i + ".txt", 10));
        }

        List<ChunkPreparation.Chunk> chunks = chunkPreparation.prepareChunks(files, 50000);

        // Default max files per chunk is 10 -> expect a second chunk
        assertEquals(2, chunks.size());
        assertEquals(10, chunks.get(0).getFiles().size());
        assertEquals(2, chunks.get(1).getFiles().size());
    }

    @Test
    void testPrepareChunks_FileLargerThanSingleFileLimitIsSkipped() throws Exception {
        File bigFile = createFile("big.bin", 5L * 1024 * 1024); // > default 4MB
        File smallFile = createFile("small.txt", 100);

        List<ChunkPreparation.Chunk> chunks = chunkPreparation
                .prepareChunks(List.of(bigFile, smallFile), 50000);

        assertEquals(1, chunks.size());
        assertEquals(1, chunks.get(0).getFiles().size());
        assertEquals(smallFile, chunks.get(0).getFiles().get(0));
    }

    @Test
    void testPrepareChunks_TotalFilesSizeExceededCreatesNewChunk() throws Exception {
        File first = createFile("first.bin", 3L * 1024 * 1024);
        File second = createFile("second.bin", 3L * 1024 * 1024);

        List<ChunkPreparation.Chunk> chunks = chunkPreparation
                .prepareChunks(List.of(first, second), 50000);

        // Default total files size is 4MB -> second file goes to a new chunk
        assertEquals(2, chunks.size());
        assertEquals(1, chunks.get(0).getFiles().size());
        assertEquals(1, chunks.get(1).getFiles().size());
        assertEquals(3L * 1024 * 1024, chunks.get(0).getTotalFilesSize());
    }

    @Test
    void testPrepareChunks_MapEntriesWithFiles() throws Exception {
        when(mockTokenCounter.countTokens(anyString())).thenReturn(10);
        Map<String, File> map = new LinkedHashMap<>();
        map.put("key1", createFile("a.txt", 10));
        map.put("key2", createFile("b.txt", 10));

        List<ChunkPreparation.Chunk> chunks = chunkPreparation.prepareChunks(map.entrySet(), 1000);

        assertEquals(1, chunks.size());
        assertEquals(2, chunks.get(0).getFiles().size());
        assertTrue(chunks.get(0).getText().contains("key1"));
        assertTrue(chunks.get(0).getText().contains(",\n"));
        assertTrue(chunks.get(0).getText().contains("key2"));
    }

    @Test
    void testPrepareChunks_MapEntryKeyTokensOverflowCreatesNewChunk() throws Exception {
        when(mockTokenCounter.countTokens(anyString())).thenReturn(10);
        Map<String, File> map = new LinkedHashMap<>();
        map.put("key1", createFile("a.txt", 10));
        map.put("key2", createFile("b.txt", 10));

        List<ChunkPreparation.Chunk> chunks = chunkPreparation.prepareChunks(map.entrySet(), 15);

        assertEquals(2, chunks.size());
        assertTrue(chunks.get(0).getText().contains("key1"));
        assertTrue(chunks.get(1).getText().contains("key2"));
    }

    @Test
    void testPrepareChunks_MapEntryFileOverflowForcesNewChunk() throws Exception {
        when(mockTokenCounter.countTokens(anyString())).thenReturn(1);
        List<Object> objects = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            objects.add(createFile("f" + i + ".txt", 10));
        }
        Map<String, File> map = new LinkedHashMap<>();
        map.put("extra", createFile("extra.txt", 10));
        objects.addAll(map.entrySet());

        List<ChunkPreparation.Chunk> chunks = chunkPreparation.prepareChunks(objects, 50000);

        assertEquals(2, chunks.size());
        assertEquals(10, chunks.get(0).getFiles().size());
        assertEquals(1, chunks.get(1).getFiles().size());
        assertTrue(chunks.get(1).getText().contains("extra"));
    }

    @Test
    void testPrepareChunks_MapEntryWithNonFileValue() throws Exception {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("key", "value");

        List<ChunkPreparation.Chunk> chunks = chunkPreparation.prepareChunks(map.entrySet(), 1000);

        assertEquals(1, chunks.size());
        assertTrue(chunks.get(0).getText().contains("key=value"));
        assertTrue(chunks.get(0).getFiles().isEmpty());
    }

    @Test
    void testPrepareChunks_ToTextObject() throws Exception {
        ToText toText = () -> "text from ToText";

        List<ChunkPreparation.Chunk> chunks = chunkPreparation.prepareChunks(List.of(toText), 1000);

        assertEquals(1, chunks.size());
        assertTrue(chunks.get(0).getText().contains("text from ToText"));
    }

    @Test
    void testPrepareChunks_MixedObjects() throws Exception {
        List<Object> objects = new ArrayList<>();
        objects.add("plain string");
        objects.add(createFile("file.txt", 50));
        objects.add((ToText) () -> "toText content");
        objects.add(42);

        List<ChunkPreparation.Chunk> chunks = chunkPreparation.prepareChunks(objects, 50000);

        assertEquals(1, chunks.size());
        assertEquals(1, chunks.get(0).getFiles().size());
        String text = chunks.get(0).getText();
        assertTrue(text.contains("plain string"));
        assertTrue(text.contains("toText content"));
        assertTrue(text.contains("42"));
    }

    @Test
    void testSplitLargeObject_CommaBreaks() throws Exception {
        String text = "aaaa,bbbb,cccc,dddd";

        List<ChunkPreparation.Chunk> chunks = chunkPreparation.prepareChunks(List.of(text), 10);

        assertTrue(chunks.size() > 1);
        StringBuilder reassembled = new StringBuilder();
        for (ChunkPreparation.Chunk chunk : chunks) {
            reassembled.append(chunk.getText());
        }
        assertEquals(text, reassembled.toString());
    }

    @Test
    void testSplitLargeObject_NewlineBreaks() throws Exception {
        String text = "aaaa\nbbbb\ncccc\ndddd";

        List<ChunkPreparation.Chunk> chunks = chunkPreparation.prepareChunks(List.of(text), 10);

        assertTrue(chunks.size() > 1);
        StringBuilder reassembled = new StringBuilder();
        for (ChunkPreparation.Chunk chunk : chunks) {
            reassembled.append(chunk.getText());
        }
        assertEquals(text, reassembled.toString());
    }

    @Test
    void testSplitLargeObject_SpaceBreakWithLookAhead() throws Exception {
        String text = "aaa bbb ccc ddd";

        List<ChunkPreparation.Chunk> chunks = chunkPreparation.prepareChunks(List.of(text), 12);

        assertEquals(2, chunks.size());
        assertEquals("aaa bbb ccc ", chunks.get(0).getText());
        assertEquals("ddd", chunks.get(1).getText());
    }

    @Test
    void testSplitLargeObject_LookAheadFindsHighPriorityBreak() throws Exception {
        String text = "aaaaaa aa b,cccc";

        List<ChunkPreparation.Chunk> chunks = chunkPreparation.prepareChunks(List.of(text), 8);

        assertEquals(3, chunks.size());
        assertEquals("aaaaaa ", chunks.get(0).getText());
        assertEquals("aa b,", chunks.get(1).getText());
        assertEquals("cccc", chunks.get(2).getText());
    }

    @Test
    void testSplitLargeObject_OversizedSegmentWithBreakInsideBudget() throws Exception {
        // Multi-char strings count 100 tokens, single chars count 1 token,
        // so findBestBreakPosition can reach the comma inside the segment.
        when(mockTokenCounter.countTokens(anyString())).thenAnswer(inv -> {
            String s = inv.getArgument(0);
            return s.length() > 1 ? 100 : 1;
        });
        String text = "aaaaa,bbbbb,";

        List<ChunkPreparation.Chunk> chunks = chunkPreparation.prepareChunks(List.of(text), 10);

        assertEquals(2, chunks.size());
        assertEquals("aaaaa,", chunks.get(0).getText());
        assertEquals("bbbbb,", chunks.get(1).getText());
    }

    @Test
    void testSplitLargeObject_FallbackCharSplitWithoutBreaks() throws Exception {
        String text = "aaaaaaaaaaaaaaaaaaaa,";

        List<ChunkPreparation.Chunk> chunks = chunkPreparation.prepareChunks(List.of(text), 10);

        assertTrue(chunks.size() > 1);
        StringBuilder reassembled = new StringBuilder();
        for (ChunkPreparation.Chunk chunk : chunks) {
            reassembled.append(chunk.getText());
        }
        assertEquals(text, reassembled.toString());
    }

    @Test
    void testPrepareChunks_TextExactlyAtTokenLimit() throws Exception {
        List<ChunkPreparation.Chunk> chunks = chunkPreparation
                .prepareChunks(List.of("aaaa", "bbbb"), 8);

        // 4 + 4 fits exactly into one chunk
        assertEquals(1, chunks.size());
        assertEquals("aaaa\nbbbb", chunks.get(0).getText());
    }

    @Test
    void testPrepareChunks_TextOverflowSplitsIntoChunks() throws Exception {
        List<ChunkPreparation.Chunk> chunks = chunkPreparation
                .prepareChunks(List.of("aaaa", "bbbb", "cc"), 8);

        assertEquals(2, chunks.size());
        assertEquals("aaaa\nbbbb", chunks.get(0).getText());
        assertEquals("cc", chunks.get(1).getText());
    }

    @Test
    void testChunk_FilesListIsUnmodifiable() {
        List<File> files = new ArrayList<>(List.of(new File("test.txt")));
        ChunkPreparation.Chunk chunk = new ChunkPreparation.Chunk("text", files, 100);

        assertThrows(UnsupportedOperationException.class,
                () -> chunk.getFiles().add(new File("other.txt")));
    }
}
