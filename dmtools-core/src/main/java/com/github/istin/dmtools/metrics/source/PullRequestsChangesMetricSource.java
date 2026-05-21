// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.metrics.source;

import com.github.istin.dmtools.common.code.SourceCode;
import com.github.istin.dmtools.common.model.IDiffStats;
import com.github.istin.dmtools.common.model.IPullRequest;
import com.github.istin.dmtools.report.model.KeyTime;
import com.github.istin.dmtools.team.IEmployees;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class PullRequestsChangesMetricSource extends PullRequestsBaseMetricSource {

    private final double divider;

    public PullRequestsChangesMetricSource(
            String workspace, String repo, SourceCode sourceCode, IEmployees employees,
            Calendar startDate, String titleRegex,
            AtomicReference<List<IPullRequest>> sharedPrList,
            double divider) {
        super(workspace, repo, sourceCode, employees, startDate, titleRegex,
                IPullRequest.PullRequestState.STATE_MERGED, sharedPrList);
        this.divider = divider > 0 ? divider : 1.0;
    }

    @Override
    public List<KeyTime> performSourceCollection(boolean isPersonalized, String metricName) throws Exception {
        List<KeyTime> data = new ArrayList<>();
        for (IPullRequest pullRequest : getPullRequests()) {
            if (isFilteredOut(pullRequest)) continue;

            String rawName = pullRequest.getAuthor() != null ? pullRequest.getAuthor().getFullName() : null;
            if (rawName == null || isNameIgnored(rawName)) continue;

            String displayName = transformName(rawName);
            if (!isTeamContainsTheName(displayName)) {
                displayName = IEmployees.UNKNOWN;
            }

            IDiffStats diffStats = sourceCode.getPullRequestDiff(workspace, repo, String.valueOf(pullRequest.getId()));
            if (diffStats == null || diffStats.getStats() == null) continue;

            KeyTime keyTime = new KeyTime(
                    pullRequest.getId().toString(),
                    IPullRequest.Utils.getClosedDateAsCalendar(pullRequest),
                    isPersonalized ? displayName : metricName);
            keyTime.setWeight(diffStats.getStats().getTotal() / divider);
            data.add(keyTime);
        }
        return data;
    }
}

