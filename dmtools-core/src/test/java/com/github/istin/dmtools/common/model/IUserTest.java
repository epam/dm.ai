// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.model;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class IUserTest {

    @Test
    public void testGetID() {
        IUser user = mock(IUser.class);
        when(user.getID()).thenReturn("12345");

        String id = user.getID();
        assertEquals("12345", id);
    }

    @Test
    public void testGetFullName() {
        IUser user = mock(IUser.class);
        when(user.getFullName()).thenReturn("John Doe");

        String fullName = user.getFullName();
        assertEquals("John Doe", fullName);
    }

    @Test
    public void testGetEmailAddress() {
        IUser user = mock(IUser.class);
        when(user.getEmailAddress()).thenReturn("john.doe@example.com");

        String email = user.getEmailAddress();
        assertEquals("john.doe@example.com", email);
    }
}