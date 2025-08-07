package com.github.istin.dmtools.job;

import com.github.istin.dmtools.common.ai.config.AIPromptConfig;
import com.github.istin.dmtools.common.code.model.SourceCodeConfig;
import com.github.istin.dmtools.common.tracker.model.JiraConfig;
import com.github.istin.dmtools.common.tracker.model.RallyConfig;
import com.github.istin.dmtools.common.tracker.model.TrackerConfig;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ParamsTest {

    @Test
    void testSourceCodeConfigAndTrackerConfig() {
        Params params = new Params();
        
        // Test SourceCodeConfig
        SourceCodeConfig sourceCodeConfig1 = SourceCodeConfig.builder()
                .repoName("repo1")
                .branchName("main")
                .type(SourceCodeConfig.Type.GITHUB)
                .build();
        
        SourceCodeConfig sourceCodeConfig2 = SourceCodeConfig.builder()
                .repoName("repo2")
                .branchName("develop")
                .type(SourceCodeConfig.Type.GITLAB)
                .build();
        
        params.setSourceCodeConfigs(sourceCodeConfig1, sourceCodeConfig2);
        
        assertEquals(2, params.getSourceCodeConfig().length);
        assertEquals("repo1", params.getSourceCodeConfig()[0].getRepoName());
        assertEquals("repo2", params.getSourceCodeConfig()[1].getRepoName());
        
        // Test TrackerConfig
        JiraConfig jiraConfig = JiraConfig.builder()
                .baseUrl("https://jira.example.com")
                .projectKey("PROJ")
                .cloudId("cloud-123")
                .type(TrackerConfig.Type.JIRA)
                .build();
        
        RallyConfig rallyConfig = RallyConfig.builder()
                .baseUrl("https://rally.example.com")
                .projectKey("RALLY_PROJ")
                .workspace("Workspace1")
                .type(TrackerConfig.Type.RALLY)
                .build();
        
        params.setTrackerConfigs(jiraConfig, rallyConfig);
        
        assertEquals(2, params.getTrackerConfig().length);
        assertEquals(TrackerConfig.Type.JIRA, params.getTrackerConfig()[0].getType());
        assertEquals("https://jira.example.com", params.getTrackerConfig()[0].getBaseUrl());
        assertEquals("PROJ", params.getTrackerConfig()[0].getProjectKey());
        
        assertEquals(TrackerConfig.Type.RALLY, params.getTrackerConfig()[1].getType());
        assertEquals("https://rally.example.com", params.getTrackerConfig()[1].getBaseUrl());
        assertEquals("RALLY_PROJ", params.getTrackerConfig()[1].getProjectKey());
        
        // Test that we can cast to specific tracker types
        assertTrue(params.getTrackerConfig()[0] instanceof JiraConfig);
        assertTrue(params.getTrackerConfig()[1] instanceof RallyConfig);
        
        JiraConfig retrievedJiraConfig = (JiraConfig) params.getTrackerConfig()[0];
        assertEquals("cloud-123", retrievedJiraConfig.getCloudId());
        
        RallyConfig retrievedRallyConfig = (RallyConfig) params.getTrackerConfig()[1];
        assertEquals("Workspace1", retrievedRallyConfig.getWorkspace());
    }
    
    @Test
    void testAIPromptConfig() {
        Params params = new Params();
        
        AIPromptConfig aiPromptConfig = AIPromptConfig.builder()
                .modelName("gpt-4")
                .modelProvider(AIPromptConfig.ModelProvider.DIAL)
                .apiKey("sk-1234567890abcdef")
                .promptChunkTokenLimit(8000)
                .promptChunkMaxSingleFileSize(1024L * 1024L) // 1MB
                .promptChunkMaxTotalFilesSize(4L * 1024L * 1024L) // 4MB
                .promptChunkMaxFiles(10)
                .build();
        
        params.setAiPromptConfig(aiPromptConfig);
        
        assertNotNull(params.getAiPromptConfig());
        assertEquals("gpt-4", params.getAiPromptConfig().getModelName());
        assertEquals(AIPromptConfig.ModelProvider.DIAL, params.getAiPromptConfig().getModelProvider());
        assertEquals("sk-1234567890abcdef", params.getAiPromptConfig().getApiKey());
        assertEquals(8000, params.getAiPromptConfig().getPromptChunkTokenLimit());
        assertEquals(1024L * 1024L, params.getAiPromptConfig().getPromptChunkMaxSingleFileSize());
        assertEquals(4L * 1024L * 1024L, params.getAiPromptConfig().getPromptChunkMaxTotalFilesSize());
        assertEquals(10, params.getAiPromptConfig().getPromptChunkMaxFiles());
        
        assertTrue(params.getAiPromptConfig().isModelConfigured());
        assertTrue(params.getAiPromptConfig().isChunkConfigured());
    }
} 