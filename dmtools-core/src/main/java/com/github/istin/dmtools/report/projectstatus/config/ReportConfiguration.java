// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.projectstatus.config;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Builder
public class ReportConfiguration {
    private final String[] completedStatuses;
    private final String[] rolePrefixes;
    private final List<String> priorityOrder;
    private final List<String> issueTypeOrder;
    private final Map<String, String> roleDescriptions;
}