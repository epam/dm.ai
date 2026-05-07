// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools;

import org.junit.Test;

import static org.junit.Assert.fail;

public class DeprecatedCompatibilityWorkflowFailureMarkerTest {

    @Test
    public void testFailsOnlyWhenDeprecatedCompatibilityWorkflowRequestsIt() {
        if ("true".equalsIgnoreCase(System.getenv("DMTOOLS_FORCE_COMPATIBILITY_TEST_FAILURE"))) {
            fail("Intentional dmtools-core:test failure for deprecated standalone compatibility workflow coverage.");
        }
    }
}
