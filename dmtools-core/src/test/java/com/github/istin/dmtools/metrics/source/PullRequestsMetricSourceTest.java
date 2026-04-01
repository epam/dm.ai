// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.metrics.source;

import com.github.istin.dmtools.common.code.SourceCode;
import com.github.istin.dmtools.common.model.IPullRequest;
import com.github.istin.dmtools.common.model.IUser;
import com.github.istin.dmtools.report.model.KeyTime;
import com.github.istin.dmtools.team.IEmployees;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PullRequestsMetricSourceTest {

    private PullRequestsMetricSource pullRequestsMetricSource;
    private SourceCode sourceCodeMock;
    private IEmployees employeesMock;
    private Calendar startDate;

    @Before
    public void setUp() {
        sourceCodeMock = mock(SourceCode.class);
        employeesMock = mock(IEmployees.class);
        startDate = Calendar.getInstance();
        pullRequestsMetricSource = new PullRequestsMetricSource("workspace", "repo", sourceCodeMock, employeesMock, startDate);
    }

    @Test
    public void testPerformSourceCollection() throws Exception {
        List<IPullRequest> pullRequests = new ArrayList<>();
        IPullRequest pullRequestMock = mock(IPullRequest.class);
        IUser authorMock = mock(IUser.class);

        when(pullRequestMock.getAuthor()).thenReturn(authorMock);
        when(authorMock.getFullName()).thenReturn("John Doe");
        when(pullRequestMock.getId()).thenReturn(1);
        when(sourceCodeMock.pullRequests("workspace", "repo", IPullRequest.PullRequestState.STATE_MERGED, true, startDate))
                .thenReturn(pullRequests);

        pullRequests.add(pullRequestMock);

        List<KeyTime> result = pullRequestsMetricSource.performSourceCollection(true, "metricName");

        assertEquals(1, result.size());
        KeyTime keyTime = result.get(0);
        assertEquals("1", keyTime.getKey());
    }

    @Test
    public void testPerformSourceCollectionWithUnknownAuthor() throws Exception {
        List<IPullRequest> pullRequests = new ArrayList<>();
        IPullRequest pullRequestMock = mock(IPullRequest.class);
        IUser authorMock = mock(IUser.class);

        when(pullRequestMock.getAuthor()).thenReturn(authorMock);
        when(authorMock.getFullName()).thenReturn("Unknown Author");
        when(pullRequestMock.getId()).thenReturn(2);
        when(sourceCodeMock.pullRequests("workspace", "repo", IPullRequest.PullRequestState.STATE_MERGED, true, startDate))
                .thenReturn(pullRequests);

        pullRequests.add(pullRequestMock);

        when(employeesMock.contains("Unknown Author")).thenReturn(false);

        List<KeyTime> result = pullRequestsMetricSource.performSourceCollection(true, "metricName");

        assertEquals(1, result.size());
        KeyTime keyTime = result.get(0);
        assertEquals("2", keyTime.getKey());
        assertEquals(IEmployees.UNKNOWN, keyTime.getWho());
    }
}