// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools;

import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ConfigTest {

    @Test
    public void testDemoPageSetValue() {
        // Test setting the value of DEMO_PAGE
        Config.DEMO_PAGE = true;
        assertTrue(Config.DEMO_PAGE);
    }

    @Test
    public void testDemoSiteSetValue() {
        // Test setting the value of DEMO_SITE
        Config.DEMO_SITE = true;
        assertTrue(Config.DEMO_SITE);
    }
}