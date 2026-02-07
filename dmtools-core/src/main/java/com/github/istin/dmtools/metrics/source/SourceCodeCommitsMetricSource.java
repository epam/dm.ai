package com.github.istin.dmtools.metrics.source;

import com.github.istin.dmtools.common.code.SourceCode;
import com.github.istin.dmtools.common.model.ICommit;
import com.github.istin.dmtools.report.model.KeyTime;
import com.github.istin.dmtools.team.IEmployees;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects commit metrics using the generic SourceCode interface.
 * Supports optional since date to limit the range of commits fetched.
 * Works with GitHub, GitLab, Bitbucket, etc.
 */
public class SourceCodeCommitsMetricSource extends CommonSourceCollector {

    private final String workspace;
    private final String repo;
    private final String branch;
    private final String since;
    private final SourceCode sourceCode;

    public SourceCodeCommitsMetricSource(String workspace, String repo, String branch, String since, SourceCode sourceCode, IEmployees employees) {
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

            KeyTime keyTime = new KeyTime(model.getId(), model.getCommitterDate(), isPersonalized ? displayName : metricName);
            data.add(keyTime);
        }
        return data;
    }
}
