// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.metrics.source;

import com.github.istin.dmtools.common.code.SourceCode;
import com.github.istin.dmtools.common.model.IPullRequest;
import com.github.istin.dmtools.team.IEmployees;

import java.util.Calendar;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * Abstract base for all PR-based metric sources.
 *
 * <p>Holds common fields (workspace, repo, sourceCode, startDate, titlePattern) and
 * exposes {@link #getPullRequests()} which fetches the PR list on first call and
 * caches it for subsequent calls via a shared {@link AtomicReference}.
 *
 * <p>When multiple metrics in the same data-source config share the same
 * {@code AtomicReference} instance (wired up by {@code MetricFactory}), the
 * GitHub API is called only once regardless of how many metrics consume the list.
 */
public abstract class PullRequestsBaseMetricSource extends CommonSourceCollector {

    protected final String workspace;
    protected final String repo;
    protected final SourceCode sourceCode;
    protected final Calendar startDate;
    protected final Pattern titlePattern;

    private final String state;
    private final AtomicReference<List<IPullRequest>> sharedPrList;

    protected PullRequestsBaseMetricSource(
            String workspace,
            String repo,
            SourceCode sourceCode,
            IEmployees employees,
            Calendar startDate,
            String titleRegex,
            String state,
            AtomicReference<List<IPullRequest>> sharedPrList) {
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
    }

    /**
     * Returns the full PR list for this source's (workspace, repo, state, startDate).
     * The list is fetched from the API exactly once; subsequent calls return the cached result.
     */
    protected List<IPullRequest> getPullRequests() throws Exception {
        List<IPullRequest> cached = sharedPrList.get();
        if (cached == null) {
            List<IPullRequest> fetched = sourceCode.pullRequests(workspace, repo, state, true, startDate);
            // compareAndSet ensures only one thread pays the fetch cost
            sharedPrList.compareAndSet(null, fetched);
            cached = sharedPrList.get();
        }
        return cached;
    }

    /** Convenience: returns true if this PR should be skipped by the titleRegex filter. */
    protected boolean isFilteredOut(IPullRequest pr) {
        if (titlePattern == null) return false;
        String title = pr.getTitle();
        return !titlePattern.matcher(title != null ? title : "").find();
    }
}
