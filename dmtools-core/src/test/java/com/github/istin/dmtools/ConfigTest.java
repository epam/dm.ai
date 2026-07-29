// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools;

import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ConfigTest {

    @After
    public void tearDown() {
        // restore global flags so other tests in the same JVM fork are not affected
        Config.DEMO_PAGE = false;
        Config.DEMO_SITE = false;
    }

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