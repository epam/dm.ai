// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.metrics.source;

import com.github.istin.dmtools.common.code.SourceCode;
import com.github.istin.dmtools.common.model.IActivity;
import com.github.istin.dmtools.common.model.IPullRequest;
import com.github.istin.dmtools.team.IEmployees;

import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Abstract base for all PR-based metric sources.
 *
 * <p>Provides two shared caches (wired by {@code MetricFactory}) that eliminate
 * redundant GitHub API calls when multiple metric sources process the same data:
 * <ul>
 *   <li><b>sharedPrList</b> – fetches the PR list once per (workspace, repo, state, date) tuple.</li>
 *   <li><b>sharedActivitiesCache</b> – fetches {@code pullRequestActivities} once per PR ID,
 *       shared across Approvals, CommentsWritten and CommentsGotten sources.</li>
 * </ul>
 */
public abstract class PullRequestsBaseMetricSource extends CommonSourceCollector {

    protected final String workspace;
    protected final String repo;
    protected final SourceCode sourceCode;
    protected final Calendar startDate;
    protected final Pattern titlePattern;

    private final String state;
    private final AtomicReference<List<IPullRequest>> sharedPrList;
    private final ConcurrentHashMap<String, List<IActivity>> sharedActivitiesCache;

    protected PullRequestsBaseMetricSource(
            String workspace,
            String repo,
            SourceCode sourceCode,
            IEmployees employees,
            Calendar startDate,
            String titleRegex,
            String state,
            AtomicReference<List<IPullRequest>> sharedPrList) {
        this(workspace, repo, sourceCode, employees, startDate, titleRegex, state, sharedPrList, null);
    }

    protected PullRequestsBaseMetricSource(
            String workspace,
            String repo,
            SourceCode sourceCode,
            IEmployees employees,
            Calendar startDate,
            String titleRegex,
            String state,
            AtomicReference<List<IPullRequest>> sharedPrList,
            ConcurrentHashMap<String, List<IActivity>> sharedActivitiesCache) {
        super(employees);
        this.workspace = workspace;
        this.repo = repo;
        this.sourceCode = sourceCode;
        this.startDate = startDate;
        this.titlePattern = (titleRegex != null && !titleRegex.isEmpty())
                ? Pattern.compile(titleRegex)
                : null;
        this.state = state;
        this.sharedPrList = (sharedPrList != null) ? sharedPrList : new AtomicReference<>(null);
        this.sharedActivitiesCache = (sharedActivitiesCache != null)
                ? sharedActivitiesCache
                : new ConcurrentHashMap<>();
    }

    /**
     * Returns the full PR list for this source's (workspace, repo, state, startDate).
     * The list is fetched from the API exactly once; subsequent calls return the cached result.
     */
    protected List<IPullRequest> getPullRequests() throws Exception {
        List<IPullRequest> cached = sharedPrList.get();
        if (cached == null) {
            List<IPullRequest> fetched;
            if (titlePattern != null && sourceCode.supportsPullRequestTitleFiltering()) {
                fetched = sourceCode.pullRequests(workspace, repo, state, true, startDate, titlePattern);
            } else {
                fetched = sourceCode.pullRequests(workspace, repo, state, true, startDate);
                if (titlePattern != null) {
                    fetched = fetched.stream()
                            .filter(pr -> titlePattern.matcher(pr.getTitle() != null ? pr.getTitle() : "").find())
                            .collect(Collectors.toList());
                }
            }
            sharedPrList.compareAndSet(null, fetched);
            cached = sharedPrList.get();
        }
        return cached;
    }

    /**
     * Returns the activity list (reviews + inline comments) for the given PR.
     * The result is fetched from the API at most once per PR ID; subsequent calls
     * for the same ID return the cached result even across different metric sources.
     */
    protected List<IActivity> getActivities(String prId) throws Exception {
        List<IActivity> cached = sharedActivitiesCache.get(prId);
        if (cached != null) return cached;
        List<IActivity> fetched = sourceCode.pullRequestActivities(workspace, repo, prId);
        List<IActivity> existing = sharedActivitiesCache.putIfAbsent(prId, fetched);
        return existing != null ? existing : fetched;
    }

    /** Returns true if this PR should be skipped by the titleRegex filter. */
    protected boolean isFilteredOut(IPullRequest pr) {
        if (titlePattern == null) return false;
        String title = pr.getTitle();
        return !titlePattern.matcher(title != null ? title : "").find();
    }
}
