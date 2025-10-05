package com.github.istin.dmtools.github.model;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GitHubArtifactTest {

    @Test
    void testDefaultConstructor() {
        GitHubArtifact artifact = new GitHubArtifact();
        assertNotNull(artifact);
    }

    @Test
    void testJsonStringConstructor() throws JSONException {
        String json = "{\"id\": 12345, \"name\": \"test-artifact\", \"archive_download_url\": \"https://api.github.com/download\", \"size_in_bytes\": 1024, \"created_at\": \"2024-01-01T00:00:00Z\", \"updated_at\": \"2024-01-02T00:00:00Z\", \"expired\": false}";
        GitHubArtifact artifact = new GitHubArtifact(json);
        
        assertNotNull(artifact);
        assertEquals(12345L, artifact.getId());
        assertEquals("test-artifact", artifact.getName());
        assertEquals("https://api.github.com/download", artifact.getArchiveDownloadUrl());
        assertEquals(1024L, artifact.getSize());
        assertEquals("2024-01-01T00:00:00Z", artifact.getCreatedAt());
        assertEquals("2024-01-02T00:00:00Z", artifact.getUpdatedAt());
        assertFalse(artifact.isExpired());
    }

    @Test
    void testJsonObjectConstructor() {
        JSONObject json = new JSONObject();
        json.put("id", 67890);
        json.put("name", "build-artifact");
        json.put("archive_download_url", "https://api.github.com/artifacts/67890");
        json.put("size_in_bytes", 2048);
        json.put("created_at", "2024-02-01T00:00:00Z");
        json.put("updated_at", "2024-02-02T00:00:00Z");
        json.put("expired", true);
        
        GitHubArtifact artifact = new GitHubArtifact(json);
        
        assertEquals(67890L, artifact.getId());
        assertEquals("build-artifact", artifact.getName());
        assertEquals("https://api.github.com/artifacts/67890", artifact.getArchiveDownloadUrl());
        assertEquals(2048L, artifact.getSize());
        assertEquals("2024-02-01T00:00:00Z", artifact.getCreatedAt());
        assertEquals("2024-02-02T00:00:00Z", artifact.getUpdatedAt());
        assertTrue(artifact.isExpired());
    }

    @Test
    void testGetId() {
        JSONObject json = new JSONObject();
        json.put("id", 11111);
        GitHubArtifact artifact = new GitHubArtifact(json);
        
        assertEquals(11111L, artifact.getId());
    }

    @Test
    void testGetName() {
        JSONObject json = new JSONObject();
        json.put("name", "my-artifact");
        GitHubArtifact artifact = new GitHubArtifact(json);
        
        assertEquals("my-artifact", artifact.getName());
    }

    @Test
    void testGetArchiveDownloadUrl() {
        JSONObject json = new JSONObject();
        json.put("archive_download_url", "https://example.com/download");
        GitHubArtifact artifact = new GitHubArtifact(json);
        
        assertEquals("https://example.com/download", artifact.getArchiveDownloadUrl());
    }

    @Test
    void testGetSize() {
        JSONObject json = new JSONObject();
        json.put("size_in_bytes", 5120);
        GitHubArtifact artifact = new GitHubArtifact(json);
        
        assertEquals(5120L, artifact.getSize());
    }

    @Test
    void testIsExpired_True() {
        JSONObject json = new JSONObject();
        json.put("expired", true);
        GitHubArtifact artifact = new GitHubArtifact(json);
        
        assertTrue(artifact.isExpired());
    }

    @Test
    void testIsExpired_False() {
        JSONObject json = new JSONObject();
        json.put("expired", false);
        GitHubArtifact artifact = new GitHubArtifact(json);
        
        assertFalse(artifact.isExpired());
    }
}
