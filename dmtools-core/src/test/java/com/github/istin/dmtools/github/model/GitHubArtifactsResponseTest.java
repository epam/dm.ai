package com.github.istin.dmtools.github.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GitHubArtifactsResponseTest {

    @Test
    void testDefaultConstructor() {
        GitHubArtifactsResponse response = new GitHubArtifactsResponse();
        assertNotNull(response);
    }

    @Test
    void testJsonStringConstructor() throws JSONException {
        String json = "{\"total_count\": 2, \"artifacts\": [{\"id\": 1, \"name\": \"artifact1\"}, {\"id\": 2, \"name\": \"artifact2\"}]}";
        GitHubArtifactsResponse response = new GitHubArtifactsResponse(json);
        
        assertNotNull(response);
        assertEquals(2, response.getTotalCount());
        
        List<GitHubArtifact> artifacts = response.getArtifacts();
        assertNotNull(artifacts);
        assertEquals(2, artifacts.size());
    }

    @Test
    void testJsonObjectConstructor() {
        JSONObject json = new JSONObject();
        json.put("total_count", 3);
        
        JSONArray artifacts = new JSONArray();
        JSONObject artifact1 = new JSONObject();
        artifact1.put("id", 100);
        artifact1.put("name", "build-output");
        artifacts.put(artifact1);
        
        JSONObject artifact2 = new JSONObject();
        artifact2.put("id", 101);
        artifact2.put("name", "test-results");
        artifacts.put(artifact2);
        
        JSONObject artifact3 = new JSONObject();
        artifact3.put("id", 102);
        artifact3.put("name", "coverage-report");
        artifacts.put(artifact3);
        
        json.put("artifacts", artifacts);
        
        GitHubArtifactsResponse response = new GitHubArtifactsResponse(json);
        
        assertEquals(3, response.getTotalCount());
        List<GitHubArtifact> artifactsList = response.getArtifacts();
        assertEquals(3, artifactsList.size());
        assertEquals(100L, artifactsList.get(0).getId());
        assertEquals("build-output", artifactsList.get(0).getName());
    }

    @Test
    void testGetTotalCount() {
        JSONObject json = new JSONObject();
        json.put("total_count", 42);
        
        GitHubArtifactsResponse response = new GitHubArtifactsResponse(json);
        assertEquals(42, response.getTotalCount());
    }

    @Test
    void testGetArtifacts_Empty() {
        JSONObject json = new JSONObject();
        json.put("total_count", 0);
        json.put("artifacts", new JSONArray());
        
        GitHubArtifactsResponse response = new GitHubArtifactsResponse(json);
        
        List<GitHubArtifact> artifacts = response.getArtifacts();
        assertNotNull(artifacts);
        assertTrue(artifacts.isEmpty());
    }
}
