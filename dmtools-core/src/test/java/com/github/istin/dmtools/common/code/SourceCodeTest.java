// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.code;

import com.github.istin.dmtools.atlassian.bitbucket.BasicBitbucket;
import com.github.istin.dmtools.common.model.*;
import com.github.istin.dmtools.github.BasicGithub;
import com.github.istin.dmtools.networking.AbstractRestClient;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.json.JSONArray;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

public class SourceCodeTest {

    private SourceCode sourceCodeMock;

    @Before
    public void setUp() {
        sourceCodeMock = mock(SourceCode.class);
    }

    @Test
    public void testPullRequests() throws IOException {
        List<IPullRequest> mockPullRequests = new ArrayList<>();
        when(sourceCodeMock.pullRequests(anyString(), anyString(), anyString(), anyBoolean(), any())).thenReturn(mockPullRequests);

        List<IPullRequest> result = sourceCodeMock.pullRequests("workspace", "repository", "state", true, null);
        assertEquals(mockPullRequests, result);
    }

    @Test
    public void testPullRequest() throws IOException {
        IPullRequest mockPullRequest = mock(IPullRequest.class);
        when(sourceCodeMock.pullRequest(anyString(), anyString(), anyString())).thenReturn(mockPullRequest);

        IPullRequest result = sourceCodeMock.pullRequest("workspace", "repository", "pullRequestId");
        assertEquals(mockPullRequest, result);
    }

    @Test
    public void testPullRequestComments() throws IOException {
        List<IComment> mockComments = new ArrayList<>();
        when(sourceCodeMock.pullRequestComments(anyString(), anyString(), anyString())).thenReturn(mockComments);

        List<IComment> result = sourceCodeMock.pullRequestComments("workspace", "repository", "pullRequestId");
        assertEquals(mockComments, result);
    }

    @Test
    public void testPullRequestActivities() throws IOException {
        List<IActivity> mockActivities = new ArrayList<>();
        when(sourceCodeMock.pullRequestActivities(anyString(), anyString(), anyString())).thenReturn(mockActivities);

        List<IActivity> result = sourceCodeMock.pullRequestActivities("workspace", "repository", "pullRequestId");
        assertEquals(mockActivities, result);
    }

    @Test
    public void testPullRequestTasks() throws IOException {
        List<ITask> mockTasks = new ArrayList<>();
        when(sourceCodeMock.pullRequestTasks(anyString(), anyString(), anyString())).thenReturn(mockTasks);

        List<ITask> result = sourceCodeMock.pullRequestTasks("workspace", "repository", "pullRequestId");
        assertEquals(mockTasks, result);
    }

    @Test
    public void testAddTask() throws IOException {
        String mockResponse = "Task added";
        when(sourceCodeMock.addTask(anyInt(), anyString())).thenReturn(mockResponse);

        String result = sourceCodeMock.addTask(1, "text");
        assertEquals(mockResponse, result);
    }

