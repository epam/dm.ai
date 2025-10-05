package com.github.istin.dmtools.context;

import com.github.istin.dmtools.ai.ChunkPreparation;
import com.github.istin.dmtools.ai.agent.ContentMergeAgent;
import com.github.istin.dmtools.ai.agent.SummaryContextAgent;
import com.github.istin.dmtools.common.model.ToText;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Test for ContextOrchestrator - validates context processing without actual external calls
 */
class ContextOrchestratorTest {

    @Mock
    private SummaryContextAgent mockSummaryAgent;

    @Mock
    private ContentMergeAgent mockContentMergeAgent;

    @Mock
    private UriToObject mockUriProcessor;

    private ContextOrchestrator orchestrator;
    private AutoCloseable mocks;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        orchestrator = new ContextOrchestrator(mockSummaryAgent, mockContentMergeAgent);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (orchestrator != null) {
            orchestrator.shutdown();
        }
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    void testConstructor() {
        assertNotNull(orchestrator);
    }

    @Test
    void testClear() {
        orchestrator.clear();
        // Should execute without throwing exception
    }

    @Test
    void testShutdown() {
        orchestrator.shutdown();
        // Should execute without throwing exception
    }

    @Test
    void testProcessFullContent_WithNull() throws Exception {
        MockUriProcessor processor = new MockUriProcessor();
        
        boolean result = orchestrator.processFullContent(null, "test", processor, null, 0);
        
        assertFalse(result);
    }

    @Test
    void testProcessFullContent_WithToTextObject() throws Exception {
        ToText toTextObject = () -> "Text content";
        MockUriProcessor processor = new MockUriProcessor();
        
        boolean result = orchestrator.processFullContent("key1", toTextObject, processor, null, 0);
        
        assertFalse(result);
    }

    @Test
    void testProcessFullContent_WithStringObject() throws Exception {
        MockUriProcessor processor = new MockUriProcessor();
        
        boolean result = orchestrator.processFullContent("key2", "simple string", processor, null, 0);
        
        assertFalse(result);
    }

    @Test
    void testProcessFullContent_WithFile() throws Exception {
        File testFile = tempDir.resolve("test.txt").toFile();
        FileUtils.writeStringToFile(testFile, "File content", "UTF-8");
        MockUriProcessor processor = new MockUriProcessor();
        
        try (MockedStatic<FileToTextTransformer> mockedTransformer = mockStatic(FileToTextTransformer.class)) {
            FileToTextTransformer.TransformationResult mockResult = 
                new FileToTextTransformer.TransformationResult("Transformed text", null);
            mockedTransformer.when(() -> FileToTextTransformer.transform(testFile))
                .thenReturn(List.of(mockResult));
            
            boolean result = orchestrator.processFullContent("fileKey", testFile, processor, null, 0);
            
            assertFalse(result);
            mockedTransformer.verify(() -> FileToTextTransformer.transform(testFile));
        }
    }

    @Test
    void testProcessFullContent_WithFileMultipleResults() throws Exception {
        File testFile = tempDir.resolve("test2.txt").toFile();
        FileUtils.writeStringToFile(testFile, "File content", "UTF-8");
        MockUriProcessor processor = new MockUriProcessor();
        
        try (MockedStatic<FileToTextTransformer> mockedTransformer = mockStatic(FileToTextTransformer.class)) {
            FileToTextTransformer.TransformationResult result1 = 
                new FileToTextTransformer.TransformationResult("Part 1", null);
            FileToTextTransformer.TransformationResult result2 = 
                new FileToTextTransformer.TransformationResult("Part 2", null);
            mockedTransformer.when(() -> FileToTextTransformer.transform(testFile))
                .thenReturn(List.of(result1, result2));
            
            boolean result = orchestrator.processFullContent("multiKey", testFile, processor, null, 0);
            
            assertFalse(result);
        }
    }

    @Test
    void testProcessFullContent_WithFileAndFiles() throws Exception {
        File testFile = tempDir.resolve("test3.txt").toFile();
        File attachedFile = tempDir.resolve("attached.jpg").toFile();
        FileUtils.writeStringToFile(testFile, "File content", "UTF-8");
        FileUtils.touch(attachedFile);
        MockUriProcessor processor = new MockUriProcessor();
        
        try (MockedStatic<FileToTextTransformer> mockedTransformer = mockStatic(FileToTextTransformer.class)) {
            FileToTextTransformer.TransformationResult mockResult = 
                new FileToTextTransformer.TransformationResult("Text", List.of(attachedFile));
            mockedTransformer.when(() -> FileToTextTransformer.transform(testFile))
                .thenReturn(List.of(mockResult));
            
            boolean result = orchestrator.processFullContent("fileWithAttachments", testFile, processor, null, 0);
            
            assertFalse(result);
        }
    }

