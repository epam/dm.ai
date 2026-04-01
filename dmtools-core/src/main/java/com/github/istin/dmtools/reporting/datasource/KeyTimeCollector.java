// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.reporting.datasource;

import com.github.istin.dmtools.report.model.KeyTime;
import org.json.JSONObject;
import java.util.List;

@FunctionalInterface
public interface KeyTimeCollector {
    void collect(List<KeyTime> keyTimes, JSONObject rawMetadata, String itemKey);
}
