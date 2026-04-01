// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.tracker.model;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class WorkflowTest {

    @Test
    public void testSafeValueOf_ExistingValue() {
        Workflow result = Workflow.safeValueOf("NEW");
        assertNotNull(result);
        assertEquals(Workflow.NEW, result);
    }

    @Test
    public void testSafeValueOf_UnknownValue() {
        Workflow result = Workflow.safeValueOf("NON_EXISTENT");
        assertNotNull(result);
        assertEquals(Workflow.UNKNOWN, result);
    }

    @Test
    public void testSafeValueOf_CaseInsensitive() {
        Workflow result = Workflow.safeValueOf("new");
        assertNotNull(result);
        assertEquals(Workflow.NEW, result);
    }

    @Test
    public void testSafeValueOf_NullValue() {
        Workflow result = Workflow.safeValueOf(null);
        assertNotNull(result);
        assertEquals(Workflow.UNKNOWN, result);
    }
}