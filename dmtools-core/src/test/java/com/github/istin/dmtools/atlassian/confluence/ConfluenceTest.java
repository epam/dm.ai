package com.github.istin.dmtools.atlassian.confluence;

import com.github.istin.dmtools.atlassian.confluence.model.Content;
import com.github.istin.dmtools.atlassian.confluence.model.ContentResult;
import com.github.istin.dmtools.common.networking.GenericRequest;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.*;

public class ConfluenceTest {

    private Confluence confluence;
    private GenericRequest mockRequest;
    private OkHttpClient mockClient;
    private Response mockResponse;
    private ResponseBody mockResponseBody;

    @Before
    public void setUp() throws IOException {
        mockClient = mock(OkHttpClient.class);
        mockRequest = mock(GenericRequest.class);
        mockResponse = mock(Response.class);
        mockResponseBody = mock(ResponseBody.class);

        confluence = Mockito.spy(new Confluence("http://example.com", "auth"));
    }

    @Test
    public void testPath() {
        String path = confluence.path("test");
        assertEquals("http://example.com/rest/api/test", path);
    }

    @Test
    public void testContentsByUrls() throws IOException {
        Content mockContent = mock(Content.class);
        doReturn(mockContent).when(confluence).contentByUrl(anyString());

        List<Content> contents = confluence.contentsByUrls("http://example.com/spaces/spaceID/pages/pageID/pageName");
        assertNotNull(contents);
        assertEquals(1, contents.size());
        verify(confluence, times(1)).contentByUrl(anyString());
    }

    @Test
    public void testContentByUrl() throws IOException {
        Content mockContent = mock(Content.class);
        doReturn(mockContent).when(confluence).contentById(anyString());

        Content content = confluence.contentByUrl("http://example.com/spaces/spaceID/pages/pageID/pageName");
        assertNotNull(content);
        verify(confluence, times(1)).contentById(anyString());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testContentByUrlWithInvalidUrl() throws IOException {
        confluence.contentByUrl("http://example.com/invalid/url");
    }

    /**
     * Test the fix for hardcoded index bug in URL parsing.
     * This test ensures that the page ID extraction works correctly for both
     * /wiki/spaces/... and /spaces/... URL formats.
     */
    @Test
    public void testContentByUrlWithCorrectPageIdExtraction() throws IOException {
        Content mockContent = mock(Content.class);
        doReturn(mockContent).when(confluence).contentById(anyString());

        // Test wiki/spaces/pages URL format
        String wikiUrl = "http://example.com/wiki/spaces/AINA/pages/6750209/Acceptance+Criteria";
        Content wikiResult = confluence.contentByUrl(wikiUrl);
        assertNotNull("Should parse wiki/spaces/pages URL", wikiResult);
        
        // Test direct spaces/pages URL format
        String directUrl = "http://example.com/spaces/AINA/pages/6750210/Test+Page";
        Content directResult = confluence.contentByUrl(directUrl);
        assertNotNull("Should parse direct spaces/pages URL", directResult);
        
        // Verify the correct page IDs were extracted
        verify(confluence, times(1)).contentById("6750209");
        verify(confluence, times(1)).contentById("6750210");
    }


}