// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.freemarker.cells;

import com.github.istin.dmtools.common.model.Key;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Collection;

import static org.junit.Assert.assertNotNull;

public class DevStoriesSPSumCellTest {

    @Test
    public void testConstructorWithKeys() {
        Collection<Key> keys = new ArrayList<>();
        DevStoriesSPSumCell cell = new DevStoriesSPSumCell("base/path", keys);
        assertNotNull(cell);
    }

    @Test
    public void testConstructorWithoutKeys() {
        DevStoriesSPSumCell cell = new DevStoriesSPSumCell("base/path");
        assertNotNull(cell);
    }
}