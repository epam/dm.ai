// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report;

import com.github.istin.dmtools.common.timeline.Release;

import java.util.Calendar;
import java.util.List;

public interface IReleaseGenerator {

    List<Release> generate();

    int getTypeOfReleases();

    Release getCurrentIteration();

    int getStartSprint();

    int getStartFixVersion();

    int getExtraSprintTimeline();

    Calendar getStartDate();

    long getMaxTime();
}