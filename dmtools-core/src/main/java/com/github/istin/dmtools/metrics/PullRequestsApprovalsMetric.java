// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.metrics;

import com.github.istin.dmtools.common.code.SourceCode;
import com.github.istin.dmtools.metrics.source.PullRequestsApprovalsMetricSource;
import com.github.istin.dmtools.team.IEmployees;

import java.util.Calendar;

public class PullRequestsApprovalsMetric extends CommonSourceCodeMetric {

    public static final String NAME = "Pull Requests Approvals";

    public PullRequestsApprovalsMetric(boolean isPersonlized, String workspace, String repo, SourceCode sourceCode, IEmployees employees, Calendar startDate) {
        super(NAME, isPersonlized, workspace, repo, sourceCode, employees, new PullRequestsApprovalsMetricSource(workspace, repo, sourceCode, employees, startDate));
    }

    @Override
    public String getName() {
        return NAME;
    }

}