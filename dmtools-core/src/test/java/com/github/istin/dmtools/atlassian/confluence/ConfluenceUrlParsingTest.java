package com.github.istin.dmtools.atlassian.confluence;

import com.github.istin.dmtools.atlassian.confluence.model.Content;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.net.MalformedURLException;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive tests for Confluence URL parsing logic, specifically focusing on the 
 * checkBaseIndex method and contentByUrl functionality.
 */
public class ConfluenceUrlParsingTest {

    private Confluence confluence;
    private Content mockContent;

    @Before
    public void setUp() throws IOException {
        confluence = Mockito.spy(new Confluence("https://dmtools.atlassian.net", "auth"));
        mockContent = mock(Content.class);
        
        // Mock the contentById method to return our mock content
        doReturn(mockContent).when(confluence).contentById(anyString());
    }

    /**
     * Test the fix for hardcoded index bug with /wiki/spaces/{spaceKey}/pages/{pageId}/{title} URLs
     * This was the main issue we fixed - ensure pageId extraction works correctly.
     */
    @Test
    public void testWikiSpacesPagesUrlParsing() throws IOException {
        String url = "https://dmtools.atlassian.net/wiki/spaces/AINA/pages/6750209/Acceptance+Criteria";
        
        Content result = confluence.contentByUrl(url);
        
        assertNotNull("Should successfully parse wiki/spaces/pages URL", result);
        // Verify that contentById was called with the correct page ID (6750209)
        verify(confluence, times(1)).contentById("6750209");
    }

    /**
     * Test the original /spaces/{spaceKey}/pages/{pageId}/{title} URL format (without wiki prefix)
     * This should still work after our fix.
     */
    @Test
    public void testDirectSpacesPagesUrlParsing() throws IOException {
        String url = "https://dmtools.atlassian.net/spaces/AINA/pages/6750209/Test+Page";
        
        Content result = confluence.contentByUrl(url);
        
        assertNotNull("Should successfully parse direct spaces/pages URL", result);
        // Verify that contentById was called with the correct page ID (6750209)
        verify(confluence, times(1)).contentById("6750209");
    }

    /**
     * Test URL with different space keys to ensure our fix works with various space configurations
     */
    @Test
    public void testDifferentSpaceKeys() throws IOException {
        String[] testUrls = {
            "https://dmtools.atlassian.net/wiki/spaces/DEV/pages/12345/Development+Guide",
            "https://dmtools.atlassian.net/wiki/spaces/PROD/pages/67890/Production+Manual",
            "https://dmtools.atlassian.net/spaces/TEST/pages/11111/Test+Documentation"
        };
        
        String[] expectedPageIds = {"12345", "67890", "11111"};
        
        for (int i = 0; i < testUrls.length; i++) {
            Content result = confluence.contentByUrl(testUrls[i]);
            assertNotNull("Should parse URL " + testUrls[i], result);
        }
        
        // Verify all expected page IDs were called
        for (String pageId : expectedPageIds) {
            verify(confluence, times(1)).contentById(pageId);
        }
    }

    /**
     * Test edge case: URL with minimum required segments
     */
    @Test
    public void testMinimumRequiredSegments() throws IOException {
        String url = "https://dmtools.atlassian.net/wiki/spaces/A/pages/1/T";
        
        Content result = confluence.contentByUrl(url);
        
        assertNotNull("Should parse URL with minimum segments", result);
        verify(confluence, times(1)).contentById("1");
    }

    /**
     * Test edge case: URL with extra segments after page title
     */
    @Test
    public void testExtraSegmentsAfterTitle() throws IOException {
        String url = "https://dmtools.atlassian.net/wiki/spaces/AINA/pages/6750209/Acceptance+Criteria/extra/segments";
        
        Content result = confluence.contentByUrl(url);
        
        assertNotNull("Should parse URL with extra segments", result);
        verify(confluence, times(1)).contentById("6750209");
    }

    /**
     * Test URLs with special characters in page titles
     */
    @Test
    public void testSpecialCharactersInPageTitle() throws IOException {
        String url = "https://dmtools.atlassian.net/wiki/spaces/AINA/pages/6750209/Page+With+Special+Characters+%26+Symbols";
        
        Content result = confluence.contentByUrl(url);
        
        assertNotNull("Should parse URL with special characters", result);
        verify(confluence, times(1)).contentById("6750209");
    }

