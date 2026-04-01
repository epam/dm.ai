// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.freemarker.cells;

import com.github.istin.dmtools.common.model.Key;

import java.util.Collection;

public class DevItemsSumCell extends DevProductivityCell {

    public DevItemsSumCell(String basePath, Collection<? extends Key> keys) {
        super(basePath, keys);
        setWeightPrint(false);
    }

    public DevItemsSumCell(String basePath) {
        super(basePath);
        setWeightPrint(false);
    }

}