    @Test
    public void testCreatePullRequestCommentAndTaskIfNotExists() throws IOException {
        String mockResponse = "Comment and task created";
        when(sourceCodeMock.createPullRequestCommentAndTaskIfNotExists(anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(mockResponse);

        String result = sourceCodeMock.createPullRequestCommentAndTaskIfNotExists("workspace", "repository", "pullRequestId", "commentText", "taskText");
        assertEquals(mockResponse, result);
    }

    @Test
    public void testCreatePullRequestCommentAndTask() throws IOException {
        String mockResponse = "Comment and task created";
        when(sourceCodeMock.createPullRequestCommentAndTask(anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(mockResponse);

        String result = sourceCodeMock.createPullRequestCommentAndTask("workspace", "repository", "pullRequestId", "commentText", "taskText");
        assertEquals(mockResponse, result);
    }

    @Test
    public void testAddPullRequestComment() throws IOException {
        String mockResponse = "Comment added";
        when(sourceCodeMock.addPullRequestComment(anyString(), anyString(), anyString(), anyString())).thenReturn(mockResponse);

        String result = sourceCodeMock.addPullRequestComment("workspace", "repository", "pullRequestId", "text");
        assertEquals(mockResponse, result);
    }

    @Test
    public void testCommitComments() throws IOException {
        JSONModel mockJsonModel = mock(JSONModel.class);
        when(sourceCodeMock.commitComments(anyString(), anyString(), anyString())).thenReturn(mockJsonModel);

        JSONModel result = sourceCodeMock.commitComments("workspace", "repository", "commitId");
        assertEquals(mockJsonModel, result);
    }

    @Test
    public void testRenamePullRequest() throws IOException {
        String mockResponse = "Pull request renamed";
        IPullRequest mockPullRequest = mock(IPullRequest.class);
        when(sourceCodeMock.renamePullRequest(anyString(), anyString(), eq(mockPullRequest), anyString())).thenReturn(mockResponse);

        String result = sourceCodeMock.renamePullRequest("workspace", "repository", mockPullRequest, "newTitle");
        assertEquals(mockResponse, result);
    }

    @Test
    public void testGetTags() throws IOException {
        List<ITag> mockTags = new ArrayList<>();
        when(sourceCodeMock.getTags(anyString(), anyString())).thenReturn(mockTags);

        List<ITag> result = sourceCodeMock.getTags("workspace", "repository");
        assertEquals(mockTags, result);
    }

    @Test
    public void testGetBranches() throws IOException {
        List<ITag> mockBranches = new ArrayList<>();
        when(sourceCodeMock.getBranches(anyString(), anyString())).thenReturn(mockBranches);

        List<ITag> result = sourceCodeMock.getBranches("workspace", "repository");
        assertEquals(mockBranches, result);
    }

    @Test
    public void testAddPullRequestLabel() throws IOException {
        doNothing().when(sourceCodeMock).addPullRequestLabel(anyString(), anyString(), anyString(), anyString());

        sourceCodeMock.addPullRequestLabel("workspace", "repository", "pullRequestId", "label");
        verify(sourceCodeMock, times(1)).addPullRequestLabel("workspace", "repository", "pullRequestId", "label");
    }

    @Test
    public void testGetRepositories() throws IOException {
        List<IRepository> mockRepositories = new ArrayList<>();
        when(sourceCodeMock.getRepositories(anyString())).thenReturn(mockRepositories);

        List<IRepository> result = sourceCodeMock.getRepositories("namespace");
        assertEquals(mockRepositories, result);
    }

    @Test
    public void testGetCommitsBetween() throws IOException {
        List<ICommit> mockCommits = new ArrayList<>();
        when(sourceCodeMock.getCommitsBetween(anyString(), anyString(), anyString(), anyString())).thenReturn(mockCommits);

        List<ICommit> result = sourceCodeMock.getCommitsBetween("workspace", "repository", "from", "to");
        assertEquals(mockCommits, result);
    }

    @Test
    public void testGetCommitsFromBranch() throws IOException {
        List<ICommit> mockCommits = new ArrayList<>();
        when(sourceCodeMock.getCommitsFromBranch(anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(mockCommits);

        List<ICommit> result = sourceCodeMock.getCommitsFromBranch("workspace", "repository", "branchName", "2024-01-15", "2024-01-16");
        assertEquals(mockCommits, result);
    }

    @Test
    public void testPerformCommitsFromBranch() throws Exception {
        AbstractRestClient.Performer<ICommit> performerMock = mock(AbstractRestClient.Performer.class);
        doNothing().when(sourceCodeMock).performCommitsFromBranch(anyString(), anyString(), anyString(), eq(performerMock));

        sourceCodeMock.performCommitsFromBranch("workspace", "repository", "branchName", performerMock);
        verify(sourceCodeMock, times(1)).performCommitsFromBranch("workspace", "repository", "branchName", performerMock);
    }

    @Test
    public void testGetCommitDiffStat() throws IOException {
        IDiffStats mockDiffStats = mock(IDiffStats.class);
        when(sourceCodeMock.getCommitDiffStat(anyString(), anyString(), anyString())).thenReturn(mockDiffStats);

        IDiffStats result = sourceCodeMock.getCommitDiffStat("workspace", "repository", "commitId");
        assertEquals(mockDiffStats, result);
    }

    @Test
    public void testGetPullRequestDiff() throws IOException {
        IDiffStats mockDiffStats = mock(IDiffStats.class);
        when(sourceCodeMock.getPullRequestDiff(anyString(), anyString(), anyString())).thenReturn(mockDiffStats);

        IDiffStats result = sourceCodeMock.getPullRequestDiff("workspace", "repository", "pullRequestID");
        assertEquals(mockDiffStats, result);
    }

    @Test
    public void testGetCommitDiff() throws IOException {
        IBody mockBody = mock(IBody.class);
        when(sourceCodeMock.getCommitDiff(anyString(), anyString(), anyString())).thenReturn(mockBody);

        IBody result = sourceCodeMock.getCommitDiff("workspace", "repository", "commitId");
        assertEquals(mockBody, result);
    }

    @Test
    public void testGetDiff() throws IOException {
        IBody mockBody = mock(IBody.class);
        when(sourceCodeMock.getDiff(anyString(), anyString(), anyString())).thenReturn(mockBody);

        IBody result = sourceCodeMock.getDiff("workspace", "repository", "pullRequestId");
        assertEquals(mockBody, result);
    }

    @Test
    public void testGetListOfFiles() throws IOException {
        List<IFile> mockFiles = new ArrayList<>();
        when(sourceCodeMock.getListOfFiles(anyString(), anyString(), anyString())).thenReturn(mockFiles);

        List<IFile> result = sourceCodeMock.getListOfFiles("workspace", "repository", "branchName");
        assertEquals(mockFiles, result);
    }

    @Test
    public void testGetFileContent() throws IOException {
        String mockContent = "File content";
        when(sourceCodeMock.getFileContent(anyString())).thenReturn(mockContent);

        String result = sourceCodeMock.getFileContent("selfLink");
        assertEquals(mockContent, result);
    }

    @Test
    public void testGetDefaultRepository() {
        String mockRepository = "defaultRepo";
        when(sourceCodeMock.getDefaultRepository()).thenReturn(mockRepository);

        String result = sourceCodeMock.getDefaultRepository();
        assertEquals(mockRepository, result);
    }

    @Test
    public void testGetDefaultBranch() {
        String mockBranch = "defaultBranch";
        when(sourceCodeMock.getDefaultBranch()).thenReturn(mockBranch);

        String result = sourceCodeMock.getDefaultBranch();
        assertEquals(mockBranch, result);
    }

    @Test
    public void testGetDefaultWorkspace() {
        String mockWorkspace = "defaultWorkspace";
        when(sourceCodeMock.getDefaultWorkspace()).thenReturn(mockWorkspace);

        String result = sourceCodeMock.getDefaultWorkspace();
        assertEquals(mockWorkspace, result);
    }

    @Test
    public void testIsConfigured() {
        when(sourceCodeMock.isConfigured()).thenReturn(true);

        boolean result = sourceCodeMock.isConfigured();
        assertTrue(result);
    }

    @Test
    public void testGetBasePath() {
        String mockBasePath = "/base/path";
        when(sourceCodeMock.getBasePath()).thenReturn(mockBasePath);

        String result = sourceCodeMock.getBasePath();
        assertEquals(mockBasePath, result);
    }

    @Test
    public void testGetPullRequestUrl() {
        String mockUrl = "http://example.com/pullrequest";
        when(sourceCodeMock.getPullRequestUrl(anyString(), anyString(), anyString())).thenReturn(mockUrl);

        String result = sourceCodeMock.getPullRequestUrl("workspace", "repository", "id");
        assertEquals(mockUrl, result);
    }

    @Test
    public void testGetConfiguredSourceCodes() throws IOException {
        try (MockedStatic<BasicGithub> githubMockedStatic = Mockito.mockStatic(BasicGithub.class);
             MockedStatic<BasicBitbucket> bitbucketMockedStatic = Mockito.mockStatic(BasicBitbucket.class)) {

            SourceCode githubMock = mock(SourceCode.class);
            when(githubMock.isConfigured()).thenReturn(true);
            githubMockedStatic.when(BasicGithub::getInstance).thenReturn(githubMock);

            SourceCode bitbucketMock = mock(SourceCode.class);
            when(bitbucketMock.isConfigured()).thenReturn(true);
            bitbucketMockedStatic.when(BasicBitbucket::getInstance).thenReturn(bitbucketMock);


            JSONArray sources = new JSONArray("[\"github\", \"bitbucket\"]");
            List<SourceCode> result = SourceCode.Impl.getConfiguredSourceCodes(sources);
            assertEquals(2, result.size());
        }
    }
}