    /**
     * Test invalid URL formats that should throw UnsupportedOperationException
     */
    @Test(expected = UnsupportedOperationException.class)
    public void testInvalidUrlFormat() throws IOException {
        String url = "https://dmtools.atlassian.net/invalid/url/format";
        confluence.contentByUrl(url);
    }

    /**
     * Test URL without pages segment (should fail)
     */
    @Test(expected = UnsupportedOperationException.class)
    public void testUrlWithoutPagesSegment() throws IOException {
        String url = "https://dmtools.atlassian.net/wiki/spaces/AINA/content/6750209";
        confluence.contentByUrl(url);
    }

    /**
     * Test URL with insufficient segments after spaces
     */
    @Test(expected = UnsupportedOperationException.class)
    public void testUrlWithInsufficientSegments() throws IOException {
        String url = "https://dmtools.atlassian.net/wiki/spaces/AINA";
        confluence.contentByUrl(url);
    }

    /**
     * Test URL with wrong segment order
     */
    @Test(expected = UnsupportedOperationException.class)
    public void testUrlWithWrongSegmentOrder() throws IOException {
        String url = "https://dmtools.atlassian.net/wiki/pages/spaces/AINA/6750209/Page";
        confluence.contentByUrl(url);
    }

    /**
     * Test null URL handling
     */
    @Test(expected = MalformedURLException.class)
    public void testNullUrl() throws IOException {
        confluence.contentByUrl(null);
    }

    /**
     * Test empty URL handling
     */
    @Test(expected = MalformedURLException.class)
    public void testEmptyUrl() throws IOException {
        confluence.contentByUrl("");
    }

    /**
     * Test URL with non-numeric page ID (should still work as contentById handles validation)
     */
    @Test
    public void testUrlWithNonNumericPageId() throws IOException {
        String url = "https://dmtools.atlassian.net/wiki/spaces/AINA/pages/ABC123/Page";
        
        Content result = confluence.contentByUrl(url);
        
        assertNotNull("Should parse URL with non-numeric page ID", result);
        verify(confluence, times(1)).contentById("ABC123");
    }

    /**
     * Test that both base index paths (with and without wiki) work correctly
     */
    @Test
    public void testBothBaseIndexPaths() throws IOException {
        String wikiUrl = "https://dmtools.atlassian.net/wiki/spaces/AINA/pages/6750209/Wiki+Page";
        String directUrl = "https://dmtools.atlassian.net/spaces/AINA/pages/6750210/Direct+Page";
        
        Content wikiResult = confluence.contentByUrl(wikiUrl);
        Content directResult = confluence.contentByUrl(directUrl);
        
        assertNotNull("Wiki URL should work", wikiResult);
        assertNotNull("Direct URL should work", directResult);
        
        verify(confluence, times(1)).contentById("6750209");
        verify(confluence, times(1)).contentById("6750210");
    }

    /**
     * Test the specific URL from the user's issue to ensure it works correctly
     */
    @Test
    public void testUserSpecificUrl() throws IOException {
        String userUrl = "https://dmtools.atlassian.net/wiki/spaces/AINA/pages/6750209/Acceptance+Criteria";
        
        Content result = confluence.contentByUrl(userUrl);
        
        assertNotNull("User's specific URL should work", result);
        verify(confluence, times(1)).contentById("6750209");
    }

    /**
     * Test multiple URLs to ensure no cross-contamination between calls
     */
    @Test
    public void testMultipleUrlsSequentially() throws IOException {
        String[] urls = {
            "https://dmtools.atlassian.net/wiki/spaces/AINA/pages/1001/Page1",
            "https://dmtools.atlassian.net/wiki/spaces/AINA/pages/1002/Page2",
            "https://dmtools.atlassian.net/spaces/AINA/pages/1003/Page3"
        };
        
        for (String url : urls) {
            Content result = confluence.contentByUrl(url);
            assertNotNull("Should parse URL: " + url, result);
        }
        
        // Verify each page ID was called exactly once
        verify(confluence, times(1)).contentById("1001");
        verify(confluence, times(1)).contentById("1002");
        verify(confluence, times(1)).contentById("1003");
    }
} 