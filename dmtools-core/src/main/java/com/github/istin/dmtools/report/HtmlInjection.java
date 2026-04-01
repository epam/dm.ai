// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report;

import com.github.istin.dmtools.report.freemarker.DevProductivityReport;

public interface HtmlInjection {

    String getHtmBeforeTimeline(DevProductivityReport productivityReport);
}
