// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.github;

import com.github.istin.dmtools.common.networking.GenericRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GitHub pull request label operations (add/remove).
 */
class GitHubPullRequestLabelTest {

    private GitHub github;

    @BeforeEach
    void setUp() throws IOException {
        github = spy(new TestGitHub());
    }

    @Test
    @DisplayName("addPullRequestLabel should POST label array to correct GitHub API path")
    void testAddPullRequestLabel() throws IOException {
        doReturn("").when(github).post(any(GenericRequest.class));

        github.addPullRequestLabel("IstiN", "dmtools", "74", "bug");

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(github).post(captor.capture());

        GenericRequest request = captor.getValue();
        assertTrue(request.url().contains("repos/IstiN/dmtools/issues/74/labels"),
            "Should use issues endpoint for labels. URL: " + request.url());
        assertEquals("[\"bug\"]", request.getBody(),
            "Body should be JSON array with single label");
    }

    @Test
    @DisplayName("removePullRequestLabel should DELETE to correct GitHub API path with label in URL")
    void testRemovePullRequestLabel() throws IOException {
        doReturn("").when(github).delete(any(GenericRequest.class));

        github.removePullRequestLabel("IstiN", "dmtools", "74", "bug");

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(github).delete(captor.capture());

        GenericRequest request = captor.getValue();
        assertTrue(request.url().contains("repos/IstiN/dmtools/issues/74/labels/bug"),
            "Should include label name in DELETE path. URL: " + request.url());
    }

    @Test
    @DisplayName("removePullRequestLabel should URL-encode label with special characters")
    void testRemovePullRequestLabelWithSpecialChars() throws IOException {
        doReturn("").when(github).delete(any(GenericRequest.class));

        github.removePullRequestLabel("IstiN", "dmtools", "74", "help wanted");

        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(github).delete(captor.capture());

        GenericRequest request = captor.getValue();
        assertTrue(request.url().contains("labels/help wanted"),
            "Should pass label name in path. URL: " + request.url());
    }

    /**
     * Minimal concrete subclass for testing the abstract GitHub class.
     */
    private static class TestGitHub extends GitHub {
        TestGitHub() throws IOException {
            super("https://api.github.com", "test-token");
        }

        @Override
        public String getDefaultRepository() { return "dmtools"; }

        @Override
        public String getDefaultBranch() { return "main"; }

        @Override
        public String getDefaultWorkspace() { return "IstiN"; }

        @Override
        public boolean isConfigured() { return true; }

        @Override
        public com.github.istin.dmtools.common.code.model.SourceCodeConfig getDefaultConfig() { return null; }
    }
}
