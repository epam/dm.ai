// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.model;

import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

public class ToTextTest {

    @Test
    public void testToText() throws IOException {
        // Create a mock instance of ToText
        ToText toTextMock = mock(ToText.class);

        // Define the behavior of the mock
        String expectedText = "Expected converted text";
        Mockito.when(toTextMock.toText()).thenReturn(expectedText);

        // Call the method and assert the result
        String actualText = toTextMock.toText();
        assertEquals(expectedText, actualText);
    }

    @Test(expected = IOException.class)
    public void testToTextThrowsIOException() throws IOException {
        // Create a mock instance of ToText
        ToText toTextMock = mock(ToText.class);

        // Define the behavior of the mock to throw an IOException
        doThrow(new IOException()).when(toTextMock).toText();

        // Call the method, expecting an IOException
        toTextMock.toText();
    }
}