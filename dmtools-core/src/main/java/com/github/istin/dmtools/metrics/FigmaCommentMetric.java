// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.metrics;

import com.github.istin.dmtools.figma.FigmaClient;
import com.github.istin.dmtools.metrics.source.CommonSourceCollector;
import com.github.istin.dmtools.metrics.source.FigmaCommentsMetricSource;
import com.github.istin.dmtools.team.IEmployees;

public class FigmaCommentMetric extends Metric {

    private static final String NAME = "Figma Comment";

    public FigmaCommentMetric(boolean isPersonlized, FigmaClient figmaClient, String... files) {
        this(isPersonlized, null, figmaClient, files);
    }

    public FigmaCommentMetric(boolean isPersonlized, IEmployees employees, FigmaClient figmaClient, String... files) {
        this(isPersonlized, employees, new FigmaCommentsMetricSource(employees, null, figmaClient, files));
    }

    public FigmaCommentMetric(boolean isPersonlized,  IEmployees employees, CommonSourceCollector commonSourceCollector) {
        super(NAME, true, isPersonlized, null, commonSourceCollector);
    }


    @Override
    public String getName() {
        return NAME;
    }
}
