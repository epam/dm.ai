// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.metrics.source;

import com.github.istin.dmtools.common.code.SourceCode;
import com.github.istin.dmtools.common.model.ICommit;
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
 * Collects commit metrics using the generic SourceCode interface.
 * Supports optional since date to limit the range of commits fetched.
 * Works with GitHub, GitLab, Bitbucket, etc.
 *
 * Supports two branch selection modes:
 * - {@code branch}: exact branch name (existing behavior)
 * - {@code branchNameRegex}: regex matched against all branch names; commits from all
 *   matching branches are aggregated and de-duplicated by commit hash.
 */
public class SourceCodeCommitsMetricSource extends CommonSourceCollector {

    private static final Logger logger = LogManager.getLogger(SourceCodeCommitsMetricSource.class);

    private final String workspace;
    private final String repo;
    private final String branch;
    private final String since;
    private final SourceCode sourceCode;
    private final Pattern branchPattern;

    public SourceCodeCommitsMetricSource(String workspace, String repo, String branch, String since, SourceCode sourceCode, IEmployees employees) {
        this(workspace, repo, branch, since, sourceCode, employees, null);
    }

    public SourceCodeCommitsMetricSource(String workspace, String repo, String branch, String since, SourceCode sourceCode, IEmployees employees, String branchNameRegex) {
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
        }

        List<KeyTime> data = new ArrayList<>();
        for (ICommit model : commits) {
            if (model.getAuthor() == null) {
                continue;
            }
            String fullName = model.getAuthor().getFullName();
            if (fullName == null) {
                continue;
            }
            String displayName = transformName(fullName);
            if (isNameIgnored(displayName)) {
                continue;
            }

            if (!isTeamContainsTheName(displayName)) {
                displayName = IEmployees.UNKNOWN;
            }

            String commitKey = model.getHash() != null ? model.getHash() : model.getId();
            if (commitKey == null || commitKey.isEmpty()) {
                continue;
            }

            KeyTime keyTime = new KeyTime(commitKey,
                model.getCommitterDate(), isPersonalized ? displayName : metricName);
            keyTime.setLink(model.getUrl());
            keyTime.setSummary(model.getMessage());
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
        logger.info("Collected {} unique commits from {} matching branches", result.size(), matchingBranches.size());
        return result;
    }
}
