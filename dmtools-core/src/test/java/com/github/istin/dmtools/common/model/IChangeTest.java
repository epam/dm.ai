// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.model;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class IChangeTest {

    @Test
    public void testGetFilePath() {
        // Create a mock instance of IChange
        IChange changeMock = mock(IChange.class);

        // Define the behavior of getFilePath method
        when(changeMock.getFilePath()).thenReturn("mock/file/path");

        // Verify the behavior
        assertEquals("mock/file/path", changeMock.getFilePath());

        // Verify that getFilePath was called
        verify(changeMock).getFilePath();
    }
}