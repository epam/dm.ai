package com.github.istin.dmtools.atlassian.confluence.model;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SearchResultTest {

    @Test
    void testDefaultConstructor() {
        SearchResult result = new SearchResult();
        assertNotNull(result);
    }

    @Test
    void testJsonStringConstructor() throws Exception {
        String json = "{\"title\":\"Test Page\",\"id\":\"123\",\"entityId\":\"456\",\"type\":\"page\",\"url\":\"/wiki/spaces/TEST/pages/123\",\"excerpt\":\"Page excerpt\"}";
        SearchResult result = new SearchResult(json);
        
        assertNotNull(result);
        assertEquals("Test Page", result.getTitle());
        assertEquals("123", result.getId());
        assertEquals("456", result.getEntityId());
        assertEquals("page", result.getType());
        assertEquals("/wiki/spaces/TEST/pages/123", result.getUrl());
        assertEquals("Page excerpt", result.getExcerpt());
    }

    @Test
    void testJsonObjectConstructor() {
        JSONObject json = new JSONObject();
        json.put("title", "Documentation");
        json.put("id", "789");
        json.put("entityId", "012");
        json.put("type", "blogpost");
        json.put("url", "/wiki/spaces/DOC/blog/789");
        json.put("excerpt", "Blog post excerpt");
        
        SearchResult result = new SearchResult(json);
        
        assertNotNull(result);
        assertEquals("Documentation", result.getTitle());
        assertEquals("789", result.getId());
    }

    @Test
    void testGetTitle() {
        JSONObject json = new JSONObject();
        json.put("title", "Project Plan");
        
        SearchResult result = new SearchResult(json);
        
        assertEquals("Project Plan", result.getTitle());
    }

    @Test
    void testGetId() {
        JSONObject json = new JSONObject();
        json.put("id", "100");
        
        SearchResult result = new SearchResult(json);
        
        assertEquals("100", result.getId());
    }

    @Test
    void testGetEntityId() {
        JSONObject json = new JSONObject();
        json.put("entityId", "200");
        
        SearchResult result = new SearchResult(json);
        
        assertEquals("200", result.getEntityId());
    }

    @Test
    void testGetType() {
        JSONObject json = new JSONObject();
        json.put("type", "attachment");
        
        SearchResult result = new SearchResult(json);
        
        assertEquals("attachment", result.getType());
    }

    @Test
    void testGetUrl() {
        JSONObject json = new JSONObject();
        json.put("url", "/wiki/display/TEST/Page");
        
        SearchResult result = new SearchResult(json);
        
        assertEquals("/wiki/display/TEST/Page", result.getUrl());
    }

    @Test
    void testGetExcerpt() {
        JSONObject json = new JSONObject();
        json.put("excerpt", "This is an excerpt from the page content");
        
        SearchResult result = new SearchResult(json);
        
        assertEquals("This is an excerpt from the page content", result.getExcerpt());
    }

    @Test
    void testAllGettersWithNull() {
        SearchResult result = new SearchResult();
        
        assertNull(result.getTitle());
        assertNull(result.getId());
        assertNull(result.getEntityId());
        assertNull(result.getType());
        assertNull(result.getUrl());
        assertNull(result.getExcerpt());
    }

    @Test
    void testConstants() {
        assertEquals("title", SearchResult.TITLE);
        assertEquals("id", SearchResult.ID);
        assertEquals("entityId", SearchResult.ENTITY_ID);
        assertEquals("type", SearchResult.TYPE);
        assertEquals("url", SearchResult.URL);
        assertEquals("excerpt", SearchResult.EXCERPT);
    }
}
