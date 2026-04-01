// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.freemarker.cells;

import com.github.istin.dmtools.common.model.Key;
import com.github.istin.dmtools.report.freemarker.JQLNumberCell;

import java.util.Collection;

public class DevProductivityCell extends JQLNumberCell {

    public DevProductivityCell(String basePath, Collection<? extends Key> keys) {
        super(basePath, keys);
    }

    public DevProductivityCell(String basePath) {
        super(basePath);
    }
}
