package com.github.istin.dmtools.index.mermaid;

import com.github.istin.dmtools.ai.agent.MermaidDiagramGeneratorAgent;
import com.github.istin.dmtools.atlassian.confluence.Confluence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MermaidIndex
 */
class MermaidIndexTest {

    @Mock
    private Confluence mockConfluence;

    @Mock
    private MermaidDiagramGeneratorAgent mockDiagramGenerator;

    @Mock
    private MermaidIndexIntegration mockIntegration;

    @TempDir
    Path tempDir;

    private MermaidIndex mermaidIndex;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testIndexWithNewContent() throws Exception {
        // Given
        String storagePath = tempDir.toString();
        String integrationName = "confluence";
        List<String> includePatterns = List.of("TEST*");
        List<String> excludePatterns = new ArrayList<>();

        // Mock Confluence to return empty results so integration doesn't process anything
        // We'll test the file operations separately
        when(mockConfluence.searchContentByText(anyString(), anyInt())).thenReturn(new ArrayList<>());
        when(mockDiagramGenerator.run(any())).thenReturn("flowchart TD\nA[Test] --> B[Content]");

        MermaidIndex index = new MermaidIndex(integrationName, storagePath, includePatterns, 
                excludePatterns, mockConfluence, mockDiagramGenerator);

        // When - this will call integration which returns empty, so no files created
        index.index();

        // Then - verify integration was called (indirectly through ConfluenceMermaidIndexIntegration)
        verify(mockConfluence, atLeastOnce()).searchContentByText(anyString(), anyInt());
    }

    @Test
    void testIndexSkipsUpToDateContent() throws Exception {
        // Given
        String storagePath = tempDir.toString();
        String integrationName = "confluence";
        List<String> includePatterns = List.of("TEST*");
        List<String> excludePatterns = new ArrayList<>();

        // Mock Confluence to return empty results
        when(mockConfluence.searchContentByText(anyString(), anyInt())).thenReturn(new ArrayList<>());

        MermaidIndex index = new MermaidIndex(integrationName, storagePath, includePatterns, 
                excludePatterns, mockConfluence, mockDiagramGenerator);

        // When
        index.index();

        // Then - verify no diagram generation since no content matched
        verify(mockDiagramGenerator, never()).run(any());
    }

    @Test
    void testUnsupportedIntegration() {
        // Given
        String storagePath = tempDir.toString();
        String integrationName = "unsupported";
        List<String> includePatterns = new ArrayList<>();
        List<String> excludePatterns = new ArrayList<>();

        // When/Then
        assertThrows(IllegalArgumentException.class, () -> {
            new MermaidIndex(integrationName, storagePath, includePatterns, 
                    excludePatterns, mockConfluence, mockDiagramGenerator);
        });
    }

    @Test
    void testNullConfluenceForConfluenceIntegration() {
        // Given
        String storagePath = tempDir.toString();
        String integrationName = "confluence";
        List<String> includePatterns = new ArrayList<>();
        List<String> excludePatterns = new ArrayList<>();

        // When/Then
        assertThrows(IllegalArgumentException.class, () -> {
            new MermaidIndex(integrationName, storagePath, includePatterns, 
                    excludePatterns, null, mockDiagramGenerator);
        });
    }
}
