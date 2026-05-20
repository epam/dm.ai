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
import java.util.regex.Pattern;

public class PullRequestsApprovalsMetricSource extends CommonSourceCollector {

    private final String workspace;
    private final String repo;
    private final SourceCode sourceCode;
    private final Calendar startDate;
    private final Pattern titlePattern;

    public PullRequestsApprovalsMetricSource(String workspace, String repo, SourceCode sourceCode, IEmployees employees, Calendar startDate) {
        this(workspace, repo, sourceCode, employees, startDate, null);
    }

    public PullRequestsApprovalsMetricSource(String workspace, String repo, SourceCode sourceCode, IEmployees employees, Calendar startDate, String titleRegex) {
        super(employees);
        this.workspace = workspace;
        this.repo = repo;
        this.sourceCode = sourceCode;
        this.startDate = startDate;
        this.titlePattern = titleRegex != null && !titleRegex.isEmpty() ? Pattern.compile(titleRegex) : null;
    }

    @Override
    public List<KeyTime> performSourceCollection(boolean isPersonalized, String metricName) throws Exception {
        List<KeyTime> data = new ArrayList<>();
        List<IPullRequest> pullRequests = sourceCode.pullRequests(workspace, repo, IPullRequest.PullRequestState.STATE_MERGED, true, startDate);
        for (IPullRequest pullRequest : pullRequests) {
            if (titlePattern != null && !titlePattern.matcher(pullRequest.getTitle() != null ? pullRequest.getTitle() : "").find()) {
                continue;
            }

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