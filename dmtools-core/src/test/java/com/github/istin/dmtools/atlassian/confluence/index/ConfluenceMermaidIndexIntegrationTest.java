package com.github.istin.dmtools.atlassian.confluence.index;

import com.github.istin.dmtools.atlassian.confluence.Confluence;
import com.github.istin.dmtools.atlassian.confluence.model.Attachment;
import com.github.istin.dmtools.atlassian.confluence.model.Content;
import com.github.istin.dmtools.atlassian.confluence.model.ContentResult;
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
 * Unit tests for ConfluenceMermaidIndexIntegration.
 * Tests the new path-based pattern retrieval.
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

    // ==================== Direct Page Only Tests ====================

    @Test
    void testGetContentForIndexWithDirectPagePattern() throws IOException {
        // Given: [SPACE]/pages/[pageId]/[PageName]
        List<String> includePatterns = List.of("MYSPACE/pages/123/MyPage");
        List<String> excludePatterns = new ArrayList<>();
        
        Content content = createMockContent("123", "MyPage", "MYSPACE", "<p>Test content</p>");
        
        when(mockConfluence.contentById(eq("123"))).thenReturn(content);
        when(mockConfluence.getContentAttachments(eq("123"))).thenReturn(new ArrayList<>());

        MermaidIndexIntegration.ContentProcessor processor = mock(MermaidIndexIntegration.ContentProcessor.class);

        // When
        integration.getContentForIndex(includePatterns, excludePatterns, processor);

        // Then
        verify(processor, times(1)).process(
                eq("MYSPACE/123"),
                eq("MyPage"),
                eq("<p>Test content</p>"),
                any(),
                any(),
                any()
        );
        // Verify no search was called - we use direct API
        verify(mockConfluence, never()).searchContentByText(anyString(), anyInt());
    }

    @Test
    void testGetContentForIndexWithUrlPattern() throws IOException {
        // Given: full URL format
        String urlPattern = "https://company.atlassian.net/wiki/spaces/MYSPACE/pages/456/Test+Page";
        List<String> includePatterns = List.of(urlPattern);
        List<String> excludePatterns = new ArrayList<>();
        
        Content content = createMockContent("456", "Test Page", "MYSPACE", "<p>URL content</p>");
        
        when(mockConfluence.contentById(eq("456"))).thenReturn(content);
        when(mockConfluence.getContentAttachments(eq("456"))).thenReturn(new ArrayList<>());

        MermaidIndexIntegration.ContentProcessor processor = mock(MermaidIndexIntegration.ContentProcessor.class);

        // When
        integration.getContentForIndex(includePatterns, excludePatterns, processor);

        // Then
        verify(processor, times(1)).process(
                eq("MYSPACE/456"),
                eq("Test Page"),
                eq("<p>URL content</p>"),
                any(),
                any(),
                any()
        );
    }

    // ==================== Immediate Children Tests ====================

    @Test
    void testGetContentForIndexWithImmediateChildrenPattern() throws IOException {
        // Given: [SPACE]/pages/[pageId]/[PageName]/*
        List<String> includePatterns = List.of("MYSPACE/pages/100/Parent/*");
        List<String> excludePatterns = new ArrayList<>();
        
        Content parentContent = createMockContent("100", "Parent", "MYSPACE", "<p>Parent content</p>");
        Content childContent1 = createMockContent("101", "Child1", "MYSPACE", "<p>Child1 content</p>");
        Content childContent2 = createMockContent("102", "Child2", "MYSPACE", "<p>Child2 content</p>");
        
        when(mockConfluence.contentById(eq("100"))).thenReturn(parentContent);
        when(mockConfluence.getChildrenOfContentById(eq("100"))).thenReturn(List.of(childContent1, childContent2));
        when(mockConfluence.getContentAttachments(anyString())).thenReturn(new ArrayList<>());

        MermaidIndexIntegration.ContentProcessor processor = mock(MermaidIndexIntegration.ContentProcessor.class);

        // When
        integration.getContentForIndex(includePatterns, excludePatterns, processor);

        // Then - should process parent and both children
        verify(processor, times(3)).process(anyString(), anyString(), anyString(), any(), any(), any());
        verify(processor).process(eq("MYSPACE/100"), eq("Parent"), anyString(), any(), any(), any());
        verify(processor).process(eq("MYSPACE/101"), eq("Child1"), anyString(), any(), any(), any());
        verify(processor).process(eq("MYSPACE/102"), eq("Child2"), anyString(), any(), any(), any());
    }

    // ==================== All Descendants Tests ====================

    @Test
    void testGetContentForIndexWithAllDescendantsPattern() throws IOException {
        // Given: [SPACE]/pages/[pageId]/[PageName]/**
        List<String> includePatterns = List.of("MYSPACE/pages/200/Root/**");
        List<String> excludePatterns = new ArrayList<>();
        
        Content rootContent = createMockContent("200", "Root", "MYSPACE", "<p>Root content</p>");
        Content childContent = createMockContent("201", "Child", "MYSPACE", "<p>Child content</p>");
        when(childContent.getParentId()).thenReturn("200");
        Content grandchildContent = createMockContent("202", "Grandchild", "MYSPACE", "<p>Grandchild content</p>");
        when(grandchildContent.getParentId()).thenReturn("201");
        
        when(mockConfluence.contentById(eq("200"))).thenReturn(rootContent);
        when(mockConfluence.getChildrenOfContentById(eq("200"))).thenReturn(List.of(childContent));
        when(mockConfluence.getChildrenOfContentById(eq("201"))).thenReturn(List.of(grandchildContent));
        when(mockConfluence.getChildrenOfContentById(eq("202"))).thenReturn(new ArrayList<>());
        when(mockConfluence.getContentAttachments(anyString())).thenReturn(new ArrayList<>());

        MermaidIndexIntegration.ContentProcessor processor = mock(MermaidIndexIntegration.ContentProcessor.class);

        // When
        integration.getContentForIndex(includePatterns, excludePatterns, processor);

        // Then - should process root, child, and grandchild
        verify(processor, times(3)).process(anyString(), anyString(), anyString(), any(), any(), any());
        verify(processor).process(eq("MYSPACE/200"), eq("Root"), anyString(), any(), any(), any());
        verify(processor).process(eq("MYSPACE/201"), eq("Child"), anyString(), any(), any(), any());
        verify(processor).process(eq("MYSPACE/202"), eq("Grandchild"), anyString(), any(), any(), any());
    }

    // ==================== Space-Wide Tests ====================

    @Test
    void testGetContentForIndexWithSpaceWidePattern() throws IOException {
        // Given: [SPACE]/**
        List<String> includePatterns = List.of("MYSPACE/**");
        List<String> excludePatterns = new ArrayList<>();
        
        Content rootPage = createMockContent("300", "Root", "MYSPACE", "<p>Root</p>");
        Content childPage = createMockContent("301", "Child", "MYSPACE", "<p>Child</p>");
        
        // Create mock ContentResult for root pages
        ContentResult mockContentResult = mock(ContentResult.class);
        when(mockContentResult.getContents()).thenReturn(List.of(rootPage));
        
        when(mockConfluence.content(eq(""), eq("MYSPACE"))).thenReturn(mockContentResult);
        when(mockConfluence.getChildrenOfContentById(eq("300"))).thenReturn(List.of(childPage));
        when(mockConfluence.getChildrenOfContentById(eq("301"))).thenReturn(new ArrayList<>());
        when(mockConfluence.getContentAttachments(anyString())).thenReturn(new ArrayList<>());

        MermaidIndexIntegration.ContentProcessor processor = mock(MermaidIndexIntegration.ContentProcessor.class);

        // When
        integration.getContentForIndex(includePatterns, excludePatterns, processor);

        // Then - should process all pages in space
        verify(processor, atLeastOnce()).process(anyString(), anyString(), anyString(), any(), any(), any());
    }

    // ==================== Exclude Pattern Tests ====================

    @Test
    void testGetContentForIndexWithExcludePattern() throws IOException {
        // Given: include all descendants but exclude specific page
        List<String> includePatterns = List.of("MYSPACE/pages/400/Root/**");
        List<String> excludePatterns = List.of("MYSPACE/pages/402/Excluded");
        
        Content rootContent = createMockContent("400", "Root", "MYSPACE", "<p>Root</p>");
        Content includedChild = createMockContent("401", "Included", "MYSPACE", "<p>Included</p>");
        Content excludedChild = createMockContent("402", "Excluded", "MYSPACE", "<p>Excluded</p>");
        
        when(mockConfluence.contentById(eq("400"))).thenReturn(rootContent);
        when(mockConfluence.getChildrenOfContentById(eq("400"))).thenReturn(List.of(includedChild, excludedChild));
        when(mockConfluence.getChildrenOfContentById(eq("401"))).thenReturn(new ArrayList<>());
        when(mockConfluence.getChildrenOfContentById(eq("402"))).thenReturn(new ArrayList<>());
        when(mockConfluence.getContentAttachments(anyString())).thenReturn(new ArrayList<>());

        MermaidIndexIntegration.ContentProcessor processor = mock(MermaidIndexIntegration.ContentProcessor.class);

        // When
        integration.getContentForIndex(includePatterns, excludePatterns, processor);

        // Then - should process root and included child, but not excluded child
        verify(processor, times(2)).process(anyString(), anyString(), anyString(), any(), any(), any());
        verify(processor).process(eq("MYSPACE/400"), eq("Root"), anyString(), any(), any(), any());
        verify(processor).process(eq("MYSPACE/401"), eq("Included"), anyString(), any(), any(), any());
        verify(processor, never()).process(eq("MYSPACE/402"), eq("Excluded"), anyString(), any(), any(), any());
    }

    @Test
    void testGetContentForIndexWithLegacyExcludePattern() throws IOException {
        // Given: legacy wildcard exclude pattern
        List<String> includePatterns = List.of("MYSPACE/pages/500/Root");
        List<String> excludePatterns = List.of("EXCLUDE*");
        
        Content content = createMockContent("500", "EXCLUDE-Me", "MYSPACE", "<p>Content</p>");
        
        when(mockConfluence.contentById(eq("500"))).thenReturn(content);

        MermaidIndexIntegration.ContentProcessor processor = mock(MermaidIndexIntegration.ContentProcessor.class);

        // When
        integration.getContentForIndex(includePatterns, excludePatterns, processor);

        // Then - content should be excluded due to title starting with "EXCLUDE"
        verify(processor, never()).process(anyString(), anyString(), anyString(), any(), any(), any());
    }

    // ==================== Attachment Tests ====================

    @Test
    void testGetContentForIndexWithAttachments() throws IOException {
        // Given
        List<String> includePatterns = List.of("TEST/pages/123/Page");
        List<String> excludePatterns = new ArrayList<>();
        
        Content content = createMockContent("123", "Page", "TEST", "<p>Content</p>");
        
        Attachment attachment = mock(Attachment.class);
        when(attachment.getTitle()).thenReturn("document.pdf");
        
        when(mockConfluence.contentById(eq("123"))).thenReturn(content);
        when(mockConfluence.getContentAttachments(eq("123"))).thenReturn(List.of(attachment));

        MermaidIndexIntegration.ContentProcessor processor = mock(MermaidIndexIntegration.ContentProcessor.class);

        // When
        integration.getContentForIndex(includePatterns, excludePatterns, processor);

        // Then
        verify(processor, times(1)).process(
                eq("TEST/123"),
                eq("Page"),
                eq("<p>Content</p>"),
                any(),
                any(),
                any()
        );
    }

    // ==================== Page Name with Plus Sign Tests ====================

    @Test
    void testGetContentForIndexWithPlusInPageName() throws IOException {
        // Given: URL with + in page name (represents space)
        String urlPattern = "https://company.atlassian.net/wiki/spaces/DEV/pages/789/C%2B%2B+Programming";
        List<String> includePatterns = List.of(urlPattern);
        List<String> excludePatterns = new ArrayList<>();
        
        Content content = createMockContent("789", "C++ Programming", "DEV", "<p>C++ guide</p>");
        
        when(mockConfluence.contentById(eq("789"))).thenReturn(content);
        when(mockConfluence.getContentAttachments(eq("789"))).thenReturn(new ArrayList<>());

        MermaidIndexIntegration.ContentProcessor processor = mock(MermaidIndexIntegration.ContentProcessor.class);

        // When
        integration.getContentForIndex(includePatterns, excludePatterns, processor);

        // Then
        verify(processor, times(1)).process(
                eq("DEV/789"),
                eq("C++ Programming"),
                anyString(),
                any(),
                any(),
                any()
        );
    }

    // ==================== Multiple Patterns Tests ====================

    @Test
    void testGetContentForIndexWithMultiplePatterns() throws IOException {
        // Given: multiple include patterns
        List<String> includePatterns = List.of(
                "SPACE1/pages/1/Page1",
                "SPACE2/pages/2/Page2"
        );
        List<String> excludePatterns = new ArrayList<>();
        
        Content content1 = createMockContent("1", "Page1", "SPACE1", "<p>Content1</p>");
        Content content2 = createMockContent("2", "Page2", "SPACE2", "<p>Content2</p>");
        
        when(mockConfluence.contentById(eq("1"))).thenReturn(content1);
        when(mockConfluence.contentById(eq("2"))).thenReturn(content2);
        when(mockConfluence.getContentAttachments(anyString())).thenReturn(new ArrayList<>());

        MermaidIndexIntegration.ContentProcessor processor = mock(MermaidIndexIntegration.ContentProcessor.class);

        // When
        integration.getContentForIndex(includePatterns, excludePatterns, processor);

        // Then
        verify(processor, times(2)).process(anyString(), anyString(), anyString(), any(), any(), any());
        verify(processor).process(eq("SPACE1/1"), eq("Page1"), anyString(), any(), any(), any());
        verify(processor).process(eq("SPACE2/2"), eq("Page2"), anyString(), any(), any(), any());
    }

    // ==================== Duplicate Handling Tests ====================

    @Test
    void testGetContentForIndexRemovesDuplicates() throws IOException {
        // Given: patterns that would result in duplicate pages
        List<String> includePatterns = List.of(
                "SPACE/pages/999/Page",
                "SPACE/pages/999/Page"  // Same page twice
        );
        List<String> excludePatterns = new ArrayList<>();
        
        Content content = createMockContent("999", "Page", "SPACE", "<p>Content</p>");
        
        when(mockConfluence.contentById(eq("999"))).thenReturn(content);
        when(mockConfluence.getContentAttachments(eq("999"))).thenReturn(new ArrayList<>());

        MermaidIndexIntegration.ContentProcessor processor = mock(MermaidIndexIntegration.ContentProcessor.class);

        // When
        integration.getContentForIndex(includePatterns, excludePatterns, processor);

        // Then - should only process once despite duplicate patterns
        verify(processor, times(1)).process(anyString(), anyString(), anyString(), any(), any(), any());
    }

    // ==================== Empty/Null Pattern Tests ====================

    @Test
    void testGetContentForIndexWithNullPatterns() {
        // Given
        List<String> includePatterns = null;
        List<String> excludePatterns = new ArrayList<>();
        
        MermaidIndexIntegration.ContentProcessor processor = mock(MermaidIndexIntegration.ContentProcessor.class);

        // When
        integration.getContentForIndex(includePatterns, excludePatterns, processor);

        // Then - no content should be processed
        verify(processor, never()).process(anyString(), anyString(), anyString(), any(), any(), any());
    }

    @Test
    void testGetContentForIndexWithEmptyPatterns() {
        // Given
        List<String> includePatterns = new ArrayList<>();
        List<String> excludePatterns = new ArrayList<>();
        
        MermaidIndexIntegration.ContentProcessor processor = mock(MermaidIndexIntegration.ContentProcessor.class);

        // When
        integration.getContentForIndex(includePatterns, excludePatterns, processor);

        // Then - no content should be processed
        verify(processor, never()).process(anyString(), anyString(), anyString(), any(), any(), any());
    }

    // ==================== Invalid Pattern Tests ====================

    @Test
    void testGetContentForIndexWithInvalidPatternThrowsException() {
        // Given: pattern that doesn't match structured format
        List<String> includePatterns = List.of("SomeInvalidPattern");
        List<String> excludePatterns = new ArrayList<>();
        
        MermaidIndexIntegration.ContentProcessor processor = mock(MermaidIndexIntegration.ContentProcessor.class);

        // When/Then - should throw RuntimeException wrapping IllegalArgumentException
        assertThrows(RuntimeException.class, () -> 
            integration.getContentForIndex(includePatterns, excludePatterns, processor)
        );
        
        // Verify no content was processed
        verify(processor, never()).process(anyString(), anyString(), anyString(), any(), any(), any());
    }

    // ==================== Helper Methods ====================

    private Content createMockContent(String id, String title, String spaceKey, String body) {
        Content content = mock(Content.class);
        when(content.getId()).thenReturn(id);
        when(content.getTitle()).thenReturn(title);
        when(content.getLastModifiedDate()).thenReturn(new Date());
        when(content.getParentId()).thenReturn(null);
        when(content.getModels(any(), any())).thenReturn(new ArrayList<>());
        
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
