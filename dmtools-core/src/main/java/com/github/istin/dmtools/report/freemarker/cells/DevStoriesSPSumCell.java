// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.freemarker.cells;

import com.github.istin.dmtools.common.model.Key;

import java.util.Collection;

public class DevStoriesSPSumCell extends DevProductivityCell {

    public DevStoriesSPSumCell(String basePath, Collection<? extends Key> keys) {
        super(basePath, keys);
        setWeightPrint(true);
        setCountPrint(false);
    }

    public DevStoriesSPSumCell(String basePath) {
        super(basePath);
        setWeightPrint(true);
        setCountPrint(false);
    }

}
