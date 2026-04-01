// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.metrics.source;

import com.github.istin.dmtools.report.model.KeyTime;

import java.util.List;

public interface SourceCollector {
    List<KeyTime> performSourceCollection(boolean isPersonalized, String metricName) throws Exception;
}
