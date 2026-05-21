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
import java.util.concurrent.atomic.AtomicReference;

public class PullRequestsMetricSource extends PullRequestsBaseMetricSource {

    public PullRequestsMetricSource(String workspace, String repo, SourceCode sourceCode, IEmployees employees, Calendar startDate) {
        this(workspace, repo, sourceCode, employees, startDate, null, null);
    }

    public PullRequestsMetricSource(String workspace, String repo, SourceCode sourceCode, IEmployees employees, Calendar startDate, String titleRegex) {
        this(workspace, repo, sourceCode, employees, startDate, titleRegex, null);
    }

    public PullRequestsMetricSource(String workspace, String repo, SourceCode sourceCode, IEmployees employees, Calendar startDate, String titleRegex, AtomicReference<List<IPullRequest>> sharedPrList) {
        super(workspace, repo, sourceCode, employees, startDate, titleRegex, IPullRequest.PullRequestState.STATE_MERGED, sharedPrList);
    }

    @Override
    public List<KeyTime> performSourceCollection(boolean isPersonalized, String metricName) throws Exception {
        List<KeyTime> data = new ArrayList<>();
        for (IPullRequest pullRequest : getPullRequests()) {
            if (isFilteredOut(pullRequest)) continue;
            String displayName = transformName(pullRequest.getAuthor().getFullName());
            if (!isTeamContainsTheName(displayName)) {
                displayName = IEmployees.UNKNOWN;
            }
            String keyTimeOwner = isPersonalized ? displayName : metricName;
            KeyTime keyTime = new KeyTime(pullRequest.getId().toString(), IPullRequest.Utils.getClosedDateAsCalendar(pullRequest), keyTimeOwner);
            keyTime.setSummary(pullRequest.getTitle());
            data.add(keyTime);
        }
        return data;
    }
}
