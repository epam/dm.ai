// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.metrics.source;

import com.github.istin.dmtools.common.code.SourceCode;
import com.github.istin.dmtools.common.model.ICommit;
import com.github.istin.dmtools.common.model.IDiffStats;
import com.github.istin.dmtools.common.model.IStats;
import com.github.istin.dmtools.common.model.ITag;
import com.github.istin.dmtools.report.model.KeyTime;
import com.github.istin.dmtools.team.IEmployees;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Collects lines-of-code metrics from commits on a target branch.
 * For each commit, fetches diff stats (additions + deletions) via getCommitDiffStat.
 * Supports optional since date to limit the range of commits fetched.
 * Uses (additions + deletions) as weight on the KeyTime.
 * Works with GitHub, GitLab, Bitbucket via SourceCode interface.
 *
 * Supports two branch selection modes:
 * - {@code branch}: exact branch name (existing behavior)
 * - {@code branchNameRegex}: regex matched against all branch names; commits from all
 *   matching branches are aggregated and de-duplicated by commit hash.
 */
public class PullRequestsLOCMetricSource extends CommonSourceCollector {

    private static final Logger logger = LogManager.getLogger(PullRequestsLOCMetricSource.class);

    private final String workspace;
    private final String repo;
    private final String branch;
    private final String since;
    private final SourceCode sourceCode;
    private final Pattern branchPattern;

    public PullRequestsLOCMetricSource(String workspace, String repo, String branch, String since, SourceCode sourceCode, IEmployees employees) {
        this(workspace, repo, branch, since, sourceCode, employees, null);
    }

    public PullRequestsLOCMetricSource(String workspace, String repo, String branch, String since, SourceCode sourceCode, IEmployees employees, String branchNameRegex) {
        super(employees);
        this.workspace = workspace;
        this.repo = repo;
        this.branch = branch;
        this.since = since;
        this.sourceCode = sourceCode;
        this.branchPattern = branchNameRegex != null && !branchNameRegex.isEmpty() ? Pattern.compile(branchNameRegex) : null;
    }

    @Override
    public List<KeyTime> performSourceCollection(boolean isPersonalized, String metricName) throws Exception {
        List<ICommit> commits;
        if (branchPattern != null) {
            commits = collectCommitsFromMatchingBranches();
        } else {
            commits = sourceCode.getCommitsFromBranch(workspace, repo, branch, since, null);
            logger.info("Fetching LOC stats for {} commits from branch '{}' (since: {})", commits.size(), branch, since != null ? since : "all");
        }

        List<KeyTime> data = new ArrayList<>();
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

            // Fetch commit diff stats
            int additions = 0, deletions = 0;
            try {
                IDiffStats diffStats = sourceCode.getCommitDiffStat(workspace, repo, commitKey);
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

    private List<ICommit> collectCommitsFromMatchingBranches() throws Exception {
        List<ITag> branches = sourceCode.getBranches(workspace, repo);
        List<String> matchingBranches = new ArrayList<>();
        for (ITag b : branches) {
            String name = b.getName();
            if (name != null && branchPattern.matcher(name).find()) {
                matchingBranches.add(name);
            }
        }
        logger.info("branchNameRegex '{}' matched {} of {} branches in {}/{}",
            branchPattern.pattern(), matchingBranches.size(), branches.size(), workspace, repo);

        Set<String> seenHashes = new LinkedHashSet<>();
        List<ICommit> result = new ArrayList<>();
        for (String matchedBranch : matchingBranches) {
            List<ICommit> branchCommits = sourceCode.getCommitsFromBranch(workspace, repo, matchedBranch, since, null);
            for (ICommit commit : branchCommits) {
                String key = commit.getHash() != null ? commit.getHash() : commit.getId();
                if (key != null && seenHashes.add(key)) {
                    result.add(commit);
                }
            }
        }
        logger.info("Collected {} unique LOC commits from {} matching branches", result.size(), matchingBranches.size());
        return result;
    }
}
