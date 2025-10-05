package com.github.istin.dmtools.github.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GitHubMatchTest {

    @Test
    void testDefaultConstructor() {
        GitHubMatch match = new GitHubMatch();
        assertNotNull(match);
    }

    @Test
    void testJsonStringConstructor() throws JSONException {
        String json = "{\"indices\": [10, 20], \"text\": \"search term\"}";
        GitHubMatch match = new GitHubMatch(json);
        
        assertNotNull(match);
        assertEquals("search term", match.getText());
        assertNotNull(match.getIndices());
    }

    @Test
    void testJsonObjectConstructor() {
        JSONObject json = new JSONObject();
        JSONArray indices = new JSONArray();
        indices.put(5);
        indices.put(15);
        json.put("indices", indices);
        json.put("text", "matched text");
        
        GitHubMatch match = new GitHubMatch(json);
        
        assertEquals("matched text", match.getText());
        String indicesStr = match.getIndices();
        assertNotNull(indicesStr);
        assertTrue(indicesStr.contains("5"));
        assertTrue(indicesStr.contains("15"));
    }

    @Test
    void testGetText() {
        JSONObject json = new JSONObject();
        json.put("text", "important keyword");
        json.put("indices", new JSONArray().put(0).put(10));
        
        GitHubMatch match = new GitHubMatch(json);
        assertEquals("important keyword", match.getText());
    }

    @Test
    void testGetIndices() {
        JSONObject json = new JSONObject();
        JSONArray indices = new JSONArray();
        indices.put(100);
        indices.put(200);
        indices.put(300);
        json.put("indices", indices);
        json.put("text", "test");
        
        GitHubMatch match = new GitHubMatch(json);
        String indicesStr = match.getIndices();
        
        assertNotNull(indicesStr);
        assertTrue(indicesStr.contains("100"));
        assertTrue(indicesStr.contains("200"));
        assertTrue(indicesStr.contains("300"));
    }

    @Test
    void testImplementsIMatch() {
        GitHubMatch match = new GitHubMatch();
        assertTrue(match instanceof com.github.istin.dmtools.common.model.IMatch);
    }
}
