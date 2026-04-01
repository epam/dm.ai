// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.model;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class IBodyTest {

    @Test
    public void testGetBody() {
        // Create a mock instance of IBody
        IBody mockBody = mock(IBody.class);

        // Define the behavior of getBody() method
        when(mockBody.getBody()).thenReturn("Mock Body Content");

        // Verify the getBody() method returns the expected value
        assertEquals("Mock Body Content", mockBody.getBody());

        // Verify that getBody() was called exactly once
        verify(mockBody, times(1)).getBody();
    }
}