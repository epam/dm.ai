package com.github.istin.dmtools.sync;

import com.github.istin.dmtools.atlassian.jira.utils.IssuesIDsParser;
import com.github.istin.dmtools.common.code.SourceCode;
import com.github.istin.dmtools.common.model.IPullRequest;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.model.IUser;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import org.json.JSONArray;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.List;

import static org.mockito.Mockito.*;

public class SourceCodeTrackerSyncJobTest {

    private SourceCodeTrackerSyncJob job;
    private SourceCodeTrackerSyncParams params;
    private SourceCode sourceCode;
    private TrackerClient tracker;
    private IssuesIDsParser issuesIDsParser;
    private SourceCodeTrackerSyncJob.StatusSyncDelegate statusSyncDelegate;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        job = new SourceCodeTrackerSyncJob();
        params = mock(SourceCodeTrackerSyncParams.class);
        sourceCode = mock(SourceCode.class);
        tracker = mock(TrackerClient.class);
        issuesIDsParser = mock(IssuesIDsParser.class);
        statusSyncDelegate = mock(SourceCodeTrackerSyncJob.StatusSyncDelegate.class);
    }

    @Test
    public void testRunJob() throws Exception {
        when(params.getPullRequestState()).thenReturn("open");
        when(params.getIssueIdCodes()).thenReturn(new String[]{});
        when(params.isCheckAllPullRequests()).thenReturn(true);
        when(params.getAddPullRequestLabelsAsIssueType()).thenReturn(true);
        when(params.getInProgressReopenedStatuses()).thenReturn(new String[]{});

        try (MockedStatic<SourceCode.Impl> sourceCodeMock = mockStatic(SourceCode.Impl.class)) {
            sourceCodeMock.when(() -> SourceCode.Impl.getConfiguredSourceCodes(any()))
                    .thenReturn(List.of(sourceCode));

            job.runJob(params);

            verify(sourceCode, atLeastOnce()).getDefaultWorkspace();
            verify(sourceCode, atLeastOnce()).getDefaultRepository();
        }
    }

    @Test
    public void testCheckAndSyncPullRequests() throws IOException {
        when(sourceCode.pullRequests(anyString(), anyString(), anyString(), anyBoolean(), any()))
                .thenReturn(List.of(mock(IPullRequest.class)));

        SourceCodeTrackerSyncJob.checkAndSyncPullRequests(
                sourceCode, "workspace", "repository", "open", issuesIDsParser, tracker,
                s -> "icon", s -> "", statusSyncDelegate, true, true, "status1", "status2"
        );

        verify(sourceCode, atLeastOnce()).pullRequests(anyString(), anyString(), anyString(), anyBoolean(), any());
    }

    @Test
    public void testMoveToStatus() throws IOException {
        String[] statusesMapping = {"status1", "status2"};
        SourceCodeTrackerSyncJob.moveToStatus(tracker, statusesMapping, "key", "defaultStatus");

        verify(tracker, times(2)).moveToStatus(anyString(), anyString());
    }

    @Test
    public void testRenamePullRequest() throws IOException {
        IPullRequest pullRequest = mock(IPullRequest.class);
        ITicket ticket = mock(ITicket.class);
        when(ticket.getTicketTitle()).thenReturn("title");
        when(ticket.getIssueType()).thenReturn("bug");
        when(ticket.getPriority()).thenReturn("high");

        SourceCodeTrackerSyncJob.renamePullRequest(
                "workspace", "repo", sourceCode, pullRequest, ticket, s -> "icon", s -> "", true
        );

        verify(sourceCode, atLeastOnce()).renamePullRequest(anyString(), anyString(), any(), anyString());
    }

    @Test
    public void testAddTrackerCommentIfNotExists() throws IOException {
        IPullRequest pullRequest = mock(IPullRequest.class);
        when(pullRequest.getId()).thenReturn(1);
        when(pullRequest.getAuthor()).thenReturn(mock(IUser.class));

        SourceCodeTrackerSyncJob.addTrackerCommentIfNotExists(
                sourceCode, "workspace", "repository", tracker, pullRequest, "key"
        );

        verify(tracker, atLeastOnce()).postCommentIfNotExists(anyString(), anyString());
    }

    @Test
    public void testBuildMessage() throws IOException {
        ITicket ticket = mock(ITicket.class);
        when(ticket.getTicketLink()).thenReturn("link");
        when(ticket.getTicketTitle()).thenReturn("title");

        String message = SourceCodeTrackerSyncJob.buildMessage("key", ticket);

        assert message.contains("key");
        assert message.contains("link");
        assert message.contains("title");
    }
}