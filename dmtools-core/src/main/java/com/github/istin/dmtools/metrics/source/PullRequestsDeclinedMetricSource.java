// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.metrics.source;

import com.github.istin.dmtools.common.code.SourceCode;
import com.github.istin.dmtools.common.model.IPullRequest;
import com.github.istin.dmtools.report.model.KeyTime;
import com.github.istin.dmtools.team.IEmployees;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Counts declined (closed without merge) PRs, attributed to the PR author.
 */
public class PullRequestsDeclinedMetricSource extends CommonSourceCollector {

    private final String workspace;
    private final String repo;
    private final SourceCode sourceCode;
    private final Calendar startDate;
    private final Pattern titlePattern;

    public PullRequestsDeclinedMetricSource(String workspace, String repo, SourceCode sourceCode, IEmployees employees, Calendar startDate) {
        this(workspace, repo, sourceCode, employees, startDate, null);
    }

    public PullRequestsDeclinedMetricSource(String workspace, String repo, SourceCode sourceCode, IEmployees employees, Calendar startDate, String titleRegex) {
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
        List<IPullRequest> pullRequests = sourceCode.pullRequests(workspace, repo, IPullRequest.PullRequestState.STATE_DECLINED, true, startDate);
        for (IPullRequest pullRequest : pullRequests) {
            if (titlePattern != null && !titlePattern.matcher(pullRequest.getTitle() != null ? pullRequest.getTitle() : "").find()) {
                continue;
            }
            String displayName = transformName(pullRequest.getAuthor().getFullName());
            if (!isTeamContainsTheName(displayName)) {
                displayName = IEmployees.UNKNOWN;
            }
            String keyTimeOwner = isPersonalized ? displayName : metricName;
            Calendar closedDate = pullRequest.getClosedDate() != null
                    ? IPullRequest.Utils.getClosedDateAsCalendar(pullRequest)
                    : IPullRequest.Utils.getUpdatedDateAsCalendar(pullRequest);
            KeyTime keyTime = new KeyTime(pullRequest.getId().toString(), closedDate, keyTimeOwner);
            keyTime.setSummary(pullRequest.getTitle());
            data.add(keyTime);
        }
        return data;
    }
}