    @Test
    void testProcessFullContent_WithFileNullTransformation() throws Exception {
        File testFile = tempDir.resolve("binary.pdf").toFile();
        FileUtils.touch(testFile);
        MockUriProcessor processor = new MockUriProcessor();
        
        try (MockedStatic<FileToTextTransformer> mockedTransformer = mockStatic(FileToTextTransformer.class)) {
            mockedTransformer.when(() -> FileToTextTransformer.transform(testFile))
                .thenReturn(null);
            
            boolean result = orchestrator.processFullContent("binaryKey", testFile, processor, null, 0);
            
            assertFalse(result);
        }
    }

    @Test
    void testProcessFullContent_AlreadyCached() throws Exception {
        MockUriProcessor processor = new MockUriProcessor();
        
        // First call
        orchestrator.processFullContent("cachedKey", "content", processor, null, 0);
        
        // Second call with same key and depth 0 - should be skipped
        boolean result = orchestrator.processFullContent("cachedKey", "content", processor, null, 0);
        
        assertFalse(result);
    }

    @Test
    void testProcessFullContent_WithDepthAndUriList() throws Exception {
        MockUriProcessor processor = new MockUriProcessor();
        List<UriToObject> uriList = List.of(processor);
        
        boolean result = orchestrator.processFullContent("depthKey", "content", processor, uriList, 1);
        
        assertFalse(result);
    }

    @Test
    void testProcessUrisInContent_EmptyUris() throws Exception {
        MockUriProcessor processor = new MockUriProcessor();
        List<UriToObject> uriList = List.of(processor);
        
        orchestrator.processUrisInContent("test content", uriList, 1);
        
        // Should complete without exception
    }

    @Test
    void testProcessUrisInContent_WithUris() throws Exception {
        TestableUriProcessor processor = new TestableUriProcessor();
        List<UriToObject> uriList = List.of(processor);
        
        orchestrator.processUrisInContent("content with uris", uriList, 1);
        
        // Give time for async processing
        Thread.sleep(200);
        
        // Should complete without exception
    }

    @Test
    void testSummarize() throws Exception {
        // Add some content first
        MockUriProcessor processor = new MockUriProcessor();
        orchestrator.processFullContent("sumKey1", "content1", processor, null, 0);
        orchestrator.processFullContent("sumKey2", "content2", processor, null, 0);
        
        List<ChunkPreparation.Chunk> chunks = orchestrator.summarize();
        
        assertNotNull(chunks);
    }

    @Test
    void testSummarize_EmptyContext() throws Exception {
        List<ChunkPreparation.Chunk> chunks = orchestrator.summarize();
        
        assertNotNull(chunks);
        assertTrue(chunks.isEmpty());
    }

    @Test
    void testClear_RemovesContent() throws Exception {
        MockUriProcessor processor = new MockUriProcessor();
        orchestrator.processFullContent("clearKey", "content", processor, null, 0);
        
        orchestrator.clear();
        
        // After clear, summarize should return empty
        List<ChunkPreparation.Chunk> chunks = orchestrator.summarize();
        assertNotNull(chunks);
        assertTrue(chunks.isEmpty());
    }

    @Test
    void testProcessFullContent_WithFileEmptyText() throws Exception {
        File testFile = tempDir.resolve("empty.txt").toFile();
        FileUtils.writeStringToFile(testFile, "", "UTF-8");
        MockUriProcessor processor = new MockUriProcessor();
        
        try (MockedStatic<FileToTextTransformer> mockedTransformer = mockStatic(FileToTextTransformer.class)) {
            FileToTextTransformer.TransformationResult result1 = 
                new FileToTextTransformer.TransformationResult("", null);
            FileToTextTransformer.TransformationResult result2 = 
                new FileToTextTransformer.TransformationResult("", List.of());
            mockedTransformer.when(() -> FileToTextTransformer.transform(testFile))
                .thenReturn(List.of(result1, result2));
            
            boolean result = orchestrator.processFullContent("emptyKey", testFile, processor, null, 0);
            
            assertFalse(result);
        }
    }

    @Test
    void testMultipleProcessors() throws Exception {
        MockUriProcessor processor1 = new MockUriProcessor();
        MockUriProcessor processor2 = new MockUriProcessor();
        
        List<UriToObject> processors = List.of(processor1, processor2);
        
        orchestrator.processUrisInContent("content", processors, 1);
        
        // Should complete without exception
    }

    // Helper mock class for testing
    private static class MockUriProcessor implements UriToObject {
        @Override
        public Set<String> parseUris(String object) {
            return Collections.emptySet();
        }

        @Override
        public Object uriToObject(String uri) {
            return null;
        }
    }
    
    // Testable processor with URIs
    private static class TestableUriProcessor implements UriToObject {
        @Override
        public Set<String> parseUris(String object) {
            return Set.of("uri1", "uri2");
        }

        @Override
        public Object uriToObject(String uri) {
            return "object for " + uri;
        }
    }
}
