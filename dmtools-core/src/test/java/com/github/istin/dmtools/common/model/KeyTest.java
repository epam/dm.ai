// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.model;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class KeyTest {

    @Test
    public void testGetWeight() {
        Key keyMock = mock(Key.class);
        when(keyMock.getWeight()).thenReturn(10.5);

        double weight = keyMock.getWeight();
        assertEquals(10.5, weight, 0.0);
    }

    @Test
    public void testGetKey() {
        Key keyMock = mock(Key.class);
        when(keyMock.getKey()).thenReturn("testKey");

        String key = keyMock.getKey();
        assertEquals("testKey", key);
    }
}