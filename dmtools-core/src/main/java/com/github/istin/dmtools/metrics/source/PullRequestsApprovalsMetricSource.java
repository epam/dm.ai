// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.metrics.source;

import com.github.istin.dmtools.common.code.SourceCode;
import com.github.istin.dmtools.common.model.IActivity;
import com.github.istin.dmtools.common.model.IPullRequest;
import com.github.istin.dmtools.common.model.IUser;
import com.github.istin.dmtools.report.model.KeyTime;
import com.github.istin.dmtools.team.IEmployees;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class PullRequestsApprovalsMetricSource extends CommonSourceCollector {

    private final String workspace;
    private final String repo;
    private final SourceCode sourceCode;
    private final Calendar startDate;

    public PullRequestsApprovalsMetricSource(String workspace, String repo, SourceCode sourceCode, IEmployees employees, Calendar startDate) {
        super(employees);
        this.workspace = workspace;
        this.repo = repo;
        this.sourceCode = sourceCode;
        this.startDate = startDate;
    }

    @Override
    public List<KeyTime> performSourceCollection(boolean isPersonalized, String metricName) throws Exception {
        List<KeyTime> data = new ArrayList<>();
        List<IPullRequest> pullRequests = sourceCode.pullRequests(workspace, repo, IPullRequest.PullRequestState.STATE_MERGED, true, startDate);
        for (IPullRequest pullRequest : pullRequests) {

            String pullRequestAuthorDisplayName = pullRequest.getAuthor().getFullName();

            pullRequestAuthorDisplayName = getEmployees().transformName(pullRequestAuthorDisplayName);

            if (!isTeamContainsTheName(pullRequestAuthorDisplayName)) {
                pullRequestAuthorDisplayName = IEmployees.UNKNOWN;
            }

            String pullRequestIdAsString = pullRequest.getId().toString();
            List<IActivity> activities = sourceCode.pullRequestActivities(workspace, repo, pullRequestIdAsString);
            for (IActivity activity : activities) {
                String action = null;
                String activityDisplayName = null;
                IUser approval = activity.getApproval();
                if (approval != null) {
                    activityDisplayName = getEmployees().transformName(approval.getFullName());
                    if (!pullRequestAuthorDisplayName.equalsIgnoreCase(activityDisplayName)) {
                        if (!isTeamContainsTheName(activityDisplayName)) {
                            activityDisplayName = IEmployees.UNKNOWN;
                        }
                        action = "Approvals";
                    }
                }

                if (action != null) {
                    Calendar pullRequestClosedDateAsCalendar = IPullRequest.Utils.getClosedDateAsCalendar(pullRequest);
                    String keyTimeOwner = isPersonalized ? activityDisplayName : metricName;

                    KeyTime keyTime = new KeyTime(pullRequestIdAsString, pullRequestClosedDateAsCalendar, keyTimeOwner);
                    data.add(keyTime);
                }
            }
        }
        return data;
    }

}