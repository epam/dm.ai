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

public class PullRequestsCommentsMetricSource extends CommonSourceCollector {

    private final boolean isPositive;
    private final String workspace;
    private final String repo;
    private final SourceCode sourceCode;
    private final Calendar startDate;

    public PullRequestsCommentsMetricSource(boolean isPositive, String workspace, String repo, SourceCode sourceCode, IEmployees employees, Calendar startDate) {
        super(employees);
        this.isPositive = isPositive;
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
            String prTitle = pullRequest.getTitle();
            int commentIndex = 0;

            List<IActivity> activities = sourceCode.pullRequestActivities(workspace, repo, pullRequestIdAsString);
            for (IActivity activity : activities) {
                IComment comment = activity.getComment();
                if (comment == null) {
                    continue;
                }

                String commentDisplayName = comment.getAuthor().getFullName();
                commentDisplayName = getEmployees().transformName(commentDisplayName);

                if (!isTeamContainsTheName(commentDisplayName)) {
                    commentDisplayName = IEmployees.UNKNOWN;
                }

                // Skip self-comments (author commenting on own PR)
                if (pullRequestAuthorDisplayName.equalsIgnoreCase(commentDisplayName)) {
                    continue;
                }

                Calendar pullRequestClosedDateAsCalendar = IPullRequest.Utils.getClosedDateAsCalendar(pullRequest);
                String owner = isPositive ? commentDisplayName : pullRequestAuthorDisplayName;
                String keyTimeOwner = isPersonalized ? owner : metricName;

                // Unique key per comment so each appears as a separate dataset entry
                // Note: Key includes comment index for uniqueness, but link is set to PR URL
                String uniqueKey = pullRequestIdAsString + "/c" + commentIndex;
                KeyTime keyTime = new KeyTime(uniqueKey, pullRequestClosedDateAsCalendar, keyTimeOwner);

                // Explicitly set link to valid PR URL (without comment index suffix)
                // This prevents PullRequestsDataSource from building invalid URLs like /pull/123/c0
                // Use SourceCode.getPullRequestUrl() which is implemented by GitHub/GitLab/Bitbucket
                String prUrl = sourceCode.getPullRequestUrl(workspace, repo, pullRequestIdAsString);
                if (prUrl != null) {
                    keyTime.setLink(prUrl);
                }

                // Build summary with PR context and comment body
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
