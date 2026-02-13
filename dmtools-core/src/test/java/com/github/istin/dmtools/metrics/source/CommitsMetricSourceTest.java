package com.github.istin.dmtools.metrics.source;

import com.github.istin.dmtools.atlassian.bitbucket.Bitbucket;
import com.github.istin.dmtools.atlassian.common.networking.AtlassianRestClient;
import com.github.istin.dmtools.common.model.ICommit;
import com.github.istin.dmtools.common.model.IUser;
import com.github.istin.dmtools.report.model.KeyTime;
import com.github.istin.dmtools.team.IEmployees;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Calendar;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class CommitsMetricSourceTest {

    @Mock
    private Bitbucket bitbucket;

    @Mock
    private IEmployees employees;

    private CommitsMetricSource commitsMetricSource;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        commitsMetricSource = new CommitsMetricSource("workspace", "repo", "branch", bitbucket, employees);
    }

    @Test
    public void testPerformSourceCollection() throws Exception {
        // Arrange
        ICommit commit = mock(ICommit.class);
        when(commit.getId()).thenReturn("commitId");
        Calendar instance = Calendar.getInstance();
        when(commit.getCommitterDate()).thenReturn(instance);
        IUser author = mock(IUser.class);
        when(author.getFullName()).thenReturn("John Doe"); // CRITICAL: Must mock getFullName() or commit will be skipped
        when(commit.getAuthor()).thenReturn(author);

        doAnswer(invocation -> {
            AtlassianRestClient.Performer<ICommit> performer = invocation.getArgument(3);
            performer.perform(commit);
            return null;
        }).when(bitbucket).performCommitsFromBranch(any(), any(), any(), any());

        // Act
        List<KeyTime> result = commitsMetricSource.performSourceCollection(true, "metricName");

        // Assert
        assertEquals(1, result.size());
        KeyTime keyTime = result.get(0);
        assertEquals("commitId", keyTime.getKey());
        assertEquals(instance, keyTime.getWhen());
    }


    @Test
    public void testPerformSourceCollectionWithUnknownName() throws Exception {
        // Arrange
        ICommit commit = mock(ICommit.class);
        when(commit.getId()).thenReturn("commitId2"); // Mock getId() for KeyTime creation
        Calendar instance = Calendar.getInstance();
        when(commit.getCommitterDate()).thenReturn(instance); // Mock getCommitterDate() for KeyTime creation
        IUser mockedUser = mock(IUser.class);
        when(mockedUser.getFullName()).thenReturn("Unknown User"); // CRITICAL: Must mock getFullName() or commit will be skipped
        when(commit.getAuthor()).thenReturn(mockedUser);

        doAnswer(invocation -> {
            AtlassianRestClient.Performer<ICommit> performer = invocation.getArgument(3);
            performer.perform(commit);
            return null;
        }).when(bitbucket).performCommitsFromBranch(any(), any(), any(), any());

        // Act
        List<KeyTime> result = commitsMetricSource.performSourceCollection(true, "metricName");

        // Assert
        assertEquals(1, result.size());
        KeyTime keyTime = result.get(0);
        assertEquals(IEmployees.UNKNOWN, keyTime.getWho());
    }
}