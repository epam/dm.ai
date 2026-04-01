// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.freemarker.cells;

import com.github.istin.dmtools.common.model.Key;
import com.github.istin.dmtools.report.freemarker.JQLNumberCell;
import org.junit.Test;

import java.util.Collection;
import java.util.Collections;
import static org.junit.Assert.assertNotNull;

public class DevProductivityCellTest {

    @Test
    public void testConstructorWithBasePathAndKeys() {
        String basePath = "some/base/path";
        Collection<Key> keys = Collections.emptyList();
        DevProductivityCell cell = new DevProductivityCell(basePath, keys);
        assertNotNull(cell);
    }

    @Test
    public void testConstructorWithBasePathOnly() {
        String basePath = "some/base/path";
        DevProductivityCell cell = new DevProductivityCell(basePath);
        assertNotNull(cell);
    }
}