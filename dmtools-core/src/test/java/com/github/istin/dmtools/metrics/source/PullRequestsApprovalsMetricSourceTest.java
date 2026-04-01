// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.metrics.source;

import com.github.istin.dmtools.common.code.SourceCode;
import com.github.istin.dmtools.common.model.IActivity;
import com.github.istin.dmtools.common.model.IPullRequest;
import com.github.istin.dmtools.common.model.IUser;
import com.github.istin.dmtools.report.model.KeyTime;
import com.github.istin.dmtools.team.IEmployees;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class PullRequestsApprovalsMetricSourceTest {

    @Mock
    private SourceCode sourceCode;

    @Mock
    private IEmployees employees;

    @Mock
    private IPullRequest pullRequest;

    @Mock
    private IActivity activity;

    @Mock
    private IUser user;

    private PullRequestsApprovalsMetricSource metricSource;
    private Calendar startDate;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        startDate = Calendar.getInstance();
        metricSource = new PullRequestsApprovalsMetricSource("workspace", "repo", sourceCode, employees, startDate);
    }


    @Test
    public void testPerformSourceCollection_NoApprovals() throws Exception {
        List<IPullRequest> pullRequests = new ArrayList<>();
        pullRequests.add(pullRequest);

        List<IActivity> activities = new ArrayList<>();
        activities.add(activity);

        when(sourceCode.pullRequests("workspace", "repo", IPullRequest.PullRequestState.STATE_MERGED, true, startDate))
                .thenReturn(pullRequests);
        when(pullRequest.getAuthor()).thenReturn(user);
        when(user.getFullName()).thenReturn("Author Name");
        when(employees.transformName("Author Name")).thenReturn("Author Name");
        when(sourceCode.pullRequestActivities("workspace", "repo", pullRequest.getId().toString()))
                .thenReturn(activities);
        when(activity.getApproval()).thenReturn(null);

        List<KeyTime> result = metricSource.performSourceCollection(true, "metricName");

        assertEquals(0, result.size());
    }
}