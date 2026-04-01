// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.model;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.*;

public class IActivityTest {

    @Test
    public void testGetAction() {
        IActivity activity = mock(IActivity.class);
        when(activity.getAction()).thenReturn("SampleAction");

        String action = activity.getAction();

        assertEquals("SampleAction", action);
    }

    @Test
    public void testGetComment() {
        IActivity activity = mock(IActivity.class);
        IComment comment = mock(IComment.class);
        when(activity.getComment()).thenReturn(comment);

        IComment result = activity.getComment();

        assertNotNull(result);
        assertEquals(comment, result);
    }

    @Test
    public void testGetApproval() {
        IActivity activity = mock(IActivity.class);
        IUser user = mock(IUser.class);
        when(activity.getApproval()).thenReturn(user);

        IUser result = activity.getApproval();

        assertNotNull(result);
        assertEquals(user, result);
    }
}