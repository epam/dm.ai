package com.github.istin.dmtools.ai;

import com.github.istin.dmtools.atlassian.confluence.Confluence;
import com.github.istin.dmtools.atlassian.confluence.model.Content;
import com.github.istin.dmtools.atlassian.confluence.model.Storage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ConfluencePagesContextTest {

    private Confluence confluence;
    private Content mockContent;
    private Storage mockStorage;

    @BeforeEach
    void setUp() {
        confluence = Mockito.mock(Confluence.class);
        mockContent = Mockito.mock(Content.class);
        mockStorage = Mockito.mock(Storage.class);
    }

    @Test
    void testConstructor_NullPages() throws Exception {
        ConfluencePagesContext context = new ConfluencePagesContext(null, confluence, false);
        
        assertNotNull(context);
        String text = context.toText();
        assertNotNull(text);
        assertTrue(text.isEmpty() || text.trim().isEmpty());
    }

    @Test
    void testConstructor_EmptyPages() throws Exception {
        when(confluence.contentsByUrls(any())).thenReturn(new ArrayList<>());
        
        ConfluencePagesContext context = new ConfluencePagesContext(new String[]{}, confluence, false);
        
        assertNotNull(context);
        String text = context.toText();
        assertNotNull(text);
    }

    @Test
    void testConstructor_WithPages() throws Exception {
        when(mockContent.getTitle()).thenReturn("Test Page");
        when(mockContent.getStorage()).thenReturn(mockStorage);
        when(mockStorage.getValue()).thenReturn("<p>Test content</p>");
        when(mockContent.getViewUrl(any())).thenReturn("http://example.com/page");
        when(confluence.contentsByUrls(any())).thenReturn(Arrays.asList(mockContent));
        when(confluence.getBasePath()).thenReturn("http://example.com");
        
        String[] pages = {"http://example.com/page1"};
        ConfluencePagesContext context = new ConfluencePagesContext(pages, confluence, false);
        
        assertNotNull(context);
        String text = context.toText();
        assertNotNull(text);
        assertTrue(text.contains("Confluence pages"));
        assertTrue(text.contains("Test Page"));
    }

    @Test
    void testConstructor_WithMarkdownTransformation() throws Exception {
        when(mockContent.getTitle()).thenReturn("Test Page");
        when(mockContent.getStorage()).thenReturn(mockStorage);
        when(mockStorage.getValue()).thenReturn("<p>Test content</p>");
        when(mockContent.getViewUrl(any())).thenReturn("http://example.com/page");
        when(confluence.contentsByUrls(any())).thenReturn(Arrays.asList(mockContent));
        when(confluence.getBasePath()).thenReturn("http://example.com");
        
        String[] pages = {"http://example.com/page1"};
        ConfluencePagesContext context = new ConfluencePagesContext(pages, confluence, true);
        
        assertNotNull(context);
        String text = context.toText();
        assertNotNull(text);
        assertTrue(text.contains("Confluence pages"));
    }

    @Test
    void testConstructor_MultiplePages() throws Exception {
        Content mockContent2 = Mockito.mock(Content.class);
        Storage mockStorage2 = Mockito.mock(Storage.class);
        
        when(mockContent.getTitle()).thenReturn("Page 1");
        when(mockContent.getStorage()).thenReturn(mockStorage);
        when(mockStorage.getValue()).thenReturn("<p>Content 1</p>");
        when(mockContent.getViewUrl(any())).thenReturn("http://example.com/page1");
        
        when(mockContent2.getTitle()).thenReturn("Page 2");
        when(mockContent2.getStorage()).thenReturn(mockStorage2);
        when(mockStorage2.getValue()).thenReturn("<p>Content 2</p>");
        when(mockContent2.getViewUrl(any())).thenReturn("http://example.com/page2");
        
        when(confluence.contentsByUrls(any())).thenReturn(Arrays.asList(mockContent, mockContent2));
        when(confluence.getBasePath()).thenReturn("http://example.com");
        
        String[] pages = {"http://example.com/page1", "http://example.com/page2"};
        ConfluencePagesContext context = new ConfluencePagesContext(pages, confluence, false);
        
        assertNotNull(context);
        String text = context.toText();
        assertNotNull(text);
        // Text contains at least one of the pages
        assertTrue(text.contains("Page 1") || text.contains("Page 2") || text.contains("Confluence pages"));
    }

    @Test
    void testToText() throws Exception {
        when(mockContent.getTitle()).thenReturn("Test");
        when(mockContent.getStorage()).thenReturn(mockStorage);
        when(mockStorage.getValue()).thenReturn("Content");
        when(mockContent.getViewUrl(any())).thenReturn("http://example.com/page");
        when(confluence.contentsByUrls(any())).thenReturn(Arrays.asList(mockContent));
        when(confluence.getBasePath()).thenReturn("http://example.com");
        
        ConfluencePagesContext context = new ConfluencePagesContext(
            new String[]{"http://example.com/page"}, 
            confluence, 
            false
        );
        
        String text = context.toText();
        assertNotNull(text);
        assertFalse(text.isEmpty());
    }

    @Test
    void testToText_EmptyContent() throws Exception {
        when(mockContent.getTitle()).thenReturn("");
        when(mockContent.getStorage()).thenReturn(mockStorage);
        when(mockStorage.getValue()).thenReturn("");
        when(mockContent.getViewUrl(any())).thenReturn("http://example.com/page");
        when(confluence.contentsByUrls(any())).thenReturn(Arrays.asList(mockContent));
        when(confluence.getBasePath()).thenReturn("http://example.com");
        
        ConfluencePagesContext context = new ConfluencePagesContext(
            new String[]{"http://example.com/page"}, 
            confluence, 
            false
        );
        
        String text = context.toText();
        assertNotNull(text);
        assertTrue(text.contains("Confluence pages"));
    }
}
