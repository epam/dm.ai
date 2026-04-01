// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.metrics;

import com.github.istin.dmtools.common.code.SourceCode;
import com.github.istin.dmtools.metrics.source.PullRequestsLinesOfCodeMetricSource;
import com.github.istin.dmtools.team.IEmployees;

public class LinesOfCodeMetric extends Metric {

    public static final String NAME = "Lines Of Code";

    public LinesOfCodeMetric(boolean isPersonalized, String workspace, String repo, String branch, SourceCode sourceCode, IEmployees employees) {
        super(NAME, true, isPersonalized, new PullRequestsLinesOfCodeMetricSource(workspace, repo, sourceCode, branch, employees));
    }

    @Override
    public String getName() {
        return NAME;
    }


}
