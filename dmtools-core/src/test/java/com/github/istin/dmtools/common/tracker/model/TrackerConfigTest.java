package com.github.istin.dmtools.common.tracker.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TrackerConfigTest {

    @Test
    void testJiraConfig() {
        JiraConfig jiraConfig = JiraConfig.builder()
                .baseUrl("https://jira.example.com")
                .auth("token123")
                .projectKey("PROJ")
                .cloudId("cloud-123")
                .defaultJql("project = PROJ")
                .customFields(new String[]{"customfield_10001", "customfield_10002"})
                .type(TrackerConfig.Type.JIRA)
                .build();

        assertEquals(TrackerConfig.Type.JIRA, jiraConfig.getType());
        assertEquals("https://jira.example.com", jiraConfig.getBaseUrl());
        assertEquals("token123", jiraConfig.getAuth());
        assertEquals("PROJ", jiraConfig.getProjectKey());
        assertEquals("cloud-123", jiraConfig.getCloudId());
        assertEquals("project = PROJ", jiraConfig.getDefaultJql());
        assertArrayEquals(new String[]{"customfield_10001", "customfield_10002"}, jiraConfig.getCustomFields());
        assertTrue(jiraConfig.isConfigured());
    }

    @Test
    void testRallyConfig() {
        RallyConfig rallyConfig = RallyConfig.builder()
                .baseUrl("https://rally.example.com")
                .auth("token456")
                .projectKey("RALLY_PROJ")
                .workspace("Workspace1")
                .defaultQuery("(Project = \"RALLY_PROJ\")")
                .artifactType("UserStory")
                .type(TrackerConfig.Type.RALLY)
                .build();

        assertEquals(TrackerConfig.Type.RALLY, rallyConfig.getType());
        assertEquals("https://rally.example.com", rallyConfig.getBaseUrl());
        assertEquals("token456", rallyConfig.getAuth());
        assertEquals("RALLY_PROJ", rallyConfig.getProjectKey());
        assertEquals("Workspace1", rallyConfig.getWorkspace());
        assertEquals("(Project = \"RALLY_PROJ\")", rallyConfig.getDefaultQuery());
        assertEquals("UserStory", rallyConfig.getArtifactType());
        assertTrue(rallyConfig.isConfigured());
    }

    @Test
    void testIsConfigured() {
        TrackerConfig emptyConfig = TrackerConfig.builder().build();
        assertFalse(emptyConfig.isConfigured());

        TrackerConfig configWithBaseUrl = TrackerConfig.builder()
                .baseUrl("https://example.com")
                .build();
        assertTrue(configWithBaseUrl.isConfigured());

        TrackerConfig configWithAuth = TrackerConfig.builder()
                .auth("token")
                .build();
        assertTrue(configWithAuth.isConfigured());

        TrackerConfig configWithProjectKey = TrackerConfig.builder()
                .projectKey("PROJECT")
                .build();
        assertTrue(configWithProjectKey.isConfigured());
    }
} 