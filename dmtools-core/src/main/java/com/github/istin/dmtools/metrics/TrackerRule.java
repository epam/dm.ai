// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.metrics;

import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.tracker.TrackerClient;

import java.util.Collections;
import java.util.List;

public interface TrackerRule<T extends ITicket> extends Rule<TrackerClient, T> {

    default List<String> getRequiredExtraFields() {
        return Collections.emptyList();
    }

}