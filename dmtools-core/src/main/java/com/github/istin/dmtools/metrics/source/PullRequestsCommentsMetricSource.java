// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.metrics.source;

import com.github.istin.dmtools.common.code.SourceCode;
import com.github.istin.dmtools.common.model.IActivity;
import com.github.istin.dmtools.common.model.IComment;
import com.github.istin.dmtools.common.model.IPullRequest;
import com.github.istin.dmtools.report.model.KeyTime;
import com.github.istin.dmtools.team.IEmployees;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public class PullRequestsCommentsMetricSource extends PullRequestsBaseMetricSource {

    private final boolean isPositive;

    public PullRequestsCommentsMetricSource(boolean isPositive, String workspace, String repo, SourceCode sourceCode, IEmployees employees, Calendar startDate) {
        this(isPositive, workspace, repo, sourceCode, employees, startDate, null, null, null);
    }

    public PullRequestsCommentsMetricSource(boolean isPositive, String workspace, String repo, SourceCode sourceCode, IEmployees employees, Calendar startDate, String titleRegex) {
        this(isPositive, workspace, repo, sourceCode, employees, startDate, titleRegex, null, null);
    }

    public PullRequestsCommentsMetricSource(boolean isPositive, String workspace, String repo, SourceCode sourceCode, IEmployees employees, Calendar startDate, String titleRegex, AtomicReference<List<IPullRequest>> sharedPrList) {
        this(isPositive, workspace, repo, sourceCode, employees, startDate, titleRegex, sharedPrList, null);
    }

    public PullRequestsCommentsMetricSource(boolean isPositive, String workspace, String repo, SourceCode sourceCode, IEmployees employees, Calendar startDate, String titleRegex, AtomicReference<List<IPullRequest>> sharedPrList, ConcurrentHashMap<String, List<IActivity>> sharedActivitiesCache) {
        super(workspace, repo, sourceCode, employees, startDate, titleRegex, IPullRequest.PullRequestState.STATE_MERGED, sharedPrList, sharedActivitiesCache);
        this.isPositive = isPositive;
    }

    @Override
    public List<KeyTime> performSourceCollection(boolean isPersonalized, String metricName) throws Exception {
        List<KeyTime> data = new ArrayList<>();
        for (IPullRequest pullRequest : getPullRequests()) {
            if (isFilteredOut(pullRequest)) continue;

            String pullRequestAuthorDisplayName = getEmployees().transformName(pullRequest.getAuthor().getFullName());
            if (isNameIgnored(pullRequestAuthorDisplayName)) continue;
            if (!isTeamContainsTheName(pullRequestAuthorDisplayName)) {
                pullRequestAuthorDisplayName = IEmployees.UNKNOWN;
            }

            String pullRequestIdAsString = pullRequest.getId().toString();
            String prTitle = pullRequest.getTitle();
            int commentIndex = 0;

            List<IActivity> activities = getActivities(pullRequestIdAsString);
            for (IActivity activity : activities) {
                IComment comment = activity.getComment();
                if (comment == null) continue;

                String commentDisplayName = getEmployees().transformName(comment.getAuthor().getFullName());
                if (isNameIgnored(commentDisplayName)) continue;
                if (!isTeamContainsTheName(commentDisplayName)) {
                    commentDisplayName = IEmployees.UNKNOWN;
                }

                // Skip self-comments (author commenting on own PR)
                if (pullRequestAuthorDisplayName.equalsIgnoreCase(commentDisplayName)) continue;

                Calendar pullRequestClosedDateAsCalendar = IPullRequest.Utils.getClosedDateAsCalendar(pullRequest);
                String owner = isPositive ? commentDisplayName : pullRequestAuthorDisplayName;
                String keyTimeOwner = isPersonalized ? owner : metricName;

                String uniqueKey = pullRequestIdAsString + "/c" + commentIndex;
                KeyTime keyTime = new KeyTime(uniqueKey, pullRequestClosedDateAsCalendar, keyTimeOwner);

                String prUrl = sourceCode.getPullRequestUrl(workspace, repo, pullRequestIdAsString);
                if (prUrl != null) {
                    keyTime.setLink(prUrl);
                }

                String body = comment.getBody();
                String summary = "PR #" + pullRequestIdAsString + ": " + prTitle;
                if (body != null && !body.trim().isEmpty()) {
                    String truncated = body.length() > 200 ? body.substring(0, 200) + "..." : body;
                    summary += " | " + commentDisplayName + ": " + truncated;
                }
                keyTime.setSummary(summary);
                data.add(keyTime);
                commentIndex++;
            }
        }
        return data;
    }
}
