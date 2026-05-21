// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.metrics.source;

import com.github.istin.dmtools.common.code.SourceCode;
import com.github.istin.dmtools.common.model.IPullRequest;
import com.github.istin.dmtools.common.model.IUser;
import com.github.istin.dmtools.report.model.KeyTime;
import com.github.istin.dmtools.team.IEmployees;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Counts merged PRs attributed to the person who performed the merge (merged_by).
 * Falls back to PR author if merged_by is not available.
 */
public class PullRequestsMergedByMetricSource extends PullRequestsBaseMetricSource {

    private static final Logger logger = LogManager.getLogger(PullRequestsMergedByMetricSource.class);

    public PullRequestsMergedByMetricSource(String workspace, String repo, SourceCode sourceCode, IEmployees employees, Calendar startDate) {
        this(workspace, repo, sourceCode, employees, startDate, null, null);
    }

    public PullRequestsMergedByMetricSource(String workspace, String repo, SourceCode sourceCode, IEmployees employees, Calendar startDate, String titleRegex) {
        this(workspace, repo, sourceCode, employees, startDate, titleRegex, null);
    }

    public PullRequestsMergedByMetricSource(String workspace, String repo, SourceCode sourceCode, IEmployees employees, Calendar startDate, String titleRegex, AtomicReference<List<IPullRequest>> sharedPrList) {
        super(workspace, repo, sourceCode, employees, startDate, titleRegex, IPullRequest.PullRequestState.STATE_MERGED, sharedPrList);
    }

    @Override
    public List<KeyTime> performSourceCollection(boolean isPersonalized, String metricName) throws Exception {
        List<KeyTime> data = new ArrayList<>();
        for (IPullRequest pullRequest : getPullRequests()) {
            if (isFilteredOut(pullRequest)) continue;
            IUser merger = pullRequest.getMergedBy();

            // If merged_by not in list response, fetch individual PR for full details
            if (merger == null) {
                try {
                    IPullRequest fullPR = sourceCode.pullRequest(workspace, repo, pullRequest.getId().toString());
                    if (fullPR != null) {
                        merger = fullPR.getMergedBy();
                    }
                } catch (Exception e) {
                    logger.debug("Could not fetch full PR details for {}: {}", pullRequest.getId(), e.getMessage());
                }
            }

            // Fallback to author if merged_by is not available
            if (merger == null) {
                merger = pullRequest.getAuthor();
            }

            String displayName = transformName(merger.getFullName());
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
