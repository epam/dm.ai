// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.metrics;

import com.github.istin.dmtools.report.model.KeyTime;

import java.util.List;

public interface Rule<Source, Item> {
    List<KeyTime> check(Source source, Item item) throws Exception;

}
