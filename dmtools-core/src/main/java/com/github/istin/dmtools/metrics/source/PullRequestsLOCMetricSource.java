// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.metrics.source;

import com.github.istin.dmtools.common.code.SourceCode;
import com.github.istin.dmtools.common.model.ICommit;
import com.github.istin.dmtools.common.model.IDiffStats;
import com.github.istin.dmtools.common.model.IStats;
import com.github.istin.dmtools.report.model.KeyTime;
import com.github.istin.dmtools.team.IEmployees;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects lines-of-code metrics from commits on a target branch.
 * For each commit, fetches diff stats (additions + deletions) via getCommitDiffStat.
 * Supports optional since date to limit the range of commits fetched.
 * Uses (additions + deletions) as weight on the KeyTime.
 * Works with GitHub, GitLab, Bitbucket via SourceCode interface.
 */
public class PullRequestsLOCMetricSource extends CommonSourceCollector {

    private static final Logger logger = LogManager.getLogger(PullRequestsLOCMetricSource.class);

    private final String workspace;
    private final String repo;
    private final String branch;
    private final String since;
    private final SourceCode sourceCode;

    public PullRequestsLOCMetricSource(String workspace, String repo, String branch, String since, SourceCode sourceCode, IEmployees employees) {
        super(employees);
        this.workspace = workspace;
        this.repo = repo;
        this.branch = branch;
        this.since = since;
        this.sourceCode = sourceCode;
    }

    @Override
    public List<KeyTime> performSourceCollection(boolean isPersonalized, String metricName) throws Exception {
        List<KeyTime> data = new ArrayList<>();
        List<ICommit> commits = sourceCode.getCommitsFromBranch(workspace, repo, branch, since, null);
        logger.info("Fetching LOC stats for {} commits from branch '{}' (since: {})", commits.size(), branch, since != null ? since : "all");
        for (ICommit model : commits) {
            if (model.getAuthor() == null) {
                continue;
            }
            String displayName = model.getAuthor().getFullName();
            if (displayName == null) {
                continue;
            }

            if (isNameIgnored(displayName)) {
                continue;
            }

            displayName = transformName(displayName);
            if (!isTeamContainsTheName(displayName)) {
                displayName = IEmployees.UNKNOWN;
            }

            String commitKey = model.getHash() != null ? model.getHash() : model.getId();
            if (commitKey == null || commitKey.isEmpty()) {
                logger.debug("Skipping commit with null key (no hash or id), author: {}", displayName);
                continue;
            }

            // Use inline stats if the commit model already carries them (e.g. GitLab with_stats=true),
            // otherwise fall back to a separate getCommitDiffStat API call.
            int additions = 0, deletions = 0;
            try {
                IDiffStats diffStats = null;
                if (model instanceof IDiffStats) {
                    IStats inlineStats = ((IDiffStats) model).getStats();
                    if (inlineStats != null) {
                        diffStats = (IDiffStats) model;
                    }
                }
                if (diffStats == null) {
                    diffStats = sourceCode.getCommitDiffStat(workspace, repo, commitKey);
                }
                if (diffStats != null) {
                    IStats stats = diffStats.getStats();
                    if (stats != null) {
                        additions = stats.getAdditions();
                        deletions = stats.getDeletions();
                    }
                }
            } catch (Exception e) {
                logger.debug("Could not fetch diff stats for commit {}: {}", commitKey, e.getMessage());
            }

            KeyTime keyTime = new KeyTime(commitKey,
                model.getCommitterDate(), isPersonalized ? displayName : metricName);
            keyTime.setWeight(additions + deletions);
            keyTime.setLink(model.getUrl());
            String msg = model.getMessage();
            String summary = (msg != null ? msg.split("\\n")[0] : "") + " (+" + additions + " -" + deletions + ")";
            keyTime.setSummary(summary);
            data.add(keyTime);
        }
        return data;
    }
}
