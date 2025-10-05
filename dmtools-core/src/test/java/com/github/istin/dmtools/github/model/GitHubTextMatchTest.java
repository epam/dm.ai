package com.github.istin.dmtools.github.model;

import com.github.istin.dmtools.common.model.IMatch;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GitHubTextMatchTest {

    @Test
    void testDefaultConstructor() {
        GitHubTextMatch textMatch = new GitHubTextMatch();
        assertNotNull(textMatch);
    }

    @Test
    void testJsonStringConstructor() throws JSONException {
        String json = "{\"fragment\": \"code fragment\", \"object_url\": \"https://github.com/test\", \"object_type\": \"FileContent\", \"matches\": []}";
        GitHubTextMatch textMatch = new GitHubTextMatch(json);
        
        assertNotNull(textMatch);
        assertEquals("code fragment", textMatch.getFragment());
        assertEquals("https://github.com/test", textMatch.getObjectUrl());
        assertEquals("FileContent", textMatch.getObjectType());
    }

    @Test
    void testJsonObjectConstructor() {
        JSONObject json = new JSONObject();
        json.put("fragment", "test fragment");
        json.put("object_url", "https://api.github.com/repos/owner/repo");
        json.put("object_type", "Repository");
        json.put("matches", new JSONArray());
        
        GitHubTextMatch textMatch = new GitHubTextMatch(json);
        
        assertEquals("test fragment", textMatch.getFragment());
        assertEquals("https://api.github.com/repos/owner/repo", textMatch.getObjectUrl());
        assertEquals("Repository", textMatch.getObjectType());
    }

    @Test
    void testGetFragment() {
        JSONObject json = new JSONObject();
        json.put("fragment", "function example()");
        json.put("matches", new JSONArray());
        
        GitHubTextMatch textMatch = new GitHubTextMatch(json);
        assertEquals("function example()", textMatch.getFragment());
    }

    @Test
    void testGetObjectUrl() {
        JSONObject json = new JSONObject();
        json.put("object_url", "https://github.com/user/repo/blob/main/file.js");
        json.put("matches", new JSONArray());
        
        GitHubTextMatch textMatch = new GitHubTextMatch(json);
        assertEquals("https://github.com/user/repo/blob/main/file.js", textMatch.getObjectUrl());
    }

    @Test
    void testGetObjectType() {
        JSONObject json = new JSONObject();
        json.put("object_type", "Issue");
        json.put("matches", new JSONArray());
        
        GitHubTextMatch textMatch = new GitHubTextMatch(json);
        assertEquals("Issue", textMatch.getObjectType());
    }

    @Test
    void testGetMatches() {
        JSONObject json = new JSONObject();
        json.put("fragment", "test");
        
        JSONArray matches = new JSONArray();
        JSONObject match1 = new JSONObject();
        match1.put("text", "keyword1");
        match1.put("indices", new JSONArray().put(0).put(10));
        matches.put(match1);
        
        JSONObject match2 = new JSONObject();
        match2.put("text", "keyword2");
        match2.put("indices", new JSONArray().put(20).put(30));
        matches.put(match2);
        
        json.put("matches", matches);
        
        GitHubTextMatch textMatch = new GitHubTextMatch(json);
        List<IMatch> matchList = textMatch.getMatches();
        
        assertNotNull(matchList);
        assertEquals(2, matchList.size());
        assertEquals("keyword1", matchList.get(0).getText());
        assertEquals("keyword2", matchList.get(1).getText());
    }

    @Test
    void testGetMatches_Empty() {
        JSONObject json = new JSONObject();
        json.put("fragment", "no matches");
        json.put("matches", new JSONArray());
        
        GitHubTextMatch textMatch = new GitHubTextMatch(json);
        List<IMatch> matches = textMatch.getMatches();
        
        assertNotNull(matches);
        assertTrue(matches.isEmpty());
    }

    @Test
    void testImplementsITextMatch() {
        GitHubTextMatch textMatch = new GitHubTextMatch();
        assertTrue(textMatch instanceof com.github.istin.dmtools.common.model.ITextMatch);
    }
}
