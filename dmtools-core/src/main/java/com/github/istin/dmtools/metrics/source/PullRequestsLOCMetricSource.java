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

            // Fetch commit diff stats
            int linesChanged = 0;
            try {
                IDiffStats diffStats = sourceCode.getCommitDiffStat(workspace, repo, model.getHash());
                if (diffStats != null) {
                    IStats stats = diffStats.getStats();
                    if (stats != null) {
                        linesChanged = stats.getAdditions() + stats.getDeletions();
                    }
                }
            } catch (Exception e) {
                logger.debug("Could not fetch diff stats for commit {}: {}", model.getHash(), e.getMessage());
            }

            KeyTime keyTime = new KeyTime(model.getId(), model.getCommitterDate(), isPersonalized ? displayName : metricName);
            keyTime.setWeight(linesChanged);
            data.add(keyTime);
        }
        return data;
    }
}
