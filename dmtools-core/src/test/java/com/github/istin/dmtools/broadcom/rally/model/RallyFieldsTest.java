// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.broadcom.rally.model;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;

public class RallyFieldsTest {

    @Test
    public void testConstants() {
        assertEquals("StartDate", RallyFields.START_DATE);
        assertEquals("EndDate", RallyFields.END_DATE);
        assertEquals("Name", RallyFields.NAME);
        assertEquals("Project", RallyFields.PROJECT);
        assertEquals("State", RallyFields.STATE);
        assertEquals("RevisionHistory", RallyFields.REVISION_HISTORY);
        assertEquals("FormattedID", RallyFields.FORMATTED_ID);
        assertEquals("_ref", RallyFields._REF);
        assertEquals("_refObjectName", RallyFields._REF_OBJECT_NAME);
        assertEquals("_type", RallyFields._TYPE);
        assertEquals("Priority", RallyFields.PRIORITY);
        assertEquals("c_PriorityUserStory", RallyFields.PRIORITY_USER_STORY);
        assertEquals("CreationDate", RallyFields.CREATION_DATE);
        assertEquals("LastUpdateDate", RallyFields.LAST_UPDATE_DATE);
        assertEquals("FlowState", RallyFields.FLOW_STATE);
        assertEquals("Blocked", RallyFields.BLOCKED);
        assertEquals("BlockedReason", RallyFields.BLOCKED_REASON);
        assertEquals("PlanEstimate", RallyFields.PLAN_ESTIMATE);
        assertEquals("Description", RallyFields.DESCRIPTION);
        assertEquals("User", RallyFields.USER);
        assertEquals("Tags", RallyFields.TAGS);
        assertEquals("testcases", RallyFields.TESTCASES);
        assertEquals("Iteration", RallyFields.ITERATION);
        assertEquals("Parent", RallyFields.PARENT);
    }

    @Test
    public void testDefaultArray() {
        String[] expectedDefault = {
            RallyFields.FORMATTED_ID, RallyFields.NAME, RallyFields.PRIORITY, RallyFields.PRIORITY_USER_STORY,
            RallyFields.CREATION_DATE, RallyFields.LAST_UPDATE_DATE, RallyFields.FLOW_STATE, RallyFields.BLOCKED,
            RallyFields.BLOCKED_REASON, RallyFields.PLAN_ESTIMATE, RallyFields.REVISION_HISTORY, RallyFields.ITERATION,
            RallyFields.START_DATE, RallyFields.END_DATE, RallyFields.PROJECT, RallyFields.TAGS, RallyFields.PARENT
        };
        assertArrayEquals(expectedDefault, RallyFields.DEFAULT);
    }

    @Test
    public void testDefaultExtendedArray() {
        String[] expectedDefaultExtended = {
            RallyFields.FORMATTED_ID, RallyFields.NAME, RallyFields.PRIORITY, RallyFields.PRIORITY_USER_STORY,
            RallyFields.CREATION_DATE, RallyFields.LAST_UPDATE_DATE, RallyFields.FLOW_STATE, RallyFields.BLOCKED,
            RallyFields.BLOCKED_REASON, RallyFields.PLAN_ESTIMATE, RallyFields.REVISION_HISTORY, RallyFields.ITERATION,
            RallyFields.START_DATE, RallyFields.END_DATE, RallyFields.PROJECT, RallyFields.TAGS, RallyFields.DESCRIPTION,
            RallyFields.TESTCASES, RallyFields.PARENT, "Attachments", "WorkProduct", "TestFolder"
        };
        assertArrayEquals(expectedDefaultExtended, RallyFields.DEFAULT_EXTENDED);
    }
}