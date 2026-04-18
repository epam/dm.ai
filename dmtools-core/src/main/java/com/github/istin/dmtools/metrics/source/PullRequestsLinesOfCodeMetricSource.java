// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.metrics.source;

import com.github.istin.dmtools.atlassian.common.networking.AtlassianRestClient;
import com.github.istin.dmtools.common.code.SourceCode;
import com.github.istin.dmtools.common.model.ICommit;
import com.github.istin.dmtools.common.model.IDiffStats;
import com.github.istin.dmtools.report.model.KeyTime;
import com.github.istin.dmtools.team.IEmployees;

import java.util.ArrayList;
import java.util.List;

public class PullRequestsLinesOfCodeMetricSource extends CommonSourceCollector {

    private final String workspace;
    private final String repo;
    private final SourceCode sourceCode;

    private final String branchName;

    public PullRequestsLinesOfCodeMetricSource(String workspace, String repo, SourceCode sourceCode, String branchName, IEmployees employees) {
        super(employees);
        this.workspace = workspace;
        this.repo = repo;
        this.sourceCode = sourceCode;
        this.branchName = branchName;
    }

    @Override
    public List<KeyTime> performSourceCollection(boolean isPersonalized, String metricName) throws Exception {
        List<KeyTime> data = new ArrayList<>();
        sourceCode.performCommitsFromBranch(workspace, repo, branchName, new AtlassianRestClient.Performer<ICommit>() {
            @Override
            public boolean perform(ICommit model) throws Exception {
                if (model.getAuthor() == null) {
                    return false;
                }
                String displayName = model.getAuthor().getFullName();
                if (displayName == null) {
                    return false;
                }

                if (isNameIgnored(displayName)) {
                    return false;
                }

                displayName = transformName(displayName);
                if (!isTeamContainsTheName(displayName)) {
                    displayName = IEmployees.UNKNOWN;
                }
                KeyTime keyTime = new KeyTime(model.getId(), model.getCommitterDate(), isPersonalized ? displayName : metricName);
                IDiffStats diffStats = (model instanceof IDiffStats) ? (IDiffStats) model : null;
                if (diffStats != null && diffStats.getStats() != null) {
                    int additions = diffStats.getStats().getAdditions();
                    keyTime.setWeight(additions / 1000.0);
                }
                data.add(keyTime);
                return false;
            }
        });
        return data;
    }

    public static boolean isValidFileCounted(String source) {
        if (source.endsWith(".g.dart")) {
            return false;
        }
        if (source.endsWith(".freezed.dart")) {
            return false;
        }
        if (source.endsWith(".config.dart")) {
            return false;
        }
        boolean isSwaggerGenFile = source.contains("swagger_generated_code/");
        if (isSwaggerGenFile && source.endsWith("swagger.chopper.dart")) {
            return false;
        }
        if (isSwaggerGenFile && source.endsWith("swagger.dart")) {
            return false;
        }

        return true;
    }
}