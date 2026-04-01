// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.freemarker;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.*;

import com.github.istin.dmtools.atlassian.common.model.Assignee;

public class AssigneeLinkCellTest {

    @Test
    public void testAssigneeLinkCellWithNullAssignee() {
        AssigneeLinkCell cell = new AssigneeLinkCell(null);
        assertNotNull(cell);
        assertEquals("&nbsp;", cell.getText());
    }

    @Test
    public void testAssigneeLinkCellWithValidAssignee() {
        Assignee mockAssignee = mock(Assignee.class);
        when(mockAssignee.getEmailAddress()).thenReturn("test@example.com");
        when(mockAssignee.getDisplayName()).thenReturn("Test User");

        AssigneeLinkCell cell = new AssigneeLinkCell(mockAssignee);
        assertNotNull(cell);
        assertEquals("<a href=\"mailto:test@example.com\">Test User</a>", cell.getText());
    }
}