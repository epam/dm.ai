// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.utils;

import org.junit.Test;
import org.mockito.Mockito;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;

public class LogTest {

    @Test
    public void testLogError() {
        // Arrange
        String tag = "TestTag";
        Exception exception = new Exception("Test Exception");

        // Act
        Log.e(tag, exception);

        // Assert
        // Since Log.e() prints to System.err, we can't directly assert the output.
        // However, we can ensure that no exceptions are thrown during the call.
    }
}