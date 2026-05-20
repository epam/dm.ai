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
import java.util.regex.Pattern;

/**
 * Counts merged PRs attributed to the person who performed the merge (merged_by).
 * Falls back to PR author if merged_by is not available.
 */
public class PullRequestsMergedByMetricSource extends CommonSourceCollector {

    private static final Logger logger = LogManager.getLogger(PullRequestsMergedByMetricSource.class);

    private final String workspace;
    private final String repo;
    private final SourceCode sourceCode;
    private final Calendar startDate;
    private final Pattern titlePattern;

    public PullRequestsMergedByMetricSource(String workspace, String repo, SourceCode sourceCode, IEmployees employees, Calendar startDate) {
        this(workspace, repo, sourceCode, employees, startDate, null);
    }

    public PullRequestsMergedByMetricSource(String workspace, String repo, SourceCode sourceCode, IEmployees employees, Calendar startDate, String titleRegex) {
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
