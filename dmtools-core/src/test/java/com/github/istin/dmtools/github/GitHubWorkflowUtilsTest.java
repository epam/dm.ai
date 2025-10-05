package com.github.istin.dmtools.github;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GitHubWorkflowUtilsTest {

    @Mock
    private GitHub mockGitHub;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testTriggerWorkflow_ThrowsIOException() {
        when(mockGitHub.processLargePayload(anyString())).thenReturn("processed");
        when(mockGitHub.getAuthorization()).thenReturn("test-token");
        when(mockGitHub.getTimeout()).thenReturn(30);

        // This will fail because we're using a fake GitHub instance
        assertThrows(Exception.class, () -> {
            GitHubWorkflowUtils.triggerWorkflow(mockGitHub, "owner", "repo", "workflow.yml", "request");
        });
    }

    @Test
    void testTriggerWorkflow_WithNullOwner() {
        when(mockGitHub.processLargePayload(anyString())).thenReturn("processed");
        when(mockGitHub.getAuthorization()).thenReturn("test-token");
        when(mockGitHub.getTimeout()).thenReturn(30);

        assertThrows(Exception.class, () -> {
            GitHubWorkflowUtils.triggerWorkflow(mockGitHub, null, "repo", "workflow.yml", "request");
        });
    }

    @Test
    void testTriggerWorkflow_WithEmptyRepo() {
        when(mockGitHub.processLargePayload(anyString())).thenReturn("processed");
        when(mockGitHub.getAuthorization()).thenReturn("test-token");
        when(mockGitHub.getTimeout()).thenReturn(30);

        assertThrows(Exception.class, () -> {
            GitHubWorkflowUtils.triggerWorkflow(mockGitHub, "owner", "", "workflow.yml", "request");
        });
    }

    @Test
    void testTriggerWorkflow_ProcessesPayload() {
        when(mockGitHub.processLargePayload("test request")).thenReturn("processed request");
        when(mockGitHub.getAuthorization()).thenReturn("Bearer token123");
        when(mockGitHub.getTimeout()).thenReturn(60);

        try {
            GitHubWorkflowUtils.triggerWorkflow(mockGitHub, "test-owner", "test-repo", "workflow.yml", "test request");
        } catch (IOException e) {
            // Expected to fail with network error since we're not actually connecting
            assertTrue(e.getMessage().contains("workflow trigger failed") || 
                      e.getMessage().contains("URLConnection"));
        }

        // Verify that processLargePayload was called
        verify(mockGitHub, atLeastOnce()).processLargePayload("test request");
    }

    @Test
    void testTriggerWorkflow_UsesCorrectTimeout() {
        when(mockGitHub.processLargePayload(anyString())).thenReturn("processed");
        when(mockGitHub.getAuthorization()).thenReturn("test-token");
        when(mockGitHub.getTimeout()).thenReturn(120);

        try {
            GitHubWorkflowUtils.triggerWorkflow(mockGitHub, "owner", "repo", "workflow.yml", "request");
        } catch (IOException e) {
            // Expected
        }

        verify(mockGitHub, atLeastOnce()).getTimeout();
    }
}
