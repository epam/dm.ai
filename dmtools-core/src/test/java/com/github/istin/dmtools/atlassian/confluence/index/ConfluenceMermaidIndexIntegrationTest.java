package com.github.istin.dmtools.atlassian.confluence.index;

import com.github.istin.dmtools.atlassian.confluence.Confluence;
import com.github.istin.dmtools.atlassian.confluence.model.Attachment;
import com.github.istin.dmtools.atlassian.confluence.model.Content;
import com.github.istin.dmtools.atlassian.confluence.model.SearchResult;
import com.github.istin.dmtools.atlassian.confluence.model.Storage;
import com.github.istin.dmtools.index.mermaid.MermaidIndexIntegration;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ConfluenceMermaidIndexIntegration
 */
class ConfluenceMermaidIndexIntegrationTest {

    @Mock
    private Confluence mockConfluence;

    private ConfluenceMermaidIndexIntegration integration;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        integration = new ConfluenceMermaidIndexIntegration(mockConfluence);
    }

    @Test
    void testGetContentForIndexWithIncludePattern() throws IOException {
        // Given
        List<String> includePatterns = List.of("TEST*");
        List<String> excludePatterns = new ArrayList<>();
        
        SearchResult searchResult = mock(SearchResult.class);
        when(searchResult.getId()).thenReturn("123");
        
        Content content = createMockContent("123", "TEST Page", "TEST", "<p>Test content</p>");
        
        when(mockConfluence.searchContentByText(eq("TEST"), eq(100)))
                .thenReturn(List.of(searchResult));
        when(mockConfluence.contentById(eq("123"))).thenReturn(content);
        when(mockConfluence.getContentAttachments(eq("123"))).thenReturn(new ArrayList<>());

        MermaidIndexIntegration.ContentProcessor processor = mock(MermaidIndexIntegration.ContentProcessor.class);

        // When
        integration.getContentForIndex(includePatterns, excludePatterns, processor);

        // Then
        verify(processor, times(1)).process(
                eq("TEST/123"),
                eq("TEST Page"),
                eq("<p>Test content</p>"),
                any(),
                any()
        );
    }

    @Test
    void testGetContentForIndexWithExcludePattern() throws IOException {
        // Given
        List<String> includePatterns = List.of("TEST*");
        List<String> excludePatterns = List.of("TEST-EXCLUDE*");
        
        SearchResult searchResult = mock(SearchResult.class);
        when(searchResult.getId()).thenReturn("123");
        
        Content content = createMockContent("123", "TEST-EXCLUDE Page", "TEST-EXCLUDE", "<p>Test content</p>");
        
        when(mockConfluence.searchContentByText(eq("TEST"), eq(100)))
                .thenReturn(List.of(searchResult));
        when(mockConfluence.contentById(eq("123"))).thenReturn(content);

        MermaidIndexIntegration.ContentProcessor processor = mock(MermaidIndexIntegration.ContentProcessor.class);

        // When
        integration.getContentForIndex(includePatterns, excludePatterns, processor);

        // Then
        verify(processor, never()).process(any(), any(), any(), any(), any());
    }

    @Test
    void testGetContentForIndexWithAttachments() throws IOException {
        // Given
        List<String> includePatterns = List.of("TEST");
        List<String> excludePatterns = new ArrayList<>();
        
        SearchResult searchResult = mock(SearchResult.class);
        when(searchResult.getId()).thenReturn("123");
        
        Content content = createMockContent("123", "TEST Page", "TEST", "<p>Test content</p>");
        
        Attachment attachment = mock(Attachment.class);
        when(attachment.getTitle()).thenReturn("test.pdf");
        
        when(mockConfluence.searchContentByText(eq("TEST"), eq(100)))
                .thenReturn(List.of(searchResult));
        when(mockConfluence.contentById(eq("123"))).thenReturn(content);
        when(mockConfluence.getContentAttachments(eq("123"))).thenReturn(List.of(attachment));

        MermaidIndexIntegration.ContentProcessor processor = mock(MermaidIndexIntegration.ContentProcessor.class);

        // When
        integration.getContentForIndex(includePatterns, excludePatterns, processor);

        // Then
        verify(processor, times(1)).process(
                eq("TEST/123"),
                eq("TEST Page"),
                eq("<p>Test content</p>"),
                argThat(metadata -> metadata.contains("attachment:test.pdf")),
                any()
        );
    }

    private Content createMockContent(String id, String title, String spaceKey, String body) {
        Content content = mock(Content.class);
        when(content.getId()).thenReturn(id);
        when(content.getTitle()).thenReturn(title);
        when(content.getLastModifiedDate()).thenReturn(new Date());
        
        JSONObject jsonObject = new JSONObject();
        JSONObject expandable = new JSONObject();
        expandable.put("space", "/rest/api/space/" + spaceKey);
        jsonObject.put("_expandable", expandable);
        jsonObject.put("id", id);
        jsonObject.put("title", title);
        
        JSONObject storage = new JSONObject();
        storage.put("value", body);
        JSONObject bodyObj = new JSONObject();
        bodyObj.put("storage", storage);
        jsonObject.put("body", bodyObj);
        
        JSONObject version = new JSONObject();
        version.put("when", "2023-01-01T00:00:00.000Z");
        jsonObject.put("version", version);
        
        when(content.getJSONObject()).thenReturn(jsonObject);
        
        Storage storageObj = mock(Storage.class);
        when(storageObj.getValue()).thenReturn(body);
        when(content.getStorage()).thenReturn(storageObj);
        
        return content;
    }
}
